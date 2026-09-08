use phosphor_root_launcher::*;
use std::{
    ffi::{c_char, c_int, c_long, c_ulong, c_void, CString},
    fs::{File, OpenOptions},
    io::Read,
    os::{
        fd::{AsRawFd, FromRawFd},
        unix::fs::{MetadataExt, OpenOptionsExt},
    },
    time::Instant,
};
// Linux/bionic LP64 declarations. No JNI or ART state is present in this process.
unsafe extern "C" {
    fn getresuid(r: *mut u32, e: *mut u32, s: *mut u32) -> c_int;
    fn getpid() -> c_int;
    fn getppid() -> c_int;
    fn prctl(option: c_int, ...) -> c_int;
    fn alarm(seconds: u32) -> u32;
    fn signal(sig: c_int, handler: usize) -> usize;
    fn sigemptyset(set: *mut c_void) -> c_int;
    fn sigprocmask(how: c_int, set: *const c_void, old: *mut c_void) -> c_int;
    fn syscall(number: c_long, ...) -> c_long;
    fn ioctl(fd: c_int, request: c_ulong, ...) -> c_int;
    fn fcntl(fd: c_int, cmd: c_int, ...) -> c_int;
    fn pipe2(fds: *mut c_int, flags: c_int) -> c_int;
    fn read(fd: c_int, buf: *mut c_void, n: usize) -> isize;
    fn write(fd: c_int, buf: *const c_void, n: usize) -> isize;
    fn close(fd: c_int) -> c_int;
    fn dup2(old: c_int, new: c_int) -> c_int;
    fn fork() -> c_int;
    fn execve(path: *const c_char, argv: *const *const c_char, env: *const *const c_char) -> c_int;
    fn kill(pid: c_int, sig: c_int) -> c_int;
    fn waitpid(pid: c_int, status: *mut c_int, options: c_int) -> c_int;
    fn _exit(code: c_int) -> !;
}
const BUILD: &str = match option_env!("ROOT_HELPER_BUILD") {
    Some(v) => v,
    None => "host-test-not-packaged",
};
const HASH: &str = match option_env!("ROOT_HELPER_SHA256") {
    Some(v) => v,
    None => "host-test-not-packaged",
};
fn os(stage: &str) -> String {
    format!("{stage}: {}", std::io::Error::last_os_error())
}
fn uids() -> Result<[u32; 3], String> {
    let mut u = [0; 3];
    if unsafe { getresuid(&mut u[0], &mut u[1], &mut u[2]) } != 0 {
        Err(os("getresuid"))
    } else {
        Ok(u)
    }
}
fn death(parent: i32) -> bool {
    let mut sig = 0i32;
    unsafe {
        prctl(1, 9 as c_ulong, 0 as c_ulong, 0 as c_ulong, 0 as c_ulong) == 0
            && prctl(2, &mut sig, 0 as c_ulong, 0 as c_ulong, 0 as c_ulong) == 0
            && sig == 9
            && getppid() == parent
            && parent > 1
    }
}
fn nonblock(fd: i32) -> Result<(), String> {
    let f = unsafe { fcntl(fd, 3) };
    if f < 0 || unsafe { fcntl(fd, 4, f | 0x800) } < 0 {
        Err(os("nonblocking_pipe"))
    } else {
        Ok(())
    }
}
fn send(fd: i32, kind: u32, payload: &[u8]) -> Result<(), String> {
    let b = frame(kind, payload).map_err(str::to_owned)?;
    let mut n = 0;
    while n < b.len() {
        let w = unsafe { write(fd, b[n..].as_ptr().cast(), b.len() - n) };
        if w <= 0 {
            if std::io::Error::last_os_error().raw_os_error() == Some(4) {
                continue;
            }
            return Err(os("pipe_backpressure_or_closed"));
        }
        n += w as usize;
    }
    Ok(())
}
fn receive(fd: i32, d: &mut Decoder) -> Result<(Vec<(u32, Vec<u8>)>, bool), String> {
    let mut frames = Vec::new();
    for _ in 0..8192 {
        let mut b = 0u8;
        let n = unsafe { read(fd, (&mut b as *mut u8).cast(), 1) };
        if n == 0 {
            d.eof().map_err(str::to_owned)?;
            return Ok((frames, true));
        }
        if n < 0 {
            match std::io::Error::last_os_error().raw_os_error() {
                Some(11) => return Ok((frames, false)),
                Some(4) => continue,
                _ => return Err(os("pipe_read")),
            }
        }
        d.push(&[b]).map_err(str::to_owned)?;
        if let Some(f) = d.next_frame().map_err(str::to_owned)? {
            frames.push(f);
            if frames.len() >= 16 {
                return Ok((frames, false));
            }
        }
    }
    Ok((frames, false))
}
fn quote(s: &str) -> String {
    let mut out = String::from("\"");
    for c in s.chars().take(1400) {
        match c {
            '"' => out.push_str("\\\""),
            '\\' => out.push_str("\\\\"),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            '\t' => out.push_str("\\t"),
            c if c < ' ' => out.push('?'),
            c => out.push(c),
        }
    }
    out.push('"');
    out
}
fn bounded(path: &str, max: usize) -> Result<String, String> {
    let mut s = Vec::new();
    File::open(path)
        .map_err(|e| format!("{path}: {e}"))?
        .take((max + 1) as u64)
        .read_to_end(&mut s)
        .map_err(|e| e.to_string())?;
    if s.len() > max {
        return Err(format!("{path}: evidence_limit"));
    }
    Ok(String::from_utf8_lossy(&s).trim().to_owned())
}
fn ns() -> Result<String, String> {
    Ok(std::fs::read_link("/proc/self/ns/mnt")
        .map_err(|e| format!("mount_namespace: {e}"))?
        .to_string_lossy()
        .into_owned())
}
fn evidence() -> Result<String, String> {
    let status = bounded("/proc/self/status", 8192)?;
    let selected = status
        .lines()
        .filter(|l| {
            ["Uid:", "Gid:", "Groups:", "Seccomp:"]
                .iter()
                .any(|k| l.starts_with(k))
        })
        .collect::<Vec<_>>()
        .join("\n");
    Ok(format!(
        "{{\"credentials\":{},\"selinux\":{},\"namespace\":{}}}",
        quote(&selected),
        quote(&bounded("/proc/self/attr/current", 512)?),
        quote(&ns()?)
    ))
}
fn sealed(uid: u32) -> Result<File, String> {
    let path = dex_path(uid, BUILD).map_err(str::to_owned)?;
    let parent = std::path::Path::new(&path).parent().unwrap();
    // Android owns app-home permissions. Only our private directories require mode0700.
    for dir in parent.ancestors().take(2) {
        let m = std::fs::symlink_metadata(dir).map_err(|e| format!("sealed_directory: {e}"))?;
        if !m.is_dir() || m.uid() != uid || m.mode() & 0o077 != 0 {
            return Err("sealed_directory_owner_or_mode".into());
        }
    }
    let managed = parent.ancestors().nth(2).unwrap();
    let m = std::fs::symlink_metadata(managed).map_err(|e| format!("managed_directory: {e}"))?;
    if !m.is_dir() || m.uid() != uid || m.mode() & 0o002 != 0 {
        return Err("managed_directory_owner_type_world_write".into());
    }
    let mut f = OpenOptions::new()
        .read(true)
        .custom_flags(0x20000)
        .open(path)
        .map_err(|e| format!("sealed_open: {e}"))?;
    let m = f.metadata().map_err(|e| e.to_string())?;
    if !m.is_file()
        || m.uid() != uid
        || m.mode() & 0o222 != 0
        || m.len() == 0
        || m.len() > 1_048_576
    {
        return Err("sealed_inode_owner_type_mode_size".into());
    }
    let mut bytes = Vec::new();
    (&mut f)
        .take(1_048_577)
        .read_to_end(&mut bytes)
        .map_err(|e| e.to_string())?;
    if bytes.len() != m.len() as usize || sha256::digest(&bytes) != HASH {
        return Err("sealed_sha256_mismatch".into());
    }
    Ok(f)
}
fn pipe() -> Result<[i32; 2], String> {
    let mut f = [-1; 2];
    if unsafe { pipe2(f.as_mut_ptr(), 0x80000) } != 0 {
        Err(os("pipe"))
    } else {
        Ok(f)
    }
}
fn runtime_environment() -> Result<Vec<CString>, String> {
    // Read the platform-generated init data, never caller environment or shell syntax.
    for path in ["/data", "/data/system", "/data/system/environ"] {
        let m = std::fs::symlink_metadata(path)
            .map_err(|e| format!("platform_classpath_parent: {e}"))?;
        if !m.is_dir()
            || !matches!(m.uid(), 0 | 1000)
            || !matches!(m.gid(), 0 | 1000)
            || m.mode() & 0o002 != 0
        {
            return Err("platform_classpath_parent_owner_type_mode".into());
        }
    }
    let mut file = OpenOptions::new()
        .read(true)
        .custom_flags(0x20000 | 0x80000)
        .open("/data/system/environ/classpath")
        .map_err(|e| format!("platform_classpath_open: {e}"))?;
    let m = file
        .metadata()
        .map_err(|e| format!("platform_classpath_stat: {e}"))?;
    if !m.is_file()
        || !matches!(m.uid(), 0 | 1000)
        || !matches!(m.gid(), 0 | 1000)
        || m.mode() & 0o022 != 0
        || m.len() == 0
        || m.len() > classpath::LIMIT as u64
    {
        return Err(format!(
            "platform_classpath_file_owner_type_mode_size: uid={} gid={} mode={:o} size={}",
            m.uid(),
            m.gid(),
            m.mode(),
            m.len()
        ));
    }
    let mut bytes = Vec::new();
    (&mut file)
        .take((classpath::LIMIT + 1) as u64)
        .read_to_end(&mut bytes)
        .map_err(|e| format!("platform_classpath_read: {e}"))?;
    let text = std::str::from_utf8(&bytes).map_err(|_| "platform_classpath_utf8")?;
    let exports = classpath::parse(text).map_err(str::to_owned)?;
    for export in &exports {
        let (_, value) = export.split_once('=').ok_or("platform_classpath_export")?;
        for path in value.split(':') {
            let real = std::fs::canonicalize(path)
                .map_err(|e| format!("platform_classpath_jar_realpath: {e}"))?;
            if !real.to_str().is_some_and(classpath::jar_path) {
                return Err("platform_classpath_jar_resolves_outside_platform".into());
            }
            let m = std::fs::metadata(&real)
                .map_err(|e| format!("platform_classpath_jar_stat: {e}"))?;
            if !classpath::trusted_jar(m.is_file(), m.uid(), m.gid(), m.mode()) {
                return Err(format!(
                    "platform_classpath_jar_owner_type_mode: uid={} gid={} mode={:o}",
                    m.uid(),
                    m.gid(),
                    m.mode()
                ));
            }
        }
    }
    ENV.iter()
        .map(|s| (*s).to_owned())
        .chain(exports)
        .map(|s| CString::new(s).map_err(|_| "platform_environment_nul".to_owned()))
        .collect()
}
fn setup() -> Result<(File, File, u32, String, Selection), String> {
    // sigset_t storage is oversized and aligned for bionic LP64.
    let mut set = [0u64; 16];
    unsafe {
        if sigemptyset(set.as_mut_ptr().cast()) != 0
            || sigprocmask(2, set.as_ptr().cast(), std::ptr::null_mut()) != 0
        {
            return Err(os("signal_mask"));
        }
        signal(14, 0);
        signal(15, 0);
        signal(2, 0);
        signal(17, 0);
        signal(13, 1);
        alarm(5);
    }
    if std::env::args_os().count() != 1 {
        return Err("fixed_no_argument_contract".into());
    }
    let parent = unsafe { getppid() };
    if !death(parent) {
        return Err(os("parent_death_contract"));
    }
    nonblock(0)?;
    nonblock(1)?;
    nonblock(2)?;
    let mut selection_decoder = Decoder::default();
    let selection = loop {
        // Stop reading at the first complete frame. Later controls remain in the pipe.
        let mut byte = 0u8;
        let n = unsafe { read(0, (&mut byte as *mut u8).cast(), 1) };
        if n == 0 {
            return Err("selection_eof".into());
        }
        if n < 0 {
            match std::io::Error::last_os_error().raw_os_error() {
                Some(4) => continue,
                Some(11) => {
                    std::thread::sleep(std::time::Duration::from_millis(5));
                    continue;
                }
                _ => return Err(os("selection_read")),
            }
        }
        if let Some(selection) = selection_byte(
            &mut selection_decoder,
            byte,
            option_env!("ROOT_HELPER_DEBUG") == Some("true"),
        )
        .map_err(str::to_owned)?
        {
            break selection;
        }
    };
    let u = uids()?;

    if !app_uid(u) {
        return Err("original_app_uid_invalid".into());
    }
    let dex = sealed(u[0])?;
    let before = evidence()?;
    let before_ns = ns()?;
    let mut fd = -1i32;
    unsafe {
        syscall(
            ARM64_SYS_REBOOT as c_long,
            0xdeadbeefu64,
            0xcafebabeu64,
            0u64,
            &mut fd,
        );
    }
    if fd < 0 {
        return Err(os("ksu_fd_unavailable"));
    }
    let driver = unsafe { File::from_raw_fd(fd) };
    let mut info = Info::default();
    if unsafe { ioctl(fd, GET_INFO as c_ulong, &mut info) } != 0 {
        return Err(os("ksu_get_info"));
    }
    if !supported(&info) {
        return Err(format!("unsupported_provider: {info:?}"));
    }
    if unsafe { ioctl(fd, GRANT_ROOT as c_ulong, std::ptr::null_mut::<c_void>()) } != 0 {
        return Err(os("ksu_grant_root"));
    }
    if !death(parent) {
        return Err(os("postgrant_parent_death_contract"));
    }
    if uids() != Ok([0; 3]) {
        return Err("grant_did_not_produce_all_uid_zero".into());
    }
    let after = evidence()?;
    if ns()? != before_ns {
        let _ = send(
            1,
            13,
            format!(
                "{{\"generation\":{},\"before\":{before},\"after\":{after}}}",
                selection.generation
            )
            .as_bytes(),
        );
        return Err("mount_namespace_changed_refusing_capture".into());
    }
    unsafe {
        alarm(5);
    }
    let detail=format!("{{\"before\":{before},\"after\":{after},\"original_uid\":{},\"pid\":{},\"driver_version\":{},\"flags\":{},\"features\":{},\"uapi\":{}}}",u[0],unsafe{getpid()},info.version,info.flags,info.features,info.uapi_version);
    let detail = format!("{{\"generation\":{},{}", selection.generation, &detail[1..]);
    Ok((dex, driver, u[0], detail, selection))
}
pub fn run() -> i32 {
    let (dex, driver, uid, detail, selection) = match setup() {
        Ok(v) => v,
        Err(e) => {
            let _=send(1,14,format!("{{\"status\":\"error\",\"error\":{},\"fix\":\"Inspect this fixed-launch stage and existing provider authorization. Do not change system policy.\",\"cleanup_confirmed\":true,\"child_started\":false,\"killed\":false}}",quote(&e)).as_bytes());
            return 4;
        }
    };
    if selection.mode == 1 {
        let _ = send(1, 13, detail.as_bytes());
        let ok = send(1, 14, format!("{{\"generation\":{},\"status\":\"ok\",\"authorized\":true,\"cleanup_confirmed\":true,\"child_started\":false,\"killed\":false}}", selection.generation).as_bytes()).is_ok();
        return if ok { 0 } else { 4 };
    }
    match supervise(dex, driver, uid, &detail, selection) {
        Ok(ok) => {
            if ok {
                0
            } else {
                4
            }
        }
        Err(e) => {
            let _=send(1,14,format!("{{\"generation\":{},\"status\":\"error\",\"error\":{},\"fix\":\"Inspect packaged helper and private pipe setup.\",\"cleanup_confirmed\":true,\"child_started\":false,\"killed\":false}}",selection.generation,quote(&e)).as_bytes());
            4
        }
    }
}
fn supervise(
    dex: File,
    driver: File,
    uid: u32,
    detail: &str,
    selection: Selection,
) -> Result<bool, String> {
    // All fallible setup is before fork. Afterwards every exit uses the one wait owner.
    send(1, 13, detail.as_bytes())?;
    let env = runtime_environment()?;
    let input = pipe()?;
    let output = pipe()?;
    nonblock(input[1])?;
    nonblock(output[0])?;
    let pinned = unsafe { fcntl(dex.as_raw_fd(), 1030, 20) };
    if pinned < 0 {
        return Err(os("pin_dex_fd"));
    }
    let pinned = unsafe { File::from_raw_fd(pinned) };
    let argv = [EXECUTABLE, "/", CLASS].map(|s| CString::new(s).unwrap());
    let mut ap = argv.iter().map(|s| s.as_ptr()).collect::<Vec<_>>();
    ap.push(std::ptr::null());
    let mut ep = env.iter().map(|s| s.as_ptr()).collect::<Vec<_>>();
    ep.push(std::ptr::null());
    let fds = std::fs::read_dir("/proc/self/fd")
        .map_err(|e| e.to_string())?
        .filter_map(|e| e.ok()?.file_name().to_str()?.parse::<i32>().ok())
        .collect::<Vec<_>>();
    let parent = unsafe { getpid() };
    let child = unsafe { fork() };
    if child < 0 {
        return Err(os("fork"));
    }
    if child == 0 {
        unsafe {
            if !death(parent)
                || dup2(input[0], 0) < 0
                || dup2(output[1], 1) < 0
                || dup2(pinned.as_raw_fd(), 6) < 0
            {
                _exit(120);
            }
            for fd in fds {
                if fd > 2 && fd != 6 {
                    close(fd);
                }
            }
            signal(13, 0);
            execve(argv[0].as_ptr(), ap.as_ptr(), ep.as_ptr());
            _exit(121);
        }
    }
    unsafe {
        close(input[0]);
        close(output[1]);
    }
    drop(pinned);
    drop(dex);
    let start = Instant::now();
    let mut s = Session {
        selection,
        ..Session::default()
    };
    let id = selection.tag(uid, BUILD);
    let mut app = Decoder::default();
    let mut helper = Decoder::default();
    let mut next_grant = 0;
    let mut stop_sent = false;
    let mut pipe_eof = false;
    let mut detail_error = String::new();
    if let Err(e) = send(input[1], 10, &id) {
        detail_error = e;
        s.stop(0, Some("helper_init_write"));
    }
    loop {
        unsafe {
            alarm(5);
        }
        let now = start.elapsed().as_millis() as u64;
        match receive(0, &mut app) {
            Ok((frames, eof)) => {
                for (k, p) in frames {
                    match s.app(k, &p, now) {
                        Ok(true) => {
                            if let Err(e) = send(input[1], k, &p) {
                                detail_error = e;
                                s.stop(now, Some("backpressure"));
                            }
                        }
                        Ok(false) => {}
                        Err(e) => s.reject(now, e),
                    }
                }
                if eof {
                    s.stop(now, Some("app_eof"));
                }
            }
            Err(e) => {
                detail_error = e;
                s.reject(now, "app_protocol");
            }
        }
        if !pipe_eof {
            match receive(output[0], &mut helper) {
                Ok((frames, eof)) => {
                    for (k, p) in frames {
                        match s.helper(k, &p, &id, now) {
                            Ok(()) => {
                                if let Err(e) = send(1, k, &p) {
                                    detail_error = e;
                                    s.stop(now, Some("backpressure"));
                                }
                            }
                            Err(e) => s.reject(now, e),
                        }
                    }
                    if eof {
                        pipe_eof = true;
                        if !s.result {
                            s.reject(now, "helper_eof");
                        }
                    }
                }
                Err(e) => {
                    detail_error = e;
                    s.reject(now, "helper_protocol");
                }
            }
        }
        if now >= next_grant {
            next_grant = now + 1000;
            let mut g = Granted {
                uid,
                granted: 0,
                padding: [0; 3],
            };
            if unsafe { ioctl(driver.as_raw_fd(), UID_GRANTED_ROOT as c_ulong, &mut g) } != 0 {
                detail_error = os("ksu_uid_granted_root");
                s.stop(now, Some("grant_query"));
            } else if g.granted != 1 {
                s.stop(now, Some("revoked"));
            }
        }
        s.tick(now);
        if matches!(s.phase, Phase::Stopping(_)) && !stop_sent {
            let _ = send(input[1], 3, &selection.generation.to_le_bytes());
            stop_sent = true;
        }
        let mut status = 0;
        let waited = unsafe { waitpid(child, &mut status, 1) };
        if waited == child {
            // Exit can race the EAGAIN observation above. Reap ownership is final,
            // but parse queued terminal frames before retiring protocol state.
            for _ in 0..4 {
                if pipe_eof {
                    break;
                }
                match receive(output[0], &mut helper) {
                    Ok((frames, eof)) => {
                        for (k, p) in frames {
                            match s.helper(k, &p, &id, now) {
                                Ok(()) => {
                                    if let Err(e) = send(1, k, &p) {
                                        detail_error = e;
                                        s.stop(now, Some("backpressure"));
                                    }
                                }
                                Err(e) => s.reject(now, e),
                            }
                        }
                        pipe_eof = eof;
                    }
                    Err(e) => {
                        detail_error = e;
                        s.reject(now, "terminal_helper_protocol");
                        break;
                    }
                }
            }
            if !pipe_eof || !s.result {
                s.reject(now, "missing_terminal_helper_frame");
            }
            s.reaped(status);
            break;
        }
        if waited < 0 && std::io::Error::last_os_error().raw_os_error() != Some(4) {
            detail_error = os("waitpid");
            s.stop(now, Some("wait_failed"));
            break;
        }
        if s.should_kill(now) {
            if unsafe { kill(child, 9) } != 0 {
                detail_error = os("direct_child_kill");
            }
            s.killed = true;
        }
        if matches!(s.phase, Phase::Stopping(at) if now.saturating_sub(at) >= 3000) {
            s.stop(now, Some("reap_deadline"));
            break;
        }
        std::thread::sleep(std::time::Duration::from_millis(10));
    }
    unsafe {
        close(input[1]);
        close(output[0]);
    }
    let cleanup = s.cleanup_confirmed();
    let ok = s.success();
    let cause = s
        .failure
        .unwrap_or(if ok { "" } else { "helper_exit_or_cleanup" });
    let final_json=format!("{{\"status\":{},\"error\":{},\"detail\":{},\"fix\":\"Inspect helper stage, existing authorization and cleanup receipt before another trial.\",\"cleanup_confirmed\":{cleanup},\"child_started\":true,\"killed\":{},\"child_wait_status\":{}}}",quote(if ok{"ok"}else{"error"}),quote(cause),quote(&detail_error),s.killed,s.exit.map(|e|e.to_string()).unwrap_or("null".into()));
    let final_json = format!(
        "{{\"generation\":{},{}",
        selection.generation,
        &final_json[1..]
    );
    let _ = send(1, 14, final_json.as_bytes());
    // Process exit triggers PDEATHSIG on any unreaped helper. That is not cleanup proof.
    Ok(ok)
}

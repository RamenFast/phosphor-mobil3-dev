pub mod classpath;
pub mod sha256;
pub const MAGIC: u32 = 0x31524150;
pub const MAX_PAYLOAD: usize = 4096;
pub const GET_INFO: u64 = 0x80104b02;
pub const GRANT_ROOT: u64 = 0x4b01;
pub const UID_GRANTED_ROOT: u64 = 0xc0004b08;
pub const ARM64_SYS_REBOOT: i64 = 142;
pub const PACKAGE: &str = "dev.phosphor.mobil3.debug";
pub const EXECUTABLE: &str = "/system/bin/app_process";
pub const CLASS: &str = "dev.phosphor.mobil3.root.AudioPolicyMain";
pub const ENV: &[&str] = &[
    "CLASSPATH=/proc/self/fd/6",
    "ANDROID_ROOT=/system",
    "ANDROID_DATA=/data",
    "ANDROID_ART_ROOT=/apex/com.android.art",
    "ANDROID_I18N_ROOT=/apex/com.android.i18n",
    "ANDROID_TZDATA_ROOT=/apex/com.android.tzdata",
];
#[repr(C)]
#[derive(Default, Debug)]
pub struct Info {
    pub version: u32,
    pub flags: u32,
    pub features: u32,
    pub uapi_version: u32,
}
#[repr(C)]
pub struct Granted {
    pub uid: u32,
    pub granted: u8,
    pub padding: [u8; 3],
}
pub fn supported(i: &Info) -> bool {
    i.version == 32525 && i.uapi_version == 2 && i.flags == 5
}
pub fn app_uid(u: [u32; 3]) -> bool {
    u[0] == u[1] && u[1] == u[2] && (10000..=19999).contains(&(u[0] % 100000))
}
pub fn dex_path(uid: u32, build: &str) -> Result<String, &'static str> {
    if !app_uid([uid; 3])
        || build.len() != 64
        || !build
            .bytes()
            .all(|c| c.is_ascii_digit() || (b'a'..=b'f').contains(&c))
    {
        return Err("identity_invalid");
    }
    Ok(format!(
        "/data/user/{}/{PACKAGE}/no_backup/root-helper/{build}/helper.jar",
        uid / 100000
    ))
}
pub fn identity(uid: u32, build: &str) -> Vec<u8> {
    [uid.to_le_bytes().as_slice(), build.as_bytes()].concat()
}
pub fn frame(kind: u32, payload: &[u8]) -> Result<Vec<u8>, &'static str> {
    if payload.len() > MAX_PAYLOAD {
        return Err("frame_overflow");
    }
    Ok([
        MAGIC.to_le_bytes().as_slice(),
        kind.to_le_bytes().as_slice(),
        (payload.len() as u32).to_le_bytes().as_slice(),
        payload,
    ]
    .concat())
}
#[derive(Default)]
pub struct Decoder {
    buf: Vec<u8>,
}
impl Decoder {
    pub fn push(&mut self, data: &[u8]) -> Result<(), &'static str> {
        if self.buf.len() + data.len() > MAX_PAYLOAD + 12 {
            return Err("frame_overflow");
        }
        self.buf.extend_from_slice(data);
        Ok(())
    }
    pub fn next_frame(&mut self) -> Result<Option<(u32, Vec<u8>)>, &'static str> {
        if self.buf.len() < 12 {
            return Ok(None);
        }
        let word = |i| u32::from_le_bytes(self.buf[i..i + 4].try_into().unwrap());
        let (magic, kind, len) = (word(0), word(4), word(8) as usize);
        if magic != MAGIC || len > MAX_PAYLOAD {
            return Err("frame_header");
        }
        if self.buf.len() < len + 12 {
            return Ok(None);
        }
        let payload = self.buf[12..12 + len].to_vec();
        self.buf.drain(..len + 12);
        Ok(Some((kind, payload)))
    }
    pub fn eof(&self) -> Result<(), &'static str> {
        if self.buf.is_empty() {
            Ok(())
        } else {
            Err("partial_frame_eof")
        }
    }
}
#[derive(Debug, PartialEq, Eq)]
pub enum Phase {
    Waiting,
    Ready,
    Capture(u64),
    Stopping(u64),
    Reaped,
}
pub struct Session {
    pub phase: Phase,
    pub heartbeat: u64,
    pub result: bool,
    pub killed: bool,
    pub failure: Option<&'static str>,
    pub exit: Option<i32>,
}
impl Default for Session {
    fn default() -> Self {
        Self {
            phase: Phase::Waiting,
            heartbeat: 0,
            result: false,
            killed: false,
            failure: None,
            exit: None,
        }
    }
}
impl Session {
    pub fn stop(&mut self, now: u64, cause: Option<&'static str>) {
        if self.failure.is_none() {
            self.failure = cause;
        }
        if !matches!(self.phase, Phase::Stopping(_) | Phase::Reaped) {
            self.phase = Phase::Stopping(now);
        }
    }
    pub fn app(&mut self, kind: u32, payload: &[u8], now: u64) -> Result<bool, &'static str> {
        if !payload.is_empty() {
            return Err("control_payload");
        }
        match kind {
            1 => {
                self.heartbeat = now;
                Ok(false)
            }
            2 if self.phase == Phase::Ready => {
                self.phase = Phase::Capture(now);
                Ok(true)
            }
            3 => {
                self.stop(now, Some("app_stop"));
                Ok(true)
            }
            _ => Err("control_state"),
        }
    }
    pub fn helper(
        &mut self,
        kind: u32,
        payload: &[u8],
        id: &[u8],
        now: u64,
    ) -> Result<(), &'static str> {
        if payload.len() <= id.len() || !payload.starts_with(id) {
            return Err("helper_identity");
        }
        match kind {
            11 if self.phase == Phase::Waiting => self.phase = Phase::Ready,
            12 if !self.result && !matches!(self.phase, Phase::Reaped) => {
                self.result = true;
                self.stop(now, None);
            }
            _ => return Err("helper_state"),
        }
        Ok(())
    }
    pub fn tick(&mut self, now: u64) {
        if now.saturating_sub(self.heartbeat) > 2000 {
            self.stop(now, Some("heartbeat_timeout"));
        }
        if matches!(self.phase, Phase::Waiting | Phase::Ready) && now >= 10000 {
            self.stop(now, Some("ready_timeout"));
        }
        if let Phase::Capture(start) = self.phase {
            if now.saturating_sub(start) >= 6000 {
                self.stop(now, Some("capture_timeout"));
            }
        }
    }
    pub fn should_kill(&self, now: u64) -> bool {
        matches!(self.phase, Phase::Stopping(start) if now.saturating_sub(start) >= 1000)
            && !self.killed
    }
    pub fn reaped(&mut self, exit: i32) {
        self.exit = Some(exit);
        self.phase = Phase::Reaped;
    }
    pub fn success(&self) -> bool {
        self.phase == Phase::Reaped
            && self.exit == Some(0)
            && self.result
            && !self.killed
            && self.failure.is_none()
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn uapi_layout() {
        assert_eq!(std::mem::size_of::<Info>(), 16);
        assert_eq!(std::mem::align_of::<Info>(), 4);
        assert_eq!(std::mem::size_of::<Granted>(), 8);
        assert_eq!(std::mem::offset_of!(Granted, granted), 4);
        assert_eq!(
            (GET_INFO, GRANT_ROOT, UID_GRANTED_ROOT, ARM64_SYS_REBOOT),
            (0x80104b02, 0x4b01, 0xc0004b08, 142)
        );
    }
    #[test]
    fn provider_compatibility() {
        let mut i = Info {
            version: 32525,
            flags: 5,
            features: 99,
            uapi_version: 2,
        };
        assert!(supported(&i));
        for flags in [0, 1, 4, 7, 13] {
            i.flags = flags;
            assert!(!supported(&i));
        }
    }
    #[test]
    fn uid_and_fixed_launch() {
        assert!(app_uid([10401; 3]));
        assert!(!app_uid([0; 3]));
        assert!(!app_uid([10401, 0, 10401]));
        assert!(!app_uid([2000; 3]));
        assert_eq!(
            dex_path(110401, &"a".repeat(64)).unwrap(),
            format!(
                "/data/user/1/{PACKAGE}/no_backup/root-helper/{}/helper.jar",
                "a".repeat(64)
            )
        );
        assert!(dex_path(10401, "../x").is_err());
        assert_eq!(ENV[0], "CLASSPATH=/proc/self/fd/6");
        assert_eq!(EXECUTABLE, "/system/bin/app_process");
        assert_eq!(CLASS, "dev.phosphor.mobil3.root.AudioPolicyMain");
    }
    #[test]
    fn partial_io_and_eof() {
        let f = frame(11, b"test").unwrap();
        let mut d = Decoder::default();
        for b in &f[..f.len() - 1] {
            d.push(&[*b]).unwrap();
            assert!(d.next_frame().unwrap().is_none());
        }
        assert!(d.eof().is_err());
        d.push(&f[f.len() - 1..]).unwrap();
        assert_eq!(d.next_frame().unwrap(), Some((11, b"test".to_vec())));
        assert!(d.eof().is_ok());
    }
    #[test]
    fn frame_bounds_and_corruption() {
        assert!(frame(1, &vec![0; 4097]).is_err());
        let mut d = Decoder::default();
        assert!(d.push(&vec![0; 4109]).is_err());
        d.push(&[0; 12]).unwrap();
        assert!(d.next_frame().is_err());
    }
    #[test]
    fn stale_ready_and_control() {
        let mut s = Session::default();
        let id = identity(10401, &"a".repeat(64));
        let p = [id.clone(), b"{}".to_vec()].concat();
        assert!(s.app(2, &[], 1).is_err());
        s.helper(11, &p, &id, 1).unwrap();
        assert!(s.helper(11, &p, &id, 2).is_err());
        assert!(s.app(2, b"x", 2).is_err());
        assert!(s.app(2, &[], 2).unwrap());
        assert!(s.app(2, &[], 3).is_err());
    }
    #[test]
    fn stop_eof_revocation_backpressure_kill_reap() {
        for cause in [
            "app_stop",
            "app_eof",
            "revoked",
            "grant_query",
            "backpressure",
            "helper_exit",
            "partial_frame_eof",
        ] {
            let mut s = Session::default();
            s.stop(10, Some(cause));
            assert!(!s.should_kill(1009));
            assert!(s.should_kill(1010));
            s.killed = true;
            assert!(!s.should_kill(1011));
            s.reaped(9);
            assert!(!s.should_kill(3000));
            assert!(!s.success());
        }
    }
    #[test]
    fn deadlines() {
        let mut s = Session::default();
        s.tick(2001);
        assert_eq!(s.failure, Some("heartbeat_timeout"));
        let mut s = Session::default();
        s.heartbeat = 10000;
        s.tick(10000);
        assert_eq!(s.failure, Some("ready_timeout"));
        let mut s = Session::default();
        s.phase = Phase::Capture(100);
        s.heartbeat = 6100;
        s.tick(6100);
        assert_eq!(s.failure, Some("capture_timeout"));
    }
    #[test]
    fn no_success_without_cleanup() {
        let mut s = Session::default();
        s.result = true;
        assert!(!s.success());
        s.reaped(0);
        assert!(s.success());
        s.killed = true;
        assert!(!s.success());
        s.killed = false;
        s.failure = Some("revoked");
        assert!(!s.success());
    }
}

#[cfg(test)]
mod terminal_tests {
    use super::*;
    #[test]
    fn terminal_frame_arriving_with_child_exit_precedes_retirement() {
        let id = identity(10401, &"a".repeat(64));
        let payload = [id.clone(), b"{}".to_vec()].concat();
        let mut s = Session::default();
        s.helper(11, &payload, &id, 0).unwrap();
        s.app(2, &[], 1).unwrap();
        let mut d = Decoder::default();
        assert!(d.next_frame().unwrap().is_none());
        // EAGAIN occurred, then RESULT was queued immediately before child exit.
        for byte in frame(12, &payload).unwrap() {
            d.push(&[byte]).unwrap();
        }
        let (kind, body) = d.next_frame().unwrap().unwrap();
        s.helper(kind, &body, &id, 5001).unwrap();
        d.eof().unwrap();
        s.reaped(0);
        assert!(s.success());
        assert!(!s.should_kill(9000));
        assert!(s.helper(12, &payload, &id, 5002).is_err());
    }
}

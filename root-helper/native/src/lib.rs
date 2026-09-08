pub mod classpath;
pub mod root_epoch;
#[cfg(test)]
mod root_epoch_tests;
pub mod sha256;
pub mod signal;
pub const MAGIC: u32 = 0x31524150;
pub const MAX_PAYLOAD: usize = 4096;
pub const GET_INFO: u64 = 0x80104b02;
pub const GRANT_ROOT: u64 = 0x4b01;
pub const UID_GRANTED_ROOT: u64 = 0xc0004b08;
pub const ARM64_SYS_REBOOT: i64 = 142;
pub const PACKAGE: &str = match option_env!("ROOT_HELPER_PACKAGE") {
    Some(v) => v,
    None => "dev.phosphor.mobil3.debug",
};
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Selection {
    pub generation: u64,
    pub mode: u32,
}
impl Selection {
    pub fn parse(kind: u32, p: &[u8], debug: bool) -> Result<Self, &'static str> {
        if kind != 20 || p.len() != 12 {
            return Err("selection_contract");
        }
        let generation = u64::from_le_bytes(p[..8].try_into().unwrap());
        let mode = u32::from_le_bytes(p[8..].try_into().unwrap());
        if generation == 0
            || generation > i64::MAX as u64
            || mode > 5
            || (!debug && (mode == 0 || mode >= 3))
        {
            return Err("selection_mode_or_generation");
        }
        Ok(Self { generation, mode })
    }
    pub fn tag(self, uid: u32, build: &str) -> Vec<u8> {
        [
            identity(uid, build),
            self.generation.to_le_bytes().to_vec(),
            self.mode.to_le_bytes().to_vec(),
        ]
        .concat()
    }
    pub fn stream(self) -> bool {
        self.mode == 2 || self.mode == 3
    }
}
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
pub fn selection_byte(
    decoder: &mut Decoder,
    byte: u8,
    debug: bool,
) -> Result<Option<Selection>, &'static str> {
    decoder.push(&[byte])?;
    decoder
        .next_frame()?
        .map(|(k, p)| Selection::parse(k, &p, debug))
        .transpose()
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
    pub selection: Selection,
    pub sequence: u64,
    pub pcm_at: u64,
    pub progress_sequence: u64,
    pub protocol_clean: bool,
    pub epoch: root_epoch::RootEpoch,
    pub capture_started: bool,
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
            selection: Selection {
                generation: 1,
                mode: 0,
            },
            sequence: 0,
            pcm_at: 0,
            progress_sequence: 0,
            protocol_clean: true,
            epoch: root_epoch::RootEpoch::default(),
            capture_started: false,
        }
    }
}
impl Session {
    pub fn reject(&mut self, now: u64, cause: &'static str) {
        self.protocol_clean = false;
        self.stop(now, Some(cause));
    }
    pub fn stop(&mut self, now: u64, cause: Option<&'static str>) {
        if self.failure.is_none() {
            self.failure = cause;
        }
        if !matches!(self.phase, Phase::Stopping(_) | Phase::Reaped) {
            self.phase = Phase::Stopping(now);
        }
    }
    pub fn app(&mut self, kind: u32, payload: &[u8], now: u64) -> Result<bool, &'static str> {
        if kind == 4 {
            if !self.selection.stream() || !matches!(self.phase, Phase::Ready | Phase::Capture(_)) || self.result {
                return Err("epoch_control_state");
            }
            self.epoch.request(payload, self.selection.generation, now)?;
            return Ok(true);
        }
        if payload != self.selection.generation.to_le_bytes() {
            return Err("control_payload");
        }
        match kind {
            1 => {
                self.heartbeat = now;
                Ok(false)
            }
            2 if self.phase == Phase::Ready => {
                if self.selection.stream() && self.epoch.acknowledged.is_none() {
                    return Err("epoch_start_unbound");
                }
                self.phase = Phase::Capture(now);
                self.capture_started = true;
                self.pcm_at = now;
                Ok(true)
            }
            3 => {
                self.stop(now, None);
                Ok(false)
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
        if payload.len() <= id.len() || !payload.starts_with(id)
            || (self.selection.stream() && (id.len() != 80 || payload.len() > MAX_PAYLOAD)) {
            return Err("helper_identity");
        }
        match kind {
            11 if self.phase == Phase::Waiting => {
                if self.selection.stream() && !root_epoch::schema_one(&payload[id.len()..]) {
                    return Err("pcm_epoch_schema");
                }
                self.phase = Phase::Ready;
            }
            17 if self.selection.stream()
                && matches!(self.phase, Phase::Ready | Phase::Capture(_) | Phase::Stopping(_))
                && !self.result =>
            {
                if self.epoch.expired(now) {
                    self.stop(now, Some("epoch_ack_timeout"));
                }
                self.epoch.ack(&payload[id.len()..], self.sequence)?;
            }
            15 if self.selection.stream()
                && matches!(self.phase, Phase::Capture(_) | Phase::Stopping(_))
                && self.capture_started
                && !self.result =>
            {
                let pcm = &payload[id.len()..];
                if pcm.len() < 40 {
                    return Err("pcm_header");
                }
                let seq = u64::from_le_bytes(pcm[..8].try_into().unwrap());
                let word = |i| u32::from_le_bytes(pcm[i..i + 4].try_into().unwrap());
                let count = word(20) as usize;
                if seq != self.sequence
                    || seq >= i64::MAX as u64
                    || word(8) != 16000
                    || word(12) != 1
                    || word(16) != 2
                    || !(1..=160).contains(&count)
                    || pcm.len() != 40 + count * 2
                    || self.epoch.acknowledged != Some(root_epoch::Binding {
                        control_sequence: u64::from_le_bytes(pcm[24..32].try_into().unwrap()),
                        epoch: u64::from_le_bytes(pcm[32..40].try_into().unwrap()),
                    })
                {
                    return Err("pcm_sequence_format_count");
                }
                self.sequence = self
                    .sequence
                    .checked_add(1)
                    .ok_or("pcm_sequence_overflow")?;
                self.pcm_at = now;
            }
            16 if (self.selection.stream() || self.selection.mode >= 4)
                && matches!(self.phase, Phase::Capture(_) | Phase::Stopping(_))
                && (!self.selection.stream() || self.capture_started)
                && !self.result =>
            {
                let progress = &payload[id.len()..];
                if progress.len() != 8
                    || u64::from_le_bytes(progress.try_into().unwrap()) != self.progress_sequence
                    || (self.selection.stream() && self.progress_sequence >= i64::MAX as u64)
                {
                    return Err("progress_sequence");
                }
                self.progress_sequence = self
                    .progress_sequence
                    .checked_add(1)
                    .ok_or("progress_overflow")?;
                self.pcm_at = now;
            }
            12 if !self.result && !matches!(self.phase, Phase::Reaped) => {
                self.result = true;
                self.epoch.pending = None;
                self.stop(now, None);
            }
            _ => return Err("helper_state"),
        }
        Ok(())
    }
    pub fn tick(&mut self, now: u64) {
        if self.epoch.expired(now) && !self.result {
            self.stop(now, Some("epoch_ack_timeout"));
        }
        if now.saturating_sub(self.heartbeat) > 2000 {
            self.stop(now, Some("heartbeat_timeout"));
        }
        if matches!(self.phase, Phase::Waiting | Phase::Ready) && now >= 10000 {
            self.stop(now, Some("ready_timeout"));
        }
        if let Phase::Capture(start) = self.phase {
            if self.selection.mode != 2 && now.saturating_sub(start) >= 6000 {
                self.stop(now, Some("capture_timeout"));
            }
        }
        if (self.selection.stream() || self.selection.mode >= 4)
            && matches!(self.phase, Phase::Capture(_))
            && now.saturating_sub(self.pcm_at) > 3000
        {
            self.stop(now, Some("helper_progress_timeout"));
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
        self.cleanup_confirmed()
            && self.exit == Some(0)
            && self.failure.is_none()
    }
    pub fn cleanup_confirmed(&self) -> bool {
        self.phase == Phase::Reaped && self.result && !self.killed && self.protocol_clean
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn rejected_terminal_protocol_cannot_be_cleaned_by_natural_exit() {
        let id = Selection { generation: 9, mode: 4 }.tag(10401, &"a".repeat(64));
        let result = [id.as_slice(), b"{\"cleanup_confirmed\":true}"].concat();
        let mut s = Session::default();
        s.helper(12, &result, &id, 10).unwrap();
        let failure = s.helper(12, &result, &id, 11).unwrap_err();
        s.reject(11, failure);
        s.reaped(0);
        assert!(!s.cleanup_confirmed());
        assert!(!s.success());
        s.stop(12, None);
        assert!(!s.cleanup_confirmed());
    }
    #[test]
    fn partial_terminal_frame_and_control_rejection_latch_cleanup_uncertain() {
        let mut decoder = Decoder::default();
        decoder.push(&[1]).unwrap();
        assert!(decoder.eof().is_err());
        for cause in ["terminal_helper_protocol", "app_protocol", "control_payload", "missing_terminal_helper_frame"] {
            let mut s = Session { result: true, ..Session::default() };
            s.reject(1, cause);
            s.reaped(0);
            assert!(!s.cleanup_confirmed(), "{cause}");
        }
    }
    #[test]
    fn clean_helper_failure_or_eof_is_not_confused_with_protocol_corruption() {
        for cause in [None, Some("app_eof"), Some("revoked")] {
            let mut s = Session { result: true, ..Session::default() };
            s.stop(1, cause);
            s.reaped(4 << 8);
            assert!(s.cleanup_confirmed());
            assert!(!s.success());
            s.killed = true;
            assert!(!s.cleanup_confirmed());
        }
        let mut s = Session::default();
        s.reaped(0);
        assert!(!s.cleanup_confirmed());
    }
    #[test]
    fn stereo_fixed_modes() {
        for mode in [4u32, 5] {
            let payload = [7u64.to_le_bytes().as_slice(), mode.to_le_bytes().as_slice()].concat();
            assert_eq!(Selection::parse(20, &payload, true).unwrap().mode, mode);
            assert!(Selection::parse(20, &payload, false).is_err());
            let selection = Selection { generation: 7, mode };
            assert!(!selection.stream());
            let id = selection.tag(10401, &"a".repeat(64));
            let mut s = Session { selection, phase: Phase::Capture(0), ..Session::default() };
            let p = [id.as_slice(), 0u64.to_le_bytes().as_slice()].concat();
            assert!(s.helper(16, &p, &id, 250).is_ok());
            assert!(s.helper(15, &p, &id, 251).is_err());
            assert!(s.helper(16, &p, &id, 252).is_err());
            s.heartbeat = 5500;
            s.tick(5500);
            assert_eq!(s.failure, Some("helper_progress_timeout"));
        }
    }
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
        assert!(s.app(2, &1u64.to_le_bytes(), 1).is_err());
        s.helper(11, &p, &id, 1).unwrap();
        assert!(s.helper(11, &p, &id, 2).is_err());
        assert!(s.app(2, b"x", 2).is_err());
        assert!(s.app(2, &1u64.to_le_bytes(), 2).unwrap());
        assert!(s.app(2, &1u64.to_le_bytes(), 3).is_err());
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
        s.app(2, &1u64.to_le_bytes(), 1).unwrap();
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

#[cfg(test)]
mod product_tests {
    use super::*;
    const BUILD: &str = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    fn select(mode: u32) -> Selection {
        Selection {
            generation: 42,
            mode,
        }
    }
    fn session(mode: u32) -> (Session, Vec<u8>) {
        let selection = select(mode);
        let id = selection.tag(10401, BUILD);
        let mut s = Session {
            selection,
            ..Session::default()
        };
        s.helper(11, &[id.clone(), br#"{"pcm_epoch_schema":1}"#.to_vec()].concat(), &id, 0)
            .unwrap();
        if selection.stream() {
            s.app(4, &[42u64.to_le_bytes(), 1u64.to_le_bytes(), 0u64.to_le_bytes()].concat(), 0).unwrap();
            let ack = [id.clone(), [1u64.to_le_bytes(), 0u64.to_le_bytes(), 0u64.to_le_bytes()].concat()].concat();
            s.helper(17, &ack, &id, 0).unwrap();
        }
        s.app(2, &42u64.to_le_bytes(), 1).unwrap();
        (s, id)
    }
    fn pcm(id: &[u8], seq: u64, count: u32) -> Vec<u8> {
        [
            id.to_vec(),
            seq.to_le_bytes().to_vec(),
            16000u32.to_le_bytes().to_vec(),
            1u32.to_le_bytes().to_vec(),
            2u32.to_le_bytes().to_vec(),
            count.to_le_bytes().to_vec(),
            1u64.to_le_bytes().to_vec(),
            0u64.to_le_bytes().to_vec(),
            vec![0; count as usize * 2],
        ]
        .concat()
    }
    #[test]
    fn release_rejects_fixed_debug_modes_before_grant() {
        for mode in 0u32..7 {
            let p = [
                42u64.to_le_bytes().as_slice(),
                mode.to_le_bytes().as_slice(),
            ]
            .concat();
            assert_eq!(
                Selection::parse(20, &p, false).is_ok(),
                mode == 1 || mode == 2
            );
            assert_eq!(Selection::parse(20, &p, true).is_ok(), mode <= 5);
        }
        assert!(Selection::parse(20, &[0; 12], true).is_err());
        assert!(Selection::parse(1, &[0; 12], true).is_err());
        assert!(Selection::parse(20, &[0; 13], true).is_err());
    }
    #[test]
    fn coalesced_select_leaves_complete_or_partial_control_in_pipe() {
        let selection = frame(
            20,
            &[
                42u64.to_le_bytes().as_slice(),
                2u32.to_le_bytes().as_slice(),
            ]
            .concat(),
        )
        .unwrap();
        let control = frame(1, &42u64.to_le_bytes()).unwrap();
        for next in [1, 5, control.len()] {
            let bytes = [selection.clone(), control[..next].to_vec()].concat();
            let mut decoder = Decoder::default();
            let mut used = 0;
            for b in &bytes {
                used += 1;
                if selection_byte(&mut decoder, *b, false).unwrap().is_some() {
                    break;
                }
            }
            assert_eq!(used, selection.len());
            assert_eq!(&bytes[used..], &control[..next]);
            assert!(decoder.eof().is_ok());
            let mut following = Decoder::default();
            following.push(&bytes[used..]).unwrap();
            following.push(&control[next..]).unwrap();
            assert_eq!(
                following.next_frame().unwrap(),
                Some((1, 42u64.to_le_bytes().to_vec()))
            );
        }
    }
    #[test]
    fn typed_pcm_rejects_stale_partial_oversize_format_and_sequence() {
        let (mut s, id) = session(2);
        let good = pcm(&id, 0, 160);
        s.helper(15, &good, &id, 10).unwrap();
        assert_eq!(s.sequence, 1);
        assert!(s.helper(15, &good, &id, 11).is_err());
        for at in [0, 68, 76, 88, 92, 96, 100] {
            let (mut t, tag) = session(2);
            let mut bad = pcm(&tag, 0, 160);
            bad[at] ^= 1;
            assert!(t.helper(15, &bad, &tag, 12).is_err(), "offset {at}");
        }
        let (mut t, tag) = session(2);
        let p = pcm(&tag, 0, 160);
        assert!(t.helper(15, &p[..p.len() - 1], &tag, 1).is_err());
        assert!(t.helper(15, &pcm(&tag, 0, 161), &tag, 1).is_err());
        assert!(t.helper(15, &pcm(&tag, 0, 0), &tag, 1).is_err());
        assert!(t.app(1, &41u64.to_le_bytes(), 2).is_err());
    }
    #[test]
    fn continuous_lease_outlives_old_absolute_deadline() {
        let (mut s, id) = session(2);
        for i in 0..1000u64 {
            let now = i * 250;
            s.app(1, &42u64.to_le_bytes(), now).unwrap();
            s.helper(
                16,
                &[id.clone(), i.to_le_bytes().to_vec()].concat(),
                &id,
                now,
            )
            .unwrap();
            s.tick(now);
            assert!(matches!(s.phase, Phase::Capture(_)));
        }
        assert_eq!(s.sequence, 0); // Idle is not invented PCM.
        assert_eq!(s.progress_sequence, 1000);
    }
    #[test]
    fn app_heartbeat_does_not_hide_blocked_helper() {
        let (mut s, _) = session(2);
        for now in (250..=3250).step_by(250) {
            s.app(1, &42u64.to_le_bytes(), now).unwrap();
            s.tick(now);
        }
        assert_eq!(s.failure, Some("helper_progress_timeout"));
        assert!(s.should_kill(4250));
        s.reaped(9);
        assert!(!s.should_kill(10000));
    }
    #[test]
    fn finite_feasibility_and_controlled_stream_stay_finite() {
        for mode in [0, 3] {
            let (mut s, id) = session(mode);
            s.app(1, &42u64.to_le_bytes(), 5999).unwrap();
            if mode == 3 {
                s.helper(
                    16,
                    &[id, 0u64.to_le_bytes().to_vec()].concat(),
                    &select(mode).tag(10401, BUILD),
                    5999,
                )
                .unwrap();
            }
            s.tick(6001);
            assert_eq!(s.failure, Some("capture_timeout"));
        }
    }
    #[test]
    fn idle_progress_sequence_cannot_replay_and_stop_does_not_restart() {
        let (mut s, id) = session(2);
        let progress = [id.clone(), 0u64.to_le_bytes().to_vec()].concat();
        s.helper(16, &progress, &id, 200).unwrap();
        assert!(s.helper(16, &progress, &id, 300).is_err());
        assert!(!s.app(3, &42u64.to_le_bytes(), 400).unwrap());
        assert!(!s.app(3, &42u64.to_le_bytes(), 500).unwrap());
        assert!(s.app(2, &42u64.to_le_bytes(), 501).is_err());
        assert_eq!(s.phase, Phase::Stopping(400));
    }
}

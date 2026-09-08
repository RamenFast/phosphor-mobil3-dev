#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Binding {
    pub control_sequence: u64,
    pub epoch: u64,
}
#[derive(Default)]
pub struct RootEpoch {
    pub acknowledged: Option<Binding>,
    pub pending: Option<(Binding, u64)>,
    last_request: Option<u64>,
    next_control: u64,
}
impl RootEpoch {
    pub fn request(
        &mut self,
        payload: &[u8],
        generation: u64,
        now: u64,
    ) -> Result<(), &'static str> {
        if payload.len() != 24 || u64::from_le_bytes(payload[..8].try_into().unwrap()) != generation
        {
            return Err("epoch_control_payload");
        }
        let binding = Binding {
            control_sequence: u64::from_le_bytes(payload[8..16].try_into().unwrap()),
            epoch: u64::from_le_bytes(payload[16..24].try_into().unwrap()),
        };
        let next = if self.next_control == 0 {
            1
        } else {
            self.next_control
        };
        if self.pending.is_some() || binding.control_sequence != next || next >= i64::MAX as u64 {
            return Err("epoch_control_sequence");
        }
        if self
            .acknowledged
            .is_some_and(|old| binding.epoch <= old.epoch)
        {
            return Err("epoch_regression");
        }
        if self
            .last_request
            .is_some_and(|at| now.saturating_sub(at) < 125)
        {
            return Err("epoch_request_rate");
        }
        self.pending = Some((binding, now));
        self.last_request = Some(now);
        self.next_control = next + 1;
        Ok(())
    }
    pub fn ack(&mut self, payload: &[u8], sequence: u64) -> Result<(), &'static str> {
        if payload.len() != 24 {
            return Err("epoch_ack_length");
        }
        let binding = Binding {
            control_sequence: u64::from_le_bytes(payload[..8].try_into().unwrap()),
            epoch: u64::from_le_bytes(payload[8..16].try_into().unwrap()),
        };
        if self.pending.map(|p| p.0) != Some(binding)
            || u64::from_le_bytes(payload[16..24].try_into().unwrap()) != sequence
        {
            return Err("epoch_ack_sequence");
        }
        self.acknowledged = Some(binding);
        self.pending = None;
        Ok(())
    }
    pub fn expired(&self, now: u64) -> bool {
        self.pending
            .is_some_and(|(_, at)| now.saturating_sub(at) >= 1000)
    }
}

/// Accept only one top-level integer capability in the helper's JSON object.
pub fn schema_one(json: &[u8]) -> bool {
    let mut i = 0;
    let mut depth = 0usize;
    let mut found = false;
    while i < json.len() {
        match json[i] {
            b'{' | b'[' => depth += 1,
            b'}' | b']' => {
                if depth == 0 {
                    return false;
                }
                depth -= 1;
            }
            b'"' => {
                let start = i + 1;
                i += 1;
                while i < json.len() && json[i] != b'"' {
                    if json[i] == b'\\' {
                        i += 1;
                    }
                    i += 1;
                }
                if i >= json.len() {
                    return false;
                }
                if depth == 1 && &json[start..i] == b"pcm_epoch_schema" {
                    if found {
                        return false;
                    }
                    found = true;
                    i += 1;
                    while i < json.len() && json[i].is_ascii_whitespace() {
                        i += 1;
                    }
                    if json.get(i) != Some(&b':') {
                        return false;
                    }
                    i += 1;
                    while i < json.len() && json[i].is_ascii_whitespace() {
                        i += 1;
                    }
                    if json.get(i) != Some(&b'1') {
                        return false;
                    }
                    i += 1;
                    while i < json.len() && json[i].is_ascii_whitespace() {
                        i += 1;
                    }
                    if !matches!(json.get(i), Some(b',' | b'}')) {
                        return false;
                    }
                    continue;
                }
            }
            _ => {}
        }
        i += 1;
    }
    found && depth == 0 && json.iter().find(|b| !b.is_ascii_whitespace()) == Some(&b'{')
}

#[cfg(test)]
mod tests {
    use super::*;
    fn request(seq: u64, epoch: u64) -> Vec<u8> {
        [42u64.to_le_bytes(), seq.to_le_bytes(), epoch.to_le_bytes()].concat()
    }
    fn ack(seq: u64, epoch: u64, pcm: u64) -> Vec<u8> {
        [seq.to_le_bytes(), epoch.to_le_bytes(), pcm.to_le_bytes()].concat()
    }
    #[test]
    fn zero_high_bit_rate_pending_deadline_and_exhaustion() {
        let mut e = RootEpoch::default();
        e.request(&request(1, 0), 42, 0).unwrap();
        assert!(e.request(&request(2, 1), 42, 125).is_err());
        assert!(!e.expired(999));
        assert!(e.expired(1000));
        assert!(e.ack(&ack(1, 0, 1), 0).is_err());
        e.ack(&ack(1, 0, 0), 0).unwrap();
        assert!(e.request(&request(2, 1), 42, 124).is_err());
        e.request(&request(2, 1 << 63), 42, 125).unwrap();
        e.ack(&ack(2, 1 << 63, 0), 0).unwrap();
        assert!(e.request(&request(3, 0), 42, 250).is_err());
        assert!(e.request(&request(2, u64::MAX), 42, 250).is_err());
        e.next_control = i64::MAX as u64;
        assert!(e
            .request(&request(i64::MAX as u64, u64::MAX), 42, 250)
            .is_err());
    }
    #[test]
    fn capability_is_top_level_unique_integer_one() {
        assert!(schema_one(br#"{"pcm_epoch_schema":1,"x":{"a":"quoted"}}"#));
        assert!(schema_one(br#"{ "pcm_epoch_schema" : 1 }"#));
        for bad in [
            br#"{}"#.as_slice(),
            br#"{"pcm_epoch_schema":10}"#,
            br#"{"pcm_epoch_schema":"1"}"#,
            br#"{"nested":{"pcm_epoch_schema":1}}"#,
            br#"{"pcm_epoch_schema":1,"pcm_epoch_schema":1}"#,
            br#"{"x":"pcm_epoch_schema"}"#,
            br#"{"pcm_epoch_schema":1.0}"#,
        ] {
            assert!(!schema_one(bad), "{:?}", bad);
        }
    }
}

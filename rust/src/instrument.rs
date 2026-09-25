//! Typed instrument setup and finite CPU admission. No Android or renderer calls here.
use crate::light_cycle::LightSettings;
use serde::Deserialize;
use std::sync::{Arc, Condvar, Mutex};
use std::time::Instant;

pub const MAX_SETUP_BYTES: usize = 16 * 1024;

#[derive(Clone, Debug, Deserialize, PartialEq)]
#[serde(deny_unknown_fields)]
pub struct Setup {
    pub mode: u8,
    pub random_mode_armed: bool,
    pub random_ban_modes: Vec<u8>,
    pub geom_fx: u8,
    pub geom_amount: f32,
    pub gain: f32,
    pub auto_gain: bool,
    pub focus: f32,
    pub beam_energy: f32,
    pub glow: f32,
    pub beam_random_armed: bool,
    pub beam_random_min: f32,
    pub beam_random_max: f32,
    pub glow_random_armed: bool,
    pub glow_random_min: f32,
    pub glow_random_max: f32,
    pub grid: bool,
    pub grid_data: bool,
    pub oversample: u8,
    pub light: Light,
}

#[derive(Clone, Debug, Deserialize, PartialEq)]
#[serde(deny_unknown_fields)]
pub struct Light {
    pub preset: u8,
    pub slots: Vec<[f32; 3]>,
    pub selected_mask: u8,
    pub seconds: f32,
    pub clock: Clock,
    pub generated_auto: bool,
    pub shuffle: bool,
    pub random_interval: bool,
    pub interval_min: f32,
    pub interval_max: f32,
}
#[derive(Clone, Copy, Debug, Deserialize, PartialEq)]
pub enum Clock {
    #[serde(rename = "TIMER")]
    Timer,
    #[serde(rename = "TRACK")]
    Track,
}
impl Light {
    pub fn settings(&self) -> LightSettings {
        LightSettings {
            colors: self.slots.clone(),
            selected_mask: self.selected_mask,
            preset: self.preset,
            seconds: self.seconds,
            per_track: self.clock == Clock::Track,
            generated_auto: self.generated_auto,
            shuffle: self.shuffle,
            random_interval: self.random_interval,
            interval_min: self.interval_min,
            interval_max: self.interval_max,
        }
    }
}
impl Setup {
    pub fn decode(json: &str) -> Result<Self, &'static str> {
        if json.len() > MAX_SETUP_BYTES {
            return Err("Instrument setup exceeds 16 KiB");
        }
        let setup: Self = serde_json::from_str(json)
            .map_err(|_| "Instrument setup fields or types are invalid")?;
        if !setup.valid() {
            return Err("Instrument setup contains an invalid range or selection");
        }
        Ok(setup)
    }
    pub fn valid(&self) -> bool {
        let in_range = |v: f32, lo: f32, hi: f32| v.is_finite() && (lo..=hi).contains(&v);
        self.mode < 11
            && self.geom_fx <= 4
            && self.random_ban_modes.len() <= 11
            && self.random_ban_modes.iter().all(|&i| i < 11)
            && self.random_ban_modes.windows(2).all(|w| w[0] < w[1])
            && (!self.random_mode_armed || self.random_ban_modes.len() <= 9)
            && in_range(self.geom_amount, 0.0, 1.0)
            && in_range(self.gain, 0.1, crate::engine::GAIN_MAX)
            && in_range(self.focus, 0.3, 3.0)
            && in_range(self.beam_energy, 1.0, 30.0)
            && in_range(self.glow, 0.0, 0.98)
            && in_range(self.beam_random_min, 1.0, 30.0)
            && in_range(self.beam_random_max, self.beam_random_min, 30.0)
            && in_range(self.glow_random_min, 0.0, 0.98)
            && in_range(self.glow_random_max, self.glow_random_min, 0.98)
            && matches!(self.oversample, 1 | 2 | 4)
            && self.light.settings().valid()
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Outcome {
    Pending,
    Committed,
    Cancelled,
    Rejected,
}

pub struct Request {
    setup: Setup,
    deadline: Instant,
    outcome: Mutex<Outcome>,
    changed: Condvar,
}
impl Request {
    pub fn new(setup: Setup, deadline: Instant) -> Result<Self, &'static str> {
        if !setup.valid() {
            return Err("Instrument setup is invalid");
        }
        Ok(Self {
            setup,
            deadline,
            outcome: Mutex::new(Outcome::Pending),
            changed: Condvar::new(),
        })
    }
    pub fn outcome(&self) -> Outcome {
        *self.outcome.lock().unwrap()
    }
    pub fn cancel(&self) -> Outcome {
        let mut state = self.outcome.lock().unwrap();
        if *state == Outcome::Pending {
            *state = Outcome::Cancelled;
            self.changed.notify_all();
        }
        *state
    }
    /// Invoke only on the render owner. `apply` is validated, finite CPU assignment only.
    pub fn admit(&self, local_capability: bool, apply: impl FnOnce(&Setup)) -> Outcome {
        let mut state = self.outcome.lock().unwrap();
        if *state != Outcome::Pending {
            return *state;
        }
        if Instant::now() >= self.deadline {
            *state = Outcome::Cancelled;
        } else if !local_capability {
            *state = Outcome::Rejected;
        } else {
            apply(&self.setup);
            *state = Outcome::Committed;
        }
        self.changed.notify_all();
        *state
    }
    /// Call off the UI thread. Expiration cancels only work that has not been admitted.
    pub fn wait(&self) -> Outcome {
        let mut state = self.outcome.lock().unwrap();
        while *state == Outcome::Pending {
            let remaining = self.deadline.saturating_duration_since(Instant::now());
            if remaining.is_zero() {
                *state = Outcome::Cancelled;
                self.changed.notify_all();
                break;
            }
            state = self.changed.wait_timeout(state, remaining).unwrap().0;
        }
        *state
    }
}

#[derive(Default)]
pub struct RequestBook {
    next: u64,
    requests: std::collections::BTreeMap<u64, Arc<Request>>,
}
impl RequestBook {
    pub fn reserve(
        &mut self,
        setup: Setup,
        deadline: Instant,
    ) -> Result<(u64, Arc<Request>), &'static str> {
        if self.requests.len() >= 4 {
            return Err("Finish the pending preset request before another apply");
        }
        let id = self
            .next
            .checked_add(1)
            .filter(|&id| id <= i64::MAX as u64)
            .ok_or("Preset request identity exhausted")?;
        let request = Arc::new(Request::new(setup, deadline)?);
        self.requests.insert(id, request.clone());
        self.next = id;
        Ok((id, request))
    }
    pub fn get(&self, id: u64) -> Option<Arc<Request>> {
        self.requests.get(&id).cloned()
    }
    pub fn release(&mut self, id: u64) -> bool {
        if let Some(request) = self.requests.remove(&id) {
            request.cancel();
            true
        } else {
            false
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::{Value, json};
    use std::sync::{Arc, mpsc};
    use std::time::Duration;

    fn fixture() -> Value {
        json!({"mode":0,"random_mode_armed":false,"random_ban_modes":[],
            "geom_fx":0,"geom_amount":0.6,"gain":1.8332275,"auto_gain":true,"focus":0.3,
            "beam_energy":8.0,"glow":0.2,"beam_random_armed":false,"beam_random_min":6.0,"beam_random_max":20.0,
            "glow_random_armed":false,"glow_random_min":0.3,"glow_random_max":0.9,
            "grid":true,"grid_data":false,"oversample":1,
            "light":{"preset":0,"slots":[],"selected_mask":0,"seconds":3.0,"clock":"TIMER",
                "generated_auto":false,"shuffle":false,"random_interval":false,"interval_min":3.0,"interval_max":6.0}})
    }
    fn setup() -> Setup {
        Setup::decode(&fixture().to_string()).unwrap()
    }
    #[test]
    fn actual_kotlin_setup_vectors_match_native_contract() {
        let clean = Setup::decode(include_str!("testdata/instrument/clean-xy.setup.json")).unwrap();
        let spectral = Setup::decode(include_str!(
            "testdata/instrument/spectral-bench.setup.json"
        ))
        .unwrap();
        let ambient =
            Setup::decode(include_str!("testdata/instrument/ambient.setup.json")).unwrap();
        let six = Setup::decode(include_str!("testdata/instrument/six-slot.setup.json")).unwrap();
        assert_eq!(clean, setup());
        assert_eq!((spectral.mode, spectral.light.preset), (8, 2));
        assert_eq!(
            (
                ambient.mode,
                ambient.light.selected_mask,
                ambient.light.seconds
            ),
            (7, 3, 10.0)
        );
        assert_eq!(
            ambient.light.slots,
            vec![[0.42, 1.0, 0.55], [0.35, 0.75, 1.0]]
        );
        assert_eq!(
            (six.mode, six.oversample, six.light.selected_mask),
            (10, 4, 45)
        );
        assert_eq!(six.light.slots.len(), 6);
        assert_eq!(six.random_ban_modes, vec![0, 2, 4, 6, 8, 10]);
        assert!(six.light.settings().valid());
    }
    fn request() -> Arc<Request> {
        Arc::new(Request::new(setup(), Instant::now() + Duration::from_secs(2)).unwrap())
    }

    #[test]
    fn strict_fields_and_scalar_types() {
        let value = fixture();
        for key in value.as_object().unwrap().keys() {
            let mut bad = value.clone();
            bad.as_object_mut().unwrap().remove(key);
            assert!(Setup::decode(&bad.to_string()).is_err(), "missing {key}");
        }
        for key in value["light"].as_object().unwrap().keys() {
            let mut bad = value.clone();
            bad["light"].as_object_mut().unwrap().remove(key);
            assert!(
                Setup::decode(&bad.to_string()).is_err(),
                "missing light.{key}"
            );
        }
        for (key, replacement) in [
            ("mode", json!(1.0)),
            ("mode", json!("1")),
            ("auto_gain", json!(1)),
            ("gain", json!("2")),
            ("grid", Value::Null),
            ("root_capture_enabled", json!(true)),
        ] {
            let mut bad = value.clone();
            bad[key] = replacement;
            assert!(Setup::decode(&bad.to_string()).is_err(), "wrong {key}");
        }
        let mut bad = value.clone();
        bad["light"]["runtime_ack"] = json!(true);
        assert!(Setup::decode(&bad.to_string()).is_err());
        let duplicate = value.to_string().replacen('{', "{\"mode\":0,", 1);
        assert!(Setup::decode(&duplicate).is_err());
        assert!(Setup::decode(&" ".repeat(MAX_SETUP_BYTES + 1)).is_err());
    }
    #[test]
    fn numeric_ranges_and_mode_eligibility() {
        for (key, lo, hi) in [
            ("geom_amount", 0.0, 1.0),
            ("gain", 0.1, crate::engine::GAIN_MAX),
            ("focus", 0.3, 3.0),
            ("beam_energy", 1.0, 30.0),
            ("glow", 0.0, 0.98),
        ] {
            for value in [lo, hi] {
                let mut s = fixture();
                s[key] = json!(value);
                assert!(Setup::decode(&s.to_string()).is_ok());
            }
            for value in [lo - 0.01, hi + 0.01] {
                let mut s = fixture();
                s[key] = json!(value);
                assert!(Setup::decode(&s.to_string()).is_err());
            }
        }
        for n in [0, 3, 8] {
            let mut s = fixture();
            s["oversample"] = json!(n);
            assert!(Setup::decode(&s.to_string()).is_err());
        }
        for bans in [vec![1, 1], vec![2, 1], vec![11], (0..10).collect()] {
            let mut s = fixture();
            s["random_mode_armed"] = json!(true);
            s["random_ban_modes"] = json!(bans);
            assert!(Setup::decode(&s.to_string()).is_err());
        }
        let mut s = setup();
        s.gain = f32::NAN;
        assert!(!s.valid());
        s = setup();
        s.beam_random_min = 25.0;
        assert!(!s.valid());
        s = setup();
        s.glow_random_min = 0.95;
        assert!(!s.valid());
    }
    #[test]
    fn nested_light_uses_existing_policy() {
        let mut s = fixture();
        s["light"]["slots"] = json!([
            [1, 0, 0],
            [0, 1, 0],
            [0, 0, 1],
            [1, 1, 0],
            [0, 1, 1],
            [1, 0, 1]
        ]);
        s["light"]["selected_mask"] = json!(63);
        s["light"]["clock"] = json!("TRACK");
        let parsed = Setup::decode(&s.to_string()).unwrap();
        assert!(parsed.light.settings().per_track);
        assert_eq!(parsed.light.settings().colors.len(), 6);
        s["light"]["selected_mask"] = json!(64);
        assert!(Setup::decode(&s.to_string()).is_err());
        s["light"]["selected_mask"] = json!(63);
        s["light"]["slots"][0] = json!([1, 0]);
        assert!(Setup::decode(&s.to_string()).is_err());
    }
    #[test]
    fn cancelled_queued_candidate_is_inert() {
        let r = request();
        let worker = r.clone();
        let (tx, rx) = mpsc::channel();
        let join = std::thread::spawn(move || {
            rx.recv_timeout(Duration::from_secs(2)).unwrap();
            worker.admit(true, |_| panic!("late apply"))
        });
        assert_eq!(r.cancel(), Outcome::Cancelled);
        tx.send(()).unwrap();
        assert_eq!(join.join().unwrap(), Outcome::Cancelled);
        assert_eq!(r.wait(), Outcome::Cancelled);
    }
    #[test]
    fn expired_or_rejected_candidate_never_changes_setup() {
        let expired = Request::new(setup(), Instant::now()).unwrap();
        assert_eq!(expired.wait(), Outcome::Cancelled);
        assert_eq!(
            expired.admit(true, |_| panic!("expired apply")),
            Outcome::Cancelled
        );
        let r = request();
        assert_eq!(
            r.admit(false, |_| panic!("geometry-owned apply")),
            Outcome::Rejected
        );
        assert_eq!(r.cancel(), Outcome::Rejected);
    }
    #[test]
    fn complete_tuple_and_lost_reply_keep_exact_commit() {
        let r = request();
        let mut accepted = None;
        assert_eq!(
            r.admit(true, |s| accepted = Some(s.clone())),
            Outcome::Committed
        );
        assert_eq!(accepted, Some(setup()));
        assert_eq!(r.cancel(), Outcome::Committed);
        assert_eq!(r.wait(), Outcome::Committed);
        assert_eq!(
            r.admit(true, |_| panic!("double apply")),
            Outcome::Committed
        );
    }
    #[test]
    fn waiter_observes_committed_candidate() {
        let r = request();
        let waiting = r.clone();
        let join = std::thread::spawn(move || waiting.wait());
        assert_eq!(r.admit(true, |_| {}), Outcome::Committed);
        assert_eq!(join.join().unwrap(), Outcome::Committed);
    }
    #[test]
    fn receipt_release_cancels_only_its_owned_queued_request() {
        let mut book = RequestBook::default();
        let deadline = Instant::now() + Duration::from_secs(2);
        let (a, queued) = book.reserve(setup(), deadline).unwrap();
        let (b, next) = book.reserve(setup(), deadline).unwrap();
        assert!(book.release(a));
        assert!(!book.release(a));
        assert!(book.get(a).is_none());
        assert_eq!(
            queued.admit(true, |_| panic!("released apply")),
            Outcome::Cancelled
        );
        assert_eq!(next.outcome(), Outcome::Pending);
        assert_eq!(book.get(b).unwrap().admit(true, |_| {}), Outcome::Committed);
        assert_eq!(next.wait(), Outcome::Committed);
        assert!(book.release(b));
        assert_eq!(next.cancel(), Outcome::Committed);
    }
    #[test]
    fn bounded_receipt_store_preserves_unconsumed_results() {
        let mut book = RequestBook::default();
        let deadline = Instant::now() + Duration::from_secs(2);
        let mut ids = Vec::new();
        for _ in 0..4 {
            ids.push(book.reserve(setup(), deadline).unwrap().0);
        }
        assert!(book.reserve(setup(), deadline).is_err());
        for id in &ids {
            assert_eq!(book.get(*id).unwrap().outcome(), Outcome::Pending);
        }
        let old = ids[0];
        book.release(old);
        let fresh = book.reserve(setup(), deadline).unwrap().0;
        assert!(fresh > *ids.last().unwrap());
        assert_ne!(old, fresh);
    }
}

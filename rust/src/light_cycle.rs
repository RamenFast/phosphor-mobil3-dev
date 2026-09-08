//! Native light policy. Observation interpolates, boundaries alone consume randomness.
#[derive(Clone, Debug, PartialEq)]
pub struct LightSettings {
    pub colors: Vec<[f32; 3]>, pub selected_mask: u8, pub preset: u8,
    pub seconds: f32, pub per_track: bool, pub generated_auto: bool,
    pub shuffle: bool, pub random_interval: bool, pub interval_min: f32, pub interval_max: f32,
}
impl Default for LightSettings {
    fn default() -> Self {
        Self { colors: vec![], selected_mask: 0, preset: 7, seconds: 3.0, per_track: false,
            generated_auto: false, shuffle: false, random_interval: false, interval_min: 3.0, interval_max: 6.0 }
    }
}
impl LightSettings {
    pub fn valid(&self) -> bool {
        let bounded = |x: f32| x.is_finite() && (0.1..=60.0).contains(&x);
        self.colors.len() <= 6 && (self.selected_mask as usize) >> self.colors.len() == 0
            && self.preset <= 8 && self.colors.iter().flatten().all(|v| v.is_finite() && (0.0..=1.0).contains(v))
            && bounded(self.seconds) && bounded(self.interval_min) && bounded(self.interval_max)
            && self.interval_min <= self.interval_max
    }
    fn ids(&self) -> Vec<usize> {
        (0..self.colors.len()).filter(|i| self.selected_mask & (1 << i) != 0).collect()
    }
}
pub struct LightCycle {
    settings: LightSettings, rng: u64, draws: u64, bag: Vec<usize>, last_id: Option<usize>,
    from: [f32; 3], to: [f32; 3], live: [f32; 3], start: f64, duration: f64,
    suspended: bool, temporary: bool,
}
impl LightCycle {
    pub fn new(seed: u64) -> Self {
        Self { settings: LightSettings::default(), rng: seed.max(1), draws: 0, bag: vec![], last_id: None,
            from: [0.0; 3], to: [0.0; 3], live: [0.0; 3], start: 0.0, duration: 3.0,
            suspended: true, temporary: false }
    }
    pub fn settings(&self) -> &LightSettings { &self.settings }
    pub fn apply(&mut self, settings: LightSettings, now: f64) -> bool {
        self.apply_edit(settings, now, None)
    }
    pub fn apply_edit(&mut self, settings: LightSettings, now: f64, deleted: Option<usize>) -> bool {
        if !settings.valid() || !now.is_finite() { return false; }
        if let Some(index) = deleted {
            if index >= self.settings.colors.len() { return false; }
            let mut remaining = self.settings.colors.clone();
            remaining.remove(index);
            let old = self.settings.selected_mask;
            let shifted = (old & ((1 << index) - 1)) | ((old >> (index + 1)) << index);
            if remaining != settings.colors || shifted != settings.selected_mask { return false; }
            self.last_id = self.last_id.and_then(|last| {
                if last == index { None } else { Some(last - usize::from(last > index)) }
            });
        } else if !self.temporary && self.same_owner(&settings) {
            // Timing changes take effect at the next leg, not as invented track events.
            self.settings = settings;
            return true;
        }
        self.settings = settings;
        self.bag.clear(); self.temporary = false; self.start = now; self.suspended = true;
        if self.custom() {
            self.from = self.next_color(); self.live = self.from; self.to = self.from;
            if !self.settings.per_track && self.moving() { self.begin_leg(now); }
        }
        true
    }
    fn same_owner(&self, next: &LightSettings) -> bool {
        self.settings.per_track == next.per_track
            && self.settings.generated_auto == next.generated_auto
            && (next.generated_auto || (self.settings.colors == next.colors
                && self.settings.selected_mask == next.selected_mask
                && self.settings.shuffle == next.shuffle
                && (next.selected_mask != 0 || self.settings.preset == next.preset)))
    }
    pub fn suspend(&mut self) { self.suspended = true; }
    fn custom(&self) -> bool { self.temporary || self.settings.generated_auto || self.settings.selected_mask != 0 }
    fn moving(&self) -> bool { self.settings.generated_auto || self.settings.selected_mask.count_ones() >= 2 }
    fn unit(&mut self) -> f32 {
        self.rng ^= self.rng << 13; self.rng ^= self.rng >> 7; self.rng ^= self.rng << 17;
        self.draws += 1;
        ((self.rng >> 40) as u32) as f32 / 16_777_215.0
    }
    fn generated(&mut self) -> [f32; 3] {
        let h = self.unit() * 6.0;
        let s = 0.65 + self.unit() * 0.35;
        let x = 1.0 - s * ((h % 2.0) - 1.0).abs();
        let m = 1.0 - s;
        match (h as u32) % 6 {
            0 => [1.0, x, m], 1 => [x, 1.0, m], 2 => [m, 1.0, x],
            3 => [m, x, 1.0], 4 => [x, m, 1.0], _ => [1.0, m, x],
        }
    }
    fn next_color(&mut self) -> [f32; 3] {
        if self.settings.generated_auto { return self.generated(); }
        let ids = self.settings.ids();
        if ids.is_empty() { return self.live; }
        let id = if self.settings.shuffle {
            if self.bag.is_empty() {
                self.bag = ids;
                for i in (1..self.bag.len()).rev() {
                    let j = (self.unit() * (i + 1) as f32) as usize;
                    self.bag.swap(i, j.min(i));
                }
                let end = self.bag.len() - 1;
                if end > 0 && Some(self.bag[end]) == self.last_id { self.bag.swap(0, end); }
            }
            self.bag.pop().unwrap()
        } else {
            self.last_id.and_then(|last| ids.iter().position(|i| *i == last))
                .map(|pos| ids[(pos + 1) % ids.len()]).unwrap_or(ids[0])
        };
        self.last_id = Some(id); self.settings.colors[id]
    }
    fn begin_leg(&mut self, now: f64) {
        self.start = now; self.to = self.next_color();
        self.duration = if self.settings.random_interval && self.settings.interval_min != self.settings.interval_max {
            (self.settings.interval_min + self.unit() * (self.settings.interval_max - self.settings.interval_min)) as f64
        } else if self.settings.random_interval { self.settings.interval_min as f64 } else { self.settings.seconds as f64 };
    }
    pub fn roll(&mut self, now: f64) {
        self.live = self.generated(); self.from = self.live; self.to = self.live;
        self.temporary = !self.settings.generated_auto; self.start = now;
        if self.settings.generated_auto && !self.settings.per_track { self.begin_leg(now); }
    }
    pub fn track(&mut self, now: f64) {
        if self.settings.per_track && self.moving() && !self.temporary {
            self.live = self.next_color(); self.from = self.live; self.to = self.live; self.start = now;
        }
    }
    pub fn observe(&mut self, now: f64) -> Option<([f32; 3], [f32; 3])> {
        if !self.custom() { return None; }
        if self.suspended { self.from = self.live; self.start = now; self.suspended = false; }
        if !self.settings.per_track && self.moving() && !self.temporary {
            let elapsed = (now - self.start).max(0.0);
            if elapsed >= self.duration {
                // One boundary at most. Hidden time cannot drain an unbounded bag.
                self.from = if elapsed >= self.duration * 2.0 { self.live } else { self.to };
                self.begin_leg(now);
            }
            let t = ((now - self.start) / self.duration).clamp(0.0, 1.0) as f32;
            let t = t * t * (3.0 - 2.0 * t);
            self.live = std::array::from_fn(|i| self.from[i] + (self.to[i] - self.from[i]) * t);
        }
        let grid = if self.settings.generated_auto || self.temporary { self.live } else { self.settings.colors[0] };
        Some((self.live, grid.map(|v| v * 0.85)))
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    fn six() -> LightSettings { LightSettings { colors: (0..6).map(|i| [i as f32 / 6.0, 1.0, 0.0]).collect(), selected_mask: 63, ..LightSettings::default() } }
    #[test] fn timer_interpolates_without_track_events_and_rebases_long_gaps() {
        let mut c = LightCycle::new(55);
        c.apply(six(), 0.0);
        let start = c.observe(0.0).unwrap().0;
        let target = c.to;
        let middle = c.observe(1.5).unwrap().0;
        assert_eq!(middle, std::array::from_fn(|i| (start[i] + target[i]) * 0.5));
        let id = c.last_id;
        c.track(2.0); assert_eq!(id, c.last_id);
        assert_eq!(middle, c.observe(1e9).unwrap().0);
    }
    #[test] fn equal_interval_endpoints_consume_no_duration_draws() {
        let mut a = LightCycle::new(8);
        a.apply(LightSettings { random_interval: true, interval_min: 0.1, interval_max: 0.1, ..six() }, 0.0);
        assert_eq!(a.draws, 0);
        assert_eq!(a.duration, 0.1f32 as f64);
        a.observe(0.0); a.observe(0.2); assert_eq!(a.draws, 0);
    }
    #[test] fn membership_reset_avoids_previous_identity_in_track_shuffle() {
        let mut c = LightCycle::new(92);
        let settings = LightSettings { shuffle: true, per_track: true, ..six() };
        c.apply(settings.clone(), 0.0);
        for i in 0..100 {
            let previous = c.last_id;
            let mut changed = settings.clone();
            changed.selected_mask = if i % 2 == 0 { 31 } else { 63 };
            c.apply(changed, i as f64);
            assert_ne!(previous, c.last_id);
        }
    }
    #[test] fn redundant_track_and_timer_apply_preserve_leg_and_rng() {
        for per_track in [false, true] {
            let mut c = LightCycle::new(1);
            let settings = LightSettings { per_track, shuffle: true, ..six() };
            c.apply(settings.clone(), 0.0);
            c.observe(0.0);
            let before = (c.live, c.to, c.start, c.last_id, c.draws, c.bag.clone());
            c.apply(settings.clone(), 1.0);
            assert_eq!(before, (c.live, c.to, c.start, c.last_id, c.draws, c.bag.clone()));
            c.apply(LightSettings { seconds: 8.0, ..settings }, 2.0);
            assert_eq!(before, (c.live, c.to, c.start, c.last_id, c.draws, c.bag.clone()));
        }
    }
    #[test] fn explicit_deletion_remaps_duplicate_rgb_slot_identity() {
        for duplicate in [false, true] {
            let mut c = LightCycle::new(1);
            let colors = if duplicate { vec![[1.0, 0.0, 0.0]; 3] }
                else { vec![[1.0, 0.0, 0.0], [0.0, 1.0, 0.0], [0.0, 0.0, 1.0]] };
            let mut settings = LightSettings { colors, selected_mask: 7, per_track: true, shuffle: true, ..LightSettings::default() };
            c.apply(settings.clone(), 0.0);
            c.track(1.0);
            assert_eq!(c.last_id, Some(2));
            settings.colors.remove(0); settings.selected_mask = 3;
            assert!(c.apply_edit(settings, 2.0, Some(0)));
            assert_eq!(c.last_id, Some(0), "surviving previous slot is now 1, even with duplicate RGB");
        }
    }
    #[test] fn invalid_deletion_does_not_mutate_settings_or_identity() {
        let mut c = LightCycle::new(1);
        c.apply(six(), 0.0);
        let before = (c.settings.clone(), c.last_id, c.draws);
        assert!(!c.apply_edit(six(), 1.0, Some(0)));
        assert!(!c.apply_edit(six(), 1.0, Some(6)));
        assert_eq!(before, (c.settings.clone(), c.last_id, c.draws));
    }
    #[test] fn automatic_owner_preserves_saved_slots_and_track_roll_steps_once() {
        let mut c = LightCycle::new(18);
        let settings = LightSettings { generated_auto: true, per_track: true, ..six() };
        c.apply(settings.clone(), 0.0);
        let first = c.observe(0.0);
        let draws = c.draws;
        assert_eq!(first, c.observe(999.0)); assert_eq!(draws, c.draws);
        c.roll(1000.0); assert_ne!(first, c.observe(1000.0));
        assert_eq!(&settings, c.settings());
        let draws = c.draws; c.track(1001.0); assert_eq!(draws + 2, c.draws);
        let rgb = c.observe(1001.0).unwrap().0;
        assert!(rgb.iter().copied().fold(1.0, f32::min) <= 0.35);
        c.apply(LightSettings { generated_auto: false, ..settings }, 1002.0);
        let rgb = c.observe(1002.0).unwrap().0; assert!(c.settings().colors.contains(&rgb));
    }
    #[test] fn validates_whole_tuple_without_mutation() {
        let mut cycle = LightCycle::new(2);
        let invalid = LightSettings { interval_min: f32::NAN, ..six() };
        assert!(!cycle.apply(invalid, 0.0)); assert_eq!(cycle.settings(), &LightSettings::default());
        assert!(!LightSettings { selected_mask: 1, ..LightSettings::default() }.valid());
        assert!(!LightSettings { interval_min: 7.0, interval_max: 6.0, ..six() }.valid());
    }
    #[test] fn track_holds_and_steps_without_initial_fade() {
        let mut c = LightCycle::new(7); c.apply(LightSettings { per_track: true, ..six() }, 0.0);
        let first = c.observe(0.0).unwrap().0; assert_eq!(first, c.observe(1000.0).unwrap().0);
        c.suspend(); c.track(1001.0); assert_ne!(first, c.observe(2000.0).unwrap().0);
    }
    #[test] fn seeded_generated_duration_and_no_observation_draws() {
        let settings = LightSettings { generated_auto: true, random_interval: true, interval_min: 0.1, interval_max: 0.9, ..six() };
        let (mut a, mut b) = (LightCycle::new(19), LightCycle::new(19));
        a.apply(settings.clone(), 0.0); b.apply(settings, 0.0);
        for leg in 0..100 {
            let now = leg as f64; assert_eq!(a.observe(now), b.observe(now));
            assert!((0.1..=0.9).contains(&(a.duration as f32))); let draws = a.draws;
            for _ in 0..10 { a.observe(now + 0.001); }
            assert_eq!(draws, a.draws); assert_eq!(a.observe(now + 0.001), b.observe(now + 0.001));
            assert!(a.to.iter().all(|v| (0.0..=1.0).contains(v)));
            assert_eq!(a.to.iter().copied().fold(0.0, f32::max), 1.0);
        }
    }
    #[test] fn bags_cover_duplicate_rgb_identities_and_never_repeat_at_refill() {
        let mut c = LightCycle::new(83);
        c.apply(LightSettings { colors: vec![[1.0; 3]; 6], selected_mask: 63, shuffle: true, per_track: true, ..LightSettings::default() }, 0.0);
        let mut last = None;
        for _ in 0..40 {
            let mut bag = vec![];
            for _ in 0..6 {
                let id = c.last_id.unwrap(); assert_ne!(last, Some(id));
                bag.push(id); last = Some(id); c.track(0.0);
            }
            bag.sort(); assert_eq!(bag, vec![0, 1, 2, 3, 4, 5]);
        }
    }
    #[test] fn suspend_rebases_without_draws_and_roll_is_not_saved() {
        let mut c = LightCycle::new(4); let settings = six(); c.apply(settings.clone(), 0.0);
        c.observe(0.0); let before = c.observe(1.0); let draws = c.draws;
        c.suspend(); assert_eq!(before, c.observe(1e9)); assert_eq!(draws, c.draws);
        c.roll(1e9); let rolled = c.observe(1e9); assert_eq!(rolled, c.observe(2e9));
        assert_eq!(&settings, c.settings()); c.apply(settings, 2e9); assert!(!c.temporary);
    }
    #[test] fn grid_uses_first_saved_even_when_unselected_and_one_is_solid() {
        let mut c = LightCycle::new(1); c.apply(LightSettings { selected_mask: 32, ..six() }, 0.0);
        let first = c.observe(0.0).unwrap(); assert_eq!(first.1, [0.0, 0.85, 0.0]);
        assert_eq!(first, c.observe(600.0).unwrap()); c.apply(LightSettings::default(), 0.0);
        assert_eq!(None, c.observe(0.0));
    }
}

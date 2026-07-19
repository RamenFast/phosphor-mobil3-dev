//! Lock-free SPSC audio ring — the first implementation of the house design
//! (`../phosphor/docs/dev/SPSC-RING-DESIGN.md` §1 core + §4 playback pattern).
//!
//! Contract: fixed power-of-two capacity, MONOTONIC cache-padded indices masked
//! on access (`write_pos - read_pos` is the fill level, no full-flag), producer
//! copies then `store(Release)`, consumer copies then `store(Release)`, each
//! side `load(Acquire)`s the other — the standard SPSC proof. Two-segment
//! `copy_from_slice` for wraparound, whole-stereo-frame granularity, and
//! **no alloc / no lock / no syscall / no logging on either hot path**. The
//! consumer side is what runs on the oboe real-time callback (audit finding 10:
//! the Mutex+Condvar AudibleRing priority-inverted there). Backpressure is
//! producer-side `park_timeout` (self-waking — close() needs no unpark);
//! `skip_to_latest` is the consumer-side catch-up jump (index math only,
//! RT-legal) that makes accumulated latency structurally impossible.
//! `AdaptiveJitter` is the other half of that consumer policy: pure integer
//! math which widens after callback underruns and earns latency back only after
//! minutes of clean frames. It deliberately owns no clock, allocation, lock,
//! or logging surface, so the oboe callback can drive it directly.
//!
//! Single-producer/single-consumer is enforced by usage at the seam
//! (`remote::AudioSink`/`AudioTap`, both !Clone): the oboe restart supervisor
//! legitimately mints one tap per reopened stream, and streams never run
//! concurrently (install happens only after AAudio closed the predecessor).

use std::cell::UnsafeCell;
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, AtomicU8, AtomicU64, AtomicUsize, Ordering};
use std::time::Duration;

/// Interleaved stereo: every transfer is rounded down to whole frames.
pub const FRAME: usize = 2;

const CHUNK_FREE: u8 = 0;
const CHUNK_WRITING: u8 = 1;
const CHUNK_READY: u8 = 2;
const CHUNK_READING: u8 = 3;

pub const LATENCY_MODE_TIGHT: u8 = 0;
pub const LATENCY_MODE_BALANCED: u8 = 1;
pub const LATENCY_MODE_SAFE: u8 = 2;

const TIGHT_TARGET_MS: usize = 80;
const BALANCED_TARGET_MS: usize = 150;
const SAFE_TARGET_MS: usize = 250;
const CATCHUP_HEADROOM_MS: usize = 100;
const CATCHUP_SUSTAIN_MS: usize = 250;
const WIDEN_STEP_MS: usize = 40;
const SHRINK_STEP_MS: usize = 20;
const CLEAN_SHRINK_MS: usize = 2 * 60 * 1000;

/// RT-local adaptive jitter policy. Every duration is converted to frames once;
/// callback work is then bounded integer comparison/addition only.
pub struct AdaptiveJitter {
    rate: usize,
    mode: u8,
    floor_frames: usize,
    safe_frames: usize,
    headroom_frames: usize,
    sustain_frames: usize,
    widen_step_frames: usize,
    shrink_step_frames: usize,
    clean_shrink_frames: u64,
    target_frames: usize,
    high_streak_frames: usize,
    clean_frames: u64,
}

impl AdaptiveJitter {
    fn frames(rate: usize, ms: usize) -> usize {
        rate.saturating_mul(ms) / 1000
    }

    pub fn normalize_mode(mode: u8) -> u8 {
        match mode {
            LATENCY_MODE_TIGHT | LATENCY_MODE_BALANCED => mode,
            _ => LATENCY_MODE_SAFE,
        }
    }

    pub fn floor_frames(rate: usize, mode: u8) -> usize {
        let ms = match Self::normalize_mode(mode) {
            LATENCY_MODE_TIGHT => TIGHT_TARGET_MS,
            LATENCY_MODE_BALANCED => BALANCED_TARGET_MS,
            _ => SAFE_TARGET_MS,
        };
        Self::frames(rate, ms)
    }

    /// `resume_target_frames` preserves learned headroom across an oboe route
    /// reopen. A mode change is different: `set_mode` deliberately starts at
    /// that mode's requested floor.
    pub fn new(rate: usize, mode: u8, resume_target_frames: usize) -> Self {
        let rate = rate.max(1);
        let mode = Self::normalize_mode(mode);
        let floor_frames = Self::floor_frames(rate, mode);
        let safe_frames = Self::frames(rate, SAFE_TARGET_MS);
        let target_frames = if mode == LATENCY_MODE_SAFE {
            safe_frames
        } else {
            resume_target_frames.clamp(floor_frames, safe_frames)
        };
        Self {
            rate,
            mode,
            floor_frames,
            safe_frames,
            headroom_frames: Self::frames(rate, CATCHUP_HEADROOM_MS),
            sustain_frames: Self::frames(rate, CATCHUP_SUSTAIN_MS),
            widen_step_frames: Self::frames(rate, WIDEN_STEP_MS),
            shrink_step_frames: Self::frames(rate, SHRINK_STEP_MS),
            clean_shrink_frames: Self::frames(rate, CLEAN_SHRINK_MS) as u64,
            target_frames,
            high_streak_frames: 0,
            clean_frames: 0,
        }
    }

    pub fn mode(&self) -> u8 {
        self.mode
    }

    pub fn target_frames(&self) -> usize {
        self.target_frames
    }

    pub fn high_frames(&self) -> usize {
        self.target_frames.saturating_add(self.headroom_frames)
    }

    /// Apply a user mode change at the next callback. Re-selecting the current
    /// mode retains its learned target; changing modes starts at the new floor.
    pub fn set_mode(&mut self, mode: u8) {
        let mode = Self::normalize_mode(mode);
        if mode == self.mode {
            return;
        }
        self.mode = mode;
        self.floor_frames = Self::floor_frames(self.rate, mode);
        self.target_frames = self.floor_frames;
        self.high_streak_frames = 0;
        self.clean_frames = 0;
    }

    /// Sustained catch-up gate. Returns the newest-frame count to retain when
    /// one cut is due; the caller performs the ring's consumer-index jump.
    pub fn catch_up(&mut self, buffered_frames: usize, frames_this_cb: usize) -> Option<usize> {
        if buffered_frames > self.high_frames() {
            self.high_streak_frames = self.high_streak_frames.saturating_add(frames_this_cb);
            if self.high_streak_frames >= self.sustain_frames {
                self.high_streak_frames = 0;
                return Some(self.target_frames);
            }
        } else {
            self.high_streak_frames = 0;
        }
        None
    }

    /// One callback had to zero-fill because the ring could not satisfy it.
    /// Safe is frozen; adaptive modes widen quickly up to safe's 250 ms target.
    pub fn observe_underrun(&mut self) {
        self.clean_frames = 0;
        self.high_streak_frames = 0;
        if self.mode != LATENCY_MODE_SAFE {
            self.target_frames = self
                .target_frames
                .saturating_add(self.widen_step_frames)
                .min(self.safe_frames);
        }
    }

    /// A fully supplied callback. Two clean minutes earn one conservative
    /// 20 ms shrink; any intervening underrun restarts the clean interval.
    pub fn observe_clean(&mut self, frames_this_cb: usize) {
        if self.mode == LATENCY_MODE_SAFE || self.target_frames <= self.floor_frames {
            self.clean_frames = 0;
            return;
        }
        self.clean_frames = self.clean_frames.saturating_add(frames_this_cb as u64);
        if self.clean_frames >= self.clean_shrink_frames {
            self.clean_frames -= self.clean_shrink_frames;
            self.target_frames = self
                .target_frames
                .saturating_sub(self.shrink_step_frames)
                .max(self.floor_frames);
            if self.target_frames == self.floor_frames {
                self.clean_frames = 0;
            }
        }
    }
}

#[repr(align(64))]
struct CachePadded<T>(T);

pub struct BlockRing {
    buf: Box<[UnsafeCell<f32>]>,
    mask: usize,
    write_pos: CachePadded<AtomicUsize>,
    read_pos: CachePadded<AtomicUsize>,
    closed: AtomicBool,
    /// Catch-up events (skip_to_latest fired) and frames dropped by them —
    /// Relaxed counters, safe to bump from the RT thread.
    pub skips: AtomicU64,
    pub skipped_frames: AtomicU64,
}

// SAFETY: the buffer is only ever written by the single producer in
// [read_pos, write_pos+free) and only ever read by the single consumer in
// [read_pos, write_pos); the Acquire/Release protocol on the indices makes the
// producer's writes visible before the consumer reads them and vice versa.
// Single-producer/single-consumer discipline is upheld by the owning seam.
unsafe impl Send for BlockRing {}
unsafe impl Sync for BlockRing {}

impl BlockRing {
    /// Capacity in FRAMES, rounded up to a power of two of samples.
    pub fn new(capacity_frames: usize) -> Arc<BlockRing> {
        let samples = (capacity_frames.max(64) * FRAME).next_power_of_two();
        let buf: Box<[UnsafeCell<f32>]> = (0..samples).map(|_| UnsafeCell::new(0.0)).collect();
        Arc::new(BlockRing {
            buf,
            mask: samples - 1,
            write_pos: CachePadded(AtomicUsize::new(0)),
            read_pos: CachePadded(AtomicUsize::new(0)),
            closed: AtomicBool::new(false),
            skips: AtomicU64::new(0),
            skipped_frames: AtomicU64::new(0),
        })
    }

    pub fn close(&self) {
        self.closed.store(true, Ordering::SeqCst);
    }

    pub fn is_closed(&self) -> bool {
        self.closed.load(Ordering::SeqCst)
    }

    pub fn capacity_samples(&self) -> usize {
        self.buf.len()
    }

    /// Approximate fill (Relaxed on both indices — instrumentation only).
    pub fn buffered_frames(&self) -> usize {
        let w = self.write_pos.0.load(Ordering::Relaxed);
        let r = self.read_pos.0.load(Ordering::Relaxed);
        w.wrapping_sub(r) / FRAME
    }

    /// PRODUCER ONLY. Blocking push of whole frames; parks in ≤`slice` steps
    /// while full; returns false once closed (teardown's exit). Partial input
    /// tails (< one frame) are ignored by contract.
    pub fn push_blocking(&self, samples: &[f32]) -> bool {
        let want = samples.len() - (samples.len() % FRAME);
        let mut done = 0;
        let slice = Duration::from_micros(1500);
        while done < want {
            if self.is_closed() {
                return false;
            }
            let w = self.write_pos.0.load(Ordering::Relaxed);
            let r = self.read_pos.0.load(Ordering::Acquire);
            let free = self.buf.len() - w.wrapping_sub(r);
            if free == 0 {
                std::thread::park_timeout(slice); // self-waking backpressure
                continue;
            }
            let n = free.min(want - done);
            let start = w & self.mask;
            let first = n.min(self.buf.len() - start);
            // SAFETY: producer-exclusive region [w, w+free); see impl Sync note.
            unsafe {
                let dst = self.buf.as_ptr() as *mut f32;
                std::ptr::copy_nonoverlapping(samples.as_ptr().add(done), dst.add(start), first);
                if n > first {
                    std::ptr::copy_nonoverlapping(
                        samples.as_ptr().add(done + first),
                        dst,
                        n - first,
                    );
                }
            }
            self.write_pos.0.store(w.wrapping_add(n), Ordering::Release);
            done += n;
        }
        true
    }

    /// CONSUMER ONLY — RT-safe (no alloc/lock/syscall/log). Fills `out` with
    /// whole frames, returns samples written; the caller zero-fills the rest.
    pub fn pop_into(&self, out: &mut [f32]) -> usize {
        let want = out.len() - (out.len() % FRAME);
        let r = self.read_pos.0.load(Ordering::Relaxed);
        let w = self.write_pos.0.load(Ordering::Acquire);
        let avail = w.wrapping_sub(r);
        let n = avail.min(want);
        if n == 0 {
            return 0;
        }
        let n = n - (n % FRAME);
        let start = r & self.mask;
        let first = n.min(self.buf.len() - start);
        // SAFETY: consumer-exclusive region [r, w); see impl Sync note.
        unsafe {
            let src = self.buf.as_ptr() as *const f32;
            std::ptr::copy_nonoverlapping(src.add(start), out.as_mut_ptr(), first);
            if n > first {
                std::ptr::copy_nonoverlapping(src, out.as_mut_ptr().add(first), n - first);
            }
        }
        self.read_pos.0.store(r.wrapping_add(n), Ordering::Release);
        n
    }

    /// CONSUMER ONLY — RT-safe catch-up: if more than `keep_frames` are
    /// buffered, jump the read index so exactly `keep_frames` remain (newest
    /// audio wins). Returns frames dropped. One glitch, then live again —
    /// accumulated network lag can never persist (the "lagging behind" killer).
    pub fn skip_to_latest(&self, keep_frames: usize) -> usize {
        let r = self.read_pos.0.load(Ordering::Relaxed);
        let w = self.write_pos.0.load(Ordering::Acquire);
        let avail_frames = w.wrapping_sub(r) / FRAME;
        if avail_frames <= keep_frames {
            return 0;
        }
        let drop_frames = avail_frames - keep_frames;
        self.read_pos
            .0
            .store(r.wrapping_add(drop_frames * FRAME), Ordering::Release);
        self.skips.fetch_add(1, Ordering::Relaxed);
        self.skipped_frames
            .fetch_add(drop_frames as u64, Ordering::Relaxed);
        drop_frames
    }
}

/// Preallocated lossy SPSC for samples finalized by the audio callback and
/// consumed by the non-RT scope worker. Slots carry an explicit ownership
/// state so a full producer can replace the oldest READY chunk without ever
/// overwriting the one chunk the consumer may currently be reading.
///
/// The producer path is bounded scans + CAS + memcpy only: no allocation,
/// lock, syscall, logging, or wait. Visual history is disposable, so a full
/// ring drops the oldest unclaimed chunk rather than delaying audible audio.
pub struct ScopeChunkRing {
    slots: Box<[ScopeChunkSlot]>,
    chunk_samples: usize,
    next_seq: AtomicU64,
    dropped_chunks: AtomicU64,
}

struct ScopeChunkSlot {
    samples: Box<[UnsafeCell<f32>]>,
    len: AtomicUsize,
    seq: AtomicU64,
    state: AtomicU8,
}

// SAFETY: WRITING and READING are mutually exclusive slot ownership states.
// The producer publishes samples with READY/Release; the consumer claims with
// Acquire before copying and publishes FREE/Release afterward. The producer
// may replace READY, but can never claim READING, so the backing f32 cells are
// never accessed concurrently.
unsafe impl Send for ScopeChunkRing {}
unsafe impl Sync for ScopeChunkRing {}

impl ScopeChunkRing {
    pub fn new(chunk_count: usize, chunk_samples: usize) -> Arc<Self> {
        let chunk_count = chunk_count.max(2);
        let chunk_samples = (chunk_samples.max(FRAME) / FRAME) * FRAME;
        let slots = (0..chunk_count)
            .map(|_| ScopeChunkSlot {
                samples: (0..chunk_samples).map(|_| UnsafeCell::new(0.0)).collect(),
                len: AtomicUsize::new(0),
                seq: AtomicU64::new(0),
                state: AtomicU8::new(CHUNK_FREE),
            })
            .collect();
        Arc::new(Self {
            slots,
            chunk_samples,
            next_seq: AtomicU64::new(1),
            dropped_chunks: AtomicU64::new(0),
        })
    }

    pub fn sink(self: &Arc<Self>) -> ScopeChunkSink {
        ScopeChunkSink {
            ring: self.clone(),
            hint: 0,
        }
    }

    pub fn tap(self: &Arc<Self>) -> ScopeChunkTap {
        ScopeChunkTap { ring: self.clone() }
    }

    pub fn chunk_samples(&self) -> usize {
        self.chunk_samples
    }

    pub fn dropped_chunks(&self) -> u64 {
        self.dropped_chunks.load(Ordering::Relaxed)
    }
}

/// Single producer endpoint, owned by the active oboe callback.
pub struct ScopeChunkSink {
    ring: Arc<ScopeChunkRing>,
    hint: usize,
}

impl ScopeChunkSink {
    /// RT-safe, non-blocking publish. Inputs beyond the preallocated chunk size
    /// are truncated at whole-stereo-frame granularity.
    pub fn push(&mut self, samples: &[f32]) {
        let want = samples.len().min(self.ring.chunk_samples);
        let want = want - (want % FRAME);
        if want == 0 {
            return;
        }

        let mut chosen = None;
        let mut replaced = false;

        // Prefer a free slot near the last publish. CAS matters because the
        // consumer can release a slot while this bounded scan is in flight.
        for offset in 0..self.ring.slots.len() {
            let i = (self.hint + offset) % self.ring.slots.len();
            if self.ring.slots[i]
                .state
                .compare_exchange(
                    CHUNK_FREE,
                    CHUNK_WRITING,
                    Ordering::Acquire,
                    Ordering::Relaxed,
                )
                .is_ok()
            {
                chosen = Some(i);
                break;
            }
        }

        // Full: replace the oldest READY slot. A concurrently claimed READING
        // slot simply loses this CAS and is never touched by the producer.
        if chosen.is_none() {
            for _ in 0..self.ring.slots.len() {
                let oldest = self
                    .ring
                    .slots
                    .iter()
                    .enumerate()
                    .filter(|(_, slot)| slot.state.load(Ordering::Acquire) == CHUNK_READY)
                    .min_by_key(|(_, slot)| slot.seq.load(Ordering::Relaxed))
                    .map(|(i, _)| i);
                let Some(i) = oldest else { break };
                if self.ring.slots[i]
                    .state
                    .compare_exchange(
                        CHUNK_READY,
                        CHUNK_WRITING,
                        Ordering::Acquire,
                        Ordering::Relaxed,
                    )
                    .is_ok()
                {
                    chosen = Some(i);
                    replaced = true;
                    break;
                }
            }
        }

        let Some(i) = chosen else {
            // Only possible while the consumer owns a slot and every other
            // slot changed state under the bounded scans. Audio still wins.
            self.ring.dropped_chunks.fetch_add(1, Ordering::Relaxed);
            return;
        };
        let slot = &self.ring.slots[i];
        // SAFETY: the successful CAS gave this producer exclusive WRITING
        // ownership; see ScopeChunkRing's Sync proof.
        unsafe {
            let dst = slot.samples.as_ptr() as *mut f32;
            std::ptr::copy_nonoverlapping(samples.as_ptr(), dst, want);
        }
        slot.len.store(want, Ordering::Relaxed);
        // Sequence belongs to the ring, not one callback instance: an oboe
        // route reopen mints a new sink after the old stream closes, and its
        // chunks must still sort after any old visual backlog.
        slot.seq.store(
            self.ring.next_seq.fetch_add(1, Ordering::Relaxed),
            Ordering::Relaxed,
        );
        slot.state.store(CHUNK_READY, Ordering::Release);
        if replaced {
            self.ring.dropped_chunks.fetch_add(1, Ordering::Relaxed);
        }
        self.hint = (i + 1) % self.ring.slots.len();
    }
}

/// Single consumer endpoint, owned by the non-RT scope worker.
pub struct ScopeChunkTap {
    ring: Arc<ScopeChunkRing>,
}

impl ScopeChunkTap {
    /// Non-blocking oldest-first pop. The caller supplies reusable storage;
    /// too-small output deliberately discards the remainder of that visual
    /// chunk rather than retaining partial history.
    pub fn pop_into(&mut self, out: &mut [f32]) -> usize {
        for _ in 0..self.ring.slots.len() {
            let oldest = self
                .ring
                .slots
                .iter()
                .enumerate()
                .filter(|(_, slot)| slot.state.load(Ordering::Acquire) == CHUNK_READY)
                .min_by_key(|(_, slot)| slot.seq.load(Ordering::Relaxed))
                .map(|(i, _)| i);
            let Some(i) = oldest else { return 0 };
            let slot = &self.ring.slots[i];
            if slot
                .state
                .compare_exchange(
                    CHUNK_READY,
                    CHUNK_READING,
                    Ordering::Acquire,
                    Ordering::Relaxed,
                )
                .is_err()
            {
                continue;
            }
            let n = slot.len.load(Ordering::Relaxed).min(out.len());
            // SAFETY: the successful CAS gave this consumer exclusive READING
            // ownership; see ScopeChunkRing's Sync proof.
            unsafe {
                let src = slot.samples.as_ptr() as *const f32;
                std::ptr::copy_nonoverlapping(src, out.as_mut_ptr(), n);
            }
            slot.state.store(CHUNK_FREE, Ordering::Release);
            return n;
        }
        0
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn roundtrip_preserves_order_and_frames() {
        let ring = BlockRing::new(64);
        let data: Vec<f32> = (0..96).map(|i| i as f32).collect();
        assert!(ring.push_blocking(&data));
        let mut out = vec![0.0f32; 96];
        assert_eq!(ring.pop_into(&mut out), 96);
        assert_eq!(out, data);
        // Odd tail (not a whole frame) is ignored by contract.
        assert!(ring.push_blocking(&[1.0, 2.0, 3.0]));
        let mut out2 = vec![0.0f32; 4];
        assert_eq!(ring.pop_into(&mut out2), 2);
        assert_eq!(&out2[..2], &[1.0, 2.0]);
    }

    #[test]
    fn wraparound_is_exact() {
        let ring = BlockRing::new(64); // 128 samples
        let cap = ring.capacity_samples();
        // Fill 3/4, drain half, fill past the physical end repeatedly.
        let mut next_in = 0u32;
        let mut next_out = 0u32;
        for _ in 0..50 {
            let chunk: Vec<f32> = (0..cap / 4)
                .map(|_| {
                    let v = next_in as f32;
                    next_in += 1;
                    v
                })
                .collect();
            assert!(ring.push_blocking(&chunk));
            let mut out = vec![0.0f32; cap / 4];
            assert_eq!(ring.pop_into(&mut out), cap / 4);
            for v in out {
                assert_eq!(v, next_out as f32, "sample continuity across wrap");
                next_out += 1;
            }
        }
    }

    #[test]
    fn threaded_stress_keeps_continuity() {
        let ring = BlockRing::new(256);
        let r2 = ring.clone();
        let producer = std::thread::spawn(move || {
            let mut n = 0u32;
            while n < 100_000 {
                let chunk: Vec<f32> = (0..64)
                    .map(|_| {
                        let v = n as f32;
                        n += 1;
                        v
                    })
                    .collect();
                if !r2.push_blocking(&chunk) {
                    break;
                }
            }
        });
        let mut expect = 0u32;
        let mut out = vec![0.0f32; 128];
        while expect < 100_000 {
            let got = ring.pop_into(&mut out);
            for v in &out[..got] {
                assert_eq!(*v, expect as f32);
                expect += 1;
            }
            if got == 0 {
                std::thread::yield_now();
            }
        }
        producer.join().unwrap();
    }

    #[test]
    fn close_unblocks_a_parked_producer() {
        let ring = BlockRing::new(64);
        let cap = ring.capacity_samples();
        let fill: Vec<f32> = vec![0.5; cap];
        assert!(ring.push_blocking(&fill)); // exactly full
        let r2 = ring.clone();
        let h = std::thread::spawn(move || r2.push_blocking(&[1.0, 2.0]));
        std::thread::sleep(Duration::from_millis(30)); // let it park
        ring.close();
        assert!(!h.join().unwrap(), "closed ring must return false");
    }

    #[test]
    fn skip_to_latest_keeps_newest_audio() {
        let ring = BlockRing::new(512);
        let data: Vec<f32> = (0..800).map(|i| i as f32).collect(); // 400 frames
        assert!(ring.push_blocking(&data));
        let dropped = ring.skip_to_latest(50);
        assert_eq!(dropped, 350);
        assert_eq!(ring.buffered_frames(), 50);
        let mut out = vec![0.0f32; 100];
        assert_eq!(ring.pop_into(&mut out), 100);
        assert_eq!(out[0], 700.0, "newest 50 frames survive");
        assert_eq!(ring.skips.load(Ordering::Relaxed), 1);
        assert_eq!(ring.skipped_frames.load(Ordering::Relaxed), 350);
    }

    #[test]
    fn consumer_scope_tracks_post_skip_playhead_not_producer() {
        const TARGET_FRAMES: usize = 50;
        const CALLBACK_FRAMES: usize = 60;
        let audible = BlockRing::new(512);
        let produced: Vec<f32> = (0..800).map(|i| i as f32).collect(); // 400 frames
        assert!(audible.push_blocking(&produced));

        // The adaptive catch-up moves the audible consumer to frame 350 while
        // the producer has already reached frame 400.
        assert_eq!(audible.skip_to_latest(TARGET_FRAMES), 350);
        let mut callback = vec![0.0; CALLBACK_FRAMES * FRAME];
        let got = audible.pop_into(&mut callback);
        assert_eq!(got, TARGET_FRAMES * FRAME);
        callback[got..].fill(0.0); // the callback's actual underrun decision

        // Scope publication happens only after consumer index math and uses
        // precisely the callback's finalized samples.
        let scope = ScopeChunkRing::new(4, callback.len());
        let mut scope_sink = scope.sink();
        let mut scope_tap = scope.tap();
        scope_sink.push(&callback);
        let mut surfaced = vec![0.0; callback.len()];
        assert_eq!(scope_tap.pop_into(&mut surfaced), surfaced.len());
        assert_eq!(&surfaced[..got], &produced[700..800]);
        assert!(surfaced[got..].iter().all(|sample| *sample == 0.0));
        assert_ne!(
            &surfaced[..got],
            &produced[..got],
            "must not expose producer-side arrival"
        );
    }

    #[test]
    fn scope_chunk_ring_replaces_oldest_visual_chunk_when_full() {
        let scope = ScopeChunkRing::new(2, 4);
        let mut sink = scope.sink();
        let mut tap = scope.tap();
        sink.push(&[0.0, 1.0, 2.0, 3.0]);
        sink.push(&[4.0, 5.0, 6.0, 7.0]);
        sink.push(&[8.0, 9.0, 10.0, 11.0]);
        assert_eq!(scope.dropped_chunks(), 1);

        let mut out = [0.0; 4];
        assert_eq!(tap.pop_into(&mut out), 4);
        assert_eq!(out, [4.0, 5.0, 6.0, 7.0]);
        assert_eq!(tap.pop_into(&mut out), 4);
        assert_eq!(out, [8.0, 9.0, 10.0, 11.0]);
    }

    #[test]
    fn safe_profile_is_the_field_tuned_shape_exactly() {
        let rate = 48_000;
        let mut jitter = AdaptiveJitter::new(rate, LATENCY_MODE_SAFE, 0);
        assert_eq!(jitter.target_frames(), 12_000); // 250 ms
        assert_eq!(jitter.high_frames(), 16_800); // 350 ms

        for _ in 0..24 {
            assert_eq!(jitter.catch_up(16_801, 480), None);
        }
        assert_eq!(
            jitter.catch_up(16_800, 480),
            None,
            "touching the threshold resets the sustained streak"
        );
        for _ in 0..24 {
            assert_eq!(jitter.catch_up(16_801, 480), None);
        }
        assert_eq!(jitter.catch_up(16_801, 480), Some(12_000));
        jitter.observe_underrun();
        assert_eq!(jitter.target_frames(), 12_000, "safe never adapts");
    }

    #[test]
    fn adaptive_profiles_start_at_their_requested_floors() {
        let rate = 48_000;
        let tight = AdaptiveJitter::new(rate, LATENCY_MODE_TIGHT, 0);
        assert_eq!(tight.target_frames(), 3_840); // 80 ms
        assert_eq!(tight.high_frames(), 8_640); // target + 100 ms

        let balanced = AdaptiveJitter::new(rate, LATENCY_MODE_BALANCED, 0);
        assert_eq!(balanced.target_frames(), 7_200); // 150 ms
        assert_eq!(balanced.high_frames(), 12_000); // target + 100 ms
    }

    #[test]
    fn underruns_widen_quickly_but_never_past_safe() {
        let rate = 48_000;
        let mut jitter = AdaptiveJitter::new(rate, LATENCY_MODE_TIGHT, 3_840);
        jitter.observe_underrun();
        assert_eq!(jitter.target_frames(), 5_760); // 120 ms
        for _ in 0..20 {
            jitter.observe_underrun();
        }
        assert_eq!(jitter.target_frames(), 12_000); // safe ceiling, 250 ms
        assert_eq!(jitter.high_frames(), 16_800); // safe threshold, 350 ms
    }

    #[test]
    fn clean_playback_shrinks_one_step_per_two_minutes() {
        let rate = 48_000;
        let mut jitter = AdaptiveJitter::new(rate, LATENCY_MODE_BALANCED, 9_120); // 190 ms
        jitter.observe_clean(rate * 120 - 1);
        assert_eq!(jitter.target_frames(), 9_120);
        jitter.observe_clean(1);
        assert_eq!(jitter.target_frames(), 8_160); // 170 ms

        // An underrun resets the clean clock while widening by 40 ms.
        jitter.observe_clean(rate * 60);
        jitter.observe_underrun();
        assert_eq!(jitter.target_frames(), 10_080); // 210 ms
        jitter.observe_clean(rate * 60);
        assert_eq!(jitter.target_frames(), 10_080);
    }

    #[test]
    fn mode_changes_reset_to_floor_and_invalid_values_choose_safe() {
        let rate = 48_000;
        let mut jitter = AdaptiveJitter::new(rate, LATENCY_MODE_TIGHT, 12_000);
        assert_eq!(jitter.target_frames(), 12_000, "learned headroom resumes");

        jitter.set_mode(LATENCY_MODE_BALANCED);
        assert_eq!(jitter.mode(), LATENCY_MODE_BALANCED);
        assert_eq!(jitter.target_frames(), 7_200);
        jitter.set_mode(99);
        assert_eq!(jitter.mode(), LATENCY_MODE_SAFE);
        assert_eq!(jitter.target_frames(), 12_000);
    }
}

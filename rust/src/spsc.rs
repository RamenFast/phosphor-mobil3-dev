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
//!
//! Single-producer/single-consumer is enforced by usage at the seam
//! (`remote::AudioSink`/`AudioTap`, both !Clone): the oboe restart supervisor
//! legitimately mints one tap per reopened stream, and streams never run
//! concurrently (install happens only after AAudio closed the predecessor).

use std::cell::UnsafeCell;
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, AtomicU64, AtomicUsize, Ordering};
use std::time::Duration;

/// Interleaved stereo: every transfer is rounded down to whole frames.
pub const FRAME: usize = 2;

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
        let buf: Box<[UnsafeCell<f32>]> =
            (0..samples).map(|_| UnsafeCell::new(0.0)).collect();
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
        self.skipped_frames.fetch_add(drop_frames as u64, Ordering::Relaxed);
        drop_frames
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
            let chunk: Vec<f32> = (0..cap / 4).map(|_| {
                let v = next_in as f32;
                next_in += 1;
                v
            }).collect();
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
                let chunk: Vec<f32> = (0..64).map(|_| {
                    let v = n as f32;
                    n += 1;
                    v
                }).collect();
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
}

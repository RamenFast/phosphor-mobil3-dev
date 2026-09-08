//! A window's retirement signal follows its real resource, including queued work.
use std::ops::Deref;
use std::sync::{Arc, Condvar, Mutex, mpsc};

#[derive(Clone, Default)]
pub struct Retirement(Arc<(Mutex<State>, Condvar)>);
#[derive(Default)]
struct State {
    cancelled: bool,
    dropped: bool,
}
impl Retirement {
    pub fn cancel(&self) {
        self.0.0.lock().unwrap().cancelled = true;
    }
    pub fn dropped(&self) -> bool {
        self.0.0.lock().unwrap().dropped
    }
    pub fn cancelled(&self) -> bool {
        self.0.0.lock().unwrap().cancelled
    }
    pub fn publish(&self, ack: &mpsc::SyncSender<i32>, mode: i32) -> bool {
        let state = self.0.0.lock().unwrap();
        !state.cancelled && ack.send(mode).is_ok()
    }
    pub fn attached(
        &self,
        result: mpsc::Receiver<i32>,
        deadline: std::time::Duration,
    ) -> Option<i32> {
        match result.recv_timeout(deadline) {
            Ok(mode) if mode >= 0 => Some(mode),
            _ => {
                self.cancel();
                None
            }
        }
    }
    pub fn wait(&self) {
        let mut state = self.0.0.lock().unwrap();
        while !state.dropped {
            state = self.0.1.wait(state).unwrap();
        }
    }
    pub fn barrier(&self, ack: mpsc::Receiver<()>) -> bool {
        let clean = ack.recv().is_ok();
        self.wait();
        clean
    }
}
pub struct Retiring<T> {
    value: Option<T>,
    pub retirement: Retirement,
}
impl<T> Retiring<T> {
    pub fn new(value: T) -> Self {
        Self {
            value: Some(value),
            retirement: Retirement::default(),
        }
    }
}
impl<T> Deref for Retiring<T> {
    type Target = T;
    fn deref(&self) -> &T {
        self.value.as_ref().unwrap()
    }
}
impl<T> Drop for Retiring<T> {
    fn drop(&mut self) {
        drop(self.value.take());
        self.retirement.0.0.lock().unwrap().dropped = true;
        self.retirement.0.1.notify_all();
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::atomic::{AtomicBool, Ordering};
    struct Resource(Arc<AtomicBool>);
    impl Drop for Resource {
        fn drop(&mut self) {
            self.0.store(true, Ordering::SeqCst);
        }
    }
    #[test]
    fn queued_cancel_skips_creation_and_waits_for_real_drop() {
        let dropped = Arc::new(AtomicBool::new(false));
        let resource = Retiring::new(Resource(dropped.clone()));
        let life = resource.retirement.clone();
        let (tx, rx) = mpsc::channel();
        tx.send(resource).unwrap();
        let (ack, result) = mpsc::sync_channel(1);
        assert_eq!(life.attached(result, std::time::Duration::ZERO), None);
        let queued = rx.recv().unwrap();
        assert!(queued.retirement.cancelled());
        assert!(!queued.retirement.publish(&ack, 1));
        assert!(!dropped.load(Ordering::SeqCst));
        drop(queued);
        life.wait();
        assert!(dropped.load(Ordering::SeqCst));
    }
    #[test]
    fn in_flight_cancel_rejects_late_creation() {
        let resource = Retiring::new(());
        let life = resource.retirement.clone();
        let (started, seen) = mpsc::channel();
        let (resume, proceed) = mpsc::channel();
        let worker = std::thread::spawn(move || {
            assert!(!resource.retirement.cancelled());
            started.send(()).unwrap();
            proceed.recv().unwrap();
            let (ack, result) = mpsc::sync_channel(1);
            assert!(!resource.retirement.publish(&ack, 1));
            assert!(result.try_recv().is_err());
            drop(resource);
        });
        seen.recv().unwrap();
        let (_ack, result) = mpsc::sync_channel(1);
        assert_eq!(life.attached(result, std::time::Duration::ZERO), None);
        resume.send(()).unwrap();
        life.wait();
        worker.join().unwrap();
    }
    #[test]
    fn lost_attach_receiver_cannot_publish() {
        let resource = Retiring::new(());
        let (ack, result) = mpsc::sync_channel(1);
        drop(result);
        assert!(!resource.retirement.publish(&ack, 1));
    }
    #[test]
    fn disconnect_is_not_ack_and_barrier_waits_past_ack_until_drop() {
        for clean in [true, false] {
            let dropped = Arc::new(AtomicBool::new(false));
            let resource = Retiring::new(Resource(dropped.clone()));
            let life = resource.retirement.clone();
            let (ack, result) = mpsc::channel();
            if clean {
                ack.send(()).unwrap();
            }
            drop(ack);
            let (done, observed) = mpsc::channel();
            let worker = std::thread::spawn(move || {
                let confirmed = life.barrier(result);
                assert!(dropped.load(Ordering::SeqCst));
                done.send(confirmed).unwrap();
            });
            assert!(observed.try_recv().is_err());
            drop(resource);
            assert_eq!(observed.recv().unwrap(), clean);
            worker.join().unwrap();
        }
    }
}

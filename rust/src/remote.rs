//! Remote source (the Tailscale bridge, phone side): connect to a desktop `phosphor-relay`,
//! play its audio through oboe AND scope it, show its metadata, and send transport commands
//! back. Sidesteps Android's capture wall by letting a Linux box be the capture proxy.
//! See docs/BRIDGE.md.

use std::io::{Read, Write};
use std::net::TcpStream;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex, OnceLock};

use oboe::{
    AudioOutputCallback, AudioOutputStreamSafe, AudioStream, AudioStreamAsync,
    AudioStreamBuilder, DataCallbackResult, Output, PerformanceMode, SharingMode, Stereo,
    Usage,
};
use phosphor_audio::playback::AudibleRing;

use crate::deck::{RATE, scope_ring};

struct RemoteOutput {
    audible: Arc<AudibleRing>,
    scratch: Vec<f32>,
}

impl AudioOutputCallback for RemoteOutput {
    type FrameType = (f32, Stereo);
    fn on_audio_ready(
        &mut self,
        _s: &mut dyn AudioOutputStreamSafe,
        frames: &mut [(f32, f32)],
    ) -> DataCallbackResult {
        let need = frames.len() * 2;
        self.scratch.resize(need, 0.0);
        let got = self.audible.pop_into(&mut self.scratch);
        self.scratch[got..need].fill(0.0);
        for (i, f) in frames.iter_mut().enumerate() {
            f.0 = self.scratch[2 * i];
            f.1 = self.scratch[2 * i + 1];
        }
        DataCallbackResult::Continue
    }
}

struct Remote {
    stream: TcpStream, // write side (transport commands)
    _out: AudioStreamAsync<Output, RemoteOutput>,
    alive: Arc<AtomicBool>,
    meta: Arc<Mutex<String>>,
}

// TcpStream + oboe handle move with the struct; access is serialized behind the mutex.
unsafe impl Send for Remote {}

static REMOTE: Mutex<Option<Remote>> = Mutex::new(None);

fn meta_slot() -> &'static Arc<Mutex<String>> {
    static M: OnceLock<Arc<Mutex<String>>> = OnceLock::new();
    M.get_or_init(|| Arc::new(Mutex::new("{}".to_string())))
}

pub fn connect(host: &str, port: u16) -> Result<(), String> {
    disconnect();

    let stream = TcpStream::connect((host, port)).map_err(|e| format!("connect {host}:{port}: {e}"))?;
    stream.set_nodelay(true).ok();

    let audible = AudibleRing::new(RATE);
    let alive = Arc::new(AtomicBool::new(true));
    let meta = meta_slot().clone();

    // oboe output plays the streamed audio (Ben hears it).
    let mut out = AudioStreamBuilder::default()
        .set_performance_mode(PerformanceMode::LowLatency)
        .set_sharing_mode(SharingMode::Shared)
        .set_usage(Usage::Media)
        .set_sample_rate(RATE as i32)
        .set_format::<f32>()
        .set_channel_count::<Stereo>()
        .set_callback(RemoteOutput { audible: audible.clone(), scratch: Vec::new() })
        .open_stream()
        .map_err(|e| format!("oboe open: {e}"))?;
    out.start().map_err(|e| format!("oboe start: {e}"))?;

    // Audio-writer thread: drains a bounded channel into the (blocking) audible ring. Kept
    // SEPARATE from the reader so audio backpressure can never stall the scope feed.
    let (audio_tx, audio_rx) = std::sync::mpsc::sync_channel::<Vec<f32>>(8);
    let aw_audible = audible.clone();
    let aw_alive = alive.clone();
    std::thread::Builder::new()
        .name("phosphor-remote-audio".into())
        .spawn(move || {
            while aw_alive.load(Ordering::Relaxed) {
                match audio_rx.recv() {
                    Ok(buf) => {
                        aw_audible.push_blocking(&buf);
                    }
                    Err(_) => break,
                }
            }
        })
        .map_err(|e| e.to_string())?;

    // Reader thread: parse frames, feed the scope (never blocks), hand audio off, store meta.
    let read_stream = stream.try_clone().map_err(|e| e.to_string())?;
    let r_alive = alive.clone();
    let r_meta = meta.clone();
    std::thread::Builder::new()
        .name("phosphor-remote".into())
        .spawn(move || reader(read_stream, audio_tx, r_alive, r_meta))
        .map_err(|e| e.to_string())?;

    scope_ring().lock().unwrap().clear_pending();
    crate::deck::DECK_ACTIVE.store(true, Ordering::Relaxed);
    *REMOTE.lock().unwrap() = Some(Remote { stream, _out: out, alive, meta });
    log::info!("remote connected: {host}:{port}");
    Ok(())
}

fn reader(
    mut s: TcpStream,
    audio_tx: std::sync::mpsc::SyncSender<Vec<f32>>,
    alive: Arc<AtomicBool>,
    meta: Arc<Mutex<String>>,
) {
    let mut hdr = [0u8; 5];
    while alive.load(Ordering::Relaxed) {
        if s.read_exact(&mut hdr).is_err() {
            break;
        }
        let len = u32::from_be_bytes([hdr[1], hdr[2], hdr[3], hdr[4]]) as usize;
        let mut payload = vec![0u8; len];
        if s.read_exact(&mut payload).is_err() {
            break;
        }
        match hdr[0] {
            b'A' => {
                // Relay sends s16le; convert to f32 ONCE and feed both the scope (the f32
                // push — NOT push_interleaved_le_bytes, which expects f32 bytes) and audio.
                let mut f32buf = Vec::with_capacity(payload.len() / 2);
                for c in payload.chunks_exact(2) {
                    f32buf.push(i16::from_le_bytes([c[0], c[1]]) as f32 / 32768.0);
                }
                // Scope always gets fed (non-blocking). Audio is handed off and dropped if the
                // writer is behind — a brief audio glitch, never a black scope.
                scope_ring().lock().unwrap().push_interleaved(&f32buf);
                let _ = audio_tx.try_send(f32buf);
            }
            b'M' => {
                if let Ok(txt) = String::from_utf8(payload) {
                    *meta.lock().unwrap() = txt;
                }
            }
            _ => {}
        }
    }
    alive.store(false, Ordering::Relaxed);
    log::info!("remote reader ended");
}

pub fn transport(cmd: &str) {
    if let Some(r) = REMOTE.lock().unwrap().as_mut() {
        let bytes = cmd.as_bytes();
        let len = (bytes.len() as u32).to_be_bytes();
        let _ = r.stream.write_all(&[b'T']);
        let _ = r.stream.write_all(&len);
        let _ = r.stream.write_all(bytes);
        let _ = r.stream.flush();
        log::info!("remote transport: {cmd}");
    }
}

pub fn metadata_json() -> String {
    meta_slot().lock().unwrap().clone()
}

pub fn disconnect() {
    if let Some(r) = REMOTE.lock().unwrap().take() {
        r.alive.store(false, Ordering::Relaxed);
        let _ = r.stream.shutdown(std::net::Shutdown::Both);
    }
    crate::deck::DECK_ACTIVE.store(false, Ordering::Relaxed);
}

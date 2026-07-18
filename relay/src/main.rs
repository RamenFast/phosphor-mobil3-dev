//! phosphor-relay — the laptop-side half of the phosphor-mobil3 Tailscale bridge.
//!
//! Streams the machine's playing audio (default sink monitor, via `parec`) and player
//! metadata (`playerctl`) to a connected phone, and forwards the phone's transport commands
//! to the player over MPRIS. See docs/BRIDGE.md for the protocol.
//!
//! Deliberately shells out to `parec`/`playerctl` rather than linking PipeWire/D-Bus: those
//! tools are already on the machine, and it keeps the relay a small, auditable binary.

use std::io::{Read, Write};
use std::net::{TcpListener, TcpStream};
use std::process::{Child, Command, Stdio};
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};
use std::thread;
use std::time::Duration;

const RATE: &str = "48000";
const PLAYER: &str = "spotify"; // default; overridable with --player

struct Args {
    port: u16,
    monitor: Option<String>,
    player: String,
}

fn parse_args() -> Args {
    let mut port = 45777u16;
    let mut monitor = None;
    let mut player = PLAYER.to_string();
    let mut it = std::env::args().skip(1);
    while let Some(a) = it.next() {
        match a.as_str() {
            "--port" => port = it.next().and_then(|v| v.parse().ok()).unwrap_or(port),
            "--monitor" => monitor = it.next(),
            "--player" => player = it.next().unwrap_or(player),
            "-h" | "--help" => {
                eprintln!(
                    "phosphor-relay [--port 45777] [--monitor <pactl source>] [--player spotify]\n\
                     Streams default-sink audio + {PLAYER} metadata to a phone; forwards transport."
                );
                std::process::exit(0);
            }
            other => eprintln!("phosphor-relay: ignoring unknown arg {other}"),
        }
    }
    Args { port, monitor, player }
}

fn default_monitor() -> Option<String> {
    let out = Command::new("pactl").arg("get-default-sink").output().ok()?;
    let sink = String::from_utf8_lossy(&out.stdout).trim().to_string();
    if sink.is_empty() { None } else { Some(format!("{sink}.monitor")) }
}

fn write_frame(stream: &mut TcpStream, tag: u8, payload: &[u8]) -> std::io::Result<()> {
    let len = (payload.len() as u32).to_be_bytes();
    stream.write_all(&[tag])?;
    stream.write_all(&len)?;
    stream.write_all(payload)?;
    stream.flush()
}

fn main() {
    let args = parse_args();
    let monitor = args.monitor.or_else(default_monitor).unwrap_or_else(|| {
        eprintln!("phosphor-relay: could not resolve a monitor source. fix: pass --monitor <name> (see `pactl list sources short`)");
        std::process::exit(2);
    });
    let listener = TcpListener::bind(("0.0.0.0", args.port)).unwrap_or_else(|e| {
        eprintln!("phosphor-relay: bind :{} failed: {e}. fix: pick a free --port", args.port);
        std::process::exit(4);
    });
    eprintln!("phosphor-relay: listening on 0.0.0.0:{} · monitor={monitor} · player={}", args.port, args.player);

    for conn in listener.incoming() {
        match conn {
            Ok(stream) => {
                let monitor = monitor.clone();
                let player = args.player.clone();
                thread::spawn(move || {
                    let peer = stream.peer_addr().map(|a| a.to_string()).unwrap_or_default();
                    eprintln!("phosphor-relay: client {peer} connected");
                    if let Err(e) = serve_client(stream, &monitor, &player) {
                        eprintln!("phosphor-relay: client {peer} ended: {e}");
                    }
                });
            }
            Err(e) => eprintln!("phosphor-relay: accept error: {e}"),
        }
    }
}

fn serve_client(stream: TcpStream, monitor: &str, player: &str) -> std::io::Result<()> {
    let alive = Arc::new(AtomicBool::new(true));

    // parec: capture the monitor as raw s16le stereo on stdout.
    let mut parec: Child = Command::new("parec")
        .args(["--format=s16le", "--rate", RATE, "--channels=2", "--raw", "-d", monitor])
        .stdout(Stdio::piped())
        .stderr(Stdio::null())
        .spawn()?;
    let mut pcm = parec.stdout.take().expect("parec stdout");

    // Audio thread: PCM frames → client.
    let audio_stream = stream.try_clone()?;
    let audio_alive = alive.clone();
    let audio = thread::spawn(move || {
        let mut s = audio_stream;
        let mut buf = [0u8; 3840]; // ~20 ms
        while audio_alive.load(Ordering::Relaxed) {
            match pcm.read(&mut buf) {
                Ok(0) => break,
                Ok(n) => {
                    if write_frame(&mut s, b'A', &buf[..n]).is_err() {
                        break;
                    }
                }
                Err(_) => break,
            }
        }
        audio_alive.store(false, Ordering::Relaxed);
    });

    // Metadata thread: playerctl poll → client.
    let meta_stream = stream.try_clone()?;
    let meta_alive = alive.clone();
    let meta_player = player.to_string();
    let meta = thread::spawn(move || {
        let mut s = meta_stream;
        let mut last = String::new();
        while meta_alive.load(Ordering::Relaxed) {
            let json = player_metadata(&meta_player);
            if json != last {
                if write_frame(&mut s, b'M', json.as_bytes()).is_err() {
                    break;
                }
                last = json;
            }
            thread::sleep(Duration::from_millis(1000));
        }
        meta_alive.store(false, Ordering::Relaxed);
    });

    // This thread: read client transport frames.
    let mut reader = stream;
    let mut hdr = [0u8; 5];
    while alive.load(Ordering::Relaxed) {
        if reader.read_exact(&mut hdr).is_err() {
            break;
        }
        let len = u32::from_be_bytes([hdr[1], hdr[2], hdr[3], hdr[4]]) as usize;
        let mut payload = vec![0u8; len.min(256)];
        if reader.read_exact(&mut payload).is_err() {
            break;
        }
        if hdr[0] == b'T' {
            let cmd = String::from_utf8_lossy(&payload);
            transport(player, cmd.trim());
        }
    }

    alive.store(false, Ordering::Relaxed);
    let _ = parec.kill();
    let _ = audio.join();
    let _ = meta.join();
    Ok(())
}

fn player_metadata(player: &str) -> String {
    let field = |f: &str| -> String {
        Command::new("playerctl")
            .args(["-p", player, "metadata", "--format", f])
            .output()
            .ok()
            .map(|o| String::from_utf8_lossy(&o.stdout).trim().to_string())
            .unwrap_or_default()
    };
    let status = Command::new("playerctl")
        .args(["-p", player, "status"])
        .output()
        .ok()
        .map(|o| String::from_utf8_lossy(&o.stdout).trim().to_string())
        .unwrap_or_default();
    serde_json::json!({
        "title": field("{{title}}"),
        "artist": field("{{artist}}"),
        "album": field("{{album}}"),
        "playing": status.eq_ignore_ascii_case("Playing"),
    })
    .to_string()
}

fn transport(player: &str, cmd: &str) {
    let verb = match cmd {
        "next" => "next",
        "prev" => "previous",
        "playpause" => "play-pause",
        _ => return,
    };
    let _ = Command::new("playerctl").args(["-p", player, verb]).status();
    eprintln!("phosphor-relay: transport {cmd} -> playerctl {verb}");
}

//! phosphor-relay v2 — the desktop-side half of the phosphor-mobil3 bridge.
//!
//! Streams the machine's playing audio (per-app or whole-output), now-playing
//! metadata, and — on demand — phosphor's live beam geometry to a phone over one
//! framed TCP connection, and drives the desktop's player + a browsable music
//! library back from the phone. See docs/BRIDGE.md (proto 2) and `schema`.
//!
//! Design law: one connection, one **single writer thread** (no mid-frame
//! interleave), every error carries a `fix`, and 8 s of client silence tears the
//! session and every child process down. Shells out to pw-record / pw-dump /
//! wpctl / pactl / parec / playerctl / busctl / ffmpeg / ffprobe / curl / rclone /
//! phosphor — the binary stays small and auditable. Deps: serde + serde_json only.

mod capture;
mod cli;
mod config;
mod geometry;
mod library;
mod player;
mod proto;
mod scope;
mod session;
mod util;

fn main() {
    let args: Vec<String> = std::env::args().skip(1).collect();
    let verb = args.first().map(|s| s.as_str()).unwrap_or("serve");
    let rest = if args.is_empty() { &[] } else { &args[1..] };

    match verb {
        "serve" => cli::serve(rest),
        "sources" => cli::sources(rest),
        "doctor" => cli::doctor(rest),
        "probe" => cli::probe(rest),
        "config" => cli::config(rest),
        "schema" | "--schema" => cli::schema(rest),
        "-h" | "--help" | "help" => cli::help(),
        "-V" | "--version" | "version" => cli::version(),
        // No verb given but flags present (e.g. `phosphor-relay --port 45888`)
        // → treat as serve; a bare unknown token is a usage error (exit 3).
        other if other.starts_with("--") => cli::serve(&args),
        other => cli::bad_verb(other),
    }
}

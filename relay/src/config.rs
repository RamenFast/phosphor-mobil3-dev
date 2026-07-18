//! `~/.config/phosphor-relay/config.json` — data, per AGENT-CLI. CLI flags
//! override the file; a missing file yields sensible defaults (port 45777,
//! player spotify, `~/Music` as `music0` when it exists). Writes are atomic
//! (temp sibling + rename).

use serde::{Deserialize, Serialize};

use crate::util;

/// A configured library root: a local `path` OR an `rclone` remote (`remote:path`).
#[derive(Serialize, Deserialize, Clone)]
pub struct LibraryRoot {
    pub id: String,
    pub label: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub path: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub rclone: Option<String>,
}

impl LibraryRoot {
    /// Human/display path for the W frame (real path, or the rclone remote).
    pub fn display_path(&self) -> String {
        self.path.clone().or_else(|| self.rclone.clone()).unwrap_or_default()
    }
    pub fn is_rclone(&self) -> bool {
        self.rclone.is_some()
    }
}

#[derive(Serialize, Deserialize, Clone)]
pub struct Config {
    #[serde(default = "default_port")]
    pub port: u16,
    #[serde(default = "default_player")]
    pub player: String,
    #[serde(default)]
    pub libraries: Vec<LibraryRoot>,
}

fn default_port() -> u16 {
    45777
}
fn default_player() -> String {
    "spotify".into()
}

impl Default for Config {
    fn default() -> Self {
        let mut libraries = Vec::new();
        let music = util::home().join("Music");
        if music.is_dir() {
            libraries.push(LibraryRoot {
                id: "music0".into(),
                label: "Music".into(),
                path: Some(music.to_string_lossy().into_owned()),
                rclone: None,
            });
        }
        Config { port: default_port(), player: default_player(), libraries }
    }
}

impl Config {
    pub fn path() -> std::path::PathBuf {
        util::config_dir().join("config.json")
    }

    /// Load the config file, falling back to defaults when it is missing. A
    /// present-but-broken file surfaces its parse error (caller decides).
    pub fn load() -> Result<Config, String> {
        let p = Self::path();
        match std::fs::read_to_string(&p) {
            Ok(s) => serde_json::from_str(&s)
                .map_err(|e| format!("{}: {e}", p.display())),
            Err(e) if e.kind() == std::io::ErrorKind::NotFound => Ok(Config::default()),
            Err(e) => Err(format!("{}: {e}", p.display())),
        }
    }

    pub fn find_root(&self, id: &str) -> Option<&LibraryRoot> {
        self.libraries.iter().find(|r| r.id == id)
    }

    /// Atomic write: temp sibling + rename. (Kept for completeness — the relay
    /// reads config; a `config --write` path can land here later.)
    #[allow(dead_code)]
    pub fn save(&self) -> std::io::Result<()> {
        let dir = util::config_dir();
        std::fs::create_dir_all(&dir)?;
        let tmp = dir.join(".config.json.tmp");
        let body = serde_json::to_vec_pretty(self).unwrap_or_default();
        std::fs::write(&tmp, body)?;
        std::fs::rename(&tmp, Self::path())
    }
}

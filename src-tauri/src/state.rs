use std::collections::HashMap;
use std::fs::OpenOptions;
use std::io::Write;
use std::path::PathBuf;
use std::sync::atomic::AtomicBool;
use std::sync::{Arc, Mutex, RwLock};
use std::time::Instant;
use tauri::{AppHandle, Emitter};

/// User agent sent with every request (Mojang, Modrinth and CurseForge ask for one).
pub const USER_AGENT: &str = "WinLaucher/1.0.0 (+https://github.com/egorbelovziiik-design/WinLauncher)";

use crate::models::*;
use crate::paths::Paths;
use crate::secret::SecretStore;
use crate::util;

pub fn lock<T>(m: &Mutex<T>) -> std::sync::MutexGuard<'_, T> {
    m.lock().unwrap_or_else(|e| e.into_inner())
}

pub fn rlock<T>(m: &RwLock<T>) -> std::sync::RwLockReadGuard<'_, T> {
    m.read().unwrap_or_else(|e| e.into_inner())
}

pub fn wlock<T>(m: &RwLock<T>) -> std::sync::RwLockWriteGuard<'_, T> {
    m.write().unwrap_or_else(|e| e.into_inner())
}

/// Everything needed to run a failed download again.
#[derive(Debug, Clone)]
pub struct RetryJob {
    pub title: String,
    pub profile_id: Option<String>,
    pub files: Vec<crate::download::FileSpec>,
}

pub struct AppState {
    pub app: AppHandle,
    pub paths: RwLock<Paths>,
    pub settings: RwLock<Settings>,
    pub profiles: RwLock<Vec<Profile>>,
    pub accounts: RwLock<Vec<Account>>,
    pub downloads: Mutex<Vec<DownloadItem>>,
    pub java: Mutex<Vec<JavaRuntime>>,
    pub launch: Mutex<LaunchStatus>,
    pub logs: Mutex<Vec<LogLine>>,
    pub cancel: Mutex<HashMap<String, Arc<AtomicBool>>>,
    pub retry: Mutex<HashMap<String, RetryJob>>,
    pub child: Mutex<Option<std::process::Child>>,
    pub manifest: Mutex<Option<(Instant, VersionManifest)>>,
    pub secrets: Arc<SecretStore>,
    pub http: reqwest::Client,
}

impl AppState {
    pub fn new(app: AppHandle, paths: Paths, secrets: SecretStore, http: reqwest::Client) -> Self {
        Self {
            app,
            paths: RwLock::new(paths),
            settings: RwLock::new(Settings::with_defaults(String::new(), 4096)),
            profiles: RwLock::new(Vec::new()),
            accounts: RwLock::new(Vec::new()),
            downloads: Mutex::new(Vec::new()),
            java: Mutex::new(Vec::new()),
            launch: Mutex::new(LaunchStatus::default()),
            logs: Mutex::new(Vec::new()),
            cancel: Mutex::new(HashMap::new()),
            retry: Mutex::new(HashMap::new()),
            child: Mutex::new(None),
            manifest: Mutex::new(None),
            secrets: Arc::new(secrets),
            http,
        }
    }

    pub fn paths(&self) -> Paths {
        rlock(&self.paths).clone()
    }
    pub fn settings(&self) -> Settings {
        rlock(&self.settings).clone()
    }
    pub fn profiles(&self) -> Vec<Profile> {
        rlock(&self.profiles).clone()
    }
    pub fn accounts(&self) -> Vec<Account> {
        rlock(&self.accounts).clone()
    }
    pub fn http(&self) -> reqwest::Client {
        self.http.clone()
    }

    /* ------------------------------ logging ----------------------------- */

    pub fn log(&self, level: &str, source: &str, text: impl Into<String>) {
        let raw: String = text.into();
        let clean = redact(&raw);
        let line = LogLine::new(level, source, clean.clone());
        {
            let mut logs = lock(&self.logs);
            logs.push(line.clone());
            if logs.len() > 4000 {
                let drop = logs.len() - 4000;
                logs.drain(0..drop);
            }
        }
        let paths = self.paths();
        let file = paths.logs.join(if source == "game" { "game.log" } else { "launcher.log" });
        if let Ok(mut f) = OpenOptions::new().create(true).append(true).open(&file) {
            let _ = writeln!(f, "[{}] {} {}", line.ts, level.to_uppercase(), clean);
        }
        let _ = self.app.emit("log-line", line);
    }

    /* ---------------------------- persistence --------------------------- */

    pub fn save_settings(&self) {
        let s = self.settings();
        let _ = util::write_json(&self.paths().settings_file(), &s);
    }

    pub fn save_profiles(&self) {
        let list = self.profiles();
        let _ = util::write_json(&self.paths().profiles_file(), &list);
    }

    pub fn save_accounts(&self) {
        let list = self.accounts();
        let _ = util::write_json(&self.paths().accounts_file(), &list);
    }

    /* ---------------------------- downloads ----------------------------- */

    pub fn emit_downloads(&self) {
        let list = lock(&self.downloads).clone();
        let _ = self.app.emit("downloads-update", list);
    }

    pub fn add_download(&self, item: DownloadItem) -> String {
        let id = item.id.clone();
        {
            let mut list = lock(&self.downloads);
            list.push(item);
            if list.len() > 120 {
                let done: Vec<usize> = list
                    .iter()
                    .enumerate()
                    .filter(|(_, d)| d.status == "done")
                    .map(|(i, _)| i)
                    .collect();
                for idx in done.into_iter().rev().take(list.len().saturating_sub(120)) {
                    list.remove(idx);
                }
            }
        }
        self.emit_downloads();
        id
    }

    pub fn update_download<F: FnOnce(&mut DownloadItem)>(&self, id: &str, f: F) {
        {
            let mut list = lock(&self.downloads);
            if let Some(item) = list.iter_mut().find(|d| d.id == id) {
                f(item);
            }
        }
    }

    pub fn finish_download(&self, id: &str, error: Option<String>) {
        self.update_download(id, |d| {
            d.status = if error.is_some() { "error".into() } else { "done".into() };
            d.error = error;
            d.finished_at = Some(chrono::Utc::now().timestamp_millis());
            d.speed = 0.0;
            if d.total > 0 && d.status == "done" {
                d.current = d.total;
            }
        });
        self.emit_downloads();
    }

    /* ----------------------------- launch ------------------------------- */

    pub fn set_launch(&self, status: LaunchStatus) {
        {
            let mut cur = lock(&self.launch);
            *cur = status.clone();
        }
        let _ = self.app.emit("launch-status", status);
    }

    pub fn set_launch_state(&self, profile_id: Option<String>, state: &str, message: impl Into<String>) {
        let prev = lock(&self.launch).clone();
        let started = if state == "starting" || state == "running" {
            prev.started_at.clone().or_else(|| Some(chrono::Utc::now().to_rfc3339()))
        } else if state == "exited" || state == "error" || state == "idle" {
            None
        } else {
            prev.started_at.clone()
        };
        self.set_launch(LaunchStatus {
            profile_id,
            state: state.into(),
            message: redact(&message.into()),
            code: prev.code,
            started_at: started,
        });
    }

    pub fn emit_progress(&self, message: &str, current: u64, total: u64, bytes: u64, total_bytes: u64) {
        let payload = serde_json::json!({
            "message": message,
            "current": current,
            "total": total,
            "bytes": bytes,
            "totalBytes": total_bytes,
        });
        let _ = self.app.emit("launch-progress", payload);
    }

    /* ------------------------------ java -------------------------------- */

    pub fn java_runtimes(&self) -> Vec<JavaRuntime> {
        lock(&self.java).clone()
    }

    pub fn set_java_runtimes(&self, list: Vec<JavaRuntime>) {
        *lock(&self.java) = list;
    }

    pub fn profile_by_id(&self, id: &str) -> Option<Profile> {
        self.profiles().into_iter().find(|p| p.id == id)
    }

    pub fn profile_game_dir(&self, p: &Profile) -> PathBuf {
        PathBuf::from(&p.game_dir)
    }

    /// Isolated game directory of a profile, looked up by id.
    pub fn game_dir_for(&self, id: &str) -> Result<PathBuf, String> {
        self.profile_by_id(id)
            .map(|p| PathBuf::from(&p.game_dir))
            .ok_or_else(|| "Profile not found".to_string())
    }
}

/// Removes credentials from any text that goes to the UI or disk.
pub fn redact(input: &str) -> String {
    use regex::Regex;
    use std::sync::OnceLock;
    static RE: OnceLock<Regex> = OnceLock::new();
    let re = RE.get_or_init(|| {
        Regex::new(
            r#"(?i)("?(?:access_?token|refresh_?token|client_?secret|authorization|x-api-key)"?\s*[:=]\s*"?)([^",\s}]+)"#,
        )
        .expect("redaction regex")
    });
    let step1 = re.replace_all(input, "$1***").to_string();
    // JWT-looking strings (header.payload.signature) are always tokens.
    static JWT: OnceLock<Regex> = OnceLock::new();
    let jwt = JWT.get_or_init(|| Regex::new(r"eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}").expect("jwt regex"));
    jwt.replace_all(&step1, "***").to_string()
}

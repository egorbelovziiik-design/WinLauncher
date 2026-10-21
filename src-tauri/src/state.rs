use crate::{models::*, security};
use anyhow::{bail, Context, Result};
use rusqlite::{params, Connection};
use serde::{de::DeserializeOwned, Serialize};
use std::{
    collections::{HashMap, VecDeque},
    fs,
    io::Write,
    ops::Deref,
    path::PathBuf,
    sync::{Arc, Mutex, RwLock},
};
use tauri::{AppHandle, Emitter};
use tokio_util::sync::CancellationToken;

pub struct PendingAuth {
    pub device_code: String,
    pub interval: u64,
    pub expires: i64,
    pub client_id: String,
    pub next_poll: i64,
}

pub struct Inner {
    pub app: Option<AppHandle>,
    pub db: Mutex<Connection>,
    pub settings: RwLock<Settings>,
    pub client: reqwest::Client,
    pub job: Mutex<Option<Job>>,
    pub cancel: Mutex<CancellationToken>,
    pub running: Mutex<Option<RunningGame>>,
    pub game_stop: Mutex<CancellationToken>,
    pub logs: Mutex<VecDeque<LogLine>>,
    pub pending_auth: Mutex<HashMap<String, PendingAuth>>,
    pub discord_status: RwLock<String>,
    pub activity: RwLock<String>,
    pub exit: std::sync::atomic::AtomicBool,
}
#[derive(Clone)]
pub struct AppState(pub Arc<Inner>);
impl Deref for AppState {
    type Target = Inner;
    fn deref(&self) -> &Inner {
        &self.0
    }
}

impl AppState {
    pub fn new(app: AppHandle, base: PathBuf) -> Result<Self> {
        Self::create(Some(app), base)
    }
    #[cfg(feature = "native-smoke")]
    pub fn headless(base: PathBuf) -> Result<Self> {
        Self::create(None, base)
    }
    fn create(app: Option<AppHandle>, base: PathBuf) -> Result<Self> {
        fs::create_dir_all(&base)?;
        let conn = Connection::open(base.join("winlauncher.sqlite3"))?;
        conn.execute_batch("PRAGMA journal_mode=WAL; PRAGMA foreign_keys=ON; PRAGMA busy_timeout=5000;
            CREATE TABLE IF NOT EXISTS settings (id INTEGER PRIMARY KEY CHECK(id=1), payload TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS profiles (id TEXT PRIMARY KEY, payload TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS accounts (id TEXT PRIMARY KEY, payload TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS installed (id TEXT PRIMARY KEY, payload TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS sessions (id TEXT PRIMARY KEY, payload TEXT NOT NULL);
            PRAGMA user_version=1;")?;
        let saved = conn.query_row("SELECT payload FROM settings WHERE id=1", [], |r| {
            r.get::<_, String>(0)
        });
        let mut settings: Settings = match saved {
            Ok(s) => serde_json::from_str(&s).context("Повреждены настройки SQLite")?,
            Err(rusqlite::Error::QueryReturnedNoRows) => Settings::default(),
            Err(e) => return Err(e.into()),
        };
        if settings.data_dir.is_empty() {
            settings.data_dir = base.join("data").to_string_lossy().into_owned();
        }
        for folder in [
            "minecraft",
            "profiles",
            "runtime",
            "downloads",
            "logs",
            "backups",
        ] {
            fs::create_dir_all(PathBuf::from(&settings.data_dir).join(folder))?;
        }
        let state = Self(Arc::new(Inner {
            app,
            db: Mutex::new(conn),
            settings: RwLock::new(settings),
            client: security::client()?,
            job: Mutex::new(None),
            cancel: Mutex::new(CancellationToken::new()),
            running: Mutex::new(None),
            game_stop: Mutex::new(CancellationToken::new()),
            logs: Mutex::new(VecDeque::new()),
            pending_auth: Mutex::new(HashMap::new()),
            discord_status: RwLock::new("disabled".into()),
            activity: RwLock::new("Выбирает версию Minecraft".into()),
            exit: std::sync::atomic::AtomicBool::new(false),
        }));
        state.persist_settings()?;
        state.log(
            "info",
            "launcher",
            "WinLauncher 1.0.1. Телеметрия отключена. Данные хранятся локально.",
        );
        Ok(state)
    }
    pub fn emit<T: Serialize + Clone>(&self, event: &str, payload: T) -> tauri::Result<()> {
        if let Some(app) = &self.app {
            app.emit(event, payload)
        } else {
            Ok(())
        }
    }
    pub fn config(&self) -> Settings {
        self.settings
            .read()
            .unwrap_or_else(|e| e.into_inner())
            .clone()
    }
    pub fn root(&self) -> PathBuf {
        PathBuf::from(self.config().data_dir)
    }
    pub fn instance(&self, id: &str) -> Result<PathBuf> {
        security::safe_id(id)?;
        security::within(&self.root(), &format!("profiles/{id}"))
    }
    pub fn persist_settings(&self) -> Result<()> {
        let s = serde_json::to_string(&self.config())?;
        self.db.lock().unwrap_or_else(|e| e.into_inner()).execute("INSERT INTO settings(id,payload) VALUES(1,?1) ON CONFLICT(id) DO UPDATE SET payload=excluded.payload", [s])?;
        Ok(())
    }
    fn table(table: &str) -> Result<()> {
        if !["profiles", "accounts", "installed", "sessions"].contains(&table) {
            bail!("Некорректная таблица");
        }
        Ok(())
    }
    pub fn list<T: DeserializeOwned>(&self, table: &str) -> Result<Vec<T>> {
        Self::table(table)?;
        let conn = self.db.lock().unwrap_or_else(|e| e.into_inner());
        let mut query =
            conn.prepare(&format!("SELECT payload FROM {table} ORDER BY rowid DESC"))?;
        let rows = query.query_map([], |r| r.get::<_, String>(0))?;
        rows.map(|r| Ok(serde_json::from_str(&r?)?)).collect()
    }
    pub fn get<T: DeserializeOwned>(&self, table: &str, id: &str) -> Result<T> {
        Self::table(table)?;
        let json: String = self
            .db
            .lock()
            .unwrap_or_else(|e| e.into_inner())
            .query_row(
                &format!("SELECT payload FROM {table} WHERE id=?1"),
                [id],
                |r| r.get(0),
            )
            .context("Запись не найдена")?;
        Ok(serde_json::from_str(&json)?)
    }
    pub fn put<T: Serialize>(&self, table: &str, id: &str, item: &T) -> Result<()> {
        Self::table(table)?;
        self.db.lock().unwrap_or_else(|e| e.into_inner()).execute(&format!("INSERT INTO {table}(id,payload) VALUES(?1,?2) ON CONFLICT(id) DO UPDATE SET payload=excluded.payload"), params![id, serde_json::to_string(item)?])?;
        Ok(())
    }
    pub fn remove(&self, table: &str, id: &str) -> Result<()> {
        Self::table(table)?;
        self.db
            .lock()
            .unwrap_or_else(|e| e.into_inner())
            .execute(&format!("DELETE FROM {table} WHERE id=?1"), [id])?;
        Ok(())
    }
    pub fn has_running(&self) -> bool {
        self.running
            .lock()
            .unwrap_or_else(|e| e.into_inner())
            .is_some()
    }
    pub fn has_job(&self) -> bool {
        self.job.lock().unwrap_or_else(|e| e.into_inner()).is_some()
    }
    pub fn begin_job(&self, stage: &str) -> Result<JobGuard> {
        let mut lock = self.job.lock().unwrap_or_else(|e| e.into_inner());
        if lock.is_some() {
            bail!("Другая операция уже выполняется");
        }
        if self
            .running
            .lock()
            .unwrap_or_else(|e| e.into_inner())
            .is_some()
        {
            bail!("Сначала закройте Minecraft");
        }
        let token = CancellationToken::new();
        *self.cancel.lock().unwrap_or_else(|e| e.into_inner()) = token.clone();
        let job = Job {
            stage: stage.into(),
            detail: String::new(),
            completed_bytes: 0,
            total_bytes: 0,
            completed_files: 0,
            total_files: 0,
        };
        *lock = Some(job.clone());
        let _ = self.emit("download-progress", Some(job));
        Ok(JobGuard {
            state: self.clone(),
            token,
        })
    }
    pub fn progress(&self, update: impl FnOnce(&mut Job)) {
        if let Some(job) = self.job.lock().unwrap_or_else(|e| e.into_inner()).as_mut() {
            update(job);
            let _ = self.emit("download-progress", Some(job.clone()));
        }
    }
    pub fn stage(&self, name: &str, detail: &str) {
        self.progress(|j| {
            j.stage = name.into();
            j.detail = detail.into();
            j.completed_bytes = 0;
            j.total_bytes = 0;
            j.completed_files = 0;
            j.total_files = 0;
        });
    }
    pub fn log(&self, level: &str, source: &str, message: &str) {
        let entry = LogLine {
            timestamp: chrono::Utc::now().timestamp_millis(),
            level: level.into(),
            source: source.into(),
            message: security::redact(message, &[]),
        };
        let mut logs = self.logs.lock().unwrap_or_else(|e| e.into_inner());
        if logs.len() >= 5000 {
            logs.pop_front();
        }
        logs.push_back(entry.clone());
        drop(logs);
        let path = self
            .root()
            .join("logs")
            .join(format!("{}.log", chrono::Utc::now().format("%Y-%m-%d")));
        if fs::metadata(&path).map(|m| m.len()).unwrap_or(0) < 50 * 1024 * 1024 {
            if let Ok(mut f) = fs::OpenOptions::new().create(true).append(true).open(path) {
                let _ = writeln!(
                    f,
                    "{} [{}] [{}] {}",
                    entry.timestamp, level, source, entry.message
                );
            }
        }
        let _ = self.emit("log-line", entry);
    }
    pub fn notify(&self, kind: &str, message: &str) {
        let _ = self.emit(
            "notification",
            serde_json::json!({ "kind": kind, "message": message }),
        );
    }
    pub fn changed(&self) {
        let _ = self.emit("data-changed", ());
    }
}

pub struct JobGuard {
    state: AppState,
    pub token: CancellationToken,
}
impl Drop for JobGuard {
    fn drop(&mut self) {
        *self.state.job.lock().unwrap_or_else(|e| e.into_inner()) = None;
        let _ = self.state.emit("download-progress", Option::<Job>::None);
        self.state.changed();
    }
}

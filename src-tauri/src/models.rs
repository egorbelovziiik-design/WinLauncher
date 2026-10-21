use serde::{Deserialize, Serialize};

#[derive(Clone, Debug, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "lowercase")]
pub enum Loader {
    Vanilla,
    Fabric,
    Forge,
    Neoforge,
    Quilt,
}
impl Loader {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Vanilla => "vanilla",
            Self::Fabric => "fabric",
            Self::Forge => "forge",
            Self::Neoforge => "neoforge",
            Self::Quilt => "quilt",
        }
    }
}

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(default)]
pub struct DiscordSettings {
    pub enabled: bool,
    pub application_id: String,
    pub show_version: bool,
    pub show_pack: bool,
    pub show_elapsed: bool,
    pub show_download: bool,
    pub show_launcher: bool,
    pub hide_while_playing: bool,
    pub show_website: bool,
}
impl Default for DiscordSettings {
    fn default() -> Self {
        Self {
            enabled: false,
            application_id: String::new(),
            show_version: true,
            show_pack: true,
            show_elapsed: true,
            show_download: true,
            show_launcher: true,
            hide_while_playing: false,
            show_website: false,
        }
    }
}

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(default)]
pub struct Settings {
    pub theme: String,
    pub language: String,
    pub effects: bool,
    pub selected_profile: Option<String>,
    pub selected_account: Option<String>,
    pub microsoft_client_id: String,
    pub data_dir: String,
    pub download_concurrency: usize,
    pub download_limit_kib: u64,
    pub show_snapshots: bool,
    pub close_to_tray: bool,
    pub launch_hotkey: String,
    pub check_updates: bool,
    pub favorite_versions: Vec<String>,
    pub favorite_projects: Vec<String>,
    pub discord: DiscordSettings,
}
impl Default for Settings {
    fn default() -> Self {
        Self {
            theme: "overworld".into(),
            language: "ru".into(),
            effects: true,
            selected_profile: None,
            selected_account: None,
            microsoft_client_id: String::new(),
            data_dir: String::new(),
            download_concurrency: 6,
            download_limit_kib: 0,
            show_snapshots: false,
            close_to_tray: false,
            launch_hotkey: "Ctrl+Enter".into(),
            check_updates: true,
            favorite_versions: vec![],
            favorite_projects: vec![],
            discord: DiscordSettings::default(),
        }
    }
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct Profile {
    pub id: String,
    pub name: String,
    pub game_version: String,
    pub loader: Loader,
    pub loader_version: Option<String>,
    pub min_memory_mb: u32,
    pub max_memory_mb: u32,
    pub java_path: Option<String>,
    pub java_args: Vec<String>,
    pub width: u32,
    pub height: u32,
    pub fullscreen: bool,
    pub created_at: i64,
    pub last_played: Option<i64>,
    pub play_seconds: u64,
    pub installed: bool,
    pub modpack: Option<String>,
}
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct Account {
    pub id: String,
    pub name: String,
    pub skin_url: Option<String>,
    pub added_at: i64,
}
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct InstalledFile {
    pub profile_id: String,
    pub provider: String,
    pub project_id: String,
    pub version_id: String,
    pub name: String,
    pub filename: String,
    pub kind: String,
    pub sha: String,
    pub hash_algorithm: String,
    pub enabled: bool,
    pub download_url: String,
    pub size: u64,
}
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct Session {
    pub id: String,
    pub profile_id: String,
    pub account_id: String,
    pub version: String,
    pub loader: String,
    pub started_at: i64,
    pub duration: u64,
    pub exit_code: Option<i32>,
}
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct Job {
    pub stage: String,
    pub detail: String,
    pub completed_bytes: u64,
    pub total_bytes: u64,
    pub completed_files: usize,
    pub total_files: usize,
}
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct RunningGame {
    pub pid: u32,
    pub profile_id: String,
    pub version: String,
    pub loader: String,
    pub profile_name: String,
    pub started_at: i64,
}
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct LogLine {
    pub timestamp: i64,
    pub level: String,
    pub source: String,
    pub message: String,
}
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct SystemInfo {
    pub cpu: String,
    pub gpu: String,
    pub os: String,
    pub arch: String,
    pub total_memory_mb: u64,
    pub available_memory_mb: u64,
    pub free_disk_bytes: u64,
}
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct SearchProject {
    pub id: String,
    pub provider: String,
    pub title: String,
    pub description: String,
    pub icon_url: Option<String>,
    pub downloads: u64,
    pub author: String,
    pub categories: Vec<String>,
    pub project_type: String,
    pub slug: String,
}
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct SearchResults {
    pub hits: Vec<SearchProject>,
    pub total: u64,
    pub offset: u64,
}
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct ProjectDetail {
    pub project: SearchProject,
    pub body: String,
    pub gallery: Vec<String>,
    pub website: String,
}

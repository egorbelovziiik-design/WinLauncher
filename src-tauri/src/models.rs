use serde::{Deserialize, Serialize};

fn now_iso() -> String {
    chrono::Utc::now().to_rfc3339_opts(chrono::SecondsFormat::Secs, true)
}

/* ----------------------------- Minecraft ----------------------------- */

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct McVersion {
    pub id: String,
    #[serde(rename = "type")]
    pub version_type: String,
    pub release_time: String,
    pub url: String,
    #[serde(default)]
    pub sha1: Option<String>,
    #[serde(default)]
    pub compliance_level: Option<i64>,
    #[serde(default)]
    pub installed: Option<bool>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct LoaderRef {
    pub kind: String,
    pub version: Option<String>,
}

impl Default for LoaderRef {
    fn default() -> Self {
        Self { kind: "vanilla".into(), version: None }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ModpackRef {
    pub project_id: String,
    pub version_id: String,
    pub name: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Profile {
    pub id: String,
    pub name: String,
    pub mc_version: String,
    pub loader: LoaderRef,
    pub game_dir: String,
    pub ram_mb: u64,
    pub java_path: Option<String>,
    pub jvm_args: String,
    pub width: u32,
    pub height: u32,
    pub fullscreen: bool,
    pub created_at: String,
    pub last_played_at: Option<String>,
    pub source: String,
    pub modpack: Option<ModpackRef>,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ProfileDraft {
    pub name: String,
    pub mc_version: String,
    #[serde(default)]
    pub loader: LoaderRef,
    #[serde(default)]
    pub game_dir: Option<String>,
    #[serde(default)]
    pub ram_mb: Option<u64>,
    #[serde(default)]
    pub java_path: Option<String>,
    #[serde(default)]
    pub jvm_args: Option<String>,
    #[serde(default)]
    pub width: Option<u32>,
    #[serde(default)]
    pub height: Option<u32>,
    #[serde(default)]
    pub fullscreen: Option<bool>,
    #[serde(default)]
    pub source: Option<String>,
    #[serde(default)]
    pub modpack: Option<ModpackRef>,
}

/* ------------------------------ Accounts ----------------------------- */

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Skin {
    pub id: String,
    pub state: String,
    pub url: String,
    pub variant: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Account {
    pub id: String,
    pub kind: String,
    pub name: String,
    pub uuid: String,
    pub created_at: String,
    pub last_used_at: Option<String>,
    pub owned: Option<bool>,
    #[serde(default)]
    pub skins: Vec<Skin>,
    pub primary: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DeviceCodeInfo {
    pub device_code: String,
    pub user_code: String,
    pub verification_uri: String,
    pub expires_in: u64,
    pub interval: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct LaunchAccount {
    pub name: String,
    pub uuid: String,
    pub access_token: String,
    pub user_type: String,
    pub xuid: String,
    pub user_properties: Vec<(String, String)>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct StoredTokens {
    /// Which Microsoft flow produced the refresh token ("live" or "azure").
    #[serde(default)]
    pub flow: String,
    pub ms_access_token: String,
    pub ms_refresh_token: String,
    pub mc_access_token: String,
    pub mc_expires_at: i64,
    pub xuid: String,
    pub owned: bool,
}

/* ------------------------------- Launch ------------------------------ */

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LaunchStatus {
    pub profile_id: Option<String>,
    pub state: String,
    pub message: String,
    pub code: Option<i32>,
    pub started_at: Option<String>,
}

impl Default for LaunchStatus {
    fn default() -> Self {
        Self { profile_id: None, state: "idle".into(), message: String::new(), code: None, started_at: None }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct JavaRuntime {
    pub path: String,
    pub version: String,
    pub major: u32,
    pub vendor: String,
    pub arch: String,
    pub source: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ProfileStatus {
    pub profile_id: String,
    pub version_installed: bool,
    pub loader_installed: bool,
    pub loader_name: String,
    pub missing_files: usize,
    pub total_files: usize,
    pub installed_bytes: u64,
    pub required_java: u32,
    pub java: Option<JavaRuntime>,
    pub ready: bool,
    pub blocking: Vec<String>,
}

/* ----------------------------- Downloads ----------------------------- */

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DownloadItem {
    pub id: String,
    pub title: String,
    pub detail: String,
    pub status: String,
    pub current: u64,
    pub total: u64,
    pub bytes: u64,
    pub total_bytes: u64,
    pub speed: f64,
    pub started_at: i64,
    pub finished_at: Option<i64>,
    pub error: Option<String>,
    pub profile_id: Option<String>,
}

/* ------------------------------ Settings ----------------------------- */

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CustomTheme {
    pub accent: String,
    pub accent2: String,
    pub background: String,
    pub transparency: u32,
}

impl Default for CustomTheme {
    fn default() -> Self {
        Self {
            accent: "#58d68d".into(),
            accent2: "#489eff".into(),
            background: "#0f1118".into(),
            transparency: 62,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Settings {
    pub locale: String,
    pub locale_touched: bool,
    pub theme: String,
    pub custom_theme: CustomTheme,
    pub animations: String,
    pub ui_scale: f64,
    pub minecraft_dir: String,
    pub default_ram_mb: u64,
    pub java_path: Option<String>,
    pub jvm_args: String,
    pub resolution_width: u32,
    pub resolution_height: u32,
    pub fullscreen: bool,
    pub max_concurrent_downloads: usize,
    pub speed_limit_kbps: u64,
    pub auto_update: bool,
    pub launch_on_startup: bool,
    pub minimize_on_launch: bool,
    pub close_on_launch: bool,
    pub onboarded: bool,
    pub active_profile_id: Option<String>,
    pub active_account_id: Option<String>,
    pub curseforge_key_set: bool,
}

impl Settings {
    pub fn with_defaults(dir: String, ram_mb: u64) -> Self {
        Self {
            locale: "ru".into(),
            locale_touched: false,
            theme: "dark".into(),
            custom_theme: CustomTheme::default(),
            animations: "full".into(),
            ui_scale: 1.0,
            minecraft_dir: dir,
            default_ram_mb: ram_mb,
            java_path: None,
            jvm_args: "-XX:+UseG1GC -XX:+UnlockExperimentalVMOptions -XX:G1HeapRegionSize=4M".into(),
            resolution_width: 1280,
            resolution_height: 720,
            fullscreen: false,
            max_concurrent_downloads: 8,
            speed_limit_kbps: 0,
            auto_update: true,
            launch_on_startup: false,
            minimize_on_launch: true,
            close_on_launch: false,
            onboarded: false,
            active_profile_id: None,
            active_account_id: None,
            curseforge_key_set: false,
        }
    }
}

/* ------------------------------- Info -------------------------------- */

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AppInfo {
    pub version: String,
    pub os: String,
    pub arch: String,
    pub data_dir: String,
    pub total_ram_mb: u64,
    pub available_ram_mb: u64,
    pub tauri: bool,
    pub preview: bool,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct NewsItem {
    pub id: String,
    pub title: String,
    pub text: String,
    pub image: String,
    pub category: String,
    pub date: String,
    pub url: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LogLine {
    pub ts: String,
    pub level: String,
    pub source: String,
    pub line: String,
}

impl LogLine {
    pub fn new(level: &str, source: &str, line: impl Into<String>) -> Self {
        Self {
            ts: now_iso(),
            level: level.into(),
            source: source.into(),
            line: line.into(),
        }
    }
}

/* ------------------------------ Modrinth ----------------------------- */

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ModrinthHit {
    pub project_id: String,
    pub slug: String,
    pub title: String,
    pub description: String,
    pub categories: Vec<String>,
    pub project_type: String,
    pub downloads: u64,
    pub follows: u64,
    pub icon_url: Option<String>,
    pub author: String,
    pub versions: Vec<String>,
    pub date_modified: String,
    pub gallery: Vec<String>,
    #[serde(default)]
    pub body: Option<String>,
    #[serde(default)]
    pub source_url: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ModrinthSearchResult {
    pub hits: Vec<ModrinthHit>,
    pub total_hits: u64,
    pub offset: u64,
    pub limit: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ModFile {
    pub filename: String,
    pub url: String,
    pub size: u64,
    pub hashes: std::collections::HashMap<String, String>,
    pub primary: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ModDependency {
    pub project_id: Option<String>,
    pub version_id: Option<String>,
    pub dependency_type: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ModrinthVersion {
    pub id: String,
    pub project_id: String,
    pub name: String,
    pub version_number: String,
    pub version_type: String,
    pub game_versions: Vec<String>,
    pub loaders: Vec<String>,
    pub date_published: String,
    pub downloads: u64,
    pub files: Vec<ModFile>,
    pub dependencies: Vec<ModDependency>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct InstalledMod {
    pub file_name: String,
    pub path: String,
    pub enabled: bool,
    pub size: u64,
    pub project_id: Option<String>,
    pub project_name: Option<String>,
    pub version_name: Option<String>,
    pub icon_url: Option<String>,
    pub source: String,
    #[serde(default)]
    pub update_version_id: Option<String>,
    #[serde(default)]
    pub update_version_name: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LoaderVersion {
    pub version: String,
    pub stable: bool,
    pub mc_version: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CurseForgeMod {
    pub id: u64,
    pub name: String,
    pub summary: String,
    pub download_count: u64,
    pub logo_url: Option<String>,
    pub categories: Vec<String>,
    pub authors: Vec<String>,
    pub website_url: Option<String>,
    pub latest_file_id: Option<u64>,
    pub latest_file_name: Option<String>,
    pub game_versions: Vec<String>,
    pub date_modified: String,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct CurseForgeStatus {
    pub available: bool,
    pub reason: Option<String>,
}

/* --------------------------- Mojang metadata -------------------------- */

#[derive(Debug, Clone, Deserialize)]
pub struct VersionManifest {
    pub latest: ManifestLatest,
    pub versions: Vec<ManifestVersion>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct ManifestLatest {
    pub release: String,
    pub snapshot: String,
}

#[derive(Debug, Clone, Deserialize)]
pub struct ManifestVersion {
    pub id: String,
    #[serde(rename = "type")]
    pub version_type: String,
    #[serde(rename = "releaseTime")]
    pub release_time: String,
    pub url: String,
    #[serde(default)]
    pub sha1: Option<String>,
    #[serde(default, rename = "complianceLevel")]
    pub compliance_level: Option<i64>,
}

#[derive(Debug, Clone, Deserialize, Default)]
pub struct VersionJson {
    #[serde(default)]
    pub id: String,
    #[serde(default, rename = "type")]
    pub version_type: String,
    #[serde(default, rename = "mainClass")]
    pub main_class: String,
    #[serde(default, rename = "inheritsFrom")]
    pub inherits_from: Option<String>,
    #[serde(default, rename = "assets")]
    pub assets: Option<String>,
    #[serde(default, rename = "assetIndex")]
    pub asset_index: Option<AssetIndexRef>,
    #[serde(default)]
    pub downloads: Option<Downloads>,
    #[serde(default)]
    pub libraries: Vec<Library>,
    #[serde(default)]
    pub arguments: Option<Arguments>,
    #[serde(default, rename = "minecraftArguments")]
    pub minecraft_arguments: Option<String>,
    #[serde(default, rename = "javaVersion")]
    pub java_version: Option<JavaVersion>,
    #[serde(default)]
    pub logging: Option<Logging>,
    #[serde(default, rename = "releaseTime")]
    pub release_time: Option<String>,
}

#[derive(Debug, Clone, Deserialize, Default)]
pub struct Downloads {
    #[serde(default)]
    pub client: Option<ClientDownload>,
}

#[derive(Debug, Clone, Deserialize, Default)]
pub struct ClientDownload {
    #[serde(default)]
    pub url: Option<String>,
    #[serde(default)]
    pub sha1: Option<String>,
    #[serde(default)]
    pub size: u64,
}

#[derive(Debug, Clone, Deserialize, Default)]
pub struct AssetIndexRef {
    #[serde(default)]
    pub id: String,
    #[serde(default)]
    pub url: Option<String>,
    #[serde(default)]
    pub sha1: Option<String>,
    #[serde(default)]
    pub size: u64,
    #[serde(default)]
    pub total_size: u64,
    #[serde(default, rename = "virtual")]
    pub virtual_: bool,
}

#[derive(Debug, Clone, Deserialize, Default)]
pub struct JavaVersion {
    #[serde(default, rename = "majorVersion")]
    pub major_version: u32,
    #[serde(default, rename = "component")]
    pub component: String,
}

#[derive(Debug, Clone, Deserialize, Default)]
pub struct Arguments {
    #[serde(default)]
    pub game: Vec<serde_json::Value>,
    #[serde(default)]
    pub jvm: Vec<serde_json::Value>,
}

#[derive(Debug, Clone, Deserialize, Default)]
pub struct Logging {
    pub client: Option<LoggingClient>,
}

#[derive(Debug, Clone, Deserialize, Default)]
pub struct LoggingClient {
    pub argument: Option<String>,
    pub file: Option<LoggingFile>,
    #[serde(rename = "type", default)]
    pub log_type: String,
}

#[derive(Debug, Clone, Deserialize, Default)]
pub struct LoggingFile {
    #[serde(default)]
    pub id: String,
    #[serde(default)]
    pub url: String,
    #[serde(default)]
    pub sha1: Option<String>,
    #[serde(default)]
    pub size: u64,
}

#[derive(Debug, Clone, Deserialize, Default)]
pub struct Library {
    #[serde(default)]
    pub name: String,
    /// Maven base url, used when a version file has no resolved `downloads`.
    #[serde(default)]
    pub url: String,
    #[serde(default)]
    pub downloads: LibraryDownloads,
    #[serde(default)]
    pub natives: Option<std::collections::HashMap<String, String>>,
    #[serde(default)]
    pub rules: Vec<Rule>,
    #[serde(default)]
    pub extract: Option<Extract>,
}

#[derive(Debug, Clone, Deserialize, Default)]
pub struct LibraryDownloads {
    #[serde(default)]
    pub artifact: Option<LibraryArtifact>,
    #[serde(default)]
    pub classifiers: Option<std::collections::HashMap<String, LibraryArtifact>>,
}

#[derive(Debug, Clone, Deserialize, Default)]
pub struct LibraryArtifact {
    #[serde(default)]
    pub path: String,
    #[serde(default)]
    pub url: String,
    #[serde(default)]
    pub sha1: Option<String>,
    #[serde(default)]
    pub size: u64,
}

#[derive(Debug, Clone, Deserialize, Default)]
pub struct Extract {
    #[serde(default)]
    pub exclude: Vec<String>,
}

#[derive(Debug, Clone, Deserialize, Default)]
pub struct Rule {
    #[serde(default)]
    pub action: String,
    #[serde(default)]
    pub os: Option<RuleOs>,
    #[serde(default)]
    pub features: Option<std::collections::HashMap<String, bool>>,
}

#[derive(Debug, Clone, Deserialize, Default)]
pub struct RuleOs {
    #[serde(default)]
    pub name: Option<String>,
    #[serde(default)]
    pub arch: Option<String>,
    #[serde(default)]
    pub version: Option<String>,
}

#[derive(Debug, Clone, Deserialize, Default)]
pub struct AssetIndexFile {
    #[serde(default)]
    pub objects: std::collections::HashMap<String, AssetObject>,
    #[serde(default)]
    pub map_to_resources: Option<bool>,
    #[serde(default, rename = "virtual")]
    pub virtual_: Option<bool>,
}

#[derive(Debug, Clone, Deserialize, Default)]
pub struct AssetObject {
    #[serde(default)]
    pub hash: String,
    #[serde(default)]
    pub size: u64,
}

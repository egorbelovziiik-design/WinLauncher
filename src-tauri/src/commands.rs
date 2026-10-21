use std::path::{Path, PathBuf};
use std::process::Command;

use serde_json::Value;
use tauri::{AppHandle, Manager};

use crate::accounts;
use crate::curseforge;
use crate::download;
use crate::java;
use crate::launch;
use crate::loaders;
use crate::models::*;
use crate::modpack;
use crate::modrinth;
use crate::mojang;
use crate::news;
use crate::profiles;
use crate::state::{lock, wlock, AppState};
use crate::util;

/// Translates internal `key|detail` errors into something the UI can show.
pub fn map_error(error: String) -> String {
    if let Some((head, tail)) = error.split_once('|') {
        if head.contains('.') && !head.contains(' ') {
            return head.to_string();
        }
        return tail.to_string();
    }
    if error == "cancelled" {
        return "errors.cancelled".into();
    }
    error
}

fn err<T>(result: Result<T, String>) -> Result<T, String> {
    result.map_err(map_error)
}

/* ------------------------------------------------------------------ */
/* App / settings                                                      */
/* ------------------------------------------------------------------ */

#[tauri::command]
pub fn app_boot(app: AppHandle) -> Value {
    let state = app.state::<AppState>();
    let mut system = sysinfo::System::new();
    system.refresh_memory();
    let info = AppInfo {
        version: env!("CARGO_PKG_VERSION").to_string(),
        os: std::env::consts::OS.to_string(),
        arch: std::env::consts::ARCH.to_string(),
        data_dir: state.paths().data.to_string_lossy().to_string(),
        total_ram_mb: system.total_memory() / 1024 / 1024,
        available_ram_mb: system.available_memory() / 1024 / 1024,
        tauri: true,
        preview: false,
    };
    serde_json::json!({ "info": info })
}

#[tauri::command]
pub fn settings_load(app: AppHandle) -> Settings {
    app.state::<AppState>().settings()
}

#[tauri::command]
pub fn settings_save(app: AppHandle, settings: Settings) -> Settings {
    let state = app.state::<AppState>();
    let previous = state.settings();
    let mut next = settings;
    next.ui_scale = next.ui_scale.clamp(0.75, 1.5);
    next.max_concurrent_downloads = next.max_concurrent_downloads.clamp(1, 32);
    next.default_ram_mb = next.default_ram_mb.clamp(512, 65_536);
    next.speed_limit_kbps = next.speed_limit_kbps.min(1_000_000);
    next.curseforge_key_set = state.secrets.get("curseforge-key").is_some();
    *wlock(&state.settings) = next.clone();
    state.save_settings();
    if previous.launch_on_startup != next.launch_on_startup {
        let _ = apply_autostart(next.launch_on_startup);
    }
    next
}

#[tauri::command]
pub fn settings_reset(app: AppHandle) -> Settings {
    let state = app.state::<AppState>();
    let current = state.settings();
    let mut system = sysinfo::System::new();
    system.refresh_memory();
    let total = (system.total_memory() / 1024 / 1024) as u64;
    let default_ram = ((total / 2).clamp(2048, 8192)) as u64;
    let mut fresh = Settings::with_defaults(current.minecraft_dir.clone(), default_ram);
    fresh.locale = current.locale.clone();
    fresh.locale_touched = current.locale_touched;
    fresh.onboarded = true;
    fresh.curseforge_key_set = state.secrets.get("curseforge-key").is_some();
    fresh.active_profile_id = current.active_profile_id.clone();
    fresh.active_account_id = current.active_account_id.clone();
    *wlock(&state.settings) = fresh.clone();
    state.save_settings();
    fresh
}

/* ------------------------------------------------------------------ */
/* Profiles                                                            */
/* ------------------------------------------------------------------ */

#[tauri::command]
pub fn profiles_list(app: AppHandle) -> Vec<Profile> {
    app.state::<AppState>().profiles()
}

#[tauri::command]
pub fn profile_create(app: AppHandle, draft: ProfileDraft) -> Result<Profile, String> {
    err(profiles::create(&app, draft))
}

#[tauri::command]
pub fn profile_update(app: AppHandle, profile: Profile) -> Result<Profile, String> {
    err(profiles::update(&app, profile))
}

#[tauri::command]
pub fn profile_delete(app: AppHandle, profile_id: String) -> Result<(), String> {
    err(profiles::delete(&app, &profile_id))
}

#[tauri::command]
pub fn profile_open_folder(app: AppHandle, profile_id: String) -> Result<(), String> {
    let state = app.state::<AppState>();
    let dir = err(state.game_dir_for(&profile_id))?;
    std::fs::create_dir_all(&dir).map_err(|e| e.to_string())?;
    util::open_target(&dir.to_string_lossy())
}

#[tauri::command]
pub async fn profile_status(app: AppHandle, profile_id: String) -> Result<ProfileStatus, String> {
    let state = app.state::<AppState>();
    let profile = state
        .profile_by_id(&profile_id)
        .ok_or_else(|| "profiles.notFound".to_string())?;
    let settings = state.settings();
    let paths = state.paths();
    let loader_id = loaders::resolved_version_id(&profile, &paths);
    let version_id = loader_id.clone().unwrap_or_else(|| profile.mc_version.clone());

    let mut status = ProfileStatus {
        profile_id: profile.id.clone(),
        version_installed: false,
        loader_installed: !loaders::is_modded(&profile.loader.kind) || loader_id.is_some(),
        loader_name: profile.loader.kind.clone(),
        missing_files: 0,
        total_files: 0,
        installed_bytes: 0,
        required_java: mojang::java_major_for(&profile.mc_version),
        java: None,
        ready: false,
        blocking: Vec::new(),
    };

    if let Ok(merged) = mojang::load_merged(&app, &version_id).await {
        let (missing, total, bytes, installed) = mojang::install_status(&app, &merged);
        status.missing_files = missing;
        status.total_files = total;
        status.installed_bytes = bytes;
        status.version_installed = installed || loader_id.is_some();
        status.required_java = merged.required_java();
    } else {
        status.version_installed = paths
            .version_dir(&profile.mc_version)
            .join(format!("{}.jar", profile.mc_version))
            .is_file();
    }

    let configured = profile.java_path.clone().or(settings.java_path.clone());
    let mut candidates = java::list(&app);
    if let Some(path) = configured.as_deref() {
        if let Some(runtime) = java::looks_like_java(path) {
            candidates.insert(0, runtime);
        }
    }
    candidates.sort_by_key(|r| r.major);
    status.java = candidates
        .iter()
        .find(|r| r.major == status.required_java)
        .or_else(|| {
            if status.required_java <= 8 {
                candidates.iter().find(|r| r.major == 8)
            } else {
                candidates.iter().find(|r| r.major >= status.required_java)
            }
        })
        .cloned();

    if !status.version_installed {
        status.blocking.push("version".into());
    }
    if !status.loader_installed {
        status.blocking.push("loader".into());
    }
    if status.java.is_none() {
        status.blocking.push("java".into());
    }
    status.ready = status.blocking.is_empty();
    Ok(status)
}

#[tauri::command]
pub async fn verify_files(app: AppHandle, profile_id: String) -> Result<ProfileStatus, String> {
    let status = profile_status(app.clone(), profile_id.clone()).await?;
    app.state::<AppState>()
        .log("info", "launcher", format!("Verified profile {profile_id}: {} / {} files missing", status.missing_files, status.total_files));
    Ok(status)
}

/* ------------------------------------------------------------------ */
/* Accounts                                                            */
/* ------------------------------------------------------------------ */

#[tauri::command]
pub fn accounts_list(app: AppHandle) -> Vec<Account> {
    app.state::<AppState>().accounts()
}

#[tauri::command]
pub fn account_add_offline(app: AppHandle, name: String) -> Result<Account, String> {
    err(accounts::add_offline(&app, &name))
}

#[tauri::command]
pub fn account_delete(app: AppHandle, id: String) -> Result<(), String> {
    err(accounts::delete(&app, &id))
}

#[tauri::command]
pub fn account_set_primary(app: AppHandle, id: String) -> Result<(), String> {
    err(accounts::set_primary(&app, &id))
}

#[tauri::command]
pub async fn ms_begin(app: AppHandle) -> Result<DeviceCodeInfo, String> {
    err(accounts::begin(&app).await)
}

#[tauri::command]
pub async fn ms_poll(app: AppHandle, device_code: String) -> Result<Option<Account>, String> {
    err(accounts::poll(&app, &device_code).await)
}

#[tauri::command]
pub async fn ms_refresh(app: AppHandle, id: String) -> Result<Account, String> {
    err(accounts::refresh(&app, &id).await)
}

/* ------------------------------------------------------------------ */
/* Java                                                                */
/* ------------------------------------------------------------------ */

#[tauri::command]
pub fn java_list(app: AppHandle) -> Vec<JavaRuntime> {
    java::list(&app)
}

#[tauri::command]
pub async fn java_detect(app: AppHandle) -> Result<Vec<JavaRuntime>, String> {
    let app_for_scan = app.clone();
    let detected = tauri::async_runtime::spawn_blocking(move || java::detect(&app_for_scan))
        .await
        .map_err(|e| e.to_string())?;
    for runtime in &detected {
        java::add(&app, &runtime.path).ok();
    }
    Ok(java::list(&app))
}

#[tauri::command]
pub fn java_add(app: AppHandle, path: String) -> Result<JavaRuntime, String> {
    err(java::add(&app, &path))
}

#[tauri::command]
pub async fn java_download(app: AppHandle, major: u32) -> Result<JavaRuntime, String> {
    err(java::download(&app, major).await)
}

/* ------------------------------------------------------------------ */
/* Versions                                                            */
/* ------------------------------------------------------------------ */

#[tauri::command]
pub async fn mc_versions(app: AppHandle) -> Result<Vec<McVersion>, String> {
    err(mojang::list_versions(&app).await)
}

#[tauri::command]
pub async fn install_version(app: AppHandle, mc_version: String) -> Result<(), String> {
    let merged = err(mojang::load_merged(&app, &mc_version).await)?;
    err(mojang::ensure_version(&app, &merged, None, None).await)?;
    app.state::<AppState>()
        .log("info", "launcher", format!("Minecraft {mc_version} installed"));
    Ok(())
}

#[tauri::command]
pub fn delete_version(app: AppHandle, mc_version: String) -> Result<(), String> {
    err(mojang::delete_version(&app, &mc_version))
}

#[tauri::command]
pub async fn loader_versions(
    app: AppHandle,
    kind: String,
    mc_version: String,
) -> Result<Vec<LoaderVersion>, String> {
    err(loaders::versions(&app, &kind, &mc_version).await)
}

#[tauri::command]
pub async fn news_list(app: AppHandle) -> Vec<NewsItem> {
    news::list(&app).await
}

/* ------------------------------------------------------------------ */
/* Downloads                                                           */
/* ------------------------------------------------------------------ */

#[tauri::command]
pub fn downloads_list(app: AppHandle) -> Vec<DownloadItem> {
    lock(&app.state::<AppState>().downloads).clone()
}

#[tauri::command]
pub fn downloads_cancel(app: AppHandle, id: String) -> Result<(), String> {
    let state = app.state::<AppState>();
    let cancel = lock(&state.cancel).get(&id).cloned();
    match cancel {
        Some(flag) => {
            flag.store(true, std::sync::atomic::Ordering::Relaxed);
            Ok(())
        }
        None => Err("This download already finished".into()),
    }
}

#[tauri::command]
pub async fn downloads_retry(app: AppHandle, id: String) -> Result<(), String> {
    let state = app.state::<AppState>();
    let job = lock(&state.retry).get(&id).cloned();
    let Some(job) = job else {
        return Err("Nothing left to retry for this download".into());
    };
    {
        let mut list = lock(&state.downloads);
        list.retain(|item| item.id != id);
    }
    state.emit_downloads();
    err(download::download_files(app.clone(), job.title, job.profile_id, job.files).await)
}

#[tauri::command]
pub fn downloads_clear(app: AppHandle) {
    let state = app.state::<AppState>();
    {
        let mut list = lock(&state.downloads);
        list.retain(|item| item.status == "running" || item.status == "queued");
    }
    state.emit_downloads();
}

/* ------------------------------------------------------------------ */
/* Logs & folders                                                      */
/* ------------------------------------------------------------------ */

#[tauri::command]
pub fn logs_tail(app: AppHandle, limit: usize) -> Vec<LogLine> {
    let state = app.state::<AppState>();
    let logs = lock(&state.logs);
    let skip = logs.len().saturating_sub(limit.clamp(1, 4000));
    logs.iter().skip(skip).cloned().collect()
}

#[tauri::command]
pub fn logs_clear(app: AppHandle) {
    let state = app.state::<AppState>();
    lock(&state.logs).clear();
    let _ = std::fs::write(state.paths().logs.join("launcher.log"), "");
    let _ = std::fs::write(state.paths().logs.join("game.log"), "");
}

#[tauri::command]
pub fn logs_export(app: AppHandle) -> Result<String, String> {
    let state = app.state::<AppState>();
    let logs = lock(&state.logs).clone();
    let stamp = chrono::Local::now().format("%Y%m%d-%H%M%S");
    let file = state.paths().logs.join(format!("WinLaucher-log-{stamp}.txt"));
    let mut text = String::new();
    for line in logs {
        text.push_str(&format!("[{}] {:<5} {:<8} {}\n", line.ts, line.level, line.source, line.line));
    }
    std::fs::write(&file, text).map_err(|e| e.to_string())?;
    util::open_target(&file.to_string_lossy())?;
    Ok(file.to_string_lossy().to_string())
}

#[tauri::command]
pub fn app_open_logs_dir(app: AppHandle) -> Result<(), String> {
    let dir = app.state::<AppState>().paths().logs.clone();
    std::fs::create_dir_all(&dir).map_err(|e| e.to_string())?;
    util::open_target(&dir.to_string_lossy())
}

#[tauri::command]
pub fn app_open_data_dir(app: AppHandle) -> Result<(), String> {
    let dir = app.state::<AppState>().paths().data.clone();
    std::fs::create_dir_all(&dir).map_err(|e| e.to_string())?;
    util::open_target(&dir.to_string_lossy())
}

#[tauri::command]
pub fn app_open_url(url: String) -> Result<(), String> {
    if !url.starts_with("https://") && !url.starts_with("http://") {
        return Err("Only http(s) links are allowed".into());
    }
    util::open_target(&url)
}

#[tauri::command]
pub fn app_open_path(path: String) -> Result<(), String> {
    let target = PathBuf::from(&path);
    if !target.exists() {
        return Err("errors.fileMissing".into());
    }
    util::open_target(&target.to_string_lossy())
}

/* ------------------------------------------------------------------ */
/* Maintenance                                                         */
/* ------------------------------------------------------------------ */

#[tauri::command]
pub fn cache_size(app: AppHandle) -> u64 {
    util::dir_size(&app.state::<AppState>().paths().cache)
}

#[tauri::command]
pub fn cache_clear(app: AppHandle) -> u64 {
    let state = app.state::<AppState>();
    let cache = state.paths().cache.clone();
    let before = util::dir_size(&cache);
    if let Ok(entries) = std::fs::read_dir(&cache) {
        for entry in entries.flatten() {
            let path = entry.path();
            if path.is_dir() {
                let _ = std::fs::remove_dir_all(&path);
            } else {
                let _ = std::fs::remove_file(&path);
            }
        }
    }
    let after = util::dir_size(&cache);
    state.log("info", "launcher", format!("Cache cleared ({} bytes)", before.saturating_sub(after)));
    before.saturating_sub(after)
}

#[cfg(windows)]
fn autostart_command(enabled: bool) -> Result<bool, String> {
    let exe = std::env::current_exe().map_err(|e| e.to_string())?;
    let exe_arg = format!("\"{}\"", exe.to_string_lossy());
    let mut cmd = Command::new("reg");
    if enabled {
        cmd.arg("add")
            .arg(r"HKCU\Software\Microsoft\Windows\CurrentVersion\Run")
            .arg("/v")
            .arg("WinLaucher")
            .arg("/t")
            .arg("REG_SZ")
            .arg("/d")
            .arg(&exe_arg)
            .arg("/f");
    } else {
        cmd.arg("delete")
            .arg(r"HKCU\Software\Microsoft\Windows\CurrentVersion\Run")
            .arg("/v")
            .arg("WinLaucher")
            .arg("/f");
    }
    cmd.stdout(std::process::Stdio::null())
        .stderr(std::process::Stdio::null());
    util::hide_console(&mut cmd);
    let status = cmd.status().map_err(|e| e.to_string())?;
    Ok(status.success())
}

#[cfg(not(windows))]
fn autostart_command(_enabled: bool) -> Result<bool, String> {
    Err("Autostart is only implemented on Windows".into())
}

fn apply_autostart(enabled: bool) -> Result<bool, String> {
    autostart_command(enabled)?;
    Ok(enabled)
}

#[tauri::command]
pub fn autostart_get() -> bool {
    #[cfg(windows)]
    {
        let mut cmd = Command::new("reg");
        cmd.args([
            "query",
            r"HKCU\Software\Microsoft\Windows\CurrentVersion\Run",
            "/v",
            "WinLaucher",
        ]);
        cmd.stdout(std::process::Stdio::null()).stderr(std::process::Stdio::null());
        util::hide_console(&mut cmd);
        return cmd.status().map(|s| s.success()).unwrap_or(false);
    }
    #[cfg(not(windows))]
    {
        false
    }
}

#[tauri::command]
pub fn autostart_set(app: AppHandle, enabled: bool) -> Result<bool, String> {
    let value = apply_autostart(enabled)?;
    let state = app.state::<AppState>();
    let mut settings = state.settings();
    settings.launch_on_startup = value;
    *wlock(&state.settings) = settings;
    state.save_settings();
    Ok(value)
}

/* ------------------------------------------------------------------ */
/* Launch                                                              */
/* ------------------------------------------------------------------ */

#[tauri::command]
pub fn launch_start(app: AppHandle, profile_id: String, account_id: String) -> Result<(), String> {
    let state = app.state::<AppState>();
    if lock(&state.child).is_some() {
        return Err("errors.alreadyRunning".into());
    }
    if state.profile_by_id(&profile_id).is_none() {
        return Err("profiles.notFound".into());
    }
    if !state.accounts().iter().any(|a| a.id == account_id) {
        return Err("accounts.noAccounts".into());
    }
    launch::start_launch(app, profile_id, account_id);
    Ok(())
}

#[tauri::command]
pub fn launch_stop(app: AppHandle) -> Result<(), String> {
    err(launch::stop(&app))
}

/* ------------------------------------------------------------------ */
/* Modrinth                                                            */
/* ------------------------------------------------------------------ */

#[tauri::command]
pub async fn modrinth_search(
    app: AppHandle,
    query: Option<String>,
    limit: Option<u64>,
    offset: Option<u64>,
    loader: Option<String>,
    game_version: Option<String>,
    sort: Option<String>,
) -> Result<ModrinthSearchResult, String> {
    err(
        modrinth::search(
            &app,
            query.as_deref().unwrap_or(""),
            limit.unwrap_or(24),
            offset.unwrap_or(0),
            loader,
            game_version,
            sort,
            "mod",
        )
        .await,
    )
}

#[tauri::command]
pub async fn modrinth_tags(app: AppHandle) -> Result<Value, String> {
    err(modrinth::tags(&app).await)
}

#[tauri::command]
pub async fn modrinth_project(app: AppHandle, id_or_slug: String) -> Result<ModrinthHit, String> {
    err(modrinth::project(&app, &id_or_slug).await)
}

#[tauri::command]
pub async fn modrinth_versions(
    app: AppHandle,
    project_id: String,
    game_version: Option<String>,
    loader: Option<String>,
) -> Result<Vec<ModrinthVersion>, String> {
    err(modrinth::versions(&app, &project_id, game_version, loader).await)
}

/* ------------------------------------------------------------------ */
/* Mods                                                                */
/* ------------------------------------------------------------------ */

#[tauri::command]
pub fn mods_installed(app: AppHandle, profile_id: String) -> Result<Vec<InstalledMod>, String> {
    err(modrinth::installed(&app, &profile_id))
}

#[tauri::command]
pub async fn mod_install(
    app: AppHandle,
    profile_id: String,
    project_id: String,
    version_id: Option<String>,
) -> Result<InstalledMod, String> {
    err(modrinth::install_mod(&app, &profile_id, &project_id, version_id).await)
}

#[tauri::command]
pub fn mod_delete(app: AppHandle, profile_id: String, file_name: String) -> Result<(), String> {
    err(modrinth::delete(&app, &profile_id, &file_name))
}

#[tauri::command]
pub fn mod_toggle(
    app: AppHandle,
    profile_id: String,
    file_name: String,
    enabled: bool,
) -> Result<(), String> {
    err(modrinth::toggle(&app, &profile_id, &file_name, enabled))
}

#[tauri::command]
pub fn mod_import(app: AppHandle, profile_id: String, path: String) -> Result<InstalledMod, String> {
    err(modrinth::import(&app, &profile_id, &path))
}

/* ------------------------------------------------------------------ */
/* CurseForge                                                          */
/* ------------------------------------------------------------------ */

#[tauri::command]
pub fn curseforge_status(app: AppHandle) -> CurseForgeStatus {
    curseforge::status(&app)
}

#[tauri::command]
pub async fn curseforge_search(
    app: AppHandle,
    query: Option<String>,
    limit: Option<u64>,
    offset: Option<u64>,
    game_version: Option<String>,
    loader: Option<String>,
) -> Result<Vec<CurseForgeMod>, String> {
    err(
        curseforge::search(
            &app,
            query.as_deref().unwrap_or(""),
            limit.unwrap_or(24),
            offset.unwrap_or(0),
            game_version,
            loader,
        )
        .await,
    )
}

#[tauri::command]
pub async fn curseforge_save_key(app: AppHandle, key: String) -> Result<CurseForgeStatus, String> {
    err(curseforge::save_key(&app, &key).await)
}

/* ------------------------------------------------------------------ */
/* Modpacks                                                            */
/* ------------------------------------------------------------------ */

#[tauri::command]
pub async fn modpacks_search(
    app: AppHandle,
    query: Option<String>,
    limit: Option<u64>,
    offset: Option<u64>,
) -> Result<ModrinthSearchResult, String> {
    err(modpack::search(&app, query.as_deref().unwrap_or(""), limit.unwrap_or(24), offset.unwrap_or(0)).await)
}

#[tauri::command]
pub async fn modpack_versions(app: AppHandle, project_id: String) -> Result<Vec<ModrinthVersion>, String> {
    err(modpack::versions(&app, &project_id).await)
}

#[tauri::command]
pub async fn modpack_install(app: AppHandle, project_id: String, version_id: String) -> Result<Profile, String> {
    err(modpack::install(&app, &project_id, &version_id).await)
}

#[tauri::command]
pub async fn modpack_import(app: AppHandle, path: String) -> Result<Profile, String> {
    err(modpack::install_from_file(&app, Path::new(&path), None).await)
}

/* ------------------------------------------------------------------ */
/* Misc                                                                */
/* ------------------------------------------------------------------ */


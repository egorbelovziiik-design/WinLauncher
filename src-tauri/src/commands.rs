use crate::{accounts, content, curseforge, downloads, java, logs, minecraft, models::*, modpacks, modrinth, profiles, security, settings, state::AppState, worlds};
use serde_json::{json, Value};
use std::path::PathBuf;
use tauri::State;
type Response<T> = Result<T, String>;
fn err(e: impl std::fmt::Display) -> String { security::redact(&e.to_string(), &[]) }

#[tauri::command] pub fn get_snapshot(state: State<'_, AppState>) -> Response<Value> {
    // Clone lock-protected state in separate statements: never serialize while
    // holding several locks in an order that can conflict with begin_job.
    let job = state.job.lock().unwrap_or_else(|e| e.into_inner()).clone();
    let running = state.running.lock().unwrap_or_else(|e| e.into_inner()).clone();
    let discord_status = state.discord_status.read().unwrap_or_else(|e| e.into_inner()).clone();
    Ok(json!({ "settings": state.config(), "profiles": state.list::<Profile>("profiles").map_err(err)?, "accounts": state.list::<Account>("accounts").map_err(err)?, "installed": state.list::<InstalledFile>("installed").map_err(err)?, "sessions": state.list::<Session>("sessions").map_err(err)?, "job": job, "running": running, "discord_status": discord_status, "curseforge_configured": accounts::vault::get("curseforge-api-key").is_ok(), "version": env!("CARGO_PKG_VERSION") }))
}
#[tauri::command] pub fn save_settings(state: State<'_, AppState>, settings: Settings) -> Response<()> { settings::save(&state, settings).map_err(err) }
#[tauri::command] pub async fn get_system_info(state: State<'_, AppState>) -> Response<SystemInfo> { let s = state.inner().clone(); tokio::task::spawn_blocking(move || settings::system_info(&s)).await.map_err(err)?.map_err(err) }
#[tauri::command] pub async fn version_manifest(state: State<'_, AppState>) -> Response<minecraft::metadata::Manifest> { minecraft::metadata::manifest(&state).await.map_err(err) }
#[tauri::command] pub async fn loader_versions(state: State<'_, AppState>, game_version: String, loader: Loader) -> Response<Vec<minecraft::loaders::LoaderRelease>> { minecraft::loaders::releases(&state, &game_version, &loader).await.map_err(err) }
#[tauri::command] pub fn save_profile(state: State<'_, AppState>, profile: Profile) -> Response<Profile> { profiles::save(&state, profile).map_err(err) }
#[tauri::command] pub fn delete_profile(state: State<'_, AppState>, id: String) -> Response<()> { profiles::delete(&state, &id).map_err(err) }
#[tauri::command] pub async fn install_game(state: State<'_, AppState>, profile_id: String) -> Response<()> { minecraft::install_only(&state, &profile_id).await.map_err(err) }
#[tauri::command] pub async fn launch_game(state: State<'_, AppState>, profile_id: String, allow_memory_pressure: bool) -> Response<u32> { minecraft::launch(&state, &profile_id, allow_memory_pressure).await.map_err(err) }
#[tauri::command] pub fn cancel_task(state: State<'_, AppState>) { state.cancel.lock().unwrap_or_else(|e| e.into_inner()).cancel(); }
#[tauri::command] pub fn stop_game(state: State<'_, AppState>) { state.game_stop.lock().unwrap_or_else(|e| e.into_inner()).cancel(); }
#[tauri::command] pub async fn discover_java(state: State<'_, AppState>) -> Response<Vec<java::JavaRuntime>> { Ok(java::discover(&state).await) }
#[tauri::command] pub async fn install_java(state: State<'_, AppState>, major: u32, force_managed: Option<bool>) -> Response<java::JavaRuntime> { let guard = state.begin_job("java").map_err(err)?; if force_managed.unwrap_or(false) { java::install_managed(&state, major, &guard.token).await.map_err(err) } else { java::ensure(&state, major, None, &guard.token).await.map_err(err) } }
#[tauri::command] pub async fn begin_login(state: State<'_, AppState>) -> Response<accounts::Challenge> { accounts::begin(&state).await.map_err(err) }
#[tauri::command] pub async fn poll_login(state: State<'_, AppState>, id: String) -> Response<accounts::PollResult> { accounts::poll(&state, &id).await.map_err(err) }
#[tauri::command] pub fn cancel_login(state: State<'_, AppState>, id: String) { state.pending_auth.lock().unwrap_or_else(|e| e.into_inner()).remove(&id); }
#[tauri::command] pub fn remove_account(state: State<'_, AppState>, id: String) -> Response<()> { accounts::remove(&state, &id).map_err(err) }
#[tauri::command] pub async fn save_curseforge_key(state: State<'_, AppState>, key: String) -> Response<()> {
    if key.is_empty() { return accounts::vault::delete("curseforge-api-key").map_err(err); }
    if key.len() > 1024 || key.contains(['\n', '\r']) { return Err("Некорректный формат ключа".into()); }
    let response = state.client.get("https://api.curseforge.com/v1/games/432").header("x-api-key", key.trim()).send().await.map_err(err)?;
    if !response.status().is_success() { return Err(format!("CurseForge не принял ключ: HTTP {}", response.status().as_u16())); }
    accounts::vault::set("curseforge-api-key", key.trim()).map_err(err)?; state.changed(); Ok(())
}
#[tauri::command] pub async fn search_projects(state: State<'_, AppState>, provider: String, query: String, kind: String, profile_id: Option<String>, offset: u64, sort: String) -> Response<SearchResults> {
    let profile = profile_id.as_deref().map(|id| state.get::<Profile>("profiles", id)).transpose().map_err(err)?;
    match provider.as_str() { "modrinth" => modrinth::search(&state, &query, &kind, profile.as_ref(), offset, &sort).await.map_err(err), "curseforge" => curseforge::search(&state, &query, &kind, profile.as_ref(), offset, &sort).await.map_err(err), _ => Err("Неизвестный провайдер".into()) }
}
#[tauri::command] pub async fn project_detail(state: State<'_, AppState>, provider: String, id: String) -> Response<ProjectDetail> { match provider.as_str() { "modrinth" => modrinth::detail(&state, &id).await.map_err(err), "curseforge" => curseforge::detail(&state, &id).await.map_err(err), _ => Err("Неизвестный провайдер".into()) } }
#[tauri::command] pub async fn project_versions(state: State<'_, AppState>, provider: String, id: String, profile_id: String, kind: String) -> Response<Vec<Value>> {
    let p: Profile = state.get("profiles", &profile_id).map_err(err)?;
    let raw = match provider.as_str() { "modrinth" => modrinth::compatible_versions(&state, &id, &p, &kind).await.map_err(err)?, "curseforge" => curseforge::compatible_files(&state, &id, &p).await.map_err(err)?, _ => return Err("Неизвестный провайдер".into()) };
    Ok(raw.iter().map(|v| if provider == "modrinth" { json!({ "id": v["id"], "name": v["name"], "date": v["date_published"] }) } else { json!({ "id": v["id"].as_u64().unwrap_or(0).to_string(), "name": v["displayName"], "date": v["fileDate"] }) }).collect())
}
#[tauri::command] pub async fn install_project(state: State<'_, AppState>, provider: String, profile_id: String, project_id: String, version_id: Option<String>) -> Response<()> {
    let guard = state.begin_job("mods").map_err(err)?;
    let profile: Profile = state.get("profiles", &profile_id).map_err(err)?;
    match provider.as_str() { "modrinth" => { modrinth::install(&state, &profile, &project_id, version_id.as_deref(), &guard.token).await.map_err(err)?; }, "curseforge" => { curseforge::install(&state, &profile, &project_id, version_id.as_deref(), &guard.token).await.map_err(err)?; }, _ => return Err("Неизвестный провайдер".into()) }
    state.notify("success", "Контент и обязательные зависимости установлены. Контрольные суммы проверены."); Ok(())
}
#[tauri::command] pub fn toggle_content(state: State<'_, AppState>, id: String) -> Response<()> { content::toggle(&state, &id).map_err(err) }
#[tauri::command] pub fn remove_content(state: State<'_, AppState>, id: String) -> Response<()> { content::remove(&state, &id).map_err(err) }
#[tauri::command] pub async fn check_mod_updates(state: State<'_, AppState>, profile_id: String) -> Response<Vec<Value>> {
    let profile: Profile = state.get("profiles", &profile_id).map_err(err)?; let mut updates = Vec::new();
    for file in state.list::<InstalledFile>("installed").map_err(err)?.into_iter().filter(|f| f.profile_id == profile_id) {
        let latest = match file.provider.as_str() {
            "modrinth" => modrinth::compatible_versions(&state, &file.project_id, &profile, &file.kind).await.map_err(err)?.first().and_then(|v| v["id"].as_str()).map(str::to_owned),
            "curseforge" => curseforge::compatible_files(&state, &file.project_id, &profile).await.map_err(err)?.first().and_then(|v| v["id"].as_u64()).map(|n| n.to_string()),
            _ => None,
        };
        if let Some(version) = latest.filter(|v| *v != file.version_id) { updates.push(json!({ "file": file, "version_id": version })); }
    }
    Ok(updates)
}
#[tauri::command] pub async fn install_modrinth_pack(state: State<'_, AppState>, project_id: String) -> Response<Profile> { modrinth::install_pack(&state, &project_id).await.map_err(err) }
#[tauri::command] pub async fn install_curseforge_pack(state: State<'_, AppState>, project_id: String) -> Response<Profile> { curseforge::install_pack(&state, &project_id).await.map_err(err) }
#[tauri::command] pub async fn import_modpack(state: State<'_, AppState>, path: String) -> Response<Profile> { modpacks::import(&state, &PathBuf::from(path)).await.map_err(err) }
#[tauri::command] pub async fn export_modpack(state: State<'_, AppState>, profile_id: String, path: String) -> Response<()> { let s = state.inner().clone(); tokio::task::spawn_blocking(move || modpacks::export(&s, &profile_id, &PathBuf::from(path))).await.map_err(err)?.map_err(err) }
#[tauri::command] pub fn open_directory(state: State<'_, AppState>, kind: String, profile_id: Option<String>) -> Response<()> { settings::open_managed(&state, &kind, profile_id.as_deref()).map_err(err) }
#[tauri::command] pub fn open_external(url: String) -> Response<()> { settings::open_url(&url).map_err(err) }
#[tauri::command] pub async fn migrate_directory(state: State<'_, AppState>, path: String) -> Response<()> { let s = state.inner().clone(); tokio::task::spawn_blocking(move || settings::migrate(&s, &PathBuf::from(path))).await.map_err(err)?.map_err(err) }
#[tauri::command] pub fn get_logs(state: State<'_, AppState>) -> Vec<LogLine> { state.logs.lock().unwrap_or_else(|e| e.into_inner()).iter().cloned().collect() }
#[tauri::command] pub fn clear_logs_view(state: State<'_, AppState>) { state.logs.lock().unwrap_or_else(|e| e.into_inner()).clear(); }
#[tauri::command] pub fn export_logs(state: State<'_, AppState>, path: String) -> Response<()> { logs::export(&state, &PathBuf::from(path)).map_err(err) }
#[tauri::command] pub fn local_files(state: State<'_, AppState>, profile_id: String, kind: String) -> Response<Vec<worlds::LocalFile>> { worlds::list(&state, &profile_id, &kind).map_err(err) }
#[tauri::command] pub async fn backup_world(state: State<'_, AppState>, profile_id: String, name: String) -> Response<String> { let s = state.inner().clone(); tokio::task::spawn_blocking(move || worlds::backup(&s, &profile_id, &name)).await.map_err(err)?.map_err(err) }
#[tauri::command] pub async fn restore_world(state: State<'_, AppState>, profile_id: String, name: String) -> Response<String> { let s = state.inner().clone(); tokio::task::spawn_blocking(move || worlds::restore(&s, &profile_id, &name)).await.map_err(err)?.map_err(err) }
#[tauri::command] pub async fn screenshot_data(state: State<'_, AppState>, profile_id: String, name: String) -> Response<String> { let s = state.inner().clone(); tokio::task::spawn_blocking(move || worlds::screenshot(&s, &profile_id, &name)).await.map_err(err)?.map_err(err) }
#[tauri::command] pub fn set_activity(state: State<'_, AppState>, page: String) {
    let text = match page.as_str() { "mods" => "Просматривает Modrinth и CurseForge", "profiles" => "Настраивает Minecraft", "themes" => "Выбирает свой мир", "settings" => "Настраивает WinLauncher", "worlds" => "Управляет своими мирами", _ => "Выбирает версию Minecraft" };
    *state.activity.write().unwrap_or_else(|e| e.into_inner()) = text.into();
}
#[tauri::command] pub async fn minecraft_news(state: State<'_, AppState>) -> Response<Value> { downloads::json(&state, "https://launchercontent.mojang.com/news.json").await.map_err(err) }
#[tauri::command] pub async fn check_launcher_update(state: State<'_, AppState>) -> Response<Value> {
    let response = state.client.get("https://api.github.com/repos/egorbelovziiik-design/WinLauncher/releases/latest").send().await.map_err(err)?;
    if response.status() == reqwest::StatusCode::NOT_FOUND { return Ok(json!({ "available": false, "version": "1.0.0", "message": "Публичных релизов пока нет" })); }
    let value: Value = serde_json::from_slice(&downloads::bounded_response(response.error_for_status().map_err(err)?, 4 * 1024 * 1024).await.map_err(err)?).map_err(err)?;
    let tag = value["tag_name"].as_str().unwrap_or("1.0.0").trim_start_matches('v');
    let numbers = |s: &str| -> Vec<u32> { s.split('.').map(|n| n.parse().unwrap_or(0)).collect() };
    Ok(json!({ "available": numbers(tag) > numbers(env!("CARGO_PKG_VERSION")), "version": tag, "url": value["html_url"], "message": "Обновление устанавливается вручную. Подписанный автоматический канал ещё не опубликован." }))
}

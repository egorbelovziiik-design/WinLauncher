use std::path::PathBuf;

use tauri::{AppHandle, Emitter, Manager};

use crate::models::*;
use crate::state::{wlock, AppState};

fn slug(text: &str) -> String {
    crate::paths::sanitize_name(text.trim()).to_lowercase()
}

fn unique_id(app: &AppHandle, base: &str) -> String {
    let state = app.state::<AppState>();
    let existing = state.profiles();
    if !existing.iter().any(|p| p.id == base) {
        return base.to_string();
    }
    for n in 2..10_000 {
        let candidate = format!("{base}-{n}");
        if !existing.iter().any(|p| p.id == candidate) {
            return candidate;
        }
    }
    format!("{base}-{}", uuid::Uuid::new_v4())
}

fn prepare_game_dir(dir: &PathBuf) -> Result<(), String> {
    for sub in ["mods", "config", "saves", "resourcepacks", "shaderpacks", "logs"] {
        std::fs::create_dir_all(dir.join(sub)).map_err(|e| e.to_string())?;
    }
    Ok(())
}

pub fn create(app: &AppHandle, draft: ProfileDraft) -> Result<Profile, String> {
    let state = app.state::<AppState>();
    let settings = state.settings();

    let name = draft.name.trim().to_string();
    if name.is_empty() {
        return Err("profiles.nameRequired".into());
    }
    let mc_version = draft.mc_version.trim().to_string();
    if mc_version.is_empty() {
        return Err("profiles.versionRequired".into());
    }
    let loader_kind = crate::loaders::normalize(&draft.loader.kind);
    let loader = LoaderRef {
        kind: loader_kind.clone(),
        version: draft.loader.version.clone().filter(|v| !v.trim().is_empty()),
    };

    let id = unique_id(app, &format!("{}-{}", slug(&name), slug(&mc_version)));
    let default_dir = state.paths().profile_dir(&id);
    let game_dir = match draft.game_dir.as_ref().map(|d| d.trim()).filter(|d| !d.is_empty()) {
        Some(dir) => PathBuf::from(dir),
        None => default_dir,
    };
    prepare_game_dir(&game_dir)?;

    let ram = draft
        .ram_mb
        .unwrap_or(settings.default_ram_mb)
        .clamp(512, 65_536);
    let profile = Profile {
        id: id.clone(),
        name,
        mc_version,
        loader,
        game_dir: game_dir.to_string_lossy().to_string(),
        ram_mb: ram,
        java_path: draft.java_path.clone().filter(|p| !p.trim().is_empty()),
        jvm_args: draft.jvm_args.clone().unwrap_or_default(),
        width: draft.width.unwrap_or(settings.resolution_width).max(320),
        height: draft.height.unwrap_or(settings.resolution_height).max(240),
        fullscreen: draft.fullscreen.unwrap_or(settings.fullscreen),
        created_at: chrono::Utc::now().to_rfc3339(),
        last_played_at: None,
        source: draft.source.clone().unwrap_or_else(|| "manual".into()),
        modpack: draft.modpack.clone(),
    };

    {
        let mut profiles = wlock(&state.profiles);
        profiles.push(profile.clone());
    }
    state.save_profiles();

    if settings.active_profile_id.is_none() {
        let mut s = state.settings();
        s.active_profile_id = Some(profile.id.clone());
        *wlock(&state.settings) = s;
        state.save_settings();
    }
    state.log("info", "launcher", format!("Created profile '{}' ({})", profile.name, profile.id));
    let _ = app.emit("profile-changed", serde_json::json!({ "profileId": profile.id }));
    Ok(profile)
}

pub fn update(app: &AppHandle, incoming: Profile) -> Result<Profile, String> {
    let state = app.state::<AppState>();
    if incoming.name.trim().is_empty() {
        return Err("profiles.nameRequired".into());
    }
    let profile = {
        let mut profiles = wlock(&state.profiles);
        let Some(slot) = profiles.iter_mut().find(|p| p.id == incoming.id) else {
            return Err("profiles.notFound".into());
        };
        let mut next = incoming.clone();
        next.name = next.name.trim().to_string();
        next.ram_mb = next.ram_mb.clamp(512, 65_536);
        next.width = next.width.max(320);
        next.height = next.height.max(240);
        next.loader.kind = crate::loaders::normalize(&next.loader.kind);
        next.loader.version = next.loader.version.filter(|v| !v.trim().is_empty());
        next.created_at = slot.created_at.clone();
        next.last_played_at = slot.last_played_at.clone();
        next.source = slot.source.clone();
        if next.game_dir.trim().is_empty() {
            next.game_dir = slot.game_dir.clone();
        }
        *slot = next.clone();
        next
    };
    state.save_profiles();
    let _ = app.emit("profile-changed", serde_json::json!({ "profileId": profile.id }));
    Ok(profile)
}

pub fn delete(app: &AppHandle, id: &str) -> Result<(), String> {
    let state = app.state::<AppState>();
    let removed = {
        let mut profiles = wlock(&state.profiles);
        let before = profiles.len();
        profiles.retain(|p| p.id != id);
        before != profiles.len()
    };
    if !removed {
        return Err("profiles.notFound".into());
    }
    state.save_profiles();
    let settings = state.settings();
    if settings.active_profile_id.as_deref() == Some(id) {
        let next = state.profiles().first().map(|p| p.id.clone());
        let mut s = settings;
        s.active_profile_id = next;
        *wlock(&state.settings) = s;
        state.save_settings();
    }
    let _ = app.emit("profile-changed", serde_json::json!({ "profileId": id }));
    Ok(())
}

/// Ensures that settings point at an existing profile/account.
pub fn reconcile(app: &AppHandle) {
    let state = app.state::<AppState>();
    let mut settings = state.settings();
    let profiles = state.profiles();
    let accounts = state.accounts();
    let mut changed = false;

    if settings.active_profile_id.is_none()
        || !profiles
            .iter()
            .any(|p| Some(&p.id) == settings.active_profile_id.as_ref())
    {
        settings.active_profile_id = profiles.first().map(|p| p.id.clone());
        changed = true;
    }
    if settings.active_account_id.is_none()
        || !accounts
            .iter()
            .any(|a| Some(&a.id) == settings.active_account_id.as_ref())
    {
        settings.active_account_id = accounts
            .iter()
            .find(|a| a.primary)
            .or_else(|| accounts.first())
            .map(|a| a.id.clone());
        changed = true;
    }
    if changed {
        *wlock(&state.settings) = settings;
        state.save_settings();
    }
}

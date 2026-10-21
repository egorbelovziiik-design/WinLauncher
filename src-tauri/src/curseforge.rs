use serde_json::Value;
use tauri::{AppHandle, Manager};

use crate::models::*;
use crate::state::{wlock, AppState};

const API: &str = "https://api.curseforge.com/v1";
const KEY_SLOT: &str = "curseforge-key";
const MINECRAFT_GAME_ID: u64 = 432;
const MODS_CLASS_ID: u64 = 6;

pub fn key(app: &AppHandle) -> Option<String> {
    let state = app.state::<AppState>();
    state
        .secrets
        .get(KEY_SLOT)
        .map(|k| k.trim().to_string())
        .filter(|k| !k.is_empty())
}

pub fn status(app: &AppHandle) -> CurseForgeStatus {
    let available = key(app).is_some();
    CurseForgeStatus {
        available,
        reason: if available {
            None
        } else {
            Some("A personal CurseForge API key is required. Create one at console.curseforge.com and paste it in Settings.".into())
        },
    }
}

fn loader_type(kind: &str) -> Option<u32> {
    match kind.to_lowercase().as_str() {
        "forge" => Some(1),
        "fabric" => Some(4),
        "quilt" => Some(5),
        "neoforge" => Some(6),
        _ => None,
    }
}

pub async fn verify(app: &AppHandle, candidate: &str) -> Result<(), String> {
    let state = app.state::<AppState>();
    let res = state
        .http()
        .get(format!("{API}/games/{MINECRAFT_GAME_ID}"))
        .header("x-api-key", candidate)
        .header("Accept", "application/json")
        .timeout(std::time::Duration::from_secs(30))
        .send()
        .await
        .map_err(|e| format!("errors.curseforgeKey|{e}"))?;
    match res.status().as_u16() {
        200 => Ok(()),
        401 | 403 => Err("errors.curseforgeKey".into()),
        other => Err(format!("errors.curseforgeKey|CurseForge returned HTTP {other}")),
    }
}

pub async fn save_key(app: &AppHandle, candidate: &str) -> Result<CurseForgeStatus, String> {
    let candidate = candidate.trim();
    let state = app.state::<AppState>();
    if candidate.is_empty() {
        state.secrets.clear(KEY_SLOT);
        let mut settings = state.settings();
        settings.curseforge_key_set = false;
        *wlock(&state.settings) = settings;
        state.save_settings();
        return Ok(status(app));
    }
    verify(app, candidate).await?;
    state.secrets.set(KEY_SLOT, candidate)?;
    let mut settings = state.settings();
    settings.curseforge_key_set = true;
    *wlock(&state.settings) = settings;
    state.save_settings();
    state.log("info", "launcher", "CurseForge API key saved");
    Ok(status(app))
}

#[allow(clippy::too_many_arguments)]
pub async fn search(
    app: &AppHandle,
    query: &str,
    limit: u64,
    offset: u64,
    game_version: Option<String>,
    loader: Option<String>,
) -> Result<Vec<CurseForgeMod>, String> {
    let api_key = key(app).ok_or_else(|| "errors.curseforgeKey".to_string())?;
    let state = app.state::<AppState>();
    let mut url = format!(
        "{API}/mods/search?gameId={MINECRAFT_GAME_ID}&classId={MODS_CLASS_ID}&searchFilter={}&sortField=2&sortOrder=desc&pageSize={}&index={}",
        crate::util::url_encode(query.trim()),
        limit.clamp(1, 50),
        offset
    );
    if let Some(gv) = game_version.as_deref().filter(|v| !v.is_empty()) {
        url.push_str(&format!("&gameVersion={}", crate::util::url_encode(gv)));
    }
    if let Some(lt) = loader.as_deref().and_then(loader_type) {
        url.push_str(&format!("&modLoaderType={lt}"));
    }
    let res = state
        .http()
        .get(&url)
        .header("x-api-key", api_key)
        .header("Accept", "application/json")
        .timeout(std::time::Duration::from_secs(30))
        .send()
        .await
        .map_err(|e| format!("errors.network|{e}"))?;
    if res.status().as_u16() == 403 || res.status().as_u16() == 401 {
        return Err("errors.curseforgeKey".into());
    }
    if !res.status().is_success() {
        return Err(format!("errors.network|CurseForge returned HTTP {}", res.status()));
    }
    let value: Value = res.json().await.map_err(|e| format!("errors.network|{e}"))?;
    let mut out = Vec::new();
    if let Some(items) = value.get("data").and_then(|d| d.as_array()) {
        for item in items {
            out.push(parse_mod(item));
        }
    }
    Ok(out)
}

fn parse_mod(item: &Value) -> CurseForgeMod {
    let strings = |key: &str, inner: Option<&str>| -> Vec<String> {
        item.get(key)
            .and_then(|v| v.as_array())
            .map(|items| {
                items
                    .iter()
                    .filter_map(|entry| {
                        let value = match inner {
                            Some(field) => entry.get(field).and_then(|v| v.as_str()),
                            None => entry.as_str(),
                        }?;
                        Some(value.to_string())
                    })
                    .collect()
            })
            .unwrap_or_default()
    };
    let latest = item
        .get("latestFilesIndexes")
        .and_then(|v| v.as_array())
        .and_then(|items| items.first());
    CurseForgeMod {
        id: item.get("id").and_then(|v| v.as_u64()).unwrap_or(0),
        name: item.get("name").and_then(|v| v.as_str()).unwrap_or_default().to_string(),
        summary: item.get("summary").and_then(|v| v.as_str()).unwrap_or_default().to_string(),
        download_count: item.get("downloadCount").and_then(|v| v.as_u64()).unwrap_or(0),
        logo_url: item
            .get("logo")
            .and_then(|l| l.get("url"))
            .and_then(|v| v.as_str())
            .map(|s| s.to_string()),
        categories: strings("categories", Some("name")),
        authors: strings("authors", Some("name")),
        website_url: item
            .get("links")
            .and_then(|l| l.get("websiteUrl"))
            .and_then(|v| v.as_str())
            .map(|s| s.to_string()),
        latest_file_id: latest.and_then(|l| l.get("fileId")).and_then(|v| v.as_u64()),
        latest_file_name: latest
            .and_then(|l| l.get("filename"))
            .and_then(|v| v.as_str())
            .map(|s| s.to_string()),
        game_versions: latest
            .and_then(|l| l.get("gameVersion"))
            .and_then(|v| v.as_str())
            .map(|s| vec![s.to_string()])
            .unwrap_or_default(),
        date_modified: item
            .get("dateModified")
            .and_then(|v| v.as_str())
            .unwrap_or_default()
            .to_string(),
    }
}

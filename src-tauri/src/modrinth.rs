use std::collections::HashMap;
use std::path::{Path, PathBuf};

use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use tauri::{AppHandle, Manager};

use crate::download::{self, FileSpec};
use crate::models::*;
use crate::state::AppState;
use crate::util;

const API: &str = "https://api.modrinth.com/v2";

/* ------------------------------------------------------------------ */
/* Bookkeeping for locally installed mods                              */
/* ------------------------------------------------------------------ */

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct ModMeta {
    pub project_id: Option<String>,
    pub project_name: Option<String>,
    pub version_id: Option<String>,
    pub version_name: Option<String>,
    pub icon_url: Option<String>,
    pub source: String,
}

fn meta_file(game_dir: &Path) -> PathBuf {
    game_dir.join("winlaucher-mods.json")
}

fn read_meta(game_dir: &Path) -> HashMap<String, ModMeta> {
    util::read_json(&meta_file(game_dir)).unwrap_or_default()
}

fn write_meta(game_dir: &Path, map: &HashMap<String, ModMeta>) -> Result<(), String> {
    util::write_json(&meta_file(game_dir), map)
}

fn mods_dir(app: &AppHandle, profile_id: &str) -> Result<PathBuf, String> {
    let state = app.state::<AppState>();
    let dir = state.game_dir_for(profile_id)?;
    let mods = dir.join("mods");
    std::fs::create_dir_all(&mods).map_err(|e| e.to_string())?;
    Ok(mods)
}

/* ------------------------------------------------------------------ */
/* Search & metadata                                                   */
/* ------------------------------------------------------------------ */

fn facet(inner: &str) -> String {
    format!("[\"{inner}\"]")
}

#[allow(clippy::too_many_arguments)]
pub async fn search(
    app: &AppHandle,
    query: &str,
    limit: u64,
    offset: u64,
    loader: Option<String>,
    game_version: Option<String>,
    sort: Option<String>,
    project_type: &str,
) -> Result<ModrinthSearchResult, String> {
    let state = app.state::<AppState>();
    let index = match sort.as_deref() {
        Some("downloads") => "downloads",
        Some("follows") => "follows",
        Some("newest") => "newest",
        Some("updated") => "updated",
        _ => "relevance",
    };
    let mut facets: Vec<String> = vec![facet(&format!("project_type:{project_type}"))];
    if let Some(l) = loader.as_deref().filter(|l| !l.is_empty() && *l != "vanilla") {
        facets.push(facet(&format!("categories:{l}")));
    }
    if let Some(v) = game_version.as_deref().filter(|v| !v.is_empty()) {
        facets.push(facet(&format!("versions:{v}")));
    }
    let mut url = format!(
        "{API}/search?query={}&limit={}&offset={}&index={}",
        util::url_encode(query.trim()),
        limit.clamp(1, 100),
        offset,
        index
    );
    if !facets.is_empty() {
        url.push_str(&format!("&facets={}", util::url_encode(&format!("[{}]", facets.join(",")))));
    }
    let value = download::get_json_value(&state.http(), &url, 2).await?;
    let hits = value
        .get("hits")
        .and_then(|h| h.as_array())
        .map(|items| items.iter().filter_map(parse_hit).collect())
        .unwrap_or_default();
    Ok(ModrinthSearchResult {
        hits,
        total_hits: value.get("total_hits").and_then(|v| v.as_u64()).unwrap_or(0),
        offset: value.get("offset").and_then(|v| v.as_u64()).unwrap_or(offset),
        limit: value.get("limit").and_then(|v| v.as_u64()).unwrap_or(limit),
    })
}

fn parse_hit(value: &Value) -> Option<ModrinthHit> {
    let project_id = value
        .get("project_id")
        .or_else(|| value.get("id"))
        .and_then(|v| v.as_str())?
        .to_string();
    let s = |key: &str| value.get(key).and_then(|v| v.as_str()).unwrap_or("").to_string();
    let mut gallery = Vec::new();
    if let Some(items) = value.get("gallery").and_then(|v| v.as_array()) {
        for item in items {
            if let Some(url) = item.as_str() {
                gallery.push(url.to_string());
            } else if let Some(url) = item.get("url").and_then(|v| v.as_str()) {
                gallery.push(url.to_string());
            }
        }
    }
    let mut categories: Vec<String> = Vec::new();
    for key in ["categories", "display_categories"] {
        if let Some(items) = value.get(key).and_then(|v| v.as_array()) {
            for item in items {
                if let Some(cat) = item.as_str() {
                    if !categories.iter().any(|c| c == cat) {
                        categories.push(cat.to_string());
                    }
                }
            }
        }
    }
    let versions = value
        .get("versions")
        .and_then(|v| v.as_array())
        .map(|items| items.iter().filter_map(|v| v.as_str().map(|s| s.to_string())).collect())
        .unwrap_or_default();
    let author = value
        .get("author")
        .and_then(|v| v.as_str())
        .unwrap_or("")
        .to_string();
    Some(ModrinthHit {
        project_id,
        slug: s("slug"),
        title: s("title"),
        description: s("description"),
        categories,
        project_type: s("project_type"),
        downloads: value.get("downloads").and_then(|v| v.as_u64()).unwrap_or(0),
        follows: value
            .get("follows")
            .or_else(|| value.get("followers"))
            .and_then(|v| v.as_u64())
            .unwrap_or(0),
        icon_url: value.get("icon_url").and_then(|v| v.as_str()).map(|s| s.to_string()),
        author,
        versions,
        date_modified: s("date_modified"),
        gallery,
        body: value.get("body").and_then(|v| v.as_str()).map(|s| s.to_string()),
        source_url: value.get("source_url").and_then(|v| v.as_str()).map(|s| s.to_string()),
    })
}

pub async fn project(app: &AppHandle, id_or_slug: &str) -> Result<ModrinthHit, String> {
    let state = app.state::<AppState>();
    let url = format!("{API}/project/{}", util::url_encode(id_or_slug));
    let value = download::get_json_value(&state.http(), &url, 2).await?;
    parse_hit(&value).ok_or_else(|| "Modrinth project not found".to_string())
}

pub fn parse_version(value: &Value) -> Option<ModrinthVersion> {
    let files = value
        .get("files")
        .and_then(|v| v.as_array())
        .map(|items| {
            items
                .iter()
                .filter_map(|file| {
                    Some(ModFile {
                        filename: file.get("filename")?.as_str()?.to_string(),
                        url: file.get("url")?.as_str()?.to_string(),
                        size: file.get("size").and_then(|v| v.as_u64()).unwrap_or(0),
                        hashes: file
                            .get("hashes")
                            .and_then(|h| serde_json::from_value(h.clone()).ok())
                            .unwrap_or_default(),
                        primary: file.get("primary").and_then(|v| v.as_bool()).unwrap_or(false),
                    })
                })
                .collect()
        })
        .unwrap_or_default();
    let dependencies = value
        .get("dependencies")
        .and_then(|v| v.as_array())
        .map(|items| {
            items
                .iter()
                .map(|dep| ModDependency {
                    project_id: dep.get("project_id").and_then(|v| v.as_str()).map(|s| s.to_string()),
                    version_id: dep.get("version_id").and_then(|v| v.as_str()).map(|s| s.to_string()),
                    dependency_type: dep
                        .get("dependency_type")
                        .and_then(|v| v.as_str())
                        .unwrap_or("required")
                        .to_string(),
                })
                .collect()
        })
        .unwrap_or_default();
    let strings = |key: &str| -> Vec<String> {
        value
            .get(key)
            .and_then(|v| v.as_array())
            .map(|items| items.iter().filter_map(|v| v.as_str().map(|s| s.to_string())).collect())
            .unwrap_or_default()
    };
    Some(ModrinthVersion {
        id: value.get("id")?.as_str()?.to_string(),
        project_id: value.get("project_id")?.as_str()?.to_string(),
        name: value.get("name").and_then(|v| v.as_str()).unwrap_or_default().to_string(),
        version_number: value
            .get("version_number")
            .and_then(|v| v.as_str())
            .unwrap_or_default()
            .to_string(),
        version_type: value.get("version_type").and_then(|v| v.as_str()).unwrap_or("release").to_string(),
        game_versions: strings("game_versions"),
        loaders: strings("loaders"),
        date_published: value
            .get("date_published")
            .and_then(|v| v.as_str())
            .unwrap_or_default()
            .to_string(),
        downloads: value.get("downloads").and_then(|v| v.as_u64()).unwrap_or(0),
        files,
        dependencies,
    })
}

pub async fn versions(
    app: &AppHandle,
    project_id: &str,
    game_version: Option<String>,
    loader: Option<String>,
) -> Result<Vec<ModrinthVersion>, String> {
    let state = app.state::<AppState>();
    let project_id = project_id.trim();
    if project_id.is_empty() {
        return Err("Unknown Modrinth project".into());
    }
    let mut query: Vec<String> = Vec::new();
    if let Some(gv) = game_version.as_deref().filter(|v| !v.is_empty()) {
        query.push(format!("game_versions={}", util::url_encode(&format!("[\"{gv}\"]"))));
    }
    if let Some(l) = loader.as_deref().filter(|l| !l.is_empty() && *l != "vanilla") {
        query.push(format!("loaders={}", util::url_encode(&format!("[\"{l}\"]"))));
    }
    let mut url = format!("{API}/project/{}/version", util::url_encode(project_id));
    if !query.is_empty() {
        url.push('?');
        url.push_str(&query.join("&"));
    }
    let value = download::get_json_value(&state.http(), &url, 2).await?;
    Ok(value
        .as_array()
        .map(|items| items.iter().filter_map(parse_version).collect())
        .unwrap_or_default())
}

pub async fn tags(app: &AppHandle) -> Result<Value, String> {
    let state = app.state::<AppState>();
    let client = state.http();
    let game_versions = download::get_json_value(&client, &format!("{API}/tag/game_version"), 1)
        .await
        .unwrap_or(Value::Array(Vec::new()));
    let loaders = download::get_json_value(&client, &format!("{API}/tag/loader"), 1)
        .await
        .unwrap_or(Value::Array(Vec::new()));
    let categories = download::get_json_value(&client, &format!("{API}/tag/category"), 1)
        .await
        .unwrap_or(Value::Array(Vec::new()));
    let toolchain = ["fabric", "forge", "neoforge", "quilt"];
    let loader_names: Vec<String> = loaders
        .as_array()
        .map(|items| {
            items
                .iter()
                .filter_map(|item| {
                    let name = item.get("name")?.as_str()?.to_string();
                    let supported = item
                        .get("supported_project_types")
                        .and_then(|v| v.as_array())
                        .map(|s| s.iter().any(|v| v.as_str() == Some("mod")))
                        .unwrap_or(true);
                    if supported && toolchain.contains(&name.as_str()) {
                        Some(name)
                    } else {
                        None
                    }
                })
                .collect()
        })
        .unwrap_or_default();
    Ok(json!({
        "gameVersions": game_versions,
        "loaders": loader_names,
        "categories": categories,
    }))
}

/* ------------------------------------------------------------------ */
/* Installed mods                                                      */
/* ------------------------------------------------------------------ */

fn to_installed(file: &Path, meta: Option<&ModMeta>) -> Option<InstalledMod> {
    let name = file.file_name()?.to_string_lossy().to_string();
    let enabled = name.ends_with(".jar");
    if !enabled && !name.ends_with(".jar.disabled") {
        return None;
    }
    let size = std::fs::metadata(file).map(|m| m.len()).unwrap_or(0);
    let meta = meta.cloned().unwrap_or_default();
    Some(InstalledMod {
        file_name: name.clone(),
        path: file.to_string_lossy().to_string(),
        enabled,
        size,
        project_id: meta.project_id,
        project_name: meta.project_name,
        version_name: meta.version_name,
        icon_url: meta.icon_url,
        source: if meta.source.is_empty() { "local".into() } else { meta.source },
        update_version_id: None,
        update_version_name: None,
    })
}

pub fn installed(app: &AppHandle, profile_id: &str) -> Result<Vec<InstalledMod>, String> {
    let state = app.state::<AppState>();
    let game_dir = state.game_dir_for(profile_id)?;
    let mods = game_dir.join("mods");
    let meta = read_meta(&game_dir);
    let mut out = Vec::new();
    if let Ok(entries) = std::fs::read_dir(&mods) {
        for entry in entries.flatten() {
            let path = entry.path();
            if !path.is_file() {
                continue;
            }
            let name = entry.file_name().to_string_lossy().to_string();
            if let Some(item) = to_installed(&path, meta.get(&name)) {
                out.push(item);
            }
        }
    }
    out.sort_by(|a, b| a.file_name.to_lowercase().cmp(&b.file_name.to_lowercase()));
    Ok(out)
}

pub async fn install_mod(
    app: &AppHandle,
    profile_id: &str,
    project_id: &str,
    version_id: Option<String>,
) -> Result<InstalledMod, String> {
    let state = app.state::<AppState>();
    let profile = state
        .profile_by_id(profile_id)
        .ok_or_else(|| "Profile not found".to_string())?;
    let loader = if profile.loader.kind == "vanilla" {
        None
    } else {
        Some(profile.loader.kind.clone())
    };

    let chosen = match version_id.filter(|v| !v.trim().is_empty()) {
        Some(id) => {
            let url = format!("{API}/version/{}", util::url_encode(&id));
            let value = download::get_json_value(&state.http(), &url, 2).await?;
            parse_version(&value).ok_or_else(|| "Modrinth version not found".to_string())?
        }
        None => {
            let list = versions(
                app,
                project_id,
                Some(profile.mc_version.clone()),
                loader.clone(),
            )
            .await?;
            list.into_iter()
                .next()
                .ok_or_else(|| "errors.noCompatibleVersion".to_string())?
        }
    };

    let file = chosen
        .files
        .iter()
        .find(|f| f.primary)
        .or_else(|| chosen.files.first())
        .cloned()
        .ok_or_else(|| "Modrinth version has no files".to_string())?;
    let mods = mods_dir(app, profile_id)?;
    let target = mods.join(crate::paths::sanitize_name(&file.filename));
    let sha1 = file.hashes.get("sha1").cloned();
    download::download_files(
        app.clone(),
        format!("{} · {}", chosen.name, file.filename),
        Some(profile_id.to_string()),
        vec![FileSpec::new(&file.url, target.clone()).hash(sha1).size(file.size)],
    )
    .await?;

    let project_title = match state.http().get(format!("{API}/project/{}", util::url_encode(project_id))).send().await {
        Ok(res) if res.status().is_success() => res
            .json::<Value>()
            .await
            .ok()
            .and_then(|v| v.get("title").and_then(|t| t.as_str()).map(|s| s.to_string())),
        _ => None,
    };

    let mut meta = read_meta(Path::new(&profile.game_dir));
    meta.insert(
        file.filename.clone(),
        ModMeta {
            project_id: Some(chosen.project_id.clone()),
            project_name: project_title.clone(),
            version_id: Some(chosen.id.clone()),
            version_name: Some(chosen.version_number.clone()),
            icon_url: None,
            source: "modrinth".into(),
        },
    );
    write_meta(&PathBuf::from(&profile.game_dir), &meta)?;
    state.log("info", "launcher", format!("Installed mod {}", file.filename));
    to_installed(&target, meta.get(&file.filename))
        .ok_or_else(|| "Installed mod disappeared".to_string())
}

pub fn toggle(app: &AppHandle, profile_id: &str, file_name: &str, enabled: bool) -> Result<(), String> {
    let mods = mods_dir(app, profile_id)?;
    let file_name = crate::paths::sanitize_name(file_name);
    let current = mods.join(&file_name);
    let (source, target) = if enabled {
        (mods.join(format!("{file_name}.disabled")), current.clone())
    } else {
        (current.clone(), mods.join(format!("{file_name}.disabled")))
    };
    if source.is_file() {
        std::fs::rename(&source, &target).map_err(|e| e.to_string())?;
    }
    Ok(())
}

pub fn delete(app: &AppHandle, profile_id: &str, file_name: &str) -> Result<(), String> {
    let state = app.state::<AppState>();
    let game_dir = state.game_dir_for(profile_id)?;
    let mods = game_dir.join("mods");
    let base = file_name.trim_end_matches(".disabled").to_string();
    for candidate in [base.clone(), format!("{base}.disabled")] {
        let path = mods.join(&candidate);
        if path.is_file() {
            std::fs::remove_file(&path).map_err(|e| e.to_string())?;
        }
        let mut meta = read_meta(&game_dir);
        if meta.remove(&candidate).is_some() {
            write_meta(&game_dir, &meta)?;
        }
    }
    Ok(())
}

pub fn import(app: &AppHandle, profile_id: &str, source: &str) -> Result<InstalledMod, String> {
    let state = app.state::<AppState>();
    let game_dir = state.game_dir_for(profile_id)?;
    let src = PathBuf::from(source);
    if !src.is_file() {
        return Err("File not found".into());
    }
    let file_name = src
        .file_name()
        .map(|n| n.to_string_lossy().to_string())
        .ok_or_else(|| "Invalid file".to_string())?;
    if !file_name.ends_with(".jar") && !file_name.ends_with(".jar.disabled") {
        return Err("Only .jar mods can be imported".into());
    }
    let mods = game_dir.join("mods");
    std::fs::create_dir_all(&mods).map_err(|e| e.to_string())?;
    let target = mods.join(crate::paths::sanitize_name(&file_name));
    std::fs::copy(&src, &target).map_err(|e| e.to_string())?;
    to_installed(&target, None).ok_or_else(|| "Import failed".to_string())
}

/* ------------------------------------------------------------------ */
/* Dependency resolution helper used by the modpack installer           */
/* ------------------------------------------------------------------ */

pub async fn resolve_required(
    app: &AppHandle,
    version: &ModrinthVersion,
    game_version: &str,
    loader: Option<String>,
) -> Vec<(String, ModMeta)> {
    let mut out = Vec::new();
    for dep in version.dependencies.iter().filter(|d| d.dependency_type == "required") {
        let Some(project) = dep.project_id.clone() else { continue };
        let list = versions(app, &project, Some(game_version.to_string()), loader.clone())
            .await
            .unwrap_or_default();
        if let Some(best) = list.first() {
            if let Some(file) = best.files.iter().find(|f| f.primary).or_else(|| best.files.first()) {
                out.push((
                    file.url.clone(),
                    ModMeta {
                        project_id: Some(project),
                        project_name: None,
                        version_id: Some(best.id.clone()),
                        version_name: Some(best.version_number.clone()),
                        icon_url: None,
                        source: "modrinth".into(),
                    },
                ));
            }
        }
    }
    out
}

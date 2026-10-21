use std::io::Read;
use std::path::{Path, PathBuf};

use serde_json::Value;
use tauri::{AppHandle, Emitter, Manager};

use crate::download::{self, FileSpec};
use crate::loaders;
use crate::models::*;
use crate::modrinth;
use crate::paths::safe_join;
use crate::profiles;
use crate::state::AppState;
use crate::util;

const INDEX_FILE: &str = "modrinth.index.json";

pub struct MrpackIndex {
    pub name: String,
    pub summary: String,
    pub version_id: String,
    pub loader_kind: String,
    pub loader_version: Option<String>,
    pub mc_version: String,
    pub files: Vec<MrpackFile>,
}

pub struct MrpackFile {
    pub path: String,
    pub url: String,
    pub sha1: Option<String>,
    pub size: u64,
    pub skip: bool,
}

fn read_index(path: &Path) -> Result<MrpackIndex, String> {
    let file = std::fs::File::open(path).map_err(|e| e.to_string())?;
    let mut archive = zip::ZipArchive::new(file).map_err(|e| format!("Not a valid .mrpack archive: {e}"))?;
    if archive.by_name("manifest.json").is_ok() {
        return Err(
            "This archive is a CurseForge modpack. CurseForge packs need a personal API key and are \
             not supported by the .mrpack importer — use a Modrinth .mrpack file instead."
                .into(),
        );
    }
    let mut entry = archive
        .by_name(INDEX_FILE)
        .map_err(|_| "The archive does not contain modrinth.index.json".to_string())?;
    let mut text = String::new();
    entry.read_to_string(&mut text).map_err(|e| e.to_string())?;
    let value: Value = serde_json::from_str(&text).map_err(|e| format!("Invalid modpack index: {e}"))?;

    let deps = value.get("dependencies").cloned().unwrap_or(Value::Null);
    let mc_version = deps
        .get("minecraft")
        .and_then(|v| v.as_str())
        .ok_or_else(|| "The modpack does not declare a Minecraft version".to_string())?
        .to_string();

    let mut loader_kind = "vanilla".to_string();
    let mut loader_version = None;
    for (key, kind) in [
        ("fabric-loader", "fabric"),
        ("quilt-loader", "quilt"),
        ("forge", "forge"),
        ("neoforge", "neoforge"),
    ] {
        if let Some(v) = deps.get(key).and_then(|v| v.as_str()) {
            loader_kind = kind.to_string();
            loader_version = Some(v.to_string());
            break;
        }
    }

    let mut files = Vec::new();
    if let Some(items) = value.get("files").and_then(|v| v.as_array()) {
        for item in items {
            let Some(rel) = item.get("path").and_then(|v| v.as_str()) else { continue };
            let rel_path = Path::new(rel);
            if rel_path.is_absolute() || rel.contains("..") || rel.is_empty() {
                continue; // path traversal attempt inside the index
            }
            let skip = item
                .get("env")
                .and_then(|e| e.get("client"))
                .and_then(|v| v.as_str())
                .map(|v| v == "unsupported")
                .unwrap_or(false);
            let url = item
                .get("downloads")
                .and_then(|d| d.as_array())
                .and_then(|d| d.first())
                .and_then(|v| v.as_str())
                .unwrap_or_default()
                .to_string();
            if url.is_empty() {
                continue;
            }
            let sha1 = item
                .get("hashes")
                .and_then(|h| h.get("sha1"))
                .and_then(|v| v.as_str())
                .map(|s| s.to_string());
            files.push(MrpackFile {
                path: rel.to_string(),
                url,
                sha1,
                size: item.get("fileSize").and_then(|v| v.as_u64()).unwrap_or(0),
                skip,
            });
        }
    }

    Ok(MrpackIndex {
        name: value
            .get("name")
            .and_then(|v| v.as_str())
            .unwrap_or("Modpack")
            .to_string(),
        summary: value
            .get("summary")
            .and_then(|v| v.as_str())
            .unwrap_or_default()
            .to_string(),
        version_id: value
            .get("versionId")
            .and_then(|v| v.as_str())
            .unwrap_or_default()
            .to_string(),
        loader_kind,
        loader_version,
        mc_version,
        files,
    })
}

pub async fn search(app: &AppHandle, query: &str, limit: u64, offset: u64) -> Result<ModrinthSearchResult, String> {
    modrinth::search(app, query, limit, offset, None, None, Some("downloads".to_string()), "modpack").await
}

pub async fn versions(app: &AppHandle, project_id: &str) -> Result<Vec<ModrinthVersion>, String> {
    modrinth::versions(app, project_id, None, None).await
}

pub async fn install(app: &AppHandle, project_id: &str, version_id: &str) -> Result<Profile, String> {
    let state = app.state::<AppState>();
    let url = format!("https://api.modrinth.com/v2/version/{}", util::url_encode(version_id));
    let value = download::get_json_value(&state.http(), &url, 2).await?;
    let version = modrinth::parse_version(&value).ok_or_else(|| "Modrinth version not found".to_string())?;
    let file = version
        .files
        .iter()
        .find(|f| f.filename.ends_with(".mrpack"))
        .or_else(|| version.files.first())
        .ok_or_else(|| "This Modrinth version has no downloadable file".to_string())?;

    let target = state
        .paths()
        .cache
        .join(crate::paths::sanitize_name(&file.filename));
    download::download_files(
        app.clone(),
        format!("Modpack · {}", version.name),
        None,
        vec![FileSpec::new(&file.url, target.clone())
            .hash(file.hashes.get("sha1").cloned())
            .size(file.size)],
    )
    .await?;

    install_from_file(app, &target, Some((project_id.to_string(), version_id.to_string(), version.name.clone()))).await
}

pub async fn install_from_file(
    app: &AppHandle,
    path: &Path,
    origin: Option<(String, String, String)>,
) -> Result<Profile, String> {
    let state = app.state::<AppState>();
    if !path.is_file() {
        return Err("Modpack file not found".into());
    }
    let index = read_index(path)?;
    state.log(
        "info",
        "launcher",
        format!(
            "Installing modpack '{}' ({} {} / {})",
            index.name, index.loader_kind, index.mc_version, index.version_id
        ),
    );

    let profile = profiles::create(
        app,
        ProfileDraft {
            name: index.name.clone(),
            mc_version: index.mc_version.clone(),
            loader: LoaderRef {
                kind: index.loader_kind.clone(),
                version: index.loader_version.clone(),
            },
            game_dir: None,
            ram_mb: None,
            java_path: None,
            jvm_args: None,
            width: None,
            height: None,
            fullscreen: None,
            source: Some("modpack".into()),
            modpack: origin.as_ref().map(|(project, version, name)| ModpackRef {
                project_id: project.clone(),
                version_id: version.clone(),
                name: name.clone(),
            }),
        },
    )?;

    let game_dir = PathBuf::from(&profile.game_dir);

    // Loader first: launching without it would fail later anyway.
    if index.loader_kind != "vanilla" {
        loaders::install(
            app,
            &index.loader_kind,
            &index.mc_version,
            index.loader_version.clone(),
            Some(profile.id.clone()),
        )
        .await?;
    }

    // Files listed in the index.
    let mut specs = Vec::new();
    let mut skipped = 0;
    for file in &index.files {
        if file.skip {
            skipped += 1;
            continue;
        }
        let dest = match safe_join(&game_dir, &file.path) {
            Ok(p) => p,
            Err(_) => continue,
        };
        if let Some(parent) = dest.parent() {
            let _ = std::fs::create_dir_all(parent);
        }
        specs.push(FileSpec::new(file.url.clone(), dest).hash(file.sha1.clone()).size(file.size));
    }
    let total = specs.len();
    if !specs.is_empty() {
        download::download_files(
            app.clone(),
            format!("{} · {}", index.name, index.version_id),
            Some(profile.id.clone()),
            specs,
        )
        .await?;
    }
    state.log(
        "info",
        "launcher",
        format!("Modpack downloaded {} files ({skipped} client-unsupported skipped)", total),
    );

    // Pack overrides win over downloaded files.
    for prefix in ["overrides/", "client-overrides/"] {
        if let Err(e) = download::extract_zip_filtered(path, &game_dir, prefix, None) {
            state.log("warn", "launcher", format!("Could not extract {prefix}: {e}"));
        }
    }

    if !index.summary.is_empty() {
        let _ = util::write_json(
            &game_dir.join("winlaucher-modpack.json"),
            &serde_json::json!({ "summary": index.summary, "versionId": index.version_id }),
        );
    }

    let _ = app.emit("profile-changed", serde_json::json!({ "profileId": profile.id }));
    Ok(profile)
}

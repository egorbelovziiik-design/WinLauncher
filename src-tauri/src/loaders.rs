use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};

use serde_json::{json, Value};
use tauri::{AppHandle, Manager};

use crate::download::{self, FileSpec};
use crate::java;
use crate::models::*;
use crate::mojang;
use crate::paths::{safe_join, Paths};
use crate::state::AppState;
use crate::util;

const FABRIC_META: &str = "https://meta.fabricmc.net/v2";
const QUILT_META: &str = "https://meta.quiltmc.org/v3";
const FORGE_MAVEN: &str = "https://maven.minecraftforge.net";
const NEOFORGE_MAVEN: &str = "https://maven.neoforged.net";

pub fn normalize(kind: &str) -> String {
    match kind.trim().to_lowercase().as_str() {
        "fabric" => "fabric".into(),
        "quilt" => "quilt".into(),
        "forge" => "forge".into(),
        "neoforge" => "neoforge".into(),
        _ => "vanilla".into(),
    }
}

pub fn is_modded(kind: &str) -> bool {
    normalize(kind) != "vanilla"
}

fn forge_full_version(mc: &str, loader: &str) -> String {
    if loader.starts_with(&format!("{mc}-")) {
        loader.to_string()
    } else {
        format!("{mc}-{loader}")
    }
}

/// Name of the version folder a loader install should live in.
pub fn version_id(kind: &str, mc: &str, loader_version: &str) -> String {
    match normalize(kind).as_str() {
        "fabric" => format!("fabric-loader-{loader_version}-{mc}"),
        "quilt" => format!("quilt-loader-{loader_version}-{mc}"),
        "forge" => format!("{mc}-forge-{loader_version}"),
        "neoforge" => format!("neoforge-{loader_version}"),
        _ => mc.to_string(),
    }
}

/// Finds the installed version folder that belongs to this profile, if any.
pub fn resolved_version_id(profile: &Profile, paths: &Paths) -> Option<String> {
    if !is_modded(&profile.loader.kind) {
        return None;
    }
    let kind = normalize(&profile.loader.kind);
    let mc = profile.mc_version.as_str();
    let loader = profile.loader.version.clone().unwrap_or_default();
    let versions = paths.versions();

    let is_installed = |id: &str| -> bool {
        versions.join(id).join(format!("{id}.json")).is_file()
    };

    if !loader.is_empty() {
        let candidate = version_id(&kind, mc, &loader);
        if is_installed(&candidate) {
            return Some(candidate);
        }
    }

    // Fall back to any installed version that matches the loader + MC version.
    let entries = std::fs::read_dir(&versions).ok()?;
    let mut best: Option<String> = None;
    for entry in entries.flatten() {
        let name = entry.file_name().to_string_lossy().to_string();
        let matches = match kind.as_str() {
            "fabric" => name.starts_with("fabric-loader-") && name.contains(mc),
            "quilt" => name.starts_with("quilt-loader-") && name.contains(mc),
            "forge" => name.contains("forge") && name.contains(mc) && !name.starts_with("neoforge"),
            "neoforge" => name.starts_with("neoforge-") && (loader.is_empty() || name.contains(&loader)),
            _ => false,
        };
        if matches && entry.path().join(format!("{name}.json")).is_file() {
            best = Some(name);
        }
    }
    best
}

/* ------------------------------------------------------------------ */
/* Available loader versions                                           */
/* ------------------------------------------------------------------ */

fn json_loader_version(value: &Value) -> Option<LoaderVersion> {
    let loader = value.get("loader").unwrap_or(value);
    let version = loader.get("version").and_then(|v| v.as_str())?.to_string();
    let stable = loader.get("stable").and_then(|v| v.as_bool()).unwrap_or(true);
    Some(LoaderVersion { version, stable, mc_version: None })
}

pub async fn versions(app: &AppHandle, kind: &str, mc: &str) -> Result<Vec<LoaderVersion>, String> {
    let state = app.state::<AppState>();
    let client = state.http();
    let kind = normalize(kind);
    match kind.as_str() {
        "fabric" => {
            let url = format!("{FABRIC_META}/versions/loader/{mc}");
            let arr = download::get_json_value(&client, &url, 2).await?;
            let list: Vec<LoaderVersion> = arr
                .as_array()
                .map(|items| items.iter().filter_map(json_loader_version).collect())
                .unwrap_or_default();
            Ok(list)
        }
        "quilt" => {
            let url = format!("{QUILT_META}/versions/loader/{mc}");
            let arr = download::get_json_value(&client, &url, 2).await?;
            let list: Vec<LoaderVersion> = arr
                .as_array()
                .map(|items| {
                    items
                        .iter()
                        .filter_map(|item| {
                            let mut v = json_loader_version(item)?;
                            v.mc_version = Some(mc.to_string());
                            Some(v)
                        })
                        .collect()
                })
                .unwrap_or_default();
            Ok(list)
        }
        "forge" => {
            let url = format!("{FORGE_MAVEN}/net/minecraftforge/forge/maven-metadata.json");
            let value = download::get_json_value(&client, &url, 2).await?;
            let recommended = forge_recommended(&client, mc).await;
            let mut list = Vec::new();
            if let Some(items) = value.get(mc).and_then(|v| v.as_array()) {
                for item in items {
                    if let Some(full) = item.as_str() {
                        let short = full.strip_prefix(&format!("{mc}-")).unwrap_or(full).to_string();
                        list.push(LoaderVersion {
                            stable: recommended.as_deref() == Some(short.as_str()),
                            version: short,
                            mc_version: Some(mc.to_string()),
                        });
                    }
                }
            }
            list.reverse();
            Ok(list)
        }
        "neoforge" => {
            let url = format!("{NEOFORGE_MAVEN}/api/maven/versions/releases/net/neoforged/neoforge");
            let value = download::get_json_value(&client, &url, 2).await?;
            let prefix = neoforge_prefix(mc);
            let mut list = Vec::new();
            if let Some(items) = value.get("versions").and_then(|v| v.as_array()) {
                for item in items {
                    if let Some(version) = item.as_str() {
                        if version.starts_with(&prefix) && !version.contains("-beta") {
                            list.push(LoaderVersion {
                                version: version.to_string(),
                                stable: true,
                                mc_version: Some(mc.to_string()),
                            });
                        }
                    }
                }
            }
            list.reverse();
            Ok(list)
        }
        _ => Ok(Vec::new()),
    }
}

async fn forge_recommended(client: &reqwest::Client, mc: &str) -> Option<String> {
    let url = format!("{FORGE_MAVEN}/net/minecraftforge/forge/promotions_slim.json");
    let value = download::get_json_value(client, &url, 1).await.ok()?;
    value
        .get("promos")
        .and_then(|p| p.get(format!("{mc}-recommended")))
        .and_then(|v| v.as_str())
        .map(|s| s.to_string())
}

fn neoforge_prefix(mc: &str) -> String {
    let stripped = mc.strip_prefix("1.").unwrap_or(mc);
    let mut parts = stripped.split('.');
    let minor = parts.next().unwrap_or("0");
    let patch = parts.next().unwrap_or("0");
    format!("{minor}.{patch}.")
}

pub async fn latest(app: &AppHandle, kind: &str, mc: &str) -> Result<String, String> {
    let list = versions(app, kind, mc).await?;
    if let Some(stable) = list.iter().find(|v| v.stable) {
        return Ok(stable.version.clone());
    }
    list.first()
        .map(|v| v.version.clone())
        .ok_or_else(|| format!("No {} builds available for Minecraft {mc}", normalize(kind)))
}

/* ------------------------------------------------------------------ */
/* Installation                                                        */
/* ------------------------------------------------------------------ */

/// Ensures the loader for `profile` is installed and returns its version id.
pub async fn ensure(app: &AppHandle, profile: &Profile) -> Result<Option<String>, String> {
    if !is_modded(&profile.loader.kind) {
        return Ok(None);
    }
    let paths = app.state::<AppState>().paths();
    if let Some(id) = resolved_version_id(profile, &paths) {
        return Ok(Some(id));
    }
    let kind = normalize(&profile.loader.kind);
    let loader = match profile.loader.version.clone().filter(|v| !v.trim().is_empty()) {
        Some(v) => v,
        None => latest(app, &kind, &profile.mc_version).await?,
    };
    let id = install(app, &kind, &profile.mc_version, Some(loader), Some(profile.id.clone())).await?;
    Ok(Some(id))
}

pub async fn install(
    app: &AppHandle,
    kind: &str,
    mc: &str,
    loader_version: Option<String>,
    profile_id: Option<String>,
) -> Result<String, String> {
    let kind = normalize(kind);
    let state = app.state::<AppState>();
    let loader_version = match loader_version.filter(|v| !v.trim().is_empty()) {
        Some(v) => v,
        None => latest(app, &kind, mc).await?,
    };
    match kind.as_str() {
        "fabric" | "quilt" => {
            install_meta_loader(app, &kind, mc, &loader_version, profile_id).await
        }
        "forge" | "neoforge" => install_with_installer(app, &kind, mc, &loader_version, profile_id).await,
        _ => Ok(mc.to_string()),
    }
}

async fn install_meta_loader(
    app: &AppHandle,
    kind: &str,
    mc: &str,
    loader: &str,
    profile_id: Option<String>,
) -> Result<String, String> {
    let state = app.state::<AppState>();
    let paths = state.paths();
    state.set_launch_state(profile_id.clone(), "installing", format!("Minecraft {mc} · {kind} {loader}"));

    // The vanilla json has to exist before the loader can inherit from it.
    mojang::load_or_fetch_version_json(app, mc).await?;

    let url = if kind == "fabric" {
        format!("{FABRIC_META}/versions/loader/{mc}/{loader}/profile/json")
    } else {
        format!("{QUILT_META}/versions/loader/{mc}/{loader}/profile/json")
    };
    let mut profile_json = download::get_json_value(&state.http(), &url, 2).await?;
    if !profile_json.is_object() {
        return Err(format!("{kind} did not return a version profile"));
    }

    let id = version_id(kind, mc, loader);
    let obj = profile_json
        .as_object_mut()
        .ok_or_else(|| "Unexpected loader profile".to_string())?;
    obj.insert("id".into(), json!(id));
    obj.insert("inheritsFrom".into(), json!(mc));
    if !obj.contains_key("type") {
        obj.insert("type".into(), json!("release"));
    }

    let dir = paths.version_dir(&id);
    std::fs::create_dir_all(&dir).map_err(|e| e.to_string())?;
    let file = dir.join(format!("{id}.json"));
    util::write_json(&file, &profile_json)?;
    state.log("info", "launcher", format!("Installed {kind} {loader} for {mc} as {id}"));

    // Make sure the vanilla client jar and libraries exist too.
    let merged = mojang::load_merged(app, &id).await?;
    mojang::ensure_version(app, &merged, None, profile_id).await?;
    Ok(id)
}

async fn install_with_installer(
    app: &AppHandle,
    kind: &str,
    mc: &str,
    loader: &str,
    profile_id: Option<String>,
) -> Result<String, String> {
    let state = app.state::<AppState>();
    let paths = state.paths();
    state.set_launch_state(profile_id.clone(), "installing", format!("Minecraft {mc} · {kind} {loader}"));
    state.log("info", "launcher", format!("Installing {kind} {loader} for Minecraft {mc}"));

    // The installer patches a vanilla client, so it needs the base version first.
    mojang::load_or_fetch_version_json(app, mc).await?;

    let required_java = mojang::java_major_for(mc);
    let runtime = java::ensure_major(app, required_java).await?;

    let (url, file_name) = if kind == "forge" {
        let full = forge_full_version(mc, loader);
        (
            format!("{FORGE_MAVEN}/net/minecraftforge/forge/{full}/forge-{full}-installer.jar"),
            format!("forge-{full}-installer.jar"),
        )
    } else {
        (
            format!("{NEOFORGE_MAVEN}/releases/net/neoforged/neoforge/{loader}/neoforge-{loader}-installer.jar"),
            format!("neoforge-{loader}-installer.jar"),
        )
    };

    let installer = paths.cache.join(&file_name);
    download::download_files(app.clone(), file_name.clone(), None, vec![FileSpec::new(&url, installer.clone())]).await?;

    // Forge requires a launcher profile file to install into.
    let root = paths.mc.clone();
    std::fs::create_dir_all(&root).map_err(|e| e.to_string())?;
    let profiles = root.join("launcher_profiles.json");
    if !profiles.exists() {
        let body = json!({ "profiles": {}, "settings": {}, "version": 3, "clientToken": "" });
        util::write_json(&profiles, &body)?;
    }

    let before = list_version_dirs(&paths);
    run_installer(app, &runtime.path, &installer, &root)?;
    let after = list_version_dirs(&paths);

    let mut created: Vec<String> = after.into_iter().filter(|id| !before.contains(id)).collect();
    if created.is_empty() {
        // Re-installing the same build produces no new folder: look for the target name.
        let expected = version_id(kind, mc, loader);
        if paths.version_dir(&expected).join(format!("{expected}.json")).is_file() {
            created.push(expected);
        }
    }
    if created.is_empty() {
        return Err(format!(
            "The {kind} installer finished but no version file was created — check the log for details"
        ));
    }
    created.sort();
    let id = created
        .iter()
        .find(|id| id.contains(loader) || id.contains(kind))
        .cloned()
        .unwrap_or_else(|| created.last().cloned().unwrap_or_default());
    state.log("info", "launcher", format!("{kind} installed as version '{id}'"));

    // Make sure libraries and assets referenced by the merged version are present.
    let merged = mojang::load_merged(app, &id).await?;
    mojang::ensure_version(app, &merged, None, profile_id).await?;
    Ok(id)
}

fn list_version_dirs(paths: &Paths) -> Vec<String> {
    let mut out = Vec::new();
    if let Ok(entries) = std::fs::read_dir(paths.versions()) {
        for entry in entries.flatten() {
            if entry.file_type().map(|t| t.is_dir()).unwrap_or(false) {
                out.push(entry.file_name().to_string_lossy().to_string());
            }
        }
    }
    out
}

fn run_installer(app: &AppHandle, java: &str, jar: &Path, target: &Path) -> Result<(), String> {
    let state = app.state::<AppState>();
    let mut cmd = Command::new(java);
    cmd.arg("-jar")
        .arg(jar)
        .arg("--installClient")
        .arg(target)
        .current_dir(target)
        .stdin(Stdio::null())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());
    util::hide_console(&mut cmd);
    let mut child = cmd
        .spawn()
        .map_err(|e| format!("Could not start the loader installer: {e}"))?;

    let stdout = child.stdout.take();
    let stderr = child.stderr.take();
    if let Some(out) = stdout {
        let app = app.clone();
        std::thread::spawn(move || {
            use std::io::BufRead;
            for line in std::io::BufReader::new(out).lines().map_while(Result::ok) {
                if !line.trim().is_empty() {
                    app.state::<AppState>().log("info", "installer", line);
                }
            }
        });
    }
    if let Some(err) = stderr {
        let app = app.clone();
        std::thread::spawn(move || {
            use std::io::BufRead;
            for line in std::io::BufReader::new(err).lines().map_while(Result::ok) {
                if !line.trim().is_empty() {
                    app.state::<AppState>().log("warn", "installer", line);
                }
            }
        });
    }

    let started = std::time::Instant::now();
    loop {
        match child.try_wait() {
            Ok(Some(status)) => {
                if status.success() {
                    return Ok(());
                }
                return Err(format!(
                    "The loader installer exited with code {:?} — see the log for details",
                    status.code()
                ));
            }
            Ok(None) => {
                if started.elapsed() > std::time::Duration::from_secs(1200) {
                    let _ = child.kill();
                    return Err("The loader installer timed out".into());
                }
                std::thread::sleep(std::time::Duration::from_millis(500));
            }
            Err(e) => return Err(format!("Lost track of the loader installer: {e}")),
        }
    }
}

/* ------------------------------------------------------------------ */
/* Modpack helpers                                                     */
/* ------------------------------------------------------------------ */

/// Writes a minimal version json for loaders whose profile we generate locally.
pub fn write_generated_version(paths: &Paths, id: &str, json: &Value) -> Result<PathBuf, String> {
    let dir = safe_join(&paths.versions(), id)?;
    std::fs::create_dir_all(&dir).map_err(|e| e.to_string())?;
    let file = dir.join(format!("{id}.json"));
    util::write_json(&file, json)?;
    Ok(file)
}

use std::collections::{HashMap, HashSet};
use std::path::{Path, PathBuf};
use std::time::{Duration, Instant};

use serde_json::Value;
use tauri::{AppHandle, Manager};

use crate::download::{self, FileSpec};
use crate::models::*;
use crate::paths::safe_join;
use crate::state::AppState;
use crate::util;

const MANIFEST_V2: &str = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
const RESOURCES: &str = "https://resources.download.minecraft.net";

pub struct MergedVersion {
    pub id: String,
    pub root_id: String,
    pub main_class: String,
    pub libraries: Vec<Library>,
    pub jvm_args: Vec<Value>,
    pub game_args: Vec<Value>,
    pub minecraft_arguments: Option<String>,
    pub asset_index: Option<AssetIndexRef>,
    pub assets: String,
    pub downloads: Downloads,
    pub java_version: Option<JavaVersion>,
    pub logging: Option<Logging>,
    pub version_type: String,
    pub natives_dir: PathBuf,
    pub client_jar: PathBuf,
}

impl MergedVersion {
    pub fn required_java(&self) -> u32 {
        if let Some(j) = &self.java_version {
            if j.major_version > 0 {
                return j.major_version;
            }
        }
        java_major_for(self.root_id.as_str())
    }
}

/* ------------------------------------------------------------------ */
/* Manifest                                                            */
/* ------------------------------------------------------------------ */

pub async fn fetch_manifest(app: &AppHandle, force: bool) -> Result<VersionManifest, String> {
    let state = app.state::<AppState>();
    if !force {
        if let Some((t, m)) = crate::state::lock(&state.manifest).as_ref() {
            if t.elapsed() < Duration::from_secs(3600) {
                return Ok(m.clone());
            }
        }
    }
    let paths = state.paths();
    let cache_file = paths.cache.join("version_manifest_v2.json");
    if !force {
        if let Ok(meta) = std::fs::metadata(&cache_file) {
            if meta.modified().ok().and_then(|t| t.elapsed().ok()).map(|e| e < Duration::from_secs(3600)).unwrap_or(false) {
                if let Ok(text) = std::fs::read_to_string(&cache_file) {
                    if let Ok(m) = serde_json::from_str::<VersionManifest>(&text) {
                        *crate::state::lock(&state.manifest) = Some((Instant::now(), m.clone()));
                        return Ok(m);
                    }
                }
            }
        }
    }
    let client = state.http();
    let text = download::get_text(&client, MANIFEST_V2, 2).await?;
    if let Some(parent) = cache_file.parent() {
        let _ = std::fs::create_dir_all(parent);
    }
    let _ = std::fs::write(&cache_file, &text);
    let manifest: VersionManifest =
        serde_json::from_str(&text).map_err(|e| format!("Invalid version manifest: {e}"))?;
    *crate::state::lock(&state.manifest) = Some((Instant::now(), manifest.clone()));
    Ok(manifest)
}

pub async fn list_versions(app: &AppHandle) -> Result<Vec<McVersion>, String> {
    let manifest = fetch_manifest(app, false).await?;
    let paths = app.state::<AppState>().paths();
    let versions_dir = paths.versions();
    Ok(manifest
        .versions
        .into_iter()
        .map(|v| {
            let dir = versions_dir.join(&v.id);
            let installed = dir.join(format!("{}.json", v.id)).is_file() && dir.join(format!("{}.jar", v.id)).is_file();
            McVersion {
                id: v.id,
                version_type: v.version_type,
                release_time: v.release_time,
                url: v.url,
                sha1: v.sha1,
                compliance_level: v.compliance_level,
                installed: Some(installed),
            }
        })
        .collect())
}

pub fn version_json_path(app: &AppHandle, id: &str) -> PathBuf {
    app.state::<AppState>().paths().version_dir(id).join(format!("{id}.json"))
}

pub async fn load_or_fetch_version_json(app: &AppHandle, id: &str) -> Result<VersionJson, String> {
    if id.contains("..") || id.contains(['/', '\\']) {
        return Err("Unsafe version id".into());
    }
    let path = version_json_path(app, id);
    if path.is_file() {
        if let Some(json) = util::read_json::<VersionJson>(&path) {
            return Ok(json);
        }
    }
    let manifest = fetch_manifest(app, false).await?;
    let entry = manifest
        .versions
        .iter()
        .find(|v| v.id == id)
        .ok_or_else(|| format!("Version {id} not found in the Mojang manifest"))?;
    let state = app.state::<AppState>();
    let text = download::get_text(&state.http(), &entry.url, 2).await?;
    let json: VersionJson = serde_json::from_str(&text).map_err(|e| format!("Invalid version {id}: {e}"))?;
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent).map_err(|e| e.to_string())?;
    }
    std::fs::write(&path, &text).map_err(|e| e.to_string())?;
    Ok(json)
}

/* ------------------------------------------------------------------ */
/* Version resolution (parents, loaders)                               */
/* ------------------------------------------------------------------ */

pub async fn load_merged(app: &AppHandle, id: &str) -> Result<MergedVersion, String> {
    let mut child = load_or_fetch_version_json(app, id).await?;
    if child.id.is_empty() {
        child.id = id.to_string();
    }
    let mut root_id = id.to_string();
    let mut parent: Option<VersionJson> = None;
    let mut parent_id: Option<String> = None;

    if let Some(inherits) = child.inherits_from.clone() {
        let mut current = inherits.clone();
        for _ in 0..3 {
            let p = load_or_fetch_version_json(app, &current).await?;
            if let Some(next) = p.inherits_from.clone() {
                let pid = p.id.clone();
                parent = merge_parent(parent, p);
                parent_id = Some(pid);
                current = next;
                continue;
            }
            root_id = current.clone();
            parent = merge_parent(parent, p);
            parent_id = Some(current);
            break;
        }
    }

    let paths = app.state::<AppState>().paths();
    let merged = merge_chain(id, &child, parent.as_ref(), &root_id, &paths);
    let _ = parent_id;
    Ok(merged)
}

fn merge_parent(acc: Option<VersionJson>, next: VersionJson) -> Option<VersionJson> {
    match acc {
        None => Some(next),
        Some(mut a) => {
            // `next` is older (further up the chain): keep its base props, but its libraries
            // are inherited, while the closer parent's libraries come first.
            let mut libs = a.libraries;
            libs.extend(next.libraries);
            a.libraries = libs;
            if a.main_class.is_empty() {
                a.main_class = next.main_class;
            }
            if a.asset_index.is_none() {
                a.asset_index = next.asset_index;
            }
            if a.downloads.is_none() {
                a.downloads = next.downloads;
            }
            if a.java_version.is_none() {
                a.java_version = next.java_version;
            }
            if a.logging.is_none() {
                a.logging = next.logging;
            }
            Some(a)
        }
    }
}

fn merge_chain(
    id: &str,
    child: &VersionJson,
    parent: Option<&VersionJson>,
    root_id: &str,
    paths: &crate::paths::Paths,
) -> MergedVersion {
    let mut libraries: Vec<Library> = Vec::new();
    let mut jvm_args: Vec<Value> = Vec::new();
    let mut game_args: Vec<Value> = Vec::new();
    let mut minecraft_arguments = None;
    let mut main_class = String::new();
    let mut asset_index = None;
    let mut assets = None;
    let mut downloads = Downloads::default();
    let mut java_version = None;
    let mut logging = None;
    let mut version_type = "release".to_string();

    if let Some(p) = parent {
        libraries.extend(p.libraries.iter().cloned());
        if let Some(a) = &p.arguments {
            jvm_args.extend(a.jvm.iter().cloned());
            game_args.extend(a.game.iter().cloned());
        }
        if p.minecraft_arguments.is_some() {
            minecraft_arguments = p.minecraft_arguments.clone();
        }
        main_class = p.main_class.clone();
        asset_index = p.asset_index.clone();
        assets = p.assets.clone();
        downloads = p.downloads.clone().unwrap_or_default();
        java_version = p.java_version.clone();
        logging = p.logging.clone();
        if !p.version_type.is_empty() {
            version_type = p.version_type.clone();
        }
    }

    libraries.extend(child.libraries.iter().cloned());
    if let Some(a) = &child.arguments {
        jvm_args.extend(a.jvm.iter().cloned());
        game_args.extend(a.game.iter().cloned());
    }
    if child.minecraft_arguments.is_some() {
        minecraft_arguments = child.minecraft_arguments.clone();
    }
    if !child.main_class.is_empty() {
        main_class = child.main_class.clone();
    }
    if child.asset_index.is_some() {
        asset_index = child.asset_index.clone();
    }
    if child.assets.is_some() {
        assets = child.assets.clone();
    }
    if child.downloads.is_some() {
        downloads = child.downloads.clone().unwrap_or_default();
    }
    if child.java_version.is_some() {
        java_version = child.java_version.clone();
    }
    if child.logging.is_some() {
        logging = child.logging.clone();
    }
    if !child.version_type.is_empty() {
        version_type = child.version_type.clone();
    }

    let version_dir = paths.version_dir(id);
    MergedVersion {
        id: id.to_string(),
        root_id: root_id.to_string(),
        main_class,
        libraries,
        jvm_args,
        game_args,
        minecraft_arguments,
        asset_index,
        assets: assets.unwrap_or_else(|| "legacy".to_string()),
        downloads,
        java_version,
        logging,
        version_type,
        natives_dir: version_dir.join("natives"),
        client_jar: paths.version_dir(root_id).join(format!("{root_id}.jar")),
    }
}

/* ------------------------------------------------------------------ */
/* Rules & files                                                       */
/* ------------------------------------------------------------------ */

pub fn os_name() -> &'static str {
    if cfg!(windows) {
        "windows"
    } else if cfg!(target_os = "macos") {
        "osx"
    } else {
        "linux"
    }
}

pub fn os_arch() -> &'static str {
    if cfg!(target_arch = "x86_64") {
        "x86_64"
    } else if cfg!(target_arch = "aarch64") {
        "arm64"
    } else {
        "x86"
    }
}

pub fn default_features() -> HashMap<String, bool> {
    let mut f = HashMap::new();
    f.insert("is_demo_user".to_string(), false);
    f.insert("has_custom_resolution".to_string(), true);
    f
}

pub fn rules_allow(rules: &[Rule], features: &HashMap<String, bool>) -> bool {
    if rules.is_empty() {
        return true;
    }
    let mut allowed = false;
    for rule in rules {
        let os_ok = match &rule.os {
            None => true,
            Some(os) => {
                let name_ok = os.name.as_ref().map(|n| n == os_name()).unwrap_or(true);
                let arch_ok = os.arch.as_ref().map(|a| a == os_arch()).unwrap_or(true);
                name_ok && arch_ok
            }
        };
        let feat_ok = match &rule.features {
            None => true,
            Some(map) => map.iter().all(|(k, v)| features.get(k).copied().unwrap_or(false) == *v),
        };
        if os_ok && feat_ok {
            allowed = rule.action != "disallow";
        }
    }
    allowed
}

fn maven_fallback(name: &str, path: &str) -> String {
    let base = if name.starts_with("net.neoforged") {
        "https://maven.neoforged.net/releases/"
    } else if name.starts_with("net.minecraftforge") {
        "https://maven.minecraftforge.net/"
    } else if name.starts_with("org.quiltmc") {
        "https://maven.quiltmc.org/repository/release/"
    } else if name.starts_with("net.fabricmc") || name.starts_with("org.fabricmc") {
        "https://maven.fabricmc.net/"
    } else {
        "https://libraries.minecraft.net/"
    };
    format!("{base}{path}")
}

/// Builds a library artifact from a maven coordinate
/// (`group:artifact:version[:classifier][@ext]`) for version files that only
/// ship a `url` and no resolved `downloads` block (older Forge builds).
fn coords_to_artifact(name: &str, base_url: &str) -> Option<LibraryArtifact> {
    let (coords, ext) = match name.split_once('@') {
        Some((c, e)) => (c, e.to_string()),
        None => (name, "jar".to_string()),
    };
    let parts: Vec<&str> = coords.split(':').collect();
    if parts.len() < 3 {
        return None;
    }
    let group = parts[0];
    let artifact = parts[1];
    let version = parts[2];
    let classifier = parts.get(3).map(|c| (*c).to_string()).filter(|c| !c.is_empty());
    let file_name = match &classifier {
        Some(c) => format!("{artifact}-{version}-{c}.{ext}"),
        None => format!("{artifact}-{version}.{ext}"),
    };
    let path = format!(
        "{}/{artifact}/{version}/{file_name}",
        group.replace('.', "/")
    );
    let base = if base_url.is_empty() {
        maven_fallback(name, &path)
    } else {
        format!("{}{path}", if base_url.ends_with('/') { base_url.to_string() } else { format!("{base_url}/") })
    };
    Some(LibraryArtifact {
        path,
        url: base,
        sha1: None,
        size: 0,
    })
}

pub fn library_artifacts(merged: &MergedVersion) -> Vec<(LibraryArtifact, String)> {
    let features = default_features();
    let mut seen: HashSet<String> = HashSet::new();
    let mut out: Vec<(LibraryArtifact, String)> = Vec::new();
    for lib in &merged.libraries {
        if !rules_allow(&lib.rules, &features) {
            continue;
        }
        let resolved = match &lib.downloads.artifact {
            Some(artifact) if !artifact.path.is_empty() => Some(artifact.clone()),
            _ => coords_to_artifact(&lib.name, &lib.url),
        };
        if let Some(mut art) = resolved {
            if !seen.insert(art.path.clone()) {
                continue;
            }
            if art.url.is_empty() {
                art.url = maven_fallback(&lib.name, &art.path);
            }
            out.push((art, lib.name.clone()));
        }
    }
    out
}

/// Native classifier jars that have to be extracted (old Minecraft versions).
pub fn native_artifacts(merged: &MergedVersion) -> Vec<(LibraryArtifact, Vec<String>)> {
    let features = default_features();
    let mut out = Vec::new();
    for lib in &merged.libraries {
        if !rules_allow(&lib.rules, &features) {
            continue;
        }
        let Some(natives) = &lib.natives else { continue };
        let Some(classifier_template) = natives.get(os_name()) else { continue };
        let classifier = classifier_template.replace("${arch}", if os_arch() == "x86_64" { "64" } else { "32" });
        let Some(classifiers) = &lib.downloads.classifiers else { continue };
        let Some(artifact) = classifiers.get(&classifier) else { continue };
        if artifact.path.is_empty() {
            continue;
        }
        let mut art = artifact.clone();
        if art.url.is_empty() {
            art.url = maven_fallback(&lib.name, &art.path);
        }
        let exclude = lib.extract.as_ref().map(|e| e.exclude.clone()).unwrap_or_default();
        out.push((art, exclude));
    }
    out
}

pub fn library_specs(app: &AppHandle, merged: &MergedVersion) -> Vec<FileSpec> {
    let paths = app.state::<AppState>().paths();
    let lib_root = paths.libraries();
    let mut specs = Vec::new();
    for (artifact, _) in library_artifacts(merged) {
        specs.push(
            FileSpec::new(artifact.url.clone(), lib_root.join(artifact.path.replace('/', std::path::MAIN_SEPARATOR_STR)))
                .hash(artifact.sha1.clone())
                .size(artifact.size),
        );
    }
    for (artifact, _) in native_artifacts(merged) {
        specs.push(
            FileSpec::new(artifact.url.clone(), lib_root.join(artifact.path.replace('/', std::path::MAIN_SEPARATOR_STR)))
                .hash(artifact.sha1.clone())
                .size(artifact.size),
        );
    }
    specs
}

pub fn client_spec(app: &AppHandle, merged: &MergedVersion) -> Option<FileSpec> {
    let paths = app.state::<AppState>().paths();
    let client = merged.downloads.client.as_ref()?;
    let url = client.url.clone().unwrap_or_default();
    if url.is_empty() {
        return None;
    }
    Some(
        FileSpec::new(url, paths.version_dir(&merged.root_id).join(format!("{}.jar", merged.root_id)))
            .hash(client.sha1.clone())
            .size(client.size),
    )
}

pub fn asset_index_spec(app: &AppHandle, merged: &MergedVersion) -> Option<FileSpec> {
    let paths = app.state::<AppState>().paths();
    let idx = merged.asset_index.as_ref()?;
    let url = idx.url.clone().unwrap_or_default();
    if url.is_empty() {
        return None;
    }
    Some(
        FileSpec::new(url, paths.assets().join("indexes").join(format!("{}.json", idx.id)))
            .hash(idx.sha1.clone())
            .size(idx.size),
    )
}

pub fn logging_spec(app: &AppHandle, merged: &MergedVersion) -> Option<FileSpec> {
    let paths = app.state::<AppState>().paths();
    let client = merged.logging.as_ref()?.client.as_ref()?;
    let file = client.file.as_ref()?;
    if file.url.is_empty() {
        return None;
    }
    Some(
        FileSpec::new(file.url.clone(), paths.assets().join("log_configs").join(&file.id))
            .hash(file.sha1.clone())
            .size(file.size),
    )
}

pub fn read_asset_index(app: &AppHandle, merged: &MergedVersion) -> Option<AssetIndexFile> {
    let paths = app.state::<AppState>().paths();
    let idx = merged.asset_index.as_ref()?;
    util::read_json(&paths.assets().join("indexes").join(format!("{}.json", idx.id)))
}

pub fn asset_specs(app: &AppHandle, index: &AssetIndexFile) -> Vec<FileSpec> {
    let paths = app.state::<AppState>().paths();
    let objects = paths.assets().join("objects");
    let mut seen = HashSet::new();
    let mut specs = Vec::new();
    for obj in index.objects.values() {
        if obj.hash.len() < 2 || !seen.insert(obj.hash.clone()) {
            continue;
        }
        let prefix = &obj.hash[0..2];
        specs.push(
            FileSpec::new(
                format!("{RESOURCES}/{prefix}/{}", obj.hash),
                objects.join(prefix).join(&obj.hash),
            )
            .hash(Some(obj.hash.clone()))
            .size(obj.size),
        );
    }
    specs
}

/* ------------------------------------------------------------------ */
/* Installation                                                        */
/* ------------------------------------------------------------------ */

pub async fn ensure_libraries(app: &AppHandle, merged: &MergedVersion) -> Result<(), String> {
    let specs = library_specs(app, merged);
    download::download_files(app.clone(), format!("{} · Libraries", merged.id), None, specs).await?;
    extract_natives(app, merged)
}

fn extract_natives(app: &AppHandle, merged: &MergedVersion) -> Result<(), String> {
    let paths = app.state::<AppState>().paths();
    let lib_root = paths.libraries();
    let natives_dir = &merged.natives_dir;
    if native_artifacts(merged).is_empty() {
        return Ok(());
    }
    std::fs::create_dir_all(natives_dir).map_err(|e| e.to_string())?;
    for (artifact, excludes) in native_artifacts(merged) {
        let jar = lib_root.join(artifact.path.replace('/', std::path::MAIN_SEPARATOR_STR));
        if !jar.is_file() {
            continue;
        }
        let skip = |name: &str| {
            name.starts_with("META-INF/")
                || excludes.iter().any(|ex| {
                    let prefix = ex.trim_end_matches('/');
                    name.starts_with(prefix)
                })
        };
        let _ = download::extract_zip_filtered(&jar, natives_dir, "", Some(&skip));
    }
    Ok(())
}

pub async fn ensure_client(app: &AppHandle, merged: &MergedVersion) -> Result<(), String> {
    let paths = app.state::<AppState>().paths();
    let dest = paths.version_dir(&merged.root_id).join(format!("{}.jar", merged.root_id));
    if dest.is_file() {
        return Ok(());
    }
    if let Some(spec) = client_spec(app, merged) {
        download::download_files(app.clone(), format!("Minecraft {}", merged.root_id), None, vec![spec]).await?;
    }
    if !dest.is_file() {
        return Err(format!(
            "Client jar for {} is missing and the version metadata has no download URL",
            merged.root_id
        ));
    }
    Ok(())
}

pub async fn ensure_assets(app: &AppHandle, merged: &MergedVersion, game_dir: Option<&Path>) -> Result<(), String> {
    if let Some(spec) = asset_index_spec(app, merged) {
        download::download_files(app.clone(), format!("Assets {}", merged.root_id), None, vec![spec]).await?;
    } else {
        return Ok(());
    }
    let Some(index) = read_asset_index(app, merged) else {
        return Ok(());
    };
    let specs = asset_specs(app, &index);
    download::download_files(app.clone(), format!("Assets {}", merged.root_id), None, specs).await?;

    let paths = app.state::<AppState>().paths();
    let idx_id = merged.asset_index.as_ref().map(|i| i.id.clone()).unwrap_or_default();
    let objects = paths.assets().join("objects");

    // Legacy versions read every asset by its logical name.
    if index.virtual_.unwrap_or(false) || index.map_to_resources.unwrap_or(false) {
        let virtual_dir = paths.assets().join("virtual").join(&idx_id);
        let objects_clone = objects.clone();
        let entries: Vec<(String, String)> = index
            .objects
            .iter()
            .map(|(name, obj)| (name.clone(), obj.hash.clone()))
            .collect();
        let virtual_clone = virtual_dir.clone();
        std::thread::spawn(move || {
            for (name, hash) in entries {
                if hash.len() < 2 {
                    continue;
                }
                let src = objects_clone.join(&hash[0..2]).join(&hash);
                if let Ok(dest) = safe_join(&virtual_clone, &name) {
                    if let Some(parent) = dest.parent() {
                        let _ = std::fs::create_dir_all(parent);
                    }
                    let _ = std::fs::copy(&src, &dest);
                }
            }
        });
        if index.map_to_resources.unwrap_or(false) {
            if let Some(gd) = game_dir {
                let res = gd.join("resources");
                let _ = util::copy_dir_all(&virtual_dir, &res);
            }
        }
    }
    Ok(())
}

pub async fn ensure_logging(app: &AppHandle, merged: &MergedVersion) -> Result<(), String> {
    if let Some(spec) = logging_spec(app, merged) {
        download::download_files(app.clone(), "Logging configuration".to_string(), None, vec![spec]).await?;
    }
    Ok(())
}

/// Downloads/verifies everything required to run this version.
pub async fn ensure_version(
    app: &AppHandle,
    merged: &MergedVersion,
    game_dir: Option<&Path>,
    profile_id: Option<String>,
) -> Result<(), String> {
    let state = app.state::<AppState>();
    state.set_launch_state(profile_id, "downloading", format!("Minecraft {}", merged.id));
    ensure_client(app, merged).await?;
    ensure_libraries(app, merged).await?;
    ensure_logging(app, merged).await?;
    ensure_assets(app, merged, game_dir).await?;
    Ok(())
}

/// Fast existence check used for status badges (no hashing).
pub fn install_status(app: &AppHandle, merged: &MergedVersion) -> (usize, usize, u64, bool) {
    let paths = app.state::<AppState>().paths();
    let mut missing = 0usize;
    let mut total = 0usize;
    let mut bytes = 0u64;
    let mut version_installed = merged.client_jar.is_file();

    for (artifact, _) in library_artifacts(merged) {
        total += 1;
        let p = paths.libraries().join(artifact.path.replace('/', std::path::MAIN_SEPARATOR_STR));
        match std::fs::metadata(&p) {
            Ok(m) if m.is_file() && (artifact.size == 0 || m.len() == artifact.size) => {
                bytes += m.len();
            }
            _ => missing += 1,
        }
    }
    if let Some(index) = read_asset_index(app, merged) {
        let objects = paths.assets().join("objects");
        for obj in index.objects.values() {
            total += 1;
            let p = objects.join(&obj.hash[0..2.min(obj.hash.len())]).join(&obj.hash);
            match std::fs::metadata(&p) {
                Ok(m) if m.is_file() => bytes += m.len(),
                _ => missing += 1,
            }
        }
    } else if merged.asset_index.is_some() {
        missing += 1;
        total += 1;
        version_installed = false;
    }
    if !merged.client_jar.is_file() {
        missing += 1;
        total += 1;
        version_installed = false;
    }
    (missing, total, bytes, version_installed)
}

pub fn delete_version(app: &AppHandle, id: &str) -> Result<(), String> {
    if id.contains("..") || id.contains(['/', '\\']) {
        return Err("Unsafe version id".into());
    }
    let paths = app.state::<AppState>().paths();
    util::remove_dir_if_exists(&paths.version_dir(id)).map_err(|e| e.to_string())
}

/* ------------------------------------------------------------------ */
/* Java requirements                                                   */
/* ------------------------------------------------------------------ */

pub fn parse_mc_version(id: &str) -> Option<(u32, u32, u32)> {
    let mut parts = id.split('.');
    let a = parts.next()?.parse::<u32>().ok()?;
    let b = parts.next().and_then(|s| s.parse::<u32>().ok()).unwrap_or(0);
    let c = parts
        .next()
        .map(|s| s.chars().take_while(|ch| ch.is_ascii_digit()).collect::<String>())
        .and_then(|s| s.parse::<u32>().ok())
        .unwrap_or(0);
    Some((a, b, c))
}

pub fn java_major_for(mc: &str) -> u32 {
    // Weekly snapshots ("25w31a") always use the newest runtime.
    if !mc.contains('.') && mc.chars().next().map(|c| c.is_ascii_digit()).unwrap_or(false) {
        return 21;
    }
    match parse_mc_version(mc) {
        Some((1, minor, patch)) => {
            if minor >= 21 {
                21
            } else if minor == 20 {
                if patch >= 5 {
                    21
                } else {
                    17
                }
            } else if minor == 18 || minor == 19 {
                17
            } else if minor == 17 {
                16
            } else {
                8
            }
        }
        _ => 8,
    }
}

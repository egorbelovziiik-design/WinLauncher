use crate::{
    downloads::{self, Hash, Transfer},
    security,
    state::AppState,
};
use anyhow::{bail, Context, Result};
use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::{collections::HashMap, path::Path};
use tokio_util::sync::CancellationToken;

pub const MANIFEST_URL: &str = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
#[derive(Clone, Serialize, Deserialize)]
pub struct VersionEntry {
    pub id: String,
    #[serde(rename(deserialize = "type", serialize = "kind"))]
    pub kind: String,
    pub url: String,
    pub sha1: String,
    #[serde(rename(deserialize = "releaseTime", serialize = "release_time"))]
    pub release_time: String,
}
#[derive(Clone, Serialize, Deserialize)]
pub struct Manifest {
    pub latest: HashMap<String, String>,
    pub versions: Vec<VersionEntry>,
}
#[derive(Clone, Serialize, Deserialize)]
pub struct Download {
    pub url: String,
    pub sha1: String,
    pub size: Option<u64>,
    pub path: Option<String>,
}
#[derive(Clone, Serialize, Deserialize)]
pub struct AssetIndex {
    pub id: String,
    pub url: String,
    pub sha1: String,
    pub size: Option<u64>,
}
#[derive(Clone, Serialize, Deserialize, Default)]
pub struct LibraryDownloads {
    pub artifact: Option<Download>,
    #[serde(default)]
    pub classifiers: HashMap<String, Download>,
}
#[derive(Clone, Serialize, Deserialize)]
pub struct Library {
    pub name: String,
    pub downloads: Option<LibraryDownloads>,
    pub url: Option<String>,
    pub rules: Option<Vec<Value>>,
    pub natives: Option<HashMap<String, String>>,
    pub extract: Option<Value>,
}
#[derive(Clone, Serialize, Deserialize)]
pub struct JavaVersion {
    #[serde(rename = "majorVersion")]
    pub major_version: u32,
}
#[derive(Clone, Serialize, Deserialize)]
pub struct Version {
    pub id: String,
    #[serde(rename = "mainClass")]
    pub main_class: String,
    #[serde(rename = "type", default = "release")]
    pub kind: String,
    #[serde(default)]
    pub libraries: Vec<Library>,
    #[serde(default)]
    pub downloads: HashMap<String, Download>,
    #[serde(rename = "assetIndex")]
    pub asset_index: Option<AssetIndex>,
    pub assets: Option<String>,
    #[serde(rename = "javaVersion")]
    pub java_version: Option<JavaVersion>,
    pub arguments: Option<Value>,
    #[serde(rename = "minecraftArguments")]
    pub minecraft_arguments: Option<String>,
    #[serde(rename = "inheritsFrom")]
    pub inherits_from: Option<String>,
    pub logging: Option<Value>,
}
fn release() -> String {
    "release".into()
}

pub async fn manifest(state: &AppState) -> Result<Manifest> {
    let data = downloads::bytes(state, MANIFEST_URL, 8 * 1024 * 1024).await?;
    let result: Manifest = serde_json::from_slice(&data)?;
    std::fs::write(
        state.root().join("minecraft/version_manifest_v2.json"),
        &data,
    )?;
    Ok(result)
}
pub async fn version(state: &AppState, id: &str, cancel: &CancellationToken) -> Result<Version> {
    security::safe_id(id)?;
    let entry = manifest(state)
        .await?
        .versions
        .into_iter()
        .find(|v| v.id == id)
        .context("Версия отсутствует в официальном манифесте Mojang")?;
    let path = security::within(&state.root(), &format!("minecraft/versions/{id}/{id}.json"))?;
    downloads::download(
        state,
        &Transfer {
            url: entry.url,
            path: path.clone(),
            hash: Hash::new("sha1", &entry.sha1)?,
            size: None,
        },
        cancel,
    )
    .await?;
    let version: Version = serde_json::from_slice(&std::fs::read(path)?)?;
    if version.id != id {
        bail!("Идентификатор версии не совпадает с манифестом Mojang");
    }
    Ok(version)
}

#[derive(Clone)]
pub struct RuleContext {
    pub os: String,
    pub arch: String,
    pub os_version: String,
    pub features: HashMap<String, bool>,
}
impl RuleContext {
    pub fn windows() -> Self {
        Self {
            os: "windows".into(),
            arch: "x86_64".into(),
            os_version: windows_kernel_version(),
            features: HashMap::from([
                ("is_demo_user".into(), false),
                ("has_custom_resolution".into(), true),
                ("has_quick_plays_support".into(), false),
                ("is_quick_play_singleplayer".into(), false),
                ("is_quick_play_multiplayer".into(), false),
                ("is_quick_play_realms".into(), false),
            ]),
        }
    }
}
fn windows_kernel_version() -> String {
    sysinfo::System::kernel_version().unwrap_or_else(|| "10.0".into())
}
fn rule_matches(rule: &Value, ctx: &RuleContext) -> bool {
    if let Some(os) = rule.get("os") {
        if os["name"].as_str().is_some_and(|name| name != ctx.os) {
            return false;
        }
        if let Some(arch) = os["arch"].as_str() {
            if !regex::Regex::new(&format!("^(?:{arch})$"))
                .map(|r| r.is_match(&ctx.arch))
                .unwrap_or(false)
            {
                return false;
            }
        }
        if let Some(version) = os["version"].as_str() {
            if !regex::Regex::new(version)
                .map(|r| r.is_match(&ctx.os_version))
                .unwrap_or(false)
            {
                return false;
            }
        }
    }
    if let Some(features) = rule.get("features").and_then(Value::as_object) {
        for (name, required) in features {
            if ctx.features.get(name).copied().unwrap_or(false)
                != required.as_bool().unwrap_or(false)
            {
                return false;
            }
        }
    }
    true
}
pub fn allowed(rules: Option<&[Value]>, ctx: &RuleContext) -> bool {
    let Some(rules) = rules else {
        return true;
    };
    let mut allow = false;
    for rule in rules {
        if rule_matches(rule, ctx) {
            allow = rule["action"] == "allow";
        }
    }
    allow
}

pub fn maven_path(coordinate: &str) -> Result<String> {
    let (parts, extension) = coordinate.split_once('@').unwrap_or((coordinate, "jar"));
    let p: Vec<_> = parts.split(':').collect();
    if p.len() < 3 || p.len() > 4 {
        bail!("Некорректная Maven-координата");
    }
    for part in &p {
        security::safe_id(part)?;
    }
    security::safe_id(extension)?;
    let classifier = if p.len() == 4 {
        format!("-{}", p[3])
    } else {
        String::new()
    };
    let path = format!(
        "{}/{}/{}/{}-{}{}.{}",
        p[0].replace('.', "/"),
        p[1],
        p[2],
        p[1],
        p[2],
        classifier,
        extension
    );
    security::safe_relative(&path)?;
    Ok(path)
}

pub async fn library_transfer(
    state: &AppState,
    library: &Library,
    download: Option<&Download>,
    coordinate: &str,
) -> Result<Transfer> {
    let path = download
        .and_then(|d| d.path.clone())
        .unwrap_or(maven_path(coordinate)?);
    let target = security::within(&state.root().join("minecraft/libraries"), &path)?;
    let url = download
        .map(|d| d.url.clone())
        .filter(|s| !s.is_empty())
        .unwrap_or_else(|| {
            format!(
                "{}/{}",
                library
                    .url
                    .as_deref()
                    .unwrap_or("https://libraries.minecraft.net")
                    .trim_end_matches('/'),
                path
            )
        });
    let sha = if let Some(hash) = download.map(|d| d.sha1.as_str()).filter(|s| !s.is_empty()) {
        hash.to_owned()
    } else {
        // Never compare a cached file with its own hash: fetch the publisher's checksum.
        // Generated Forge artifacts get pinned checksums immediately after the verified installer runs.
        downloads::text(state, &format!("{url}.sha1"))
            .await?
            .split_whitespace()
            .next()
            .context("Нет SHA1 библиотеки")?
            .to_owned()
    };
    Ok(Transfer {
        url,
        path: target,
        hash: Hash::new("sha1", &sha)?,
        size: download.and_then(|d| d.size),
    })
}

pub fn merge(mut base: Version, child: Version) -> Version {
    // Child coordinates override base coordinates of the same group:artifact:classifier.
    fn key(lib: &Library) -> String {
        let p: Vec<_> = lib.name.split(':').collect();
        format!(
            "{}:{}:{}",
            p.first().unwrap_or(&""),
            p.get(1).unwrap_or(&""),
            p.get(3).unwrap_or(&"")
        )
    }
    let keys: Vec<_> = child.libraries.iter().map(key).collect();
    base.libraries.retain(|l| !keys.contains(&key(l)));
    base.libraries.extend(child.libraries);
    if let Some(arguments) = child.arguments {
        let existing = base
            .arguments
            .get_or_insert_with(|| serde_json::json!({ "jvm": [], "game": [] }));
        for section in ["game", "jvm"] {
            let mut combined = existing[section].as_array().cloned().unwrap_or_default();
            if let Some(extra) = arguments[section].as_array() {
                combined.extend(extra.iter().cloned());
            }
            existing[section] = Value::Array(combined);
        }
    }
    if child.minecraft_arguments.is_some() {
        base.minecraft_arguments = child.minecraft_arguments;
    }
    base.id = child.id;
    base.main_class = child.main_class;
    if child.java_version.is_some() {
        base.java_version = child.java_version;
    }
    if child.asset_index.is_some() {
        base.asset_index = child.asset_index;
    }
    base
}

pub fn extract_natives(archive: &Path, target: &Path, excludes: &[String]) -> Result<()> {
    let mut zip = zip::ZipArchive::new(std::fs::File::open(archive)?)?;
    if zip.len() > 20_000 {
        bail!("Слишком много native-файлов");
    }
    let mut total_size = 0u64;
    for i in 0..zip.len() {
        let entry = zip.by_index(i)?;
        let raw = entry.name().trim_end_matches('/').to_string();
        if raw.is_empty()
            || entry.is_dir()
            || raw.starts_with("META-INF/")
            || excludes.iter().any(|e| raw.starts_with(e))
        {
            continue;
        }
        if entry.unix_mode().is_some_and(|m| m & 0o170000 == 0o120000) {
            bail!("Ссылка в native-архиве");
        }
        let path = security::within(target, &raw)?;
        total_size = total_size
            .checked_add(entry.size())
            .context("Переполнение размера natives")?;
        if entry.size() > 256 * 1024 * 1024 || total_size > 512 * 1024 * 1024 {
            bail!("Превышен безопасный размер native-файлов");
        }
        std::fs::create_dir_all(path.parent().context("Нет папки")?)?;
        let mut output = std::fs::File::create(path)?;
        let expected = entry.size();
        let copied = std::io::copy(
            &mut std::io::Read::take(entry, 256 * 1024 * 1024 + 1),
            &mut output,
        )?;
        if copied != expected {
            bail!("Неверный размер распакованного native-файла");
        }
    }
    Ok(())
}

pub fn classpath_separator() -> &'static str {
    if cfg!(windows) {
        ";"
    } else {
        ":"
    }
}
pub fn path_string(path: &Path) -> String {
    path.to_string_lossy().into_owned()
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;
    #[test]
    fn manifest_ipc_fields_match_frontend_contract() {
        let source = json!({ "id":"1.21.4", "type":"release", "url":"https://piston-meta.mojang.com/v.json", "sha1":"a".repeat(40), "releaseTime":"2024-12-03T00:00:00Z" });
        let parsed: VersionEntry = serde_json::from_value(source).unwrap();
        let output = serde_json::to_value(parsed).unwrap();
        assert_eq!(output["kind"], "release");
        assert_eq!(output["release_time"], "2024-12-03T00:00:00Z");
    }
    #[test]
    fn x86_rule_does_not_match_x64() {
        assert!(!allowed(
            Some(&[json!({"action":"allow","os":{"name":"windows","arch":"x86"}})]),
            &RuleContext::windows()
        ));
    }
    #[test]
    fn maven_coordinates() {
        assert_eq!(
            maven_path("org.lwjgl:lwjgl:3.3.3:natives-windows").unwrap(),
            "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-windows.jar"
        );
        assert!(maven_path("x:../y:1").is_err());
    }
    #[test]
    fn rule_order_and_features() {
        let c = RuleContext::windows();
        assert!(allowed(None, &c));
        assert!(!allowed(Some(&[]), &c));
        assert!(allowed(
            Some(&[json!({"action":"allow","os":{"name":"windows"}})]),
            &c
        ));
        assert!(!allowed(
            Some(&[
                json!({"action":"allow"}),
                json!({"action":"disallow","os":{"name":"windows"}})
            ]),
            &c
        ));
        assert!(!allowed(
            Some(&[json!({"action":"allow","features":{"is_demo_user":true}})]),
            &c
        ));
    }
}

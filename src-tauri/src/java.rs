use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};

use tauri::{AppHandle, Manager};

use crate::download::{self, FileSpec};
use crate::models::{JavaRuntime, Profile, Settings};
use crate::state::{lock, AppState};
use crate::util;

const ADOPTIUM: &str = "https://api.adoptium.net/v3";

fn exe_name() -> &'static str {
    if cfg!(windows) {
        "java.exe"
    } else {
        "java"
    }
}

/* ------------------------------------------------------------------ */
/* Detection                                                           */
/* ------------------------------------------------------------------ */

pub fn looks_like_java(path: &str) -> Option<JavaRuntime> {
    let mut candidate = PathBuf::from(path.trim().trim_matches('"'));
    if candidate.is_dir() {
        candidate = candidate.join("bin").join(exe_name());
    }
    probe(&candidate)
}

/// Runs `java -XshowSettings:properties -version` and parses the output.
fn probe(path: &Path) -> Option<JavaRuntime> {
    if !path.is_file() {
        return None;
    }
    let mut cmd = Command::new(path);
    cmd.args(["-XshowSettings:properties", "-version"]);
    cmd.stdin(Stdio::null()).stdout(Stdio::piped()).stderr(Stdio::piped());
    util::hide_console(&mut cmd);
    let output = cmd.output().ok()?;
    let mut text = String::from_utf8_lossy(&output.stderr).to_string();
    text.push_str(&String::from_utf8_lossy(&output.stdout));
    let (version, vendor, arch) = parse_properties(&text);
    let major = major_of(&version);
    if major == 0 {
        return None;
    }
    Some(JavaRuntime {
        path: path.to_string_lossy().to_string(),
        version,
        major,
        vendor,
        arch,
        source: "system".into(),
    })
}

fn parse_properties(text: &str) -> (String, String, String) {
    let mut version = String::new();
    let mut vendor = String::new();
    let mut arch = String::new();
    for line in text.lines() {
        let line = line.trim();
        if let Some(rest) = line.strip_prefix("java.version = ") {
            version = rest.trim().to_string();
        } else if let Some(rest) = line.strip_prefix("java.vendor = ") {
            vendor = rest.trim().to_string();
        } else if let Some(rest) = line.strip_prefix("os.arch = ") {
            let raw = rest.trim();
            arch = match raw {
                "amd64" | "x86_64" => "x86_64".to_string(),
                "aarch64" => "arm64".to_string(),
                other => other.to_string(),
            };
        }
    }
    if version.is_empty() {
        for line in text.lines() {
            if line.contains(" version \"") {
                if let Some(start) = line.find('"') {
                    if let Some(end) = line[start + 1..].find('"') {
                        version = line[start + 1..start + 1 + end].to_string();
                        break;
                    }
                }
            }
        }
    }
    if vendor.is_empty() {
        vendor = "Unknown".into();
    }
    if arch.is_empty() {
        arch = "x86_64".into();
    }
    (version, vendor, arch)
}

pub fn major_of(version: &str) -> u32 {
    let trimmed = version.trim();
    let without_prefix = trimmed.strip_prefix("1.").unwrap_or(trimmed);
    let head: String = without_prefix
        .chars()
        .take_while(|c| c.is_ascii_digit())
        .collect();
    head.parse::<u32>().unwrap_or(0)
}

fn scan_dir(dir: &Path, depth: u8, out: &mut Vec<PathBuf>) {
    if depth == 0 || !dir.is_dir() {
        return;
    }
    let direct = dir.join("bin").join(exe_name());
    if direct.is_file() {
        out.push(direct);
        return;
    }
    if let Ok(entries) = std::fs::read_dir(dir) {
        for entry in entries.flatten() {
            if entry.file_type().map(|t| t.is_dir()).unwrap_or(false) {
                scan_dir(&entry.path(), depth - 1, out);
            }
        }
    }
}

fn common_roots(app: &AppHandle) -> Vec<PathBuf> {
    let state = app.state::<AppState>();
    let mut roots: Vec<PathBuf> = vec![state.paths().java.clone()];

    if let Some(home) = std::env::var_os("JAVA_HOME") {
        roots.push(PathBuf::from(home));
    }
    if let Some(path) = std::env::var_os("PATH") {
        for dir in std::env::split_paths(&path) {
            let candidate = dir.join(exe_name());
            if candidate.is_file() {
                roots.push(dir);
            }
        }
    }
    #[cfg(windows)]
    {
        for var in ["ProgramFiles", "ProgramFiles(x86)", "LOCALAPPDATA"] {
            if let Some(base) = std::env::var_os(var) {
                let base = PathBuf::from(base);
                for vendor in [
                    "Java",
                    "Eclipse Adoptium",
                    "Eclipse Foundation",
                    "Microsoft",
                    "Zulu",
                    "BellSoft",
                    "Amazon Corretto",
                    "Semeru",
                    "AdoptOpenJDK",
                ] {
                    roots.push(base.join(vendor));
                }
                roots.push(base.join("Programs").join("Eclipse Adoptium"));
                roots.push(base.join("Programs").join("Microsoft").join("jdk"));
            }
        }
        if let Some(appdata) = std::env::var_os("APPDATA") {
            roots.push(PathBuf::from(appdata).join(".minecraft").join("runtime"));
        }
    }
    #[cfg(not(windows))]
    {
        roots.push(PathBuf::from("/usr/lib/jvm"));
        roots.push(PathBuf::from("/Library/Java/JavaVirtualMachines"));
    }
    roots
}

pub fn detect(app: &AppHandle) -> Vec<JavaRuntime> {
    let mut candidates: Vec<PathBuf> = Vec::new();
    for root in common_roots(app) {
        scan_dir(&root, 4, &mut candidates);
    }
    let mut seen = std::collections::HashSet::new();
    let mut found: Vec<JavaRuntime> = Vec::new();
    for path in candidates {
        let key = path.to_string_lossy().to_lowercase();
        if !seen.insert(key) {
            continue;
        }
        if let Some(runtime) = probe(&path) {
            if !found.iter().any(|r| r.major == runtime.major && r.path == runtime.path) {
                found.push(runtime);
            }
        }
    }
    found.sort_by(|a, b| a.major.cmp(&b.major));
    found
}

pub fn list(app: &AppHandle) -> Vec<JavaRuntime> {
    let state = app.state::<AppState>();
    let mut out = lock(&state.java).clone();
    out.sort_by(|a, b| a.major.cmp(&b.major));
    out
}

pub fn add(app: &AppHandle, path: &str) -> Result<JavaRuntime, String> {
    let runtime = looks_like_java(path).ok_or_else(|| "errors.javaMissing|Invalid path".to_string())?;
    let state = app.state::<AppState>();
    {
        let mut list = lock(&state.java);
        if !list.iter().any(|r| r.path == runtime.path) {
            list.push(runtime.clone());
        }
    }
    Ok(runtime)
}

/* ------------------------------------------------------------------ */
/* Adoptium download                                                   */
/* ------------------------------------------------------------------ */

async fn adoptium_package(
    app: &AppHandle,
    major: u32,
) -> Result<(String, String, Option<String>, u64), String> {
    let state = app.state::<AppState>();
    let url = format!(
        "{ADOPTIUM}/assets/latest/{major}/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse"
    );
    let value = download::get_json_value(&state.http(), &url, 2).await?;
    let item = value
        .as_array()
        .and_then(|items| items.first())
        .ok_or_else(|| format!("errors.javaMissing|No Adoptium build for Java {major}"))?;
    let package = item
        .get("binary")
        .and_then(|binary| binary.get("package"))
        .ok_or_else(|| "errors.network|Unexpected Adoptium response".to_string())?;
    let link = package
        .get("link")
        .and_then(|v| v.as_str())
        .ok_or_else(|| "errors.network|Adoptium returned no download link".to_string())?;
    let name = package
        .get("name")
        .and_then(|v| v.as_str())
        .unwrap_or("jdk.zip")
        .to_string();
    let checksum = package.get("checksum").and_then(|v| v.as_str()).map(|s| s.to_string());
    let size = package.get("size").and_then(|v| v.as_u64()).unwrap_or(0);
    Ok((link.to_string(), name, checksum, size))
}

pub async fn download(app: &AppHandle, major: u32) -> Result<JavaRuntime, String> {
    let state = app.state::<AppState>();
    let paths = state.paths();
    let (link, name, checksum, size) = adoptium_package(app, major).await?;
    let archive = paths.cache.join(&name);
    state.log("info", "launcher", format!("Downloading Java {major} from Adoptium"));
    download::download_files(
        app.clone(),
        format!("Java {major}"),
        None,
        vec![FileSpec::new(&link, archive.clone()).size(size)],
    )
    .await?;

    if let Some(expected) = checksum.filter(|s| !s.is_empty()) {
        let actual = util::sha256_file(&archive).ok_or_else(|| "Could not hash the archive".to_string())?;
        if !actual.eq_ignore_ascii_case(&expected) {
            let _ = std::fs::remove_file(&archive);
            return Err("Java archive checksum mismatch".into());
        }
    }

    let target = paths.java.join(format!("jdk-{major}"));
    if target.exists() {
        let _ = std::fs::remove_dir_all(&target);
    }
    std::fs::create_dir_all(&target).map_err(|e| e.to_string())?;
    download::extract_zip(&archive, &target)?;
    let _ = std::fs::remove_file(&archive);

    // The archive contains a top-level folder; find java.exe inside it.
    let mut candidates = Vec::new();
    scan_dir(&target, 3, &mut candidates);
    let exe = candidates
        .into_iter()
        .next()
        .ok_or_else(|| "The downloaded archive does not contain java.exe".to_string())?;
    let mut runtime = probe(&exe).ok_or_else(|| "The downloaded Java could not be started".to_string())?;
    runtime.source = "managed".into();

    {
        let mut list = lock(&state.java);
        list.retain(|r| r.path != runtime.path);
        list.push(runtime.clone());
    }
    state.log("info", "launcher", format!("Java {} installed ({})", runtime.version, runtime.path));
    Ok(runtime)
}

/* ------------------------------------------------------------------ */
/* Selection for launching                                             */
/* ------------------------------------------------------------------ */

fn compatible(runtime: &JavaRuntime, required: u32) -> bool {
    // Minecraft (and especially mod loaders) are picky: a newer major version is
    // not a drop-in replacement. Only 1.21+ tolerates newer runtimes, and 1.17
    // accepts Java 17 as well as its declared Java 16.
    if required <= 8 {
        runtime.major == 8
    } else if required >= 21 {
        runtime.major >= required
    } else if required == 16 {
        runtime.major == 16 || runtime.major == 17
    } else {
        runtime.major == required
    }
}

fn best(candidates: &[JavaRuntime], required: u32) -> Option<JavaRuntime> {
    let usable: Vec<&JavaRuntime> = candidates.iter().filter(|r| compatible(r, required)).collect();
    if usable.is_empty() {
        return None;
    }
    if let Some(exact) = usable.iter().find(|r| r.major == required) {
        return Some((**exact).clone());
    }
    let mut sorted = usable;
    sorted.sort_by_key(|r| r.major);
    sorted.first().map(|r| (**r).clone())
}

/// Returns a runtime able to run a version that needs `major`, installing one when needed.
pub async fn ensure_major(app: &AppHandle, major: u32) -> Result<JavaRuntime, String> {
    let managed = list(app);
    if let Some(runtime) = best(&managed, major) {
        return Ok(runtime);
    }
    let detected = tauri::async_runtime::spawn_blocking({
        let app = app.clone();
        move || detect(&app)
    })
    .await
    .unwrap_or_default();
    if let Some(runtime) = best(&detected, major) {
        return Ok(runtime);
    }
    download(app, major).await
}

pub async fn resolve_for_launch(
    app: &AppHandle,
    profile: &Profile,
    settings: &Settings,
    required: u32,
) -> Result<JavaRuntime, String> {
    let state = app.state::<AppState>();

    for configured in [profile.java_path.as_deref(), settings.java_path.as_deref()]
        .into_iter()
        .flatten()
    {
        if let Some(runtime) = looks_like_java(configured) {
            if compatible(&runtime, required) {
                return Ok(runtime);
            }
        }
    }

    let managed = list(app);
    if let Some(runtime) = best(&managed, required) {
        return Ok(runtime);
    }

    let detected = tauri::async_runtime::spawn_blocking({
        let app = app.clone();
        move || detect(&app)
    })
    .await
    .unwrap_or_default();
    if let Some(runtime) = best(&detected, required) {
        return Ok(runtime);
    }

    state.log(
        "warn",
        "launcher",
        format!("No Java {required} found — installing an Adoptium runtime"),
    );
    download(app, required).await
}

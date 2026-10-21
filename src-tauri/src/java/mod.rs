use crate::{
    downloads::{self, Hash, Transfer},
    security,
    state::AppState,
};
use anyhow::{bail, Context, Result};
use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::{
    path::{Path, PathBuf},
    process::Stdio,
};
use tokio::process::Command;
use tokio_util::sync::CancellationToken;

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct JavaRuntime {
    pub path: String,
    pub version: String,
    pub major: u32,
    pub architecture: String,
    pub managed: bool,
}

pub fn parse_major(version: &str) -> Result<u32> {
    let parts: Vec<_> = version.split(['.', '_', '-', '+']).collect();
    let first: u32 = parts.first().context("Пустая версия Java")?.parse()?;
    Ok(if first == 1 {
        parts.get(1).context("Некорректная версия Java")?.parse()?
    } else {
        first
    })
}

pub async fn inspect(path: &Path) -> Result<JavaRuntime> {
    security::reject_link(path)?;
    let mut command = Command::new(path);
    command
        .args(["-XshowSettings:properties", "-version"])
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .kill_on_drop(true);
    #[cfg(windows)]
    command.creation_flags(0x08000000);
    let output = tokio::time::timeout(std::time::Duration::from_secs(10), command.output())
        .await
        .context("Java не ответила за 10 секунд")??;
    if !output.status.success() {
        bail!("Java вернула ошибку. Выберите исправный java.exe");
    }
    let text = format!(
        "{}\n{}",
        String::from_utf8_lossy(&output.stderr),
        String::from_utf8_lossy(&output.stdout)
    );
    let property = |name: &str| {
        text.lines().find_map(|line| {
            line.trim()
                .strip_prefix(name)
                .and_then(|v| v.trim().strip_prefix('='))
                .map(|v| v.trim().to_string())
        })
    };
    let version = property("java.version")
        .or_else(|| {
            text.lines()
                .find(|l| l.contains("version \""))
                .and_then(|l| l.split('"').nth(1))
                .map(str::to_owned)
        })
        .context("Не удалось определить версию Java")?;
    let architecture = property("os.arch").unwrap_or_default();
    let major = parse_major(&version)?;
    Ok(JavaRuntime {
        path: path.to_string_lossy().into_owned(),
        version,
        major,
        architecture,
        managed: false,
    })
}

fn java_name() -> &'static str {
    if cfg!(windows) {
        "java.exe"
    } else {
        "java"
    }
}
fn find_java(root: &Path, depth: usize) -> Option<PathBuf> {
    if depth == 0 || security::reject_link(root).is_err() {
        return None;
    }
    let candidate = root.join("bin").join(java_name());
    if candidate.is_file() {
        return Some(candidate);
    }
    for entry in std::fs::read_dir(root).ok()?.flatten().take(100) {
        if entry.file_type().ok()?.is_dir() {
            if let Some(p) = find_java(&entry.path(), depth - 1) {
                return Some(p);
            }
        }
    }
    None
}

pub async fn discover(state: &AppState) -> Vec<JavaRuntime> {
    let mut candidates = Vec::new();
    if let Some(home) = std::env::var_os("JAVA_HOME") {
        candidates.push(PathBuf::from(home).join("bin").join(java_name()));
    }
    if let Some(path) = std::env::var_os("PATH") {
        for dir in std::env::split_paths(&path) {
            candidates.push(dir.join(java_name()));
        }
    }
    let runtimes = state.root().join("runtime");
    if let Ok(entries) = std::fs::read_dir(&runtimes) {
        for entry in entries.flatten() {
            if let Some(p) = find_java(&entry.path(), 3) {
                candidates.push(p);
            }
        }
    }
    #[cfg(windows)]
    if let Some(program_files) = std::env::var_os("ProgramFiles") {
        for vendor in ["Eclipse Adoptium", "Java", "Microsoft"] {
            if let Ok(entries) = std::fs::read_dir(PathBuf::from(&program_files).join(vendor)) {
                for entry in entries.flatten().take(32) {
                    if let Some(p) = find_java(&entry.path(), 2) {
                        candidates.push(p);
                    }
                }
            }
        }
    }
    candidates.sort();
    candidates.dedup();
    let mut result = Vec::new();
    for path in candidates.into_iter().filter(|p| p.is_file()) {
        if let Ok(mut runtime) = inspect(&path).await {
            runtime.managed = path.starts_with(&runtimes);
            result.push(runtime);
        }
    }
    result
}

pub async fn ensure(
    state: &AppState,
    required: u32,
    custom: Option<&str>,
    cancel: &CancellationToken,
) -> Result<JavaRuntime> {
    if let Some(path) = custom.filter(|p| !p.is_empty()) {
        let runtime = inspect(Path::new(path)).await?;
        compatible(&runtime, required)?;
        return Ok(runtime);
    }
    for runtime in discover(state).await {
        if compatible(&runtime, required).is_ok() {
            return Ok(runtime);
        }
    }
    install_managed(state, required, cancel).await
}

pub async fn install_managed(
    state: &AppState,
    required: u32,
    cancel: &CancellationToken,
) -> Result<JavaRuntime> {
    for runtime in discover(state).await {
        if runtime.managed && compatible(&runtime, required).is_ok() {
            return Ok(runtime);
        }
    }
    if !cfg!(windows) {
        bail!("Автоустановка Java в этой сборке предназначена для Windows x64");
    }
    if !(8..=99).contains(&required) {
        bail!("Неизвестная требуемая версия Java: {required}");
    }
    state.stage(
        "java",
        &format!("Установка Eclipse Temurin Java {required}"),
    );
    let url = format!("https://api.adoptium.net/v3/assets/latest/{required}/hotspot?architecture=x64&heap_size=normal&image_type=jre&jvm_impl=hotspot&os=windows&vendor=eclipse");
    let packages: Vec<Value> = downloads::json(state, &url).await?;
    let package = &packages
        .first()
        .context("Adoptium не нашёл Java для Windows x64")?["binary"]["package"];
    let link = package["link"]
        .as_str()
        .context("В ответе Adoptium нет ссылки")?;
    let checksum = package["checksum"]
        .as_str()
        .context("В ответе Adoptium нет SHA256")?;
    let archive = security::within(
        &state.root(),
        &format!("downloads/java-{required}-{checksum}.zip"),
    )?;
    downloads::batch(
        state,
        vec![Transfer {
            url: link.into(),
            path: archive.clone(),
            hash: Hash::new("sha256", checksum)?,
            size: package["size"].as_u64(),
        }],
        cancel,
    )
    .await?;
    let staging = security::within(
        &state.root(),
        &format!("runtime/staging-{}", uuid::Uuid::new_v4()),
    )?;
    let staging_clone = staging.clone();
    let result = tokio::task::spawn_blocking(move || {
        security::extract_zip(&archive, &staging_clone, None, false)
    })
    .await?;
    if let Err(e) = result {
        let _ = std::fs::remove_dir_all(&staging);
        return Err(e);
    }
    if cancel.is_cancelled() {
        let _ = std::fs::remove_dir_all(&staging);
        bail!("Операция отменена");
    }
    let binary = find_java(&staging, 4).context("В архиве Adoptium не найден java.exe")?;
    let checked = inspect(&binary).await?;
    compatible(&checked, required)?;
    let relative = binary.strip_prefix(&staging)?.to_owned();
    let install = security::within(
        &state.root(),
        &format!("runtime/temurin-{required}-{}", &checksum[..12]),
    )?;
    if install.exists() {
        bail!(
            "Папка runtime уже существует, но Java в ней не прошла проверку. Проверьте её вручную."
        );
    }
    std::fs::rename(&staging, &install)?;
    let mut runtime = inspect(&install.join(relative)).await?;
    runtime.managed = true;
    state.log(
        "info",
        "launcher",
        &format!(
            "Установлена Java {} x64; SHA256 архива проверен",
            runtime.version
        ),
    );
    state.notify("success", &format!("Java {required} установлена"));
    Ok(runtime)
}

fn compatible(runtime: &JavaRuntime, required: u32) -> Result<()> {
    if runtime.major != required {
        bail!(
            "Требуется Java {required}, выбрана Java {}. Включите автоматический выбор Java.",
            runtime.major
        );
    }
    if !["amd64", "x86_64"].contains(&runtime.architecture.as_str()) {
        bail!("Требуется Java x64 (найдена {})", runtime.architecture);
    }
    Ok(())
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn parses_java_versions() {
        for (v, m) in [
            ("1.8.0_402", 8),
            ("17.0.12+7", 17),
            ("21.0.5", 21),
            ("25-ea", 25),
        ] {
            assert_eq!(parse_major(v).unwrap(), m);
        }
        assert!(parse_major("unknown").is_err());
    }
}

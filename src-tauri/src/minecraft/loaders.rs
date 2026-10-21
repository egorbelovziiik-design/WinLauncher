use crate::{
    downloads::{self, Hash, Transfer},
    java,
    minecraft::metadata::{self, Version},
    models::{Loader, Profile},
    security,
    state::AppState,
};
use anyhow::{bail, Context, Result};
use serde::Serialize;
use serde_json::Value;
use std::{fs, path::Path, process::Stdio};
use tokio::{
    io::{AsyncBufReadExt, BufReader},
};
use tokio_util::sync::CancellationToken;

#[derive(Serialize, Clone)]
pub struct LoaderRelease {
    pub id: String,
    pub stable: bool,
    pub java_major: Option<u32>,
}

pub async fn releases(
    state: &AppState,
    minecraft: &str,
    loader: &Loader,
) -> Result<Vec<LoaderRelease>> {
    security::safe_id(minecraft)?;
    match loader {
        Loader::Vanilla => Ok(vec![]),
        Loader::Fabric | Loader::Quilt => {
            let host = if *loader == Loader::Fabric {
                "https://meta.fabricmc.net/v2"
            } else {
                "https://meta.quiltmc.org/v3"
            };
            let data: Vec<Value> =
                downloads::json(state, &format!("{host}/versions/loader/{minecraft}")).await?;
            let mut versions: Vec<_> = data
                .iter()
                .filter_map(|v| {
                    v["loader"]["version"].as_str().map(|id| LoaderRelease {
                        id: id.into(),
                        java_major: v["launcherMeta"]["min_java_version"].as_u64().and_then(|n| u32::try_from(n).ok()),
                        stable: v["loader"]["stable"]
                            .as_bool()
                            .unwrap_or(!id.contains("beta")),
                    })
                })
                .collect();
            versions.sort_by_key(|v| !v.stable);
            Ok(versions)
        }
        Loader::Forge | Loader::Neoforge => {
            let (url, prefix) = if *loader == Loader::Forge {
                (
                    "https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml",
                    format!("{minecraft}-"),
                )
            } else {
                let numbers: Vec<_> = minecraft.split('.').collect();
                let prefix = if numbers.first() == Some(&"1") && numbers.len() >= 2 {
                    format!("{}.{}.", numbers[1], numbers.get(2).unwrap_or(&"0"))
                } else if numbers.len() == 2 {
                    format!("{minecraft}.")
                } else {
                    bail!("NeoForge не поддерживает эту версию Minecraft");
                };
                ("https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml", prefix)
            };
            let xml = downloads::text(state, url).await?;
            let mut reader = quick_xml::Reader::from_str(&xml);
            reader.config_mut().trim_text(true);
            let mut in_version = false;
            let mut ids = Vec::new();
            loop {
                match reader.read_event()? {
                    quick_xml::events::Event::Start(e) if e.name().as_ref() == b"version" => {
                        in_version = true
                    }
                    quick_xml::events::Event::End(_) => in_version = false,
                    quick_xml::events::Event::Text(t) if in_version => {
                        let text = String::from_utf8_lossy(t.as_ref()).to_string();
                        if text.starts_with(&prefix) {
                            ids.push(text);
                        }
                    }
                    quick_xml::events::Event::Eof => break,
                    _ => {}
                }
            }
            fn numbers(s: &str) -> Vec<u32> {
                s.split(|c: char| !c.is_ascii_digit())
                    .filter_map(|v| v.parse().ok())
                    .collect()
            }
            ids.sort_by_key(|b| std::cmp::Reverse(numbers(b)));
            let mut result: Vec<_> = ids
                .into_iter()
                .map(|id| LoaderRelease {
                    stable: !id.contains("beta") && !id.contains("alpha"),
                    java_major: None,
                    id,
                })
                .collect();
            result.sort_by_key(|r| !r.stable);
            Ok(result)
        }
    }
}

pub async fn install(
    state: &AppState,
    profile: &mut Profile,
    base: Version,
    runtime: &java::JavaRuntime,
    cancel: &CancellationToken,
) -> Result<Version> {
    if profile.loader == Loader::Vanilla {
        return Ok(base);
    }
    state.stage("loader", &format!("Установка {}", profile.loader.as_str()));
    let choices = releases(state, &profile.game_version, &profile.loader).await?;
    let requested = if let Some(v) = &profile.loader_version {
        // mrpack Forge dependencies sometimes omit the Minecraft prefix.
        if profile.loader == Loader::Forge && !v.starts_with(&format!("{}-", profile.game_version))
        {
            format!("{}-{v}", profile.game_version)
        } else {
            v.clone()
        }
    } else {
        choices
            .first()
            .context("Для этой версии Minecraft нет совместимого загрузчика")?
            .id
            .clone()
    };
    if !choices.iter().any(|r| r.id == requested) {
        bail!("Выбранная версия загрузчика отсутствует в официальных метаданных");
    }
    security::safe_id(&requested)?;
    let child: Version = match profile.loader {
        Loader::Fabric | Loader::Quilt => {
            let host = if profile.loader == Loader::Fabric {
                "https://meta.fabricmc.net/v2"
            } else {
                "https://meta.quiltmc.org/v3"
            };
            downloads::json(
                state,
                &format!(
                    "{host}/versions/loader/{}/{requested}/profile/json",
                    profile.game_version
                ),
            )
            .await?
        }
        Loader::Forge | Loader::Neoforge => {
            install_forge(state, profile, &requested, runtime, cancel).await?
        }
        Loader::Vanilla => return Ok(base),
    };
    if child
        .inherits_from
        .as_deref()
        .is_some_and(|id| id != profile.game_version)
    {
        bail!("Загрузчик предназначен для другой версии Minecraft");
    }
    let loader_java = choices.iter().find(|r| r.id == requested).and_then(|r| r.java_major);
    profile.loader_version = Some(requested);
    let mut result = metadata::merge(base, child)?;
    if let Some(major) = loader_java {
        if result.java_version.as_ref().is_none_or(|j| major > j.major_version) { result.java_version = Some(metadata::JavaVersion { major_version: major }); }
    }
    let path = state
        .instance(&profile.id)?
        .join(".winlauncher-version.json");
    fs::write(path, serde_json::to_vec_pretty(&result)?)?;
    Ok(result)
}

async fn install_forge(
    state: &AppState,
    profile: &Profile,
    version: &str,
    runtime: &java::JavaRuntime,
    cancel: &CancellationToken,
) -> Result<Version> {
    let (prefix, name) = if profile.loader == Loader::Forge {
        (
            "https://maven.minecraftforge.net/net/minecraftforge/forge",
            "forge",
        )
    } else {
        (
            "https://maven.neoforged.net/releases/net/neoforged/neoforge",
            "neoforge",
        )
    };
    let url = format!("{prefix}/{version}/{name}-{version}-installer.jar");
    let checksum = downloads::text(state, &format!("{url}.sha1"))
        .await?
        .split_whitespace()
        .next()
        .context("У установщика нет SHA1")?
        .to_string();
    let archive = security::within(
        &state.root(),
        &format!("downloads/{name}-{version}-installer.jar"),
    )?;
    downloads::batch(
        state,
        vec![Transfer {
            url,
            path: archive.clone(),
            hash: Hash::new("sha1", &checksum)?,
            size: None,
        }],
        cancel,
    )
    .await?;
    let expected_version = installer_version(&archive)?;
    security::safe_id(&expected_version)?;
    let root = state.root().join("minecraft");
    let output = security::within(
        &root,
        &format!("versions/{expected_version}/{expected_version}.json"),
    )?;
    let verified_output = security::within(
        &root,
        &format!("versions/{expected_version}/winlauncher-verified-loader.json"),
    )?;
    if let Ok(bytes) = fs::read(&verified_output) {
        if let Ok(cached) = serde_json::from_slice::<Version>(&bytes) {
            let mut valid = cached.id == expected_version;
            for library in &cached.libraries {
                if let Some(artifact) = library
                    .downloads
                    .as_ref()
                    .and_then(|d| d.artifact.as_ref())
                    .filter(|a| a.url.is_empty())
                {
                    let relative = artifact
                        .path
                        .clone()
                        .unwrap_or(metadata::maven_path(&library.name)?);
                    let path = security::within(&root.join("libraries"), &relative)?;
                    valid &= downloads::verify(&path, &Hash::new("sha1", &artifact.sha1)?)?;
                }
            }
            if valid {
                return Ok(cached);
            }
        }
    }
    {
        let launcher_profiles = root.join("launcher_profiles.json");
        if !launcher_profiles.exists() {
            fs::write(
                &launcher_profiles,
                b"{\"profiles\":{},\"selectedProfile\":\"\"}",
            )?;
        }
        let mut command = java::command(Path::new(&runtime.path));
        command
            .arg("-Djava.awt.headless=true")
            .arg("-jar")
            .arg(&archive)
            .arg("--installClient")
            .arg(&root)
            .current_dir(&root)
            .stdout(Stdio::piped())
            .stderr(Stdio::piped())
            .kill_on_drop(true);
        #[cfg(windows)]
        command.creation_flags(0x08000000);
        let mut child = command
            .spawn()
            .context("Не удалось запустить официальный установщик загрузчика")?;
        if let Some(stdout) = child.stdout.take() {
            let state = state.clone();
            tokio::spawn(async move {
                let mut lines = BufReader::new(stdout).lines();
                while let Ok(Some(line)) = lines.next_line().await {
                    state.log("info", "installer", &line);
                }
            });
        }
        if let Some(stderr) = child.stderr.take() {
            let state = state.clone();
            tokio::spawn(async move {
                let mut lines = BufReader::new(stderr).lines();
                while let Ok(Some(line)) = lines.next_line().await {
                    state.log("warn", "installer", &line);
                }
            });
        }
        let status = tokio::select! {
            _ = cancel.cancelled() => { child.kill().await?; bail!("Установка загрузчика отменена"); }
            r = tokio::time::timeout(std::time::Duration::from_secs(1800), child.wait()) => r.context("Установщик не завершился за 30 минут")??,
        };
        if !status.success() {
            let tail = state
                .logs
                .lock()
                .unwrap_or_else(|e| e.into_inner())
                .iter()
                .rev()
                .filter(|line| line.source == "installer")
                .take(8)
                .map(|line| line.message.clone())
                .collect::<Vec<_>>()
                .into_iter()
                .rev()
                .collect::<Vec<_>>()
                .join("\n");
            bail!("Официальный установщик {} завершился с кодом {:?}. Старые GUI-only установщики могут не поддерживать headless-режим.\n{}", profile.loader.as_str(), status.code(), tail);
        }
    }
    let mut child: Version =
        serde_json::from_slice(&fs::read(&output).context("Установщик не создал JSON версии")?)?;
    if child.id != expected_version {
        bail!("Версия результата установщика не совпадает");
    }
    // Pin processor output integrity at the trust boundary: only AFTER a verified
    // official installer successfully produced the artifacts. Future launches
    // compare bytes with these saved hashes and rerun the installer if changed.
    for library in &mut child.libraries {
        if let Some(artifact) = library
            .downloads
            .as_mut()
            .and_then(|d| d.artifact.as_mut())
            .filter(|a| a.url.is_empty())
        {
            let relative = artifact
                .path
                .clone()
                .unwrap_or(metadata::maven_path(&library.name)?);
            let path = security::within(&root.join("libraries"), &relative)?;
            if artifact.sha1.is_empty() {
                artifact.sha1 = downloads::hash_file(&path, "sha1")?;
            }
            if !downloads::verify(&path, &Hash::new("sha1", &artifact.sha1)?)? {
                bail!("Результат процессора Forge не прошёл проверку целостности");
            }
            artifact.size = Some(fs::metadata(path)?.len());
        }
    }
    let temp = verified_output.with_extension("json.part");
    fs::write(&temp, serde_json::to_vec_pretty(&child)?)?;
    downloads::atomic_replace(&temp, &verified_output)?;
    Ok(child)
}

fn installer_version(archive: &Path) -> Result<String> {
    use std::io::Read;
    let mut zip = zip::ZipArchive::new(fs::File::open(archive)?)?;
    let profile: Value = {
        let file = zip.by_name("install_profile.json")?;
        if file.size() > 4 * 1024 * 1024 {
            bail!("Слишком большой install_profile.json");
        }
        let mut body = String::new();
        file.take(4 * 1024 * 1024).read_to_string(&mut body)?;
        serde_json::from_str(&body)?
    };
    if let Some(id) = profile["version"]
        .as_str()
        .or_else(|| profile["versionInfo"]["id"].as_str())
    {
        return Ok(id.into());
    }
    if let Some(json_path) = profile["json"].as_str() {
        let mut body = String::new();
        zip.by_name(json_path.trim_start_matches('/'))?
            .take(4 * 1024 * 1024)
            .read_to_string(&mut body)?;
        let value: Value = serde_json::from_str(&body)?;
        return Ok(value["id"].as_str().context("Нет id Forge")?.into());
    }
    bail!("Неизвестный формат официального установщика Forge/NeoForge")
}

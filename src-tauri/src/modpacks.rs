use crate::{
    downloads::{self, Hash, Transfer},
    models::{InstalledFile, Loader, Profile},
    profiles, security,
    state::AppState,
};
use anyhow::{bail, Context, Result};
use serde_json::{json, Value};
use std::{
    collections::HashSet,
    fs,
    io::{Read, Write},
    path::Path,
};
use tokio_util::sync::CancellationToken;

fn zip_json(path: &Path, name: &str) -> Result<Value> {
    let mut zip = zip::ZipArchive::new(fs::File::open(path)?)?;
    let file = zip.by_name(name)?;
    if file.size() > 16 * 1024 * 1024 {
        bail!("Манифест модпака слишком большой");
    }
    let mut buf = Vec::new();
    file.take(16 * 1024 * 1024 + 1).read_to_end(&mut buf)?;
    Ok(serde_json::from_slice(&buf)?)
}

pub async fn import(state: &AppState, path: &Path) -> Result<Profile> {
    let guard = state.begin_job("modpack")?;
    import_inner(state, path, &guard.token).await
}
pub async fn import_inner(
    state: &AppState,
    path: &Path,
    cancel: &CancellationToken,
) -> Result<Profile> {
    security::reject_link(path)?;
    let is_mrpack = zip_json(path, "modrinth.index.json").is_ok();
    let index = zip_json(path, if is_mrpack { "modrinth.index.json" } else { "manifest.json" }).context("Поддерживаются .mrpack и CurseForge ZIP с manifest.json; произвольный ZIP не является модпаком")?;
    let name = index["name"]
        .as_str()
        .unwrap_or("Импортированная сборка")
        .chars()
        .take(64)
        .collect::<String>();
    let (version, loader, loader_version) = if is_mrpack {
        if index["formatVersion"] != 1 || index["game"] != "minecraft" {
            bail!("Неподдерживаемый формат mrpack");
        }
        let deps = index["dependencies"]
            .as_object()
            .context("Нет зависимостей модпака")?;
        let game = deps
            .get("minecraft")
            .and_then(Value::as_str)
            .context("Нет версии Minecraft")?
            .to_string();
        let mut found = Vec::new();
        for (key, loader) in [
            ("fabric-loader", Loader::Fabric),
            ("forge", Loader::Forge),
            ("neoforge", Loader::Neoforge),
            ("quilt-loader", Loader::Quilt),
        ] {
            if let Some(v) = deps.get(key) {
                found.push((
                    loader,
                    v.as_str()
                        .context("Некорректная версия загрузчика")?
                        .to_string(),
                ));
            }
        }
        if found.len() > 1 {
            bail!("Сборка указывает несколько несовместимых загрузчиков");
        }
        if let Some((loader, v)) = found.pop() {
            (game, loader, Some(v))
        } else {
            (game, Loader::Vanilla, None)
        }
    } else {
        if index["manifestType"] != "minecraftModpack" || index["manifestVersion"] != 1 {
            bail!("Неизвестный формат CurseForge-модпака");
        }
        let game = index["minecraft"]["version"]
            .as_str()
            .context("Нет версии Minecraft")?
            .to_string();
        let entries = index["minecraft"]["modLoaders"]
            .as_array()
            .context("Нет загрузчика модпака")?;
        let raw = entries
            .iter()
            .find(|l| l["primary"] == true)
            .or_else(|| entries.first())
            .and_then(|l| l["id"].as_str());
        let (loader, v) = if let Some(raw) = raw {
            let (kind, v) = raw.split_once('-').context("Некорректный загрузчик")?;
            (
                match kind {
                    "forge" => Loader::Forge,
                    "neoforge" => Loader::Neoforge,
                    "fabric" => Loader::Fabric,
                    "quilt" => Loader::Quilt,
                    _ => bail!("Загрузчик сборки не поддерживается"),
                },
                Some(v.to_string()),
            )
        } else {
            (Loader::Vanilla, None)
        };
        (game, loader, v)
    };
    // Reject unknown game versions before writing any files.
    if !crate::minecraft::metadata::manifest(state)
        .await?
        .versions
        .iter()
        .any(|v| v.id == version)
    {
        bail!("Minecraft из модпака отсутствует в официальном манифесте");
    }
    let mut profile = profiles::new(name, version, loader);
    profile.loader_version = loader_version;
    profile.modpack = Some(if is_mrpack { "mrpack" } else { "curseforge" }.into());
    profiles::validate(&profile)?;
    let root = state.instance(&profile.id)?;
    fs::create_dir_all(&root)?;
    let result: Result<Vec<InstalledFile>> = async {
        let mut installed = Vec::new();
        if is_mrpack {
            let files = index["files"]
                .as_array()
                .context("Нет индекса файлов модпака")?;
            if files.len() > 20_000 {
                bail!("Слишком много файлов в модпаке");
            }
            let mut seen = HashSet::new();
            let mut transfers = Vec::new();
            for file in files {
                if file["env"]["client"] == "unsupported" || file["env"]["client"] == "optional" {
                    continue;
                }
                let relative = file["path"].as_str().context("Нет пути файла сборки")?;
                let target = security::within(&root, relative)?;
                if !seen.insert(relative.to_ascii_lowercase()) {
                    bail!("Повторяющийся путь в модпаке");
                }
                if relative.starts_with("saves/")
                    || relative.starts_with(".natives/")
                    || relative.starts_with(".winlauncher")
                {
                    bail!("Модпак не может изменять миры и служебные файлы");
                }
                let url = file["downloads"]
                    .as_array()
                    .and_then(|a| {
                        a.iter()
                            .filter_map(Value::as_str)
                            .find(|u| security::check_url(u).is_ok())
                    })
                    .context("У файла сборки нет разрешённого HTTPS-источника")?;
                let (algorithm, checksum) = if let Some(h) = file["hashes"]["sha512"].as_str() {
                    ("sha512", h)
                } else {
                    (
                        "sha1",
                        file["hashes"]["sha1"]
                            .as_str()
                            .context("Нет хеша файла сборки")?,
                    )
                };
                let hash = Hash::new(algorithm, checksum)?;
                let size = file["fileSize"].as_u64();
                transfers.push(Transfer {
                    url: url.into(),
                    path: target,
                    hash,
                    size,
                });
                let parts: Vec<_> = relative.split('/').collect();
                if parts.len() == 2 && ["mods", "resourcepacks", "shaderpacks"].contains(&parts[0])
                {
                    let parsed = url::Url::parse(url)?;
                    let segments: Vec<_> = parsed
                        .path_segments()
                        .map(|s| s.collect())
                        .unwrap_or_default();
                    let known = parsed.host_str() == Some("cdn.modrinth.com")
                        && segments.len() >= 5
                        && segments[0] == "data"
                        && segments[2] == "versions";
                    installed.push(InstalledFile {
                        profile_id: profile.id.clone(),
                        provider: if known { "modrinth" } else { "mrpack" }.into(),
                        project_id: if known {
                            segments[1].to_string()
                        } else {
                            checksum[..20].into()
                        },
                        version_id: if known {
                            segments[3].to_string()
                        } else {
                            index["versionId"].as_str().unwrap_or("imported").into()
                        },
                        name: parts[1].trim_end_matches(".jar").to_string(),
                        filename: parts[1].into(),
                        kind: match parts[0] {
                            "mods" => "mod",
                            "resourcepacks" => "resourcepack",
                            _ => "shader",
                        }
                        .into(),
                        sha: checksum.into(),
                        hash_algorithm: algorithm.into(),
                        enabled: true,
                        download_url: url.into(),
                        size: size.unwrap_or(0),
                    });
                }
            }
            state.stage("modpack", "Загрузка и проверка файлов .mrpack");
            downloads::batch(state, transfers, cancel).await?;
            security::extract_zip(path, &root, Some("overrides/"), false)?;
            security::extract_zip(path, &root, Some("client-overrides/"), false)?;
        } else {
            let entries = index["files"]
                .as_array()
                .context("Нет списка файлов CurseForge")?;
            if entries.len() > 5000 {
                bail!("Слишком много файлов CurseForge");
            }
            let mut pins = std::collections::HashMap::new();
            for entry in entries.iter().filter(|e| e["required"] != false) {
                let project = entry["projectID"].as_u64().context("Нет projectID")?;
                let version = entry["fileID"].as_u64().context("Нет fileID")?;
                if pins
                    .insert(project, version)
                    .is_some_and(|old| old != version)
                {
                    bail!("В модпаке закреплены разные файлы одного проекта CurseForge");
                }
            }
            for entry in entries {
                if entry["required"] == false {
                    continue;
                }
                let project_id = entry["projectID"]
                    .as_u64()
                    .context("Нет projectID")?
                    .to_string();
                let file_id = entry["fileID"].as_u64().context("Нет fileID")?.to_string();
                let plans = crate::curseforge::resolve_pinned(
                    state,
                    &profile,
                    &project_id,
                    Some(&file_id),
                    &pins,
                    cancel,
                )
                .await?;
                for file in plans {
                    if let Some(old) = installed.iter().find(|f: &&InstalledFile| {
                        f.project_id == file.project_id && f.provider == file.provider
                    }) {
                        if old.version_id != file.version_id {
                            bail!("Конфликт версий зависимости в CurseForge-модпаке");
                        }
                    } else {
                        installed.push(file);
                    }
                }
            }
            crate::content::apply(state, &profile, &installed, cancel).await?;
            let overrides = index["overrides"].as_str().unwrap_or("overrides");
            security::safe_relative(overrides)?;
            security::extract_zip(path, &root, Some(&format!("{overrides}/")), false)?;
        }
        if cancel.is_cancelled() {
            bail!("Импорт отменён");
        }
        Ok(installed)
    }
    .await;
    let files = match result {
        Ok(files) => files,
        Err(error) => {
            // Only the NEW isolated instance is removed; never an existing user's world.
            let _ = fs::remove_dir_all(&root);
            for f in state
                .list::<InstalledFile>("installed")?
                .into_iter()
                .filter(|f| f.profile_id == profile.id)
            {
                let _ = state.remove("installed", &profiles::file_id(&f));
            }
            return Err(error);
        }
    };
    let mut conn = state.db.lock().unwrap_or_else(|e| e.into_inner());
    let tx = conn.transaction()?;
    tx.execute(
        "INSERT INTO profiles(id,payload) VALUES(?1,?2)",
        rusqlite::params![profile.id, serde_json::to_string(&profile)?],
    )?;
    for file in files {
        tx.execute("INSERT INTO installed(id,payload) VALUES(?1,?2) ON CONFLICT(id) DO UPDATE SET payload=excluded.payload", rusqlite::params![profiles::file_id(&file), serde_json::to_string(&file)?])?;
    }
    tx.commit()?;
    drop(conn);
    state
        .settings
        .write()
        .unwrap_or_else(|e| e.into_inner())
        .selected_profile = Some(profile.id.clone());
    state.persist_settings()?;
    state.notify(
        "success",
        "Модпак импортирован в отдельный профиль. Minecraft и Java будут установлены при запуске.",
    );
    state.changed();
    Ok(profile)
}

pub fn export(state: &AppState, profile_id: &str, destination: &Path) -> Result<()> {
    let _operation = state.begin_job("export")?;
    if destination.extension().and_then(|s| s.to_str()) != Some("mrpack") || destination.exists() {
        bail!("Выберите новое имя файла с расширением .mrpack");
    }
    for parent in destination.ancestors() {
        security::reject_link(parent)?;
    }
    let profile: Profile = state.get("profiles", profile_id)?;
    let files: Vec<InstalledFile> = state
        .list::<InstalledFile>("installed")?
        .into_iter()
        .filter(|f| f.profile_id == profile_id && f.enabled)
        .collect();
    if files
        .iter()
        .any(|f| !["modrinth", "mrpack"].contains(&f.provider.as_str()))
    {
        bail!("Этот экспорт поддерживает только Modrinth/.mrpack-файлы с разрешёнными URL. Файлы CurseForge не перераспространяются.");
    }
    let mut entries = Vec::new();
    for f in files {
        let relative = format!("{}/{}", profiles::content_folder(&f.kind)?, f.filename);
        let path = security::within(&state.instance(profile_id)?, &relative)?;
        if !downloads::verify(&path, &Hash::new(&f.hash_algorithm, &f.sha)?)? {
            bail!(
                "Файл {} изменён: экспорт ссылки с неверным хешем запрещён",
                f.filename
            );
        }
        entries.push(json!({ "path": relative, "hashes": { "sha1": downloads::hash_file(&path, "sha1")?, "sha512": downloads::hash_file(&path, "sha512")? }, "env": { "client": "required", "server": "optional" }, "downloads": [f.download_url], "fileSize": fs::metadata(path)?.len() }));
    }
    let mut dependencies = json!({ "minecraft": profile.game_version });
    if profile.loader != Loader::Vanilla {
        let key = match profile.loader {
            Loader::Fabric => "fabric-loader",
            Loader::Quilt => "quilt-loader",
            Loader::Forge => "forge",
            Loader::Neoforge => "neoforge",
            Loader::Vanilla => "minecraft",
        };
        let version = profile
            .loader_version
            .context("Сначала установите загрузчик, чтобы закрепить его версию")?;
        dependencies[key] = Value::String(if profile.loader == Loader::Forge {
            version
                .strip_prefix(&format!("{}-", profile.game_version))
                .unwrap_or(&version)
                .to_string()
        } else {
            version
        });
    }
    let index = json!({ "formatVersion": 1, "game": "minecraft", "versionId": "1.0.0", "name": profile.name, "summary": "Exported with WinLauncher. Worlds, accounts and private configurations are intentionally excluded.", "dependencies": dependencies, "files": entries });
    let output = fs::OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(destination)?;
    let mut zip = zip::ZipWriter::new(output);
    zip.start_file(
        "modrinth.index.json",
        zip::write::SimpleFileOptions::default()
            .compression_method(zip::CompressionMethod::Deflated),
    )?;
    zip.write_all(&serde_json::to_vec_pretty(&index)?)?;
    zip.finish()?;
    Ok(())
}

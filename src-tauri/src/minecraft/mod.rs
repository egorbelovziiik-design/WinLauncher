pub mod arguments;
pub mod loaders;
pub mod metadata;
use crate::{
    accounts,
    downloads::{self, Hash, Transfer},
    java,
    models::{AccountKind, Profile, RunningGame, Session},
    security,
    state::AppState,
};
use anyhow::{bail, Context, Result};
use metadata::{RuleContext, Version};
use serde::Deserialize;
use serde_json::Value;
use std::{
    collections::{HashMap, HashSet},
    fs,
    path::PathBuf,
    process::Stdio,
};
use tokio::{
    io::{AsyncBufReadExt, BufReader},
    process::Command,
};
use tokio_util::sync::CancellationToken;

struct Installed {
    version: Version,
    java: java::JavaRuntime,
    classpath: Vec<PathBuf>,
    natives: PathBuf,
    assets: PathBuf,
    asset_index: String,
    logging_arg: Option<String>,
    client: PathBuf,
}
#[derive(Deserialize)]
struct Assets {
    objects: HashMap<String, Asset>,
    #[serde(default, rename = "virtual")]
    is_virtual: bool,
    #[serde(default)]
    map_to_resources: bool,
}
#[derive(Deserialize)]
struct Asset {
    hash: String,
    size: u64,
}

pub async fn install_only(state: &AppState, profile_id: &str) -> Result<()> {
    let guard = state.begin_job("metadata")?;
    let mut profile: Profile = state.get("profiles", profile_id)?;
    let installed = install(state, &mut profile, &guard.token).await?;
    let _ = fs::remove_dir_all(&installed.natives);
    state.put("profiles", &profile.id, &profile)?;
    state.notify(
        "success",
        "Minecraft установлен. Хеши игровых файлов проверены.",
    );
    Ok(())
}

async fn install(
    state: &AppState,
    profile: &mut Profile,
    cancel: &CancellationToken,
) -> Result<Installed> {
    crate::profiles::validate(profile)?;
    fs::create_dir_all(state.instance(&profile.id)?)?;
    state.stage("metadata", "Официальный манифест Mojang");
    let base = metadata::version(state, &profile.game_version, cancel).await?;
    let major = base
        .java_version
        .as_ref()
        .map(|j| j.major_version)
        .unwrap_or(8);
    let runtime = java::ensure(state, major, profile.java_path.as_deref(), cancel).await?;
    let client_download = base
        .downloads
        .get("client")
        .context("У этой версии нет Java-клиента")?;
    let client_path = security::within(
        &state.root(),
        &format!("minecraft/versions/{0}/{0}.jar", profile.game_version),
    )?;
    state.stage("client", "Загрузка Minecraft Java Edition");
    downloads::batch(
        state,
        vec![Transfer {
            url: client_download.url.clone(),
            path: client_path.clone(),
            hash: Hash::new("sha1", &client_download.sha1)?,
            size: client_download.size,
        }],
        cancel,
    )
    .await?;
    let natives = security::within(
        &state.root(),
        &format!("profiles/{}/.natives/{}", profile.id, uuid::Uuid::new_v4()),
    )?;
    fs::create_dir_all(&natives)?;
    state.stage("libraries", "Проверка библиотек и native-файлов");
    let mut classpath = libraries(state, &base, &natives, cancel).await?;
    let (assets, asset_index) = assets(state, profile, &base, cancel).await?;
    let logging_arg = logging(state, &base, cancel).await?;
    let modded = profile.loader != crate::models::Loader::Vanilla;
    let version = loaders::install(state, profile, base, &runtime, cancel).await?;
    if modded {
        state.stage("libraries", "Проверка библиотек загрузчика");
        classpath = libraries(state, &version, &natives, cancel).await?;
    }
    // A loader can request a newer Java than its base game.
    let requested = version
        .java_version
        .as_ref()
        .map(|j| j.major_version)
        .unwrap_or(major);
    let runtime = if requested != major {
        java::ensure(state, requested, profile.java_path.as_deref(), cancel).await?
    } else {
        runtime
    };
    if cancel.is_cancelled() {
        bail!("Операция отменена");
    }
    profile.installed = true;
    Ok(Installed {
        version,
        java: runtime,
        classpath,
        natives,
        assets,
        asset_index,
        logging_arg,
        client: client_path,
    })
}

async fn libraries(
    state: &AppState,
    version: &Version,
    natives: &std::path::Path,
    cancel: &CancellationToken,
) -> Result<Vec<PathBuf>> {
    let ctx = RuleContext::windows();
    let mut transfers = Vec::new();
    let mut classpath = Vec::new();
    let mut archives = Vec::new();
    let mut paths = HashSet::new();
    for library in &version.libraries {
        if !metadata::allowed(library.rules.as_deref(), &ctx) {
            continue;
        }
        let artifact = library.downloads.as_ref().and_then(|d| d.artifact.as_ref());
        // A native-only legacy library has no classpath artifact.
        if artifact.is_some() || library.downloads.is_none() {
            let transfer =
                metadata::library_transfer(state, library, artifact, &library.name).await?;
            if paths.insert(transfer.path.clone()) {
                classpath.push(transfer.path.clone());
                transfers.push(transfer);
            }
        }
        if let Some(classifier) = library.natives.as_ref().and_then(|n| n.get("windows")) {
            let classifier = classifier.replace("${arch}", "64");
            let native = library
                .downloads
                .as_ref()
                .and_then(|d| d.classifiers.get(&classifier))
                .context("У библиотеки нет Windows x64 natives")?;
            let transfer = metadata::library_transfer(
                state,
                library,
                Some(native),
                &format!("{}:{classifier}", library.name),
            )
            .await?;
            let excludes: Vec<String> = library
                .extract
                .as_ref()
                .and_then(|v| v["exclude"].as_array())
                .map(|a| {
                    a.iter()
                        .filter_map(|v| v.as_str().map(str::to_owned))
                        .collect()
                })
                .unwrap_or_default();
            archives.push((transfer.path.clone(), excludes));
            if paths.insert(transfer.path.clone()) {
                transfers.push(transfer);
            }
        }
    }
    downloads::batch(state, transfers, cancel).await?;
    let native_dir = natives.to_owned();
    tokio::task::spawn_blocking(move || -> Result<()> {
        for (archive, excludes) in archives {
            metadata::extract_natives(&archive, &native_dir, &excludes)?;
        }
        Ok(())
    })
    .await??;
    Ok(classpath)
}

async fn assets(
    state: &AppState,
    profile: &Profile,
    version: &Version,
    cancel: &CancellationToken,
) -> Result<(PathBuf, String)> {
    let assets_root = state.root().join("minecraft/assets");
    fs::create_dir_all(&assets_root)?;
    let Some(index) = &version.asset_index else {
        return Ok((
            assets_root,
            version.assets.clone().unwrap_or_else(|| "legacy".into()),
        ));
    };
    security::safe_id(&index.id)?;
    state.stage("assets", "Получение индекса ресурсов");
    let path = security::within(&assets_root, &format!("indexes/{}.json", index.id))?;
    downloads::download(
        state,
        &Transfer {
            url: index.url.clone(),
            path: path.clone(),
            hash: Hash::new("sha1", &index.sha1)?,
            size: index.size,
        },
        cancel,
    )
    .await?;
    let index_data: Assets = serde_json::from_slice(&fs::read(&path)?)?;
    if index_data.objects.len() > 200_000 {
        bail!("Индекс ресурсов слишком велик");
    }
    let mut transfers = Vec::new();
    let mut hashes = HashSet::new();
    for asset in index_data.objects.values() {
        let hash = Hash::new("sha1", &asset.hash)?;
        if hashes.insert(asset.hash.clone()) {
            let relative = format!("{}/{}", &hash.value[..2], hash.value);
            transfers.push(Transfer {
                url: format!("https://resources.download.minecraft.net/{relative}"),
                path: security::within(&assets_root.join("objects"), &relative)?,
                hash,
                size: Some(asset.size),
            });
        }
    }
    state.stage("assets", "Загрузка звуков, текстур и игровых ресурсов");
    downloads::batch(state, transfers, cancel).await?;
    let virtual_dir = security::within(&assets_root, &format!("virtual/{}", index.id))?;
    if index_data.is_virtual || index_data.map_to_resources {
        let roots = [
            if index_data.is_virtual {
                Some(virtual_dir.clone())
            } else {
                None
            },
            if index_data.map_to_resources {
                Some(state.instance(&profile.id)?.join("resources"))
            } else {
                None
            },
        ];
        for root in roots.into_iter().flatten() {
            for (name, asset) in &index_data.objects {
                if cancel.is_cancelled() {
                    bail!("Операция отменена");
                }
                let target = security::within(&root, name)?;
                let source = assets_root
                    .join("objects")
                    .join(&asset.hash[..2])
                    .join(&asset.hash);
                fs::create_dir_all(target.parent().context("Нет папки assets")?)?;
                if !downloads::verify(&target, &Hash::new("sha1", &asset.hash)?)? {
                    fs::copy(&source, &target)?;
                }
            }
        }
    }
    Ok((
        if index_data.is_virtual {
            virtual_dir
        } else {
            assets_root
        },
        index.id.clone(),
    ))
}

async fn logging(
    state: &AppState,
    version: &Version,
    cancel: &CancellationToken,
) -> Result<Option<String>> {
    let Some(client) = version.logging.as_ref().and_then(|l| l.get("client")) else {
        return Ok(None);
    };
    let file = &client["file"];
    let id = file["id"].as_str().context("Нет имени logging config")?;
    let target = security::within(&state.root().join("minecraft/assets/log_configs"), id)?;
    downloads::download(
        state,
        &Transfer {
            url: file["url"]
                .as_str()
                .context("Нет URL logging config")?
                .into(),
            path: target.clone(),
            hash: Hash::new(
                "sha1",
                file["sha1"].as_str().context("Нет SHA1 logging config")?,
            )?,
            size: file["size"].as_u64(),
        },
        cancel,
    )
    .await?;
    Ok(client["argument"]
        .as_str()
        .map(|a| a.replace("${path}", &metadata::path_string(&target))))
}

pub async fn launch(
    state: &AppState,
    profile_id: &str,
    allow_memory_pressure: bool,
) -> Result<u32> {
    let guard = state.begin_job("authentication")?;
    let (account, token) = accounts::launch_identity(state).await?;
    let local = account.kind == AccountKind::Offline;
    let session_id = if local { "-".to_string() } else { format!("token:{token}:{}", account.id) };
    let mut profile: Profile = state.get("profiles", profile_id)?;
    let mut system = sysinfo::System::new();
    system.refresh_memory();
    let memory = arguments::memory_args(
        &profile,
        system.total_memory() / 1024 / 1024,
        system.available_memory() / 1024 / 1024,
        allow_memory_pressure,
    )?;
    let installed = install(state, &mut profile, &guard.token).await?;
    state.put("profiles", &profile.id, &profile)?;
    state.stage("launching", "Запуск настоящего Java-процесса");
    let instance = state.instance(&profile.id)?;
    let mut cp = installed
        .classpath
        .iter()
        .map(|p| metadata::path_string(p))
        .collect::<Vec<_>>();
    cp.push(metadata::path_string(&installed.client));
    let vars = HashMap::from([
        ("auth_player_name".into(), account.name.clone()),
        ("auth_uuid".into(), account.id.clone()),
        ("auth_access_token".into(), token.clone()),
        (
            "auth_session".into(),
            session_id.clone(),
        ),
        ("auth_xuid".into(), token_xuid(&token)),
        ("clientid".into(), if local { String::new() } else { state.config().microsoft_client_id }),
        ("user_type".into(), if local { "legacy" } else { "msa" }.into()),
        ("user_properties".into(), "{}".into()),
        ("version_name".into(), installed.version.id.clone()),
        ("version_type".into(), installed.version.kind.clone()),
        ("game_directory".into(), metadata::path_string(&instance)),
        (
            "assets_root".into(),
            metadata::path_string(&state.root().join("minecraft/assets")),
        ),
        (
            "game_assets".into(),
            metadata::path_string(&installed.assets),
        ),
        ("assets_index_name".into(), installed.asset_index),
        (
            "natives_directory".into(),
            metadata::path_string(&installed.natives),
        ),
        (
            "library_directory".into(),
            metadata::path_string(&state.root().join("minecraft/libraries")),
        ),
        ("classpath".into(), cp.join(metadata::classpath_separator())),
        (
            "classpath_separator".into(),
            metadata::classpath_separator().into(),
        ),
        ("launcher_name".into(), "WinLauncher".into()),
        ("launcher_version".into(), "1.0.0".into()),
        ("resolution_width".into(), profile.width.to_string()),
        ("resolution_height".into(), profile.height.to_string()),
    ]);
    let context = RuleContext::windows();
    let mut jvm = memory;
    if let Some(args) = &installed.version.arguments {
        jvm.extend(arguments::collect(args.get("jvm"), &context, &vars)?);
    }
    if !jvm
        .iter()
        .any(|a| a == "-cp" || a == "-classpath" || a == "--class-path")
    {
        jvm.extend([
            format!("-Djava.library.path={}", installed.natives.display()),
            "-cp".into(),
            cp.join(metadata::classpath_separator()),
        ]);
    }
    jvm.push("-Dlog4j2.formatMsgNoLookups=true".into());
    if let Some(arg) = installed.logging_arg {
        jvm.push(arg);
    }
    jvm.extend(profile.java_args.clone());
    jvm.push(installed.version.main_class.clone());
    let mut game = if let Some(args) = &installed.version.arguments {
        arguments::collect(args.get("game"), &context, &vars)?
    } else if let Some(args) = &installed.version.minecraft_arguments {
        arguments::split_legacy(args)?
            .iter()
            .map(|s| arguments::expand(s, &vars))
            .collect::<Result<Vec<_>>>()?
    } else {
        vec![
            account.name.clone(),
            session_id.clone(),
        ]
    };
    if !game.iter().any(|a| a == "--width") && installed.version.minecraft_arguments.is_some() {
        game.extend([
            "--width".into(),
            profile.width.to_string(),
            "--height".into(),
            profile.height.to_string(),
        ]);
    }
    if profile.fullscreen {
        game.push("--fullscreen".into());
    }
    if guard.token.is_cancelled() {
        bail!("Запуск отменён");
    }
    let mut command = Command::new(&installed.java.path);
    command
        .args(&jvm)
        .args(&game)
        .current_dir(&instance)
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .kill_on_drop(false);
    #[cfg(windows)]
    command.creation_flags(0x08000000);
    let mut child = command
        .spawn()
        .context("Java не запустилась. Проверьте путь runtime и антивирус.")?;
    let pid = child.id().context("Не удалось получить PID Java")?;
    let running = RunningGame {
        pid,
        profile_id: profile.id.clone(),
        version: profile.game_version.clone(),
        loader: profile.loader.as_str().into(),
        profile_name: profile.name.clone(),
        started_at: chrono::Utc::now().timestamp(),
    };
    *state.running.lock().unwrap_or_else(|e| e.into_inner()) = Some(running.clone());
    let stop = CancellationToken::new();
    *state.game_stop.lock().unwrap_or_else(|e| e.into_inner()) = stop.clone();
    let _ = state.emit("game-state", Some(running.clone()));
    state.log(
        "info",
        "launcher",
        &format!(
            "Minecraft {} ({}) запущен, PID {pid}. Команда с токенами не записывается.",
            profile.game_version,
            profile.loader.as_str()
        ),
    );
    let secrets = if local { Vec::new() } else { vec![token.clone(), session_id] };
    if let Some(stdout) = child.stdout.take() {
        let state = state.clone();
        let secrets = secrets.clone();
        tokio::spawn(async move {
            let mut lines = BufReader::new(stdout).lines();
            while let Ok(Some(line)) = lines.next_line().await {
                let text = security::redact(&line, &secrets);
                let level = if line.contains("ERROR") || line.contains("FATAL") {
                    "error"
                } else if line.contains("WARN") {
                    "warn"
                } else {
                    "info"
                };
                state.log(level, "minecraft", &text);
            }
        });
    }
    if let Some(stderr) = child.stderr.take() {
        let state = state.clone();
        tokio::spawn(async move {
            let mut lines = BufReader::new(stderr).lines();
            while let Ok(Some(line)) = lines.next_line().await {
                state.log("error", "minecraft", &security::redact(&line, &secrets));
            }
        });
    }
    let state = state.clone();
    tokio::spawn(async move {
        let outcome = tokio::select! {
            r = child.wait() => r,
            _ = stop.cancelled() => { let _ = child.kill().await; child.wait().await },
        };
        let duration = (chrono::Utc::now().timestamp() - running.started_at).max(0) as u64;
        let code = outcome.as_ref().ok().and_then(|s| s.code());
        let session = Session {
            id: uuid::Uuid::new_v4().to_string(),
            profile_id: profile.id.clone(),
            account_id: account.id,
            version: profile.game_version.clone(),
            loader: profile.loader.as_str().into(),
            started_at: running.started_at,
            duration,
            exit_code: code,
        };
        if let Err(e) = state.put("sessions", &session.id, &session) {
            state.log(
                "error",
                "launcher",
                &format!("Не удалось сохранить сессию: {e}"),
            );
        }
        profile.last_played = Some(running.started_at);
        profile.play_seconds += duration;
        if let Err(e) = state.put("profiles", &profile.id, &profile) {
            state.log(
                "error",
                "launcher",
                &format!("Не удалось сохранить статистику: {e}"),
            );
        }
        *state.running.lock().unwrap_or_else(|e| e.into_inner()) = None;
        let _ = state.emit("game-state", Option::<RunningGame>::None);
        state.log(
            if code == Some(0) { "info" } else { "warn" },
            "launcher",
            &format!("Minecraft завершён. Код: {code:?}. Время: {duration} с."),
        );
        if code != Some(0) {
            state.notify(
                "error",
                "Minecraft завершился с ошибкой. Откройте логи для подробностей.",
            );
        }
        if let Err(e) = fs::remove_dir_all(&installed.natives) {
            state.log(
                "warn",
                "launcher",
                &format!("Не удалось очистить natives: {e}"),
            );
        }
        state.changed();
    });
    Ok(pid)
}

fn token_xuid(token: &str) -> String {
    use base64::Engine;
    token
        .split('.')
        .nth(1)
        .and_then(|p| {
            base64::engine::general_purpose::URL_SAFE_NO_PAD
                .decode(p)
                .ok()
        })
        .and_then(|b| serde_json::from_slice::<Value>(&b).ok())
        .and_then(|v| v["xuid"].as_str().map(str::to_owned))
        .unwrap_or_default()
}

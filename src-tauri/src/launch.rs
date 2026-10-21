use std::collections::HashMap;
use std::io::{BufRead, BufReader};
use std::path::PathBuf;
use std::process::{Command, Stdio};

use serde_json::Value;
use tauri::{AppHandle, Manager};

use crate::accounts;
use crate::java;
use crate::loaders;
use crate::models::*;
use crate::mojang::{self, MergedVersion};
use crate::paths::Paths;
use crate::state::{lock, wlock, AppState};
use crate::util;

pub fn start_launch(app: AppHandle, profile_id: String, account_id: String) {
    tauri::async_runtime::spawn(async move {
        {
            let state = app.state::<AppState>();
            state.set_launch(LaunchStatus {
                profile_id: Some(profile_id.clone()),
                state: "checking".into(),
                message: String::new(),
                code: None,
                started_at: Some(chrono::Utc::now().to_rfc3339()),
            });
        }
        if let Err(error) = launch_inner(&app, &profile_id, &account_id).await {
            let state = app.state::<AppState>();
            state.log("error", "launcher", format!("Launch failed: {error}"));
            state.set_launch(LaunchStatus {
                profile_id: Some(profile_id),
                state: "error".into(),
                message: error,
                code: None,
                started_at: None,
            });
        }
    });
}

async fn launch_inner(app: &AppHandle, profile_id: &str, account_id: &str) -> Result<(), String> {
    let state = app.state::<AppState>();
    let profile = state
        .profile_by_id(profile_id)
        .ok_or_else(|| "profiles.notFound".to_string())?;
    let settings = state.settings();
    let paths = state.paths();

    // 1. Which version folder should run? (loaders install lazily)
    let version_id = match loaders::resolved_version_id(&profile, &paths) {
        Some(id) => id,
        None => {
            if loaders::is_modded(&profile.loader.kind) {
                return Err("versions.notInstalled".into());
            }
            profile.mc_version.clone()
        }
    };

    // 2. Files.
    state.set_launch_state(Some(profile.id.clone()), "checking", String::new());
    let merged = mojang::load_merged(app, &version_id).await?;
    let game_dir = PathBuf::from(&profile.game_dir);
    std::fs::create_dir_all(&game_dir).map_err(|e| e.to_string())?;
    let (missing, total, _, installed) = mojang::install_status(app, &merged);
    if missing > 0 || !installed {
        state.set_launch_state(
            Some(profile.id.clone()),
            "downloading",
            format!("{} / {} files", missing, total),
        );
        mojang::ensure_version(app, &merged, Some(game_dir.as_path()), Some(profile.id.clone())).await?;
    }

    // 3. Java.
    let required = merged.required_java();
    state.set_launch_state(Some(profile.id.clone()), "preparingJava", format!("Java {required}"));
    let runtime = java::resolve_for_launch(app, &profile, &settings, required).await?;
    state.log(
        "info",
        "launcher",
        format!("Java {} ({}) — {}", runtime.major, runtime.vendor, runtime.path),
    );

    // 4. Account.
    let account = accounts::launch_account(app, account_id).await?;

    // 5. Command line.
    let (java_path, args) = build_launch(&merged, &profile, &account, &runtime, &paths, &settings)?;

    // 6. Spawn the game.
    state.set_launch_state(Some(profile.id.clone()), "starting", String::new());
    state.emit_progress("Starting Minecraft", 1, 1, 0, 0);
    let mut command = Command::new(&java_path);
    command
        .args(&args)
        .current_dir(&game_dir)
        .stdin(Stdio::null())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());
    util::hide_console(&mut command);
    state.log("info", "launcher", format!("Starting Minecraft with {}", java_path.display()));
    let mut child = command
        .spawn()
        .map_err(|e| format!("Could not start Minecraft: {e}"))?;
    let pid = child.id();

    if let Some(stdout) = child.stdout.take() {
        let app = app.clone();
        std::thread::spawn(move || {
            for line in BufReader::new(stdout).lines().flatten() {
                if line.trim().is_empty() {
                    continue;
                }
                app.state::<AppState>().log("info", "game", line);
            }
        });
    }
    if let Some(stderr) = child.stderr.take() {
        let app = app.clone();
        std::thread::spawn(move || {
            for line in BufReader::new(stderr).lines().flatten() {
                if line.trim().is_empty() {
                    continue;
                }
                app.state::<AppState>().log("warn", "game", line);
            }
        });
    }

    *lock(&state.child) = Some(child);
    state.set_launch_state(Some(profile.id.clone()), "running", String::new());
    state.log("info", "launcher", format!("Minecraft is running (pid {pid})"));

    {
        let mut profiles = wlock(&state.profiles);
        if let Some(slot) = profiles.iter_mut().find(|p| p.id == profile.id) {
            slot.last_played_at = Some(chrono::Utc::now().to_rfc3339());
        }
    }
    state.save_profiles();

    // 7. Window behaviour + exit watcher.
    if let Some(window) = app.get_webview_window("main") {
        if settings.minimize_on_launch {
            let _ = window.minimize();
        }
        if settings.close_on_launch {
            let _ = window.close();
        }
    }

    let watcher = app.clone();
    let watched_id = profile.id.clone();
    std::thread::spawn(move || loop {
        std::thread::sleep(std::time::Duration::from_millis(700));
        let st = watcher.state::<AppState>();
        let mut guard = lock(&st.child);
        let finished = match guard.as_mut() {
            None => return, // stopped from the UI
            Some(child) => match child.try_wait() {
                Ok(Some(status)) => Some(status.code()),
                Ok(None) => None,
                Err(_) => Some(None),
            },
        };
        let Some(code) = finished else { continue };
        *guard = None;
        drop(guard);
        st.log(
            if code == Some(0) { "info" } else { "warn" },
            "launcher",
            format!("Minecraft exited with code {code:?}"),
        );
        st.set_launch(LaunchStatus {
            profile_id: Some(watched_id.clone()),
            state: "exited".into(),
            message: String::new(),
            code,
            started_at: None,
        });
        if let Some(window) = watcher.get_webview_window("main") {
            let _ = window.show();
            let _ = window.unminimize();
            let _ = window.set_focus();
        }
        return;
    });

    Ok(())
}

pub fn stop(app: &AppHandle) -> Result<(), String> {
    let state = app.state::<AppState>();
    let mut guard = lock(&state.child);
    let Some(child) = guard.as_mut() else {
        return Err("Minecraft is not running".into());
    };
    let _ = child.kill();
    let _ = child.wait();
    *guard = None;
    drop(guard);
    state.log("warn", "launcher", "Minecraft was stopped by the user");
    state.set_launch(LaunchStatus {
        profile_id: None,
        state: "idle".into(),
        message: String::new(),
        code: None,
        started_at: None,
    });
    Ok(())
}

/* ------------------------------------------------------------------ */
/* Command line                                                        */
/* ------------------------------------------------------------------ */

fn collect_args(items: &[Value], features: &HashMap<String, bool>) -> Vec<String> {
    let mut out = Vec::new();
    for item in items {
        match item {
            Value::String(text) => out.push(text.clone()),
            Value::Object(map) => {
                if let Some(rules) = map.get("rules").and_then(|r| r.as_array()) {
                    let parsed: Vec<Rule> = rules
                        .iter()
                        .filter_map(|r| serde_json::from_value(r.clone()).ok())
                        .collect();
                    if !mojang::rules_allow(&parsed, features) {
                        continue;
                    }
                }
                match map.get("value") {
                    Some(Value::String(text)) => out.push(text.clone()),
                    Some(Value::Array(values)) => {
                        for value in values {
                            if let Some(text) = value.as_str() {
                                out.push(text.to_string());
                            }
                        }
                    }
                    _ => {}
                }
            }
            _ => {}
        }
    }
    out
}

fn substitute(template: &str, vars: &HashMap<&str, String>) -> String {
    let mut out = template.to_string();
    for (key, value) in vars {
        let needle = format!("${{{key}}}");
        if out.contains(&needle) {
            out = out.replace(&needle, value);
        }
    }
    out
}

pub fn split_args(raw: &str) -> Vec<String> {
    raw.split_whitespace().map(|s| s.to_string()).collect()
}

pub fn build_launch(
    merged: &MergedVersion,
    profile: &Profile,
    account: &LaunchAccount,
    runtime: &JavaRuntime,
    paths: &Paths,
    settings: &Settings,
) -> Result<(PathBuf, Vec<String>), String> {
    let features = mojang::default_features();
    let game_dir = PathBuf::from(&profile.game_dir);
    let sep = if cfg!(windows) { ";" } else { ":" };
    let lib_root = paths.libraries();

    // Classpath ---------------------------------------------------------
    let mut classpath_entries: Vec<String> = Vec::new();
    for (artifact, _) in mojang::library_artifacts(merged) {
        let path = lib_root.join(artifact.path.replace('/', std::path::MAIN_SEPARATOR_STR));
        if path.is_file() {
            classpath_entries.push(path.to_string_lossy().to_string());
        }
    }
    if merged.client_jar.is_file() {
        classpath_entries.push(merged.client_jar.to_string_lossy().to_string());
    }
    if classpath_entries.is_empty() {
        return Err("versions.notInstalled".into());
    }
    let classpath = classpath_entries.join(sep);

    // Substitution table ------------------------------------------------
    let asset_index_name = merged
        .asset_index
        .as_ref()
        .map(|idx| idx.id.clone())
        .unwrap_or_else(|| "legacy".to_string());
    let game_assets = paths.assets().join("virtual").join(&asset_index_name);
    let log_config = merged
        .logging
        .as_ref()
        .and_then(|logging| logging.client.as_ref())
        .and_then(|client| client.file.as_ref())
        .map(|file| paths.assets().join("log_configs").join(&file.id))
        .unwrap_or_default();

    let mut vars: HashMap<&str, String> = HashMap::new();
    vars.insert("auth_player_name", account.name.clone());
    vars.insert("version_name", merged.id.clone());
    vars.insert("game_directory", game_dir.to_string_lossy().to_string());
    vars.insert("assets_root", paths.assets().to_string_lossy().to_string());
    vars.insert("game_assets", game_assets.to_string_lossy().to_string());
    vars.insert("assets_index_name", asset_index_name.clone());
    vars.insert("auth_uuid", account.uuid.replace('-', ""));
    vars.insert("auth_access_token", account.access_token.clone());
    vars.insert("auth_session", account.access_token.clone());
    vars.insert("clientid", "winlaucher".to_string());
    vars.insert("auth_xuid", account.xuid.clone());
    vars.insert("user_type", account.user_type.clone());
    vars.insert("version_type", merged.version_type.clone());
    vars.insert("natives_directory", merged.natives_dir.to_string_lossy().to_string());
    vars.insert("launcher_name", "WinLaucher".to_string());
    vars.insert("launcher_version", env!("CARGO_PKG_VERSION").to_string());
    vars.insert("classpath", classpath.clone());
    vars.insert("classpath_separator", sep.to_string());
    vars.insert("library_directory", lib_root.to_string_lossy().to_string());
    vars.insert("resolution_width", profile.width.to_string());
    vars.insert("resolution_height", profile.height.to_string());

    // JVM arguments -----------------------------------------------------
    let ram = profile.ram_mb.clamp(512, 65_536);
    let mut jvm_args: Vec<String> = vec![
        format!("-Xms{}M", (ram / 2).clamp(512, 4096)),
        format!("-Xmx{ram}M"),
    ];
    for arg in collect_args(&merged.jvm_args, &features) {
        let mut value = substitute(&arg, &vars);
        if value.contains("${path}") && !log_config.as_os_str().is_empty() {
            value = value.replace("${path}", &log_config.to_string_lossy());
        }
        jvm_args.push(value);
    }
    if !log_config.as_os_str().is_empty()
        && !jvm_args.iter().any(|arg| arg.starts_with("-Dlog4j.configurationFile="))
        && !merged.jvm_args.is_empty()
    {
        jvm_args.push(format!("-Dlog4j.configurationFile={}", log_config.to_string_lossy()));
    }
    if !jvm_args.iter().any(|arg| arg.contains("-Djava.library.path")) {
        jvm_args.push(format!(
            "-Djava.library.path={}",
            merged.natives_dir.to_string_lossy()
        ));
    }
    jvm_args.push("-Dminecraft.launcher.brand=WinLaucher".to_string());
    jvm_args.push(format!("-Dminecraft.launcher.version={}", env!("CARGO_PKG_VERSION")));
    for arg in split_args(&profile.jvm_args) {
        if !arg.trim().is_empty() {
            jvm_args.push(arg);
        }
    }
    for arg in split_args(&settings.jvm_args) {
        let arg = arg.trim();
        if arg.starts_with("-Xmx") || arg.starts_with("-Xms") || arg.is_empty() {
            continue; // the profile owns the memory settings
        }
        if arg.starts_with("-Djava.library.path") {
            continue;
        }
        jvm_args.push(arg.to_string());
    }

    // Game arguments ----------------------------------------------------
    let mut game_args: Vec<String> = Vec::new();
    if !merged.game_args.is_empty() {
        for arg in collect_args(&merged.game_args, &features) {
            game_args.push(substitute(&arg, &vars));
        }
    } else if let Some(legacy) = &merged.minecraft_arguments {
        for arg in legacy.split_whitespace() {
            game_args.push(substitute(arg, &vars));
        }
    }

    // Older metadata sometimes omits these; add whatever is missing.
    let missing: [(&str, String); 5] = [
        ("--username", account.name.clone()),
        ("--uuid", account.uuid.replace('-', "")),
        ("--accessToken", account.access_token.clone()),
        ("--version", merged.id.clone()),
        ("--gameDir", game_dir.to_string_lossy().to_string()),
    ];
    for (flag, value) in missing {
        if !game_args.iter().any(|arg| arg == flag) {
            game_args.push(flag.to_string());
            game_args.push(value);
        }
    }
    let optional: [(&str, String); 4] = [
        ("--assetsDir", paths.assets().to_string_lossy().to_string()),
        ("--assetIndex", asset_index_name.clone()),
        ("--userType", account.user_type.clone()),
        ("--versionType", "WinLaucher".to_string()),
    ];
    for (flag, value) in optional {
        if !game_args.iter().any(|arg| arg == flag) {
            game_args.push(flag.to_string());
            game_args.push(value);
        }
    }
    if !account.xuid.is_empty() && !game_args.iter().any(|arg| arg == "--xuid") {
        game_args.push("--xuid".to_string());
        game_args.push(account.xuid.clone());
    }
    if !game_args.iter().any(|arg| arg == "--width") {
        game_args.push("--width".to_string());
        game_args.push(profile.width.to_string());
        game_args.push("--height".to_string());
        game_args.push(profile.height.to_string());
    }
    if profile.fullscreen && !game_args.iter().any(|arg| arg == "--fullscreen") {
        game_args.push("--fullscreen".to_string());
    }

    if merged.main_class.trim().is_empty() {
        return Err("This version has no main class".into());
    }

    let mut args = jvm_args;
    args.push("-cp".to_string());
    args.push(classpath);
    args.push(merged.main_class.clone());
    args.extend(game_args);

    Ok((PathBuf::from(&runtime.path), args))
}

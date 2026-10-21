use std::collections::{HashMap, VecDeque};
use std::io::{BufRead, BufReader};
use std::path::PathBuf;
use std::process::{Command, Stdio};
use std::sync::{Arc, Mutex};

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

/// Remembers profiles that already used the "clean JVM arguments" retry so a
/// permanently broken install cannot loop forever.
static SAFE_RETRIED: Mutex<Option<std::collections::HashSet<String>>> = Mutex::new(None);

fn take_safe_retry(profile_id: &str) -> bool {
    let mut guard = match SAFE_RETRIED.lock() {
        Ok(guard) => guard,
        Err(poisoned) => poisoned.into_inner(),
    };
    let set = guard.get_or_insert_with(std::collections::HashSet::new);
    !set.insert(profile_id.to_string())
}

fn clear_safe_retry(profile_id: &str) {
    if let Ok(mut guard) = SAFE_RETRIED.lock() {
        if let Some(set) = guard.as_mut() {
            set.remove(profile_id);
        }
    }
}

pub fn start_launch(app: AppHandle, profile_id: String, account_id: String) {
    // A user-initiated start may use the automatic safe-mode retry once again.
    clear_safe_retry(&profile_id);
    spawn_launch(app, profile_id, account_id, false);
}

fn spawn_launch(app: AppHandle, profile_id: String, account_id: String, safe: bool) {
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
        if let Err(error) = launch_inner(&app, &profile_id, &account_id, safe).await {
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

async fn launch_inner(
    app: &AppHandle,
    profile_id: &str,
    account_id: &str,
    safe: bool,
) -> Result<(), String> {
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
                state.set_launch_state(
                    Some(profile.id.clone()),
                    "installing",
                    format!("{} {}", profile.loader.kind, profile.loader.version.clone().unwrap_or_default()),
                );
                loaders::ensure(app, &profile)
                    .await?
                    .ok_or_else(|| "versions.notInstalled".to_string())?
            } else {
                profile.mc_version.clone()
            }
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
    } else {
        // Everything exists, but a file may still be corrupt (same size, wrong
        // bytes). Re-hash and re-download instead of failing with exit code 1.
        let repaired = mojang::repair_files(app, &merged).await?;
        if repaired > 0 {
            state.log(
                "warn",
                "launcher",
                format!("{repaired} file(s) failed verification and were downloaded again"),
            );
            mojang::prepare_natives(app, &merged)?;
        }
    }
    // Native libraries must always be unpacked: without them the game dies with
    // UnsatisfiedLinkError (lwjgl.dll) even when every jar is present.
    mojang::prepare_natives(app, &merged)?;

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
    let (java_path, args) = build_launch(&merged, &profile, &account, &runtime, &paths, &settings, safe)?;
    state.log(
        "info",
        "launcher",
        format!(
            "Main class {} · {} args · natives {}",
            merged.main_class,
            args.len(),
            merged.natives_dir.display()
        ),
    );
    if settings.jvm_args.contains("debug") || safe {
        let mut printable = args.clone();
        if let Some(index) = printable.iter().position(|arg| arg == "--accessToken") {
            if let Some(token) = printable.get_mut(index + 1) {
                *token = "***".to_string();
            }
        }
        state.log("info", "launcher", format!("Command: {} {}", java_path.display(), printable.join(" ")));
    }
    if let Some(unresolved) = args.iter().find(|arg| arg.contains("${")) {
        state.log(
            "warn",
            "launcher",
            format!("Unresolved placeholder in the command line: {unresolved}"),
        );
    }

    // 6. Spawn the game.
    state.set_launch_state(Some(profile.id.clone()), "starting", String::new());
    state.emit_progress("Starting Minecraft", 1, 1, 0, 0);
    clear_crash_dumps(&game_dir);
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

    let tail: Arc<Mutex<VecDeque<String>>> = Arc::new(Mutex::new(VecDeque::new()));
    if let Some(stdout) = child.stdout.take() {
        let app = app.clone();
        let tail = Arc::clone(&tail);
        std::thread::spawn(move || pump(BufReader::new(stdout), app, tail, "info"));
    }
    if let Some(stderr) = child.stderr.take() {
        let app = app.clone();
        let tail = Arc::clone(&tail);
        std::thread::spawn(move || pump(BufReader::new(stderr), app, tail, "warn"));
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
    let account_id_watcher = account_id.to_string();
    let watched_dir = game_dir.clone();
    let safe_launch = safe;
    let tail_watcher = Arc::clone(&tail);
    let started = std::time::Instant::now();
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
        let lines: Vec<String> = tail_watcher
            .lock()
            .map(|t| t.iter().cloned().collect())
            .unwrap_or_default();
        let failed = code != Some(0);
        let mut reason = if failed { identify_reason(&lines) } else { String::new() };
        let mut hint = String::new();
        if failed {
            if let Some(dump) = newest_crash_dump(&watched_dir) {
                if let Ok(text) = std::fs::read_to_string(&dump) {
                    let details = parse_crash_dump(&text);
                    if !details.is_empty() {
                        reason = details.join(" | ");
                        hint = crash_hint(&details.join(" "));
                        st.log(
                            "error",
                            "launcher",
                            format!("JVM crash dump {}:\n{}", dump.display(), details.join("\n")),
                        );
                    }
                }
            }
        }
        let uptime = started.elapsed().as_secs();
        st.log(
            if failed { "error" } else { "info" },
            "launcher",
            format!("Minecraft exited with code {code:?} after {uptime}s"),
        );
        // A fast, unexplained exit is usually caused by a bad JVM flag or a heap
        // that the machine cannot reserve. Retry once with pristine arguments
        // before showing an error, so the launcher still starts the game.
        if failed && uptime < 90 && !safe_launch && take_safe_retry(&watched_id) {
            st.log(
                "warn",
                "launcher",
                "Retrying with clean JVM arguments (no custom flags, 1 GB heap)",
            );
            st.set_launch_state(Some(watched_id.clone()), "starting", "Safe mode".to_string());
            let handle = watcher.clone();
            let pid = watched_id.clone();
            let acct = account_id_watcher.clone();
            std::thread::spawn(move || {
                std::thread::sleep(std::time::Duration::from_millis(400));
                spawn_launch(handle, pid, acct, true);
            });
            return;
        }
        if !failed {
            clear_safe_retry(&watched_id);
        }
        if failed {
            for line in lines.iter().rev().take(60).rev() {
                st.log("error", "game", line.clone());
            }
            st.log(
                "error",
                "launcher",
                if hint.is_empty() {
                    format!("Launch failed: {reason}")
                } else {
                    format!("Launch failed: {reason} — {hint}")
                },
            );
        }
        st.set_launch(LaunchStatus {
            profile_id: Some(watched_id.clone()),
            state: if failed { "error".into() } else { "exited".into() },
            message: if failed {
                let head = match code {
                    Some(value) => format!("Exit code {value}: {reason}"),
                    None => reason.clone(),
                };
                if hint.is_empty() {
                    head
                } else {
                    format!("{head}\n\n{hint}")
                }
            } else {
                String::new()
            },
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

fn push_tail(tail: &Arc<Mutex<VecDeque<String>>>, line: &str) {
    if let Ok(mut buffer) = tail.lock() {
        if buffer.len() >= 200 {
            buffer.pop_front();
        }
        buffer.push_back(line.to_string());
    }
}

/// Picks the line from the game output that explains a crash.
pub fn identify_reason(lines: &[String]) -> String {
    const MARKERS: [&str; 7] = [
        "Exception",
        "Error:",
        "Caused by",
        "NoClassDefFound",
        "UnsatisfiedLink",
        "A fatal error",
        "failed to",
    ];
    for line in lines {
        let trimmed = line.trim();
        if trimmed.is_empty() {
            continue;
        }
        let lowered = trimmed.to_lowercase();
        if MARKERS.iter().any(|marker| lowered.contains(&marker.to_lowercase())) {
            return trimmed.chars().take(400).collect();
        }
    }
    lines
        .iter()
        .rev()
        .find(|line| !line.trim().is_empty())
        .map(|line| line.trim().chars().take(400).collect())
        .unwrap_or_default()
}

/// Minecraft 1.12+ writes its log as XML (`<log4j:Event ...>`). Showing that raw
/// in the UI is unreadable and hides the crash reason, so each event is folded
/// back into a single `logger: message` line.
pub fn extract_attribute(block: &str, name: &str) -> Option<String> {
    let needle = format!("{name}=\"");
    let start = block.find(&needle)? + needle.len();
    let rest = &block[start..];
    let end = rest.find('"')?;
    Some(rest[..end].to_string())
}

pub fn format_game_block(block: &str) -> Option<(String, String)> {
    if !block.contains("<log4j:Event") {
        return None;
    }
    let level = extract_attribute(block, "level").unwrap_or_else(|| "INFO".to_string());
    let logger = extract_attribute(block, "logger").unwrap_or_default();
    let mut parts: Vec<String> = Vec::new();
    for chunk in block.split("<![CDATA[").skip(1) {
        if let Some(end) = chunk.find("]]>") {
            parts.push(chunk[..end].to_string());
        }
    }
    let text = parts.join("\n").trim().to_string();
    if text.is_empty() {
        return None;
    }
    let text = if logger.is_empty() {
        text
    } else {
        format!("{logger}: {text}")
    };
    Some((level, text))
}

fn emit_game_line(app: &AppHandle, tail: &Arc<Mutex<VecDeque<String>>>, fallback: &str, level: &str, text: &str) {
    let text = text.trim();
    if text.is_empty() {
        return;
    }
    push_tail(tail, text);
    let normalized = match level.to_ascii_uppercase().as_str() {
        "ERROR" | "FATAL" => "error",
        "WARN" => "warn",
        "DEBUG" | "TRACE" => "debug",
        _ => fallback,
    };
    app.state::<AppState>().log(normalized, "game", text.to_string());
}

fn pump<R: BufRead>(reader: R, app: AppHandle, tail: Arc<Mutex<VecDeque<String>>>, fallback: &'static str) {
    let mut block = String::new();
    for line in reader.lines().map_while(Result::ok) {
        let trimmed = line.trim_end();
        if trimmed.trim().is_empty() {
            continue;
        }
        if !block.is_empty() {
            block.push('\n');
            block.push_str(trimmed);
            if block.contains("</log4j:Event>") {
                if let Some((level, text)) = format_game_block(&block) {
                    emit_game_line(&app, &tail, fallback, &level, &text);
                }
                block.clear();
            }
            continue;
        }
        if trimmed.contains("<log4j:Event") {
            if trimmed.contains("</log4j:Event>") {
                if let Some((level, text)) = format_game_block(trimmed) {
                    emit_game_line(&app, &tail, fallback, &level, &text);
                }
            } else {
                block = trimmed.to_string();
            }
            continue;
        }
        emit_game_line(&app, &tail, fallback, fallback, trimmed);
    }
    if !block.is_empty() {
        if let Some((level, text)) = format_game_block(&block) {
            emit_game_line(&app, &tail, fallback, &level, &text);
        } else {
            emit_game_line(&app, &tail, fallback, fallback, &block);
        }
    }
}

/// Removes crash dumps left by earlier runs so the newest one can be attributed
/// to the launch we are about to make.
fn clear_crash_dumps(game_dir: &std::path::Path) {
    let Ok(entries) = std::fs::read_dir(game_dir) else { return };
    for entry in entries.flatten() {
        let name = entry.file_name().to_string_lossy().to_string();
        if name.starts_with("hs_err_pid") && name.ends_with(".log") {
            let _ = std::fs::remove_file(entry.path());
        }
    }
}

fn newest_crash_dump(game_dir: &std::path::Path) -> Option<std::path::PathBuf> {
    let entries = std::fs::read_dir(game_dir).ok()?;
    entries
        .flatten()
        .filter(|entry| {
            let name = entry.file_name().to_string_lossy().to_string();
            name.starts_with("hs_err_pid") && name.ends_with(".log")
        })
        .max_by_key(|entry| entry.metadata().and_then(|m| m.modified()).ok())
        .map(|entry| entry.path())
}

/// Pulls the useful lines out of a HotSpot crash dump: the exception, the
/// signal and the native frame that faulted.
pub fn parse_crash_dump(text: &str) -> Vec<String> {
    let mut out: Vec<String> = Vec::new();
    let mut want_next_frame = false;
    for line in text.lines().take(120) {
        let trimmed = line.trim();
        if want_next_frame {
            // The frame itself is printed on the following line as `# C  [module]`.
            let frame = trimmed.trim_start_matches('#').trim();
            if !frame.is_empty() {
                out.push(format!("Problematic frame: {frame}"));
                want_next_frame = false;
            }
            continue;
        }
        if trimmed.starts_with("# A fatal error") {
            out.push(trimmed.trim_start_matches("# ").to_string());
        } else if trimmed.starts_with("siginfo:") || trimmed.starts_with("#  EXCEPTION") {
            out.push(trimmed.trim_start_matches("# ").trim().to_string());
        } else if let Some(rest) = trimmed.strip_prefix("Problematic frame") {
            let rest = rest.trim_start_matches([':', ' ']).trim();
            if rest.is_empty() {
                want_next_frame = true;
            } else {
                out.push(format!("Problematic frame: {rest}"));
            }
        } else if trimmed.starts_with("JRE version:") || trimmed.starts_with("Java VM:") {
            out.push(trimmed.to_string());
        }
        if out.len() >= 8 {
            break;
        }
    }
    out
}

/// Turns the faulting module into advice a user can act on.
pub fn crash_hint(detail: &str) -> String {
    let lowered = detail.to_lowercase();
    // Concrete Windows module names only: short fragments like "igd" or "atio"
    // also occur inside ordinary words and would produce false advice.
    let graphics = [
        "nvoglv", "nvwgf", "atio6axx", "atig6txx", "amdpcom", "amdvlk", "igc64", "igd10",
        "igd12", "igdumd", "opengl32", "glfw", "d3d11", "d3d9", "dxgi",
    ];
    if graphics.iter().any(|needle| lowered.contains(needle)) {
        return "Похоже на сбой драйвера видеокарты. Обновите драйвер GPU с сайта NVIDIA/AMD/Intel, затем запустите игру снова.".to_string();
    }
    if lowered.contains("lwjgl") || lowered.contains("openal") || lowered.contains("jinput") {
        return "Не загрузилась нативная библиотека игры. Нажмите «Играть» ещё раз: лаунчер пересоберёт папку natives и перепроверит файлы.".to_string();
    }
    if lowered.contains("jvm.dll") || lowered.contains("java.dll") {
        return "Сбой самой Java. Попробуйте другую версию Java в «Настройки → Java» (например 17 вместо 21).".to_string();
    }
    "Попробуйте нажать «Играть» ещё раз (лаунчер перезапустит игру с чистыми JVM-аргументами и heap 1 ГБ). Если не поможет — приложите лог.".to_string()
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

/// Keeps `-Xmx` inside the physical memory of the machine, otherwise the JVM
/// refuses to start with "Could not reserve enough space for object heap".
pub fn clamp_ram(requested: u64) -> u64 {
    let mut system = sysinfo::System::new();
    system.refresh_memory();
    let total_mb = system.total_memory() / 1024 / 1024;
    if total_mb == 0 {
        return requested.clamp(512, 65_536);
    }
    let ceiling = total_mb.saturating_sub(1024).max(1024).min(65_536);
    requested.clamp(512, ceiling)
}

pub fn build_launch(
    merged: &MergedVersion,
    profile: &Profile,
    account: &LaunchAccount,
    runtime: &JavaRuntime,
    paths: &Paths,
    settings: &Settings,
    safe: bool,
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
    vars.insert("primary_jar", merged.client_jar.to_string_lossy().to_string());
    let user_properties = if account.user_properties.is_empty() {
        "{}".to_string()
    } else {
        let mut map = serde_json::Map::new();
        for (key, value) in &account.user_properties {
            map.insert(key.clone(), Value::String(value.clone()));
        }
        Value::Object(map).to_string()
    };
    vars.insert("user_properties", user_properties);
    vars.insert("natives_directory", merged.natives_dir.to_string_lossy().to_string());
    vars.insert("launcher_name", "WinLaucher".to_string());
    vars.insert("launcher_version", env!("CARGO_PKG_VERSION").to_string());
    vars.insert("classpath", classpath.clone());
    vars.insert("classpath_separator", sep.to_string());
    vars.insert("library_directory", lib_root.to_string_lossy().to_string());
    vars.insert("resolution_width", profile.width.to_string());
    vars.insert("resolution_height", profile.height.to_string());

    // JVM arguments -----------------------------------------------------
    let ram = if safe { 1024 } else { clamp_ram(profile.ram_mb) };
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
    // Custom flags are skipped in safe mode: a single invalid -XX option makes
    // the JVM exit with code 1 before Minecraft even starts.
    for arg in if safe { Vec::new() } else { split_args(&profile.jvm_args) } {
        if !arg.trim().is_empty() {
            jvm_args.push(arg);
        }
    }
    for arg in if safe { Vec::new() } else { split_args(&settings.jvm_args) } {
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
    if profile.fullscreen && !safe && !game_args.iter().any(|arg| arg == "--fullscreen") {
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

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn substitutes_every_placeholder() {
        let mut vars: HashMap<&str, String> = HashMap::new();
        vars.insert("auth_player_name", "Steve".to_string());
        vars.insert("classpath", "a.jar;b.jar".to_string());
        assert_eq!(
            substitute("--username ${auth_player_name} -cp ${classpath}", &vars),
            "--username Steve -cp a.jar;b.jar"
        );
        assert_eq!(substitute("--gameDir ${unknown}", &vars), "--gameDir ${unknown}");
    }

    #[test]
    fn honours_argument_rules() {
        let items = vec![
            serde_json::json!("--demo"),
            serde_json::json!({
                "rules": [{ "action": "allow", "features": { "is_demo_user": true } }],
                "value": "--demoOnly"
            }),
            serde_json::json!({
                "rules": [{ "action": "allow", "features": { "has_custom_resolution": true } }],
                "value": ["--width", "1280", "--height", "720"]
            }),
        ];
        let args = collect_args(&items, &mojang::default_features());
        assert_eq!(args, vec!["--demo", "--width", "1280", "--height", "720"]);
    }

    #[test]
    fn reads_hotspot_crash_dumps() {
        let dump = r#"# A fatal error has been detected by the Java Runtime Environment:
#
#  EXCEPTION_ACCESS_VIOLATION (0xc0000005) at pc=0x00007ffb2d1a1234, pid=4242, tid=4243
#
# JRE version: OpenJDK Runtime Environment Temurin-21.0.5+11 (21.0.5+11) (build 21.0.5+11-LTS)
# Java VM: OpenJDK 64-Bit Server VM
#
# Problematic frame:
# C  [nvoglv64.dll+0x9c1234]
"#;
        let details = parse_crash_dump(dump);
        assert!(details.iter().any(|line| line.contains("EXCEPTION_ACCESS_VIOLATION")));
        assert!(details.iter().any(|line| line.contains("nvoglv64.dll")));
        assert!(details.iter().any(|line| line.contains("JRE version")));
        let hint = crash_hint(dump);
        assert!(hint.contains("драйвер"), "a GPU module must produce a driver hint: {hint}");
        let lwjgl_hint = crash_hint("C  [lwjgl.dll+0x1234]");
        assert!(!lwjgl_hint.contains("драйвер"), "lwjgl is not a driver problem: {lwjgl_hint}");
        assert!(lwjgl_hint.contains("natives"));
        assert!(crash_hint("random text").contains("Играть"));
    }

    #[test]
    fn unwraps_log4j_xml() {
        let block = r#"  <log4j:Event logger="flk" timestamp="1792588053579" level="INFO" thread="Render thread">
    <log4j:Message><![CDATA[Setting user: SelfTest]]></log4j:Message>
  </log4j:Event>"#;
        let (level, text) = format_game_block(block).expect("event must be parsed");
        assert_eq!(level, "INFO");
        assert_eq!(text, "flk: Setting user: SelfTest");
        let crash = r#"<log4j:Event logger="net.minecraft" level="ERROR"><log4j:Message><![CDATA[Could not execute entrypoint]]></log4j:Message><log4j:Throwable><![CDATA[java.lang.RuntimeException: boom]]></log4j:Throwable></log4j:Event>"#;
        let (level, text) = format_game_block(crash).unwrap();
        assert_eq!(level, "ERROR");
        assert!(text.contains("Could not execute entrypoint"));
        assert!(text.contains("java.lang.RuntimeException"));
    }

    #[test]
    fn finds_the_crash_reason() {
        let lines = vec![
            "Picked up _JAVA_OPTIONS".to_string(),
            "java.lang.UnsatisfiedLinkError: Failed to locate library: lwjgl.dll".to_string(),
            "at org.lwjgl.system.Library.loadSystem(Library.java:1)".to_string(),
        ];
        assert!(identify_reason(&lines).contains("UnsatisfiedLinkError"));
        assert_eq!(identify_reason(&[]), "");
    }

    #[test]
    fn keeps_ram_between_sane_bounds() {
        assert!(clamp_ram(64) >= 512);
        assert!(clamp_ram(1_000_000) <= 65_536);
    }
}

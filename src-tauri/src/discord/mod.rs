#[cfg(windows)] mod transport;
use crate::state::AppState;
use std::{sync::atomic::Ordering, time::Duration};

/// Real Discord Desktop IPC; no bot token and no server/IP disclosure.
/// Names and image assets belong to the user's Discord Developer application.
pub fn start(state: AppState) -> std::thread::JoinHandle<()> {
    std::thread::spawn(move || {
        #[cfg(windows)] {
            match tokio::runtime::Builder::new_current_thread().enable_all().build() {
                Ok(runtime) => runtime.block_on(worker(state)),
                Err(_) => set_status(&state, "disconnected"),
            }
        }
        #[cfg(not(windows))] {
            set_status(&state, "unsupported_platform");
            while !state.exit.load(Ordering::Relaxed) { std::thread::sleep(Duration::from_secs(1)); }
        }
    })
}

#[cfg(windows)]
async fn worker(state: AppState) {
    use serde_json::json;
    let mut connection: Option<transport::Connection> = None;
    let mut application_id = String::new();
    let mut last_payload = String::new();
    let mut last_sent = std::time::Instant::now();
    while !state.exit.load(Ordering::Relaxed) {
        let settings = state.config().discord;
        let running = state.running.lock().unwrap_or_else(|e| e.into_inner()).clone();
        let job = state.job.lock().unwrap_or_else(|e| e.into_inner()).clone();
        let hidden = !settings.enabled || settings.application_id.is_empty()
            || (running.is_some() && settings.hide_while_playing)
            || (running.is_none() && job.is_none() && !settings.show_launcher)
            || (running.is_none() && job.is_some() && !settings.show_download && !settings.show_launcher);
        if hidden || application_id != settings.application_id {
            if let Some(mut client) = connection.take() { client.close().await; }
            last_payload.clear();
            set_status(&state, if !settings.enabled { "disabled" } else if settings.application_id.is_empty() { "not_configured" } else { "hidden" });
        }
        if !hidden {
            if connection.is_none() {
                application_id = settings.application_id.clone();
                match transport::Connection::connect(&application_id).await {
                    Ok(client) => { connection = Some(client); last_payload.clear(); }
                    Err(_) => set_status(&state, "disconnected"),
                }
            }
            if let Some(client) = connection.as_mut() {
                let (details, activity_state, since) = if let Some(game) = running {
                    let text = if settings.show_version { format!("Minecraft {} · {}", game.version, game.loader) } else { "Minecraft Java Edition".into() };
                    let label = if settings.show_pack { format!("Играет · {}", game.profile_name) } else { "Играет через WinLauncher".into() };
                    (text, label, if settings.show_elapsed { Some(game.started_at) } else { None })
                } else if let Some(job) = job.filter(|_| settings.show_download) {
                    let progress = if job.total_bytes > 0 { format!("Загрузка: {}%", (job.completed_bytes.saturating_mul(100) / job.total_bytes).min(100)) } else { "Проверка и подготовка файлов".into() };
                    let detail = if settings.show_version && !job.detail.is_empty() { job.detail } else { "Устанавливает Minecraft и контент".into() };
                    (detail, progress, None)
                } else {
                    (state.activity.read().unwrap_or_else(|e| e.into_inner()).clone(), "WinLauncher — Minecraft Launcher".into(), None)
                };
                let mut payload = json!({
                    "details": details.chars().take(64).collect::<String>(),
                    "state": activity_state.chars().take(64).collect::<String>(),
                    "assets": { "large_image": "winlauncher", "large_text": "WinLauncher" },
                    "instance": false,
                });
                if let Some(start) = since { payload["timestamps"] = json!({ "start": start }); }
                if settings.show_website { payload["buttons"] = json!([{ "label": "WinLauncher", "url": "https://github.com/egorbelovziiik-design/WinLauncher" }]); }
                let serialized = payload.to_string();
                // Re-ACK unchanged activity every 30 seconds to detect Desktop shutdown.
                if serialized != last_payload || last_sent.elapsed() > Duration::from_secs(30) {
                    if client.activity(Some(payload)).await.is_ok() {
                        last_payload = serialized; last_sent = std::time::Instant::now(); set_status(&state, "connected");
                    } else { connection = None; set_status(&state, "disconnected"); }
                }
            }
        }
        // Normally one update per 15 seconds; disabling clears within one second.
        for _ in 0..15 {
            tokio::time::sleep(Duration::from_secs(1)).await;
            if state.exit.load(Ordering::Relaxed) || state.config().discord.enabled != settings.enabled || state.config().discord.application_id != application_id { break; }
        }
    }
    if let Some(mut client) = connection { client.close().await; }
    set_status(&state, "disabled");
}
fn set_status(state: &AppState, value: &str) {
    let mut current = state.discord_status.write().unwrap_or_else(|e| e.into_inner());
    if *current != value {
        *current = value.into(); let _ = state.emit("discord-state", value);
        if value == "connected" { state.notify("success", "Discord подтвердил подключение Rich Presence"); }
    }
}

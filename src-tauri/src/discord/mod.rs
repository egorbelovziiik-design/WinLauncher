use crate::state::AppState;
use discord_rich_presence::{activity, DiscordIpc, DiscordIpcClient};
use std::{sync::atomic::Ordering, time::Duration};
use tauri::Emitter;

/// Genuine Discord Desktop IPC, not a web-status simulation.
/// The application name and the 'winlauncher' image are configured in Discord Developer Portal.
pub fn start(state: AppState) -> std::thread::JoinHandle<()> {
    std::thread::spawn(move || {
        let mut connection: Option<DiscordIpcClient> = None;
        let mut application_id = String::new(); let mut last_payload = String::new();
        while !state.exit.load(Ordering::Relaxed) {
            let settings = state.config().discord;
            let running = state.running.lock().unwrap_or_else(|e| e.into_inner()).clone();
            let job = state.job.lock().unwrap_or_else(|e| e.into_inner()).clone();
            let hidden = !settings.enabled || settings.application_id.is_empty()
                || (running.is_some() && settings.hide_while_playing)
                || (running.is_none() && job.is_none() && !settings.show_launcher)
                || (running.is_none() && job.is_some() && !settings.show_download && !settings.show_launcher);
            if hidden || application_id != settings.application_id {
                if let Some(mut client) = connection.take() { let _ = client.clear_activity(); let _ = client.close(); }
                last_payload.clear();
                set_status(&state, if !settings.enabled { "disabled" } else if settings.application_id.is_empty() { "not_configured" } else { "hidden" });
            }
            if !hidden {
                if connection.is_none() {
                    application_id = settings.application_id.clone();
                    match DiscordIpcClient::new(&application_id) {
                        Ok(mut client) => { if client.connect().is_ok() { connection = Some(client); last_payload.clear(); } else { set_status(&state, "disconnected"); } }
                        Err(_) => set_status(&state, "invalid_id"),
                    }
                }
                if let Some(client) = connection.as_mut() {
                    let (details, activity_state, since) = if let Some(game) = running {
                        let text = if settings.show_version { format!("Minecraft {} · {}", game.version, game.loader) } else { "Minecraft Java Edition".into() };
                        let label = if settings.show_pack { format!("Играет · {}", game.profile_name) } else { "Играет через WinLauncher".into() };
                        (text, label, if settings.show_elapsed { Some(game.started_at) } else { None })
                    } else if let Some(job) = job.filter(|_| settings.show_download) {
                        let progress = if job.total_bytes > 0 { format!("Загрузка: {}%", (job.completed_bytes.saturating_mul(100) / job.total_bytes).min(100)) } else { "Проверка и подготовка файлов".into() };
                        let detail = if settings.show_version { job.detail } else { "Устанавливает Minecraft и контент".into() };
                        (detail, progress, None)
                    } else { (state.activity.read().unwrap_or_else(|e| e.into_inner()).clone(), "WinLauncher — Minecraft Launcher".into(), None) };
                    let details: String = details.chars().take(64).collect(); let activity_state: String = activity_state.chars().take(64).collect();
                    let payload = format!("{details}|{activity_state}|{since:?}|{}", settings.show_website);
                    if payload != last_payload {
                        let mut presence = activity::Activity::new().details(&details).state(&activity_state)
                            .assets(activity::Assets::new().large_image("winlauncher").large_text("WinLauncher"));
                        if let Some(start) = since { presence = presence.timestamps(activity::Timestamps::new().start(start)); }
                        if settings.show_website { presence = presence.buttons(vec![activity::Button::new("WinLauncher", "https://github.com/egorbelovziiik-design/WinLauncher")]); }
                        if client.set_activity(presence).is_ok() { last_payload = payload; set_status(&state, "connected"); }
                        else { if let Some(mut c) = connection.take() { let _ = c.close(); } set_status(&state, "disconnected"); }
                    }
                }
            }
            // Discord rate limit: no more than one activity update every 15 seconds.
            // Disable and shutdown are still observed once a second.
            for _ in 0..15 {
                std::thread::sleep(Duration::from_secs(1));
                if state.exit.load(Ordering::Relaxed) || state.config().discord.enabled != settings.enabled || state.config().discord.application_id != application_id { break; }
            }
        }
        if let Some(mut client) = connection { let _ = client.clear_activity(); let _ = client.close(); }
        set_status(&state, "disabled");
    })
}
fn set_status(state: &AppState, value: &str) {
    let mut current = state.discord_status.write().unwrap_or_else(|e| e.into_inner());
    if *current != value { *current = value.into(); let _ = state.app.emit("discord-state", value); if value == "connected" { state.notify("success", "Discord Rich Presence подключён"); } }
}

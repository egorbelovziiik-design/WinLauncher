mod accounts;
mod commands;
mod content;
mod curseforge;
mod discord;
mod downloads;
mod java;
mod logs;
mod minecraft;
mod models;
mod modpacks;
mod modrinth;
mod profiles;
mod security;
mod settings;
#[cfg(feature = "native-smoke")]
pub mod smoke;
mod state;
mod worlds;
use state::AppState;
use std::sync::{atomic::Ordering, Mutex};
use tauri::{
    menu::{Menu, MenuItem},
    tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent},
    Manager,
};
struct DiscordWorker(Mutex<Option<std::thread::JoinHandle<()>>>);

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let application = tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .setup(|app| {
            let state = AppState::new(app.handle().clone(), app.path().app_data_dir()?)?;
            let worker = discord::start(state.clone());
            app.manage(DiscordWorker(Mutex::new(Some(worker))));
            app.manage(state);
            let show = MenuItem::with_id(app, "show", "Открыть WinLauncher", true, None::<&str>)?;
            let quit = MenuItem::with_id(app, "quit", "Выйти", true, None::<&str>)?;
            let menu = Menu::with_items(app, &[&show, &quit])?;
            let mut tray = TrayIconBuilder::with_id("winlauncher").menu(&menu).tooltip("WinLauncher").show_menu_on_left_click(false);
            if let Some(icon) = app.default_window_icon() { tray = tray.icon(icon.clone()); }
            tray.on_menu_event(|app, event| {
                if event.id.as_ref() == "show" { show_main(app); }
                if event.id.as_ref() == "quit" {
                    let state = app.state::<AppState>();
                    if state.has_running() || state.has_job() {
                        show_main(app); state.notify("info", "Завершите игру или текущую операцию перед выходом. Лаунчер продолжает отслеживать процесс.");
                    } else { app.exit(0); }
                }
            }).on_tray_icon_event(|tray, event| {
                if let TrayIconEvent::Click { button: MouseButton::Left, button_state: MouseButtonState::Up, .. } = event { show_main(tray.app_handle()); }
            }).build(app)?;
            Ok(())
        })
        .on_window_event(|window, event| {
            if let tauri::WindowEvent::CloseRequested { api, .. } = event {
                let state = window.state::<AppState>();
                if state.config().close_to_tray || state.has_running() || state.has_job() {
                    api.prevent_close(); let _ = window.hide();
                }
            }
        })
        .invoke_handler(tauri::generate_handler![
            commands::get_snapshot, commands::save_settings, commands::get_system_info,
            commands::version_manifest, commands::loader_versions, commands::save_profile, commands::delete_profile,
            commands::install_game, commands::launch_game, commands::cancel_task, commands::stop_game,
            commands::discover_java, commands::install_java, commands::begin_login, commands::poll_login, commands::cancel_login, commands::remove_account,
            commands::save_curseforge_key, commands::search_projects, commands::project_detail, commands::project_versions, commands::install_project,
            commands::toggle_content, commands::remove_content, commands::check_mod_updates, commands::install_modrinth_pack, commands::install_curseforge_pack, commands::import_modpack, commands::export_modpack,
            commands::open_directory, commands::open_external, commands::migrate_directory,
            commands::get_logs, commands::clear_logs_view, commands::export_logs,
            commands::local_files, commands::backup_world, commands::restore_world, commands::screenshot_data,
            commands::set_activity, commands::minecraft_news, commands::check_launcher_update
        ])
        .build(tauri::generate_context!()).expect("Unable to start WinLauncher");
    application.run(|app, event| {
        if let tauri::RunEvent::ExitRequested { api, .. } = event {
            let state = app.state::<AppState>();
            if state.has_running() || state.has_job() {
                api.prevent_exit();
                return;
            }
            state.exit.store(true, Ordering::Relaxed);
            if let Some(worker) = app
                .state::<DiscordWorker>()
                .0
                .lock()
                .unwrap_or_else(|e| e.into_inner())
                .take()
            {
                let _ = worker.join();
            }
        }
    });
}
fn show_main(app: &tauri::AppHandle) {
    if let Some(window) = app.get_webview_window("main") {
        let _ = window.show();
        let _ = window.unminimize();
        let _ = window.set_focus();
    }
}

mod accounts;
mod commands;
mod curseforge;
mod download;
mod java;
mod launch;
mod loaders;
mod models;
mod modpack;
mod modrinth;
mod mojang;
mod news;
mod paths;
mod profiles;
mod secret;
mod state;
mod util;

use std::path::PathBuf;

use tauri::Manager;

use crate::models::{Account, Profile, Settings};
use crate::paths::Paths;
use crate::secret::SecretStore;
use crate::state::{wlock, AppState};

fn data_root() -> PathBuf {
    dirs::data_dir()
        .unwrap_or_else(|| PathBuf::from("."))
        .join("WinLaucher")
}

fn default_ram_mb() -> u64 {
    let mut system = sysinfo::System::new();
    system.refresh_memory();
    let total = system.total_memory() / 1024 / 1024;
    if total < 4096 {
        2048
    } else if total < 8192 {
        4096
    } else {
        6144
    }
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .setup(|app| {
            let handle = app.handle().clone();
            let root = data_root();
            let default_mc = root.join("minecraft");
            let mut paths = Paths::new(&default_mc.to_string_lossy());
            let io = |e: std::io::Error| std::io::Error::new(e.kind(), format!("Could not create {root:?}: {e}"));
            paths.ensure().map_err(io)?;

            // Settings first: the game directory can be relocated.
            let stored: Option<Settings> = util::read_json(&paths.settings_file());
            let mut settings = match stored {
                Some(mut value) => {
                    if !value.minecraft_dir.trim().is_empty() {
                        paths.mc = PathBuf::from(&value.minecraft_dir);
                    }
                    if value.default_ram_mb < 512 {
                        value.default_ram_mb = default_ram_mb();
                    }
                    value
                }
                None => Settings::with_defaults(default_mc.to_string_lossy().to_string(), default_ram_mb()),
            };
            let mc_path = paths.mc.clone();
            paths
                .ensure()
                .map_err(|e| std::io::Error::new(e.kind(), format!("Could not create {}: {e}", mc_path.display())))?;

            let mut profiles: Vec<Profile> = util::read_json(&paths.profiles_file()).unwrap_or_default();
            for profile in profiles.iter_mut() {
                if profile.game_dir.trim().is_empty() {
                    profile.game_dir = paths.profile_dir(&profile.id).to_string_lossy().to_string();
                }
                let _ = std::fs::create_dir_all(PathBuf::from(&profile.game_dir).join("mods"));
            }
            let accounts: Vec<Account> = util::read_json(&paths.accounts_file()).unwrap_or_default();

            let secrets = SecretStore::new(paths.data.join("secrets"));
            let state = AppState::new(handle.clone(), paths.clone(), secrets, download::http_client());
            *wlock(&state.settings) = settings.clone();
            *wlock(&state.profiles) = profiles;
            *wlock(&state.accounts) = accounts;
            app.manage(state);

            settings.curseforge_key_set = app
                .state::<AppState>()
                .secrets
                .get("curseforge-key")
                .is_some();
            *wlock(&app.state::<AppState>().settings) = settings;
            profiles::reconcile(&handle);

            let st = app.state::<AppState>();
            st.log("info", "launcher", format!("WinLaucher {} starting", env!("CARGO_PKG_VERSION")));
            st.log("info", "launcher", format!("Data directory: {}", paths.data.display()));
            st.log("info", "launcher", format!("Minecraft directory: {}", paths.mc.display()));

            // Java detection runs in the background so the UI opens instantly.
            let java_handle = handle.clone();
            std::thread::spawn(move || {
                let found = java::detect(&java_handle);
                let count = found.len();
                for runtime in found {
                    let _ = java::add(&java_handle, &runtime.path);
                }
                java_handle
                    .state::<AppState>()
                    .log("info", "launcher", format!("Detected {count} Java runtime(s)"));
            });

            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            commands::app_boot,
            commands::settings_load,
            commands::settings_save,
            commands::settings_reset,
            commands::profiles_list,
            commands::profile_create,
            commands::profile_update,
            commands::profile_delete,
            commands::profile_open_folder,
            commands::profile_status,
            commands::verify_files,
            commands::accounts_list,
            commands::account_add_offline,
            commands::account_delete,
            commands::account_set_primary,
            commands::ms_begin,
            commands::ms_poll,
            commands::ms_refresh,
            commands::java_list,
            commands::java_detect,
            commands::java_add,
            commands::java_download,
            commands::mc_versions,
            commands::install_version,
            commands::delete_version,
            commands::loader_versions,
            commands::news_list,
            commands::downloads_list,
            commands::downloads_cancel,
            commands::downloads_retry,
            commands::downloads_clear,
            commands::logs_tail,
            commands::logs_clear,
            commands::logs_export,
            commands::app_open_logs_dir,
            commands::app_open_data_dir,
            commands::app_open_url,
            commands::app_open_path,
            commands::cache_size,
            commands::cache_clear,
            commands::autostart_get,
            commands::autostart_set,
            commands::launch_start,
            commands::launch_stop,
            commands::modrinth_search,
            commands::modrinth_tags,
            commands::modrinth_project,
            commands::modrinth_versions,
            commands::mods_installed,
            commands::mod_install,
            commands::mod_delete,
            commands::mod_toggle,
            commands::mod_import,
            commands::curseforge_status,
            commands::curseforge_search,
            commands::curseforge_save_key,
            commands::modpacks_search,
            commands::modpack_versions,
            commands::modpack_install,
            commands::modpack_import,
        ])
        .run(tauri::generate_context!())
        .expect("error while running WinLaucher");
}

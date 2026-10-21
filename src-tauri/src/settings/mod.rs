use crate::{
    models::{Account, Profile, Settings, SystemInfo},
    security,
    state::AppState,
};
use anyhow::{bail, Context, Result};
use std::{
    fs,
    path::{Path, PathBuf},
};

pub const THEMES: &[&str] = &[
    "overworld",
    "nether",
    "the-end",
    "diamond",
    "cherry-grove",
    "deep-dark",
    "obsidian",
    "classic",
    "snow-biome",
    "ocean-monument",
];
pub fn save(state: &AppState, mut settings: Settings) -> Result<()> {
    if !THEMES.contains(&settings.theme.as_str())
        || !["ru", "en", "uk"].contains(&settings.language.as_str())
    {
        bail!("Неизвестная тема или язык");
    }
    if settings.data_dir != state.config().data_dir {
        bail!("Для смены директории используйте безопасный перенос файлов");
    }
    if !(1..=16).contains(&settings.download_concurrency)
        || settings.download_limit_kib > 1024 * 1024
    {
        bail!("Некорректное ограничение загрузок");
    }
    if !settings.microsoft_client_id.is_empty()
        && uuid::Uuid::parse_str(&settings.microsoft_client_id).is_err()
    {
        bail!("Microsoft Application ID должен быть UUID");
    }
    if !settings.discord.application_id.is_empty()
        && (!(16..=22).contains(&settings.discord.application_id.len())
            || !settings
                .discord
                .application_id
                .chars()
                .all(|c| c.is_ascii_digit()))
    {
        bail!("Discord Application ID должен состоять из 16–22 цифр");
    }
    if !["Ctrl+Enter", "Ctrl+P", "disabled"].contains(&settings.launch_hotkey.as_str()) {
        bail!("Неизвестная горячая клавиша");
    }
    if settings.favorite_versions.len() > 500 || settings.favorite_projects.len() > 1000 {
        bail!("Слишком много избранных элементов");
    }
    if let Some(id) = &settings.selected_profile {
        let _: Profile = state.get("profiles", id)?;
    }
    if let Some(id) = &settings.selected_account {
        let _: Account = state.get("accounts", id)?;
    }
    settings.microsoft_client_id = settings.microsoft_client_id.trim().into();
    // Persist first, then publish in-memory state (failed writes cannot look successful).
    let json = serde_json::to_string(&settings)?;
    state.db.lock().unwrap_or_else(|e| e.into_inner()).execute("INSERT INTO settings(id,payload) VALUES(1,?1) ON CONFLICT(id) DO UPDATE SET payload=excluded.payload", [json])?;
    *state.settings.write().unwrap_or_else(|e| e.into_inner()) = settings;
    state.changed();
    Ok(())
}

pub fn system_info(state: &AppState) -> Result<SystemInfo> {
    let system = sysinfo::System::new_all();
    let mut gpu = "Не удалось определить".to_string();
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        if let Ok(output) = std::process::Command::new("powershell.exe").args(["-NoProfile", "-NonInteractive", "-Command", "[Console]::OutputEncoding=[Text.Encoding]::UTF8; (Get-CimInstance Win32_VideoController).Name -join ', '"]).creation_flags(0x08000000).output() {
            let name = String::from_utf8_lossy(&output.stdout).trim().to_string(); if output.status.success() && !name.is_empty() { gpu = name; }
        }
    }
    Ok(SystemInfo {
        cpu: system
            .cpus()
            .first()
            .map(|c| c.brand().to_string())
            .unwrap_or_default(),
        gpu,
        os: format!(
            "{} {}",
            sysinfo::System::name().unwrap_or_default(),
            sysinfo::System::os_version().unwrap_or_default()
        ),
        arch: std::env::consts::ARCH.into(),
        total_memory_mb: system.total_memory() / 1024 / 1024,
        available_memory_mb: system.available_memory() / 1024 / 1024,
        free_disk_bytes: fs2::available_space(state.root())?,
    })
}

pub fn visit_files(root: &Path, out: &mut Vec<PathBuf>) -> Result<()> {
    security::reject_link(root)?;
    if out.len() > 200_000 {
        bail!("Слишком много файлов");
    }
    for entry in fs::read_dir(root)? {
        let path = entry?.path();
        security::reject_link(&path)?;
        if path.is_dir() {
            visit_files(&path, out)?;
        } else if path.is_file() {
            out.push(path);
        }
    }
    Ok(())
}

pub fn migrate(state: &AppState, destination: &Path) -> Result<()> {
    let _guard = state.begin_job("migration")?;
    let source = state.root().canonicalize()?;
    if !destination.is_absolute() {
        bail!("Выберите абсолютный путь");
    }
    for p in destination.ancestors() {
        security::reject_link(p)?;
    }
    fs::create_dir_all(destination)?;
    let dest = destination.canonicalize()?;
    if dest.starts_with(&source) || source.starts_with(&dest) {
        bail!("Новая папка не может совпадать, содержать старую или находиться внутри неё");
    }
    if fs::read_dir(&dest)?.next().is_some() {
        bail!("Выберите пустую папку. Чужие файлы не будут изменены.");
    }
    let mut files = Vec::new();
    visit_files(&source, &mut files)?;
    let total: u64 = files
        .iter()
        .map(|f| fs::metadata(f).map(|m| m.len()).unwrap_or(0))
        .sum();
    if fs2::available_space(&dest)? < total + 256 * 1024 * 1024 {
        bail!("Недостаточно свободного места для переноса");
    }
    state.progress(|j| {
        j.total_bytes = total;
        j.total_files = files.len();
    });
    for file in files {
        if _guard.token.is_cancelled() {
            bail!("Копирование отменено. Исходные данные сохранены; новая папка содержит частичную копию.");
        }
        let relative = file.strip_prefix(&source)?;
        let to = dest.join(relative);
        fs::create_dir_all(to.parent().context("Нет папки")?)?;
        fs::copy(&file, &to)?;
        if crate::downloads::hash_file(&file, "sha256")?
            != crate::downloads::hash_file(&to, "sha256")?
        {
            bail!("Копия не прошла проверку. Исходная папка остаётся активной.");
        }
        state.progress(|j| {
            j.completed_bytes += fs::metadata(&file).map(|m| m.len()).unwrap_or(0);
            j.completed_files += 1;
        });
    }
    for dir in [
        "profiles",
        "minecraft",
        "runtime",
        "downloads",
        "logs",
        "backups",
    ] {
        fs::create_dir_all(dest.join(dir))?;
    }
    let old = state.config().data_dir;
    state
        .settings
        .write()
        .unwrap_or_else(|e| e.into_inner())
        .data_dir = dest.to_string_lossy().into_owned();
    if let Err(e) = state.persist_settings() {
        state
            .settings
            .write()
            .unwrap_or_else(|e| e.into_inner())
            .data_dir = old;
        return Err(e);
    }
    state.notify(
        "success",
        "Данные скопированы и проверены. Исходная папка сохранена без изменений.",
    );
    Ok(())
}

pub fn open_managed(state: &AppState, kind: &str, profile_id: Option<&str>) -> Result<()> {
    let path = match kind {
        "data" => state.root(),
        "runtime" | "downloads" | "logs" | "backups" | "profiles" | "minecraft" => {
            security::within(&state.root(), kind)?
        }
        "mods" | "resourcepacks" | "shaderpacks" | "screenshots" | "saves" | "profile" => {
            let id = profile_id.context("Выберите профиль")?;
            let _: Profile = state.get("profiles", id)?;
            let root = state.instance(id)?;
            if kind == "profile" {
                root
            } else {
                security::within(&root, kind)?
            }
        }
        _ => bail!("Неизвестная директория"),
    };
    fs::create_dir_all(&path)?;
    open::that(path)?;
    Ok(())
}

pub fn open_url(raw: &str) -> Result<()> {
    let url = url::Url::parse(raw)?;
    if url.scheme() != "https"
        || !url.username().is_empty()
        || url.password().is_some()
        || ![
            "github.com",
            "www.minecraft.net",
            "minecraft.net",
            "www.microsoft.com",
            "microsoft.com",
            "login.microsoftonline.com",
            "login.live.com",
            "account.microsoft.com",
            "portal.azure.com",
            "portal.azure.com",
            "learn.microsoft.com",
            "discord.com",
            "modrinth.com",
            "www.curseforge.com",
            "curseforge.com",
            "console.curseforge.com",
            "support.curseforge.com",
            "xbox.com",
            "www.xbox.com",
            "www.amd.com",
        ]
        .contains(&url.host_str().unwrap_or(""))
    {
        bail!("Внешний адрес не разрешён");
    }
    open::that(url.as_str())?;
    Ok(())
}

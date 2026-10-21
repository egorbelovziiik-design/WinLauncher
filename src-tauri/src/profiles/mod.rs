use crate::{
    models::{InstalledFile, Loader, Profile},
    security,
    state::AppState,
};
use anyhow::{bail, Result};
use std::fs;

pub fn validate(profile: &Profile) -> Result<()> {
    security::safe_id(&profile.id)?;
    security::safe_id(&profile.game_version)?;
    if profile.name.trim().is_empty() || profile.name.chars().count() > 64 {
        bail!("Название профиля: от 1 до 64 символов");
    }
    if profile.min_memory_mb < 256
        || profile.max_memory_mb < profile.min_memory_mb
        || profile.max_memory_mb > 131072
    {
        bail!("Некорректный диапазон памяти (256–131072 МБ)");
    }
    if !(640..=7680).contains(&profile.width) || !(360..=4320).contains(&profile.height) {
        bail!("Недопустимое разрешение окна");
    }
    if profile.java_args.len() > 64 {
        bail!("Слишком много JVM-параметров");
    }
    for arg in &profile.java_args {
        if !arg.starts_with('-')
            || arg.len() > 512
            || arg.contains(['\n', '\r', '\0'])
            || [
                "-Xmx",
                "-Xms",
                "-cp",
                "-classpath",
                "--class-path",
                "-jar",
                "-javaagent",
                "-agentpath",
                "-agentlib",
                "@",
                "-Djava.library.path",
                "-Dminecraft",
            ]
            .iter()
            .any(|prefix| arg.starts_with(prefix))
        {
            bail!("Недопустимый JVM-параметр: {arg}. Память задаётся слайдерами; агенты и подмена classpath запрещены.");
        }
    }
    if let Some(version) = &profile.loader_version {
        security::safe_id(version)?;
    }
    Ok(())
}

pub fn new(name: String, game_version: String, loader: Loader) -> Profile {
    Profile {
        id: uuid::Uuid::new_v4().to_string(),
        name,
        game_version,
        loader,
        loader_version: None,
        min_memory_mb: 1024,
        max_memory_mb: 4096,
        java_path: None,
        java_args: vec![],
        width: 1280,
        height: 720,
        fullscreen: false,
        created_at: chrono::Utc::now().timestamp(),
        last_played: None,
        play_seconds: 0,
        installed: false,
        modpack: None,
    }
}

pub fn save(state: &AppState, mut profile: Profile) -> Result<Profile> {
    let _operation = state.begin_job("profile")?;
    validate(&profile)?;
    if let Ok(existing) = state.get::<Profile>("profiles", &profile.id) {
        if existing.game_version != profile.game_version
            || existing.loader != profile.loader
            || existing.loader_version != profile.loader_version
        {
            if state
                .list::<InstalledFile>("installed")?
                .iter()
                .any(|f| f.profile_id == profile.id)
            {
                bail!("В профиле уже есть моды. Создайте отдельный профиль для другой версии или загрузчика.");
            }
            profile.installed = false;
        } else {
            profile.installed = existing.installed;
        }
        profile.created_at = existing.created_at;
        profile.last_played = existing.last_played;
        profile.play_seconds = existing.play_seconds;
        profile.modpack = existing.modpack;
    } else {
        profile.installed = false;
        profile.play_seconds = 0;
        profile.last_played = None;
    }
    let path = state.instance(&profile.id)?;
    for folder in [
        "mods",
        "resourcepacks",
        "shaderpacks",
        "screenshots",
        "saves",
    ] {
        fs::create_dir_all(security::within(&path, folder)?)?;
    }
    state.put("profiles", &profile.id, &profile)?;
    if state.config().selected_profile.is_none() {
        state
            .settings
            .write()
            .unwrap_or_else(|e| e.into_inner())
            .selected_profile = Some(profile.id.clone());
        state.persist_settings()?;
    }
    state.changed();
    Ok(profile)
}

/// Never recursively delete worlds: archive the whole instance into managed backups.
pub fn delete(state: &AppState, id: &str) -> Result<()> {
    let _operation = state.begin_job("profile")?;
    let _: Profile = state.get("profiles", id)?;
    let path = state.instance(id)?;
    let destination = security::within(
        &state.root(),
        &format!(
            "backups/removed-profile-{id}-{}",
            chrono::Utc::now().timestamp_millis()
        ),
    )?;
    if path.exists() {
        fs::rename(&path, &destination)?;
    }
    state.remove("profiles", id)?;
    for f in state
        .list::<InstalledFile>("installed")?
        .into_iter()
        .filter(|f| f.profile_id == id)
    {
        state.remove("installed", &file_id(&f))?;
    }
    if state.config().selected_profile.as_deref() == Some(id) {
        state
            .settings
            .write()
            .unwrap_or_else(|e| e.into_inner())
            .selected_profile = state
            .list::<Profile>("profiles")?
            .first()
            .map(|p| p.id.clone());
        state.persist_settings()?;
    }
    state.notify(
        "success",
        "Профиль удалён из библиотеки. Все его файлы сохранены в backups.",
    );
    state.changed();
    Ok(())
}
pub fn file_id(file: &InstalledFile) -> String {
    format!("{}:{}:{}", file.profile_id, file.provider, file.project_id)
}
pub fn content_folder(kind: &str) -> Result<&'static str> {
    match kind { "mod" => Ok("mods"), "resourcepack" => Ok("resourcepacks"), "shader" => Ok("shaderpacks"), _ => bail!("Этот тип контента требует отдельного импорта (датапаки устанавливаются в выбранный мир)") }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn memory_and_agents_are_validated() {
        let mut p = new("Test".into(), "1.21.4".into(), Loader::Vanilla);
        assert!(validate(&p).is_ok());
        p.max_memory_mb = 512;
        assert!(validate(&p).is_err());
        p.max_memory_mb = 4096;
        p.java_args.push("-javaagent:C:/evil.jar".into());
        assert!(validate(&p).is_err());
    }
    #[test]
    fn version_paths_are_validated() {
        let mut p = new("Test".into(), "../evil".into(), Loader::Fabric);
        assert!(validate(&p).is_err());
        p.game_version = "1.8.9".into();
        assert!(validate(&p).is_ok());
    }
}

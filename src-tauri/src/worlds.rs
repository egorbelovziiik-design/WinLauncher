use crate::{models::Profile, security, settings::visit_files, state::AppState};
use anyhow::{bail, Context, Result};
use base64::Engine;
use serde::Serialize;
use std::{fs, path::Path};

#[derive(Serialize)]
pub struct LocalFile {
    pub name: String,
    pub size: u64,
    pub modified: u64,
}
pub fn list(state: &AppState, profile_id: &str, kind: &str) -> Result<Vec<LocalFile>> {
    let _: Profile = state.get("profiles", profile_id)?;
    let dir = match kind {
        "worlds" => state.instance(profile_id)?.join("saves"),
        "screenshots" => state.instance(profile_id)?.join("screenshots"),
        "backups" => security::within(&state.root(), &format!("backups/worlds/{profile_id}"))?,
        _ => bail!("Неизвестный тип файлов"),
    };
    if !dir.exists() {
        return Ok(vec![]);
    }
    security::reject_link(&dir)?;
    let mut result = Vec::new();
    for entry in fs::read_dir(dir)? {
        let entry = entry?;
        if security::reject_link(&entry.path()).is_err() {
            continue;
        }
        let name = entry.file_name().to_string_lossy().to_string();
        let meta = entry.metadata()?;
        let is_image = ["png", "jpg", "jpeg"].contains(
            &entry
                .path()
                .extension()
                .and_then(|s| s.to_str())
                .unwrap_or(""),
        );
        if (kind == "worlds" && meta.is_dir() && entry.path().join("level.dat").is_file())
            || (kind == "screenshots" && meta.is_file() && is_image)
            || (kind == "backups" && meta.is_file() && name.ends_with(".zip"))
        {
            result.push(LocalFile {
                name,
                size: meta.len(),
                modified: meta
                    .modified()
                    .ok()
                    .and_then(|t| t.duration_since(std::time::UNIX_EPOCH).ok())
                    .map(|d| d.as_secs())
                    .unwrap_or(0),
            });
        }
    }
    result.sort_by_key(|f| std::cmp::Reverse(f.modified));
    Ok(result)
}

pub fn backup(state: &AppState, profile_id: &str, world: &str) -> Result<String> {
    let guard = state.begin_job("backup")?;
    let _: Profile = state.get("profiles", profile_id)?;
    if security::safe_relative(world)?.components().count() != 1 {
        bail!("Некорректное имя мира");
    }
    let dir = security::within(&state.instance(profile_id)?.join("saves"), world)?;
    if !dir.join("level.dat").is_file() {
        bail!("Мир не найден");
    }
    let mut files = Vec::new();
    visit_files(&dir, &mut files)?;
    let total: u64 = files
        .iter()
        .map(|p| fs::metadata(p).map(|m| m.len()).unwrap_or(0))
        .sum();
    if total > 4 * 1024 * 1024 * 1024 {
        bail!("В этой версии поддерживаются резервные копии миров до 4 ГБ");
    }
    let filename = format!("{world}-{}.zip", chrono::Utc::now().format("%Y%m%d-%H%M%S"));
    let destination = security::within(
        &state.root(),
        &format!("backups/worlds/{profile_id}/{filename}"),
    )?;
    fs::create_dir_all(destination.parent().context("Нет папки backups")?)?;
    if fs2::available_space(destination.parent().unwrap())? < total + 64 * 1024 * 1024 {
        bail!("Недостаточно места для резервной копии");
    }
    let partial = destination.with_extension("zip.part");
    let result: Result<()> = (|| {
        let mut zip = zip::ZipWriter::new(fs::File::create(&partial)?);
        state.progress(|j| {
            j.total_files = files.len();
            j.total_bytes = total;
        });
        for file in files {
            if guard.token.is_cancelled() {
                bail!("Резервное копирование отменено");
            }
            let relative = file
                .strip_prefix(&dir)?
                .to_string_lossy()
                .replace('\\', "/");
            security::safe_relative(&relative)?;
            zip.start_file(
                relative,
                zip::write::SimpleFileOptions::default()
                    .compression_method(zip::CompressionMethod::Deflated),
            )?;
            let size = std::io::copy(&mut fs::File::open(&file)?, &mut zip)?;
            state.progress(|j| {
                j.completed_files += 1;
                j.completed_bytes += size;
            });
        }
        zip.finish()?.sync_all()?;
        fs::rename(&partial, &destination)?;
        Ok(())
    })();
    if result.is_err() {
        let _ = fs::remove_file(&partial);
    }
    result?;
    state.notify("success", "Резервная копия мира создана");
    Ok(filename)
}

pub fn restore(state: &AppState, profile_id: &str, archive_name: &str) -> Result<String> {
    let _guard = state.begin_job("restore")?;
    let _: Profile = state.get("profiles", profile_id)?;
    if security::safe_relative(archive_name)?.components().count() != 1
        || !archive_name.ends_with(".zip")
    {
        bail!("Некорректное имя резервной копии");
    }
    let archive = security::within(
        &state.root(),
        &format!("backups/worlds/{profile_id}/{archive_name}"),
    )?;
    let name = format!("Restored-{}", chrono::Utc::now().format("%Y%m%d-%H%M%S"));
    let target = security::within(&state.instance(profile_id)?.join("saves"), &name)?;
    if target.exists() {
        bail!("Папка восстановления уже существует");
    }
    if let Err(e) = security::extract_zip(&archive, &target, None, false) {
        let _ = fs::remove_dir_all(&target);
        return Err(e);
    }
    if !target.join("level.dat").is_file() {
        let _ = fs::remove_dir_all(&target);
        bail!("В резервной копии нет level.dat");
    }
    state.notify(
        "success",
        "Мир восстановлен в НОВУЮ папку. Существующие миры не изменены.",
    );
    Ok(name)
}

pub fn screenshot(state: &AppState, profile_id: &str, name: &str) -> Result<String> {
    let _: Profile = state.get("profiles", profile_id)?;
    if security::safe_relative(name)?.components().count() != 1 {
        bail!("Некорректное имя скриншота");
    }
    let extension = Path::new(name)
        .extension()
        .and_then(|s| s.to_str())
        .unwrap_or("");
    if !["png", "jpg", "jpeg"].contains(&extension) {
        bail!("Неподдерживаемый формат изображения");
    }
    let path = security::within(&state.instance(profile_id)?.join("screenshots"), name)?;
    if fs::metadata(&path)?.len() > 30 * 1024 * 1024 {
        bail!("Изображение больше 30 МБ. Откройте его в папке.");
    }
    let bytes = fs::read(path)?;
    if !(bytes.starts_with(b"\x89PNG\r\n\x1a\n") || bytes.starts_with(b"\xff\xd8\xff")) {
        bail!("Некорректный файл изображения");
    }
    Ok(format!(
        "data:image/{};base64,{}",
        if extension == "png" { "png" } else { "jpeg" },
        base64::engine::general_purpose::STANDARD.encode(bytes)
    ))
}

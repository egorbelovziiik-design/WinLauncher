use crate::{
    downloads::{self, Hash, Transfer},
    models::{InstalledFile, Profile},
    profiles, security,
    state::AppState,
};
use anyhow::{bail, Context, Result};
use std::{collections::HashSet, fs, path::PathBuf};
use tokio_util::sync::CancellationToken;

fn path(state: &AppState, file: &InstalledFile) -> Result<PathBuf> {
    let folder = profiles::content_folder(&file.kind)?;
    let name = if file.enabled {
        file.filename.clone()
    } else {
        format!("{}.disabled", file.filename)
    };
    security::within(
        &state.instance(&file.profile_id)?,
        &format!("{folder}/{name}"),
    )
}

/// Updating a disabled root project must not silently enable it. A newly
/// requested project cannot silently depend on a disabled dependency either.
pub fn planned_enabled(
    existing: Option<&InstalledFile>,
    required_dependency: bool,
) -> Result<bool> {
    if let Some(file) = existing {
        if required_dependency && !file.enabled {
            bail!("Обязательная зависимость {} отключена. Сначала включите её в списке установленных модов.", file.name);
        }
        Ok(file.enabled)
    } else {
        Ok(true)
    }
}

/// Stage and verify every dependency; install with rollback, then commit SQLite atomically.
pub async fn apply(
    state: &AppState,
    profile: &Profile,
    plans: &[InstalledFile],
    cancel: &CancellationToken,
) -> Result<()> {
    let root = state.instance(&profile.id)?;
    let stage = security::within(&root, &format!(".content-stage-{}", uuid::Uuid::new_v4()))?;
    fs::create_dir_all(&stage)?;
    let existing: Vec<InstalledFile> = state
        .list::<InstalledFile>("installed")?
        .into_iter()
        .filter(|f| f.profile_id == profile.id)
        .collect();
    let mut transfers = Vec::new();
    let mut targets = HashSet::new();
    for (i, file) in plans.iter().enumerate() {
        let dest = path(state, file)?;
        if !targets.insert(dest.to_string_lossy().to_lowercase()) {
            bail!("Две зависимости используют одно имя файла");
        }
        if let Some(old) = existing
            .iter()
            .find(|f| profiles::file_id(f) == profiles::file_id(file))
        {
            let old_path = path(state, old)?;
            if old_path.exists()
                && !downloads::verify(&old_path, &Hash::new(&old.hash_algorithm, &old.sha)?)?
            {
                bail!(
                    "Файл {} изменён пользователем. Сохраните его отдельно перед обновлением.",
                    old.filename
                );
            }
        }
        if dest.exists()
            && !existing.iter().any(|f| {
                path(state, f).ok().as_ref() == Some(&dest)
                    && profiles::file_id(f) == profiles::file_id(file)
            })
        {
            bail!(
                "Файл {} уже существует и не принадлежит этому проекту. Он не будет перезаписан.",
                file.filename
            );
        }
        transfers.push(Transfer {
            url: file.download_url.clone(),
            path: stage.join(format!("{i}.download")),
            hash: Hash::new(&file.hash_algorithm, &file.sha)?,
            size: if file.size == 0 {
                None
            } else {
                Some(file.size)
            },
        });
    }
    state.stage("mods", "Загрузка модов и обязательных зависимостей");
    if let Err(e) = downloads::batch(state, transfers, cancel).await {
        let _ = fs::remove_dir_all(&stage);
        return Err(e);
    }
    let mut backups = Vec::new();
    let mut committed = Vec::new();
    let result: Result<()> = (|| {
        for (i, file) in plans.iter().enumerate() {
            if cancel.is_cancelled() {
                bail!("Операция отменена");
            }
            if let Some(old) = existing
                .iter()
                .find(|f| profiles::file_id(f) == profiles::file_id(file))
            {
                let old_path = path(state, old)?;
                if old_path.exists() {
                    let backup = stage.join(format!("{i}.backup"));
                    fs::rename(&old_path, &backup)?;
                    backups.push((backup, old_path));
                }
            }
            let dest = path(state, file)?;
            fs::create_dir_all(dest.parent().context("Нет папки мода")?)?;
            fs::rename(stage.join(format!("{i}.download")), &dest)?;
            committed.push(dest);
        }
        let mut conn = state.db.lock().unwrap_or_else(|e| e.into_inner());
        let tx = conn.transaction()?;
        for file in plans {
            tx.execute("INSERT INTO installed(id,payload) VALUES(?1,?2) ON CONFLICT(id) DO UPDATE SET payload=excluded.payload", rusqlite::params![profiles::file_id(file), serde_json::to_string(file)?])?;
        }
        tx.commit()?;
        Ok(())
    })();
    if result.is_err() {
        for path in committed.iter().rev() {
            let _ = fs::remove_file(path);
        }
        let mut restored = true;
        for (backup, original) in backups.iter().rev() {
            if fs::rename(backup, original).is_err() {
                restored = false;
            }
        }
        if !restored {
            state.log(
                "error",
                "launcher",
                &format!(
                    "Резервные файлы сохранены для ручного восстановления: {}",
                    stage.display()
                ),
            );
            return result;
        }
    }
    let _ = fs::remove_dir_all(stage);
    result?;
    state.changed();
    Ok(())
}

pub fn toggle(state: &AppState, id: &str) -> Result<()> {
    let _operation = state.begin_job("mods")?;
    let mut file: InstalledFile = state.get("installed", id)?;
    let old = path(state, &file)?;
    if !downloads::verify(&old, &Hash::new(&file.hash_algorithm, &file.sha)?)? {
        bail!("Файл изменён или отсутствует. Операция отменена.");
    }
    file.enabled = !file.enabled;
    let new = path(state, &file)?;
    if new.exists() {
        bail!("Целевое имя уже занято");
    }
    fs::rename(&old, &new)?;
    if let Err(e) = state.put("installed", id, &file) {
        let _ = fs::rename(new, old);
        return Err(e);
    }
    state.changed();
    Ok(())
}
pub fn remove(state: &AppState, id: &str) -> Result<()> {
    let _operation = state.begin_job("mods")?;
    let file: InstalledFile = state.get("installed", id)?;
    let source = path(state, &file)?;
    if source.exists() {
        if !downloads::verify(&source, &Hash::new(&file.hash_algorithm, &file.sha)?)? {
            bail!("Файл был изменён. Удалите его вручную, чтобы не потерять данные.");
        }
        let backup = security::within(
            &state.root(),
            &format!(
                "backups/removed-content/{}/{}",
                uuid::Uuid::new_v4(),
                file.filename
            ),
        )?;
        fs::create_dir_all(backup.parent().context("Нет папки")?)?;
        fs::rename(&source, &backup)?;
        if let Err(e) = state.remove("installed", id) {
            let _ = fs::rename(&backup, &source);
            return Err(e);
        }
    } else {
        state.remove("installed", id)?;
    }
    state.changed();
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn disabled_updates_remain_disabled_and_dependencies_are_explicit() {
        let file = InstalledFile {
            profile_id: "test".into(),
            provider: "modrinth".into(),
            project_id: "test".into(),
            version_id: "v1".into(),
            name: "Unit-test project".into(),
            filename: "test.jar".into(),
            kind: "mod".into(),
            sha: "a".repeat(40),
            hash_algorithm: "sha1".into(),
            enabled: false,
            download_url: "https://cdn.modrinth.com/test.jar".into(),
            size: 0,
        };
        assert!(!planned_enabled(Some(&file), false).unwrap());
        assert!(planned_enabled(Some(&file), true).is_err());
        assert!(planned_enabled(None, true).unwrap());
    }
}

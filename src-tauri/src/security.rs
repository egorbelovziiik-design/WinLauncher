use anyhow::{bail, Context, Result};
use std::{
    fs,
    io::{Read, Write},
    path::{Component, Path, PathBuf},
};
use url::Url;

pub const USER_AGENT: &str =
    "WinLauncher/1.0.1 (https://github.com/egorbelovziiik-design/WinLauncher)";
const HOSTS: &[&str] = &[
    "piston-meta.mojang.com",
    "piston-data.mojang.com",
    "launchermeta.mojang.com",
    "launcher.mojang.com",
    "resources.download.minecraft.net",
    "libraries.minecraft.net",
    "launchercontent.mojang.com",
    "api.adoptium.net",
    "github.com",
    "api.github.com",
    "objects.githubusercontent.com",
    "release-assets.githubusercontent.com",
    "api.modrinth.com",
    "cdn.modrinth.com",
    "api.curseforge.com",
    "edge.forgecdn.net",
    "mediafilez.forgecdn.net",
    "media.forgecdn.net",
    "files.forgecdn.net",
    "meta.fabricmc.net",
    "maven.fabricmc.net",
    "meta.quiltmc.org",
    "maven.quiltmc.org",
    "maven.minecraftforge.net",
    "files.minecraftforge.net",
    "maven.neoforged.net",
    "maven.creeperhost.net",
    "repo.maven.apache.org",
    "repo1.maven.org",
    "maven.legacyfabric.net",
    "login.microsoftonline.com",
    "user.auth.xboxlive.com",
    "xsts.auth.xboxlive.com",
    "api.minecraftservices.com",
];

pub fn check_url(raw: &str) -> Result<Url> {
    let u = Url::parse(raw).context("Некорректный URL")?;
    if u.scheme() != "https"
        || !u.username().is_empty()
        || u.password().is_some()
        || u.port().is_some_and(|p| p != 443)
        || !HOSTS.contains(&u.host_str().unwrap_or(""))
    {
        bail!(
            "Источник загрузки не разрешён: {}",
            u.host_str().unwrap_or("unknown")
        );
    }
    Ok(u)
}

pub fn client() -> Result<reqwest::Client> {
    Ok(reqwest::Client::builder()
        .user_agent(USER_AGENT)
        .connect_timeout(std::time::Duration::from_secs(20))
        .timeout(std::time::Duration::from_secs(1800))
        .redirect(reqwest::redirect::Policy::custom(|attempt| {
            if attempt.previous().len() >= 5 {
                return attempt.error("Слишком много перенаправлений");
            }
            if check_url(attempt.url().as_str()).is_ok() {
                attempt.follow()
            } else {
                attempt.error("Перенаправление на неразрешённый источник")
            }
        }))
        .build()?)
}

/// Validate Windows paths on EVERY OS (including the Linux test runner).
pub fn safe_relative(raw: &str) -> Result<PathBuf> {
    if raw.is_empty() || raw.len() > 800 || raw.contains(['\\', ':', '\0']) || raw.starts_with('/')
    {
        bail!("Небезопасный относительный путь");
    }
    let mut result = PathBuf::new();
    for part in raw.split('/') {
        if part.is_empty()
            || part == "."
            || part == ".."
            || part.ends_with(['.', ' '])
            || part
                .chars()
                .any(|c| c.is_control() || "<>\"|?*".contains(c))
        {
            bail!("Небезопасный путь в архиве");
        }
        let base = part.split('.').next().unwrap_or("").to_ascii_uppercase();
        if ["CON", "PRN", "AUX", "NUL"].contains(&base.as_str())
            || (base.len() == 4
                && (base.starts_with("COM") || base.starts_with("LPT"))
                && matches!(base.as_bytes()[3], b'1'..=b'9'))
        {
            bail!("Зарезервированное Windows-имя");
        }
        result.push(part);
    }
    if result
        .components()
        .any(|c| !matches!(c, Component::Normal(_)))
    {
        bail!("Небезопасный путь");
    }
    Ok(result)
}

pub fn safe_id(id: &str) -> Result<()> {
    if id.is_empty()
        || id.len() > 180
        || !id
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || matches!(c, '-' | '_' | '.' | '+'))
        || id == "."
        || id == ".."
    {
        bail!("Некорректный идентификатор");
    }
    Ok(())
}

pub fn within(root: &Path, relative: &str) -> Result<PathBuf> {
    let rel = safe_relative(relative)?;
    let mut target = root.to_owned();
    // Refuse user-created symlinks/reparse points rather than following them.
    for ancestor in root.ancestors() {
        reject_link(ancestor)?;
    }
    for component in rel.components() {
        target.push(component);
        reject_link(&target)?;
    }
    Ok(target)
}

pub fn reject_link(path: &Path) -> Result<()> {
    if let Ok(meta) = fs::symlink_metadata(path) {
        if meta.file_type().is_symlink() {
            bail!("Символические ссылки запрещены: {}", path.display());
        }
        #[cfg(windows)]
        {
            use std::os::windows::fs::MetadataExt;
            if meta.file_attributes() & 0x400 != 0 {
                bail!("Windows reparse point запрещён: {}", path.display());
            }
        }
    }
    Ok(())
}

/// Bounded, traversal-safe extraction; no links, no overwrite by default.
pub fn extract_zip(
    archive: &Path,
    dest: &Path,
    prefix: Option<&str>,
    overwrite: bool,
) -> Result<usize> {
    fs::create_dir_all(dest)?;
    reject_link(dest)?;
    let mut zip = zip::ZipArchive::new(fs::File::open(archive)?)?;
    if zip.len() > 100_000 {
        bail!("В архиве слишком много файлов");
    }
    let mut total = 0u64;
    let mut count = 0;
    for index in 0..zip.len() {
        let mut entry = zip.by_index(index)?;
        let raw = entry.name().to_owned();
        let normalized = raw.trim_end_matches('/');
        if normalized.is_empty() {
            continue;
        }
        safe_relative(normalized)?;
        if entry.unix_mode().is_some_and(|m| m & 0o170000 == 0o120000) {
            bail!("Ссылки в ZIP запрещены");
        }
        total = total
            .checked_add(entry.size())
            .context("Переполнение размера ZIP")?;
        if total > 4 * 1024 * 1024 * 1024 || entry.size() > 1024 * 1024 * 1024 {
            bail!("Архив превышает безопасный лимит распаковки");
        }
        let rel = if let Some(p) = prefix {
            match normalized.strip_prefix(p) {
                Some(v) if !v.is_empty() => v,
                _ => continue,
            }
        } else {
            normalized
        };
        let target = within(dest, rel)?;
        if entry.is_dir() {
            fs::create_dir_all(target)?;
            continue;
        }
        if target.exists() && !overwrite {
            bail!("Файл уже существует: {}", target.display());
        }
        fs::create_dir_all(target.parent().context("Нет родительской папки")?)?;
        let mut output = fs::OpenOptions::new()
            .write(true)
            .create(true)
            .truncate(true)
            .open(&target)?;
        let written = std::io::copy(
            &mut entry.by_ref().take(1024 * 1024 * 1024 + 1),
            &mut output,
        )?;
        if written != entry.size() {
            bail!("Размер распакованного файла не совпадает");
        }
        output.flush()?;
        count += 1;
    }
    Ok(count)
}

pub fn redact(message: &str, secrets: &[String]) -> String {
    let mut text = message.to_owned();
    for s in secrets {
        if !s.is_empty() {
            text = text.replace(s, "[REDACTED]");
        }
    }
    // Defense in depth for auth-bearing log lines, including mods printing command arguments.
    for marker in [
        "--accessToken",
        "access_token",
        "refresh_token",
        "Authorization:",
        "XBL3.0",
        "identityToken",
        "token:",
        "password",
        "client_secret",
        "api_key",
    ] {
        if text.to_lowercase().contains(&marker.to_lowercase()) {
            return "[Скрыта строка, содержащая данные авторизации]".into();
        }
    }
    text.chars().take(16_384).collect()
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn refuses_windows_traversal_and_devices() {
        for s in [
            "../x",
            "a/../../x",
            "C:/x",
            "a\\..\\x",
            "/x",
            "//x",
            "mods/a.jar:evil",
            "NUL",
            "con.txt",
            "a/COM1.jar",
            "a/./x",
            "a//x",
            "a./x",
            "a/x ",
        ] {
            assert!(safe_relative(s).is_err(), "{s}");
        }
        assert!(safe_relative("mods/sodium-1.2.3.jar").is_ok());
    }
    #[test]
    fn only_trusted_https() {
        for u in [
            "http://cdn.modrinth.com/a",
            "https://cdn.modrinth.com.evil.org/a",
            "https://evil@cdn.modrinth.com/a",
            "file:///tmp/a",
            "https://127.0.0.1/a",
            "https://cdn.modrinth.com:444/a",
        ] {
            assert!(check_url(u).is_err());
        }
        assert!(check_url("https://cdn.modrinth.com/data/a.jar").is_ok());
    }
    #[test]
    fn redacts_secrets() {
        assert_eq!(
            redact("hello abc123", &["abc123".into()]),
            "hello [REDACTED]"
        );
        assert!(!redact("--accessToken very-secret", &[]).contains("very-secret"));
    }
    #[test]
    fn archive_cannot_escape() {
        let temp = tempfile::tempdir().unwrap();
        let archive = temp.path().join("bad.zip");
        let mut writer = zip::ZipWriter::new(fs::File::create(&archive).unwrap());
        writer
            .start_file("../escape.txt", zip::write::SimpleFileOptions::default())
            .unwrap();
        writer.write_all(b"bad").unwrap();
        writer.finish().unwrap();
        assert!(extract_zip(&archive, &temp.path().join("out"), None, false).is_err());
        assert!(!temp.path().join("escape.txt").exists());
    }
}

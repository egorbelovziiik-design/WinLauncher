use std::fs;
use std::io::{Read, Write};
use std::path::{Path, PathBuf};

pub fn read_json<T: serde::de::DeserializeOwned>(path: &Path) -> Option<T> {
    let data = fs::read(path).ok()?;
    serde_json::from_slice(&data).ok()
}

pub fn write_json<T: serde::Serialize>(path: &Path, value: &T) -> Result<(), String> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent).map_err(|e| e.to_string())?;
    }
    let tmp = path.with_extension("json.tmp");
    let data = serde_json::to_vec_pretty(value).map_err(|e| e.to_string())?;
    {
        let mut f = fs::File::create(&tmp).map_err(|e| e.to_string())?;
        f.write_all(&data).map_err(|e| e.to_string())?;
        f.sync_all().map_err(|e| e.to_string())?;
    }
    #[cfg(windows)]
    {
        // std::fs::rename does not replace existing files on Windows.
        if path.exists() {
            let _ = fs::remove_file(path);
        }
    }
    fs::rename(&tmp, path).map_err(|e| e.to_string())?;
    Ok(())
}

pub fn sha1_file(path: &Path) -> Option<String> {
    use sha1::{Digest, Sha1};
    let mut f = fs::File::open(path).ok()?;
    let mut hasher = Sha1::new();
    let mut buf = [0u8; 64 * 1024];
    loop {
        let n = f.read(&mut buf).ok()?;
        if n == 0 {
            break;
        }
        hasher.update(&buf[..n]);
    }
    Some(hex::encode(hasher.finalize()))
}

pub fn sha256_file(path: &Path) -> Option<String> {
    use sha2::{Digest, Sha256};
    let mut f = fs::File::open(path).ok()?;
    let mut hasher = Sha256::new();
    let mut buf = [0u8; 64 * 1024];
    loop {
        let n = f.read(&mut buf).ok()?;
        if n == 0 {
            break;
        }
        hasher.update(&buf[..n]);
    }
    Some(hex::encode(hasher.finalize()))
}

pub fn sha512_file(path: &Path) -> Option<String> {
    use sha2::{Digest, Sha512};
    let mut f = fs::File::open(path).ok()?;
    let mut hasher = Sha512::new();
    let mut buf = [0u8; 64 * 1024];
    loop {
        let n = f.read(&mut buf).ok()?;
        if n == 0 {
            break;
        }
        hasher.update(&buf[..n]);
    }
    Some(hex::encode(hasher.finalize()))
}

pub fn dir_size(path: &Path) -> u64 {
    let mut total = 0;
    if let Ok(rd) = fs::read_dir(path) {
        for entry in rd.flatten() {
            let p = entry.path();
            if p.is_dir() {
                total += dir_size(&p);
            } else if let Ok(md) = entry.metadata() {
                total += md.len();
            }
        }
    }
    total
}

pub fn copy_dir_all(src: &Path, dst: &Path) -> std::io::Result<()> {
    fs::create_dir_all(dst)?;
    for entry in fs::read_dir(src)? {
        let entry = entry?;
        let ty = entry.file_type()?;
        let target = dst.join(entry.file_name());
        if ty.is_dir() {
            copy_dir_all(&entry.path(), &target)?;
        } else if ty.is_file() {
            fs::copy(entry.path(), target)?;
        }
    }
    Ok(())
}

/// Removes a directory if it exists, ignoring "not found".
pub fn remove_dir_if_exists(path: &Path) -> std::io::Result<()> {
    match fs::remove_dir_all(path) {
        Ok(()) => Ok(()),
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => Ok(()),
        Err(e) => Err(e),
    }
}

pub fn which(cmd: &str) -> Option<PathBuf> {
    let path = std::env::var_os("PATH")?;
    for dir in std::env::split_paths(&path) {
        let candidate = dir.join(cmd);
        if candidate.is_file() {
            return Some(candidate);
        }
        #[cfg(windows)]
        {
            let exe = dir.join(format!("{cmd}.exe"));
            if exe.is_file() {
                return Some(exe);
            }
        }
    }
    None
}

#[cfg(windows)]
pub fn hide_console(cmd: &mut std::process::Command) {
    use std::os::windows::process::CommandExt;
    const CREATE_NO_WINDOW: u32 = 0x0800_0000;
    cmd.creation_flags(CREATE_NO_WINDOW);
}

#[cfg(not(windows))]
pub fn hide_console(_cmd: &mut std::process::Command) {}

/// Percent-encodes everything except RFC 3986 unreserved characters.
pub fn url_encode(input: &str) -> String {
    let mut out = String::with_capacity(input.len() * 3);
    for byte in input.as_bytes() {
        match byte {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => out.push(*byte as char),
            _ => out.push_str(&format!("%{byte:02X}")),
        }
    }
    out
}

/// Opens a URL, file or folder with the system default handler.
pub fn open_target(target: &str) -> Result<(), String> {
    use std::process::Command;
    let target = target.trim();
    if target.is_empty() {
        return Err("Nothing to open".into());
    }
    #[cfg(windows)]
    {
        let mut cmd = if target.starts_with("http://") || target.starts_with("https://") {
            let mut c = Command::new("cmd");
            c.args(["/C", "start", "", target]);
            c
        } else {
            let mut c = Command::new("explorer");
            c.arg(target);
            c
        };
        cmd.stdin(std::process::Stdio::null())
            .stdout(std::process::Stdio::null())
            .stderr(std::process::Stdio::null());
        hide_console(&mut cmd);
        cmd.spawn().map_err(|e| e.to_string())?;
        return Ok(());
    }
    #[cfg(target_os = "macos")]
    {
        Command::new("open")
            .arg(target)
            .spawn()
            .map_err(|e| e.to_string())?;
        return Ok(());
    }
    #[cfg(all(unix, not(target_os = "macos")))]
    {
        Command::new("xdg-open")
            .arg(target)
            .spawn()
            .map_err(|e| e.to_string())?;
        return Ok(());
    }
    #[allow(unreachable_code)]
    Err("Opening files is not supported on this platform".into())
}

/// Video adapter name + driver version + date. Used in crash diagnostics: an
/// outdated GPU driver is the most common reason for a JVM fatal error in
/// Minecraft.
pub fn gpu_summary() -> Vec<String> {
    #[cfg(windows)]
    {
        let mut cmd = std::process::Command::new("powershell");
        cmd.args([
            "-NoProfile",
            "-NonInteractive",
            "-Command",
            "Get-CimInstance Win32_VideoController | ForEach-Object { \"$($_.Name) | driver $($_.DriverVersion) | $($_.DriverDate)\" }",
        ]);
        hide_console(&mut cmd);
        if let Ok(output) = cmd.output() {
            let text = String::from_utf8_lossy(&output.stdout).to_string();
            let lines: Vec<String> = text
                .lines()
                .map(|line| line.trim().to_string())
                .filter(|line| !line.is_empty())
                .collect();
            if !lines.is_empty() {
                return lines;
            }
        }
        Vec::new()
    }
    #[cfg(not(windows))]
    {
        Vec::new()
    }
}

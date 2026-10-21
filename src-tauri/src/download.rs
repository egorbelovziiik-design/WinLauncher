use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::Arc;
use std::time::{Duration, Instant};

use futures_util::{stream, StreamExt};
use serde::de::DeserializeOwned;
use tauri::{AppHandle, Manager};
use tokio::io::AsyncWriteExt;

use crate::models::DownloadItem;
use crate::state::{lock, AppState};
use crate::util;

/// A single file that has to end up on disk, optionally verified by SHA-1.
#[derive(Debug, Clone)]
pub struct FileSpec {
    pub url: String,
    pub dest: PathBuf,
    pub sha1: Option<String>,
    pub size: u64,
    pub label: String,
}

impl FileSpec {
    pub fn new(url: impl Into<String>, dest: PathBuf) -> Self {
        Self { url: url.into(), dest, sha1: None, size: 0, label: String::new() }
    }

    pub fn hash(mut self, sha1: Option<String>) -> Self {
        self.sha1 = sha1;
        self
    }

    pub fn size(mut self, size: u64) -> Self {
        self.size = size;
        self
    }

    pub fn label(&self) -> String {
        if !self.label.is_empty() {
            return self.label.clone();
        }
        self.dest
            .file_name()
            .map(|n| n.to_string_lossy().to_string())
            .unwrap_or_else(|| self.url.clone())
    }

    fn part_path(&self) -> PathBuf {
        let mut name = self.dest.file_name().map(|n| n.to_string_lossy().to_string()).unwrap_or_default();
        name.push_str(".part");
        self.dest.with_file_name(name)
    }
}

/* ------------------------------------------------------------------ */
/* Small HTTP helpers with retries                                     */
/* ------------------------------------------------------------------ */

pub fn http_client() -> reqwest::Client {
    reqwest::Client::builder()
        .user_agent(crate::state::USER_AGENT)
        .connect_timeout(Duration::from_secs(20))
        .redirect(reqwest::redirect::Policy::limited(10))
        .build()
        .expect("http client")
}

pub async fn get_text(client: &reqwest::Client, url: &str, retries: usize) -> Result<String, String> {
    let mut last = String::new();
    for attempt in 0..=retries {
        match client.get(url).timeout(Duration::from_secs(40)).send().await {
            Ok(res) if res.status().is_success() => match res.text().await {
                Ok(t) => return Ok(t),
                Err(e) => last = e.to_string(),
            },
            Ok(res) => last = format!("HTTP {} for {url}", res.status()),
            Err(e) => last = e.to_string(),
        }
        if attempt < retries {
            tokio::time::sleep(Duration::from_millis(400 * (attempt as u64 + 1))).await;
        }
    }
    Err(last)
}

pub async fn get_json_value(
    client: &reqwest::Client,
    url: &str,
    retries: usize,
) -> Result<serde_json::Value, String> {
    let text = get_text(client, url, retries).await?;
    serde_json::from_str(&text).map_err(|e| format!("Invalid JSON from {url}: {e}"))
}

pub async fn get_json<T: DeserializeOwned>(
    client: &reqwest::Client,
    url: &str,
    retries: usize,
) -> Result<T, String> {
    let text = get_text(client, url, retries).await?;
    serde_json::from_str(&text).map_err(|e| format!("Invalid JSON from {url}: {e}"))
}

pub async fn get_bytes(client: &reqwest::Client, url: &str, retries: usize) -> Result<Vec<u8>, String> {
    let mut last = String::new();
    for attempt in 0..=retries {
        match client.get(url).timeout(Duration::from_secs(90)).send().await {
            Ok(res) if res.status().is_success() => match res.bytes().await {
                Ok(b) => return Ok(b.to_vec()),
                Err(e) => last = e.to_string(),
            },
            Ok(res) => last = format!("HTTP {} for {url}", res.status()),
            Err(e) => last = e.to_string(),
        }
        if attempt < retries {
            tokio::time::sleep(Duration::from_millis(400 * (attempt as u64 + 1))).await;
        }
    }
    Err(last)
}

/* ------------------------------------------------------------------ */
/* Download engine                                                     */
/* ------------------------------------------------------------------ */

struct Progress {
    current: AtomicU64,
    total: AtomicU64,
    bytes: AtomicU64,
    total_bytes: AtomicU64,
    done: AtomicBool,
    failed: AtomicBool,
}

/// Verifies a file that is already on disk.
pub fn verified(path: &Path, sha1: Option<&str>, size: u64) -> bool {
    let Ok(meta) = std::fs::metadata(path) else { return false };
    if !meta.is_file() {
        return false;
    }
    if size > 0 && meta.len() != size {
        return false;
    }
    match sha1.filter(|s| !s.is_empty()) {
        // A matching size is enough for the fast path; downloads are always hashed.
        Some(_) if size > 0 => true,
        Some(expected) => util::sha1_file(path)
            .map(|actual| actual.eq_ignore_ascii_case(expected))
            .unwrap_or(false),
        None => true,
    }
}

pub async fn download_files(
    app: AppHandle,
    title: String,
    profile_id: Option<String>,
    files: Vec<FileSpec>,
) -> Result<(), String> {
    let state = app.state::<AppState>();
    let retry_files = files.clone();
    let mut pending: Vec<FileSpec> = Vec::new();
    let mut skipped = 0u64;
    for file in files.into_iter() {
        if verified(&file.dest, file.sha1.as_deref(), file.size) {
            skipped += 1;
        } else {
            pending.push(file);
        }
    }
    let total = skipped + pending.len() as u64;
    let total_bytes: u64 = pending.iter().map(|f| if f.size > 0 { f.size } else { 0 }).sum();

    let id = uuid::Uuid::new_v4().to_string();
    let mut item = DownloadItem {
        id: id.clone(),
        title: title.clone(),
        detail: String::new(),
        status: if pending.is_empty() { "done".into() } else { "running".into() },
        current: skipped,
        total,
        bytes: 0,
        total_bytes,
        speed: 0.0,
        started_at: chrono::Utc::now().timestamp_millis(),
        finished_at: if pending.is_empty() { Some(chrono::Utc::now().timestamp_millis()) } else { None },
        error: None,
        profile_id: profile_id.clone(),
    };
    item.detail = format!("{} / {}", skipped, total);
    app.state::<AppState>().add_download(item);
    if pending.is_empty() {
        return Ok(());
    }
    lock(&state.retry).insert(
        id.clone(),
        crate::state::RetryJob { title: title.clone(), profile_id: profile_id.clone(), files: retry_files },
    );

    let progress = Arc::new(Progress {
        current: AtomicU64::new(skipped),
        total: AtomicU64::new(total),
        bytes: AtomicU64::new(0),
        total_bytes: AtomicU64::new(total_bytes),
        done: AtomicBool::new(false),
        failed: AtomicBool::new(false),
    });
    let cancel = Arc::new(AtomicBool::new(false));
    lock(&state.cancel).insert(id.clone(), cancel.clone());

    // Progress ticker -----------------------------------------------------
    let progress_ticker = progress.clone();
    let cancel_ticker = cancel.clone();
    let app_ticker = app.clone();
    let id_ticker = id.clone();
    let title_ticker = title.clone();
    let ticker = tokio::spawn(async move {
        let mut last_bytes = 0u64;
        loop {
            tokio::time::sleep(Duration::from_millis(400)).await;
            let st = app_ticker.state::<AppState>();
            let bytes_now = progress_ticker.bytes.load(Ordering::Relaxed);
            let current = progress_ticker.current.load(Ordering::Relaxed);
            let total = progress_ticker.total.load(Ordering::Relaxed);
            let total_bytes = progress_ticker.total_bytes.load(Ordering::Relaxed);
            let speed = (bytes_now.saturating_sub(last_bytes)) as f64 / 0.4;
            last_bytes = bytes_now;
            st.update_download(&id_ticker, |d| {
                d.current = current;
                d.total = total;
                d.bytes = bytes_now;
                d.total_bytes = total_bytes;
                d.speed = speed;
                d.detail = format!("{current} / {total}");
            });
            st.emit_downloads();
            if profile_id.is_some() {
                st.emit_progress(&title_ticker, current, total, bytes_now, total_bytes);
            }
            if progress_ticker.done.load(Ordering::Relaxed) || cancel_ticker.load(Ordering::Relaxed) {
                break;
            }
        }
    });

    let settings = state.settings();
    let concurrency = settings.max_concurrent_downloads.clamp(1, 32);
    let limit = settings.speed_limit_kbps;

    let results: Vec<Result<(), String>> = stream::iter(pending.into_iter())
        .map(|file| {
            let app = app.clone();
            let progress = progress.clone();
            let cancel = cancel.clone();
            async move { download_one(&app, &file, &progress, &cancel, limit).await }
        })
        .buffer_unordered(concurrency)
        .collect()
        .await;

    progress.done.store(true, Ordering::Relaxed);
    let _ = ticker.await;
    lock(&state.cancel).remove(&id);

    let cancelled = cancel.load(Ordering::Relaxed);
    let first_error = results.into_iter().find_map(|r| r.err());

    let st = app.state::<AppState>();
    if cancelled {
        st.update_download(&id, |d| {
            d.status = "cancelled".into();
            d.finished_at = Some(chrono::Utc::now().timestamp_millis());
            d.speed = 0.0;
        });
        st.emit_downloads();
        return Err("cancelled".into());
    }
    if let Some(err) = first_error {
        progress.failed.store(true, Ordering::Relaxed);
        st.finish_download(&id, Some(err.clone()));
        return Err(err);
    }
    st.finish_download(&id, None);
    lock(&st.retry).remove(&id);
    Ok(())
}

async fn download_one(
    app: &AppHandle,
    file: &FileSpec,
    progress: &Arc<Progress>,
    cancel: &Arc<AtomicBool>,
    limit_kbps: u64,
) -> Result<(), String> {
    if verified(&file.dest, file.sha1.as_deref(), file.size) {
        progress.current.fetch_add(1, Ordering::Relaxed);
        return Ok(());
    }
    if let Some(parent) = file.dest.parent() {
        tokio::fs::create_dir_all(parent)
            .await
            .map_err(|e| format!("Could not create {}: {e}", parent.display()))?;
    }

    let mut last_error = String::new();
    for attempt in 0..3u32 {
        if cancel.load(Ordering::Relaxed) {
            return Err("cancelled".into());
        }
        match fetch_file(app, file, progress, cancel, limit_kbps).await {
            Ok(()) => {
                progress.current.fetch_add(1, Ordering::Relaxed);
                return Ok(());
            }
            Err(e) if e == "cancelled" => return Err(e),
            Err(e) => {
                last_error = e;
                let _ = tokio::fs::remove_file(file.part_path()).await;
                if attempt < 2 {
                    tokio::time::sleep(Duration::from_millis(600 * (attempt as u64 + 1))).await;
                }
            }
        }
    }
    Err(format!("{}: {last_error}", file.label()))
}

async fn fetch_file(
    app: &AppHandle,
    file: &FileSpec,
    progress: &Arc<Progress>,
    cancel: &Arc<AtomicBool>,
    limit_kbps: u64,
) -> Result<(), String> {
    let state = app.state::<AppState>();
    let client = state.http();
    let part = file.part_path();
    let mut existing: u64 = tokio::fs::metadata(&part).await.map(|m| m.len()).unwrap_or(0);
    if file.size > 0 && existing > file.size {
        let _ = tokio::fs::remove_file(&part).await;
        existing = 0;
    }

    let mut request = client.get(&file.url).timeout(Duration::from_secs(600));
    if existing > 0 {
        request = request.header(reqwest::header::RANGE, format!("bytes={existing}-"));
    }
    let res = request.send().await.map_err(|e| e.to_string())?;
    let status = res.status();
    if status == reqwest::StatusCode::RANGE_NOT_SATISFIABLE {
        let _ = tokio::fs::remove_file(&part).await;
        return Err("Range request rejected".into());
    }
    if !status.is_success() {
        return Err(format!("HTTP {status}"));
    }
    let resumed = status == reqwest::StatusCode::PARTIAL_CONTENT && existing > 0;
    if !resumed {
        existing = 0;
        let _ = tokio::fs::remove_file(&part).await;
    }
    if progress.bytes.load(Ordering::Relaxed) == 0 {
        if let Some(len) = res.content_length() {
            let total = progress.total_bytes.load(Ordering::Relaxed);
            if total == 0 {
                progress.total_bytes.store(len, Ordering::Relaxed);
            }
        }
    }

    let mut out = tokio::fs::OpenOptions::new()
        .create(true)
        .append(true)
        .open(&part)
        .await
        .map_err(|e| e.to_string())?;
    let mut stream = res.bytes_stream();
    let mut window_start = Instant::now();
    let mut window_bytes = 0u64;
    let limit_bytes = limit_kbps.saturating_mul(1024);

    while let Some(chunk) = stream.next().await {
        if cancel.load(Ordering::Relaxed) {
            drop(out);
            return Err("cancelled".into());
        }
        let chunk = chunk.map_err(|e| e.to_string())?;
        out.write_all(&chunk).await.map_err(|e| e.to_string())?;
        progress.bytes.fetch_add(chunk.len() as u64, Ordering::Relaxed);
        window_bytes += chunk.len() as u64;
        if limit_bytes > 0 && window_bytes >= limit_bytes {
            let target = Duration::from_secs_f64(window_bytes as f64 / limit_bytes as f64);
            if let Some(remaining) = target.checked_sub(window_start.elapsed()) {
                tokio::time::sleep(remaining).await;
            }
            window_start = Instant::now();
            window_bytes = 0;
        }
    }
    out.flush().await.map_err(|e| e.to_string())?;
    drop(out);

    if let Some(expected) = file.sha1.as_deref().filter(|s| !s.is_empty()) {
        let actual = util::sha1_file(&part).unwrap_or_default();
        if !actual.eq_ignore_ascii_case(expected) {
            let _ = tokio::fs::remove_file(&part).await;
            return Err("SHA1 mismatch".into());
        }
    } else if file.size > 0 {
        let len = tokio::fs::metadata(&part).await.map(|m| m.len()).unwrap_or(0);
        if len != file.size {
            let _ = tokio::fs::remove_file(&part).await;
            return Err(format!("size mismatch (expected {}, got {len})", file.size));
        }
    }

    if file.dest.exists() {
        let _ = tokio::fs::remove_file(&file.dest).await;
    }
    tokio::fs::rename(&part, &file.dest)
        .await
        .map_err(|e| format!("Could not move {}: {e}", file.dest.display()))?;
    Ok(())
}

/* ------------------------------------------------------------------ */
/* ZIP helpers (path-traversal safe)                                   */
/* ------------------------------------------------------------------ */

pub fn extract_zip_filtered(
    zip_path: &Path,
    dest: &Path,
    prefix: &str,
    skip: Option<&dyn Fn(&str) -> bool>,
) -> Result<(), String> {
    let file = std::fs::File::open(zip_path).map_err(|e| e.to_string())?;
    let mut archive = zip::ZipArchive::new(file).map_err(|e| e.to_string())?;
    std::fs::create_dir_all(dest).map_err(|e| e.to_string())?;
    for i in 0..archive.len() {
        let mut entry = archive.by_index(i).map_err(|e| e.to_string())?;
        let raw = entry.name().to_string();
        let rel = if prefix.is_empty() {
            raw.clone()
        } else {
            match raw.strip_prefix(prefix) {
                Some(rest) => rest.to_string(),
                None => continue,
            }
        };
        if rel.is_empty() {
            continue;
        }
        let is_dir = raw.ends_with('/') || (entry.size() == 0 && !rel.contains('.'));
        if let Some(skip) = skip {
            if skip(&rel) {
                continue;
            }
        }
        let target = match crate::paths::safe_join(dest, &rel) {
            Ok(p) => p,
            Err(_) => continue,
        };
        if is_dir {
            std::fs::create_dir_all(&target).map_err(|e| e.to_string())?;
            continue;
        }
        if let Some(parent) = target.parent() {
            std::fs::create_dir_all(parent).map_err(|e| e.to_string())?;
        }
        let mut out = std::fs::File::create(&target).map_err(|e| e.to_string())?;
        std::io::copy(&mut entry, &mut out).map_err(|e| e.to_string())?;
    }
    Ok(())
}

pub fn extract_zip(zip_path: &Path, dest: &Path) -> Result<(), String> {
    extract_zip_filtered(zip_path, dest, "", None)
}

/// Extracts one directory from a zip while stripping the prefix.
pub fn extract_zip_subdir(
    zip_path: &Path,
    dir_prefix: &str,
    dest: &Path,
    skip: Option<&dyn Fn(&str) -> bool>,
) -> Result<(), String> {
    let prefix = if dir_prefix.ends_with('/') {
        dir_prefix.to_string()
    } else {
        format!("{dir_prefix}/")
    };
    extract_zip_filtered(zip_path, dest, &prefix, skip)
}

/// Reads one entry of a zip as UTF-8 text.
pub fn read_zip_entry(zip_path: &Path, name: &str) -> Result<String, String> {
    use std::io::Read;
    let file = std::fs::File::open(zip_path).map_err(|e| e.to_string())?;
    let mut archive = zip::ZipArchive::new(file).map_err(|e| e.to_string())?;
    let mut entry = archive.by_name(name).map_err(|e| e.to_string())?;
    let mut text = String::new();
    entry.read_to_string(&mut text).map_err(|e| e.to_string())?;
    Ok(text)
}

/// Lists entry names in a zip.
pub fn zip_entries(zip_path: &Path) -> Result<Vec<String>, String> {
    let file = std::fs::File::open(zip_path).map_err(|e| e.to_string())?;
    let mut archive = zip::ZipArchive::new(file).map_err(|e| e.to_string())?;
    let mut out = Vec::with_capacity(archive.len());
    for i in 0..archive.len() {
        let entry = archive.by_index(i).map_err(|e| e.to_string())?;
        out.push(entry.name().to_string());
    }
    Ok(out)
}

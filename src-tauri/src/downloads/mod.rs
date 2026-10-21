use crate::{security, state::AppState};
use anyhow::{bail, Context, Result};
use futures_util::{stream, StreamExt};
use serde::de::DeserializeOwned;
use sha1::Digest;
use std::{fs, io::Read, path::{Path, PathBuf}, time::{Duration, Instant}};
use tokio::io::AsyncWriteExt;
use tokio_util::sync::CancellationToken;

#[derive(Clone, Debug)]
pub struct Hash { pub algorithm: String, pub value: String }
impl Hash {
    pub fn new(algorithm: &str, value: &str) -> Result<Self> {
        let length = match algorithm { "sha1" => 40, "sha256" => 64, "sha512" => 128, _ => bail!("Неподдерживаемый хеш") };
        if value.len() != length || !value.chars().all(|c| c.is_ascii_hexdigit()) { bail!("Некорректная контрольная сумма {algorithm}"); }
        Ok(Self { algorithm: algorithm.into(), value: value.to_ascii_lowercase() })
    }
}
#[derive(Clone, Debug)] pub struct Transfer { pub url: String, pub path: PathBuf, pub hash: Hash, pub size: Option<u64> }

pub fn hash_file(path: &Path, algorithm: &str) -> Result<String> {
    let mut file = fs::File::open(path)?;
    let mut buf = vec![0; 128 * 1024];
    macro_rules! digest { ($kind:ty) => {{ let mut h = <$kind>::new(); loop { let n = file.read(&mut buf)?; if n == 0 { break; } h.update(&buf[..n]); } hex::encode(h.finalize()) }}; }
    Ok(match algorithm { "sha1" => digest!(sha1::Sha1), "sha256" => digest!(sha2::Sha256), "sha512" => digest!(sha2::Sha512), _ => bail!("Неподдерживаемый хеш") })
}
pub fn verify(path: &Path, hash: &Hash) -> Result<bool> { if !path.is_file() { return Ok(false); } security::reject_link(path)?; Ok(hash_file(path, &hash.algorithm)? == hash.value) }

pub async fn bytes(state: &AppState, url: &str, limit: usize) -> Result<Vec<u8>> {
    security::check_url(url)?;
    let response = state.client.get(url).send().await?.error_for_status()?;
    bounded_response(response, limit).await
}
pub async fn bounded_response(response: reqwest::Response, limit: usize) -> Result<Vec<u8>> {
    if response.content_length().is_some_and(|n| n > limit as u64) { bail!("Ответ API слишком большой"); }
    let mut stream = response.bytes_stream(); let mut result = Vec::new();
    while let Some(chunk) = stream.next().await { let chunk = chunk?; if result.len() + chunk.len() > limit { bail!("Превышен лимит ответа API"); } result.extend_from_slice(&chunk); }
    Ok(result)
}
pub async fn json<T: DeserializeOwned>(state: &AppState, url: &str) -> Result<T> { Ok(serde_json::from_slice(&bytes(state, url, 32 * 1024 * 1024).await?)?) }
pub async fn text(state: &AppState, url: &str) -> Result<String> { Ok(String::from_utf8(bytes(state, url, 16 * 1024 * 1024).await?)?) }

pub async fn download(state: &AppState, transfer: &Transfer, cancel: &CancellationToken) -> Result<()> {
    security::check_url(&transfer.url)?;
    for ancestor in transfer.path.ancestors() { security::reject_link(ancestor)?; }
    let p = transfer.path.clone(); let h = transfer.hash.clone();
    if tokio::task::spawn_blocking(move || verify(&p, &h)).await?? {
        let n = fs::metadata(&transfer.path)?.len();
        state.progress(|j| { j.completed_bytes += n; j.completed_files += 1; });
        return Ok(());
    }
    if cancel.is_cancelled() { bail!("Операция отменена"); }
    let parent = transfer.path.parent().context("Путь загрузки без родительской папки")?;
    tokio::fs::create_dir_all(parent).await?;
    let part = parent.join(format!(".{}.part", uuid::Uuid::new_v4()));
    let mut last_error = None;
    for attempt in 0..3 {
        let result = transfer_once(state, transfer, &part, cancel).await;
        match result {
            Ok(()) => {
                let dest = transfer.path.clone(); let temp = part.clone();
                tokio::task::spawn_blocking(move || atomic_replace(&temp, &dest)).await??;
                state.progress(|j| j.completed_files += 1);
                return Ok(());
            }
            Err(error) => {
                let _ = tokio::fs::remove_file(&part).await;
                last_error = Some(error);
                if cancel.is_cancelled() { break; }
                if attempt < 2 { tokio::time::sleep(Duration::from_millis(700 * (attempt + 1))).await; }
            }
        }
    }
    Err(last_error.context("Не удалось загрузить файл")?)
}

async fn transfer_once(state: &AppState, transfer: &Transfer, part: &Path, cancel: &CancellationToken) -> Result<()> {
    let response = tokio::select! {
        _ = cancel.cancelled() => bail!("Операция отменена"),
        r = state.client.get(&transfer.url).send() => r?.error_for_status()?,
    };
    let expected = transfer.size.or(response.content_length());
    if expected.is_some_and(|n| n > 4 * 1024 * 1024 * 1024) { bail!("Слишком большой файл (лимит 4 ГБ)"); }
    if fs2::available_space(part.parent().context("Нет папки")?)? < expected.unwrap_or(0) + 128 * 1024 * 1024 { bail!("Недостаточно свободного места"); }
    let mut file = tokio::fs::File::create(part).await?;
    let mut body = response.bytes_stream();
    let start = Instant::now(); let mut last_emit = Instant::now();
    let mut received = 0u64; let mut emitted = 0u64;
    let config = state.config();
    let limit = config.download_limit_kib * 1024 / config.download_concurrency.max(1) as u64;
    let result: Result<()> = async {
        loop {
            let next = tokio::select! { _ = cancel.cancelled() => bail!("Операция отменена"), r = body.next() => r };
            let Some(chunk) = next else { break; };
            let chunk = chunk?;
            received += chunk.len() as u64;
            if received > 4 * 1024 * 1024 * 1024 { bail!("Превышен безопасный размер файла"); }
            file.write_all(&chunk).await?;
            if limit > 0 {
                let elapsed = Duration::from_secs_f64(received as f64 / limit as f64);
                if elapsed > start.elapsed() { tokio::select! { _ = cancel.cancelled() => bail!("Операция отменена"), _ = tokio::time::sleep(elapsed - start.elapsed()) => {} } }
            }
            if last_emit.elapsed() > Duration::from_millis(200) {
                let delta = received - emitted; emitted = received; last_emit = Instant::now();
                state.progress(|j| j.completed_bytes += delta);
            }
        }
        file.flush().await?; file.sync_all().await?;
        if transfer.size.is_some_and(|n| n != received) { bail!("Размер файла не совпадает с метаданными"); }
        let p = part.to_owned(); let h = transfer.hash.clone();
        if !tokio::task::spawn_blocking(move || verify(&p, &h)).await?? { bail!("Контрольная сумма не совпадает. Файл не будет установлен"); }
        state.progress(|j| j.completed_bytes += received - emitted);
        Ok(())
    }.await;
    if result.is_err() { state.progress(|j| j.completed_bytes = j.completed_bytes.saturating_sub(emitted)); }
    result
}

pub fn atomic_replace(source: &Path, destination: &Path) -> Result<()> {
    security::reject_link(destination)?;
    let backup = destination.with_extension(format!("{}.old", uuid::Uuid::new_v4()));
    let existed = destination.exists();
    if existed { fs::rename(destination, &backup)?; }
    if let Err(e) = fs::rename(source, destination) {
        if existed { let _ = fs::rename(&backup, destination); }
        return Err(e.into());
    }
    if existed { fs::remove_file(backup)?; }
    Ok(())
}

pub async fn batch(state: &AppState, transfers: Vec<Transfer>, cancel: &CancellationToken) -> Result<()> {
    let total = transfers.iter().map(|t| t.size.unwrap_or(0)).sum(); let files = transfers.len();
    state.progress(|j| { j.total_bytes = total; j.total_files = files; j.completed_files = 0; j.completed_bytes = 0; });
    // All futures are drained after an error so partial files are cleaned up.
    let errors = stream::iter(transfers.into_iter().map(|t| { let s = state.clone(); let c = cancel.clone(); async move { let r = download(&s, &t, &c).await; if r.is_err() { c.cancel(); } r } }))
        .buffer_unordered(state.config().download_concurrency.clamp(1, 16)).collect::<Vec<_>>().await;
    for result in errors { result?; }
    Ok(())
}

#[cfg(test)] mod tests {
    use super::*;
    #[test] fn validates_hash_format() { assert!(Hash::new("sha1", "z").is_err()); assert!(Hash::new("md5", "a").is_err()); assert!(Hash::new("sha1", &"a".repeat(40)).is_ok()); }
    #[test] fn verifies_real_bytes() { let d = tempfile::tempdir().unwrap(); let p = d.path().join("a"); fs::write(&p, b"hello").unwrap(); let h = Hash::new("sha1", "aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d").unwrap(); assert!(verify(&p, &h).unwrap()); fs::write(&p, b"bad").unwrap(); assert!(!verify(&p, &h).unwrap()); }
    #[test] fn replacement_is_atomic_and_preserves_new_contents() { let d = tempfile::tempdir().unwrap(); let p = d.path().join("a"); let t = d.path().join("tmp"); fs::write(&p, "old").unwrap(); fs::write(&t, "new").unwrap(); atomic_replace(&t, &p).unwrap(); assert_eq!(fs::read_to_string(p).unwrap(), "new"); }
}

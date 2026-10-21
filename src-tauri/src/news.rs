use serde_json::Value;
use tauri::{AppHandle, Manager};

use crate::download;
use crate::models::NewsItem;
use crate::mojang;
use crate::state::AppState;

const NEWS_URL: &str = "https://launchercontent.mojang.com/news.json";
const CONTENT_ROOT: &str = "https://launchercontent.mojang.com";

pub async fn list(app: &AppHandle) -> Vec<NewsItem> {
    let state = app.state::<AppState>();
    match fetch_remote(app).await {
        Ok(items) if !items.is_empty() => items,
        _ => {
            state.log("warn", "launcher", "News feed unavailable — showing version data instead");
            fallback(app).await
        }
    }
}

async fn fetch_remote(app: &AppHandle) -> Result<Vec<NewsItem>, String> {
    let state = app.state::<AppState>();
    let value: Value = download::get_json_value(&state.http(), NEWS_URL, 1).await?;
    let mut out = Vec::new();
    if let Some(entries) = value.get("entries").and_then(|e| e.as_array()) {
        for (index, entry) in entries.iter().enumerate().take(12) {
            let title = entry.get("title").and_then(|v| v.as_str()).unwrap_or_default().to_string();
            if title.is_empty() {
                continue;
            }
            let image = entry
                .get("imageURL")
                .or_else(|| entry.get("image"))
                .and_then(|v| v.as_str())
                .map(|s| {
                    if s.starts_with("http") {
                        s.to_string()
                    } else {
                        format!("{CONTENT_ROOT}/{s}")
                    }
                })
                .unwrap_or_default();
            out.push(NewsItem {
                id: entry
                    .get("id")
                    .and_then(|v| v.as_str())
                    .map(|s| s.to_string())
                    .unwrap_or_else(|| format!("news-{index}")),
                title,
                text: entry
                    .get("text")
                    .or_else(|| entry.get("body"))
                    .and_then(|v| v.as_str())
                    .unwrap_or_default()
                    .to_string(),
                image,
                category: entry
                    .get("category")
                    .or_else(|| entry.get("tag"))
                    .and_then(|v| v.as_str())
                    .unwrap_or("news")
                    .to_string(),
                date: entry
                    .get("date")
                    .and_then(|v| v.as_str())
                    .unwrap_or_default()
                    .to_string(),
                url: entry
                    .get("readMoreLink")
                    .or_else(|| entry.get("url"))
                    .and_then(|v| v.as_str())
                    .unwrap_or("https://www.minecraft.net/en-us/article")
                    .to_string(),
            });
        }
    }
    Ok(out)
}

/// Builds honest items from the real version manifest when the news feed is unreachable.
async fn fallback(app: &AppHandle) -> Vec<NewsItem> {
    let state = app.state::<AppState>();
    let mut out = Vec::new();
    if let Ok(manifest) = mojang::fetch_manifest(app, false).await {
        let mut latest = manifest.versions.iter().filter(|v| v.version_type == "release");
        for entry in latest.by_ref().take(6) {
            out.push(NewsItem {
                id: entry.id.clone(),
                title: format!("Minecraft {} is available", entry.id),
                text: format!(
                    "A new {} build was published on {}. Install it from the Versions page.",
                    entry.version_type,
                    entry.release_time.chars().take(10).collect::<String>()
                ),
                image: String::new(),
                category: entry.version_type.clone(),
                date: entry.release_time.clone(),
                url: format!("https://www.minecraft.net/en-us/article/minecraft-{}", entry.id.replace('.', "-")),
            });
            if out.len() >= 6 {
                break;
            }
        }
    }
    if out.is_empty() {
        state.log("warn", "launcher", "No news data available (offline)");
    }
    out
}

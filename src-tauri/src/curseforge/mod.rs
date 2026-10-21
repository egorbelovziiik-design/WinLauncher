use crate::{accounts::vault, downloads, models::{InstalledFile, Loader, Profile, ProjectDetail, SearchProject, SearchResults}, profiles, security, state::AppState};
use anyhow::{bail, Context, Result};
use serde_json::Value;
use std::collections::{HashSet, VecDeque};
use tokio_util::sync::CancellationToken;
const API: &str = "https://api.curseforge.com/v1";

pub async fn request(state: &AppState, path: &str, params: &[(&str, String)]) -> Result<Value> {
    let key = vault::get("curseforge-api-key").context("Подключите CurseForge: сохраните API-ключ в настройках интеграций")?;
    if key.trim().is_empty() { bail!("Не указан API-ключ CurseForge"); }
    let response = state.client.get(format!("{API}{path}")).header("x-api-key", key).query(params).send().await?;
    if response.status() == reqwest::StatusCode::FORBIDDEN || response.status() == reqwest::StatusCode::UNAUTHORIZED { bail!("CurseForge отклонил ключ. Проверьте разрешения API."); }
    let response = response.error_for_status()?;
    Ok(serde_json::from_slice(&downloads::bounded_response(response, 16 * 1024 * 1024).await?)?)
}
fn id(raw: &str) -> Result<u64> { let id = raw.parse::<u64>().context("Некорректный CurseForge ID")?; if id == 0 { bail!("Некорректный CurseForge ID"); } Ok(id) }
fn loader(loader: &Loader) -> u8 { match loader { Loader::Forge => 1, Loader::Fabric => 4, Loader::Quilt => 5, Loader::Neoforge => 6, Loader::Vanilla => 0 } }
fn project(v: &Value) -> SearchProject {
    let kind = match v["classId"].as_u64() { Some(4471) => "modpack", Some(12) => "resourcepack", Some(6552) => "shader", _ => "mod" };
    SearchProject { id: v["id"].as_u64().unwrap_or(0).to_string(), provider: "curseforge".into(), title: v["name"].as_str().unwrap_or_default().into(), description: v["summary"].as_str().unwrap_or_default().into(), icon_url: v["logo"]["thumbnailUrl"].as_str().map(str::to_owned), downloads: v["downloadCount"].as_f64().unwrap_or(0.0) as u64, author: v["authors"][0]["name"].as_str().unwrap_or("CurseForge").into(), categories: v["categories"].as_array().map(|a| a.iter().filter_map(|v| v["name"].as_str().map(str::to_owned)).collect()).unwrap_or_default(), project_type: kind.into(), slug: v["slug"].as_str().unwrap_or_default().into() }
}
pub async fn search(state: &AppState, query: &str, kind: &str, profile: Option<&Profile>, offset: u64, sort: &str) -> Result<SearchResults> {
    let class = match kind { "mod" => 6, "modpack" => 4471, "resourcepack" => 12, "shader" => 6552, _ => bail!("Тип контента не поддерживается каталогом CurseForge") };
    let mut params = vec![("gameId", "432".into()), ("classId", class.to_string()), ("searchFilter", query.chars().take(200).collect()), ("index", offset.min(9982).to_string()), ("pageSize", "18".into()), ("sortField", if sort == "newest" { "11" } else if sort == "updated" { "3" } else { "2" }.into()), ("sortOrder", "desc".into())];
    if let Some(p) = profile { params.push(("gameVersion", p.game_version.clone())); if kind == "mod" && p.loader != Loader::Vanilla { params.push(("modLoaderType", loader(&p.loader).to_string())); } }
    let value = request(state, "/mods/search", &params).await?;
    Ok(SearchResults { hits: value["data"].as_array().context("Некорректный ответ CurseForge")?.iter().map(project).collect(), total: value["pagination"]["totalCount"].as_u64().unwrap_or(0), offset })
}
pub async fn detail(state: &AppState, project_id: &str) -> Result<ProjectDetail> {
    let id = id(project_id)?;
    let v = request(state, &format!("/mods/{id}"), &[]).await?;
    let description = request(state, &format!("/mods/{id}/description"), &[]).await?;
    // CurseForge descriptions are HTML; show plain text, never execute third-party HTML.
    let html = description["data"].as_str().unwrap_or_default();
    let body = regex::Regex::new(r"<[^>]*>")?.replace_all(html, " ").replace("&nbsp;", " ").replace("&amp;", "&");
    Ok(ProjectDetail { project: project(&v["data"]), body, gallery: v["data"]["screenshots"].as_array().map(|a| a.iter().filter_map(|s| s["url"].as_str().map(str::to_owned)).collect()).unwrap_or_default(), website: v["data"]["links"]["websiteUrl"].as_str().unwrap_or("https://www.curseforge.com/minecraft").into() })
}
pub async fn compatible_files(state: &AppState, project_id: &str, profile: &Profile) -> Result<Vec<Value>> {
    let id = id(project_id)?;
    let mut params = vec![("gameVersion", profile.game_version.clone()), ("pageSize", "50".into())];
    if profile.loader != Loader::Vanilla { params.push(("modLoaderType", loader(&profile.loader).to_string())); }
    let v = request(state, &format!("/mods/{id}/files"), &params).await?;
    Ok(v["data"].as_array().context("Нет файлов CurseForge")?.clone())
}
pub async fn resolve(state: &AppState, profile: &Profile, project_id: &str, requested: Option<&str>, cancel: &CancellationToken) -> Result<Vec<InstalledFile>> {
    let mut queue = VecDeque::from([(id(project_id)?, requested.map(id).transpose()?)]); let mut visited = HashSet::new(); let mut plans = Vec::new();
    while let Some((project_id, requested)) = queue.pop_front() {
        if cancel.is_cancelled() { bail!("Операция отменена"); }
        if plans.len() > 512 { bail!("Слишком много зависимостей CurseForge"); }
        if !visited.insert(project_id) { continue; }
        let info = request(state, &format!("/mods/{project_id}"), &[]).await?;
        let info = project(&info["data"]);
        profiles::content_folder(&info.project_type)?;
        if info.project_type == "mod" && profile.loader == Loader::Vanilla { bail!("Для модов нужен профиль с загрузчиком"); }
        let file = if let Some(file_id) = requested { request(state, &format!("/mods/{project_id}/files/{file_id}"), &[]).await?["data"].clone() }
            else { compatible_files(state, &project_id.to_string(), profile).await?.into_iter().find(|f| f["isAvailable"] == true).context("Нет совместимого доступного файла CurseForge")? };
        if file["modId"].as_u64() != Some(project_id) || file["isAvailable"] != true { bail!("Файл CurseForge недоступен"); }
        let versions = file["gameVersions"].as_array().context("Нет метаданных совместимости")?;
        if !versions.iter().any(|v| v == &profile.game_version) { bail!("Мод CurseForge не поддерживает выбранную версию Minecraft"); }
        if info.project_type == "mod" && !versions.iter().any(|v| v.as_str().is_some_and(|s| s.eq_ignore_ascii_case(profile.loader.as_str()))) { bail!("Файл CurseForge не поддерживает выбранный загрузчик"); }
        let url = file["downloadUrl"].as_str().filter(|s| !s.is_empty()).context("Автор запретил распространение через сторонние лаунчеры. Скачайте файл на сайте CurseForge вручную. Ограничение не обходится.")?;
        security::check_url(url)?;
        let hash = file["hashes"].as_array().and_then(|h| h.iter().find(|v| v["algo"] == 1)).and_then(|v| v["value"].as_str()).context("CurseForge не предоставил SHA1; установка без проверки запрещена")?;
        downloads::Hash::new("sha1", hash)?;
        let filename = file["fileName"].as_str().context("Нет имени файла")?;
        if security::safe_relative(filename)?.components().count() != 1 { bail!("Небезопасное имя файла"); }
        if let Some(deps) = file["dependencies"].as_array() { for dep in deps { if dep["relationType"] == 3 { queue.push_back((dep["modId"].as_u64().context("Нет ID зависимости")?, None)); } } }
        plans.push(InstalledFile { profile_id: profile.id.clone(), provider: "curseforge".into(), project_id: project_id.to_string(), version_id: file["id"].as_u64().context("Нет file ID")?.to_string(), name: info.title, filename: filename.into(), kind: info.project_type, sha: hash.into(), hash_algorithm: "sha1".into(), enabled: true, download_url: url.into(), size: file["fileLength"].as_u64().unwrap_or(0) });
    }
    Ok(plans)
}
pub async fn install(state: &AppState, profile: &Profile, project: &str, version: Option<&str>, cancel: &CancellationToken) -> Result<Vec<InstalledFile>> {
    let plans = resolve(state, profile, project, version, cancel).await?;
    crate::content::apply(state, profile, &plans, cancel).await?; Ok(plans)
}

pub async fn install_pack(state: &AppState, project_id: &str) -> Result<Profile> {
    let guard = state.begin_job("modpack")?;
    let project_id = id(project_id)?;
    let project = request(state, &format!("/mods/{project_id}"), &[]).await?;
    if project["data"]["classId"] != 4471 { bail!("Проект не является модпаком CurseForge"); }
    let versions = request(state, &format!("/mods/{project_id}/files"), &[("pageSize", "50".into())]).await?;
    let file = versions["data"].as_array().and_then(|files| files.iter().find(|f| f["isAvailable"] == true && f["downloadUrl"].as_str().is_some())).context("Нет разрешённого файла сборки. Автор мог запретить сторонние загрузки.")?;
    let checksum = file["hashes"].as_array().and_then(|hashes| hashes.iter().find(|h| h["algo"] == 1)).and_then(|h| h["value"].as_str()).context("Нет SHA1 сборки")?;
    let hash = downloads::Hash::new("sha1", checksum)?;
    let path = security::within(&state.root(), &format!("downloads/curseforge-{project_id}-{}.zip", hash.value))?;
    downloads::batch(state, vec![downloads::Transfer { url: file["downloadUrl"].as_str().context("Загрузка запрещена")?.into(), path: path.clone(), hash, size: file["fileLength"].as_u64() }], &guard.token).await?;
    crate::modpacks::import_inner(state, &path, &guard.token).await
}

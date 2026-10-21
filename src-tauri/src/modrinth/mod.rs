use crate::{downloads::{self, Hash, Transfer}, models::{InstalledFile, Profile, ProjectDetail, SearchProject, SearchResults}, profiles, security, state::AppState};
use anyhow::{bail, Context, Result};
use serde_json::Value;
use std::collections::{HashSet, VecDeque};
use tokio_util::sync::CancellationToken;
pub const API: &str = "https://api.modrinth.com/v2";

pub async fn search(state: &AppState, query: &str, kind: &str, profile: Option<&Profile>, offset: u64, sort: &str) -> Result<SearchResults> {
    if !["mod", "modpack", "shader", "resourcepack", "datapack"].contains(&kind) { bail!("Неизвестный тип проекта"); }
    let mut facets = vec![vec![format!("project_type:{kind}")]];
    if let Some(p) = profile {
        facets.push(vec![format!("versions:{}", p.game_version)]);
        if kind == "mod" && p.loader != crate::models::Loader::Vanilla { facets.push(vec![format!("categories:{}", p.loader.as_str())]); }
    }
    let sort = if ["relevance", "downloads", "follows", "newest", "updated"].contains(&sort) { sort } else { "relevance" };
    let mut url = url::Url::parse(&format!("{API}/search"))?;
    url.query_pairs_mut().append_pair("query", &query.chars().take(200).collect::<String>()).append_pair("facets", &serde_json::to_string(&facets)?).append_pair("index", sort).append_pair("limit", "18").append_pair("offset", &offset.min(10000).to_string());
    let response: Value = downloads::json(state, url.as_str()).await?;
    let hits = response["hits"].as_array().context("Некорректный ответ Modrinth")?.iter().map(|v| project(v, true)).collect();
    Ok(SearchResults { hits, total: response["total_hits"].as_u64().unwrap_or(0), offset })
}
fn project(v: &Value, search: bool) -> SearchProject {
    SearchProject { id: v[if search { "project_id" } else { "id" }].as_str().unwrap_or_default().into(), provider: "modrinth".into(), title: v["title"].as_str().unwrap_or_default().into(), description: v["description"].as_str().unwrap_or_default().into(), icon_url: v["icon_url"].as_str().map(str::to_owned), downloads: v["downloads"].as_u64().unwrap_or(0), author: v["author"].as_str().unwrap_or("Modrinth").into(), categories: v["categories"].as_array().map(|a| a.iter().filter_map(|v| v.as_str().map(str::to_owned)).collect()).unwrap_or_default(), project_type: v["project_type"].as_str().unwrap_or("mod").into(), slug: v["slug"].as_str().unwrap_or_default().into() }
}
pub async fn detail(state: &AppState, id: &str) -> Result<ProjectDetail> {
    security::safe_id(id)?;
    let value: Value = downloads::json(state, &format!("{API}/project/{id}")).await?;
    let p = project(&value, false);
    Ok(ProjectDetail { website: format!("https://modrinth.com/{}/{}", p.project_type, p.slug), project: p, body: value["body"].as_str().unwrap_or_default().into(), gallery: value["gallery"].as_array().map(|a| a.iter().filter_map(|v| v["url"].as_str().map(str::to_owned)).collect()).unwrap_or_default() })
}

pub async fn compatible_versions(state: &AppState, id: &str, profile: &Profile, kind: &str) -> Result<Vec<Value>> {
    security::safe_id(id)?;
    let mut url = url::Url::parse(&format!("{API}/project/{id}/version"))?;
    url.query_pairs_mut().append_pair("game_versions", &serde_json::to_string(&[&profile.game_version])?);
    if kind == "mod" {
        if profile.loader == crate::models::Loader::Vanilla { bail!("Для модов создайте профиль Fabric, Forge, NeoForge или Quilt"); }
        url.query_pairs_mut().append_pair("loaders", &serde_json::to_string(&[profile.loader.as_str()])?);
    }
    downloads::json(state, url.as_str()).await
}
fn compatible(version: &Value, profile: &Profile, kind: &str) -> bool {
    let game = version["game_versions"].as_array().is_some_and(|a| a.iter().any(|v| v == &profile.game_version));
    game && (kind != "mod" || version["loaders"].as_array().is_some_and(|a| a.iter().any(|v| v == profile.loader.as_str())))
}

pub async fn install(state: &AppState, profile: &Profile, project_id: &str, selected_version: Option<&str>, cancel: &CancellationToken) -> Result<Vec<InstalledFile>> {
    security::safe_id(project_id)?;
    let mut queue = VecDeque::from([(project_id.to_string(), selected_version.map(str::to_owned))]);
    let mut visited = HashSet::new(); let mut plans = Vec::new();
    // Resolve the entire required dependency graph BEFORE modifying the profile.
    while let Some((id, requested)) = queue.pop_front() {
        if plans.len() >= 512 { bail!("Слишком много зависимостей Modrinth"); }
        security::safe_id(&id)?;
        let info = detail(state, &id).await?;
        let kind = info.project.project_type.as_str();
        if kind == "modpack" { bail!("Модпак устанавливается в отдельный профиль через менеджер сборок"); }
        profiles::content_folder(kind)?;
        let version: Value = if let Some(v) = requested { security::safe_id(&v)?; downloads::json(state, &format!("{API}/version/{v}")).await? }
            else { compatible_versions(state, &id, profile, kind).await?.into_iter().next().context("Нет совместимой версии для выбранного Minecraft и загрузчика")? };
        let version_id = version["id"].as_str().context("Modrinth не вернул id версии")?.to_owned();
        if version["project_id"] != id || !compatible(&version, profile, kind) { bail!("Мод или обязательная зависимость несовместимы с профилем: {}", info.project.title); }
        if !visited.insert(id.clone()) {
            if plans.iter().any(|f: &InstalledFile| f.project_id == id && f.version_id != version_id) { bail!("Конфликт версий обязательной зависимости: {}", info.project.title); }
            continue;
        }
        if let Some(deps) = version["dependencies"].as_array() {
            for dep in deps {
                if dep["dependency_type"] == "required" {
                    let requested = dep["version_id"].as_str().map(str::to_owned);
                    let dependency_id = if let Some(project_id) = dep["project_id"].as_str() { project_id.to_owned() }
                        else if let Some(v) = &requested { security::safe_id(v)?; let data: Value = downloads::json(state, &format!("{API}/version/{v}")).await?; data["project_id"].as_str().context("Неизвестная зависимость")?.to_owned() }
                        else { bail!("Не удалось определить обязательную зависимость"); };
                    queue.push_back((dependency_id, requested));
                } else if dep["dependency_type"] == "incompatible" {
                    let existing = state.list::<InstalledFile>("installed")?;
                    if existing.iter().any(|f| f.profile_id == profile.id && f.enabled && (Some(f.project_id.as_str()) == dep["project_id"].as_str() || Some(f.version_id.as_str()) == dep["version_id"].as_str())) { bail!("Найдена несовместимая зависимость. Сначала отключите конфликтующий мод."); }
                }
            }
        }
        let files = version["files"].as_array().context("У версии нет файлов")?;
        let file = files.iter().find(|f| f["primary"] == true).or_else(|| files.first()).context("У версии нет загружаемого файла")?;
        let filename = file["filename"].as_str().context("Нет имени файла")?;
        if security::safe_relative(filename)?.components().count() != 1 { bail!("Недопустимое имя файла мода"); }
        let (algorithm, hash) = if let Some(sha) = file["hashes"]["sha512"].as_str() { ("sha512", sha) } else { ("sha1", file["hashes"]["sha1"].as_str().context("Нет контрольной суммы Modrinth")?) };
        Hash::new(algorithm, hash)?;
        plans.push(InstalledFile { profile_id: profile.id.clone(), provider: "modrinth".into(), project_id: id, version_id, name: info.project.title, filename: filename.into(), kind: kind.into(), sha: hash.into(), hash_algorithm: algorithm.into(), enabled: true, download_url: file["url"].as_str().context("Нет ссылки загрузки")?.into(), size: file["size"].as_u64().unwrap_or(0) });
    }
    crate::content::apply(state, profile, &plans, cancel).await?; Ok(plans)
}

pub async fn install_pack(state: &AppState, project_id: &str) -> Result<Profile> {
    let guard = state.begin_job("modpack")?;
    security::safe_id(project_id)?;
    let info = detail(state, project_id).await?;
    if info.project.project_type != "modpack" { bail!("Проект не является модпаком"); }
    let versions: Vec<Value> = downloads::json(state, &format!("{API}/project/{project_id}/version")).await?;
    let version = versions.first().context("У сборки нет версий")?;
    let file = version["files"].as_array().and_then(|f| f.iter().find(|v| v["filename"].as_str().is_some_and(|s| s.ends_with(".mrpack")))).context("У версии нет .mrpack")?;
    let hash = Hash::new("sha512", file["hashes"]["sha512"].as_str().context("Нет SHA512 сборки")?)?;
    let path = security::within(&state.root(), &format!("downloads/{}.mrpack", hash.value))?;
    downloads::batch(state, vec![Transfer { url: file["url"].as_str().context("Нет URL")?.into(), path: path.clone(), hash, size: file["size"].as_u64() }], &guard.token).await?;
    crate::modpacks::import_inner(state, &path, &guard.token).await
}

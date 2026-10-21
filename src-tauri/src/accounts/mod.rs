pub mod vault;
use crate::{models::Account, state::{AppState, PendingAuth}};
use anyhow::{bail, Context, Result};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};

const OAUTH: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0";
const SCOPE: &str = "XboxLive.signin offline_access";
#[derive(Serialize)] pub struct Challenge { pub id: String, pub user_code: String, pub verification_uri: String, pub expires_in: u64, pub interval: u64 }
#[derive(Serialize)] pub struct PollResult { pub pending: bool, pub interval: u64, pub account: Option<Account> }
#[derive(Serialize, Deserialize)] struct Credentials { refresh_token: String, minecraft_token: String, expires_at: i64, client_id: String }

async fn auth_json(response: reqwest::Response, step: &str) -> Result<Value> {
    let status = response.status();
    let body = crate::downloads::bounded_response(response, 1024 * 1024).await?;
    let value: Value = serde_json::from_slice(&body).context("Некорректный ответ сервиса авторизации")?;
    if !status.is_success() {
        let help = match value.get("XErr").and_then(Value::as_u64) {
            Some(2148916233) => "У аккаунта нет профиля Xbox. Создайте его на xbox.com.",
            Some(2148916238) => "Для детского аккаунта требуется разрешение Microsoft Family.",
            _ => "Проверьте регистрацию Microsoft Application ID, разрешения Minecraft API и доступ к сети. Пароль не нужен.",
        };
        bail!("{step}: HTTP {}. {help}", status.as_u16());
    }
    Ok(value)
}

pub async fn begin(state: &AppState) -> Result<Challenge> {
    let client_id = state.config().microsoft_client_id.trim().to_string();
    if uuid::Uuid::parse_str(&client_id).is_err() { bail!("Укажите действительный Microsoft Application (client) ID в настройках. Приложение должно разрешать public client / device code flow и доступ к Minecraft API."); }
    let response = state.client.post(format!("{OAUTH}/devicecode")).form(&[("client_id", client_id.as_str()), ("scope", SCOPE)]).send().await?;
    let value = auth_json(response, "Microsoft Device Code").await?;
    let id = uuid::Uuid::new_v4().to_string(); let interval = value["interval"].as_u64().unwrap_or(5).max(5);
    let expires_in = value["expires_in"].as_u64().unwrap_or(900).min(1800);
    let device_code = value["device_code"].as_str().context("Microsoft не вернул device code")?.to_string();
    let user_code = value["user_code"].as_str().context("Microsoft не вернул код входа")?.to_string();
    let uri = value["verification_uri"].as_str().context("Microsoft не вернул адрес входа")?.to_string();
    let url = url::Url::parse(&uri)?;
    if url.scheme() != "https" || !["microsoft.com", "www.microsoft.com", "login.microsoftonline.com", "login.live.com"].contains(&url.host_str().unwrap_or("")) { bail!("Неожиданный адрес Microsoft OAuth"); }
    let mut pending = state.pending_auth.lock().unwrap_or_else(|e| e.into_inner());
    pending.retain(|_, p| p.expires > chrono::Utc::now().timestamp());
    if pending.len() >= 3 { bail!("Завершите или отмените предыдущий вход"); }
    pending.insert(id.clone(), PendingAuth { device_code, interval, expires: chrono::Utc::now().timestamp() + expires_in as i64, client_id, next_poll: chrono::Utc::now().timestamp() + interval as i64 });
    Ok(Challenge { id, user_code, verification_uri: uri, expires_in, interval })
}

pub async fn poll(state: &AppState, id: &str) -> Result<PollResult> {
    let (device, client, interval) = {
        let mut map = state.pending_auth.lock().unwrap_or_else(|e| e.into_inner());
        let p = map.get_mut(id).context("Вход отменён или срок кода истёк")?;
        let now = chrono::Utc::now().timestamp();
        if now >= p.expires { map.remove(id); bail!("Код Microsoft истёк. Начните вход заново."); }
        if now < p.next_poll { return Ok(PollResult { pending: true, interval: p.interval, account: None }); }
        p.next_poll = now + p.interval as i64;
        (p.device_code.clone(), p.client_id.clone(), p.interval)
    };
    let response = state.client.post(format!("{OAUTH}/token")).form(&[("grant_type", "urn:ietf:params:oauth:grant-type:device_code"), ("client_id", client.as_str()), ("device_code", device.as_str())]).send().await?;
    let status = response.status();
    let value: Value = serde_json::from_slice(&crate::downloads::bounded_response(response, 1024 * 1024).await?)?;
    if !status.is_success() {
        match value["error"].as_str().unwrap_or("") {
            "authorization_pending" => return Ok(PollResult { pending: true, interval, account: None }),
            "slow_down" => { let interval = interval + 5; if let Some(p) = state.pending_auth.lock().unwrap_or_else(|e| e.into_inner()).get_mut(id) { p.interval = interval; p.next_poll = chrono::Utc::now().timestamp() + interval as i64; } return Ok(PollResult { pending: true, interval, account: None }); }
            "authorization_declined" => bail!("Вход отклонён пользователем"),
            "expired_token" => bail!("Срок действия кода истёк"),
            _ => bail!("Microsoft не разрешил вход (HTTP {}). Проверьте Application ID.", status.as_u16()),
        }
    }
    let access = value["access_token"].as_str().context("Нет Microsoft access token")?;
    let refresh = value["refresh_token"].as_str().context("Нет refresh token; требуется offline_access")?;
    let (account, creds) = exchange(state, access, refresh, &client).await?;
    // Cancellation cannot accidentally persist a completed login.
    if state.pending_auth.lock().unwrap_or_else(|e| e.into_inner()).remove(id).is_none() { bail!("Вход отменён"); }
    vault::set(&format!("account-{}", account.id), &serde_json::to_string(&creds)?)?;
    state.put("accounts", &account.id, &account)?;
    state.settings.write().unwrap_or_else(|e| e.into_inner()).selected_account = Some(account.id.clone());
    state.persist_settings()?; state.changed(); state.notify("success", "Аккаунт Microsoft подключён. Лицензия Minecraft подтверждена.");
    Ok(PollResult { pending: false, interval, account: Some(account) })
}

async fn exchange(state: &AppState, access: &str, refresh: &str, client_id: &str) -> Result<(Account, Credentials)> {
    let xbl = auth_json(state.client.post("https://user.auth.xboxlive.com/user/authenticate").json(&json!({ "Properties": { "AuthMethod": "RPS", "SiteName": "user.auth.xboxlive.com", "RpsTicket": format!("d={access}") }, "RelyingParty": "http://auth.xboxlive.com", "TokenType": "JWT" })).send().await?, "Xbox Live").await?;
    let xbl_token = xbl["Token"].as_str().context("Xbox Live не вернул токен")?;
    let xsts = auth_json(state.client.post("https://xsts.auth.xboxlive.com/xsts/authorize").json(&json!({ "Properties": { "SandboxId": "RETAIL", "UserTokens": [xbl_token] }, "RelyingParty": "rp://api.minecraftservices.com/", "TokenType": "JWT" })).send().await?, "Xbox XSTS").await?;
    let uhs = xsts["DisplayClaims"]["xui"][0]["uhs"].as_str().context("Нет Xbox user hash")?;
    let xsts_token = xsts["Token"].as_str().context("Нет XSTS token")?;
    let mc = auth_json(state.client.post("https://api.minecraftservices.com/authentication/login_with_xbox").json(&json!({ "identityToken": format!("XBL3.0 x={uhs};{xsts_token}") })).send().await?, "Minecraft Authentication").await?;
    let token = mc["access_token"].as_str().context("Нет Minecraft token")?.to_owned();
    check_ownership(state, &token).await?;
    let profile = auth_json(state.client.get("https://api.minecraftservices.com/minecraft/profile").bearer_auth(&token).send().await?, "Minecraft Profile").await?;
    let id = profile["id"].as_str().context("Нет UUID Minecraft")?.to_owned();
    if id.len() != 32 || !id.chars().all(|c| c.is_ascii_hexdigit()) { bail!("Некорректный UUID Minecraft"); }
    let account = Account { id, name: profile["name"].as_str().context("Нет имени Minecraft")?.into(), skin_url: profile["skins"].as_array().and_then(|s| s.iter().find(|s| s["state"] == "ACTIVE")).and_then(|s| s["url"].as_str()).map(str::to_owned), added_at: chrono::Utc::now().timestamp() };
    let credentials = Credentials { refresh_token: refresh.into(), minecraft_token: token, expires_at: chrono::Utc::now().timestamp() + mc["expires_in"].as_i64().unwrap_or(86400), client_id: client_id.into() };
    Ok((account, credentials))
}

async fn check_ownership(state: &AppState, token: &str) -> Result<()> {
    let entitlements = auth_json(state.client.get("https://api.minecraftservices.com/entitlements/mcstore").bearer_auth(token).send().await?, "Minecraft Entitlements").await?;
    let owned = entitlements["items"].as_array().is_some_and(|items| items.iter().any(|i| matches!(i["name"].as_str(), Some("game_minecraft" | "product_minecraft"))));
    if !owned { bail!("У аккаунта не найдена лицензия Minecraft Java Edition. Приобретите игру или проверьте подписку. Offline/cracked-вход не поддерживается."); }
    Ok(())
}

pub async fn launch_identity(state: &AppState) -> Result<(Account, String)> {
    let id = state.config().selected_account.context("Войдите в Microsoft-аккаунт с лицензией Minecraft")?;
    let mut account: Account = state.get("accounts", &id)?;
    let mut creds: Credentials = serde_json::from_str(&vault::get(&format!("account-{id}"))?)?;
    if creds.expires_at < chrono::Utc::now().timestamp() + 300 {
        let response = state.client.post(format!("{OAUTH}/token")).form(&[("client_id", creds.client_id.as_str()), ("grant_type", "refresh_token"), ("refresh_token", creds.refresh_token.as_str()), ("scope", SCOPE)]).send().await?;
        let ms = auth_json(response, "Обновление Microsoft-сессии").await?;
        let access = ms["access_token"].as_str().context("Войдите в Microsoft заново")?;
        let refresh = ms["refresh_token"].as_str().unwrap_or(&creds.refresh_token);
        let (new_account, new_creds) = exchange(state, access, refresh, &creds.client_id).await?;
        if new_account.id != account.id { bail!("UUID обновлённого аккаунта не совпадает"); }
        account.name = new_account.name; account.skin_url = new_account.skin_url;
        creds = new_creds;
        vault::set(&format!("account-{id}"), &serde_json::to_string(&creds)?)?;
        state.put("accounts", &id, &account)?;
    } else { check_ownership(state, &creds.minecraft_token).await?; }
    Ok((account, creds.minecraft_token))
}

pub fn remove(state: &AppState, id: &str) -> Result<()> {
    let _operation = state.begin_job("account")?;
    let _: Account = state.get("accounts", id)?;
    vault::delete(&format!("account-{id}"))?;
    state.remove("accounts", id)?;
    if state.config().selected_account.as_deref() == Some(id) { state.settings.write().unwrap_or_else(|e| e.into_inner()).selected_account = state.list::<Account>("accounts")?.first().map(|a| a.id.clone()); state.persist_settings()?; }
    state.changed(); Ok(())
}

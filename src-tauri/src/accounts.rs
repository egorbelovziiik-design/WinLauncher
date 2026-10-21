use std::time::{SystemTime, UNIX_EPOCH};

use serde_json::{json, Value};
use tauri::{AppHandle, Manager};

use crate::models::*;
use crate::state::{wlock, AppState};

/// Public client id of the official Minecraft launcher (Live SDK / MBI_SSL).
const LIVE_CLIENT_ID: &str = "00000000402b5328";
const LIVE_SCOPE: &str = "service::user.auth.xboxlive.com::MBI_SSL";
const LIVE_DEVICE_URL: &str = "https://login.live.com/oauth20_connect.srf";
const LIVE_TOKEN_URL: &str = "https://login.live.com/oauth20_token.srf";

/// Azure AD v2 endpoints, only used with a custom (GUID) client id.
const AZURE_DEVICE_URL: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
const AZURE_TOKEN_URL: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
const AZURE_SCOPE: &str = "XboxLive.signin offline_access";

fn now_secs() -> i64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_secs() as i64).unwrap_or(0)
}

/// Lets power users plug in their own Azure application registration
/// (`WINLAUCHER_MS_CLIENT_ID=<guid>`), otherwise the public launcher id is used.
fn client_id() -> String {
    std::env::var("WINLAUCHER_MS_CLIENT_ID")
        .ok()
        .map(|v| v.trim().to_string())
        .filter(|v| !v.is_empty())
        .unwrap_or_else(|| LIVE_CLIENT_ID.to_string())
}

fn is_azure_id(id: &str) -> bool {
    id.len() == 36 && id.chars().filter(|c| *c == '-').count() == 4
}

/* ------------------------------------------------------------------ */
/* Offline accounts                                                    */
/* ------------------------------------------------------------------ */

pub fn offline_uuid(name: &str) -> String {
    use md5::{Digest, Md5};
    let digest = Md5::digest(format!("OfflinePlayer:{name}").as_bytes());
    let mut bytes = [0u8; 16];
    bytes.copy_from_slice(&digest);
    bytes[6] = (bytes[6] & 0x0f) | 0x30; // version 3 (name based, MD5)
    bytes[8] = (bytes[8] & 0x3f) | 0x80; // IETF variant
    let h = hex::encode(bytes);
    format!("{}-{}-{}-{}-{}", &h[0..8], &h[8..12], &h[12..16], &h[16..20], &h[20..32])
}

fn valid_name(name: &str) -> bool {
    let len = name.chars().count();
    (3..=16).contains(&len) && name.chars().all(|c| c.is_ascii_alphanumeric() || c == '_')
}

pub fn add_offline(app: &AppHandle, name: &str) -> Result<Account, String> {
    let name = name.trim();
    if !valid_name(name) {
        return Err("accounts.invalidName".into());
    }
    let state = app.state::<AppState>();
    let uuid = offline_uuid(name);
    {
        let accounts = state.accounts();
        if accounts.iter().any(|a| a.uuid == uuid) {
            return Err("accounts.exists".into());
        }
    }
    let account = Account {
        id: uuid.clone(),
        kind: "offline".into(),
        name: name.to_string(),
        uuid,
        created_at: chrono::Utc::now().to_rfc3339(),
        last_used_at: None,
        owned: None,
        skins: Vec::new(),
        primary: false,
    };
    insert_account(app, account)
}

pub fn rename_offline(app: &AppHandle, id: &str, name: &str) -> Result<Account, String> {
    let state = app.state::<AppState>();
    let account = state
        .accounts()
        .into_iter()
        .find(|a| a.id == id)
        .ok_or_else(|| "Account not found".to_string())?;
    if account.kind != "offline" {
        return Err("Only offline accounts can be renamed".into());
    }
    let name = name.trim();
    if !valid_name(name) {
        return Err("accounts.invalidName".into());
    }
    let uuid = offline_uuid(name);
    {
        let accounts = state.accounts();
        if accounts.iter().any(|a| a.id != id && a.uuid == uuid) {
            return Err("accounts.exists".into());
        }
    }
    {
        let mut accounts = wlock(&state.accounts);
        if let Some(a) = accounts.iter_mut().find(|a| a.id == id) {
            a.name = name.to_string();
            a.uuid = uuid;
        }
    }
    state.save_accounts();
    state
        .accounts()
        .into_iter()
        .find(|a| a.id == id)
        .ok_or_else(|| "Account not found".to_string())
}

pub fn set_primary(app: &AppHandle, id: &str) -> Result<(), String> {
    let state = app.state::<AppState>();
    {
        let mut accounts = wlock(&state.accounts);
        if !accounts.iter().any(|a| a.id == id) {
            return Err("Account not found".into());
        }
        for a in accounts.iter_mut() {
            a.primary = a.id == id;
        }
    }
    state.save_accounts();
    Ok(())
}

pub fn delete(app: &AppHandle, id: &str) -> Result<(), String> {
    let state = app.state::<AppState>();
    {
        let mut accounts = wlock(&state.accounts);
        accounts.retain(|a| a.id != id);
        if !accounts.iter().any(|a| a.primary) {
            if let Some(first) = accounts.first_mut() {
                first.primary = true;
            }
        }
    }
    state.secrets.clear(&format!("account-{id}"));
    state.save_accounts();
    Ok(())
}

fn insert_account(app: &AppHandle, mut account: Account) -> Result<Account, String> {
    let state = app.state::<AppState>();
    {
        let mut accounts = wlock(&state.accounts);
        if accounts.iter().any(|a| a.uuid == account.uuid) {
            return Err("accounts.exists".into());
        }
        if accounts.is_empty() {
            account.primary = true;
        }
        accounts.push(account.clone());
    }
    state.save_accounts();
    Ok(account)
}

/* ------------------------------------------------------------------ */
/* Token storage                                                       */
/* ------------------------------------------------------------------ */

fn save_tokens(state: &AppState, id: &str, tokens: &StoredTokens) -> Result<(), String> {
    let json = serde_json::to_string(tokens).map_err(|e| e.to_string())?;
    state.secrets.set(&format!("account-{id}"), &json)
}

fn load_tokens(state: &AppState, id: &str) -> Option<StoredTokens> {
    let raw = state.secrets.get(&format!("account-{id}"))?;
    serde_json::from_str(&raw).ok()
}

/* ------------------------------------------------------------------ */
/* Microsoft sign-in (device code)                                     */
/* ------------------------------------------------------------------ */

async fn post_form(client: &reqwest::Client, url: &str, form: &[(&str, String)]) -> Result<Value, String> {
    let res = client
        .post(url)
        .form(form)
        .timeout(std::time::Duration::from_secs(40))
        .send()
        .await
        .map_err(|e| e.to_string())?;
    let status = res.status();
    let text = res.text().await.map_err(|e| e.to_string())?;
    let value: Value = serde_json::from_str(&text).unwrap_or_else(|_| json!({ "raw": text.chars().take(300).collect::<String>() }));
    if !status.is_success() {
        let err = value.get("error").and_then(|v| v.as_str()).unwrap_or("http_error");
        let desc = value
            .get("error_description")
            .and_then(|v| v.as_str())
            .unwrap_or("Microsoft sign-in failed");
        return Err(format!("{err}|{desc}"));
    }
    Ok(value)
}

pub async fn begin(app: &AppHandle) -> Result<DeviceCodeInfo, String> {
    let state = app.state::<AppState>();
    let client = state.http();
    let id = client_id();
    let azure = is_azure_id(&id);
    let (url, form) = if azure {
        (
            AZURE_DEVICE_URL,
            vec![
                ("client_id", id.clone()),
                ("scope", AZURE_SCOPE.to_string()),
            ],
        )
    } else {
        (
            LIVE_DEVICE_URL,
            vec![
                ("client_id", id.clone()),
                ("scope", LIVE_SCOPE.to_string()),
                ("response_type", "device_code".to_string()),
            ],
        )
    };
    let value = post_form(&client, url, &form).await.map_err(|e| format!("accounts.authError|{e}"))?;
    let device_code = value.get("device_code").and_then(|v| v.as_str()).unwrap_or_default();
    if device_code.is_empty() {
        return Err("accounts.authError|Microsoft did not return a device code".into());
    }
    Ok(DeviceCodeInfo {
        device_code: device_code.to_string(),
        user_code: value.get("user_code").and_then(|v| v.as_str()).unwrap_or_default().to_string(),
        verification_uri: value
            .get("verification_uri")
            .and_then(|v| v.as_str())
            .unwrap_or("https://www.microsoft.com/link")
            .to_string(),
        expires_in: value.get("expires_in").and_then(|v| v.as_u64()).unwrap_or(900),
        interval: value.get("interval").and_then(|v| v.as_u64()).unwrap_or(5).max(1),
    })
}

pub async fn poll(app: &AppHandle, device_code: &str) -> Result<Option<Account>, String> {
    let state = app.state::<AppState>();
    let client = state.http();
    let id = client_id();
    let azure = is_azure_id(&id);
    let (url, form) = if azure {
        (
            AZURE_TOKEN_URL,
            vec![
                ("grant_type", "urn:ietf:params:oauth:grant-type:device_code".to_string()),
                ("client_id", id.clone()),
                ("device_code", device_code.to_string()),
            ],
        )
    } else {
        (
            LIVE_TOKEN_URL,
            vec![
                ("grant_type", "urn:ietf:params:oauth:grant-type:device_code".to_string()),
                ("client_id", id.clone()),
                ("device_code", device_code.to_string()),
            ],
        )
    };
    let value = match post_form(&client, url, &form).await {
        Ok(v) => v,
        Err(e) => {
            let lowered = e.to_lowercase();
            if lowered.contains("authorization_pending") || lowered.contains("slow_down") {
                return Ok(None);
            }
            if lowered.contains("authorization_declined") || lowered.contains("access_denied") {
                return Err("accounts.authError|sign-in was declined".into());
            }
            if lowered.contains("expired_token") || lowered.contains("bad_verification_code") {
                return Err("errors.timeout".into());
            }
            return Err(format!("accounts.authError|{e}"));
        }
    };
    let ms_access = value.get("access_token").and_then(|v| v.as_str()).unwrap_or_default().to_string();
    let ms_refresh = value
        .get("refresh_token")
        .and_then(|v| v.as_str())
        .unwrap_or_default()
        .to_string();
    if ms_access.is_empty() {
        return Err("accounts.authError|no access token returned".into());
    }
    let flow = if azure { "azure" } else { "live" };
    complete_login(app, &ms_access, &ms_refresh, flow).await
}

async fn xbl_authenticate(client: &reqwest::Client, tickets: &[String]) -> Result<(String, String), String> {
    let mut last = String::new();
    for ticket in tickets {
        let body = json!({
            "Properties": {
                "AuthMethod": "RPS",
                "SiteName": "user.auth.xboxlive.com",
                "RpsTicket": ticket
            },
            "RelyingParty": "http://auth.xboxlive.com",
            "TokenType": "JWT"
        });
        let res = client
            .post("https://user.auth.xboxlive.com/user/authenticate")
            .header("Accept", "application/json")
            .json(&body)
            .timeout(std::time::Duration::from_secs(30))
            .send()
            .await
            .map_err(|e| format!("accounts.authError|{e}"))?;
        let status = res.status();
        let value: Value = res.json().await.unwrap_or(Value::Null);
        if status.is_success() {
            let token = value.get("Token").and_then(|v| v.as_str()).unwrap_or_default().to_string();
            let uhs = value
                .get("DisplayClaims")
                .and_then(|v| v.get("xui"))
                .and_then(|v| v.get(0))
                .and_then(|v| v.get("uhs"))
                .and_then(|v| v.as_str())
                .unwrap_or_default()
                .to_string();
            if !token.is_empty() && !uhs.is_empty() {
                return Ok((token, uhs));
            }
            last = "missing token in Xbox response".into();
        } else {
            last = format!("HTTP {status}");
        }
    }
    Err(format!("accounts.authError|Xbox Live rejected the Microsoft token ({last})"))
}

async fn complete_login(app: &AppHandle, ms_access: &str, ms_refresh: &str, flow: &str) -> Result<Account, String> {
    let state = app.state::<AppState>();
    let client = state.http();

    // 1. Xbox Live ------------------------------------------------------
    let tickets = if flow == "azure" {
        vec![format!("d={ms_access}")]
    } else {
        vec![ms_access.to_string(), format!("d={ms_access}")]
    };
    let (xbl_token, uhs) = xbl_authenticate(&client, &tickets).await?;

    // 2. XSTS -----------------------------------------------------------
    let xsts_body = json!({
        "Properties": { "SandboxId": "RETAIL", "UserTokens": [xbl_token] },
        "RelyingParty": "rp://api.minecraftservices.com/",
        "TokenType": "JWT"
    });
    let xsts_res = client
        .post("https://xsts.auth.xboxlive.com/xsts/authorize")
        .header("Accept", "application/json")
        .json(&xsts_body)
        .timeout(std::time::Duration::from_secs(30))
        .send()
        .await
        .map_err(|e| format!("accounts.authError|{e}"))?;
    let xsts_status = xsts_res.status();
    let xsts: Value = xsts_res.json().await.unwrap_or(Value::Null);
    if !xsts_status.is_success() {
        let xerr = xsts.get("XErr").and_then(|v| v.as_i64()).unwrap_or(0);
        let msg = match xerr {
            2148916233 => "this Microsoft account has no Xbox profile yet — sign in once at xbox.com and retry",
            2148916238 => "child accounts must be added to a Microsoft family by an adult",
            2148916227 => "this Xbox account is banned",
            _ => "Xbox Live authorization failed",
        };
        return Err(format!("accounts.authError|{msg}"));
    }
    let xsts_token = xsts.get("Token").and_then(|v| v.as_str()).unwrap_or_default().to_string();
    if xsts_token.is_empty() {
        return Err("accounts.authError|XSTS returned no token".into());
    }

    // 3. Minecraft services --------------------------------------------
    let mc_res = client
        .post("https://api.minecraftservices.com/authentication/login_with_xbox")
        .json(&json!({ "identityToken": format!("XBL3.0 x={uhs};{xsts_token}") }))
        .timeout(std::time::Duration::from_secs(30))
        .send()
        .await
        .map_err(|e| format!("accounts.authError|{e}"))?;
    let mc_status = mc_res.status();
    if !mc_status.is_success() {
        return Err(format!("accounts.authError|Minecraft login failed (HTTP {mc_status})"));
    }
    let mc: Value = mc_res.json().await.unwrap_or(Value::Null);
    let mc_token = mc.get("access_token").and_then(|v| v.as_str()).unwrap_or_default().to_string();
    let expires_in = mc.get("expires_in").and_then(|v| v.as_u64()).unwrap_or(86_400);
    if mc_token.is_empty() {
        return Err("accounts.authError|Minecraft returned no access token".into());
    }

    // 4. Ownership ------------------------------------------------------
    let owned = match client
        .get("https://api.minecraftservices.com/entitlements/mcstore")
        .bearer_auth(&mc_token)
        .timeout(std::time::Duration::from_secs(30))
        .send()
        .await
    {
        Ok(res) if res.status().is_success() => match res.json::<Value>().await {
            Ok(v) => v
                .get("items")
                .and_then(|i| i.as_array())
                .map(|items| {
                    items.iter().any(|it| {
                        it.get("name")
                            .and_then(|n| n.as_str())
                            .map(|n| n.contains("minecraft"))
                            .unwrap_or(false)
                    })
                })
                .unwrap_or(false),
            Err(_) => false,
        },
        _ => false,
    };

    // 5. Profile + skins ------------------------------------------------
    let profile_res = client
        .get("https://api.minecraftservices.com/minecraft/profile")
        .bearer_auth(&mc_token)
        .timeout(std::time::Duration::from_secs(30))
        .send()
        .await
        .map_err(|e| format!("accounts.authError|{e}"))?;
    if !profile_res.status().is_success() {
        return Err("accounts.authNoGame".into());
    }
    let profile: Value = profile_res.json().await.unwrap_or(Value::Null);
    let uuid = profile.get("id").and_then(|v| v.as_str()).unwrap_or_default().to_string();
    let name = profile.get("name").and_then(|v| v.as_str()).unwrap_or_default().to_string();
    if uuid.is_empty() || name.is_empty() {
        return Err("accounts.authNoGame".into());
    }
    let mut skins = Vec::new();
    if let Some(arr) = profile.get("skins").and_then(|v| v.as_array()) {
        for skin in arr {
            skins.push(Skin {
                id: skin.get("id").and_then(|v| v.as_str()).unwrap_or_default().to_string(),
                state: skin.get("state").and_then(|v| v.as_str()).unwrap_or_default().to_string(),
                url: skin.get("url").and_then(|v| v.as_str()).unwrap_or_default().to_string(),
                variant: skin.get("variant").and_then(|v| v.as_str()).unwrap_or_default().to_string(),
            });
        }
    }

    let tokens = StoredTokens {
        flow: flow.to_string(),
        ms_access_token: ms_access.to_string(),
        ms_refresh_token: ms_refresh.to_string(),
        mc_access_token: mc_token,
        mc_expires_at: now_secs() + expires_in as i64 - 60,
        xuid: uhs,
        owned,
    };
    save_tokens(&state, &uuid, &tokens)?;

    let existing = state.accounts().into_iter().find(|a| a.uuid == uuid);
    let account = Account {
        id: uuid.clone(),
        kind: "microsoft".into(),
        name,
        uuid: uuid.clone(),
        created_at: existing
            .as_ref()
            .map(|a| a.created_at.clone())
            .unwrap_or_else(|| chrono::Utc::now().to_rfc3339()),
        last_used_at: existing.as_ref().and_then(|a| a.last_used_at.clone()),
        owned: Some(owned),
        skins,
        primary: existing.as_ref().map(|a| a.primary).unwrap_or(false),
    };
    {
        let mut accounts = wlock(&state.accounts);
        match accounts.iter_mut().find(|a| a.uuid == uuid) {
            Some(slot) => *slot = account.clone(),
            None => {
                let mut acc = account.clone();
                if accounts.is_empty() {
                    acc.primary = true;
                }
                accounts.push(acc);
            }
        }
    }
    state.save_accounts();
    state.log("info", "launcher", format!("Microsoft sign-in complete: {}", account.name));
    Ok(account)
}

pub async fn refresh(app: &AppHandle, id: &str) -> Result<Account, String> {
    let state = app.state::<AppState>();
    let tokens = load_tokens(&state, id).ok_or_else(|| "errors.unauthorized".to_string())?;
    if tokens.ms_refresh_token.is_empty() {
        return Err("errors.unauthorized".into());
    }
    let client = state.http();
    let cid = client_id();
    let flow = if tokens.flow.is_empty() {
        if is_azure_id(&cid) {
            "azure".to_string()
        } else {
            "live".to_string()
        }
    } else {
        tokens.flow.clone()
    };
    let (url, scope) = if flow == "azure" {
        (AZURE_TOKEN_URL, AZURE_SCOPE)
    } else {
        (LIVE_TOKEN_URL, LIVE_SCOPE)
    };
    let form = vec![
        ("grant_type", "refresh_token".to_string()),
        ("client_id", cid),
        ("refresh_token", tokens.ms_refresh_token.clone()),
        ("scope", scope.to_string()),
    ];
    let value = post_form(&client, url, &form)
        .await
        .map_err(|_| "errors.unauthorized".to_string())?;
    let ms_access = value.get("access_token").and_then(|v| v.as_str()).unwrap_or_default().to_string();
    let ms_refresh = value
        .get("refresh_token")
        .and_then(|v| v.as_str())
        .map(|s| s.to_string())
        .unwrap_or(tokens.ms_refresh_token.clone());
    if ms_access.is_empty() {
        return Err("errors.unauthorized".into());
    }
    complete_login(app, &ms_access, &ms_refresh, &flow).await
}

/* ------------------------------------------------------------------ */
/* Launch account                                                      */
/* ------------------------------------------------------------------ */

pub async fn launch_account(app: &AppHandle, id: &str) -> Result<LaunchAccount, String> {
    let state = app.state::<AppState>();
    let account = state
        .accounts()
        .into_iter()
        .find(|a| a.id == id)
        .ok_or_else(|| "accounts.noAccounts".to_string())?;

    if account.kind == "offline" {
        return Ok(LaunchAccount {
            name: account.name.clone(),
            uuid: account.uuid.clone(),
            access_token: "0".into(),
            user_type: "legacy".into(),
            xuid: String::new(),
            user_properties: Vec::new(),
        });
    }

    let mut tokens = load_tokens(&state, id).ok_or_else(|| "errors.unauthorized".to_string())?;
    if tokens.mc_access_token.is_empty() || tokens.mc_expires_at <= now_secs() {
        state.log("info", "launcher", "Minecraft session expired — refreshing");
        refresh(app, id).await?;
        tokens = load_tokens(&state, id).ok_or_else(|| "errors.unauthorized".to_string())?;
    }
    if !tokens.owned {
        return Err("accounts.authNoGame".into());
    }
    {
        let mut accounts = wlock(&state.accounts);
        if let Some(a) = accounts.iter_mut().find(|a| a.id == id) {
            a.last_used_at = Some(chrono::Utc::now().to_rfc3339());
        }
    }
    state.save_accounts();
    Ok(LaunchAccount {
        name: account.name.clone(),
        uuid: account.uuid.clone(),
        access_token: tokens.mc_access_token,
        user_type: "msa".into(),
        xuid: tokens.xuid,
        user_properties: Vec::new(),
    })
}

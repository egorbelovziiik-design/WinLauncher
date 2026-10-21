//! Explicit local identity. No Microsoft token, skin or ownership is fabricated.
use crate::{
    models::{Account, AccountKind},
    state::AppState,
};
use anyhow::{bail, Result};
use md5::{Digest, Md5};

pub fn player_id(name: &str) -> Result<String> {
    if !(3..=16).contains(&name.len())
        || !name.bytes().all(|c| c.is_ascii_alphanumeric() || c == b'_')
    {
        bail!("Ник офлайн-аккаунта: 3–16 латинских букв, цифр или символов подчёркивания.");
    }
    // Java UUID.nameUUIDFromBytes("OfflinePlayer:" + name), NOT a
    // namespace-prefixed UUID v3. Case is significant, just like Minecraft.
    let mut bytes: [u8; 16] = Md5::digest(format!("OfflinePlayer:{name}").as_bytes()).into();
    bytes[6] = (bytes[6] & 0x0f) | 0x30;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    Ok(uuid::Uuid::from_bytes(bytes).simple().to_string())
}

pub fn create(state: &AppState, name: &str) -> Result<Account> {
    let _operation = state.begin_job("account")?;
    let name = name.trim();
    let id = player_id(name)?;
    let accounts = state.list::<Account>("accounts")?;
    let account = if let Some(existing) = accounts.into_iter().find(|a| a.id == id) {
        if existing.kind != AccountKind::Offline {
            bail!("Этот UUID уже занят Microsoft-аккаунтом");
        }
        existing
    } else {
        Account {
            id: id.clone(),
            name: name.into(),
            kind: AccountKind::Offline,
            skin_url: None,
            added_at: chrono::Utc::now().timestamp(),
        }
    };
    state.put("accounts", &id, &account)?;
    state
        .settings
        .write()
        .unwrap_or_else(|e| e.into_inner())
        .selected_account = Some(id);
    state.persist_settings()?;
    state.log("info", "accounts", "Выбран локальный офлайн-аккаунт. Microsoft-авторизация и проверка владения для него не выполняются.");
    state.changed();
    Ok(account)
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn java_compatible_offline_uuid_and_validation() {
        assert_eq!(
            player_id("Notch").unwrap(),
            "b50ad385829d3141a2167e7d7539ba7f"
        );
        assert_ne!(player_id("Notch").unwrap(), player_id("notch").unwrap());
        for invalid in [
            "",
            "ab",
            "12345678901234567",
            "Player Name",
            "../escape",
            "Игрок",
            "Name\n",
            "--demo",
        ] {
            assert!(player_id(invalid).is_err(), "accepted {invalid:?}");
        }
        assert!(player_id("Player_123").is_ok());
    }
    #[test]
    fn legacy_saved_accounts_still_require_microsoft() {
        let account: Account =
            serde_json::from_str(r#"{"id":"test","name":"Player","skin_url":null,"added_at":0}"#)
                .unwrap();
        assert_eq!(account.kind, AccountKind::Microsoft);
        assert!(serde_json::from_str::<Account>(
            r#"{"id":"test","name":"Player","skin_url":null,"added_at":0,"kind":"fake"}"#
        )
        .is_err());
    }
}

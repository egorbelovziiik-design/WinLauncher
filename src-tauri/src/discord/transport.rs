//! Discord's real local RPC protocol. This is not a network status simulator.
//! Bounded frames + timeouts + READY/nonce acknowledgements prevent an invalid
//! Application ID or a successful pipe write from being reported as connected.
use anyhow::{bail, Context, Result};
use serde_json::{json, Value};
use std::time::Duration;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::windows::named_pipe::{ClientOptions, NamedPipeClient};

pub struct Connection { pipe: NamedPipeClient }
const MAX_FRAME: usize = 64 * 1024;

pub fn frame_size(header: &[u8; 8]) -> Result<(u32, usize)> {
    let opcode = u32::from_le_bytes(header[..4].try_into()?);
    let size = u32::from_le_bytes(header[4..].try_into()?) as usize;
    if size > MAX_FRAME { bail!("Discord IPC: кадр превышает безопасный лимит"); }
    if opcode > 4 { bail!("Discord IPC: неизвестный opcode"); }
    Ok((opcode, size))
}
fn accepted(value: &Value) -> Result<()> {
    if value["evt"] == "ERROR" || value["data"]["code"].as_u64().is_some_and(|c| c >= 4000) {
        bail!("Discord отклонил запрос. Проверьте Application ID и настройки Rich Presence.");
    }
    Ok(())
}
impl Connection {
    pub async fn connect(application_id: &str) -> Result<Self> {
        let mut last_error = None;
        for index in 0..10 {
            let name = format!(r"\\.\pipe\discord-ipc-{index}");
            let pipe = match ClientOptions::new().open(&name) { Ok(pipe) => pipe, Err(error) => { last_error = Some(error); continue; } };
            let mut connection = Self { pipe };
            connection.send(0, &json!({ "v": 1, "client_id": application_id })).await?;
            let (opcode, ready) = connection.receive().await?;
            accepted(&ready)?;
            if opcode != 1 || ready["evt"] != "READY" { bail!("Discord не подтвердил RPC handshake"); }
            return Ok(connection);
        }
        Err(last_error.context("Discord Desktop не запущен")?.into())
    }
    async fn send(&mut self, opcode: u32, value: &Value) -> Result<()> {
        let payload = serde_json::to_vec(value)?;
        if payload.len() > MAX_FRAME { bail!("Слишком большой запрос Discord"); }
        let mut frame = Vec::with_capacity(payload.len() + 8);
        frame.extend_from_slice(&opcode.to_le_bytes()); frame.extend_from_slice(&(payload.len() as u32).to_le_bytes()); frame.extend_from_slice(&payload);
        tokio::time::timeout(Duration::from_secs(2), self.pipe.write_all(&frame)).await.context("Тайм-аут записи Discord IPC")??;
        Ok(())
    }
    async fn receive(&mut self) -> Result<(u32, Value)> {
        tokio::time::timeout(Duration::from_secs(2), async {
            let mut header = [0; 8]; self.pipe.read_exact(&mut header).await?;
            let (opcode, size) = frame_size(&header)?;
            let mut bytes = vec![0; size]; self.pipe.read_exact(&mut bytes).await?;
            let value: Value = serde_json::from_slice(&bytes)?;
            Ok::<_, anyhow::Error>((opcode, value))
        }).await.context("Discord не ответил вовремя")?
    }
    pub async fn activity(&mut self, activity: Option<Value>) -> Result<()> {
        let nonce = uuid::Uuid::new_v4().to_string();
        let request = json!({ "cmd": "SET_ACTIVITY", "args": { "pid": std::process::id(), "activity": activity }, "nonce": nonce });
        self.send(1, &request).await?;
        // Discord may deliver a PING or an unrelated dispatch before our ACK.
        tokio::time::timeout(Duration::from_secs(3), async {
            for _ in 0..12 {
                let (opcode, response) = self.receive().await?;
                if opcode == 2 { bail!("Discord закрыл IPC-соединение"); }
                if opcode == 3 { self.send(4, &response).await?; continue; }
                if response["nonce"].as_str() == Some(nonce.as_str()) {
                    accepted(&response)?;
                    if opcode != 1 || response["cmd"] != "SET_ACTIVITY" { bail!("Некорректное подтверждение Discord"); }
                    return Ok(());
                }
            }
            bail!("Discord не подтвердил изменение активности");
        }).await.context("Тайм-аут подтверждения Discord activity")?
    }
    pub async fn close(&mut self) { let _ = self.activity(None).await; let _ = self.pipe.shutdown().await; }
}

#[cfg(test)] mod tests {
    use super::*;
    #[test] fn bounded_frames() {
        let mut header = [0; 8]; header[..4].copy_from_slice(&1u32.to_le_bytes()); header[4..].copy_from_slice(&42u32.to_le_bytes());
        assert_eq!(frame_size(&header).unwrap(), (1, 42));
        header[4..].copy_from_slice(&1_000_000u32.to_le_bytes()); assert!(frame_size(&header).is_err());
    }
    #[test] fn rejected_rpc_is_not_success() { assert!(accepted(&json!({"evt":"ERROR","data":{"code":4000}})).is_err()); assert!(accepted(&json!({"cmd":"SET_ACTIVITY","evt":null,"data":{}})).is_ok()); }
}

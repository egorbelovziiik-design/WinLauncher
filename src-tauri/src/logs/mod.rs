use crate::state::AppState;
use anyhow::{bail, Result};
use std::{fs, io::Write, path::Path};

pub fn export(state: &AppState, destination: &Path) -> Result<()> {
    if !["txt", "log"].contains(
        &destination
            .extension()
            .and_then(|s| s.to_str())
            .unwrap_or(""),
    ) || destination.exists()
    {
        bail!("Выберите новое имя .txt или .log");
    }
    for parent in destination.ancestors() {
        crate::security::reject_link(parent)?;
    }
    let mut output = fs::OpenOptions::new()
        .create_new(true)
        .write(true)
        .open(destination)?;
    for line in state.logs.lock().unwrap_or_else(|e| e.into_inner()).iter() {
        writeln!(
            output,
            "{} [{}] [{}] {}",
            line.timestamp, line.level, line.source, line.message
        )?;
    }
    Ok(())
}

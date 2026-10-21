//! Opt-in native acceptance harness. Compiled only with `native-smoke` feature.
//! Uses the exact production engine, real HTTPS APIs, hashes and Windows Java
//! executables. Only UI event emission is absent; nothing is mocked or invented.
use crate::{
    content, java, minecraft,
    models::{InstalledFile, Loader, Profile},
    modpacks, modrinth, profiles, settings,
    state::AppState,
};
use anyhow::{bail, Context, Result};
use serde::Serialize;
use std::{fs, future::Future, path::PathBuf};

#[derive(Serialize)]
struct Check {
    check: String,
    status: String,
    detail: String,
}
async fn check<T, F: Future<Output = Result<T>>>(
    reports: &mut Vec<Check>,
    name: &str,
    future: F,
) -> Option<T> {
    match future.await {
        Ok(value) => {
            println!("PASSED: {name}");
            reports.push(Check {
                check: name.into(),
                status: "PASSED".into(),
                detail:
                    "Executed against the real native backend on Windows; no mock APIs or data."
                        .into(),
            });
            Some(value)
        }
        Err(error) => {
            let detail = format!("{error:#}");
            eprintln!("FAILED: {name}: {detail}");
            reports.push(Check {
                check: name.into(),
                status: "FAILED".into(),
                detail,
            });
            None
        }
    }
}
pub async fn run(root: PathBuf, output: PathBuf) -> Result<bool> {
    if !cfg!(windows) {
        bail!("This harness is intended for Windows x64");
    }
    if root.exists() && fs::read_dir(&root)?.next().is_some() {
        bail!("Refusing to use a non-empty directory. Smoke tests must not touch user worlds.");
    }
    let state = AppState::headless(root.clone())?;
    let mut reports = Vec::new();
    check(
        &mut reports,
        "SQLite settings persist across backend reopen",
        async {
            let mut config = state.config();
            config.theme = "nether".into();
            config.effects = false;
            settings::save(&state, config)?;
            let reopened = AppState::headless(root.clone())?;
            if reopened.config().theme != "nether" || reopened.config().effects {
                bail!("SQLite settings did not survive reopen");
            }
            Ok(())
        },
    )
    .await;
    check(
        &mut reports,
        "Windows CPU / GPU / RAM / disk information",
        async {
            let system = settings::system_info(&state)?;
            if system.total_memory_mb == 0 || system.cpu.is_empty() || system.free_disk_bytes == 0 {
                bail!("Incomplete system information");
            }
            println!(
                "SYSTEM: {} / {} / {} MB",
                system.os, system.arch, system.total_memory_mb
            );
            Ok(())
        },
    )
    .await;
    let manifest = check(&mut reports, "Official Mojang manifest API", async {
        let manifest = minecraft::metadata::manifest(&state).await?;
        if !manifest.versions.iter().any(|v| v.id == "1.21.4")
            || !manifest.versions.iter().any(|v| v.id == "1.8.9")
        {
            bail!("Expected historical versions missing from Mojang manifest");
        }
        println!(
            "MOJANG: {} official versions, latest {:?}",
            manifest.versions.len(),
            manifest.latest
        );
        Ok(manifest)
    })
    .await;
    let vanilla = profiles::new(
        "Native smoke Vanilla".into(),
        "1.21.4".into(),
        Loader::Vanilla,
    );
    check(
        &mut reports,
        "Create and isolate a real native profile",
        async {
            profiles::save(&state, vanilla.clone())?;
            if !state.instance(&vanilla.id)?.join("saves").is_dir() {
                bail!("Profile directories missing");
            }
            Ok(())
        },
    )
    .await;
    check(
        &mut reports,
        "Reject launch without licensed Microsoft account",
        async {
            match minecraft::launch(&state, &vanilla.id, false).await {
                Err(e) if e.to_string().contains("Microsoft") => {
                    if state.has_running() {
                        bail!("Unexpected game process");
                    }
                    Ok(())
                }
                Err(e) => Err(e),
                Ok(_) => bail!("Unlicensed launch was incorrectly allowed"),
            }
        },
    )
    .await;
    for major in [21, 8, 17, 25] {
        check(
            &mut reports,
            &format!("Download and install managed Java {major} with SHA256"),
            async {
                let guard = state.begin_job("java")?;
                let runtime = java::install_managed(&state, major, &guard.token).await?;
                if !runtime.managed
                    || runtime.major != major
                    || !["amd64", "x86_64"].contains(&runtime.architecture.as_str())
                {
                    bail!("Managed Windows x64 runtime was not installed correctly");
                }
                println!("JAVA: {} at {}", runtime.version, runtime.path);
                Ok(())
            },
        )
        .await;
    }
    let installed = if manifest.is_some() {
        check(
            &mut reports,
            "Real Vanilla 1.21.4 client / libraries / natives / assets installation",
            async {
                minecraft::install_only(&state, &vanilla.id).await?;
                let p: Profile = state.get("profiles", &vanilla.id)?;
                if !p.installed {
                    bail!("Minecraft was not marked installed after verification");
                }
                Ok(())
            },
        )
        .await
        .is_some()
    } else {
        false
    };
    if installed {
        check(
            &mut reports,
            "Re-verify real cached Minecraft files",
            minecraft::install_only(&state, &vanilla.id),
        )
        .await;
        let fabric = profiles::new(
            "Native smoke Fabric".into(),
            "1.21.4".into(),
            Loader::Fabric,
        );
        profiles::save(&state, fabric.clone())?;
        let fabric_installed = check(&mut reports, "Real Fabric 1.21.4 installation", async {
            minecraft::install_only(&state, &fabric.id).await?;
            let saved: Profile = state.get("profiles", &fabric.id)?;
            if !saved.installed || saved.loader_version.is_none() {
                bail!("Fabric version was not installed and pinned");
            }
            println!("FABRIC: {:?}", saved.loader_version);
            Ok(())
        })
        .await
        .is_some();
        if fabric_installed {
            let installed_mod = check(
                &mut reports,
                "Real Modrinth search / compatibility / dependency install",
                async {
                    let found =
                        modrinth::search(&state, "sodium", "mod", Some(&fabric), 0, "downloads")
                            .await?;
                    let sodium = found
                        .hits
                        .iter()
                        .find(|p| p.slug == "sodium")
                        .context("Sodium was not returned by Modrinth")?;
                    let guard = state.begin_job("mods")?;
                    let files =
                        modrinth::install(&state, &fabric, &sodium.id, None, &guard.token).await?;
                    let file = files
                        .iter()
                        .find(|f| f.project_id == sodium.id)
                        .context("Root mod not installed")?
                        .clone();
                    if !state
                        .instance(&fabric.id)?
                        .join("mods")
                        .join(&file.filename)
                        .is_file()
                    {
                        bail!("Real mod JAR missing");
                    }
                    Ok(file)
                },
            )
            .await;
            if let Some(file) = installed_mod {
                check(
                    &mut reports,
                    "Disable and re-enable actual mod files",
                    async {
                        let key = profiles::file_id(&file);
                        content::toggle(&state, &key)?;
                        let read: InstalledFile = state.get("installed", &key)?;
                        if read.enabled {
                            bail!("Mod toggle did not persist");
                        }
                        content::toggle(&state, &key)?;
                        Ok(())
                    },
                )
                .await;
                check(&mut reports, "Update real disabled Modrinth project without silently enabling it", async {
                    let versions = modrinth::compatible_versions(&state, &file.project_id, &fabric, "mod").await?;
                    let older = versions.get(1).and_then(|v| v["id"].as_str()).context("No older compatible published version available for update acceptance")?;
                    { let guard = state.begin_job("mods")?; modrinth::install(&state, &fabric, &file.project_id, Some(older), &guard.token).await?; }
                    let key = profiles::file_id(&file); content::toggle(&state, &key)?;
                    { let guard = state.begin_job("mods")?; modrinth::install(&state, &fabric, &file.project_id, Some(&file.version_id), &guard.token).await?; }
                    let read: InstalledFile = state.get("installed", &key)?;
                    if read.enabled || read.version_id != file.version_id || !state.instance(&fabric.id)?.join("mods").join(format!("{}.disabled", read.filename)).is_file() { bail!("Disabled content update did not retain the disabled state and install the requested real version"); }
                    content::toggle(&state, &key)?; Ok(())
                }).await;
                check(
                    &mut reports,
                    "Export and reimport a real Modrinth .mrpack",
                    async {
                        let pack = root.join("native-smoke-export.mrpack");
                        modpacks::export(&state, &fabric.id, &pack)?;
                        let imported = modpacks::import(&state, &pack).await?;
                        if imported.id == fabric.id || imported.loader != Loader::Fabric {
                            bail!("Modpack did not create a new isolated profile");
                        }
                        if !state
                            .instance(&imported.id)?
                            .join("mods")
                            .join(&file.filename)
                            .is_file()
                        {
                            bail!("Imported modpack did not contain the real downloaded JAR");
                        }
                        Ok(())
                    },
                )
                .await;
                check(&mut reports, "Remove mod into safe backup storage", async {
                    content::remove(&state, &profiles::file_id(&file))?;
                    if state
                        .instance(&fabric.id)?
                        .join("mods")
                        .join(&file.filename)
                        .exists()
                    {
                        bail!("Mod still exists in active mods directory");
                    }
                    Ok(())
                })
                .await;
                check(&mut reports, "Resolve and download real Iris required dependencies", async {
                    let found = modrinth::search(&state, "iris", "mod", Some(&fabric), 0, "downloads").await?;
                    let iris = found.hits.iter().find(|p| p.slug == "iris").context("Iris was not returned by Modrinth")?;
                    let guard = state.begin_job("mods")?;
                    let files = modrinth::install(&state, &fabric, &iris.id, None, &guard.token).await?;
                    if files.len() < 2 { bail!("This API version did not provide a required dependency; dependency acceptance was not exercised"); }
                    println!("MODRINTH: {} real Iris/dependency files downloaded and hash verified", files.len()); Ok(())
                }).await;
            }
        }
        // Official installers run for real; any errors remain FAILED, not success.
        for loader in [Loader::Forge, Loader::Neoforge, Loader::Quilt] {
            let p = profiles::new(
                format!("Native smoke {}", loader.as_str()),
                "1.21.4".into(),
                loader.clone(),
            );
            profiles::save(&state, p.clone())?;
            check(
                &mut reports,
                &format!("Real {} 1.21.4 installation", loader.as_str()),
                async {
                    minecraft::install_only(&state, &p.id).await?;
                    let saved: Profile = state.get("profiles", &p.id)?;
                    if !saved.installed || saved.loader_version.is_none() {
                        bail!("Loader installation did not persist");
                    }
                    Ok(())
                },
            )
            .await;
        }
        check(
            &mut reports,
            "Migrate real game data with SHA256 and retain original",
            async {
                let original = state.root();
                let destination = root.join("migrated-data");
                settings::migrate(&state, &destination)?;
                if state.root() == original
                    || !original.join("minecraft").is_dir()
                    || !state.root().join("minecraft").is_dir()
                {
                    bail!("Migration did not preserve original data and switch root");
                }
                Ok(())
            },
        )
        .await;
    }
    for name in [
        "Licensed Microsoft OAuth / refresh / multi-account",
        "Rendered Minecraft game session and actual playtime",
        "CurseForge API and ZIP modpack with a real key",
        "Discord Desktop Rich Presence",
        "RAM in a running licensed Minecraft JVM",
        "User-world backup and restoration",
        "Windows GUI restart and interactive installer",
    ] {
        reports.push(Check { check: name.into(), status: "NOT TESTED".into(), detail: "Requires credentials, a real licensed session, Discord Desktop or an interactive Windows acceptance environment. No result is simulated.".into() });
    }
    fs::create_dir_all(&output)?;
    let success = !reports.iter().any(|r| r.status == "FAILED");
    let json = serde_json::json!({ "source_commit": std::env::var("GITHUB_SHA").unwrap_or_default(), "generated_at": chrono::Utc::now().to_rfc3339(), "runtime": "Windows x64 native Rust production engine (headless event sink only)", "checks": reports });
    fs::write(
        output.join("BACKEND-SMOKE.json"),
        serde_json::to_vec_pretty(&json)?,
    )?;
    let mut markdown = "# Real native Windows backend verification\n\nAll checks use the production Rust engine. No network APIs, file downloads, runtimes, mod JARs or database operations are mocked. This does not establish a rendered licensed Minecraft session.\n\n".to_string();
    for item in &reports {
        markdown.push_str(&format!(
            "## {}: {}\n\n{}\n\n",
            item.check, item.status, item.detail
        ));
    }
    fs::write(output.join("BACKEND-SMOKE.md"), markdown)?;
    Ok(success)
}

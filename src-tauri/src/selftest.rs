//! Head-less end-to-end check used by CI:
//! installs a real version, resolves Java, builds the command line and starts
//! the game, then writes everything it saw into a report file.

use std::io::{BufRead, BufReader, Read};
use std::process::{Command, Stdio};
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};

use tauri::{AppHandle, Manager};

use crate::models::{LaunchAccount, LoaderRef, Profile};
use crate::state::AppState;
use crate::{java, launch, mojang, util};

pub fn run(app: &AppHandle, version: &str) -> String {
    let mut report = String::new();
    report.push_str("# WinLaucher self-test\n\n");
    report.push_str(&format!("Minecraft version: {version}\n"));

    let state = app.state::<AppState>();
    let paths = state.paths();
    let settings = state.settings();
    let game_dir = paths.mc.join("selftest");
    let _ = std::fs::create_dir_all(&game_dir);

    let profile = Profile {
        id: "selftest".to_string(),
        name: "SelfTest".to_string(),
        mc_version: version.to_string(),
        loader: LoaderRef { kind: "vanilla".to_string(), version: None },
        game_dir: game_dir.to_string_lossy().to_string(),
        ram_mb: 1024,
        java_path: None,
        jvm_args: String::new(),
        width: 854,
        height: 480,
        fullscreen: false,
        created_at: chrono::Utc::now().to_rfc3339(),
        last_played_at: None,
        source: "manual".to_string(),
        modpack: None,
    };
    let account = LaunchAccount {
        name: "SelfTest".to_string(),
        uuid: "00000000000000000000000000000000".to_string(),
        access_token: "0".to_string(),
        user_type: "legacy".to_string(),
        xuid: String::new(),
        user_properties: Vec::new(),
    };

    let prepared = tauri::async_runtime::block_on(async {
        let merged = mojang::load_merged(app, version).await?;
        mojang::ensure_version(app, &merged, Some(game_dir.as_path()), None).await?;
        let (missing, total, _, _) = mojang::install_status(app, &merged);
        let runtime = java::resolve_for_launch(app, &profile, &settings, merged.required_java()).await?;
        Ok::<_, String>((merged, runtime, missing, total))
    });

    let (merged, runtime, missing, total) = match prepared {
        Ok(values) => values,
        Err(error) => {
            report.push_str(&format!("\nPREPARE FAILED: {error}\n"));
            return report;
        }
    };

    report.push_str(&format!(
        "Java: {} ({})\nMain class: {}\nNatives: {}\nMissing files: {missing}/{total}\n",
        runtime.version,
        runtime.path,
        merged.main_class,
        merged.natives_dir.display()
    ));

    let built = launch::build_launch(&merged, &profile, &account, &runtime, &paths, &settings, false);
    let (java_path, args) = match built {
        Ok(values) => values,
        Err(error) => {
            report.push_str(&format!("\nBUILD ARGS FAILED: {error}\n"));
            return report;
        }
    };
    report.push_str(&format!("Java executable: {}\nArguments: {}\n", java_path.display(), args.len()));

    let mut command = Command::new(&java_path);
    command
        .args(&args)
        .current_dir(&game_dir)
        .stdin(Stdio::null())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());
    util::hide_console(&mut command);

    let mut child = match command.spawn() {
        Ok(child) => child,
        Err(error) => {
            report.push_str(&format!("\nSPAWN FAILED: {error}\n"));
            return report;
        }
    };

    let lines: Arc<Mutex<Vec<String>>> = Arc::new(Mutex::new(Vec::new()));
    let mut streams: Vec<Box<dyn Read + Send>> = Vec::new();
    if let Some(stdout) = child.stdout.take() {
        streams.push(Box::new(stdout));
    }
    if let Some(stderr) = child.stderr.take() {
        streams.push(Box::new(stderr));
    }
    for stream in streams {
        let lines = Arc::clone(&lines);
        std::thread::spawn(move || {
            for line in BufReader::new(stream).lines().map_while(Result::ok) {
                if let Ok(mut buffer) = lines.lock() {
                    if buffer.len() < 400 {
                        buffer.push(line);
                    }
                }
            }
        });
    }

    let started = Instant::now();
    let mut code: Option<i32> = None;
    loop {
        match child.try_wait() {
            Ok(Some(status)) => {
                code = status.code();
                break;
            }
            Ok(None) => {}
            Err(_) => break,
        }
        if started.elapsed() > Duration::from_secs(150) {
            let _ = child.kill();
            let _ = child.wait();
            report.push_str("\nKilled after 150s (the game kept running).\n");
            break;
        }
        std::thread::sleep(Duration::from_millis(400));
    }
    std::thread::sleep(Duration::from_millis(600));

    let captured = lines.lock().map(|buffer| buffer.clone()).unwrap_or_default();
    report.push_str(&format!(
        "\nExit code: {code:?}\nUptime: {}s\nCaptured lines: {}\n\n--- game output ---\n",
        started.elapsed().as_secs(),
        captured.len()
    ));
    for line in captured.iter().take(150) {
        report.push_str(line);
        report.push('\n');
    }
    if let Some(reason) = captured.first().map(|_| launch::identify_reason(&captured)) {
        if !reason.is_empty() {
            report.push_str(&format!("\nIdentified reason: {reason}\n"));
        }
    }
    report
}

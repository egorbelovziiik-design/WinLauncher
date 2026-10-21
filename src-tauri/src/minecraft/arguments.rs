use crate::{
    minecraft::metadata::{allowed, RuleContext},
    models::Profile,
};
use anyhow::{bail, Context, Result};
use serde_json::Value;
use std::collections::HashMap;

/// Java 9+ expands @files before constructing java.class.path. Keep the main
/// class and FabricMcEmu on the actual Windows command line for GPU drivers.
/// Game/authentication arguments must never be written into this file.
pub fn can_use_argfile(args: &[String]) -> bool {
    args.iter().all(|arg| arg.is_ascii())
}

pub fn write_jvm_argfile(path: &std::path::Path, args: &[String]) -> Result<()> {
    use std::io::Write;
    if cfg!(windows) && !can_use_argfile(args) {
        bail!("Windows Java argument files must use ASCII options; use relative managed paths or direct arguments");
    }
    for parent in path.ancestors() {
        crate::security::reject_link(parent)?;
    }
    let mut file = std::fs::OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(path)?;
    for arg in args {
        if arg.contains('\0') {
            bail!("NUL в JVM-аргументе");
        }
        let escaped = arg
            .replace('\\', "\\\\")
            .replace('"', "\\\"")
            .replace('\n', "\\n")
            .replace('\r', "\\r")
            .replace('\t', "\\t");
        writeln!(file, "\"{}\"", escaped)?;
    }
    file.sync_all()?;
    Ok(())
}

pub fn expand(input: &str, variables: &HashMap<String, String>) -> Result<String> {
    let mut result = input.to_owned();
    for (key, value) in variables {
        result = result.replace(&format!("${{{key}}}"), value);
    }
    if result.contains("${") {
        bail!("Версия требует неподдерживаемый аргумент: {}", input);
    }
    if result.contains('\0') {
        bail!("Недопустимый NUL в аргументе");
    }
    Ok(result)
}

pub fn collect(
    list: Option<&Value>,
    context: &RuleContext,
    vars: &HashMap<String, String>,
) -> Result<Vec<String>> {
    let mut output = Vec::new();
    for item in list.and_then(Value::as_array).into_iter().flatten() {
        if let Some(s) = item.as_str() {
            output.push(expand(s, vars)?);
        } else {
            let rules = item["rules"].as_array().map(Vec::as_slice);
            if !allowed(rules, context) {
                continue;
            }
            if let Some(s) = item["value"].as_str() {
                output.push(expand(s, vars)?);
            } else if let Some(values) = item["value"].as_array() {
                for v in values {
                    output.push(expand(
                        v.as_str().context("Некорректный аргумент версии")?,
                        vars,
                    )?);
                }
            } else {
                bail!("Неизвестный формат аргумента Mojang");
            }
        }
    }
    Ok(output)
}

/// Legacy Mojang uses a quoted argument string, never a shell command.
pub fn split_legacy(input: &str) -> Result<Vec<String>> {
    let mut result = Vec::new();
    let mut current = String::new();
    let mut quote = None;
    for c in input.chars() {
        if c == '"' || c == '\'' {
            if quote == Some(c) {
                quote = None;
            } else if quote.is_none() {
                quote = Some(c);
            } else {
                current.push(c);
            }
        } else if c.is_whitespace() && quote.is_none() {
            if !current.is_empty() {
                result.push(std::mem::take(&mut current));
            }
        } else {
            current.push(c);
        }
    }
    if quote.is_some() {
        bail!("Незакрытая кавычка в аргументах версии");
    }
    if !current.is_empty() {
        result.push(current);
    }
    Ok(result)
}

pub fn memory_args(
    profile: &Profile,
    total_mb: u64,
    available_mb: u64,
    allow_pressure: bool,
) -> Result<Vec<String>> {
    if profile.min_memory_mb > profile.max_memory_mb || profile.min_memory_mb < 256 {
        bail!("Некорректные настройки памяти");
    }
    if profile.max_memory_mb as u64 > total_mb {
        bail!("Выделено больше памяти, чем установлено в системе");
    }
    if !allow_pressure && profile.max_memory_mb as u64 + 1024 > available_mb {
        bail!("MEMORY_WARNING: Для Windows желательно оставить 1 ГБ свободной памяти. Уменьшите RAM или подтвердите запуск с риском нехватки памяти.");
    }
    Ok(vec![
        format!("-Xms{}M", profile.min_memory_mb),
        format!("-Xmx{}M", profile.max_memory_mb),
    ])
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn argument_files_preserve_spaces_quotes_and_windows_paths() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("java args.txt");
        write_jvm_argfile(
            &path,
            &[
                r"-Dpath=C:\My Games\Minecraft".into(),
                "-Dquoted=\"value\"".into(),
            ],
        )
        .unwrap();
        let text = std::fs::read_to_string(path).unwrap();
        assert!(text.contains(r"C:\\My Games\\Minecraft"));
        assert!(text.contains(r#"\"value\""#));
        assert!(!text.contains("accessToken"));
        assert!(!can_use_argfile(&["-Dcustom=Мир".into()]));
        assert!(can_use_argfile(&[
            "-cp".into(),
            "../../minecraft/libraries/a.jar".into()
        ]));
    }
    #[test]
    fn substitutes_without_shell_evaluation() {
        let vars = HashMap::from([("game_directory".into(), "C:\\My Games\\Minecraft".into())]);
        assert_eq!(
            expand("${game_directory}", &vars).unwrap(),
            "C:\\My Games\\Minecraft"
        );
        assert!(expand("${unknown}", &vars).is_err());
    }
    #[test]
    fn legacy_quotes() {
        assert_eq!(
            split_legacy("--username \"Player Name\" --x 1").unwrap(),
            vec!["--username", "Player Name", "--x", "1"]
        );
        assert!(split_legacy("\"bad").is_err());
    }
    #[test]
    fn ram_is_real_jvm_args() {
        let mut p = crate::profiles::new(
            "Test".into(),
            "1.21.4".into(),
            crate::models::Loader::Vanilla,
        );
        p.max_memory_mb = 6144;
        assert_eq!(
            memory_args(&p, 16384, 10000, false).unwrap(),
            vec!["-Xms1024M", "-Xmx6144M"]
        );
        assert!(memory_args(&p, 16384, 4000, false).is_err());
        assert!(memory_args(&p, 16384, 4000, true).is_ok());
        assert!(memory_args(&p, 4096, 4000, true).is_err());
    }
}

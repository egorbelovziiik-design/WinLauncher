//! Bounded, local-only crash diagnosis. Exit code 1 alone is never a diagnosis.
use crate::{downloads, security, state::AppState};
use anyhow::{Context, Result};
use serde::{Deserialize, Serialize};
use std::{
    fs,
    io::{Read, Write},
    path::{Path, PathBuf},
    time::UNIX_EPOCH,
};

const RECORD: &str = ".winlauncher-last-launch.json";
const MAX_REPORT: u64 = 1024 * 1024;
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct Diagnosis {
    pub profile_id: String,
    pub timestamp: i64,
    pub exit_code: Option<i32>,
    pub kind: String,
    pub title: String,
    pub detail: String,
    pub advice: Vec<String>,
    pub evidence: Vec<String>,
    pub report_path: Option<String>,
    pub java_path: Option<String>,
}

pub fn classify(
    text: &str,
    code: Option<i32>,
    stopped: bool,
) -> (&'static str, &'static str, &'static str, Vec<String>) {
    let lower = text.to_lowercase();
    let lines: Vec<_> = lower.lines().collect();
    let frame = lines
        .iter()
        .position(|l| l.contains("problematic frame:"))
        .map(|i| {
            lines
                .iter()
                .skip(i + 1)
                .take(3)
                .copied()
                .collect::<Vec<_>>()
                .join(" ")
        })
        .unwrap_or_default();
    let (kind, title, detail, advice): (_, _, _, &[&str]) = if stopped {
        (
            "stopped",
            "Процесс остановлен пользователем",
            "Принудительная остановка не считается ошибкой загрузчика.",
            &[],
        )
    } else if code == Some(0) {
        (
            "closed",
            "Minecraft завершён",
            "Процесс Java завершился с кодом 0.",
            &[],
        )
    } else if frame.contains("atio6axx.dll") || frame.contains("atioglxx.dll") {
        ("amd_opengl", "Сбой AMD OpenGL", "JVM упала внутри драйвера AMD, а не просто вернула код 1. Замена Fabric сама по себе не гарантирует исправление драйверного сбоя.", &[
            "Установите подходящий вашей видеокарте драйвер с сайта производителя ПК/ноутбука или AMD и перезагрузите Windows. Не скачивайте отдельные DLL с посторонних сайтов.",
            "Проверьте чистый профиль Vanilla без модов, шейдеров и сторонних оверлеев. На ноутбуке проверьте, какая видеокарта используется.",
            "Переключитесь на управляемую Temurin через восстановление профиля. Это исключает случайную системную Java, но не ремонтирует видеодрайвер."])
    } else if frame.contains("nvoglv")
        || frame.contains("ig9icd")
        || frame.contains("igxelpicd")
        || lower.contains("glfw error 65542")
        || lower.contains("does not support opengl")
    {
        ("opengl", "Ошибка OpenGL / видеодрайвера", "Minecraft не смог корректно инициализировать графический драйвер.", &[
            "Обновите подходящий драйвер у производителя видеокарты/ноутбука; проверьте поддержку OpenGL вашей моделью.",
            "Проверьте чистую Vanilla того же Minecraft без шейдеров и оверлеев. Не подменяйте opengl32.dll случайными файлами."])
    } else if lower.contains("outofmemoryerror")
        || lower.contains("could not reserve enough space")
        || lower.contains("insufficient memory")
    {
        ("memory", "Ошибка выделения памяти", "В логах есть ошибка памяти Java или операционной системы.", &[
            "Проверьте доступную RAM и файл подкачки Windows. Слишком большой Xmx также вызывает отказ запуска.",
            "Закройте лишние приложения и измените RAM в настройках профиля; не выделяйте игре всю память компьютера."])
    } else if lower.contains("unsupportedclassversionerror")
        || lower.contains("compiled by a more recent version of the java runtime")
    {
        ("java_version", "Несовместимая Java", "Классы игры или модов требуют другую версию Java.", &[
            "Восстановите профиль: лаунчер выберет управляемую Java по метаданным Minecraft и загрузчика.",
            "Проверьте, что все моды рассчитаны на эту версию Minecraft и Java."])
    } else if lower.contains("unrecognized vm option")
        || lower.contains("unrecognized option:")
        || lower.contains("could not create the java virtual machine")
    {
        ("jvm_arguments", "Ошибка параметров JVM", "Java отклонила параметры до запуска игры.", &[
            "Сбросьте пользовательские JVM-аргументы через восстановление профиля либо исправьте их в редакторе."])
    } else if lower.contains("modresolutionexception")
        || lower.contains("incompatible mods found")
        || lower.contains("incompatible mod set")
        || lower.contains("mixin apply failed")
        || lower.contains("mixintransformererror")
    {
        ("mods", "Несовместимость модов / Fabric", "В журнале обнаружена ошибка разрешения зависимостей или Mixin.", &[
            "Моды для 1.16.5, 1.21.4 и 26.2 не взаимозаменяемы. Проверьте версии модов и обязательные зависимости.",
            "Создайте чистый Fabric-профиль этой версии и добавляйте моды по одному. Восстановление не удаляет существующие моды и миры."])
    } else if lower.contains("classnotfoundexception")
        || lower.contains("could not find or load main class")
        || lower.contains("unsatisfiedlinkerror")
    {
        ("libraries", "Ошибка библиотеки / classpath", "Не удалось загрузить класс или нативную библиотеку.", &[
            "Восстановите профиль для повторной SHA-проверки файлов и загрузчика. При необходимости проверьте историю защиты Windows, не отключая антивирус."])
    } else if lower.contains("exception_access_violation")
        || lower.contains("a fatal error has been detected")
    {
        ("native_crash", "Авария нативного кода JVM", "В журнале есть фатальная ошибка нативного кода. Нужен раздел Problematic frame из hs_err, а не только код завершения.", &[
            "Откройте папку профиля и сохраните hs_err_pid*.log. Проверьте выбранную Java и видеодрайвер; протестируйте чистый профиль."])
    } else {
        ("unknown", "Minecraft завершился с ошибкой", "Сам код завершения не объясняет причину. Лаунчер не будет угадывать её без подтверждения в журнале.", &[
            "Посмотрите последние строки консоли и hs_err/crash-reports. Для Fabric сравните запуск чистого профиля без модов."])
    };
    (
        kind,
        title,
        detail,
        advice.iter().map(|s| (*s).to_string()).collect(),
    )
}

fn bounded_text(path: &Path) -> Result<String> {
    for parent in path.ancestors() {
        security::reject_link(parent)?;
    }
    let mut data = Vec::new();
    fs::File::open(path)?
        .take(MAX_REPORT)
        .read_to_end(&mut data)?;
    Ok(String::from_utf8_lossy(&data).into_owned())
}
fn candidates(root: &Path, folder: &str, out: &mut Vec<(i64, PathBuf)>) -> Result<()> {
    let directory = if folder.is_empty() {
        root.to_path_buf()
    } else {
        security::within(root, folder)?
    };
    if !directory.is_dir() {
        return Ok(());
    }
    for entry in fs::read_dir(directory)?.take(512).flatten() {
        let name = entry.file_name().to_string_lossy().into_owned();
        if !((name.starts_with("hs_err_pid") && name.ends_with(".log"))
            || (name.starts_with("crash-") && name.ends_with(".txt")))
        {
            continue;
        }
        let path = security::within(
            root,
            &if folder.is_empty() {
                name
            } else {
                format!("{folder}/{name}")
            },
        )?;
        if path.is_file() {
            let time = fs::metadata(&path)?
                .modified()?
                .duration_since(UNIX_EPOCH)?
                .as_millis() as i64;
            out.push((time, path));
        }
    }
    Ok(())
}
fn latest_report(root: &Path, since: i64) -> Result<Option<(i64, PathBuf)>> {
    let mut found = Vec::new();
    candidates(root, "", &mut found)?;
    candidates(root, "crash-reports", &mut found)?;
    let base = security::within(root, ".winlauncher-crashes")?;
    if base.is_dir() {
        let mut directories = fs::read_dir(base)?.take(2048).flatten().collect::<Vec<_>>();
        directories
            .sort_by_key(|e| std::cmp::Reverse(e.metadata().ok().and_then(|m| m.modified().ok())));
        for entry in directories.into_iter().take(32) {
            let name = entry.file_name().to_string_lossy().into_owned();
            if security::safe_id(&name).is_ok() {
                candidates(root, &format!(".winlauncher-crashes/{name}"), &mut found)?;
            }
        }
    }
    Ok(found
        .into_iter()
        .filter(|(t, _)| *t >= since - 2000)
        .max_by_key(|(t, _)| *t))
}

fn diagnosis(profile: &str, code: Option<i32>, stopped: bool, text: &str) -> Diagnosis {
    let (kind, title, detail, advice) = classify(text, code, stopped);
    let evidence = text
        .lines()
        .filter(|l| {
            let lower = l.to_lowercase();
            lower.contains("exception")
                || lower.contains("jre version")
                || lower.contains("java vm:")
                || lower.contains(".dll+")
                || lower.contains("error")
                || lower.contains("incompatible")
        })
        .take(16)
        .map(|l| security::redact(l, &[]).chars().take(600).collect())
        .collect();
    Diagnosis {
        profile_id: profile.into(),
        timestamp: chrono::Utc::now().timestamp_millis(),
        exit_code: code,
        kind: kind.into(),
        title: title.into(),
        detail: detail.into(),
        advice,
        evidence,
        report_path: None,
        java_path: None,
    }
}

pub fn record(
    state: &AppState,
    profile: &str,
    code: Option<i32>,
    stopped: bool,
    started: i64,
    java_path: &str,
) -> Result<Diagnosis> {
    let root = state.instance(profile)?;
    let recent = state
        .logs
        .lock()
        .unwrap_or_else(|e| e.into_inner())
        .iter()
        .filter(|l| l.source == "minecraft" && l.timestamp >= started)
        .map(|l| l.message.clone())
        .collect::<Vec<_>>()
        .join("\n");
    let report = latest_report(&root, started)?;
    let text = if let Some((_, path)) = &report {
        format!("{}\n{recent}", bounded_text(path)?)
    } else {
        recent
    };
    let mut result = diagnosis(profile, code, stopped, &text);
    result.java_path = Some(java_path.into());
    result.report_path = report.and_then(|(_, p)| {
        p.strip_prefix(&root)
            .ok()
            .map(|p| p.to_string_lossy().replace('\\', "/"))
    });
    let target = security::within(&root, RECORD)?;
    let temporary = security::within(
        &root,
        &format!(".launch-report-{}.tmp", uuid::Uuid::new_v4()),
    )?;
    let mut output = fs::OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(&temporary)?;
    output.write_all(&serde_json::to_vec_pretty(&result)?)?;
    output.sync_all()?;
    drop(output);
    downloads::atomic_replace(&temporary, &target)?;
    Ok(result)
}

pub fn latest(state: &AppState, profile: &str) -> Result<Option<Diagnosis>> {
    let _: crate::models::Profile = state.get("profiles", profile)?;
    let root = state.instance(profile)?;
    let file = security::within(&root, RECORD)?;
    if file.is_file() {
        let result: Diagnosis = serde_json::from_str(&bounded_text(&file)?)?;
        anyhow::ensure!(
            result.profile_id == profile,
            "Отчёт принадлежит другому профилю"
        );
        return Ok(Some(result));
    }
    // Diagnose the user's existing hs_err from an older launcher, without
    // requiring another crash or pretending a new launch was performed.
    if let Some((time, path)) = latest_report(&root, 0)? {
        let mut result = diagnosis(profile, None, false, &bounded_text(&path)?);
        result.timestamp = time;
        result.report_path = Some(
            path.strip_prefix(&root)?
                .to_string_lossy()
                .replace('\\', "/"),
        );
        return Ok(Some(result));
    }
    Ok(None)
}

pub fn open_report_folder(state: &AppState, profile: &str) -> Result<()> {
    let report = latest(state, profile)?.context("Нет отчёта об ошибке")?;
    let root = state.instance(profile)?;
    let folder = match report.report_path {
        Some(path) => security::within(&root, &path)?
            .parent()
            .context("Нет папки отчёта")?
            .to_owned(),
        None => root,
    };
    open::that(folder)?;
    Ok(())
}

pub fn log_level(line: &str) -> &'static str {
    let lower = line.to_lowercase();
    if lower.contains("error")
        || lower.contains("fatal")
        || lower.contains("exception_access_violation")
        || lower.contains("problematic frame")
    {
        "error"
    } else if lower.contains("warn") {
        "warn"
    } else {
        "info"
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn amd_problematic_frame_is_not_generic_exit_one() {
        let text = "# A fatal error has been detected by the Java Runtime Environment:\n# EXCEPTION_ACCESS_VIOLATION (0xc0000005)\n# Problematic frame:\n# C  [atio6axx.dll+0x192b60]\n# JRE version: Java(TM) SE Runtime Environment (21.0.12+7)";
        assert_eq!(classify(text, Some(1), false).0, "amd_opengl");
        assert_eq!(
            classify("Dynamic libraries:\natio6axx.dll", Some(1), false).0,
            "unknown"
        );
        assert_eq!(classify(text, Some(1), true).0, "stopped");
        assert_eq!(log_level("# A fatal error has been detected"), "error");
    }
    #[test]
    fn common_launch_failures_are_distinguished() {
        for (text, kind) in [
            (
                "Unrecognized VM option 'UseConcMarkSweepGC'",
                "jvm_arguments",
            ),
            ("UnsupportedClassVersionError", "java_version"),
            ("java.lang.OutOfMemoryError: Java heap space", "memory"),
            ("Incompatible mods found!", "mods"),
            ("GLFW error 65542", "opengl"),
            ("Could not find or load main class", "libraries"),
        ] {
            assert_eq!(classify(text, Some(1), false).0, kind);
        }
        assert_eq!(classify("", Some(0), false).0, "closed");
        assert_eq!(classify("", Some(1), false).0, "unknown");
    }
}

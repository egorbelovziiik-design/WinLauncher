use std::path::{Component, Path, PathBuf};

#[derive(Debug, Clone)]
pub struct Paths {
    pub data: PathBuf,
    pub logs: PathBuf,
    pub cache: PathBuf,
    pub java: PathBuf,
    pub mc: PathBuf,
}

impl Paths {
    pub fn new(mc_dir: &str) -> Self {
        let data = dirs::data_dir()
            .unwrap_or_else(|| PathBuf::from("."))
            .join("WinLaucher");
        let mc = PathBuf::from(mc_dir);
        Self {
            logs: data.join("logs"),
            cache: data.join("cache"),
            java: data.join("java"),
            data,
            mc,
        }
    }

    pub fn settings_file(&self) -> PathBuf {
        self.data.join("settings.json")
    }
    pub fn profiles_file(&self) -> PathBuf {
        self.data.join("profiles.json")
    }
    pub fn accounts_file(&self) -> PathBuf {
        self.data.join("accounts.json")
    }
    pub fn curseforge_file(&self) -> PathBuf {
        self.data.join("curseforge.key")
    }
    pub fn versions(&self) -> PathBuf {
        self.mc.join("versions")
    }
    pub fn libraries(&self) -> PathBuf {
        self.mc.join("libraries")
    }
    pub fn assets(&self) -> PathBuf {
        self.mc.join("assets")
    }
    pub fn version_dir(&self, id: &str) -> PathBuf {
        self.versions().join(id)
    }
    pub fn profile_dir(&self, id: &str) -> PathBuf {
        self.mc.join("profiles").join(id)
    }
    pub fn ensure(&self) -> std::io::Result<()> {
        for p in [
            &self.data,
            &self.logs,
            &self.cache,
            &self.java,
            &self.mc,
            &self.versions(),
            &self.libraries(),
            &self.assets(),
            &self.assets().join("objects"),
            &self.assets().join("indexes"),
        ] {
            std::fs::create_dir_all(p)?;
        }
        Ok(())
    }
}

/// Ensures `candidate` stays inside `root` (defence against path traversal).
pub fn safe_join(root: &Path, candidate: &str) -> Result<PathBuf, String> {
    let rel = Path::new(candidate);
    if rel.is_absolute() || candidate.contains("..") {
        return Err(format!("Unsafe path rejected: {candidate}"));
    }
    for comp in rel.components() {
        match comp {
            Component::Normal(_) => {}
            _ => return Err(format!("Unsafe path rejected: {candidate}")),
        }
    }
    let joined = root.join(rel);
    let root_norm = normalize(root);
    let joined_norm = normalize(&joined);
    if !joined_norm.starts_with(&root_norm) {
        return Err(format!("Path escapes root: {candidate}"));
    }
    Ok(joined)
}

fn normalize(p: &Path) -> PathBuf {
    let mut out = PathBuf::new();
    for c in p.components() {
        match c {
            Component::ParentDir => {
                out.pop();
            }
            Component::CurDir => {}
            other => out.push(other.as_os_str()),
        }
    }
    out
}

pub fn sanitize_name(name: &str) -> String {
    let mut out: String = name
        .chars()
        .map(|c| match c {
            'a'..='z' | 'A'..='Z' | '0'..='9' | '-' | '_' | '.' => c,
            ' ' => '-',
            _ => '_',
        })
        .collect();
    if out.is_empty() {
        out = "profile".into();
    }
    if out.len() > 48 {
        out.truncate(48);
    }
    out
}

pub fn sha1_hex(data: &[u8]) -> String {
    use sha1::{Digest, Sha1};
    let mut h = Sha1::new();
    h.update(data);
    hex::encode(h.finalize())
}

pub fn sha512_hex(data: &[u8]) -> String {
    use sha2::{Digest, Sha512};
    let mut h = Sha512::new();
    h.update(data);
    hex::encode(h.finalize())
}

pub fn offset_now() -> i64 {
    chrono::Utc::now().timestamp()
}

#[tokio::main]
async fn main() {
    let mut args = std::env::args().skip(1);
    let Some(root) = args.next() else { eprintln!("Usage: native-smoke <NEW isolated data directory> <report directory>"); std::process::exit(2); };
    let Some(output) = args.next() else { eprintln!("Report directory is required"); std::process::exit(2); };
    match winlauncher_lib::smoke::run(std::path::PathBuf::from(root), std::path::PathBuf::from(output)).await {
        Ok(true) => {},
        Ok(false) => std::process::exit(1),
        Err(error) => { eprintln!("Native smoke harness failed: {error:#}"); std::process::exit(2); }
    }
}

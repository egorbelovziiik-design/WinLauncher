
use serde::{Deserialize, Serialize};
use std::{collections::HashMap, path::{Path, PathBuf}, sync::Mutex};
use tauri::{AppHandle, Emitter, Manager, State};
use futures_util::StreamExt;

pub struct AppState { pub game_process: Mutex<Option<std::process::Child>>, pub logs: Mutex<Vec<String>>, pub downloads: Mutex<Vec<DownloadItem>>, pub stages: Mutex<Vec<serde_json::Value>>, pub cancelled: std::sync::atomic::AtomicBool }
impl Default for AppState { fn default()->Self{ Self{game_process:Mutex::new(None),logs:Mutex::new(vec![]),downloads:Mutex::new(vec![]),stages:Mutex::new(vec![]),cancelled:std::sync::atomic::AtomicBool::new(false)} } }

#[derive(Serialize,Deserialize,Clone,Debug)]
pub struct DownloadItem { pub id:String, pub name:String, pub url:String, pub total:u64, pub downloaded:u64, pub speed:u64, pub percent:f64, pub status:String, pub error:Option<String> }
#[derive(Serialize,Deserialize,Clone,Debug)]
pub struct Account { pub id:String, pub kind:String, pub username:String, pub uuid:String, pub access_token:Option<String>, pub refresh_token:Option<String>, pub expires_at:Option<i64> }
#[derive(Serialize,Deserialize,Clone,Debug)]
pub struct Profile { pub id:String, pub name:String, pub version:String, pub loader:String, pub loader_version:Option<String>, pub game_dir:String, pub java_path:Option<String>, pub ram_mb:u32 }
#[derive(Serialize,Deserialize,Clone,Debug)]
pub struct Settings { pub language:String, pub theme:String, pub custom_theme:serde_json::Value, pub ram_mb:u32, pub jvm_args:String, pub java_path:String, pub game_dir:String, pub resolution:[u32;2], pub fullscreen:bool, pub close_after_launch:bool, pub minimize_after_launch:bool, pub auto_update:bool, pub max_downloads:u32, pub curseforge_api_key:String, pub active_account:Option<String>, pub active_profile:Option<String>, pub onboarded:bool }
impl Default for Settings { fn default()->Self{ Self{language:"ru".into(),theme:"dark".into(),custom_theme:serde_json::json!({"primary":"#22c55e","background":"#0b0f14"}),ram_mb:4096,jvm_args:"".into(),java_path:"".into(),game_dir:"".into(),resolution:[1280,720],fullscreen:false,close_after_launch:false,minimize_after_launch:false,auto_update:true,max_downloads:8,curseforge_api_key:"".into(),active_account:None,active_profile:None,onboarded:false} } }

fn base_dir(app:&AppHandle)->PathBuf{ let mut p = dirs::data_dir().unwrap_or_else(||PathBuf::from(".")); p.push("WinLaucher"); std::fs::create_dir_all(&p).ok(); p }
fn mc_dir(app:&AppHandle)->PathBuf{ let s=load_settings(app); if !s.game_dir.is_empty(){ let p=PathBuf::from(&s.game_dir); std::fs::create_dir_all(&p).ok(); return p; } let mut p=base_dir(app); p.push("minecraft"); std::fs::create_dir_all(&p).ok(); p }
fn read_json<T: for<'de> Deserialize<'de>+Default>(p:&Path)->T{ std::fs::read_to_string(p).ok().and_then(|s|serde_json::from_str(&s).ok()).unwrap_or_default() }
fn write_json<T:Serialize>(p:&Path,v:&T){ if let Some(par)=p.parent(){std::fs::create_dir_all(par).ok();} std::fs::write(p,serde_json::to_string_pretty(v).unwrap_or_default()).ok(); }
fn load_settings(app:&AppHandle)->Settings{ read_json(&base_dir(app).join("settings.json")) }
fn log(app:&AppHandle,state:&State<AppState>,msg:&str){ let line=format!("[{}] {}",chrono::Local::now().format("%H:%M:%S"),msg); if let Ok(mut l)=state.logs.lock(){ l.push(line.clone()); if l.len()>2000{l.drain(0..500);} } use std::io::Write; if let Ok(mut f)=std::fs::OpenOptions::new().create(true).append(true).open(base_dir(app).join("winlaucher.log")){ let _=writeln!(f,"{}",line); } let _=app.emit("launcher-log",line); }
fn stage(app:&AppHandle,state:&State<AppState>,id:&str,label:&str,status:&str,detail:&str){ stage_inner(app,state,id,label,status,detail,true) }
fn stage_inner(app:&AppHandle,state:&State<AppState>,id:&str,label:&str,status:&str,detail:&str,write_log:bool){ if write_log{ log(app,state,&format!("{}: {}",label,detail)); } let v=serde_json::json!({"id":id,"label":label,"status":status,"detail":detail}); if let Ok(mut st)=state.stages.lock(){ if let Some(x)=st.iter_mut().find(|x|x["id"]==id){ *x=v.clone(); } else { st.push(v.clone()); } } let _=app.emit("launch-stage",v); }
fn parse_java_major(text:&str)->Option<u32>{ // "1.8.0_402" -> 8, "21.0.5" -> 21
  let v=text.split('"').nth(1)?; let mut parts=v.split('.'); let first:u32=parts.next()?.parse().ok()?; if first==1{ parts.next()?.parse().ok() } else { Some(first) } }
async fn java_major_of(exe:&Path)->Option<u32>{ let out=tokio::time::timeout(std::time::Duration::from_secs(10),tokio::process::Command::new(exe).arg("-version").output()).await.ok()?.ok()?; parse_java_major(&format!("{}{}",String::from_utf8_lossy(&out.stderr),String::from_utf8_lossy(&out.stdout))) }
fn http_client()->reqwest::Client{ static C:std::sync::OnceLock<reqwest::Client>=std::sync::OnceLock::new(); C.get_or_init(||reqwest::Client::builder().user_agent("WinLaucher/1.0").connect_timeout(std::time::Duration::from_secs(20)).timeout(std::time::Duration::from_secs(600)).pool_max_idle_per_host(8).build().expect("http client")).clone() }
fn sha1_file(p:&Path)->String{ use sha1::{Sha1,Digest}; let data=std::fs::read(p).unwrap_or_default(); let mut h=Sha1::new(); h.update(&data); hex::encode(h.finalize()) }
fn sha256_bytes(b:&[u8])->String{ use sha2::{Sha256,Digest}; let mut h=Sha256::new(); h.update(b); hex::encode(h.finalize()) }

async fn download_file(app:&AppHandle,state:&State<'_,AppState>,url:&str,dest:&Path,expected_sha1:Option<&str>,name:&str)->Result<(),String>{
 if dest.exists(){ if let Some(s)=expected_sha1{ if sha1_file(dest)==s { return Ok(()); } } else if dest.metadata().map(|m|m.len()>0).unwrap_or(false){ return Ok(()); } }
 if let Some(par)=dest.parent(){ std::fs::create_dir_all(par).map_err(|e|e.to_string())?; }
 // path traversal guard: dest must not contain ..
 let id=uuid::Uuid::new_v4().to_string();
 let client=http_client();
 for attempt in 0..4u32 { if is_cancelled(state){ return Err("Отменено пользователем".into()); }
   let existing = if dest.with_extension("part").exists(){ std::fs::metadata(dest.with_extension("part")).map(|m|m.len()).unwrap_or(0)} else {0};
   let mut req=client.get(url); if existing>0{ req=req.header("Range",format!("bytes={}-",existing)); }
   match req.send().await {
     Ok(resp)=>{ if !resp.status().is_success() && resp.status().as_u16()!=206 { if attempt==3{return Err(format!("HTTP {} {}",resp.status(),url));} tokio::time::sleep(std::time::Duration::from_secs(1<<attempt)).await; continue; }
       let total=resp.content_length().unwrap_or(0)+existing;
       let mut item=DownloadItem{id:id.clone(),name:name.to_string(),url:url.to_string(),total,downloaded:existing,speed:0,percent:0.0,status:"downloading".into(),error:None};
       { if let Ok(mut d)=state.downloads.lock(){ d.push(item.clone()); } }
       let part=dest.with_extension("part");
       let mut file=tokio::fs::OpenOptions::new().create(true).append(existing>0 && resp.status().as_u16()==206).write(true).open(&part).await.map_err(|e|e.to_string())?;
       if !(existing>0 && resp.status().as_u16()==206){ file=tokio::fs::File::create(&part).await.map_err(|e|e.to_string())?; item.downloaded=0; }
       if is_cancelled(state){ item.status="cancelled".into(); let _=app.emit("download-progress",item.clone()); return Err("Отменено пользователем".into()); }
       let mut stream=resp.bytes_stream(); let mut done=item.downloaded; let start=std::time::Instant::now();
       let mut err:Option<String>=None;
       while let Some(chunk)=stream.next().await{ if is_cancelled(state){ item.status="cancelled".into(); let _=app.emit("download-progress",item.clone()); return Err("Отменено пользователем".into()); } match chunk{ Ok(b)=>{ use tokio::io::AsyncWriteExt; if file.write_all(&b).await.is_err(){err=Some("write error".into());break;} done+=b.len() as u64; item.downloaded=done; item.percent=if total>0{done as f64/total as f64*100.0}else{0.0}; item.speed=if start.elapsed().as_secs()>0{done/start.elapsed().as_secs()}else{0}; let _=app.emit("download-progress",item.clone()); }, Err(e)=>{err=Some(e.to_string());break;} } }
       if let Some(e)=err{ if attempt==3{return Err(e);} continue; }
       drop(file); std::fs::rename(&part,dest).map_err(|e|e.to_string())?;
       if let Some(s)=expected_sha1{ if sha1_file(dest)!=s{ std::fs::remove_file(dest).ok(); if attempt==3{return Err(format!("SHA1 mismatch {}",name));} continue; } }
       item.status="done".into(); item.percent=100.0; let _=app.emit("download-progress",item.clone());
       { if let Ok(mut d)=state.downloads.lock(){ if let Some(x)=d.iter_mut().find(|x|x.id==id){*x=item.clone();} } }
       if total>1_000_000 { save_history(app,state); }
       return Ok(());
     }, Err(e)=>{ if attempt==3{return Err(e.to_string());} tokio::time::sleep(std::time::Duration::from_secs(1<<attempt)).await; }
   }
 }
 Err("download failed".into())
}
fn save_history(app:&AppHandle,state:&State<AppState>){ if let Ok(d)=state.downloads.lock(){ write_json(&base_dir(app).join("downloads.json"),&*d); } }

fn rules_allow(lib:&serde_json::Value)->bool{
 if let Some(rules)=lib.get("rules").and_then(|v|v.as_array()){ let mut allow=false;
  for r in rules{ let action=r.get("action").and_then(|v|v.as_str()).unwrap_or(""); let mut ok=true;
   if let Some(os)=r.get("os"){ if let Some(n)=os.get("name").and_then(|v|v.as_str()){ ok = (cfg!(target_os="windows")&&n=="windows")||(cfg!(target_os="macos")&&n=="osx")||(cfg!(target_os="linux")&&n=="linux"); } if ok{ if let Some(a)=os.get("arch").and_then(|v|v.as_str()){ ok = (a=="x86"&&cfg!(target_arch="x86"))||(a!="x86"); } } }
   if ok{ allow = action=="allow"; } }
  return allow; } true }

fn required_java_major(meta:&serde_json::Value)->u32{ meta.get("javaVersion").and_then(|j|j.get("majorVersion")).and_then(|v|v.as_u64()).unwrap_or(8) as u32 }

#[tauri::command] async fn get_versions()->Result<serde_json::Value,String>{ http_client().get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json").header("User-Agent","WinLaucher/1.0").send().await.map_err(|e|e.to_string())?.json().await.map_err(|e|e.to_string()) }
#[tauri::command] fn get_settings(app:AppHandle)->Settings{ load_settings(&app) }
#[tauri::command] fn set_settings(app:AppHandle,settings:Settings)->Settings{ write_json(&base_dir(&app).join("settings.json"),&settings); settings }
#[tauri::command] fn system_info()->serde_json::Value{ let mut sys=sysinfo::System::new_all(); sys.refresh_memory(); serde_json::json!({"total_ram_mb":sys.total_memory()/1024/1024,"free_ram_mb":sys.available_memory()/1024/1024,"os":std::env::consts::OS,"arch":std::env::consts::ARCH}) }
#[tauri::command] fn cancel_operation(app:AppHandle,state:State<AppState>){ state.cancelled.store(true,std::sync::atomic::Ordering::SeqCst); if let Ok(mut l)=state.logs.lock(){ l.push("[ОТМЕНА] Пользователь отменил операцию".into()); } if let Ok(mut d)=state.downloads.lock(){ for x in d.iter_mut(){ if x.status=="downloading"{ x.status="cancelled".into(); } } } let _=app.emit("launch-stage",serde_json::json!({"id":"cancel","label":"Отмена","status":"error","detail":"Операция отменена пользователем"})); let _=app.emit("operation-cancelled",()); }
fn is_cancelled(state:&State<'_,AppState>)->bool{ state.cancelled.load(std::sync::atomic::Ordering::SeqCst) }
fn reset_cancel(state:&State<'_,AppState>){ state.cancelled.store(false,std::sync::atomic::Ordering::SeqCst); }
#[tauri::command] fn get_logs(state:State<AppState>)->Vec<String>{ state.logs.lock().map(|l|l.clone()).unwrap_or_default() }
#[tauri::command] fn get_stages(state:State<AppState>)->Vec<serde_json::Value>{ state.stages.lock().map(|l|l.clone()).unwrap_or_default() }
#[tauri::command] fn clear_logs(state:State<AppState>){ if let Ok(mut l)=state.logs.lock(){l.clear();} }
#[tauri::command] fn get_downloads(app:AppHandle,state:State<AppState>)->Vec<DownloadItem>{ if let Ok(d)=state.downloads.lock(){ if !d.is_empty(){return d.clone();} } read_json::<Vec<DownloadItem>>(&base_dir(&app).join("downloads.json")) }
#[tauri::command] fn list_accounts(app:AppHandle)->Vec<Account>{ read_json(&base_dir(&app).join("accounts.json")) }
#[tauri::command] fn save_accounts(app:AppHandle,accounts:Vec<Account>){ write_json(&base_dir(&app).join("accounts.json"),&accounts); }
#[tauri::command] fn add_offline_account(app:AppHandle,username:String)->Result<Account,String>{ if username.trim().len()<3{return Err("Никнейм минимум 3 символа".into());} let digest=md5::compute(format!("OfflinePlayer:{}",username.trim()).as_bytes()); let mut b=digest.0; b[6]=(b[6]&0x0f)|0x30; b[8]=(b[8]&0x3f)|0x80; let uuid=format!("{}-{}-{}-{}-{}",hex::encode(&b[0..4]),hex::encode(&b[4..6]),hex::encode(&b[6..8]),hex::encode(&b[8..10]),hex::encode(&b[10..16])); let acc=Account{id:uuid::Uuid::new_v4().to_string(),kind:"offline".into(),username:username.trim().to_string(),uuid,access_token:None,refresh_token:None,expires_at:None}; let mut all=list_accounts(app.clone()); all.retain(|a|a.username!=acc.username); all.push(acc.clone()); save_accounts(app,all); Ok(acc) }
#[tauri::command] fn list_profiles(app:AppHandle)->Vec<Profile>{ read_json(&base_dir(&app).join("profiles.json")) }
#[tauri::command] fn save_profiles(app:AppHandle,profiles:Vec<Profile>){ write_json(&base_dir(&app).join("profiles.json"),&profiles); }
#[tauri::command] fn list_installed(app:AppHandle)->Vec<String>{ let d=mc_dir(&app).join("versions"); std::fs::read_dir(d).map(|rd|rd.flatten().filter(|e|e.path().join(format!("{}.json",e.file_name().to_string_lossy())).exists()).map(|e|e.file_name().to_string_lossy().to_string()).collect()).unwrap_or_default() }
#[tauri::command] fn delete_version(app:AppHandle,id:String)->Result<(),String>{ if id.contains("..")||id.contains('/')||id.contains('\\'){return Err("invalid id".into());} let p=mc_dir(&app).join("versions").join(&id); std::fs::remove_dir_all(p).map_err(|e|e.to_string()) }
#[tauri::command] fn open_folder(app:AppHandle,kind:String)->Result<(),String>{ let base=if kind=="game"{mc_dir(&app)}else{base_dir(&app)}; #[cfg(target_os="windows")] std::process::Command::new("explorer").arg(&base).spawn().map_err(|e|e.to_string())?; #[cfg(not(target_os="windows"))] std::process::Command::new("xdg-open").arg(&base).spawn().map_err(|e|e.to_string())?; Ok(()) }
#[tauri::command] fn game_dir_path(app:AppHandle)->String{ mc_dir(&app).to_string_lossy().to_string() }
#[tauri::command] async fn check_java(java_path:String)->Result<String,String>{ let out=tokio::process::Command::new(&java_path).arg("-version").output().await.map_err(|e|e.to_string())?; let s=String::from_utf8_lossy(&out.stderr).to_string(); if s.is_empty(){Ok(String::from_utf8_lossy(&out.stdout).to_string())}else{Ok(s)} }

async fn find_or_install_java(app:&AppHandle,state:&State<'_,AppState>,major:u32,custom:&str)->Result<PathBuf,String>{
 let exe_name=if cfg!(windows){"java.exe"}else{"java"};
 // 1) Пользовательская Java из настроек/профиля
 if !custom.is_empty(){ let p=PathBuf::from(custom); stage(app,state,"java","Этап: Java","active",&format!("Проверяю указанную Java: {}",p.display()));
   match java_major_of(&p).await{ Some(m) if m==major=>{ stage(app,state,"java","Этап: Java","done",&format!("Подходит (Java {})",m)); return Ok(p); } Some(m)=>{ log(app,state,&format!("Указанная Java {} не совпадает с нужной {}, ищу другую",m,major)); } None=>{ return Err(format!("Указанный путь Java не запускается: {}",p.display())); } } }
 // 2) Уже скачанная лаунчером Java — НЕ качаем заново, только быстрая проверка (до 10 сек)
 let runtimes=base_dir(app).join("runtimes"); let dest=runtimes.join(format!("java-{}",major)); let exe=dest.join("bin").join(exe_name);
 stage(app,state,"java","Этап: Java","active",&format!("Ищу готовую Java {} в папке лаунчера...",major));
 if exe.exists(){ if java_major_of(&exe).await==Some(major){ stage(app,state,"java","Этап: Java","done",&format!("Java {} уже установлена: {} (скачивание не нужно)",major,exe.display())); return Ok(exe); } log(app,state,"Кеш Java повреждён — удаляю и скачаю заново"); std::fs::remove_dir_all(&dest).ok(); }
 // 3) Системная Java точной версии
 stage(app,state,"java","Этап: Java","active","Проверяю системную Java...");
 if let Some(m)=java_major_of(Path::new("java")).await{ if m==major{ stage(app,state,"java","Этап: Java","done",&format!("Найдена системная Java {}",m)); return Ok(PathBuf::from("java")); } log(app,state,&format!("Системная Java {} не подходит (нужна {})",m,major)); }
 // 4) Скачивание с Adoptium (прямой binary endpoint, JRE -> fallback JDK), все запросы с таймаутами
 let os=if cfg!(windows){"windows"}else if cfg!(target_os="macos"){"mac"}else{"linux"}; let arch=if cfg!(target_arch="aarch64"){"aarch64"}else{"x64"};
 stage(app,state,"java","Этап: Java","active",&format!("Скачиваю Java {} (Adoptium Temurin, ~50 МБ)...",major));
 let archive=base_dir(app).join(format!("java-{}.zip",major)); let mut downloaded=false; let mut last_err=String::new();
 for kind in ["jre","jdk"]{ let url=format!("https://api.adoptium.net/v3/binary/latest/{}/ga/{}/{}/{}/hotspot/normal/eclipse",major,os,arch,kind);
   stage(app,state,"java","Этап: Java","active",&format!("Загрузка {} Java {}...",kind,major));
   match download_file(app,state,&url,&archive,None,&format!("Java {} ({})",major,kind)).await{ Ok(())=>{ if archive.metadata().map(|m|m.len()>5_000_000).unwrap_or(false){ downloaded=true; break; } last_err="архив слишком маленький".into(); std::fs::remove_file(&archive).ok(); } Err(e)=>{ last_err=e; std::fs::remove_file(&archive).ok(); } } }
 if !downloaded{ stage(app,state,"java","Этап: Java","error",&format!("Не удалось скачать Java {}: {}",major,last_err)); return Err(format!("Не удалось скачать Java {} (Adoptium): {}. Проверьте интернет/антивирус.",major,last_err)); }
 // 5) Распаковка (старая папка удалена выше/здесь) и обязательная проверка запуска
 stage(app,state,"java","Этап: Java","active","Распаковываю Java...");
 std::fs::remove_dir_all(&dest).ok(); std::fs::create_dir_all(&dest).map_err(|e|e.to_string())?;
 { let f=std::fs::File::open(&archive).map_err(|e|e.to_string())?; let mut z=zip::ZipArchive::new(f).map_err(|e|format!("Архив Java повреждён: {}",e))?;
   for i in 0..z.len(){ if is_cancelled(state){ return Err("Отменено пользователем".into()); } let mut file=z.by_index(i).map_err(|e|e.to_string())?; let name=file.name().to_string(); let rel=name.splitn(2,'/').nth(1).unwrap_or(""); if rel.is_empty(){continue;} let outp=dest.join(rel); if !outp.starts_with(&dest){continue;} if file.is_dir(){std::fs::create_dir_all(&outp).ok();} else { if let Some(par)=outp.parent(){std::fs::create_dir_all(par).ok();} let mut o=std::fs::File::create(&outp).map_err(|e|e.to_string())?; std::io::copy(&mut file,&mut o).map_err(|e|e.to_string())?; } } }
 std::fs::remove_file(&archive).ok();
 if !exe.exists(){ stage(app,state,"java","Этап: Java","error","java.exe не найден после распаковки"); return Err("Java не найдена после распаковки".into()); }
 match java_major_of(&exe).await{ Some(m) if m==major=>{ stage(app,state,"java","Этап: Java","done",&format!("Java {} установлена и проверена: {}",major,exe.display())); Ok(exe) } other=>{ stage(app,state,"java","Этап: Java","error",&format!("Скачанная Java не запускается/не той версии: {:?}",other)); Err("Скачанная Java не запускается".into()) } }
}
#[tauri::command] async fn ensure_java(app:AppHandle,state:State<'_,AppState>,major:u32)->Result<String,String>{ let s=load_settings(&app); Ok(find_or_install_java(&app,&state,major,&s.java_path).await?.to_string_lossy().to_string()) }

async fn install_version_internal(app:&AppHandle,state:&State<'_,AppState>,version_id:&str)->Result<serde_json::Value,String>{
 stage(app,state,"install","Этап: установка игры","active","Получаю список версий с piston-meta.mojang.com (таймаут 20 c)...");
 let manifest:serde_json::Value=get_versions().await.map_err(|e|format!("Не получил манифест Mojang: {}",e))?;
 let entry=manifest["versions"].as_array().and_then(|a|a.iter().find(|v|v["id"]==version_id)).ok_or("Версия не найдена в манифесте Mojang")?.clone();
 let url=entry["url"].as_str().unwrap().to_string();
 let mc=mc_dir(app); let vdir=mc.join("versions").join(version_id); std::fs::create_dir_all(&vdir).map_err(|e|e.to_string())?;
 let meta_path=vdir.join(format!("{}.json",version_id));
 // fetch meta
 stage(app,state,"install","Этап: установка игры","active","Скачиваю метаданные версии...");
 let meta_text=http_client().get(&url).header("User-Agent","WinLaucher/1.0").send().await.map_err(|e|format!("Метаданные версии не скачались: {}",e))?.text().await.map_err(|e|e.to_string())?;
 std::fs::write(&meta_path,&meta_text).map_err(|e|e.to_string())?;
 let meta:serde_json::Value=serde_json::from_str(&meta_text).map_err(|e|e.to_string())?;
 stage(app,state,"install","Этап: установка игры","active",&format!("Скачиваю Minecraft {} (клиент, библиотеки, ресурсы)...",version_id));
 // client jar
 if let Some(c)=meta["downloads"]["client"].as_object(){ download_file(app,state,c["url"].as_str().unwrap(),&vdir.join(format!("{}.jar",version_id)),c["sha1"].as_str(),&format!("{}.jar",version_id)).await?; }
 // libraries + natives
 if let Some(libs)=meta["libraries"].as_array(){ let ltotal=libs.len(); for (li,lib) in libs.iter().enumerate(){ if is_cancelled(state){ stage(app,state,"install","Этап: установка игры","error","Отменено пользователем"); return Err("Отменено пользователем".into()); } if li%20==0{ stage_inner(app,state,"install","Этап: установка игры","active",&format!("Библиотеки: {}/{} файлов",li,ltotal),false); } if !rules_allow(lib){continue;}
   if let Some(dl)=lib["downloads"]["artifact"].as_object(){ if let Some(p)=dl["path"].as_str(){ download_file(app,state,dl["url"].as_str().unwrap_or(""),&mc.join("libraries").join(p),dl["sha1"].as_str(),p).await.ok(); } }
   if let Some(classifiers)=lib["downloads"]["classifiers"].as_object(){ for (_k,dl) in classifiers{ if let Some(p)=dl["path"].as_str(){ download_file(app,state,dl["url"].as_str().unwrap_or(""),&mc.join("libraries").join(p),dl["sha1"].as_str(),p).await.ok(); } } }
 } }
 // assets
 let assets=meta["assetIndex"].clone(); let aurl=assets["url"].as_str().unwrap_or("").to_string(); let aid=assets["id"].as_str().unwrap_or("legacy").to_string();
 let adir=mc.join("assets"); std::fs::create_dir_all(adir.join("indexes")).ok(); std::fs::create_dir_all(adir.join("objects")).ok();
 let aindex_path=adir.join("indexes").join(format!("{}.json",aid)); download_file(app,state,&aurl,&aindex_path,assets["sha1"].as_str(),"assets index").await?;
 let aindex:serde_json::Value=read_json(&aindex_path);
 if let Some(objs)=aindex["objects"].as_object(){ let total=objs.len(); let mut done=0usize;
   for (_name,obj) in objs { if is_cancelled(state){ stage(app,state,"install","Этап: установка игры","error","Отменено пользователем"); return Err("Отменено пользователем".into()); } let hash=obj["hash"].as_str().unwrap_or(""); if hash.len()<2{continue;} if done%200==0{ stage_inner(app,state,"install","Этап: установка игры","active",&format!("Ресурсы (assets): {}/{} файлов",done,total),false); } let sub=&hash[0..2]; let dest=adir.join("objects").join(sub).join(hash); let u=format!("https://resources.download.minecraft.net/{}/{}",sub,hash); download_file(app,state,&u,&dest,Some(hash),hash).await.ok(); done+=1; if done%50==0{ let _=app.emit("install-progress",serde_json::json!({"stage":"assets","percent":done as f64/total as f64*100.0,"done":done,"total":total})); } } }
 stage(app,state,"install","Этап: установка игры","done",&format!("Minecraft {} установлен",version_id)); Ok(meta)
}
#[tauri::command] async fn install_version(app:AppHandle,state:State<'_,AppState>,version_id:String)->Result<(),String>{ reset_cancel(&state); install_version_internal(&app,&state,&version_id).await?; Ok(()) }

// Microsoft device-code flow (real endpoints; client_id is the public Minecraft-launcher-compatible Azure app placeholder configurable in code - user can replace)
const MS_CLIENT_ID:&str="00000000402b5328"; // public Xbox app client id used by many launchers for device flow; replace with own Azure app if needed
#[tauri::command] async fn ms_device_code()->Result<serde_json::Value,String>{ let c=http_client(); let v:serde_json::Value=c.post("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode").form(&[("client_id",MS_CLIENT_ID),("scope","XboxLive.signin offline_access")]).send().await.map_err(|e|e.to_string())?.json().await.map_err(|e|e.to_string())?; Ok(v) }
#[tauri::command] async fn ms_poll(app:AppHandle,device_code:String)->Result<Account,String>{
 let c=http_client();
 let ms:serde_json::Value=c.post("https://login.microsoftonline.com/consumers/oauth2/v2.0/token").form(&[("grant_type","urn:ietf:params:oauth:grant-type:device_code"),("client_id",MS_CLIENT_ID),("device_code",&device_code)]).send().await.map_err(|e|e.to_string())?.json().await.map_err(|e|e.to_string())?;
 if ms.get("error").is_some(){ return Ok(Account{id:"pending".into(),kind:"pending".into(),username:ms["error"].as_str().unwrap_or("pending").to_string(),uuid:"".into(),access_token:None,refresh_token:None,expires_at:None}); }
 let access=ms["access_token"].as_str().ok_or("Нет access_token Microsoft")?.to_string(); let refresh=ms["refresh_token"].as_str().map(|s|s.to_string());
 // Xbox Live
 let xbl:serde_json::Value=c.post("https://user.auth.xboxlive.com/user/authenticate").json(&serde_json::json!({"Properties":{"AuthMethod":"RPS","SiteName":"user.auth.xboxlive.com","RpsTicket":format!("d={}",access)},"RelyingParty":"http://auth.xboxlive.com","TokenType":"JWT"})).send().await.map_err(|e|e.to_string())?.json().await.map_err(|e|e.to_string())?;
 let xbl_token=xbl["Token"].as_str().ok_or("Xbox Live auth failed")?.to_string(); let uhs=xbl["DisplayClaims"]["xui"][0]["uhs"].as_str().unwrap_or("").to_string();
 let xsts:serde_json::Value=c.post("https://xsts.auth.xboxlive.com/xsts/authorize").json(&serde_json::json!({"Properties":{"SandboxId":"RETAIL","UserTokens":[xbl_token]},"RelyingParty":"rp://api.minecraftservices.com/","TokenType":"JWT"})).send().await.map_err(|e|e.to_string())?.json().await.map_err(|e|e.to_string())?;
 let xsts_token=xsts["Token"].as_str().ok_or("XSTS auth failed (возможно, нет Xbox профиля / регион)")?.to_string();
 let mcl:serde_json::Value=c.post("https://api.minecraftservices.com/authentication/login_with_xbox").json(&serde_json::json!({"identityToken":format!("XBL3.0 x={};{}",uhs,xsts_token)})).send().await.map_err(|e|e.to_string())?.json().await.map_err(|e|e.to_string())?;
 let mc_token=mcl["access_token"].as_str().ok_or("Minecraft login failed")?.to_string();
 let ent:serde_json::Value=c.get("https://api.minecraftservices.com/entitlements/mcstore").bearer_auth(&mc_token).send().await.map_err(|e|e.to_string())?.json().await.map_err(|e|e.to_string())?;
 let owns=ent["items"].as_array().map(|a|a.iter().any(|i|i["name"]=="game_minecraft"||i["name"]=="product_minecraft")).unwrap_or(false);
 if !owns{ return Err("Аккаунт не владеет Minecraft Java Edition".into()); }
 let prof:serde_json::Value=c.get("https://api.minecraftservices.com/minecraft/profile").bearer_auth(&mc_token).send().await.map_err(|e|e.to_string())?.json().await.map_err(|e|e.to_string())?;
 let acc=Account{id:uuid::Uuid::new_v4().to_string(),kind:"microsoft".into(),username:prof["name"].as_str().unwrap_or("Player").to_string(),uuid:prof["id"].as_str().unwrap_or("").to_string(),access_token:Some(mc_token),refresh_token:refresh,expires_at:Some(chrono::Utc::now().timestamp()+3600*20)};
 let mut all=list_accounts(app.clone()); all.retain(|a|a.uuid!=acc.uuid); all.push(acc.clone()); save_accounts(app,all); Ok(acc)
}

#[tauri::command] async fn modrinth_search(query:String,facets:Option<String>)->Result<serde_json::Value,String>{ let mut u=format!("https://api.modrinth.com/v2/search?query={}&limit=48&index=relevance",urlencoding(&query)); if let Some(f)=facets{ if !f.is_empty(){u.push_str(&format!("&facets={}",urlencoding(&f)));} } http_client().get(u).header("User-Agent","WinLaucher/1.0").send().await.map_err(|e|e.to_string())?.json().await.map_err(|e|e.to_string()) }
fn urlencoding(s:&str)->String{ s.chars().map(|c|match c{'a'..='z'|'A'..='Z'|'0'..='9'|'-'|'_'|'.'|'~'=>c.to_string(),_=>format!("%{:02X}",c as u32)}).collect() }
#[tauri::command] async fn modrinth_versions(project_id:String,game_version:String,loader:String)->Result<serde_json::Value,String>{ let u=format!("https://api.modrinth.com/v2/project/{}/version?game_versions=[\"{}\"]&loaders=[\"{}\"]",project_id,game_version,loader.to_lowercase()); http_client().get(u).header("User-Agent","WinLaucher/1.0").send().await.map_err(|e|e.to_string())?.json().await.map_err(|e|e.to_string()) }
#[tauri::command] async fn curseforge_search(app:AppHandle,query:String)->Result<serde_json::Value,String>{ let key=load_settings(&app).curseforge_api_key; if key.is_empty(){ return Err("CurseForge: укажите API-ключ в Настройках → Дополнительно (получить: console.curseforge.com). Без ключа API недоступен.".into()); } http_client().get(format!("https://api.curseforge.com/v1/mods/search?gameId=432&searchFilter={}&pageSize=36",urlencoding(&query))).header("x-api-key",key).header("Accept","application/json").send().await.map_err(|e|e.to_string())?.json().await.map_err(|e|e.to_string()) }
#[tauri::command] async fn fabric_loaders(game_version:String)->Result<serde_json::Value,String>{ http_client().get(format!("https://meta.fabricmc.net/v2/versions/loader/{}",game_version)).header("User-Agent","WinLaucher/1.0").send().await.map_err(|e|e.to_string())?.json().await.map_err(|e|e.to_string()) }
#[tauri::command] async fn quilt_loaders(game_version:String)->Result<serde_json::Value,String>{ http_client().get(format!("https://meta.quiltmc.org/v3/versions/loader/{}",game_version)).header("User-Agent","WinLaucher/1.0").send().await.map_err(|e|e.to_string())?.json().await.map_err(|e|e.to_string()) }
#[tauri::command] async fn forge_versions()->Result<String,String>{ http_client().get("https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml").send().await.map_err(|e|e.to_string())?.text().await.map_err(|e|e.to_string()) }
#[tauri::command] async fn neoforge_versions()->Result<String,String>{ http_client().get("https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml").send().await.map_err(|e|e.to_string())?.text().await.map_err(|e|e.to_string()) }

fn profile_dir(app:&AppHandle,profile:Option<&Profile>)->PathBuf{ if let Some(p)=profile{ if !p.game_dir.is_empty(){ let d=PathBuf::from(&p.game_dir); std::fs::create_dir_all(&d).ok(); return d; } } mc_dir(app) }

#[tauri::command] async fn install_loader(app:AppHandle,state:State<'_,AppState>,profile:Profile)->Result<String,String>{
 let mc=profile_dir(&app,Some(&profile)); let gv=profile.version.clone(); let loader=profile.loader.to_lowercase();
 if loader=="vanilla"{ install_version_internal(&app,&state,&gv).await?; return Ok(gv); }
 install_version_internal(&app,&state,&gv).await?;
 let meta_major={ let m:serde_json::Value=read_json(&mc_dir(&app).join("versions").join(&gv).join(format!("{}.json",gv))); required_java_major(&m) };
 let java=find_or_install_java(&app,&state,if meta_major==0{17}else{meta_major},"").await?;
 if loader=="fabric"||loader=="quilt"{
   let (base,id_prefix)=if loader=="fabric"{("https://meta.fabricmc.net/v2/versions/loader","fabric-loader")}else{("https://meta.quiltmc.org/v3/versions/loader","quilt-loader")};
   let list:serde_json::Value=http_client().get(format!("{}/{}",base,gv)).header("User-Agent","WinLaucher/1.0").send().await.map_err(|e|e.to_string())?.json().await.map_err(|e|e.to_string())?;
   let arr=list.as_array().ok_or("Нет версий загрузчика для этой версии Minecraft")?; if arr.is_empty(){return Err("Нет версий загрузчика для этой версии".into());}
   let chosen=profile.loader_version.clone().filter(|v|arr.iter().any(|e|e["loader"]["version"]==v.as_str())).unwrap_or_else(||arr[0]["loader"]["version"].as_str().unwrap_or("").to_string());
   let prof_url=format!("{}/{}/{}/profile/json",base,gv,chosen);
   let pmeta:serde_json::Value=http_client().get(&prof_url).header("User-Agent","WinLaucher/1.0").send().await.map_err(|e|e.to_string())?.json().await.map_err(|e|e.to_string())?;
   let new_id=pmeta["id"].as_str().unwrap_or(&format!("{}-{}-{}",id_prefix,chosen,gv)).to_string();
   // download loader libraries
   if let Some(libs)=pmeta["libraries"].as_array(){ for lib in libs{ if let (Some(name),Some(url))=(lib["name"].as_str(),lib["url"].as_str()){ let parts:Vec<&str>=name.split(':').collect(); if parts.len()>=3{ let path=format!("{}/{}/{}/{}-{}.jar",parts[0].replace('.',"/"),parts[1],parts[2],parts[1],parts[2]); let full=format!("{}{}",if url.ends_with('/'){url.to_string()}else{format!("{}/",url)},path); download_file(&app,&state,&full,&mc_dir(&app).join("libraries").join(&path),None,&path).await?; } } } }
   let vdir=mc_dir(&app).join("versions").join(&new_id); std::fs::create_dir_all(&vdir).map_err(|e|e.to_string())?;
   write_json(&vdir.join(format!("{}.json",new_id)),&pmeta);
   // loader profile json inherits vanilla - classpath builder handles inheritsFrom
   log(&app,&state,&format!("{} {} установлен как {}",loader,chosen,new_id)); return Ok(new_id);
 }
 if loader=="forge"||loader=="neoforge"{
   let lv=profile.loader_version.clone().ok_or("Укажите версию Forge/NeoForge (например 1.20.1-47.2.0 для Forge)")?;
   let installer_url=if loader=="forge"{ format!("https://maven.minecraftforge.net/net/minecraftforge/forge/{}/forge-{}-installer.jar",lv,lv) } else { format!("https://maven.neoforged.net/releases/net/neoforged/neoforge/{}/neoforge-{}-installer.jar",lv,lv) };
   let jar=base_dir(&app).join(format!("{}-installer.jar",loader)); download_file(&app,&state,&installer_url,&jar,None,"installer.jar").await?;
   let out=tokio::process::Command::new(&java).args(["-jar",jar.to_str().unwrap(),"--installClient",mc_dir(&app).to_str().unwrap()]).output().await.map_err(|e|e.to_string())?;
   let txt=format!("{}{}",String::from_utf8_lossy(&out.stdout),String::from_utf8_lossy(&out.stderr)); log(&app,&state,&txt);
   if !out.status.success(){ return Err(format!("Установщик {} завершился с ошибкой: {}",loader,txt)); }
   // installer creates version id containing loader version
   let installed=list_installed(app.clone()); let found=installed.iter().find(|v|v.to_lowercase().contains(&loader)&&v.contains(&gv)).cloned().ok_or("Версия загрузчика не найдена после установки")?; return Ok(found);
 }
 Err("Неизвестный загрузчик".into())
}

#[tauri::command] fn list_mods(app:AppHandle,profile:Profile)->Vec<serde_json::Value>{ let d=profile_dir(&app,Some(&profile)).join("mods"); std::fs::read_dir(d).map(|rd|rd.flatten().map(|e|{ let n=e.file_name().to_string_lossy().to_string(); serde_json::json!({"name":n,"disabled":n.ends_with(".disabled"),"size":e.metadata().map(|m|m.len()).unwrap_or(0)})}).collect()).unwrap_or_default() }
#[tauri::command] fn toggle_mod(app:AppHandle,profile:Profile,name:String,disable:bool)->Result<(),String>{ if name.contains("..")||name.contains('/'){return Err("invalid".into());} let d=profile_dir(&app,Some(&profile)).join("mods"); let (from,to)=if disable{(d.join(&name),d.join(format!("{}.disabled",name)))}else{(d.join(format!("{}.disabled",name)),d.join(&name))}; std::fs::rename(from,to).map_err(|e|e.to_string()) }
#[tauri::command] fn delete_mod(app:AppHandle,profile:Profile,name:String)->Result<(),String>{ if name.contains("..")||name.contains('/'){return Err("invalid".into());} std::fs::remove_file(profile_dir(&app,Some(&profile)).join("mods").join(name)).map_err(|e|e.to_string()) }
#[tauri::command] async fn install_mod(app:AppHandle,state:State<'_,AppState>,profile:Profile,project_id:String)->Result<String,String>{
 let vers:serde_json::Value=modrinth_versions(project_id.clone(),profile.version.clone(),profile.loader.clone()).await?; let arr=vers.as_array().ok_or("Нет совместимой версии мода")?; if arr.is_empty(){return Err("Нет совместимой версии мода для выбранной версии/загрузчика".into());}
 let file=arr[0]["files"].as_array().and_then(|f|f.iter().find(|x|x["primary"]==true).or(f.first())).ok_or("Нет файла мода")?.clone();
 let dest_dir=profile_dir(&app,Some(&profile)).join("mods"); std::fs::create_dir_all(&dest_dir).ok();
 let fname=file["filename"].as_str().unwrap().to_string(); download_file(&app,&state,file["url"].as_str().unwrap(),&dest_dir.join(&fname),None,&fname).await?;
 // sha512 check if present
 if let Some(h)=file["hashes"]["sha512"].as_str(){ let data=std::fs::read(dest_dir.join(&fname)).map_err(|e|e.to_string())?; use sha2::{Sha512,Digest}; let mut hasher=Sha512::new(); hasher.update(&data); if hex::encode(hasher.finalize())!=h{ std::fs::remove_file(dest_dir.join(&fname)).ok(); return Err("SHA512 мода не совпал, файл удалён".into()); } }
 Ok(fname)
}
#[tauri::command] async fn install_modpack(app:AppHandle,state:State<'_,AppState>,profile_name:String,project_id:String,game_version:String,loader:String)->Result<Profile,String>{
 let vers=modrinth_versions(project_id,game_version.clone(),loader.clone()).await?; let arr=vers.as_array().ok_or("Нет версии модпака")?; if arr.is_empty(){return Err("Нет совместимой версии модпака".into());}
 let file=arr[0]["files"].as_array().and_then(|f|f.first()).ok_or("Нет файла")?.clone();
 let tmp=base_dir(&app).join("tmp.mrpack"); download_file(&app,&state,file["url"].as_str().unwrap(),&tmp,None,"modpack.mrpack").await?;
 let game_dir=base_dir(&app).join("instances").join(urlencoding(&profile_name)); std::fs::create_dir_all(&game_dir).ok();
 let f=std::fs::File::open(&tmp).map_err(|e|e.to_string())?; let mut z=zip::ZipArchive::new(f).map_err(|e|e.to_string())?;
 let mut index_text=String::new(); { let mut idx=z.by_name("modrinth.index.json").map_err(|e|e.to_string())?; use std::io::Read; idx.read_to_string(&mut index_text).map_err(|e|e.to_string())?; }
 let index:serde_json::Value=serde_json::from_str(&index_text).map_err(|e|e.to_string())?;
 // overrides
 for i in 0..z.len(){ let mut file=z.by_index(i).map_err(|e|e.to_string())?; let name=file.name().to_string(); if let Some(rel)=name.strip_prefix("overrides/"){ if rel.is_empty(){continue;} let outp=game_dir.join(rel); if !outp.starts_with(&game_dir){continue;} if file.is_dir(){std::fs::create_dir_all(&outp).ok();} else { if let Some(p)=outp.parent(){std::fs::create_dir_all(p).ok();} let mut o=std::fs::File::create(&outp).map_err(|e|e.to_string())?; std::io::copy(&mut file,&mut o).map_err(|e|e.to_string())?; } } }
 drop(z);
 if let Some(files)=index["files"].as_array(){ for fl in files{ let path=fl["path"].as_str().unwrap_or(""); if path.contains(".."){continue;} let dest=game_dir.join(path); if let Some(dl)=fl["downloads"].as_array().and_then(|a|a.first()).and_then(|v|v.as_str()){ download_file(&app,&state,dl,&dest,None,path).await?; } } }
 let deps=&index["dependencies"]; let mcver=deps["minecraft"].as_str().unwrap_or(&game_version).to_string();
 let loader_name=if deps.get("fabric-loader").is_some(){"fabric"}else if deps.get("quilt-loader").is_some(){"quilt"}else if deps.get("forge").is_some(){"forge"}else if deps.get("neoforge").is_some(){"neoforge"}else{loader.as_str()};
 let prof=Profile{id:uuid::Uuid::new_v4().to_string(),name:profile_name,version:mcver,loader:loader_name.to_string(),loader_version:None,game_dir:game_dir.to_string_lossy().to_string(),java_path:None,ram_mb:4096};
 let mut all=list_profiles(app.clone()); all.push(prof.clone()); save_profiles(app,all); Ok(prof)
}

#[tauri::command] async fn launch_game(app:AppHandle,state:State<'_,AppState>,version_id:String,account:Account,profile:Profile)->Result<(),String>{
 reset_cancel(&state); { if let Ok(mut st)=state.stages.lock(){ st.clear(); } }
 { let busy=if let Ok(mut g)=state.game_process.lock(){ if let Some(c)=g.as_mut(){ c.try_wait().map(|s|s.is_none()).unwrap_or(false) } else { false } } else { false }; if busy{ return Err("Minecraft уже запущен".into()); } }
 stage(&app,&state,"prepare","Этап: подготовка","active","Проверяю аккаунт, версию и файлы игры..."); let _=app.emit("game-state","preparing");
 // resolve loader version id
 let mut effective_id=version_id.clone();
 if profile.loader.to_lowercase()!="vanilla"{ let installed=list_installed(app.clone()); if let Some(f)=installed.iter().find(|v|v.to_lowercase().contains(&profile.loader.to_lowercase())&&v.contains(&version_id)){ effective_id=f.clone(); } else { effective_id=install_loader(app.clone(),state.clone(),profile.clone()).await?; } } else {
   if !list_installed(app.clone()).contains(&version_id){ install_version_internal(&app,&state,&version_id).await?; }
 }
 let mc=mc_dir(&app); let game_directory=profile_dir(&app,Some(&profile));
 let mut meta:serde_json::Value=read_json(&mc.join("versions").join(&effective_id).join(format!("{}.json",effective_id)));
 // inheritsFrom (Fabric/Quilt/Forge profiles)
 if let Some(parent)=meta["inheritsFrom"].as_str().map(|s|s.to_string()){ let pmeta:serde_json::Value=read_json(&mc.join("versions").join(&parent).join(format!("{}.json",parent))); meta=merge_meta(pmeta,meta); }
 // Проверка целостности client JAR по SHA1 из метаданных Mojang; при повреждении — полная переустановка версии
 {
   let jar_id=meta["inheritsFrom"].as_str().unwrap_or(&effective_id).to_string();
   let jar_path=mc.join("versions").join(&jar_id).join(format!("{}.jar",jar_id));
   let expected=meta["downloads"]["client"]["sha1"].as_str().map(|x|x.to_string());
   let bad=!jar_path.exists() || expected.as_ref().map(|e|sha1_file(&jar_path)!=*e).unwrap_or(false);
   if bad{ log(&app,&state,"Client JAR отсутствует/повреждён — переустановка версии..."); install_version_internal(&app,&state,&version_id).await?; }
 }
 stage(&app,&state,"java","Этап: Java","active","Определяю нужную версию Java для этой версии Minecraft..."); let major=required_java_major(&meta); let settings=load_settings(&app);
 let java_custom=profile.java_path.clone().unwrap_or(settings.java_path.clone());
 let java=find_or_install_java(&app,&state,major,&java_custom).await?;
 // classpath
 let mut cp:Vec<String>=vec![];
 let mut native_dir=mc.join("versions").join(&effective_id).join("natives"); std::fs::create_dir_all(&native_dir).ok();
 if let Some(libs)=meta["libraries"].as_array(){ let ltotal=libs.len(); for (li,lib) in libs.iter().enumerate(){ if is_cancelled(state){ stage(app,state,"install","Этап: установка игры","error","Отменено пользователем"); return Err("Отменено пользователем".into()); } if li%20==0{ stage_inner(app,state,"install","Этап: установка игры","active",&format!("Библиотеки: {}/{} файлов",li,ltotal),false); } if !rules_allow(lib){continue;}
   if let Some(art)=lib["downloads"]["artifact"].as_object(){ if let Some(p)=art["path"].as_str(){ let f=mc.join("libraries").join(p); if f.exists(){cp.push(f.to_string_lossy().to_string());} } }
   else if let Some(name)=lib["name"].as_str(){ let parts:Vec<&str>=name.split(':').collect(); if parts.len()>=3{ let p=format!("{}/{}/{}/{}-{}.jar",parts[0].replace('.',"/"),parts[1],parts[2],parts[1],parts[2]); let f=mc.join("libraries").join(&p); if f.exists(){cp.push(f.to_string_lossy().to_string());} } }
   // natives
   if let Some(natives)=lib["natives"].as_object(){ let key=if cfg!(windows){"windows"}else if cfg!(target_os="macos"){"osx"}else{"linux"}; if let Some(nk)=natives.get(key).and_then(|v|v.as_str()){ if let Some(dl)=lib["downloads"]["classifiers"].get(nk){ if let Some(p)=dl["path"].as_str(){ let jar=mc.join("libraries").join(p); if jar.exists(){ extract_natives(&jar,&native_dir); } } } } }
 } }
 cp.push(mc.join("versions").join(meta["inheritsFrom"].as_str().unwrap_or(&effective_id)).join(format!("{}.jar",meta["inheritsFrom"].as_str().unwrap_or(&effective_id))).to_string_lossy().to_string());
 if !Path::new(cp.last().unwrap()).exists(){ cp.push(mc.join("versions").join(&version_id).join(format!("{}.jar",version_id)).to_string_lossy().to_string()); }
 let sep=if cfg!(windows){";"}else{":"}; let classpath=cp.join(sep);
 let asset_id=meta["assets"].as_str().unwrap_or(meta["assetIndex"]["id"].as_str().unwrap_or("legacy")).to_string();
 let main_class=meta["mainClass"].as_str().unwrap_or("net.minecraft.client.main.Main").to_string();
 let mut jvm_args:Vec<String>=vec![format!("-Xms{}M",profile.ram_mb.min(1024)),format!("-Xmx{}M",profile.ram_mb),format!("-Djava.library.path={}",native_dir.display()),format!("-Djna.tmpdir={}",native_dir.display()),"-cp".into(),classpath];
 let mut game_args:Vec<String>=vec![];
 let replace=|s:&str,acc:&Account|->String{ s.replace("${auth_player_name}",&acc.username).replace("${version_name}",&effective_id).replace("${game_directory}",&game_directory.to_string_lossy()).replace("${assets_root}",&mc.join("assets").to_string_lossy()).replace("${assets_index_name}",&asset_id).replace("${auth_uuid}",&acc.uuid).replace("${auth_access_token}",acc.access_token.as_deref().unwrap_or("0")).replace("${clientid}","").replace("${auth_xuid}","").replace("${user_type}","msa").replace("${version_type}","release").replace("${natives_directory}",&native_dir.to_string_lossy()).replace("${launcher_name}","WinLaucher").replace("${launcher_version}","1.0.0").replace("${classpath}",&cp.join(sep)).replace("${library_directory}",&mc.join("libraries").to_string_lossy()).replace("${classpath_separator}",sep).replace("${user_properties}","{}").replace("${resolution_width}",&settings.resolution[0].to_string()).replace("${resolution_height}",&settings.resolution[1].to_string()) };
 if let Some(args)=meta["arguments"].as_object(){
   if let Some(j)=args["jvm"].as_array(){ for a in j{ if let Some(s)=a.as_str(){jvm_args.push(replace(s,&account));} else if a.get("value").is_some() && rules_allow(a){ if let Some(s)=a["value"].as_str(){jvm_args.push(replace(s,&account));} if let Some(arr)=a["value"].as_array(){ for x in arr{ if let Some(s)=x.as_str(){jvm_args.push(replace(s,&account));} } } } } }
   if let Some(g)=args["game"].as_array(){ for a in g{ if let Some(s)=a.as_str(){game_args.push(replace(s,&account));} else if a.get("value").is_some() && rules_allow(a){ if let Some(s)=a["value"].as_str(){game_args.push(replace(s,&account));} } } }
 } else if let Some(mcargs)=meta["minecraftArguments"].as_str(){ for s in mcargs.split_whitespace(){ game_args.push(replace(s,&account)); } jvm_args.push(format!("-Djava.library.path={}",native_dir.display())); }
 if settings.fullscreen{ game_args.push("--fullscreen".into()); } else { game_args.push("--width".into()); game_args.push(settings.resolution[0].to_string()); game_args.push("--height".into()); game_args.push(settings.resolution[1].to_string()); }
 if !settings.jvm_args.is_empty(){ for s in settings.jvm_args.split_whitespace(){ jvm_args.insert(2,s.to_string()); } }
 stage(&app,&state,"classpath","Этап: сборка запуска","active","Собираю classpath, natives и аргументы JVM...");
 let mut cmd=std::process::Command::new(&java); cmd.args(&jvm_args).arg(&main_class).args(&game_args).current_dir(&game_directory).stdin(std::process::Stdio::null()).stdout(std::process::Stdio::piped()).stderr(std::process::Stdio::piped());
 stage(&app,&state,"launch","Этап: запуск","active",&format!("Запускаю процесс Java: Minecraft {}, RAM {} МБ",effective_id,profile.ram_mb)); log(&app,&state,&format!("Запуск Minecraft {} ({}) RAM={}MB",effective_id,main_class,profile.ram_mb));
 let mut child=cmd.spawn().map_err(|e|format!("Ошибка запуска Java: {}",e))?;
 // stream stdout
 // Дренируем ОБА потока: если stderr не читать, игра зависает при заполнении буфера логов
 if let Some(mut out)=child.stdout.take(){ let app2=app.clone(); std::thread::spawn(move||{ use std::io::Read; let mut buf=[0u8;8192]; loop{ match out.read(&mut buf){ Ok(0)|Err(_)=>break, Ok(n)=>{ let _=app2.emit("game-log",String::from_utf8_lossy(&buf[..n]).to_string()); } } } }); }
 if let Some(mut err)=child.stderr.take(){ let app2=app.clone(); std::thread::spawn(move||{ use std::io::Read; let mut buf=[0u8;8192]; loop{ match err.read(&mut buf){ Ok(0)|Err(_)=>break, Ok(n)=>{ let _=app2.emit("game-log",String::from_utf8_lossy(&buf[..n]).to_string()); } } } }); }
 if let Ok(mut g)=state.game_process.lock(){ *g=Some(child); }
 let _=app.emit("game-state","running");
 // Монитор завершения: сообщает код выхода (0 = норма, иначе краш) и освобождает процесс
 { let app_mon=app.clone(); std::thread::spawn(move||{ loop{ std::thread::sleep(std::time::Duration::from_secs(2)); let st=app_mon.state::<AppState>(); let mut done:Option<std::process::ExitStatus>=None; if let Ok(mut g)=st.game_process.lock(){ if let Some(c)=g.as_mut(){ if let Ok(Some(status))=c.try_wait(){ done=Some(status); } } } if let Some(status)=done{ if let Ok(mut g)=st.game_process.lock(){ if let Some(mut c)=g.take(){ let _=c.wait(); } } let _=app_mon.emit("game-exit",serde_json::json!({"code":status.code()})); let _=app_mon.emit("game-state","idle"); break; } } }); }
 Ok(())
}
fn merge_meta(parent:serde_json::Value,child:serde_json::Value)->serde_json::Value{ let mut m=parent.clone(); if let Some(o)=child.as_object(){ for (k,v) in o{ if k=="libraries"{ let mut libs=parent["libraries"].as_array().cloned().unwrap_or_default(); if let Some(c)=v.as_array(){ libs.extend(c.iter().cloned()); } m["libraries"]=serde_json::Value::Array(libs); } else if k=="arguments"{ // child arguments replace/extend game args: keep parent jvm, child takes precedence overall
   m["arguments"]=v.clone(); } else { m[k]=v.clone(); } } } m }
fn extract_natives(jar:&Path,dest:&Path){ if let Ok(f)=std::fs::File::open(jar){ if let Ok(mut z)=zip::ZipArchive::new(f){ for i in 0..z.len(){ if let Ok(mut file)=z.by_index(i){ let name=file.name().to_string(); if name.ends_with(".dll")||name.ends_with(".so")||name.ends_with(".dylib"){ let outp=dest.join(Path::new(&name).file_name().unwrap_or_default()); if let Ok(mut o)=std::fs::File::create(&outp){ std::io::copy(&mut file,&mut o).ok(); } } } } } } }
#[tauri::command] fn game_running(state:State<AppState>)->bool{ if let Ok(mut g)=state.game_process.lock(){ if let Some(c)=g.as_mut(){ return c.try_wait().map(|s|s.is_none()).unwrap_or(false); } } false }
#[tauri::command] fn kill_game(state:State<AppState>){ if let Ok(mut g)=state.game_process.lock(){ if let Some(mut c)=g.take(){ c.kill().ok(); } } }

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run(){ tauri::Builder::default().plugin(tauri_plugin_dialog::init()).plugin(tauri_plugin_opener::init()).manage(AppState::default()).invoke_handler(tauri::generate_handler![get_versions,get_stages,cancel_operation,get_settings,set_settings,system_info,get_logs,clear_logs,get_downloads,list_accounts,save_accounts,add_offline_account,list_profiles,save_profiles,list_installed,delete_version,open_folder,game_dir_path,check_java,ensure_java,install_version,ms_device_code,ms_poll,modrinth_search,modrinth_versions,curseforge_search,fabric_loaders,quilt_loaders,forge_versions,neoforge_versions,install_loader,list_mods,toggle_mod,delete_mod,install_mod,install_modpack,launch_game,game_running,kill_game]).run(tauri::generate_context!()).expect("error while running WinLaucher"); }

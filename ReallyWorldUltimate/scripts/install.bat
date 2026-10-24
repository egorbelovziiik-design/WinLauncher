@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion

echo ==================================================================
echo  ReallyWorld Ultimate — Установка ядра Purpur/Paper и плагинов
echo ==================================================================

where python >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [ОШИБКА] Установите Python 3.10+ или используйте PowerShell для загрузки ядра.
    pause
    exit /b 1
)

set "ROOT_DIR=%~dp0.."
set "SERVER_DIR=%ROOT_DIR%\server"
set "PLUGINS_DIR=%SERVER_DIR%\plugins"
set "MC_VERSION=1.21.11"

python -c "import hashlib,json,os,urllib.request; s=r'%SERVER_DIR%'; p=r'%PLUGINS_DIR%'; ua={'User-Agent':'RW-Installer/1.0'}; get=lambda u: json.loads(urllib.request.urlopen(urllib.request.Request(u,headers=ua),timeout=30).read().decode()); dl=lambda u,d,h=None,a='sha512': (data:=urllib.request.urlopen(urllib.request.Request(u,headers=ua),timeout=120).read(), (h and hashlib.new(a,data).hexdigest().lower()!=h.lower() and (_ for _ in ()).throw(RuntimeError('Hash mismatch '+d))), open(d,'wb').write(data), print('[OK]',os.path.basename(d))); m=get('https://api.purpurmc.org/v2/purpur/%MC_VERSION%'); b=m['builds']['latest']; bi=get(f'https://api.purpurmc.org/v2/purpur/%MC_VERSION%/{b}'); dl(f'https://api.purpurmc.org/v2/purpur/%MC_VERSION%/{b}/download', os.path.join(s,'server.jar'), bi.get('md5'), 'md5'); [ (vs:=get(f'https://api.modrinth.com/v2/project/{slug}/version'), ch:=next((v for v in vs if any(l in ('paper','purpur','spigot','bukkit') for l in v.get('loaders',[]))),None), ch and (f:=ch['files'][0], dl(f['url'], os.path.join(p,fn), f.get('hashes',{}).get('sha512'), 'sha512'))) for slug,fn in [('viaversion','ViaVersion.jar'),('viabackwards','ViaBackwards.jar'),('luckperms','LuckPerms-Bukkit.jar'),('placeholderapi','PlaceholderAPI.jar'),('worldedit','WorldEdit.jar'),('worldguard','WorldGuard.jar'),('tab-was-taken','TAB.jar'),('grimac','GrimAC.jar'),('coreprotect','CoreProtect.jar')] ]"

echo.
echo [ГОТОВО] Установка успешно завершена! Запускайте server\start.bat
pause

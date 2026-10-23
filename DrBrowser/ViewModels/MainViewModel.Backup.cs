using System.IO;
using System.Windows;
using System.Windows.Input;

namespace DrBrowser;

public sealed partial class MainViewModel
{
    private string _backupStatus = "Резервная копия ещё не создавалась.";
    public string BackupStatus { get => _backupStatus; private set => Set(ref _backupStatus, value); }

    public ICommand CreateBackupCommand => Cmd("backup.create", async () =>
    {
        if (IsPrivate) { BackupStatus = "Резервные копии отключены в приватном окне."; return; }
        if (_shell is null) return;
        var path = _shell.PickSaveFile(
            "DrBrowser OMEGA backup|*.drbackup;*.zip",
            $"DrBrowser-OMEGA-{DateTime.Now:yyyyMMdd-HHmm}.drbackup",
            Environment.GetFolderPath(Environment.SpecialFolder.MyDocuments));
        if (string.IsNullOrWhiteSpace(path)) return;
        try
        {
            BackupStatus = "Создаю согласованный снимок SQLite…";
            var manifest = await Backup.CreateAsync(path, Settings).ConfigureAwait(true);
            BackupStatus = $"Готово: {path} · {manifest.CreatedUtc.ToLocalTime():dd.MM.yyyy HH:mm}";
            StatusMessage = "Резервная копия создана.";
        }
        catch (Exception ex)
        {
            _log.Error("backup create failed", ex);
            BackupStatus = "Ошибка: " + ex.Message;
        }
    });

    public ICommand RestoreBackupCommand => Cmd("backup.restore", async () =>
    {
        if (IsPrivate) { BackupStatus = "Восстановление отключено в приватном окне."; return; }
        if (_shell is null) return;
        var path = _shell.PickOpenFile("DrBrowser OMEGA backup|*.drbackup;*.zip");
        if (string.IsNullOrWhiteSpace(path)) return;
        try
        {
            BackupStatus = "Проверяю manifest, контрольную сумму и состав архива…";
            var manifest = await Backup.ValidateAsync(path).ConfigureAwait(true);
            var confirmed = await _shell.ConfirmAsync(
                "Восстановить резервную копию?",
                $"Создана: {manifest.CreatedUtc.ToLocalTime():dd.MM.yyyy HH:mm}\nВерсия: {manifest.AppVersion}\n\nТекущие настройки и локальная база будут заменены. API-ключи не импортируются. После проверки DrBrowser закроется; запустите его снова.").ConfigureAwait(true);
            if (!confirmed) { BackupStatus = "Восстановление отменено."; return; }

            await Backup.RestoreAsync(path).ConfigureAwait(true);
            BackupStatus = "Копия восстановлена. DrBrowser закрывается для безопасного перезапуска.";
            StatusMessage = BackupStatus;
            await Task.Delay(600).ConfigureAwait(true);
            Application.Current.Shutdown();
        }
        catch (Exception ex)
        {
            _log.Error("backup restore failed", ex);
            BackupStatus = "Архив отклонён: " + ex.Message;
        }
    });

    public ICommand OpenDataFolderCommand => Cmd("backup.data-folder", () =>
    {
        Directory.CreateDirectory(AppPaths.DataRoot);
        _shell?.OpenExternal(AppPaths.DataRoot);
    });
}

# Архитектура DrBrowser

## Слои

```
Views (XAML)  ──►  ViewModels  ──►  Services  ──►  Database / WebView2 / Windows API
     ▲                  │
     └── DataBinding ───┘   (INotifyPropertyChanged, ICommand, без кода в code-behind,
                             кроме чисто визуальных операций: drag&drop вкладок, скриншоты)
```

* **Models** — `ObservableObject`, `RelayCommand`, `AppSettings`, записи (`HistoryEntry`, `Bookmark`,
  `PasswordEntry`, `DownloadItem`, `QuickLink`, `ChatMessage`, `SessionTab`), конвертеры WPF.
* **ViewModels** — `MainViewModel` разбит на четыре partial-файла:
  * `MainViewModel.cs` — жизненный цикл, вкладки, навигация, сессия, масштаб;
  * `MainViewModel.Commands.cs` — команды UI (вкладки, закладки, история, загрузки, пароли, AdBlock, темы);
  * `MainViewModel.Data.cs` — быстрые ссылки, обои, пути, поисковики;
  * `MainViewModel.Ai.cs` — чат DrAI, суммаризация, перевод, объяснение кода.
* **Services** — независимые от WPF классы (кроме `ThemeManager`), поэтому их можно проверять без окна.
* **Browser** — `WebViewManager` создаёт `CoreWebView2Environment` один раз на окно, выдаёт каждому
  `BrowserTab` собственный `WebView2`, навешивает `WebResourceRequested` (AdBlock), `DownloadStarting`,
  `PermissionRequested`, `NewWindowRequested`, `ProcessFailed` (восстановление после падения вкладки).
* **App/MainWindow** реализует `IBrowserShell` — весь UI-контракт (показать внутреннюю страницу,
  спросить разрешение, выбрать файл/папку, открыть внешний файл, fullscreen). ViewModel не знает о `Window`.

## Вкладки

`BrowserTab` хранит URL, заголовок, историю back/forward, закрепление, mute, состояние,
`WebView2` (View) и `CoreWebView2` (Core). `TabManager` управляет коллекцией, активной вкладкой и стеком
закрытых вкладок. Неактивные вкладки приостанавливаются `TrySuspendAsync` по таймеру; при активации
вызывается `ResumeAsync`. Закрытие освобождает WebView2 (`Dispose`), чтобы не держать процессы Chromium.

## Внутренние страницы

`drbrowser://newtab|settings|history|downloads|bookmarks|passwords` — не HTML, а настоящие WPF-страницы
(`Views/*.xaml`) внутри `ContentControl`. Это даёт мгновенное открытие, доступ к SQLite и одинаковое поведение
с системными настройками. Вкладка помечается `TabKind`, поэтому при переключении показывается либо
`InternalHost`, либо слой WebView2.

## Потоки и производительность

* Все обращения к SQLite и файлам — в `Task.Run`, UI-поток не блокируется.
* Настройки сохраняются отложенно (debounce 900 мс), чтобы не писать JSON на каждый клик.
* AdBlock использует lock-free чтение готовых множеств доменов и скомпилированные `Regex`.
* Загрузки обновляют прогресс по таймеру, а не на каждый байт.

## Ошибки и восстановление

* Глобальные обработчики: `DispatcherUnhandledException`, `AppDomain.UnhandledException`,
  `TaskScheduler.UnobservedTaskException` — всё пишется в журнал, приложение продолжает работу.
* `ProcessFailed` у WebView2: вкладка пересоздаётся, пользователю показывается сообщение.
* `--selftest` / `--smoke` — встроенный прогон, возвращающий код возврата.

"""Диалог публикации: YouTube (OAuth2) и TikTok (Playwright).

Диалог сам не загружает файл — он собирает ввод и запускает
:class:`social.youtube_uploader.YouTubeUploadTask` либо
:class:`social.tiktok_uploader.TikTokUploadTask`, показывая прогресс в
:class:`ui.dialogs.progress_dialog.ProgressDialog`.
"""

from __future__ import annotations

import logging
from datetime import datetime, timedelta
from pathlib import Path
from typing import Optional

from PyQt6.QtCore import Qt, QTimer, pyqtSignal
from PyQt6.QtWidgets import (QCheckBox, QComboBox, QDateTimeEdit, QDialog,
                             QDialogButtonBox, QFileDialog, QFormLayout,
                             QFrame, QHBoxLayout, QLabel, QLineEdit,
                             QListWidget, QListWidgetItem, QPushButton,
                             QScrollArea, QTextEdit, QVBoxLayout, QWidget)

from social.auth_manager import (Account, AuthError, AuthManager,
                                 tiktok_playwright_login, tiktok_session_info,
                                 youtube_device_flow)
from social.tiktok_uploader import TikTokMetadata, TikTokUploadTask, validate_video
from social.youtube_uploader import (YOUTUBE_CATEGORIES, VideoMetadata,
                                     YouTubeUploadTask, fetch_channel_info,
                                     to_iso8601)
from ui.dialogs.progress_dialog import ProgressDialog
from ui.theme import color
from utils.config import get_config
from utils.workers import submit

LOG = logging.getLogger(__name__)


class PublishDialog(QDialog):
    """Окно публикации видео на YouTube или в TikTok.

    Сигналы:
        published(str, str): Платформа и ссылка на публикацию.
    """

    published = pyqtSignal(str, str)

    def __init__(self, video_path: Optional[str] = None,
                 title: str = "", tags: str = "",
                 parent: Optional[QWidget] = None) -> None:
        """Args:
            video_path: Готовый файл видео (можно выбрать позже).
            title: Название по умолчанию.
            tags: Теги по умолчанию через запятую.
            parent: Родитель.
        """
        super().__init__(parent)
        self.setWindowTitle("Публикация")
        self.setMinimumSize(780, 720)
        self._auth = AuthManager()
        self._video_path = video_path or ""
        self._task = None
        self._progress: Optional[ProgressDialog] = None
        self._cfg = get_config().publish
        self._build_ui(title, tags)
        self._reload_accounts()

    # ================================================================ UI
    def _build_ui(self, title: str, tags: str) -> None:
        """Собрать интерфейс диалога."""
        outer_layout = QVBoxLayout(self)
        outer_layout.setContentsMargins(16, 14, 16, 12)
        outer_layout.setSpacing(10)

        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setFrameShape(QFrame.Shape.NoFrame)
        content = QWidget()
        scroll.setWidget(content)
        outer_layout.addWidget(scroll, 1)

        layout = QVBoxLayout(content)
        layout.setContentsMargins(4, 2, 8, 2)
        layout.setSpacing(12)

        header = QLabel("Публикация видео")
        header.setObjectName("H1")
        layout.addWidget(header)

        # --- файл
        file_row = QHBoxLayout()
        self.file_edit = QLineEdit(self._video_path)
        self.file_edit.setPlaceholderText("Файл видео (MP4, H.264 + AAC)")
        file_row.addWidget(self.file_edit, 1)
        pick = QPushButton("Выбрать…")
        pick.setObjectName("Ghost")
        pick.clicked.connect(self._pick_file)
        file_row.addWidget(pick)
        layout.addLayout(file_row)

        self.validation_label = QLabel("")
        self.validation_label.setObjectName("Tiny")
        self.validation_label.setWordWrap(True)
        layout.addWidget(self.validation_label)
        self.file_edit.textChanged.connect(self._validate_file)

        # --- аккаунты
        accounts_frame = QFrame()
        accounts_frame.setObjectName("Card")
        acc_layout = QVBoxLayout(accounts_frame)
        acc_layout.setContentsMargins(12, 10, 12, 10)
        acc_layout.setSpacing(8)
        acc_header = QHBoxLayout()
        acc_header.addWidget(QLabel("Привязанные аккаунты"))
        acc_header.addStretch(1)
        self.youtube_login = QPushButton("Привязать YouTube")
        self.youtube_login.setObjectName("Secondary")
        self.youtube_login.clicked.connect(self._login_youtube)
        acc_header.addWidget(self.youtube_login)
        self.tiktok_login = QPushButton("Привязать TikTok")
        self.tiktok_login.setObjectName("Secondary")
        self.tiktok_login.clicked.connect(self._login_tiktok)
        acc_header.addWidget(self.tiktok_login)
        acc_layout.addLayout(acc_header)

        self.accounts_list = QListWidget()
        self.accounts_list.setMaximumHeight(96)
        self.accounts_list.currentItemChanged.connect(lambda *_: self._update_state())
        acc_layout.addWidget(self.accounts_list)

        remove_row = QHBoxLayout()
        self.remove_button = QPushButton("Отвязать выбранный")
        self.remove_button.setObjectName("Danger")
        self.remove_button.clicked.connect(self._remove_account)
        remove_row.addWidget(self.remove_button)
        self.login_status = QLabel("")
        self.login_status.setObjectName("Tiny")
        self.login_status.setWordWrap(True)
        remove_row.addWidget(self.login_status, 1)
        acc_layout.addLayout(remove_row)
        layout.addWidget(accounts_frame)

        # --- платформа
        self.platform_combo = QComboBox()
        self.platform_combo.addItem("YouTube", "youtube")
        self.platform_combo.addItem("TikTok", "tiktok")
        self.platform_combo.setCurrentIndex(
            max(0, self.platform_combo.findData(self._cfg.default_platform)))
        self.platform_combo.currentIndexChanged.connect(lambda _i: self._update_state())
        platform_row = QHBoxLayout()
        platform_row.addWidget(QLabel("Платформа:"))
        platform_row.addWidget(self.platform_combo)
        platform_row.addStretch(1)
        layout.addLayout(platform_row)

        # --- метаданные
        form = QFormLayout()
        form.setHorizontalSpacing(12)
        form.setVerticalSpacing(8)
        self.title_edit = QLineEdit(title or "BeatForge Studio")
        self.title_edit.setMaxLength(100)
        self.title_edit.textChanged.connect(self._update_counter)
        form.addRow("Название:", self.title_edit)

        self.desc_edit = QTextEdit()
        self.desc_edit.setPlainText("Сделано в BeatForge Studio")
        self.desc_edit.setFixedHeight(84)
        form.addRow("Описание:", self.desc_edit)

        self.tags_edit = QLineEdit(tags or self._cfg.default_tags)
        form.addRow("Теги:", self.tags_edit)

        self.category_combo = QComboBox()
        for cid, label in YOUTUBE_CATEGORIES.items():
            self.category_combo.addItem(label, cid)
        form.addRow("Категория:", self.category_combo)

        self.privacy_combo = QComboBox()
        for key, label in (("public", "Публичное"), ("unlisted", "По ссылке"),
                           ("private", "Приватное")):
            self.privacy_combo.addItem(label, key)
        self.privacy_combo.setCurrentIndex(
            max(0, self.privacy_combo.findData(self._cfg.default_privacy)))
        form.addRow("Приватность:", self.privacy_combo)

        self.schedule_check = QCheckBox("Отложенная публикация")
        self.schedule_check.toggled.connect(self._on_schedule_toggled)
        form.addRow("", self.schedule_check)
        self.schedule_edit = QDateTimeEdit(datetime.now() + timedelta(days=1))
        self.schedule_edit.setCalendarPopup(True)
        self.schedule_edit.setDisplayFormat("dd.MM.yyyy HH:mm")
        self.schedule_edit.setEnabled(False)
        form.addRow("Когда:", self.schedule_edit)

        self.kids_check = QCheckBox("Видео для детей (обязательно указать)")
        self.kids_check.setChecked(False)
        form.addRow("", self.kids_check)

        self.counter_label = QLabel("")
        self.counter_label.setObjectName("Tiny")
        form.addRow("", self.counter_label)
        layout.addLayout(form)

        layout.addStretch(1)

        buttons = QDialogButtonBox(QDialogButtonBox.StandardButton.Ok |
                                   QDialogButtonBox.StandardButton.Cancel)
        self.publish_button = buttons.button(QDialogButtonBox.StandardButton.Ok)
        self.publish_button.setText("Опубликовать")
        self.publish_button.setObjectName("Primary")
        buttons.button(QDialogButtonBox.StandardButton.Cancel).setText("Закрыть")
        buttons.accepted.connect(self.publish)
        buttons.rejected.connect(self.reject)
        outer_layout.addWidget(buttons)
        self._update_counter()
        self._validate_file()

    # ================================================================ аккаунты
    def _reload_accounts(self) -> None:
        """Перечитать аккаунты из базы."""
        self.accounts_list.clear()
        try:
            accounts = self._auth.accounts()
        except Exception as exc:
            LOG.warning("Не удалось прочитать аккаунты: %s", exc)
            accounts = []
        for account in accounts:
            suffix = " (истёк)" if account.expired else ""
            item = QListWidgetItem(f"{account.label}{suffix}")
            item.setData(Qt.ItemDataRole.UserRole, account)
            if account.expired:
                item.setForeground(Qt.GlobalColor.gray)
            self.accounts_list.addItem(item)
        self._update_state()

    def _selected_account(self, platform: Optional[str] = None) -> Optional[Account]:
        """Выбранный аккаунт (или первый для платформы)."""
        item = self.accounts_list.currentItem()
        wanted = platform or self.platform_combo.currentData()
        if item is not None:
            account = item.data(Qt.ItemDataRole.UserRole)
            if account and account.platform == wanted and not account.expired:
                return account
        account = self._auth.default_account(wanted)
        return account if account and not account.expired else None

    def _login_youtube(self) -> None:
        """Запустить OAuth-привязку YouTube (device flow)."""
        self.youtube_login.setEnabled(False)
        self.login_status.setText("Открываю страницу подтверждения…")

        def work() -> dict:
            def on_code(url: str, code: str, _expires: int = 0) -> None:
                QTimer.singleShot(0, lambda: self.login_status.setText(
                    f"Код: <b>{code}</b> — откройте {url} и подтвердите"))

            tokens = youtube_device_flow(on_code=on_code)
            try:
                info = fetch_channel_info(auth=self._auth)
            except Exception:
                info = {}
            return {"tokens": tokens, "info": info}

        def done(payload: dict) -> None:
            self.youtube_login.setEnabled(True)
            tokens = payload.get("tokens", {})
            info = payload.get("info", {})
            account = Account(
                platform="youtube",
                account_id=str(info.get("id") or "me"),
                display_name=str(info.get("title") or "YouTube"),
                avatar_url=str(info.get("thumbnail") or ""),
                tokens=tokens,
            )
            self._auth.save_account(account)
            self.login_status.setText("YouTube привязан ✓")
            self._reload_accounts()

        def failed(message: str, _tb: str) -> None:
            self.youtube_login.setEnabled(True)
            self.login_status.setText(f"Привязка не удалась: {message[:200]}")

        submit(work, on_done=done, on_error=failed, name="youtube-login")

    def _login_tiktok(self) -> None:
        """Запустить Playwright-вход в TikTok."""
        self.tiktok_login.setEnabled(False)
        self.login_status.setText("Открываю окно браузера для входа в TikTok…")

        def work() -> dict:
            cookies = tiktok_playwright_login(headless=False)
            info = tiktok_session_info(cookies) if cookies else {}
            return {"cookies": str(cookies or ""), "info": info}

        def done(payload: dict) -> None:
            self.tiktok_login.setEnabled(True)
            cookies = payload.get("cookies", "")
            info = payload.get("info", {}) or {}
            account = Account(
                platform="tiktok",
                account_id=str(info.get("username") or "me"),
                display_name=str(info.get("username") or "TikTok"),
                tokens={"cookies_path": cookies},
                meta={"cookies_path": cookies},
            )
            self._auth.save_account(account)
            self.login_status.setText("TikTok привязан ✓")
            self._reload_accounts()

        def failed(message: str, _tb: str) -> None:
            self.tiktok_login.setEnabled(True)
            self.login_status.setText(f"Вход не удался: {message[:200]}")

        submit(work, on_done=done, on_error=failed, name="tiktok-login")

    def _remove_account(self) -> None:
        """Отвязать выбранный аккаунт."""
        item = self.accounts_list.currentItem()
        if item is None:
            return
        account: Account = item.data(Qt.ItemDataRole.UserRole)
        self._auth.remove_account(account.platform, account.account_id)
        self.login_status.setText("Аккаунт отвязан")
        self._reload_accounts()

    # ================================================================ сервис
    def _pick_file(self) -> None:
        """Выбрать видеофайл для публикации."""
        path, _ = QFileDialog.getOpenFileName(
            self, "Видео для публикации",
            str(get_config().paths.exports_dir),
            "Видео (*.mp4 *.mov *.webm *.mkv);;Все файлы (*)")
        if path:
            self.file_edit.setText(path)

    def _validate_file(self) -> None:
        """Проверить файл на требования платформы."""
        path = self.file_edit.text().strip()
        if not path or not Path(path).exists():
            self.validation_label.setText("Файл не выбран")
            self.validation_label.setStyleSheet(f"color: {color('TEXT_MUTED')};")
            return
        result = validate_video(path)
        warnings = result.get("warnings") or []
        if warnings:
            self.validation_label.setText("⚠ " + "; ".join(warnings))
            self.validation_label.setStyleSheet(f"color: {color('WARNING')};")
        else:
            self.validation_label.setText(
                f"✓ Видео подходит · {result.get('duration', 0):.1f} с")
            self.validation_label.setStyleSheet(f"color: {color('SUCCESS')};")
        self._update_state()

    def _on_schedule_toggled(self, checked: bool) -> None:
        """Отложенная публикация требует private."""
        self.schedule_edit.setEnabled(checked)
        if checked:
            index = self.privacy_combo.findData("private")
            if index >= 0:
                self.privacy_combo.setCurrentIndex(index)

    def _update_counter(self) -> None:
        """Счётчик символов заголовка."""
        length = len(self.title_edit.text())
        self.counter_label.setText(f"{length}/100 символов")

    def _update_state(self) -> None:
        """Обновить доступность кнопки публикации."""
        platform = self.platform_combo.currentData()
        account = self._selected_account(platform)
        has_file = bool(self.file_edit.text().strip()) and \
            Path(self.file_edit.text().strip()).exists()
        ready = account is not None and has_file
        self.publish_button.setEnabled(ready)
        if not has_file:
            self.publish_button.setToolTip("Выберите файл видео")
        elif account is None:
            self.publish_button.setToolTip("Привяжите аккаунт платформы")
        else:
            self.publish_button.setToolTip("")

    def _tags(self) -> list:
        """Разобрать строку тегов."""
        raw = self.tags_edit.text()
        return [tag.strip().lstrip("#") for tag in raw.replace(";", ",").split(",") if tag.strip()]

    # ================================================================ запуск
    def publish(self) -> None:
        """Запустить загрузку выбранной платформы."""
        path = self.file_edit.text().strip()
        if not path or not Path(path).exists():
            return
        platform = self.platform_combo.currentData()
        account = self._selected_account(platform)
        if account is None:
            return
        dialog = ProgressDialog("Публикация", self, allow_close_on_done=True)
        self._progress = dialog
        if platform == "youtube":
            meta = VideoMetadata(
                title=self.title_edit.text().strip(),
                description=self.desc_edit.toPlainText().strip(),
                tags=self._tags(),
                category_id=str(self.category_combo.currentData()),
                privacy=str(self.privacy_combo.currentData()),
                made_for_kids=self.kids_check.isChecked(),
                publish_at=(to_iso8601(self.schedule_edit.dateTime().toPyDateTime())
                            if self.schedule_check.isChecked() else None),
                language="ru",
            )
            task = YouTubeUploadTask(path, meta, account, self)
        else:
            meta = TikTokMetadata(
                caption=self.title_edit.text().strip(),
                hashtags=self._tags(),
                privacy=str(self.privacy_combo.currentData()),
            )
            task = TikTokUploadTask(path, meta, account, self)
        self._task = task
        dialog._cancel_callable = task.cancel  # noqa: SLF001 - единый механизм отмены

        if platform == "youtube":
            task.progress.connect(lambda p, s: dialog.set_progress(p, s))
            task.finished_ok.connect(lambda result: self._on_uploaded(dialog, result))
            task.failed.connect(dialog.fail)
        else:
            task.progress.connect(lambda p, s: dialog.set_progress(p, s))
            task.finished_ok.connect(lambda result: self._on_uploaded(dialog, result))
            task.failed.connect(dialog.fail)

        dialog.cancel_available()
        task.start()
        dialog.exec()

    def _on_uploaded(self, dialog: ProgressDialog, result) -> None:
        """Загрузка завершена: показать ссылку."""
        url = getattr(result, "url", "") or getattr(result, "watch_url", "")
        if url:
            dialog.set_upload_url(url)
        dialog.finish(self.file_edit.text().strip(),
                      "Опубликовано" if url else "Готово")
        self.published.emit(str(self.platform_combo.currentData()), url)

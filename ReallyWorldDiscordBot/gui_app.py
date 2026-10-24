#!/usr/bin/env python3
"""
ReallyWorldKPBot.exe — Standalone Windows Desktop GUI Launcher & Bot Runner.
Владелец: robik0277 (Discord ID: 1381255368650526861) | Application ID: 1563651952506576918
"""
from __future__ import annotations

import asyncio
import logging
import queue
import sys
import tempfile
import threading
import webbrowser
from pathlib import Path

# Explicit imports so PyInstaller --onefile bundles every cog cleanly
import aiosqlite  # noqa: F401
import discord
import bot.cogs.community  # noqa: F401
import bot.cogs.kp_staff  # noqa: F401
import bot.cogs.moderation  # noqa: F401
import bot.cogs.owner  # noqa: F401
import bot.cogs.tickets  # noqa: F401
from bot.config import (
    BUNDLE_DIR,
    DEFAULT_CLIENT_ID,
    DEFAULT_OWNER_ID,
    DEFAULT_OWNER_USERNAME,
    BotConfig,
    build_invite_url,
    save_env_settings,
)
from main import COGS, ReallyWorldBot


async def run_selftest() -> None:
    """Headless self-test executed in CI on Windows to verify the compiled .exe."""
    with tempfile.TemporaryDirectory() as tmp:
        cfg = BotConfig.load()
        cfg.db_path = Path(tmp) / "selftest.db"
        assert cfg.owner_id == DEFAULT_OWNER_ID, f"Unexpected owner_id: {cfg.owner_id}"
        assert cfg.owner_username == DEFAULT_OWNER_USERNAME
        assert cfg.avatar_path.exists(), f"Missing bundled avatar at {cfg.avatar_path}"
        invite = build_invite_url(cfg.client_id, permissions=8)
        assert "1563651952506576918" in invite

        bot_instance = ReallyWorldBot(cfg)
        await bot_instance.db.connect()
        for ext in COGS:
            await bot_instance.load_extension(ext)
        cmds = {c.name for c in bot_instance.tree.get_commands()}
        required = {"owner", "setup", "kp", "check", "tickets", "verify", "giveaway", "ban", "mute", "warn", "help"}
        assert required.issubset(cmds), f"Missing commands: {required - cmds}"
        bot_instance.get_cog("CommunityCog").cog_unload()
        await bot_instance.close()

    out_path = Path.cwd() / "selftest-result.txt"
    out_path.write_text(
        f"SELFTEST_PASS\nOWNER={DEFAULT_OWNER_USERNAME} ({DEFAULT_OWNER_ID})\nCLIENT_ID={DEFAULT_CLIENT_ID}\nCOMMANDS={len(cmds)}\n",
        encoding="utf-8",
    )
    print("SELFTEST_PASS")


class QueueLogHandler(logging.Handler):
    def __init__(self, log_queue: "queue.Queue[str]") -> None:
        super().__init__()
        self.log_queue = log_queue

    def emit(self, record: logging.LogRecord) -> None:
        try:
            self.log_queue.put_nowait(self.format(record))
        except queue.Full:
            pass


def launch_gui() -> None:
    import tkinter as tk
    from tkinter import messagebox

    cfg = BotConfig.load()
    log_queue: queue.Queue[str] = queue.Queue(maxsize=2000)

    root_logger = logging.getLogger()
    root_logger.setLevel(logging.INFO)
    q_handler = QueueLogHandler(log_queue)
    q_handler.setFormatter(logging.Formatter("%(asctime)s | %(levelname)s | %(message)s", "%H:%M:%S"))
    root_logger.addHandler(q_handler)

    BG = "#0c0e14"
    CARD = "#151924"
    INPUT_BG = "#0e111a"
    GOLD = "#ffaa00"
    DISCORD_BLURPLE = "#5865f2"
    GREEN = "#2ecc71"
    RED = "#e74c3c"
    TEXT = "#f3f4f6"
    MUTED = "#9ca3af"

    win = tk.Tk()
    win.title(f"ReallyWorld КП — Управление Discord-Ботом ({DEFAULT_OWNER_USERNAME})")
    win.geometry("820x700")
    win.minsize(760, 640)
    win.configure(bg=BG)

    ico_path = BUNDLE_DIR / "assets" / "avatar_kp.ico"
    if ico_path.exists():
        try:
            win.iconbitmap(default=str(ico_path))
        except Exception:
            pass

    # State for background asyncio bot loop
    state: dict[str, object] = {"loop": None, "bot": None, "thread": None, "running": False}

    def push_log(line: str) -> None:
        log_queue.put(line)

    # ---------- Header Card ----------
    header = tk.Frame(win, bg=CARD, highlightbackground="#283046", highlightthickness=1, padx=20, pady=14)
    header.pack(fill="x", padx=18, pady=(16, 10))

    badge = tk.Label(
        header,
        text=f"👑 Владелец закреплён: {DEFAULT_OWNER_USERNAME}  •  Discord ID: {DEFAULT_OWNER_ID}",
        bg="#261f10",
        fg=GOLD,
        font=("Segoe UI", 9, "bold"),
        padx=10,
        pady=4,
    )
    badge.pack(anchor="w")

    title_row = tk.Frame(header, bg=CARD)
    title_row.pack(fill="x", pady=(8, 2))
    tk.Label(
        title_row,
        text="ReallyWorld КП — Панель Discord-Бота",
        bg=CARD,
        fg=GOLD,
        font=("Segoe UI", 17, "bold"),
    ).pack(side="left")

    status_lbl = tk.Label(
        title_row,
        text="● ОСТАНОВЛЕН",
        bg=CARD,
        fg=MUTED,
        font=("Segoe UI", 10, "bold"),
    )
    status_lbl.pack(side="right")

    tk.Label(
        header,
        text="Готовое Windows-приложение: Набор в КП, Вызовы на проверку (/check), Тикеты, Модерация, Розыгрыши и Авто-Аватарка КП.",
        bg=CARD,
        fg=MUTED,
        font=("Segoe UI", 9),
    ).pack(anchor="w")

    # ---------- Settings Card ----------
    form = tk.Frame(win, bg=CARD, highlightbackground="#283046", highlightthickness=1, padx=20, pady=14)
    form.pack(fill="x", padx=18, pady=(0, 10))

    token_var = tk.StringVar(value="" if cfg.token == "PASTE_YOUR_BOT_TOKEN_HERE" else cfg.token)
    cid_var = tk.StringVar(value=cfg.client_id or DEFAULT_CLIENT_ID)
    guild_var = tk.StringVar(value=str(cfg.dev_guild_id or ""))
    ip_var = tk.StringVar(value=cfg.minecraft_ip)
    show_token_var = tk.BooleanVar(value=False)

    tk.Label(form, text="1. Bot Token (со вкладки Bot → Reset Token на Discord Portal):", bg=CARD, fg=TEXT, font=("Segoe UI", 9, "bold")).grid(
        row=0, column=0, columnspan=2, sticky="w", pady=(0, 4)
    )
    token_entry = tk.Entry(
        form, textvariable=token_var, show="•", bg=INPUT_BG, fg="#ffffff", insertbackground="#ffffff", relief="flat", font=("Consolas", 10)
    )
    token_entry.grid(row=1, column=0, sticky="ew", ipady=6, padx=(0, 8))

    def toggle_token() -> None:
        token_entry.configure(show="" if show_token_var.get() else "•")

    tk.Checkbutton(
        form,
        text="Показать",
        variable=show_token_var,
        command=toggle_token,
        bg=CARD,
        fg=MUTED,
        selectcolor=INPUT_BG,
        activebackground=CARD,
        activeforeground=TEXT,
    ).grid(row=1, column=1, sticky="e")

    row2 = tk.Frame(form, bg=CARD)
    row2.grid(row=2, column=0, columnspan=2, sticky="ew", pady=(10, 0))
    row2.columnconfigure(0, weight=1)
    row2.columnconfigure(1, weight=1)
    row2.columnconfigure(2, weight=1)

    tk.Label(row2, text="Application ID (Client ID):", bg=CARD, fg=TEXT, font=("Segoe UI", 9, "bold")).grid(row=0, column=0, sticky="w")
    tk.Entry(row2, textvariable=cid_var, bg=INPUT_BG, fg="#fcd34d", insertbackground="#ffffff", relief="flat", font=("Consolas", 10)).grid(
        row=1, column=0, sticky="ew", ipady=5, padx=(0, 8), pady=(3, 0)
    )

    tk.Label(row2, text="ID вашего Discord-сервера (опц.):", bg=CARD, fg=TEXT, font=("Segoe UI", 9, "bold")).grid(row=0, column=1, sticky="w")
    tk.Entry(row2, textvariable=guild_var, bg=INPUT_BG, fg="#ffffff", insertbackground="#ffffff", relief="flat", font=("Consolas", 10)).grid(
        row=1, column=1, sticky="ew", ipady=5, padx=(0, 8), pady=(3, 0)
    )

    tk.Label(row2, text="IP сервера Minecraft:", bg=CARD, fg=TEXT, font=("Segoe UI", 9, "bold")).grid(row=0, column=2, sticky="w")
    tk.Entry(row2, textvariable=ip_var, bg=INPUT_BG, fg="#ffffff", insertbackground="#ffffff", relief="flat", font=("Consolas", 10)).grid(
        row=1, column=2, sticky="ew", ipady=5, pady=(3, 0)
    )
    form.columnconfigure(0, weight=1)

    # ---------- Action Buttons ----------
    actions = tk.Frame(win, bg=BG)
    actions.pack(fill="x", padx=18, pady=(0, 10))

    def persist_inputs() -> BotConfig:
        save_env_settings(
            token=token_var.get(),
            client_id=cid_var.get(),
            dev_guild_id=guild_var.get(),
            minecraft_ip=ip_var.get(),
            auto_avatar=True,
        )
        return BotConfig.load()

    def start_bot() -> None:
        if state["running"]:
            messagebox.showinfo("Уже запущен", "Бот уже работает в сети!")
            return
        tok = token_var.get().strip()
        if not tok or len(tok) < 30:
            messagebox.showwarning(
                "Укажите Bot Token",
                "Вставьте ваш токен бота со вкладки Bot → Reset Token (кнопка «Открыть настройки Bot» справа)!",
            )
            return

        new_cfg = persist_inputs()
        state["running"] = True
        status_lbl.configure(text="● ЗАПУСКАЕТСЯ...", fg=GOLD)
        start_btn.configure(state="disabled")
        stop_btn.configure(state="normal")
        push_log("🚀 Инициализация бота ReallyWorld КП...")

        def worker() -> None:
            loop = asyncio.new_event_loop()
            asyncio.set_event_loop(loop)
            bot_obj = ReallyWorldBot(new_cfg)
            state["loop"] = loop
            state["bot"] = bot_obj

            orig_ready = bot_obj.on_ready

            async def wrapped_ready() -> None:
                await orig_ready()
                win.after(0, lambda: status_lbl.configure(text=f"● В СЕТИ: {bot_obj.user}", fg=GREEN))
                push_log(f"✅ Бот успешно вышел в сеть как {bot_obj.user} (ID: {bot_obj.user.id})!")
                push_log(f"🔗 Ссылка приглашения: {build_invite_url(bot_obj.user.id, 8)}")

            bot_obj.on_ready = wrapped_ready  # type: ignore[method-assign]

            try:
                loop.run_until_complete(bot_obj.start(new_cfg.token))
            except discord.PrivilegedIntentsRequired:
                push_log("❌ ОШИБКА INTENTS: Включите все 3 тумблера (Presence, Server Members, Message Content) во вкладке Bot на Discord Portal!")
            except discord.LoginFailure:
                push_log("❌ ОШИБКА АВТОРИЗАЦИИ: Неверный DISCORD_TOKEN. Нажмите Reset Token на портале Discord и вставьте заново.")
            except Exception as exc:
                push_log(f"⚠️ Остановка потока бота: {exc}")
            finally:
                try:
                    if not bot_obj.is_closed():
                        loop.run_until_complete(bot_obj.close())
                except Exception:
                    pass
                loop.close()
                state["running"] = False
                state["bot"] = None
                state["loop"] = None
                win.after(0, on_stopped_ui)

        t = threading.Thread(target=worker, daemon=True)
        state["thread"] = t
        t.start()

    def on_stopped_ui() -> None:
        status_lbl.configure(text="● ОСТАНОВЛЕН", fg=MUTED)
        start_btn.configure(state="normal")
        stop_btn.configure(state="disabled")
        push_log("⏹ Бот остановлен.")

    def stop_bot() -> None:
        bot_obj = state.get("bot")
        loop = state.get("loop")
        if isinstance(bot_obj, ReallyWorldBot) and isinstance(loop, asyncio.AbstractEventLoop):
            push_log("⏳ Корректное завершение сессии Discord и сохранение SQLite...")
            asyncio.run_coroutine_threadsafe(bot_obj.close(), loop)

    def open_invite() -> None:
        cid = "".join(c for c in cid_var.get() if c.isdigit()) or DEFAULT_CLIENT_ID
        url = build_invite_url(cid, permissions=8)
        webbrowser.open(url)
        push_log(f"🌐 Открыта ссылка приглашения в браузере: {url}")

    def copy_invite() -> None:
        cid = "".join(c for c in cid_var.get() if c.isdigit()) or DEFAULT_CLIENT_ID
        url = build_invite_url(cid, permissions=8)
        win.clipboard_clear()
        win.clipboard_append(url)
        push_log(f"📋 Ссылка приглашения скопирована в буфер обмена: {url}")

    def open_portal() -> None:
        cid = "".join(c for c in cid_var.get() if c.isdigit()) or DEFAULT_CLIENT_ID
        webbrowser.open(f"https://discord.com/developers/applications/{cid}/bot")

    start_btn = tk.Button(
        actions,
        text="🚀 Запустить бота",
        command=start_bot,
        bg=GREEN,
        fg="#081a0f",
        font=("Segoe UI", 10, "bold"),
        relief="flat",
        padx=16,
        pady=9,
        cursor="hand2",
    )
    start_btn.pack(side="left", padx=(0, 8))

    stop_btn = tk.Button(
        actions,
        text="⏹ Остановить",
        command=stop_bot,
        state="disabled",
        bg=RED,
        fg="#ffffff",
        font=("Segoe UI", 10, "bold"),
        relief="flat",
        padx=14,
        pady=9,
        cursor="hand2",
    )
    stop_btn.pack(side="left", padx=(0, 8))

    tk.Button(
        actions,
        text="🔗 Пригласить в мой Discord",
        command=open_invite,
        bg=DISCORD_BLURPLE,
        fg="#ffffff",
        font=("Segoe UI", 10, "bold"),
        relief="flat",
        padx=14,
        pady=9,
        cursor="hand2",
    ).pack(side="left", padx=(0, 8))

    tk.Button(
        actions,
        text="📋 Копировать инвайт",
        command=copy_invite,
        bg="#22283a",
        fg=TEXT,
        font=("Segoe UI", 9, "bold"),
        relief="flat",
        padx=12,
        pady=9,
        cursor="hand2",
    ).pack(side="left", padx=(0, 8))

    tk.Button(
        actions,
        text="⚙️ Открыть настройки Bot",
        command=open_portal,
        bg="#22283a",
        fg=GOLD,
        font=("Segoe UI", 9, "bold"),
        relief="flat",
        padx=12,
        pady=9,
        cursor="hand2",
    ).pack(side="right")

    # ---------- Live Console Card ----------
    log_card = tk.Frame(win, bg=CARD, highlightbackground="#283046", highlightthickness=1, padx=14, pady=12)
    log_card.pack(fill="both", expand=True, padx=18, pady=(0, 16))

    tk.Label(log_card, text="Журнал работы бота в реальном времени:", bg=CARD, fg=TEXT, font=("Segoe UI", 9, "bold")).pack(anchor="w", pady=(0, 6))

    log_text = tk.Text(
        log_card,
        bg="#090b10",
        fg="#d1d5db",
        insertbackground="#ffffff",
        relief="flat",
        font=("Consolas", 9),
        state="disabled",
        wrap="word",
    )
    log_text.pack(fill="both", expand=True)

    push_log(f"👑 Приложение готово! Главный владелец: {DEFAULT_OWNER_USERNAME} (ID: {DEFAULT_OWNER_ID})")
    push_log(f"🔗 Готовая ссылка приглашения вашего бота: {build_invite_url(cid_var.get() or DEFAULT_CLIENT_ID, 8)}")
    push_log("👉 Шаг 1: Нажмите «🔗 Пригласить в мой Discord», чтобы добавить бота на свой сервер.")
    push_log("👉 Шаг 2: Вставьте Bot Token выше и нажмите «🚀 Запустить бота» — бот выйдет в Online и поставит аватарку КП!")

    def poll_logs() -> None:
        updated = False
        while True:
            try:
                line = log_queue.get_nowait()
            except queue.Empty:
                break
            if not updated:
                log_text.configure(state="normal")
                updated = True
            log_text.insert("end", line + "\n")
        if updated:
            log_text.see("end")
            log_text.configure(state="disabled")
        win.after(150, poll_logs)

    def on_close() -> None:
        if state["running"]:
            stop_bot()
        win.destroy()

    win.protocol("WM_DELETE_WINDOW", on_close)
    poll_logs()
    win.mainloop()


if __name__ == "__main__":
    if "--selftest" in sys.argv:
        asyncio.run(run_selftest())
    else:
        launch_gui()

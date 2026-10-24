#!/usr/bin/env python3
"""
DiscordBotHub.exe / ReallyWorldKPBot.exe — Multi-Bot Discord Manager & Invite Studio.
Позволяет добавлять, настраивать, приглашать по ссылке и запускать ОДНОВРЕМЕННО сколько угодно Discord-ботов!
"""
from __future__ import annotations

import asyncio
import base64
import logging
import queue
import sys
import tempfile
import threading
import time
import webbrowser
from dataclasses import dataclass, field
from pathlib import Path

# Explicit imports so PyInstaller --onefile bundles every module cleanly
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
    build_invite_url,
)
from bot.profiles import (
    ALL_COG_KEYS,
    PERMISSION_PRESETS,
    BotProfile,
    ProfileStore,
    extract_client_id_from_token,
)
from main import ReallyWorldBot

COG_LABELS: list[tuple[str, str]] = [
    ("bot.cogs.owner", "👑 Владелец и /setup"),
    ("bot.cogs.kp_staff", "🛡 КП и Проверки (/kp, /check)"),
    ("bot.cogs.tickets", "🎫 Тикеты (/tickets)"),
    ("bot.cogs.moderation", "🔨 Модерация (/ban, /mute, /warn)"),
    ("bot.cogs.community", "🎉 Верификация и Розыгрыши"),
]


async def run_selftest() -> None:
    """Headless CI self-test on Windows verifying multi-bot profiles, token decoder, and concurrent bot instances."""
    with tempfile.TemporaryDirectory() as tmp:
        tmp_dir = Path(tmp)
        store = ProfileStore(tmp_dir / "bots_profiles.json")
        profiles = store.load()
        assert len(profiles) == 1
        assert profiles[0].owner_id == DEFAULT_OWNER_ID

        # Create 2 additional bots to verify multi-bot isolation
        p2 = store.create_new("Второй Бот Модерации")
        p2.enabled_cogs = ["bot.cogs.owner", "bot.cogs.moderation"]
        p3 = store.duplicate(p2)
        store.save()
        assert len(store.profiles) == 3

        # Verify Base64 token -> Client ID automatic extraction
        fake_cid = "1563651952506576918"
        fake_tok = base64.urlsafe_b64encode(fake_cid.encode()).decode().rstrip("=") + ".G12345.fakehmacsignature"
        assert extract_client_id_from_token(fake_tok) == fake_cid

        # Spin up two distinct ReallyWorldBot instances concurrently against isolated SQLite DBs
        b1 = ReallyWorldBot(profiles[0].to_bot_config(tmp_dir), enabled_cogs=profiles[0].enabled_cogs)
        b2 = ReallyWorldBot(p2.to_bot_config(tmp_dir), enabled_cogs=p2.enabled_cogs)
        await b1.db.connect()
        await b2.db.connect()
        for ext in b1.enabled_cogs:
            await b1.load_extension(ext)
        for ext in b2.enabled_cogs:
            await b2.load_extension(ext)

        cmds1 = {c.name for c in b1.tree.get_commands()}
        cmds2 = {c.name for c in b2.tree.get_commands()}
        assert len(cmds1) >= 24
        assert "ban" in cmds2 and "kp" not in cmds2
        assert b1.cfg.db_path != b2.cfg.db_path

        await b1.close()
        await b2.close()

    out_path = Path.cwd() / "selftest-result.txt"
    out_path.write_text(
        f"SELFTEST_PASS\nMULTI_BOT=TRUE\nDEFAULT_OWNER={DEFAULT_OWNER_USERNAME} ({DEFAULT_OWNER_ID})\n",
        encoding="utf-8",
    )
    print("SELFTEST_PASS (MULTI_BOT=TRUE)")


@dataclass
class BotRuntimeState:
    status: str = "stopped"  # stopped | starting | online
    online_tag: str = ""
    loop: asyncio.AbstractEventLoop | None = None
    bot: ReallyWorldBot | None = None
    thread: threading.Thread | None = None
    logs: list[str] = field(default_factory=list)


def launch_gui() -> None:
    import tkinter as tk
    from tkinter import filedialog, messagebox, ttk

    store = ProfileStore()
    store.load()

    runtimes: dict[str, BotRuntimeState] = {}
    ui_events: "queue.Queue[tuple[str, str]]" = queue.Queue()

    def get_rt(pid: str) -> BotRuntimeState:
        if pid not in runtimes:
            runtimes[pid] = BotRuntimeState(
                logs=[
                    f"[{time.strftime('%H:%M:%S')}] Профиль готов к работе. Вставьте Token для запуска или Application ID для приглашения!"
                ]
            )
        return runtimes[pid]

    def append_bot_log(pid: str, msg: str) -> None:
        stamp = time.strftime("%H:%M:%S")
        line = f"[{stamp}] {msg}"
        rt = get_rt(pid)
        rt.logs.append(line)
        if len(rt.logs) > 600:
            rt.logs = rt.logs[-600:]
        ui_events.put(("log", pid))

    BG = "#0b0d13"
    SIDEBAR = "#111520"
    CARD = "#161b28"
    INPUT_BG = "#0d1018"
    BORDER = "#262f45"
    GOLD = "#ffaa00"
    BLURPLE = "#5865f2"
    GREEN = "#2ecc71"
    RED = "#e74c3c"
    TEXT = "#f3f4f6"
    MUTED = "#9ca3af"

    win = tk.Tk()
    win.title("Discord Bot Hub — Мульти-Лаунчер и Приглашение Ботов")
    win.geometry("1080x740")
    win.minsize(980, 660)
    win.configure(bg=BG)

    ico_path = BUNDLE_DIR / "assets" / "avatar_kp.ico"
    if ico_path.exists():
        try:
            win.iconbitmap(default=str(ico_path))
        except Exception:
            pass

    style = ttk.Style()
    try:
        style.theme_use("clam")
    except Exception:
        pass
    style.configure(
        "Dark.TCombobox",
        fieldbackground=INPUT_BG,
        background="#22283a",
        foreground=TEXT,
        arrowcolor=GOLD,
    )

    selected_idx = {"val": 0}
    loading_form = {"busy": False}

    # ==================== MAIN LAYOUT ====================
    left = tk.Frame(win, bg=SIDEBAR, width=285, highlightbackground=BORDER, highlightthickness=1)
    left.pack(side="left", fill="y", padx=(14, 8), pady=14)
    left.pack_propagate(False)

    right = tk.Frame(win, bg=BG)
    right.pack(side="left", fill="both", expand=True, padx=(4, 14), pady=14)

    # ==================== LEFT SIDEBAR ====================
    sb_head = tk.Frame(left, bg=SIDEBAR, padx=14, pady=12)
    sb_head.pack(fill="x")
    tk.Label(sb_head, text="🤖 МОИ DISCORD-БОТЫ", bg=SIDEBAR, fg=GOLD, font=("Segoe UI", 11, "bold")).pack(anchor="w")
    online_counter_lbl = tk.Label(sb_head, text="Активно в сети: 0 из 1", bg=SIDEBAR, fg=MUTED, font=("Segoe UI", 9))
    online_counter_lbl.pack(anchor="w", pady=(2, 0))

    bot_listbox = tk.Listbox(
        left,
        bg="#0c0f17",
        fg=TEXT,
        selectbackground="#293452",
        selectforeground="#ffd166",
        activestyle="none",
        relief="flat",
        highlightthickness=0,
        font=("Segoe UI", 10, "bold"),
    )
    bot_listbox.pack(fill="both", expand=True, padx=12, pady=(4, 8))

    sb_btns1 = tk.Frame(left, bg=SIDEBAR, padx=12)
    sb_btns1.pack(fill="x", pady=(0, 6))

    sb_btns2 = tk.Frame(left, bg=SIDEBAR, padx=12)
    sb_btns2.pack(fill="x", pady=(0, 12))

    # ==================== RIGHT PANEL ====================
    # 1. Top Control & Invite Bar
    top_card = tk.Frame(right, bg=CARD, highlightbackground=BORDER, highlightthickness=1, padx=16, pady=12)
    top_card.pack(fill="x", pady=(0, 10))

    top_row1 = tk.Frame(top_card, bg=CARD)
    top_row1.pack(fill="x")

    bot_title_lbl = tk.Label(top_row1, text="Бот", bg=CARD, fg=GOLD, font=("Segoe UI", 15, "bold"))
    bot_title_lbl.pack(side="left")

    bot_status_lbl = tk.Label(top_row1, text="⚫ ОСТАНОВЛЕН", bg=CARD, fg=MUTED, font=("Segoe UI", 10, "bold"))
    bot_status_lbl.pack(side="right")

    ctrl_row = tk.Frame(top_card, bg=CARD)
    ctrl_row.pack(fill="x", pady=(10, 6))

    invite_row = tk.Frame(top_card, bg=CARD)
    invite_row.pack(fill="x", pady=(4, 0))

    # 2. Bot Settings Form
    cfg_card = tk.Frame(right, bg=CARD, highlightbackground=BORDER, highlightthickness=1, padx=16, pady=12)
    cfg_card.pack(fill="x", pady=(0, 10))

    name_var = tk.StringVar()
    token_var = tk.StringVar()
    cid_var = tk.StringVar()
    owner_id_var = tk.StringVar()
    guild_var = tk.StringVar()
    mc_ip_var = tk.StringVar()
    status_type_var = tk.StringVar(value="playing")
    status_text_var = tk.StringVar()
    perm_preset_var = tk.StringVar(value="Администратор (все права)")
    auto_avatar_var = tk.BooleanVar(value=False)
    avatar_file_var = tk.StringVar()
    show_token_var = tk.BooleanVar(value=False)
    cog_vars: dict[str, tk.BooleanVar] = {k: tk.BooleanVar(value=True) for k, _ in COG_LABELS}

    # Grid inside cfg_card
    cfg_card.columnconfigure(0, weight=1)
    cfg_card.columnconfigure(1, weight=1)
    cfg_card.columnconfigure(2, weight=1)

    tk.Label(cfg_card, text="Название профиля:", bg=CARD, fg=TEXT, font=("Segoe UI", 9, "bold")).grid(row=0, column=0, sticky="w")
    tk.Label(cfg_card, text="Application ID (Client ID — для ссылки приглашения):", bg=CARD, fg=TEXT, font=("Segoe UI", 9, "bold")).grid(
        row=0, column=1, sticky="w"
    )
    tk.Label(cfg_card, text="Discord ID Владельца бота:", bg=CARD, fg=TEXT, font=("Segoe UI", 9, "bold")).grid(row=0, column=2, sticky="w")

    tk.Entry(cfg_card, textvariable=name_var, bg=INPUT_BG, fg=TEXT, insertbackground=TEXT, relief="flat", font=("Segoe UI", 10)).grid(
        row=1, column=0, sticky="ew", ipady=5, padx=(0, 8), pady=(3, 8)
    )
    tk.Entry(cfg_card, textvariable=cid_var, bg=INPUT_BG, fg="#fcd34d", insertbackground=TEXT, relief="flat", font=("Consolas", 10)).grid(
        row=1, column=1, sticky="ew", ipady=5, padx=(0, 8), pady=(3, 8)
    )
    tk.Entry(cfg_card, textvariable=owner_id_var, bg=INPUT_BG, fg=TEXT, insertbackground=TEXT, relief="flat", font=("Consolas", 10)).grid(
        row=1, column=2, sticky="ew", ipady=5, pady=(3, 8)
    )

    # Token row (auto-decodes Client ID!)
    tok_hdr = tk.Frame(cfg_card, bg=CARD)
    tok_hdr.grid(row=2, column=0, columnspan=3, sticky="ew")
    tk.Label(
        tok_hdr,
        text="Bot Token (со вкладки Bot → Reset Token; при вставке сам определяет Application ID!):",
        bg=CARD,
        fg=TEXT,
        font=("Segoe UI", 9, "bold"),
    ).pack(side="left")

    tok_box = tk.Frame(cfg_card, bg=CARD)
    tok_box.grid(row=3, column=0, columnspan=3, sticky="ew", pady=(3, 8))
    tok_box.columnconfigure(0, weight=1)
    token_entry = tk.Entry(
        tok_box, textvariable=token_var, show="•", bg=INPUT_BG, fg="#ffffff", insertbackground="#ffffff", relief="flat", font=("Consolas", 10)
    )
    token_entry.grid(row=0, column=0, sticky="ew", ipady=5, padx=(0, 8))
    tk.Checkbutton(
        tok_box,
        text="Показать",
        variable=show_token_var,
        command=lambda: token_entry.configure(show="" if show_token_var.get() else "•"),
        bg=CARD,
        fg=MUTED,
        selectcolor=INPUT_BG,
        activebackground=CARD,
        activeforeground=TEXT,
    ).grid(row=0, column=1)

    # Row 4-5: Status, Guild ID, Avatar
    tk.Label(cfg_card, text="Тип статуса в Discord:", bg=CARD, fg=TEXT, font=("Segoe UI", 9, "bold")).grid(row=4, column=0, sticky="w")
    tk.Label(cfg_card, text="Текст статуса бота:", bg=CARD, fg=TEXT, font=("Segoe UI", 9, "bold")).grid(row=4, column=1, sticky="w")
    tk.Label(cfg_card, text="ID сервера (быстрая синхр. /команд):", bg=CARD, fg=TEXT, font=("Segoe UI", 9, "bold")).grid(row=4, column=2, sticky="w")

    status_combo = ttk.Combobox(
        cfg_card,
        textvariable=status_type_var,
        values=["playing", "watching", "listening"],
        state="readonly",
        style="Dark.TCombobox",
    )
    status_combo.grid(row=5, column=0, sticky="ew", ipady=3, padx=(0, 8), pady=(3, 8))
    tk.Entry(cfg_card, textvariable=status_text_var, bg=INPUT_BG, fg=TEXT, insertbackground=TEXT, relief="flat", font=("Segoe UI", 9)).grid(
        row=5, column=1, sticky="ew", ipady=5, padx=(0, 8), pady=(3, 8)
    )
    tk.Entry(cfg_card, textvariable=guild_var, bg=INPUT_BG, fg=TEXT, insertbackground=TEXT, relief="flat", font=("Consolas", 10)).grid(
        row=5, column=2, sticky="ew", ipady=5, pady=(3, 8)
    )

    # Row 6: Modules checkboxes + Avatar selector
    mods_frame = tk.Frame(cfg_card, bg=CARD)
    mods_frame.grid(row=6, column=0, columnspan=3, sticky="ew", pady=(2, 4))
    tk.Label(mods_frame, text="Включённые модули бота:", bg=CARD, fg=GOLD, font=("Segoe UI", 9, "bold")).pack(anchor="w")

    checks_row = tk.Frame(mods_frame, bg=CARD)
    checks_row.pack(fill="x", pady=(2, 0))
    for key, label in COG_LABELS:
        tk.Checkbutton(
            checks_row,
            text=label,
            variable=cog_vars[key],
            bg=CARD,
            fg=TEXT,
            selectcolor=INPUT_BG,
            activebackground=CARD,
            activeforeground=GOLD,
            font=("Segoe UI", 8),
        ).pack(side="left", padx=(0, 10))

    av_row = tk.Frame(cfg_card, bg=CARD)
    av_row.grid(row=7, column=0, columnspan=3, sticky="ew", pady=(6, 0))
    tk.Checkbutton(
        av_row,
        text="Авто-установка аватарки при первом старте",
        variable=auto_avatar_var,
        bg=CARD,
        fg=MUTED,
        selectcolor=INPUT_BG,
        activebackground=CARD,
        activeforeground=TEXT,
        font=("Segoe UI", 9),
    ).pack(side="left")

    avatar_lbl = tk.Label(av_row, text="Файл: встроенная КП (пляж)", bg=CARD, fg=MUTED, font=("Segoe UI", 8))
    avatar_lbl.pack(side="left", padx=(10, 8))

    def pick_avatar_file() -> None:
        chosen = filedialog.askopenfilename(
            title="Выберите аватарку для бота (PNG / JPG / GIF)",
            filetypes=[("Изображения", "*.png *.jpg *.jpeg *.gif *.webp")],
        )
        if chosen:
            avatar_file_var.set(chosen)
            avatar_lbl.configure(text=f"Файл: {Path(chosen).name}")
            save_current_profile()

    def reset_kp_avatar() -> None:
        avatar_file_var.set("")
        avatar_lbl.configure(text="Файл: встроенная КП (пляж)")
        save_current_profile()

    tk.Button(
        av_row, text="🖼 Своя картинка...", command=pick_avatar_file, bg="#22283a", fg=TEXT, relief="flat", font=("Segoe UI", 8, "bold"), padx=8, pady=3
    ).pack(side="right")
    tk.Button(
        av_row, text="🏖 Аватарка КП", command=reset_kp_avatar, bg="#22283a", fg=GOLD, relief="flat", font=("Segoe UI", 8, "bold"), padx=8, pady=3
    ).pack(side="right", padx=(0, 6))

    # 3. Live Per-Bot Logs Card
    log_card = tk.Frame(right, bg=CARD, highlightbackground=BORDER, highlightthickness=1, padx=14, pady=10)
    log_card.pack(fill="both", expand=True)

    tk.Label(log_card, text="Живая консоль выбранного бота:", bg=CARD, fg=TEXT, font=("Segoe UI", 9, "bold")).pack(anchor="w", pady=(0, 4))
    log_text = tk.Text(
        log_card,
        bg="#080a0f",
        fg="#d1d5db",
        insertbackground="#ffffff",
        relief="flat",
        font=("Consolas", 9),
        state="disabled",
        wrap="word",
    )
    log_text.pack(fill="both", expand=True)

    # ==================== INVITE URL BAR ====================
    tk.Label(invite_row, text="Права приглашения:", bg=CARD, fg=MUTED, font=("Segoe UI", 9)).pack(side="left")
    perm_combo = ttk.Combobox(
        invite_row,
        textvariable=perm_preset_var,
        values=list(PERMISSION_PRESETS.keys()),
        state="readonly",
        width=27,
        style="Dark.TCombobox",
    )
    perm_combo.pack(side="left", padx=(6, 10))

    invite_url_lbl = tk.Entry(
        invite_row, bg=INPUT_BG, fg="#a5b4fc", readonlybackground=INPUT_BG, relief="flat", font=("Consolas", 9), state="readonly"
    )
    invite_url_lbl.pack(side="left", fill="x", expand=True, ipady=4)

    # ==================== CORE FUNCTIONS ====================
    def current_profile() -> BotProfile:
        idx = min(max(0, selected_idx["val"]), len(store.profiles) - 1)
        selected_idx["val"] = idx
        return store.profiles[idx]

    def compute_invite_for_ui() -> str:
        cid = "".join(c for c in cid_var.get() if c.isdigit())
        if not cid:
            cid = extract_client_id_from_token(token_var.get()) or ""
        if len(cid) >= 15:
            perms = PERMISSION_PRESETS.get(perm_preset_var.get(), 8)
            return build_invite_url(cid, permissions=perms)
        return "Введите Application ID или вставьте Bot Token — ссылка приглашения появится автоматически!"

    def refresh_invite_preview(*_: object) -> None:
        if loading_form["busy"]:
            return
        extracted = extract_client_id_from_token(token_var.get())
        if extracted and not cid_var.get().strip():
            cid_var.set(extracted)
        url = compute_invite_for_ui()
        invite_url_lbl.configure(state="normal")
        invite_url_lbl.delete(0, "end")
        invite_url_lbl.insert(0, url)
        invite_url_lbl.configure(state="readonly")

    def refresh_sidebar() -> None:
        bot_listbox.delete(0, "end")
        online_cnt = 0
        for p in store.profiles:
            rt = get_rt(p.profile_id)
            if rt.status == "online":
                icon = "🟢"
                online_cnt += 1
            elif rt.status == "starting":
                icon = "🟡"
            else:
                icon = "⚫"
            bot_listbox.insert("end", f" {icon}  {p.name}")
        bot_listbox.selection_clear(0, "end")
        bot_listbox.selection_set(selected_idx["val"])
        online_counter_lbl.configure(text=f"Активно в сети: {online_cnt} из {len(store.profiles)}")

    def refresh_status_header() -> None:
        prof = current_profile()
        rt = get_rt(prof.profile_id)
        bot_title_lbl.configure(text=f"🤖 {prof.name}")
        if rt.status == "online":
            bot_status_lbl.configure(text=f"🟢 В СЕТИ: {rt.online_tag}", fg=GREEN)
            start_btn.configure(state="disabled")
            stop_btn.configure(state="normal")
        elif rt.status == "starting":
            bot_status_lbl.configure(text="🟡 ПОДКЛЮЧЕНИЕ...", fg=GOLD)
            start_btn.configure(state="disabled")
            stop_btn.configure(state="normal")
        else:
            bot_status_lbl.configure(text="⚫ ОСТАНОВЛЕН", fg=MUTED)
            start_btn.configure(state="normal")
            stop_btn.configure(state="disabled")

    def render_logs_for_selected() -> None:
        prof = current_profile()
        rt = get_rt(prof.profile_id)
        log_text.configure(state="normal")
        log_text.delete("1.0", "end")
        log_text.insert("end", "\n".join(rt.logs) + "\n")
        log_text.see("end")
        log_text.configure(state="disabled")

    def load_profile_into_form(prof: BotProfile) -> None:
        loading_form["busy"] = True
        try:
            name_var.set(prof.name)
            token_var.set(prof.token)
            cid_var.set(prof.client_id)
            owner_id_var.set(str(prof.owner_id))
            guild_var.set(prof.dev_guild_id)
            mc_ip_var.set(prof.minecraft_ip)
            status_type_var.set(prof.status_type if prof.status_type in {"playing", "watching", "listening"} else "playing")
            status_text_var.set(prof.status_text)
            perm_preset_var.set(prof.invite_perm_label if prof.invite_perm_label in PERMISSION_PRESETS else "Администратор (все права)")
            auto_avatar_var.set(prof.auto_set_avatar)
            avatar_file_var.set(prof.avatar_file)
            avatar_lbl.configure(
                text=f"Файл: {Path(prof.avatar_file).name}" if prof.avatar_file else "Файл: встроенная КП (пляж)"
            )
            for k in ALL_COG_KEYS:
                cog_vars[k].set(k in prof.enabled_cogs)
        finally:
            loading_form["busy"] = False
        refresh_invite_preview()
        refresh_status_header()
        render_logs_for_selected()

    def save_current_profile(*_: object) -> BotProfile:
        if loading_form["busy"]:
            return current_profile()
        prof = current_profile()
        prof.name = name_var.get().strip() or "Discord-Бот"
        prof.token = token_var.get().strip()
        extracted = extract_client_id_from_token(prof.token)
        raw_cid = "".join(c for c in cid_var.get() if c.isdigit())
        prof.client_id = raw_cid or (extracted or "")
        if prof.client_id and not cid_var.get().strip():
            cid_var.set(prof.client_id)
        oid = "".join(c for c in owner_id_var.get() if c.isdigit())
        prof.owner_id = int(oid) if oid else DEFAULT_OWNER_ID
        prof.dev_guild_id = "".join(c for c in guild_var.get() if c.isdigit())
        prof.status_type = status_type_var.get()
        prof.status_text = status_text_var.get().strip() or "/help"
        prof.invite_perm_label = perm_preset_var.get()
        prof.auto_set_avatar = bool(auto_avatar_var.get())
        prof.avatar_file = avatar_file_var.get().strip()
        selected_cogs = [k for k in ALL_COG_KEYS if cog_vars[k].get()]
        prof.enabled_cogs = selected_cogs or ["bot.cogs.owner"]
        prof.project_name = prof.name
        store.save()
        refresh_sidebar()
        refresh_status_header()
        refresh_invite_preview()
        return prof

    # Start / Stop engine for ANY profile!
    def start_profile_bot(prof: BotProfile, silent_if_no_token: bool = False) -> None:
        rt = get_rt(prof.profile_id)
        if rt.status in {"starting", "online"}:
            return
        tok = prof.token.strip()
        if not tok or len(tok) < 30:
            if not silent_if_no_token:
                messagebox.showwarning(
                    "Не указан Bot Token",
                    f"Для запуска бота «{prof.name}» вставьте его Bot Token!\n\n"
                    "(А если вы хотите просто пригласить чужого или нового бота по ID — введите его Application ID и нажмите «🔗 Пригласить в Discord».)",
                )
            return

        cfg_obj = prof.to_bot_config()
        cogs_to_load = list(prof.enabled_cogs)
        st_type = prof.status_type
        st_text = prof.status_text
        pid = prof.profile_id

        rt.status = "starting"
        ui_events.put(("status", pid))
        append_bot_log(pid, f"🚀 Запускаю «{prof.name}» (модулей: {len(cogs_to_load)}, БД: {cfg_obj.db_path.name})...")

        def worker() -> None:
            loop = asyncio.new_event_loop()
            asyncio.set_event_loop(loop)
            bot_obj = ReallyWorldBot(cfg_obj, enabled_cogs=cogs_to_load, status_type=st_type, status_text=st_text)
            rt.loop = loop
            rt.bot = bot_obj

            orig_ready = bot_obj.on_ready

            async def wrapped_ready() -> None:
                await orig_ready()
                rt.status = "online"
                rt.online_tag = str(bot_obj.user)
                if bot_obj.user and not prof.client_id:
                    prof.client_id = str(bot_obj.user.id)
                    store.save()
                ui_events.put(("status", pid))
                append_bot_log(pid, f"✅ В СЕТИ КАК {bot_obj.user} (ID: {bot_obj.user.id})!")
                append_bot_log(pid, f"🔗 Ссылка приглашения: {build_invite_url(bot_obj.user.id, prof.invite_permissions)}")

            bot_obj.on_ready = wrapped_ready  # type: ignore[method-assign]

            try:
                loop.run_until_complete(bot_obj.start(cfg_obj.token))
            except discord.PrivilegedIntentsRequired:
                append_bot_log(
                    pid,
                    "❌ ОШИБКА INTENTS: Откройте настройки этого бота в Discord Portal (кнопка сверху) и включите Presence, Server Members и Message Content Intent!",
                )
            except discord.LoginFailure:
                append_bot_log(pid, "❌ НЕВЕРНЫЙ ТОКЕН: Сбросьте токен во вкладке Bot → Reset Token и вставьте заново.")
            except Exception as exc:
                append_bot_log(pid, f"⚠️ Поток бота завершён: {exc}")
            finally:
                try:
                    if not bot_obj.is_closed():
                        loop.run_until_complete(bot_obj.close())
                except Exception:
                    pass
                loop.close()
                rt.status = "stopped"
                rt.online_tag = ""
                rt.bot = None
                rt.loop = None
                ui_events.put(("status", pid))
                append_bot_log(pid, "⏹ Бот остановлен.")

        t = threading.Thread(target=worker, daemon=True)
        rt.thread = t
        t.start()

    def stop_profile_bot(prof: BotProfile) -> None:
        rt = get_rt(prof.profile_id)
        if rt.bot is not None and rt.loop is not None:
            append_bot_log(prof.profile_id, "⏳ Останавливаю бота и сохраняю БД...")
            asyncio.run_coroutine_threadsafe(rt.bot.close(), rt.loop)

    # Sidebar handlers
    def on_select_bot(_: object = None) -> None:
        sel = bot_listbox.curselection()
        if not sel:
            return
        new_i = int(sel[0])
        if new_i == selected_idx["val"]:
            return
        save_current_profile()
        selected_idx["val"] = new_i
        load_profile_into_form(current_profile())

    def add_new_bot() -> None:
        save_current_profile()
        new_p = store.create_new(f"Бот #{len(store.profiles) + 1}")
        selected_idx["val"] = len(store.profiles) - 1
        refresh_sidebar()
        load_profile_into_form(new_p)

    def duplicate_bot() -> None:
        src = save_current_profile()
        dup = store.duplicate(src)
        selected_idx["val"] = len(store.profiles) - 1
        refresh_sidebar()
        load_profile_into_form(dup)

    def delete_selected_bot() -> None:
        prof = current_profile()
        rt = get_rt(prof.profile_id)
        if rt.status != "stopped":
            messagebox.showwarning("Бот запущен", "Сначала остановите этого бота перед удалением профиля!")
            return
        if not messagebox.askyesno("Удалить профиль?", f"Удалить профиль «{prof.name}» из списка?"):
            return
        store.delete(prof.profile_id)
        selected_idx["val"] = max(0, selected_idx["val"] - 1)
        refresh_sidebar()
        load_profile_into_form(current_profile())

    def start_all_bots() -> None:
        save_current_profile()
        started = 0
        for p in store.profiles:
            if p.token.strip() and get_rt(p.profile_id).status == "stopped":
                start_profile_bot(p, silent_if_no_token=True)
                started += 1
        if started == 0:
            messagebox.showinfo("Нет готовых токенов", "Заполните Bot Token хотя бы у одного остановленного бота!")

    def stop_all_bots() -> None:
        for p in store.profiles:
            stop_profile_bot(p)

    # Top bar button handlers
    def open_selected_invite() -> None:
        prof = save_current_profile()
        cid = prof.resolved_client_id
        if not cid:
            messagebox.showwarning(
                "Укажите Application ID",
                "Введите числовой Application ID (Client ID) этого бота ИЛИ вставьте его Bot Token — программа сама вычислит ID и откроет приглашение!",
            )
            return
        url = build_invite_url(cid, permissions=prof.invite_permissions)
        webbrowser.open(url)
        append_bot_log(prof.profile_id, f"🌐 Открыта ссылка приглашения в браузере: {url}")

    def copy_selected_invite() -> None:
        prof = save_current_profile()
        cid = prof.resolved_client_id
        if not cid:
            messagebox.showwarning("Укажите Application ID", "Сначала введите Application ID или Bot Token!")
            return
        url = build_invite_url(cid, permissions=prof.invite_permissions)
        win.clipboard_clear()
        win.clipboard_append(url)
        append_bot_log(prof.profile_id, f"📋 Ссылка скопирована: {url}")

    def open_selected_portal() -> None:
        prof = save_current_profile()
        cid = prof.resolved_client_id
        target = f"https://discord.com/developers/applications/{cid}/bot" if cid else "https://discord.com/developers/applications"
        webbrowser.open(target)

    # Populate Sidebar Buttons
    tk.Button(sb_btns1, text="➕ Новый бот", command=add_new_bot, bg=GOLD, fg="#111", font=("Segoe UI", 9, "bold"), relief="flat", pady=6).pack(
        side="left", fill="x", expand=True, padx=(0, 4)
    )
    tk.Button(sb_btns1, text="📋 Копия", command=duplicate_bot, bg="#22283a", fg=TEXT, font=("Segoe UI", 9, "bold"), relief="flat", pady=6).pack(
        side="left", padx=(0, 4)
    )
    tk.Button(sb_btns1, text="🗑", command=delete_selected_bot, bg="#2b1820", fg=RED, font=("Segoe UI", 9, "bold"), relief="flat", padx=8, pady=6).pack(
        side="left"
    )

    tk.Button(
        sb_btns2, text="⚡ Запустить ВСЕХ", command=start_all_bots, bg="#193829", fg=GREEN, font=("Segoe UI", 9, "bold"), relief="flat", pady=6
    ).pack(side="left", fill="x", expand=True, padx=(0, 4))
    tk.Button(
        sb_btns2, text="🛑 Стоп ВСЕХ", command=stop_all_bots, bg="#331a1e", fg=RED, font=("Segoe UI", 9, "bold"), relief="flat", pady=6
    ).pack(side="left", fill="x", expand=True)

    # Populate Top Control Buttons
    start_btn = tk.Button(
        ctrl_row,
        text="🚀 Запустить этого бота",
        command=lambda: start_profile_bot(save_current_profile()),
        bg=GREEN,
        fg="#071a0e",
        font=("Segoe UI", 10, "bold"),
        relief="flat",
        padx=14,
        pady=8,
        cursor="hand2",
    )
    start_btn.pack(side="left", padx=(0, 8))

    stop_btn = tk.Button(
        ctrl_row,
        text="⏹ Остановить",
        command=lambda: stop_profile_bot(current_profile()),
        state="disabled",
        bg=RED,
        fg="#ffffff",
        font=("Segoe UI", 10, "bold"),
        relief="flat",
        padx=12,
        pady=8,
        cursor="hand2",
    )
    stop_btn.pack(side="left", padx=(0, 8))

    tk.Button(
        ctrl_row,
        text="🔗 Пригласить в Discord",
        command=open_selected_invite,
        bg=BLURPLE,
        fg="#ffffff",
        font=("Segoe UI", 10, "bold"),
        relief="flat",
        padx=14,
        pady=8,
        cursor="hand2",
    ).pack(side="left", padx=(0, 8))

    tk.Button(
        ctrl_row,
        text="📋 Копировать ссылку",
        command=copy_selected_invite,
        bg="#22283a",
        fg=TEXT,
        font=("Segoe UI", 9, "bold"),
        relief="flat",
        padx=12,
        pady=8,
        cursor="hand2",
    ).pack(side="left", padx=(0, 8))

    tk.Button(
        ctrl_row,
        text="💾 Сохранить",
        command=save_current_profile,
        bg="#22283a",
        fg=GREEN,
        font=("Segoe UI", 9, "bold"),
        relief="flat",
        padx=10,
        pady=8,
        cursor="hand2",
    ).pack(side="left")

    tk.Button(
        ctrl_row,
        text="🌐 Discord Portal",
        command=open_selected_portal,
        bg="#22283a",
        fg=GOLD,
        font=("Segoe UI", 9, "bold"),
        relief="flat",
        padx=12,
        pady=8,
        cursor="hand2",
    ).pack(side="right")

    bot_listbox.bind("<<ListboxSelect>>", on_select_bot)
    token_var.trace_add("write", refresh_invite_preview)
    cid_var.trace_add("write", refresh_invite_preview)
    perm_preset_var.trace_add("write", refresh_invite_preview)

    def poll_ui_events() -> None:
        need_sidebar = False
        need_logs = False
        sel_pid = current_profile().profile_id
        while True:
            try:
                ev_type, pid = ui_events.get_nowait()
            except queue.Empty:
                break
            if ev_type == "status":
                need_sidebar = True
            if pid == sel_pid:
                need_logs = True
        if need_sidebar:
            refresh_sidebar()
            refresh_status_header()
        if need_logs:
            render_logs_for_selected()
        win.after(150, poll_ui_events)

    def on_close() -> None:
        save_current_profile()
        stop_all_bots()
        win.destroy()

    refresh_sidebar()
    load_profile_into_form(current_profile())
    win.protocol("WM_DELETE_WINDOW", on_close)
    poll_ui_events()
    win.mainloop()


if __name__ == "__main__":
    if "--selftest" in sys.argv:
        asyncio.run(run_selftest())
    else:
        launch_gui()

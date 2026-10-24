"""Automated async unit + integration test suite for ReallyWorld КП Discord Bot."""
from __future__ import annotations

import tempfile
import time
import unittest
from pathlib import Path

from bot.config import DEFAULT_OWNER_ID, DEFAULT_OWNER_USERNAME, BotConfig, build_invite_url
from bot.database import BotDatabase
from bot.profiles import ProfileStore, extract_client_id_from_token
from main import COGS, ReallyWorldBot


class ReallyWorldBotTestSuite(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.db_path = Path(self.tmp.name) / "test_rw_bot.db"
        self.cfg = BotConfig(
            token="DUMMY_TEST_TOKEN",
            client_id="1381255368650526861",
            db_path=self.db_path,
        )
        self.db = BotDatabase(self.db_path)
        await self.db.connect()

    async def asyncTearDown(self) -> None:
        await self.db.close()
        self.tmp.cleanup()

    async def test_owner_locked_to_robik0277_and_invite_url(self) -> None:
        loaded = BotConfig.load()
        self.assertEqual(loaded.owner_id, 1381255368650526861)
        self.assertEqual(loaded.owner_id, DEFAULT_OWNER_ID)
        self.assertEqual(loaded.owner_username, "robik0277")
        self.assertEqual(loaded.owner_username, DEFAULT_OWNER_USERNAME)
        self.assertTrue(loaded.avatar_path.exists(), "Bundled КП avatar must exist")

        url = build_invite_url("1381255368650526861", permissions=8)
        self.assertEqual(
            url,
            "https://discord.com/oauth2/authorize?client_id=1381255368650526861&permissions=8&scope=bot+applications.commands",
        )
        with self.assertRaises(ValueError):
            build_invite_url("not-a-snowflake")

    async def test_database_kp_tickets_checks_warnings_and_giveaways(self) -> None:
        gid = 999001
        uid = 1381255368650526861

        # Guild settings
        await self.db.update_guild_setting(gid, "staff_role_id", 111222)
        s = await self.db.get_guild_settings(gid)
        self.assertEqual(s["staff_role_id"], 111222)

        # Co-owners
        self.assertFalse(await self.db.is_co_owner(555))
        await self.db.add_co_owner(555, uid)
        self.assertTrue(await self.db.is_co_owner(555))
        self.assertTrue(await self.db.remove_co_owner(555))

        # Warnings
        wid = await self.db.add_warning(gid, 777, uid, "Флуд в чате")
        warns = await self.db.get_warnings(gid, 777)
        self.assertEqual(len(warns), 1)
        self.assertTrue(await self.db.delete_warning(gid, wid))

        # Tickets + atomic single claim
        tid = await self.db.create_ticket(gid, 88801, 777, "Вопросы по донату", "Не пришли риллики")
        self.assertGreater(tid, 0)
        self.assertIsNotNone(await self.db.get_open_ticket(gid, 777, "Вопросы по донату"))
        self.assertTrue(await self.db.claim_ticket(88801, uid))
        self.assertFalse(await self.db.claim_ticket(88801, 999))  # Anti-double-claim!
        await self.db.close_ticket(88801)
        self.assertIsNone(await self.db.get_open_ticket(gid, 777, "Вопросы по донату"))

        # КП Staff stats, applications & cheat checks
        p = await self.db.modify_kp_stats(gid, uid, points_delta=15, tickets_delta=3, checks_delta=2, position="Куратор КП")
        self.assertEqual(p["points"], 15)
        self.assertEqual(p["position"], "Куратор КП")

        app_id = await self.db.create_kp_application(gid, 777, "Модератор", "TestPlayer", "17", "МСК", "2 года опыта на RW")
        updated_app = await self.db.update_kp_application(app_id, "ACCEPTED", uid, "Отличный кандидат")
        self.assertEqual(updated_app["status"], "ACCEPTED")

        chk_id = await self.db.create_cheat_check(gid, 88802, 777, "SuspectMC", uid, "Подозрение на KillAura")
        self.assertIsNotNone(await self.db.get_active_check_by_channel(88802))
        await self.db.finish_cheat_check(chk_id, "ЧИСТ")
        self.assertIsNone(await self.db.get_active_check_by_channel(88802))

        # Giveaways & Economy
        gw_id = await self.db.create_giveaway(gid, 88803, "Привилегия DRAGON", 1, uid, int(time.time()) - 5)
        await self.db.set_giveaway_message(gw_id, 555001)
        joined, cnt = await self.db.toggle_giveaway_entry(gw_id, 777)
        self.assertTrue(joined)
        self.assertEqual(cnt, 1)
        due = await self.db.get_due_giveaways()
        self.assertEqual(len(due), 1)
        entries = await self.db.finish_giveaway(gw_id)
        self.assertEqual(entries, [777])

        ok1, _ = await self.db.claim_daily(gid, uid, 250)
        ok2, rem = await self.db.claim_daily(gid, uid, 250)
        self.assertTrue(ok1)
        self.assertFalse(ok2)
        self.assertGreater(rem, 86000)

    async def test_all_cogs_and_slash_commands_load_cleanly(self) -> None:
        bot = ReallyWorldBot(self.cfg)
        self.assertEqual(bot.owner_id, 1381255368650526861)
        for ext in COGS:
            await bot.load_extension(ext)
        cmd_names = {c.name for c in bot.tree.get_commands()}
        expected = {
            "owner", "setup", "kp", "check", "tickets", "verify", "giveaway",
            "ban", "unban", "kick", "mute", "unmute", "warn", "warns", "unwarn",
            "clear", "lock", "unlock", "server", "rules", "help", "bal", "daily", "top",
        }
        self.assertTrue(expected.issubset(cmd_names), f"Missing commands: {expected - cmd_names}")
        # Cancel background loop before exit
        bot.get_cog("CommunityCog").cog_unload()
        await bot.close()

    async def test_multi_bot_profile_store_and_token_client_id_decoder(self) -> None:
        import base64

        store = ProfileStore(Path(self.tmp.name) / "multi_bots.json")
        initial = store.load()
        self.assertEqual(len(initial), 1)
        self.assertEqual(initial[0].client_id, "1563651952506576918")

        bot2 = store.create_new("Бот Кланов")
        bot3 = store.create_new("Бот Модерации")
        self.assertEqual(len(store.profiles), 3)
        self.assertNotEqual(bot2.to_bot_config(Path(self.tmp.name)).db_path, bot3.to_bot_config(Path(self.tmp.name)).db_path)

        sample_id = "1234567890123456789"
        encoded_tok = base64.urlsafe_b64encode(sample_id.encode()).decode().rstrip("=") + ".Xyz123.SignaturePart"
        self.assertEqual(extract_client_id_from_token(encoded_tok), sample_id)


if __name__ == "__main__":
    unittest.main()

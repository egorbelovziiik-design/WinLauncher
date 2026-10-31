# Changelog

All notable changes to FunTime Auction Helper Ultra Pro.
The format loosely follows Keep a Changelog; versions are Minecraft independent.

## [1.0.0] - 2026-10-31

First public build. Three separate JARs, one per supported Minecraft version.

### Added
- **GUI** (custom immediate mode renderer, dark premium theme):
  Dashboard, Auction Analysis, Buy Settings, Sell Settings, Strategies, Price History,
  Filters, Favourites, Trade History, Notifications, Settings.
  Cards, hover animations, toggles, sliders, number fields, dropdowns, tooltips, charts,
  scrollable content, toast notifications, status dots.
- **Key handling**: opens and closes with **Ё** (`~` / `` ` ``), physical GLFW key
  `GRAVE_ACCENT` (96) so RU and EN layouts behave identically; `ESCAPE` closes;
  the key is rebindable in Settings; the screen never opens while chat or another
  screen is focused. Separate rebindable **emergency stop** hotkey (default F9).
- **Automation safety model**: master switch OFF by default, forced OFF on game start,
  server join, disconnect, error and config load. AUTO BUY / AUTO SELL are independent
  switches that require the master switch. `AUTHORIZED_AUTOMATION` mode is refused unless
  an authorized `AuctionIntegration` is present (default: `None`, which always refuses).
- **STOP ALL TRADING** button and hotkey: disables every switch, cancels queued
  operations, clears the pending queue, notifies and writes a local log entry.
- **Trade engine guard pipeline**: mode check, switch check, integration check,
  whitelist/blacklist, allowed/blocked items, max purchase price, purchases per session,
  daily spending limit, minimum balance, minimum/maximum selling price, pending limit;
  re-reads the price right before execution (a changed price aborts with `STALE`);
  a network/protocol error leaves the operation `UNVERIFIED` and blocks a blind retry.
- **Smart Price Analyzer**: price per item, price per stack, average, median, p25/p75,
  min, max, sample count, low-confidence warning, potential margin, fees, net profit.
  Wording is always "estimate", never "guaranteed profit".
- **Flip analysis** and **5 strategies** producing signals only: low price alert,
  price comparison, manual flip analysis, price history analysis, inventory selling
  planner. No strategy can create a trade by itself.
- **Filter engine**: create/edit/delete/duplicate/enable, templates, favourites,
  whitelist/blacklist, bounds on price, unit price, stack size and category.
- **Price history**: local JSON store, CSV export/import, cleanup by age, chart.
- **Trade history**: every operation with result `PENDING / CONFIRMED / UNVERIFIED /
  STALE / REJECTED / CANCELLED`; only `CONFIRMED` counts in income/expense statistics;
  CSV export, filters, cleanup.
- **Notification manager**: levels, timestamps, read/unread, colours, retention.
- **Config manager**: create/save/load/rename/delete/import/export/backup/restore,
  atomic writes, rolling backups, named profiles. Loading always forces automation OFF.
- **Theme engine**: 6 built-in themes plus user defined themes.
- **Adapter layer**: configurable regex profiles for the auction screen, a per-profile
  `verified` flag, unknown-screen diagnostics, and a "analysis disabled" state instead of
  a crash when the layout is not recognised.
- **In-game self test** (`-Dfuntimeauctionhelper.selftest=true`) producing
  `funtimeauctionhelper-selftest-<version>.json`.
- **CI**: unit tests, three remapped JAR builds, JAR content/bytecode/entrypoint
  inspection, and real Minecraft client runs under Xvfb for 1.21.4 and 1.21.11.

### Known limitations at 1.0.0
- No automatic buying or selling on FunTime: no publicly documented, server authorized
  trading API exists. `AUTHORIZED_AUTOMATION` stays unavailable until such an integration
  is implemented and configured.
- The 1.16.5 JAR is compiled and inspected but the in-game self test is not executed in
  CI (Loom 1.19 development launches require a Java 9+ runtime, Minecraft 1.16.5 does not
  run on Java 9+).
- Auction layout parsing relies on generic, unverified regex patterns until the player
  confirms the parsed offers once.
- The player balance is not exposed by the vanilla client; automated buying refuses to run
  while the balance is unknown.

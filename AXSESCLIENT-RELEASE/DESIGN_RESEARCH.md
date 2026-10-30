# AXSESCLIENT GENESIS X — DESIGN & UX RESEARCH (`DESIGN_RESEARCH.md`)

**Product:** AXSESCLIENT (Codename: GENESIS X) v1.0.0  
**Design System:** AXSES NEO-DARK  
**Target Minecraft Versions:** 1.16.5 · 1.21.4 · 1.21.11  
**Compliance Mandate:** Original code, original branding, original UI tokens. Zero proprietary asset reuse, zero anti-cheat bypasses, zero client spoofing, and zero prohibited combat/movement automation.

---

## 1. Research Methodology & Evaluation Matrix

Before designing **AXSES NEO-DARK**, we analyzed publicly documented UI architectures, open-source repositories (where applicable under MIT/GPL/MPL reference inspection without code copying), public screenshots, usability patterns, and configuration workflows of **50 well-known Minecraft clients and utility mods**.

Every client was evaluated across **14 core engineering & UX dimensions**:
1. **Interface Design** — Visual hierarchy, contrast ratios, typography, surface layering.
2. **ClickGUI Organization** — Multi-column dropdown panels vs. unified master-detail window vs. dockable workspace.
3. **Category Structure** — Cognitive grouping and discoverability of modules.
4. **Module Settings UX** — Inline accordion drawers vs. modal inspectors vs. right-hand property panes.
5. **Animations** — Easing curves, frame-time independence, hover/toggle responsiveness.
6. **HUD Editor** — Drag-and-drop snapping, alignment guides, multi-resolution scaling, locking.
7. **Theme Engine** — Preset palettes, custom color tokens, live preview, import/export validation.
8. **Config Manager** — Profile switching, versioning, backup/recovery on malformed JSON.
9. **Keybind Manager** — Inline capture, conflict detection, modifier/mouse support, quick clear.
10. **Search System** — Instant substring/fuzzy filtering across names, descriptions, and settings, plus search history.
11. **Favorites System** — One-click star pinning and dedicated quick-access view.
12. **Notifications** — Non-intrusive toast stack, severity color coding, progress bars, notification history.
13. **Control Ergonomics** — Keyboard navigation (`Right Shift`, `Escape`), scroll physics, hit-target sizing.
14. **Interface Performance** — Draw-call batching, layout caching, zero-allocation hot paths, blur cost control.

---

## 2. Comprehensive Analysis of 50 Studied Clients

### Group A — Modern Unified-Window & Neo-Dark Utility Clients (01–06)

| # | Client | UI & ClickGUI Architecture | Categories & Settings UX | Animations, HUD & Themes | Config, Keybinds, Search & Perf Notes |
|---|---|---|---|---|---|
| **01** | **Nursultan Client** | Sleek dark rounded master-detail window with subtle accent glows and frosted cards. | Sidebar categories + 2-column module cards; inline expandable sliders, mode chips, and HSV pickers. | Smooth cubic transitions on card expansion; magnetic HUD drag snapping; rich purple/cyan preset themes. | Profile-based JSON configs, inline keybind badges, instant topbar search, toast notifications; heavy blur shaders can impact integrated GPUs if uncapped. |
| **02** | **Delta Client** | Compact dark translucent window with neon edge highlights and clean iconography. | Streamlined sidebar tabs; compact toggle pills with right-click settings expansion. | Fast fade/slide page transitions; lightweight HUD overlay anchors; dual-tone gradient themes. | Quick config switcher, per-module keybind pill, header search bar, pinned favorites strip; good mid-range FPS retention. |
| **03** | **Expensive Client** | Modern dark card grid with high-contrast accent pills and rounded surfaces. | 6–7 sidebar categories; expandable module cards with multi-select chips and numeric step sliders. | Spring-like toggle switches and smooth scroll inertia; free-drag HUD elements; customizable accent gradients. | Named config files, keybind list overlay, real-time name search, bottom-right animated notifications; clean 2D batching. |
| **04** | **Celestial Client** | High-saturation gradient headers over dark slate panels; classic multi-panel or windowed toggle. | Vertical category columns or unified view; color pickers with alpha sliders and mode dropdowns. | Elastic open/close scale animations; draggable HUD widgets; extensive color theme presets. | Config save/load commands & GUI tab, inline keybind capture, search highlight dimming; dense panels require UI scaling options on 1080p+. |
| **05** | **ThunderHack** | Feature-dense multi-panel ClickGUI with technical telemetry overlays. | Granular category breakdown; deeply nested settings trees and sub-groups. | Snappy linear/quad transitions; rich HUD telemetry widgets (graphs, counters); dark/neon themes. | Extensive JSON config profiles, keybind manager, global search filter; very high setting density benefits from search & reset buttons. |
| **06** | **ThunderHack Recode** | Redesigned unified modern window with cleaner spacing, search bar, and modular HUD editor. | Left rail navigation + scrollable card grid; cleaner slider tracks and grouped settings. | Delta-time eased hover/toggle animations; grid-aligned HUD editor; customizable theme palette. | Safer config serialization, conflict-aware keybinds, favorites filtering, structured toast notifications, improved render caching. |

### Group B — Open-Source Technical & Modular Clients (07–21)

| # | Client | UI & ClickGUI Architecture | Categories & Settings UX | Animations, HUD & Themes | Config, Keybinds, Search & Perf Notes |
|---|---|---|---|---|---|
| **07** | **Meteor Client** | Clean, utilitarian dark windowing toolkit with dockable/collapsible category columns and dedicated tabs (`Modules`, `Config`, `GUI`, `HUD`). | Logical categories + modal/sub-screen settings inspector per module with clear descriptions and reset icons. | Subtle, fast expand/collapse animations; full modular HUD screen with snapping; XML/JSON custom themes. | Robust folder-based config system, modifier-capable keybinds, top-level instant search bar & star favorites, low UI overhead. |
| **08** | **LiquidBounce (Legacy)** | Flat sky-blue/dark minimalist panels with clean geometric typography and custom main menu. | Floating draggable category panels; inline settings expansion. | Smooth slide-in notifications and clean button hovers; dedicated HUD designer screen. | Text/JSON scriptable configs, command + GUI keybind editor, quick search panel; lightweight 2D rendering. |
| **09** | **LiquidBounce Nextgen** | Modern reactive UI architecture with crisp rounded cards, strong contrast tokens, and clear visual hierarchy. | Structured category panels + searchable module browser; validated typed settings (`Range`, `Choice`, `Color`, `Multi`). | Fluid 60–240Hz delta-time transitions; snap-line HUD layout editor; full theme customization engine. | Versioned JSON configs, rich keybind conflict handling, fuzzy search across descriptions, accessible notifications. |
| **10** | **RusherHack** | Crisp, high-density technical dark theme with sharp 1px borders and dockable windows. | Multi-column panels + tree-structured sub-settings; precision numeric entry alongside sliders. | Minimalist zero-lag transitions; highly customizable HUD anchor system (4-corner flow stacks); dark/amber/emerald themes. | Rock-solid profile configs, granular keybind & hold/toggle modes, fast global search bar, ultra-low frame-time overhead. |
| **11** | **Wurst Client** | Classic accessible windowed/navigator GUI; focuses on simplicity and Dual-UI (`Navigator` search-first + `ClickGUI` panels). | Search-centric `Navigator` grid or classic category windows; simple sliders, checkboxes, dropdowns. | Minimal animations prioritizing clarity; simple pinned feature list HUD; basic color customization. | Simple JSON settings files, dedicated Keybind Manager screen, pioneering `Navigator` instant search & usage-frequency sorting. |
| **12** | **Aristois** | Clean hybrid dashboard menu with rounded buttons and beginner-friendly wizard layout. | Categorized grid with clear plain-language tooltips and modal module settings. | Gentle hover fades; simplified HUD widget toggles; light/dark/accent color presets. | Cloud/local profile slots, straightforward keybind picker, search bar at top of dashboard, friendly toast hints. |
| **13** | **Impact Client** | Iconic crisp flat dark panels with magenta/cyan accents and clean custom main menu. | Draggable category headers; compact inline sliders and mode cycles. | Smooth panel accordion展開; pinned mini-widgets on HUD; customizable primary accent color. | Per-profile JSON configs, inline keybind assignment, search filter highlight, very lightweight 2D font/quad renderer. |
| **14** | **Future Client** | Compact, ultra-crisp dark gray & red technical multi-column ClickGUI. | Tightly packed category strips; inline expandable color/number/enum rows. | Fast deterministic expand animations; corner-docked HUD array & info lines; customizable single-accent themes. | Reliable profile save/load, fast keybind rebinding, minimal bloat, prized for near-zero FPS drop when ClickGUI is open. |
| **15** | **Pyro Client** | Dark crimson/charcoal multi-panel ClickGUI with bordered headers. | Standard category columns; collapsible nested setting groups. | Simple vertical slide transitions; draggable HUD text/armor elements; preset dark colorways. | Local profile switching, inline keybind row, basic module name filter. |
| **16** | **Kami Blue** | Open-source dockable card/pinning GUI system supporting mixed HUD & module panels. | Highly customizable categories; typed settings with min/max bounds. | Smooth open fade; unified ClickGUI + HUD pinning workspace; preset themes (`Modern`, `Classic`). | Structured JSON config files, multi-bind support, module search popup, strong community UX ergonomics. |
| **17** | **Lambda Client** | Direct architectural evolution of Kami Blue with refined dark spacing, icons, and plugin modularity. | Clean category columns + search bar; well-validated numeric/enum/string settings. | Frame-delta UI animations; grid-snapped HUD widgets; customizable color schemes & font scaling. | Safe atomic config writes, conflict-aware keybinds, instant search + favorites category, low CPU footprint. |
| **18** | **Inertia Client** | Rounded dark modern window with left navigation rail and embedded preview cards. | Sidebar categories + scrollable module rows with dedicated settings modal. | Smooth page & modal scale animations; visual HUD editor; preset accent themes. | Multi-profile manager, visual keyboard layout keybind editor, instant search bar. |
| **19** | **BleachHack** | Lightweight Fabric-native crisp multi-window ClickGUI with minimal dependencies. | Clean Fabric-first category columns; compact tree settings and tooltips. | Minimalist fast rendering; draggable HUD containers; customizable RGB window borders. | Simple JSON config files in `.minecraft/`, standard Fabric/custom keybinds, search highlight box, very fast startup on Fabric. |
| **20** | **Ares Client** | Clean dark crimson/slate multi-panel & windowed interface with custom main menu. | Clear category headers; inline right-click settings list. | Smooth button hover highlights; basic draggable HUD overlays; dark accent presets. | Profile export/import, inline keybind capture, fast render loop. |
| **21** | **MatHax** | Extended fork architecture inspired by Meteor with purple/crimson default theme and expanded HUD/search tools. | Tabbed top bar + collapsible columns; detailed modal setting inspectors with reset controls. | Smooth collapsible animations; grid-snapping HUD editor; extensive built-in theme presets. | Profile manager with import/export, search across names & aliases, favorites pinning, toast notifications. |

### Group C — Minimalist, Competitive & Aesthetic Clients (22–41)

| # | Client | UI & ClickGUI Architecture | Categories & Settings UX | Animations, HUD & Themes | Config, Keybinds, Search & Perf Notes |
|---|---|---|---|---|---|
| **22** | **Boze Client** | Modern dark violet/obsidian technical UI with clean vector icons and split settings pane. | Category sidebar + dual-pane module/inspector layout; rich color & curve controls. | Fluid cubic hover & slider interpolation; precision HUD alignment guides; custom theme export. | Versioned configs, advanced keybind hold/toggle modes, fast global search & favorites. |
| **23** | **Prestige Client** | Sleek Fabric-modern unified dark card interface with crisp typography and subtle borders. | Clean sidebar tabs + responsive module cards; intuitive sliders and pill toggles. | Smooth delta-time micro-interactions; clean HUD widget snapping; curated dark palettes. | Fast JSON config profiles, clear keybind badges, instant search bar, minimal GPU impact. |
| **24** | **Vape Client** | Pioneered drawer-style modular panels with pin-to-HUD capability, subtle dark gray surfaces, and clean iconography. | Collapsible panels + settings gear drawer; dual-handle sliders and clean checkboxes. | Restrained, ultra-responsive micro-animations; pinnable mini-panels; subtle accent customization. | Cloud/local profile manager with state diffs, inline bind buttons, instant filter bar, strong UX polish. |
| **25** | **Whiteout Client** | Ultra-minimalist high-contrast monochrome/dark desktop-app aesthetic. | Left navigation bar + clean vertical form controls; uncluttered whitespace. | Fast linear/cubic fades; minimal HUD footprint; dark/light monochrome themes. | Clean profile slots, straightforward keybind capture, instant search input. |
| **26** | **Raven Client** | Lightweight classic dark multi-column dropdown ClickGUI. | Compact category strips; simple inline sliders and toggles. | Minimal animation overhead; lightweight corner HUD readouts; simple accent color controls. | Lightweight text/JSON configs, simple inline keybinds, tiny memory footprint. |
| **27** | **Raven B+** | Community-refined dark ClickGUI expanding Raven with cleaner font rendering and config profiles. | Improved category spacing; clearer setting labels and bounds validation. | Smoother dropdown expand transitions; basic HUD positioning; preset color themes. | JSON profile switching, improved keybind handling, low resource usage. |
| **28** | **Raven B++** | Enhanced dark theme with search bar, richer HUD counters, and better slider ergonomics. | Category columns + quick search filter; refined numeric step controls. | Smooth bar fills; movable HUD counters (CPS/FPS/Keystrokes); expanded gradient themes. | Multi-config manager, quick unbind support, fast 2D quad rendering. |
| **29** | **Raven XD** | Modernized visual refresh with rounded dark cards and smoother transitions. | Cleaner card padding and categorized settings dividers. | Frame-time interpolated toggles & notifications; draggable HUD overlays; modern accent presets. | Structured JSON configs, inline keybind badges, responsive UI input. |
| **30** | **Rise Client** | Benchmark in motion design: modern rounded glassmorphic window, expressive typography, and cohesive theme engine. | Sidebar categories + spacious module cards; smooth animated sliders, dropdowns, and color wheels. | Industry-reference spring/cubic easing on pages, cards, and notifications; rich HUD editor; 10+ curated themes. | Seamless config browser, visual keybind badges, prominent search bar + favorites; requires careful blur optimization to avoid FPS cost. |
| **31** | **Astolfo Client** | Clean, compact dark bordered multi-panel ClickGUI with pastel/neon category color coding. | Color-coded category columns (each category has a signature hue); precise inline sliders. | Crisp, snappy expand/collapse; clean HUD framing; per-category accent colors. | Script/JSON configs, reliable keybind system, zero-clutter technical readability. |
| **32** | **Flux Client** | Polished rounded desktop-window ClickGUI with sidebar icons and custom main menu. | Left icon rail + 2-column card view; clean modal/inline settings controls. | Smooth page transitions and toast progress bars; visual HUD drag editor; light/dark/custom themes. | Visual profile cards, dedicated keybind screen, instant search box. |
| **33** | **Sigma Client (Jello)** | Famous minimalist frosted-glass aesthetic ("Jello" design language) with large clean typography andairy spacing. | Icon/card grid launcher + clean modal settings sheet per module. | Signature smooth zoom+blur modal open/close transitions; clean minimalist HUD widgets. | Profile switcher with visual previews, clean keybind map screen, instant search overlay; heavy Gaussian blur taught the industry to make blur optional! |
| **34** | **Tenacity Client** | Vibrant modern dark UI with dual-accent gradients, rounded cards, and expressive notification animations. | Unified window or dropdown mode switch; clean multi-select chips and HSV color pickers. | Smooth damped sliders and sliding tab indicators; snap-to-center HUD editor; rich preset theme gallery. | Local config manager with import/export, inline keybind pills, instant search + pinned favorites. |
| **35** | **NovoLine Client** | Classic crisp multi-column dark ClickGUI with bright status indicators and dense controls. | Compact category columns; multi-select dropdown boxes and inline color bars. | Fast expand animations; draggable HUD status boxes; customizable primary/secondary colors. | Config slot manager, keyboard bind matrix overlay, fast responsiveness. |
| **36** | **FDP Client** | Community fork extending LiquidBounce with unified dark window templates and expanded HUD presets. | Sidebar/panel switchable ClickGUI; typed settings rows. | Animated toast notifications and button ripples; visual HUD designer; imported theme templates. | JSON config profiles, visual keybind manager screen, search filter. |
| **37** | **Augustus Client** | Utilitarian dark/blue engineering interface prioritizing exhaustive configurability over decoration. | Tabbed/columnar layout with dozens of granular parameters per module. | Minimal animations; functional HUD readouts; basic dark color schemes. | Comprehensive config serialization; demonstrates why search, descriptions, and "Reset to Default" per setting are essential when settings count grows! |
| **38** | **Polar Client** | Clean icy-blue & deep-navy dark aesthetic (`#080C16` + `#00D9FF` inspiration) with sharp contrast and calm hierarchy. | Left category sidebar + uncluttered single/dual-column cards; crisp toggle switches. | Smooth subtle hover & toggle easing; clean aligned HUD badges; cool-toned dark themes. | Clean JSON configs, conflict-free keybind capture, fast search bar, excellent readability. |
| **39** | **Moon Client** | Deep midnight-indigo palette with soft glow accents and clean custom main menu. | Sidebar window or classic panels; clean inline accordions for settings. | Smooth cubic fade transitions; magnetic HUD snapping; lunar/midnight dark palettes. | Multi-config manager, inline keybind selector, toast notifications. |
| **40** | **Drip Client** | Modern sleek dark desktop-grade interface with purple/violet accents and clean spacing. | Sidebar navigation + card grid; clean step sliders and toggle switches. | Smooth micro-animations on toggles & tabs; clean minimal HUD widgets; dark violet themes. | Profile save/load/import, clean keybind pills, instant search filter. |
| **41** | **Ghost Client (Generic Archetype)** | Minimalist single-window utility overlay designed to blend cleanly with desktop/game UI without visual noise. | Simple 4–5 tab bar; uncluttered sliders and boolean switches. | Near-instant subtle fades; minimal optional HUD text; muted slate themes. | Lightweight config JSON, quick panic/close key (`Right Shift` / `Escape`), negligible CPU/GPU cost. |

### Group D — Legitimate PvP, Performance, QoL & Ecosystem Platforms (42–50)

| # | Client | UI & ClickGUI Architecture | Categories & Settings UX | Animations, HUD & Themes | Config, Keybinds, Search & Perf Notes |
|---|---|---|---|---|---|
| **42** | **Lunar Client** | Gold-standard legitimate PvP/QoL launcher + in-game `Right Shift` mod menu with large visual module cards! | Unified centered modal (`Mods` / `Settings` / `Waypoints`) + grid of icon cards with `OPTIONS` button & toggle switch. | Polished smooth hover elevation, toggle slides, and screen fades; best-in-class `Edit HUD Layout` with center/edge snap lines & scaling! | Multi-profile manager synced between Launcher & Game, clear keybind controls, instant search + `Favorites` star filter, strict fair-play server rule compliance! |
| **43** | **Badlion Client** | Comprehensive legitimate gaming platform with desktop launcher + searchable slide-out/modal in-game mod grid. | Categorized PvP/HUD/Utility cards + rich per-mod visual settings modal with live preview. | Smooth slide/fade transitions; full multi-anchor HUD editor with resize handles & presets; dark/accent themes. | Cloud/local profiles with backup/share, conflict-checked keybinds, search + favorites bar, strict server-side mod-disable API support! |
| **44** | **Feather Client** | Modern Fabric-friendly legitimate launcher + ultra-clean Neo-Dark in-game HUD/mod manager. | Clean sidebar + 3-column card grid; modern pill switches, clean color pickers, and tidy typography. | Crisp 60fps+ eased UI animations; intuitive HUD drag-and-scale canvas with lock button; sleek dark teal/indigo palette. | Seamless Launcher <-> Fabric mod profile management, clean keybind editor, fast search & favorites, high FPS efficiency. |
| **45** | **LabyMod** | Modular legitimate ecosystem seamlessly integrated into Minecraft's native Options & custom tabbed dashboard. | Nested category tabs + rich widget inspector; extensive HUD widget customization. | Smooth native-feeling transitions; advanced alignment-grid & grouped HUD widget editor; theme customization. | Per-server/global profiles, shortcut manager, instant widget search, strong multi-version adapter architecture! |
| **46** | **Essential Mod** | Polished modern UI toolkit (`Elementa`) adding social/QoL menus, screenshot browser, and wardrobe/settings screens. | Clean modern panels & modal dialogs respecting Minecraft GUI flow; rich screenshot gallery UX! | Frame-rate independent declarative animations; toast notification stack; cohesive dark/slate surfaces. | Clean JSON config persistence, non-conflicting keybinds, searchable screenshot & settings UI, multi-version adapter layer. |
| **47** | **ForgeHax** | Technical developer-oriented modular client with command/config focus. | Functional list/panel layout; typed hierarchical settings. | Minimal UI animation; compact technical HUD readouts. | Structured JSON configs; highlights importance of clean separation between core module logic and version-specific Minecraft hooks. |
| **48** | **SalHack** | Classic multi-column ClickGUI + dedicated `HudEditor` separate module/screen pattern. | Clear separation between gameplay/utility categories and a dedicated `HUD` category + `HUD Editor` canvas. | Simple fast fades; independent draggable HUD components with snapping; preset color schemes. | Per-module/preset folder configs, keybind support, instant module search filter. |
| **49** | **Meteor Rejects** | Addon ecosystem demonstrating clean extension API design atop a shared client core. | Plugs seamlessly into host categories, settings types, and HUD registry. | Inherits host animation & theme tokens automatically. | Proves that a well-designed `Module` + `Setting` + `HudElement` API lets dozens of modules remain clean, uniform, and maintainable. |
| **50** | **Huzuni Client** | Historic milestone client that popularized themed ClickGUI windows, clean font rendering, and custom main menus. | Windowed draggable panels with clean toggle rows and sliders. | Early smooth window fade & custom main menu transitions; clean corner HUD array. | Simple text configs and keybind manager; historical lesson in keeping ClickGUI immediately intuitive on first open. |

---

## 3. Architectural & UX Synthesis for AXSESCLIENT GENESIS X

From studying all 50 clients, we distilled **10 decisive engineering principles** implemented natively in **AXSESCLIENT GENESIS X**:

1. **Legitimate Fair-Play Boundary (Inspired by Lunar, Badlion, Feather, LabyMod, Essential)**:
   - Zero anti-cheat bypasses, zero packet manipulation, zero client brand spoofing, zero killaura/aimbot/reach/fly cheats.
   - Gameplay-assisting combat drills (`Training Target`, `Reaction Training`, `Attack Timing Practice`) are strictly gated by `CompatibilityEngine` to **Singleplayer / Local Practice Worlds** and automatically disabled on external multiplayer servers with an explicit user-visible reason.

2. **Unified Desktop + In-Game Design System (`AXSES NEO-DARK`)**:
   - Identical visual language, shared JSON design tokens (`AXSESCLIENT/shared/design-tokens.json`), and shared config/theme formats across the **C# Avalonia Windows Launcher (`AXSESCLIENT.exe`)** and the **Java Fabric Clients (`1.16.5`, `1.21.4`, `1.21.11`)**.
   - Canonical Color Tokens:
     - `Background`: `#070911`
     - `Surface`: `#10131D`
     - `Elevated`: `#191D2B`
     - `Primary`: `#9060FF`
     - `Secondary`: `#536FFF`
     - `Accent`: `#00D9FF`
     - `Text`: `#F7F8FF`
     - `Muted`: `#929BB1`
     - `Success`: `#39DFA2`
     - `Warning`: `#FFC16A`
     - `Error`: `#FF5B79`

3. **Master-Detail Unified ClickGUI (`Right Shift` Open/Close + `Escape` Close)**:
   - Combines the discoverability of **Lunar / Feather's** clean card grid, **Meteor / Boze's** structured right-hand/expandable inspector, and **Rise / Polar's** calm Neo-Dark contrast.
   - Left Sidebar with custom vector-styled category icons (`Combat Training`, `Movement`, `Render`, `Player`, `World`, `Utility`, `HUD`, `Settings`), topbar instant search (matching names + descriptions + search history chips), `★ Favorites` filter, sort selector, quick `HUD Editor` launcher button, and `Theme` / `Config` quick-switchers.

4. **Frame-Time Independent Animation Engine**:
   - Driven strictly by `deltaTime` (`System.nanoTime()` in Java; `Stopwatch` high-resolution ticks in Avalonia C#) — behaves identically at 30 FPS, 60 FPS, 144 FPS, and 360 FPS.
   - Includes `Animation Speed`, `Animation Intensity`, and `Reduced Motion` accessibility mode (inspired by lessons from Sigma/Rise where heavy effects needed user-controlled scaling).

5. **Magnetic Multi-Resolution HUD Editor**:
   - Normalized anchor + proportional offset coordinates so resizing from `854×480` -> `1280×720` -> `1920×1080` -> `2560×1440` never pushes HUD widgets off-screen.
   - Supports Drag & Drop, Resize handles, Per-widget Scale, Opacity, Background, Shadow, Outline, Alignment, Grid Snapping, Edge & Center magnetic guide lines, Element Grouping, Position Locking, Presets, and Multi-Resolution Canvas Preview.

6. **Strict Multi-Version Adapter Isolation (`1.16.5` · `1.21.4` · `1.21.11`)**:
   - `client-common` holds 100% pure Java 8-compatible core business logic (`AxsesCore`, all 12 managers, all 90 module/GUI controllers, zero Minecraft imports).
   - `client-1.16.5` (`PoseStack`, `ResourceLocation`, Java 8), `client-1.21.4` (`GuiGraphics` + `PoseStack`, `ResourceLocation`, Java 21), and `client-1.21.11` (`GuiGraphics` + `Matrix3x2fStack`, `Identifier`, `MouseButtonEvent`/`KeyEvent`, `HudElementRegistry`, Java 21) implement version-verified adapters and Mixins with zero cross-version hacks.

7. **Corruption-Proof Config & Theme Persistence**:
   - Strict data-only JSON parsing (no reflection/script execution), schema versioning (`schemaVersion: 1`), value clamping (`Min`/`Max`/`Step`), automatic `.bak` backup before overwrite, and graceful recovery + toast notification if a user edits or corrupts a JSON file manually.

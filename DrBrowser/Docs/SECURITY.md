# Security and privacy model

DrBrowser is a desktop browser, not a security sandbox beyond Windows/WebView2. This document states implemented controls and boundaries; it is not a claim of anonymity or formal certification.

## Implemented controls

| Area | Control |
|---|---|
| TLS | WebView2 certificate/HTTPS validation stays enabled. No ignore-certificate switch is exposed. |
| URL schemes | Navigation accepts internal routes and normal HTTP/HTTPS. Dangerous script/data schemes are rejected by address parsing; external schemes are tightly limited. |
| Permissions | Camera, microphone, geolocation and similar WebView2 permissions require a user decision and can be persisted per host. |
| Popups | New windows are routed into controlled tabs; arbitrary system access is not granted. |
| Downloads | Files are never automatically executed. Open/open-folder are explicit user actions. DrGuard can invoke Defender only after completion when enabled/available. |
| Vault | Password values are encrypted with Windows DPAPI CurrentUser before SQLite insertion. No automatic capture/autofill. |
| AI key | The OpenAI-compatible key is a DPAPI-protected secret file and is not written to settings/logs/backups. |
| Ollama | Endpoint validation accepts loopback only, preventing configuration as a general internal-network proxy. |
| DrMind actions | Hard-coded allowlist (save response as Note, open HTTPS URL) with confirmation. No shell tool, arbitrary file tool or unrestricted OS access. |
| AI context | Page, search and memory content is bounded/labeled untrusted. Explicit memory is off by default and never inferred automatically. |
| DrSearch crawler | Robots/delay/domain/page/depth/size/redirect bounds; DNS resolution rejects private, loopback, link-local and similar addresses; `noindex` honored. |
| AdBlock | Request cancellation only; no TLS interception or certificate installation. |
| Private window | Separate temporary WebView2 profile and temporary SQLite DB, best-effort deletion on close. |
| Backup | Strict ZIP allowlist/path rejection, manifest/version, SHA-256, JSON parse and SQLite integrity check. Secret directory/profile excluded. |
| Logging | Feature errors and operational status only; password/key plaintext is not logged. Private navigation is not persisted to normal history. |
| Telemetry | No hidden analytics/telemetry endpoint. |

## Trust boundaries

### Web content

Web pages execute in WebView2/Chromium. They do not receive a host object that exposes arbitrary .NET methods. DrBrowser does not inject an unrestricted bridge. Browser permissions and downloads pass through explicit WebView2 events.

### AI providers

Local deterministic mode sends nothing. Ollama sends prompts to the configured loopback service. OpenAI-compatible mode sends the user message and whichever optional contexts the user enables to the configured endpoint. Provider operators can see those requests; DrBrowser cannot enforce their retention policy.

Model output is untrusted. Controlled actions parse only an HTTPS URL or save text into Notes after confirmation. Output is not executed as code.

### Local storage

SQLite is not globally encrypted. History, bookmarks, Notes, Tasks, Workspaces, search documents and AI memory are readable by software/user accounts that can read the Windows profile. Only Vault password values and API keys receive DPAPI protection.

DPAPI protects against offline/plaintext disclosure and other Windows users under normal ACLs; it does not protect against malware running as the same user or a compromised live process. Decrypted Vault values exist in memory when loaded.

### Backup

A `.drbackup` is not a generally encrypted archive. It can contain plaintext local content such as history and Notes. It excludes API-key files and WebView2 profile data. Vault values remain DPAPI ciphertext. Store backups accordingly; portability of Vault values is limited to the same Windows user/machine context supported by DPAPI.

## Private mode limitations

Private mode prevents DrBrowser’s normal persistent history/session/database/profile from recording that window. It does **not**:

- hide IP or DNS traffic from providers/networks;
- defeat site/browser fingerprinting;
- erase files the user downloads;
- control remote website/AI/search/provider logs;
- protect against OS-level monitoring or malware;
- guarantee deletion if Windows terminates the process or files remain locked (cleanup is retried/best effort).

## DrGuard/Defender limitations

DrGuard has URL warning/block rules and can request a Microsoft Defender CLI scan. This is defense in depth, not an antivirus guarantee. Defender may be absent/disabled, signatures may miss threats, and a clean result does not prove safety. DrBrowser does not run downloaded executables automatically.

## Deliberate non-features

- No CAPTCHA/auth/DRM bypass.
- No disabling TLS verification or installing a root CA.
- No hidden process injection, game injection or anti-cheat interaction.
- No automatic model download or bundled multi-gigabyte model.
- No password interception/autofill.
- No claim that network AdBlock provides cosmetic filtering or complete tracking prevention.
- No cloud synchronization/telemetry.

## Reporting a security issue

Do not include real credentials, Vault databases or API keys in a public issue. Reproduce with synthetic data and identify the source revision, Windows version, WebView2 version and relevant log excerpt after redaction.

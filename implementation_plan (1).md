# Automatic Transaction SMS Detection — Mauzo App

Automatically detect, filter, verify, and record Tanzanian mobile money transaction SMS (Vodacom M-Pesa, Airtel Money, and Mixx by Yas) in real time and via inbox synchronization—100% offline, privacy-preserving, and resilient across restarts.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> You confirmed proceeding with automatic Transaction SMS detection. Based on Mauzo App's offline-first security architecture and Google Play SMS permission requirements, the plan adopts the recommended choices below; let me know if you want any adjustments before execution.

- **Detection Scope & Trigger (Recommended)**: Real-time incoming SMS broadcast listener paired with on-demand and startup SMS inbox synchronization (when `RECEIVE_SMS` and `READ_SMS` are granted), plus seamless manual SMS paste fallback when permissions are withheld.
- **User Alert Feedback (Recommended)**: Display a local Android notification (`NotificationCompat`) summarizing newly detected transactions (or flagging `Needs Review` / `Suspicious` alerts) while atomically updating the ledger and wallet balances.
- **Ambiguous / Partial SMS Handling (Recommended)**: Archive any potential financial SMS that fails full parsing in the **SMS** tab and create a `NEEDS_REVIEW` record so no transaction is ever silently discarded.
- **Custom Sender IDs & Dual-SIM Support (Recommended)**: Allow configuring custom sender identifiers and optional SIM slot filtering per network (Vodacom M-Pesa, Airtel Money, Mixx by Yas) inside **Mipangilio**, using `NetworkFormatEntity` and `SmsBroadcastReceiver` subscription metadata.

---

## 1. Overview & Core Concept

- **What It Does**: Enhances Mauzo App's SMS detection pipeline so incoming mobile money alerts are captured immediately in the background, stripped of OTPs and personal chats before storage, parsed by network-specific engines, verified against anti-spoofing and duplicate rules, persisted atomically to the encrypted local database, and surfaced via an unobtrusive local notification and live dashboard refresh.
- **Target Audience / Persona**: Tanzanian micro and SME business owners (duka, wakala, retail) using dual-SIM Android phones who receive dozens of M-Pesa, Airtel Money, and Mixx by Yas alerts daily and need zero-effort, offline-reliable bookkeeping.
- **Key Value**: Eliminates manual entry errors and missed payments while strictly protecting privacy (no `INTERNET` permission, no OTP collection, and explicit distinction between SMS format validation and official provider confirmation).

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Permission Rationale & Onboarding**: When auto-detection is not yet active, the **Nyumbani** and **SMS** screens display an emerald-bordered rationale card explaining why `RECEIVE_SMS`, `READ_SMS`, and (on Android 13+) `POST_NOTIFICATIONS` are requested, alongside an instant **Tumia Kuingiza SMS Mwenyewe** (Manual Import) path.
  2. **Live Auto-Detection & Notification**: When a qualifying M-Pesa, Airtel Money, or Mixx by Yas SMS arrives, the background worker processes it and posts a concise local notification (e.g., *"M-Pesa: +TZS 45,500 kutoka JUMA HASSAN — Umbizo Limethibitishwa"*). Tapping the notification opens Mauzo App directly to the ledger or detail view.
  3. **Live Detection Status & Custom Sender Config**: In **SMS** and **Mipangilio**, the business owner sees an active detection indicator (*Ufuatiliaji Kiotomatiki: Umewashwa*), can trigger **Changanua Kikasha cha SMS** to catch missed messages, and can customize accepted sender IDs (e.g., adding a custom wakala shortcode) or SIM slot preference per network.
  4. **Reviewing Unrecognized or Suspicious Alerts**: Any partial or spoofed SMS appears with an Amber (`NEEDS_REVIEW`) or Crimson (`SUSPICIOUS`) pill and an explicit disclaimer reminding the owner that SMS format matching is not official provider confirmation.

- **Visual Identity & Theme**:
  - *Aesthetic Direction*: Crisp corporate-grade Tanzanian fintech ledger—high-contrast clarity, disciplined card hierarchy, and unmistakable trust cues.
  - *Color Palette & Mood*: Dominant **Dark Green** (`#064E3B`) and **Dark Navy** (`#0F172A`) hero headers paired with **Emerald Green** (`#10B981`) positive inflows, **Pure White** (`#FFFFFF`) elevated cards over **Light Grey** (`#F1F5F9`) canvas, **Amber** (`#D97706`) review warnings, and **Crimson** (`#DC2626`) outflows/alerts.
  - *Typography & Hierarchy*: **Plus Jakarta Sans** for display figures and headers, **Inter** for clean Swahili/English body copy, and **JetBrains Mono** for tabular TZS amounts, Transaction IDs, and phone numbers.
  - *Component Styling & Layout*: Rounded Material 3 cards (`16dp`–`24dp`), `48dp` minimum touch targets, edge-to-edge safe insets, and adaptive bottom `NavigationBar` (phones) / side `NavigationRail` (tablets/foldables).

- **Interactive Feedback & Motion**: Immediate snackbar confirmation on inbox scan summaries, live reactive `StateFlow` updates across all 5 tabs (`Nyumbani`, `Miamala`, `SMS`, `Ripoti`, `Mipangilio`), and visual pulse/status badges for live SMS detection readiness.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: BroadcastReceiver + WorkManager + Optional Inbox Scan**
  - *Chosen Approach*: Use `SmsBroadcastReceiver` (`SMS_RECEIVED`) backed by `WorkManager` (`ProcessSmsWorker`) for immediate event-driven capture, complemented by `SmsInboxImporter` (`Telephony.Sms.Inbox`) for catching messages received while permissions were pending or after device restart.
  - *Why*: `BroadcastReceiver` has a strict execution window; handing off to `WorkManager` guarantees encrypted Room persistence survives app termination and battery restrictions without dropping financial alerts.
  - *Alternatives Considered*: A persistent foreground service or `NotificationListenerService`—rejected because foreground services drain battery unnecessarily and notification scraping is fragile across OEM skins and misses full SMS bodies.

- **Decision 2: Strict OTP & Personal Chat Pre-Filtering Before Persistence**
  - *Chosen Approach*: Run `SmsPreFilter` inside both `SmsBroadcastReceiver` and `SmsInboxImporter` using dynamic sender lists from `NetworkFormatEntity` plus keyword/currency heuristics before queuing or writing to `sms_archive`.
  - *Why*: Ensures private conversations and OTP codes never touch the database or logs.
  - *Alternatives Considered*: Saving all SMS and filtering in the UI—rejected due to privacy risk and storage bloat.

- **Decision 3: Preserve Opening Balances Across Wallet Recalculations**
  - *Chosen Approach*: Update `WalletDao` to use non-destructive `@Upsert` / `@Update` semantics instead of `OnConflictStrategy.REPLACE` (which triggers SQLite `DELETE` + `INSERT` and cascades deletions to `OpeningBalanceEntity`), ensuring opening balances remain intact during automatic SMS ingestion and restore operations.
  - *Why*: Protects financial accuracy across every auto-detected transaction.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

- **Architecture & Component Diagram**:

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                         Android OS Telephony & Notifications                  │
│   [SMS_RECEIVED Broadcast]          [Telephony.Sms.Inbox]   [NotificationMgr] │
└──────────────┬───────────────────────────────┬──────────────────────▲─────────┘
               │                               │                      │
               ▼                               ▼                      │
┌───────────────────────────┐   ┌────────────────────────────┐        │
│    sms_reader Module      │   │    sms_reader Module       │        │
│ • SmsBroadcastReceiver    │   │ • SmsInboxImporter         │        │
│   (SIM slot & pre-filter) │   │   (Permission-gated scan)  │        │
│ • ProcessSmsWorker        │   └──────────────┬─────────────┘        │
│   (WorkManager durable)   │                  │                      │
└──────────────┬────────────┘                  │                      │
               └───────────────┬───────────────┘                      │
                               ▼                                      │
┌─────────────────────────────────────────────────────────────────────┴─────────┐
│                    Core Processing Pipeline (UI-Independent)                   │
│                                                                                │
│  [sms_parser]                  [verification]             [transaction_engine] │
│  • SmsPreFilter (dynamic IDs)  • 9-Point Rule Engine      • BigDecimal Math    │
│  • M-Pesa / Airtel / Mixx      • Anti-Spoof & Duplicate   • Reversal/Refund    │
└──────────────────────────────────────┬─────────────────────────────────────────┘
                                       ▼
┌────────────────────────────────────────────────────────────────────────────────┐
│                        data & security Modules (Offline)                       │
│  • Android Keystore + SQLCipher AES-256 Room DB (7 Entities, MIGRATION_1_2)   │
│  • Non-destructive Wallet Upsert preserving Opening Balances                   │
└──────────────────────────────────────┬─────────────────────────────────────────┘
                                       ▼
┌────────────────────────────────────────────────────────────────────────────────┐
│                            presentation Module (M3)                            │
│  • MauzoViewModel (StateFlow) -> Nyumbani | Miamala | SMS | Ripoti | Mipangilio│
└────────────────────────────────────────────────────────────────────────────────┘
```

- **Data Model & State**:
  - `NetworkFormatEntity` stores configurable `senderIdentifiersCsv` and `isEnabled` per network (`MPESA`, `AIRTEL_MONEY`, `MIXX_BY_YAS`), allowing users to add custom sender shortcodes and toggle auto-detection per network.
  - `SmsArchiveEntity` records every processed mobile money SMS (`senderIdentifier`, `originalContent`, `network`, `extractedTransactionId`, `extractedAmountMinor`, `processingStatus`, `importSource`), linked to `TransactionEntity` via `smsArchiveId`.
  - `WalletEntity` and `OpeningBalanceEntity` maintain per-network `calculatedBalanceMinor` and `providerReportedBalanceMinor` with non-destructive updates so opening balances are never cascade-deleted.
  - `BusinessSettingsEntity` tracks `pinEnabled`, `biometricEnabled`, `primaryLanguage`, and SMS auto-detection notification preferences.

- **Interactive Component & State Mapping**:
  - **Permission Rationale & Runtime Request**: Triggered from **Nyumbani**, **SMS**, or **Mipangilio**; requests `RECEIVE_SMS`, `READ_SMS`, and `POST_NOTIFICATIONS` (API 33+) and updates `MauzoUiState.smsPermissions` immediately on result.
  - **Custom Sender ID Editor & SIM Slot Filter**: Located in **Mipangilio** under *Mitandao & Ruhusa za SMS*; edits `NetworkFormatEntity.senderIdentifiersCsv` and SIM slot preference, feeding directly into `SmsPreFilter` during both live broadcast reception and inbox scans.
  - **Local Transaction Notification**: Posted by `TransactionNotificationHelper` inside `ProcessSmsWorker` after `MauzoRepositoryImpl.ingestIncomingSms()` commits atomically, respecting `BusinessSettings` notification toggle and avoiding sensitive data leaks when the app is PIN-locked (optional privacy-masked notification mode).
  - **Manual Inbox Scan & Paste Fallback**: **Changanua Kikasha cha SMS** invokes `SmsInboxImporter.scanInboxForMobileMoneySms()`, while **Ingiza SMS** parses pasted messages through the exact same `SmsParserOrchestrator` -> `TransactionVerificationEngine` -> `FinancialTransactionEngine` pipeline.

# MAUZO APP

Offline-first Tanzanian mobile money transaction monitor, financial ledger, and local reporting Android application built with **Kotlin**, **Jetpack Compose**, **Material 3**, **Room + SQLCipher**, **Android Keystore**, and **WorkManager**.

## Key Capabilities
- **100% Offline & Private**: No `INTERNET` permission, no cloud sync, no Firebase, and no AI API transmission of SMS or financial records.
- **Supported Networks**: Vodacom M-Pesa, Airtel Money, and Mixx by Yas (including legacy Tigo Pesa formats).
- **SMS Reader & Pre-Filter**: Explicit runtime rationale & permission handling (`RECEIVE_SMS`, `READ_SMS`, `POST_NOTIFICATIONS`), automatic OTP/personal message rejection, configurable custom sender IDs, Dual-SIM slot filtering, privacy-aware local transaction alerts, WorkManager deferred ingestion, and zero-permission Manual SMS Paste/Import fallback.
- **Rule-Based Verification Engine**: Flags `FORMAT_VALIDATED`, `NEEDS_REVIEW`, `DUPLICATE`, `SUSPICIOUS` (including personal phone sender spoofing), `REVERSED`, and `REFUNDED` with explicit distinction between SMS format checks and official provider confirmation.
- **Decimal-Safe Financial Engine**: `BigDecimal` arithmetic with minor-unit integer SQLite persistence, double-reversal/double-refund prevention, per-network calculated vs provider-reported balances, and audited reconciliation.
- **Reports & Local Export**: Daily, Weekly, Monthly, and Annual summaries with grouped cashflow bar charts and local Storage Access Framework export to **CSV**, **Excel-compatible `.xls`**, and **PDF**.
- **Security**: SQLCipher AES-256 database encryption keyed via Android Keystore, salted PBKDF2 PIN lock, BiometricPrompt authentication, and AES-256-GCM encrypted backup/restore.

## Module Structure (`app/src/main/java/com/example/`)
1. `domain/` — Pure Kotlin models, `BigDecimal` `MoneyUtils`, and `MauzoRepository` contract.
2. `security/` — `KeystoreManager`, `PinManager`, `BiometricAuthHelper`, `BackupCrypto`.
3. `sms_parser/` — `SmsPreFilter`, `MpesaSmsParser`, `AirtelMoneySmsParser`, `MixxByYasSmsParser`, `SmsParserOrchestrator`.
4. `verification/` — `TransactionVerificationEngine` (9-point rule engine).
5. `transaction_engine/` — `FinancialTransactionEngine` (decimal-safe ledger math & dashboard metrics).
6. `data/` — Room entities (7 tables), DAOs, `MauzoDatabase` (SQLCipher + `MIGRATION_1_2`, `MIGRATION_2_3`), `MauzoRepositoryImpl`, `BackupRestoreManager`, `AutoBackupWorker`.
7. `sms_reader/` — `SmsBroadcastReceiver`, `ProcessSmsWorker`, `SmsInboxImporter`, `TransactionNotificationHelper`, `SenderDirectoryCache`.
8. `reporting/` — `FinancialReportGenerator`, `ReportExporter` (CSV, SpreadsheetML `.xls`, native `PdfDocument`).
9. `presentation/` — Swahili/English `AppStrings`, type-safe Navigation Compose routes, `MauzoViewModel`, and Compose screens (`Nyumbani`, `Miamala`, `SMS`, `Ripoti`, `Mipangilio`, `TransactionDetail`, `LockScreen`).

## Testing & Build Commands
```bash
# Run unit and Robolectric tests
gradle :app:testDebugUnitTest

# Build Debug APK
gradle :app:assembleDebug

# Build Release APK (requires signing env vars)
gradle :app:assembleRelease
```

package com.example.data

import androidx.room.withTransaction
import com.example.domain.AuditLogRecord
import com.example.domain.BusinessSettings
import com.example.domain.MauzoRepository
import com.example.domain.MobileMoneyNetwork
import com.example.domain.MoneyUtils
import com.example.domain.NetworkFormatConfig
import com.example.domain.OpeningBalanceRecord
import com.example.domain.SmsArchiveRecord
import com.example.domain.SmsIngestionOutcome
import com.example.domain.SmsProcessingStatus
import com.example.domain.TransactionRecord
import com.example.domain.TransactionType
import com.example.domain.VerificationStatus
import com.example.domain.WalletSummary
import com.example.sms_parser.PreFilterDecision
import com.example.sms_parser.SmsParseResult
import com.example.sms_parser.SmsParserOrchestrator
import com.example.sms_parser.SmsPreFilter
import com.example.transaction_engine.FinancialTransactionEngine
import com.example.verification.TransactionVerificationEngine
import com.example.verification.VerificationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MauzoRepositoryImpl(
    private val db: MauzoDatabase,
    private val parser: SmsParserOrchestrator = SmsParserOrchestrator(),
    private val verifier: TransactionVerificationEngine = TransactionVerificationEngine(),
    private val txEngine: FinancialTransactionEngine = FinancialTransactionEngine()
) : MauzoRepository {

    private val txDao = db.transactionDao()
    private val smsDao = db.smsArchiveDao()
    private val walletDao = db.walletDao()
    private val openingDao = db.openingBalanceDao()
    private val formatDao = db.networkFormatDao()
    private val settingsDao = db.businessSettingsDao()
    private val auditDao = db.auditLogDao()

    suspend fun ensureSeeded() {
        val existingSettings = settingsDao.getOnce()
        if (existingSettings != null && walletDao.getAllOnce().isNotEmpty()) return

        val now = System.currentTimeMillis()
        db.withTransaction {
            if (walletDao.getAllOnce().isEmpty()) {
                MobileMoneyNetwork.supportedNetworks.forEach { net ->
                    walletDao.upsert(
                        WalletEntity(
                            network = net.code,
                            calculatedBalanceMinor = 0L,
                            providerReportedBalanceMinor = null,
                            providerBalanceUpdatedAtEpochMs = null,
                            lastReconciledAtEpochMs = null,
                            transactionCount = 0,
                            updatedAtEpochMs = now
                        )
                    )
                    openingDao.upsert(
                        OpeningBalanceEntity(
                            network = net.code,
                            amountMinor = 0L,
                            effectiveDateEpochMs = now,
                            note = "Salio la awali",
                            updatedAtEpochMs = now
                        )
                    )
                }
            }

            if (formatDao.getAllOnce().isEmpty()) {
                formatDao.upsertAll(
                    listOf(
                        NetworkFormatEntity(
                            network = MobileMoneyNetwork.MPESA.code,
                            displayName = "Vodacom M-Pesa",
                            senderIdentifiersCsv = "MPESA,M-PESA,VODACOM,15000,15300",
                            formatVersion = "MPESA_TZ_V2",
                            isEnabled = true,
                            updatedAtEpochMs = now
                        ),
                        NetworkFormatEntity(
                            network = MobileMoneyNetwork.AIRTEL_MONEY.code,
                            displayName = "Airtel Money",
                            senderIdentifiersCsv = "AIRTELMONEY,AIRTEL MONEY,AIRTEL,15050",
                            formatVersion = "AIRTEL_TZ_V2",
                            isEnabled = true,
                            updatedAtEpochMs = now
                        ),
                        NetworkFormatEntity(
                            network = MobileMoneyNetwork.MIXX_BY_YAS.code,
                            displayName = "Mixx by Yas",
                            senderIdentifiersCsv = "MIXX BY YAS,MIXX,YAS,TIGOPESA,TIGO PESA,15001",
                            formatVersion = "MIXX_TZ_V2",
                            isEnabled = true,
                            updatedAtEpochMs = now
                        )
                    )
                )
            }

            if (settingsDao.getOnce() == null) {
                settingsDao.upsert(
                    BusinessSettingsEntity(
                        id = 1,
                        businessName = "Biashara Yangu",
                        primaryLanguage = "sw",
                        currencyCode = "TZS",
                        hideBalancesOnHome = false,
                        pinEnabled = false,
                        biometricEnabled = false,
                        autoBackupEnabled = true,
                        lastBackupEpochMs = null,
                        onboardingCompleted = false,
                        lastAutoBackupStatus = null
                    )
                )
            }
        }
    }

    override fun observeTransactions(): Flow<List<TransactionRecord>> =
        txDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeSmsArchive(): Flow<List<SmsArchiveRecord>> =
        smsDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeWallets(): Flow<List<WalletSummary>> =
        combine(walletDao.observeAll(), openingDao.observeAll()) { wallets, openings ->
            val openingMap = openings.associateBy { it.network }
            wallets.map { wallet ->
                val opening = openingMap[wallet.network]?.amountMinor?.let { MoneyUtils.fromMinorUnits(it) }
                    ?: BigDecimal.ZERO
                wallet.toDomain(opening)
            }
        }

    override fun observeOpeningBalances(): Flow<List<OpeningBalanceRecord>> =
        openingDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeBusinessSettings(): Flow<BusinessSettings> =
        combine(settingsDao.observe(), formatDao.observeAll()) { settings, formats ->
            val enabled = formats
                .filter { it.isEnabled }
                .map { MobileMoneyNetwork.fromCode(it.network) }
                .filter { it != MobileMoneyNetwork.UNKNOWN }
                .toSet()
                .ifEmpty { MobileMoneyNetwork.supportedNetworks.toSet() }
            settings?.toDomain(enabled) ?: BusinessSettings(enabledNetworks = enabled)
        }

    override fun observeNetworkFormats(): Flow<List<NetworkFormatConfig>> =
        formatDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeAuditLogs(): Flow<List<AuditLogRecord>> =
        auditDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getTransactionById(id: Long): TransactionRecord? =
        txDao.getById(id)?.toDomain()

    override suspend fun getSmsArchiveById(id: Long): SmsArchiveRecord? =
        smsDao.getById(id)?.toDomain()

    override suspend fun ingestIncomingSms(
        sender: String,
        body: String,
        receivedAtEpochMs: Long,
        importSource: String,
        simSlot: Int?
    ): SmsIngestionOutcome {
        ensureSeeded()

        val settingsEntity = settingsDao.getOnce()
        if (importSource == "SMS_RECEIVER" && settingsEntity != null && settingsEntity.simSlotFilter in 1..2 && simSlot != null) {
            val oneBasedSlot = simSlot + 1
            if (oneBasedSlot != settingsEntity.simSlotFilter) {
                return SmsIngestionOutcome(
                    smsArchiveId = 0L,
                    transactionLocalId = null,
                    processingStatus = SmsProcessingStatus.IGNORED_NON_TRANSACTION,
                    verificationStatus = null,
                    message = "SMS kutoka SIM $oneBasedSlot imepuuzwa (kichujio ni SIM ${settingsEntity.simSlotFilter})."
                )
            }
        }

        val formatEntities = formatDao.getAllOnce()
        val customSenders = formatEntities.associate { fmt ->
            val net = MobileMoneyNetwork.fromCode(fmt.network)
            val ids = fmt.senderIdentifiersCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            net to ids
        }

        val decision = SmsPreFilter.evaluate(sender, body, customSenders)
        if (decision == PreFilterDecision.IGNORE_OTP) {
            return SmsIngestionOutcome(
                smsArchiveId = 0L,
                transactionLocalId = null,
                processingStatus = SmsProcessingStatus.IGNORED_OTP,
                verificationStatus = null,
                message = "Ujumbe wa OTP umepuuzwa kwa usalama."
            )
        }
        if (decision == PreFilterDecision.IGNORE_NON_TRANSACTION) {
            return SmsIngestionOutcome(
                smsArchiveId = 0L,
                transactionLocalId = null,
                processingStatus = SmsProcessingStatus.IGNORED_NON_TRANSACTION,
                verificationStatus = null,
                message = "Ujumbe wa kawaida umepuuzwa (sio muamala)."
            )
        }

        val parseResult = parser.parseSms(sender, body, receivedAtEpochMs)
        val parsedData = when (parseResult) {
            is SmsParseResult.Parsed -> parseResult.data
            is SmsParseResult.Partial -> parseResult.data
            is SmsParseResult.Unrecognized -> null
        }

        val existingTransactions = txDao.getAllOnce().map { it.toDomain() }
        val verification = verifier.verifyParsedSms(
            parseResult = parseResult,
            context = VerificationContext(
                senderIdentifier = sender,
                existingTransactions = existingTransactions,
                nowEpochMs = receivedAtEpochMs,
                customSendersByNetwork = customSenders
            )
        )

        val enabledMap = formatEntities.associateBy { it.network }
        val detectedNet = parsedData?.network
            ?: SmsPreFilter.identifyNetworkFromSender(sender, customSenders)
                .takeIf { it != MobileMoneyNetwork.UNKNOWN }
            ?: SmsPreFilter.inferNetworkFromContent(body)

        if (detectedNet != MobileMoneyNetwork.UNKNOWN &&
            enabledMap[detectedNet.code]?.isEnabled == false &&
            importSource == "SMS_RECEIVER"
        ) {
            return SmsIngestionOutcome(
                smsArchiveId = 0L,
                transactionLocalId = null,
                processingStatus = SmsProcessingStatus.IGNORED_NON_TRANSACTION,
                verificationStatus = null,
                message = "Mtandao wa ${detectedNet.shortLabel} umezimwa kwenye Mipangilio."
            )
        }

        val processingStatus = when {
            verification.isDuplicate -> SmsProcessingStatus.DUPLICATE
            parseResult is SmsParseResult.Unrecognized -> SmsProcessingStatus.FAILED_PARSE
            verification.status == VerificationStatus.NEEDS_REVIEW -> SmsProcessingStatus.NEEDS_REVIEW
            else -> SmsProcessingStatus.PARSED
        }

        return db.withTransaction {
            val smsId = smsDao.insert(
                SmsArchiveEntity(
                    senderIdentifier = sender,
                    originalContent = body,
                    network = detectedNet.code,
                    extractedTransactionId = parsedData?.transactionId,
                    extractedAmountMinor = parsedData?.amount?.let { MoneyUtils.toMinorUnits(it) },
                    receivedAtEpochMs = receivedAtEpochMs,
                    processingStatus = processingStatus.code,
                    parseErrorReason = verification.reasons.takeIf { it.isNotEmpty() }?.joinToString(" | "),
                    relatedTransactionLocalId = null,
                    importSource = importSource
                )
            )

            // If exact duplicate Transaction ID already exists, link SMS to original and avoid inserting duplicate row
            val extractedTxId = parsedData?.transactionId?.trim()
            if (verification.isDuplicate && !extractedTxId.isNullOrBlank()) {
                val existing = txDao.getByTransactionId(extractedTxId)
                if (existing != null) {
                    smsDao.linkTransaction(smsId, existing.id)
                    auditDao.insert(
                        AuditLogEntity(
                            entityType = "SMS_ARCHIVE",
                            entityId = smsId.toString(),
                            action = "DUPLICATE_SMS_BLOCKED",
                            summary = "SMS rudufu yenye kumbukumbu $extractedTxId ilizuiliwa kuhesabiwa mara mbili",
                            previousValue = null,
                            newValue = extractedTxId,
                            reason = verification.reasons.joinToString("; "),
                            timestampEpochMs = System.currentTimeMillis()
                        )
                    )
                    return@withTransaction SmsIngestionOutcome(
                        smsArchiveId = smsId,
                        transactionLocalId = existing.id,
                        processingStatus = SmsProcessingStatus.DUPLICATE,
                        verificationStatus = VerificationStatus.DUPLICATE,
                        message = "Nakala imegundulika ($extractedTxId) — imehifadhiwa kwenye kumbukumbu bila kuhesabiwa tena."
                    )
                }
            }

            // Build TransactionRecord (never invent missing fields!)
            val txIdForDb = extractedTxId?.takeIf { it.isNotBlank() }
                ?: "UNPARSED-${smsId}-${UUID.randomUUID().toString().take(6).uppercase()}"

            val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.US)
            val timeFormatter = SimpleDateFormat("HH:mm", Locale.US)
            val txTimestamp = parsedData?.parsedTimestampEpochMs ?: receivedAtEpochMs

            val newEntity = TransactionEntity(
                transactionId = txIdForDb,
                network = detectedNet.code,
                type = (parsedData?.transactionType ?: TransactionType.MANUAL_ADJUSTMENT).code,
                amountMinor = parsedData?.amount?.let { MoneyUtils.toMinorUnits(it) } ?: 0L,
                feeMinor = parsedData?.fee?.let { MoneyUtils.toMinorUnits(it) } ?: 0L,
                senderOrRecipientName = parsedData?.senderOrRecipientName ?: "Haijulikani",
                senderOrRecipientPhone = parsedData?.senderOrRecipientPhone,
                transactionDateText = parsedData?.transactionDateText ?: dateFormatter.format(Date(receivedAtEpochMs)),
                transactionTimeText = parsedData?.transactionTimeText ?: timeFormatter.format(Date(receivedAtEpochMs)),
                timestampEpochMs = txTimestamp,
                description = parsedData?.description ?: "SMS isiyokamilika — inahitaji uhakiki",
                providerReportedBalanceMinor = parsedData?.remainingBalance?.let { MoneyUtils.toMinorUnits(it) },
                verificationStatus = verification.status.code,
                verificationNotes = verification.reasons.joinToString("\n"),
                isManualEntry = false,
                isCreditAdjustment = (parsedData?.transactionType ?: TransactionType.MONEY_RECEIVED).isInflowByDefault,
                relatedTransactionId = parsedData?.relatedTransactionId,
                smsArchiveId = smsId,
                formatVersion = parsedData?.formatVersion ?: "UNRECOGNIZED",
                reconciliationNote = null,
                createdAtEpochMs = System.currentTimeMillis(),
                updatedAtEpochMs = System.currentTimeMillis()
            )

            val txLocalId = txDao.insert(newEntity)
            smsDao.linkTransaction(smsId, txLocalId)

            recalculateAllWalletsLocked()

            SmsIngestionOutcome(
                smsArchiveId = smsId,
                transactionLocalId = txLocalId,
                processingStatus = processingStatus,
                verificationStatus = verification.status,
                message = "SMS imehifadhiwa (${verification.status.labelSw})"
            )
        }
    }

    override suspend fun addManualTransaction(
        transaction: TransactionRecord,
        reason: String
    ): Result<Long> {
        ensureSeeded()
        val existing = txDao.getAllOnce().map { it.toDomain() }
        val normalizedId = transaction.transactionId.trim().ifBlank {
            "MAN-${System.currentTimeMillis()}"
        }
        val candidate = transaction.copy(transactionId = normalizedId, isManualEntry = true)
        val outcome = verifier.validateManualTransaction(candidate, existing)

        if (outcome.isDuplicate) {
            return Result.failure(IllegalStateException(outcome.reasons.firstOrNull() ?: "Muamala rudufu"))
        }
        if (candidate.amount <= BigDecimal.ZERO) {
            return Result.failure(IllegalArgumentException("Kiasi lazima kiwe kikubwa kuliko 0"))
        }

        val now = System.currentTimeMillis()
        val finalTx = candidate.copy(
            verificationStatus = outcome.status,
            verificationNotes = outcome.reasons.joinToString("\n"),
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )

        val insertedId = db.withTransaction {
            val id = txDao.insert(finalTx.toEntity())
            auditDao.insert(
                AuditLogEntity(
                    entityType = "TRANSACTION",
                    entityId = normalizedId,
                    action = "MANUAL_CREATE",
                    summary = "Muamala wa mkono ${finalTx.type.labelSw} (${MoneyUtils.formatCurrency(finalTx.amount)}) umeongezwa",
                    previousValue = null,
                    newValue = "${finalTx.network.code}:${finalTx.amount.toPlainString()}",
                    reason = reason.ifBlank { "Ingizo la mmiliki" },
                    timestampEpochMs = now
                )
            )
            recalculateAllWalletsLocked()
            id
        }
        return Result.success(insertedId)
    }

    override suspend fun updateTransactionDetails(
        updated: TransactionRecord,
        reason: String
    ): Result<Unit> {
        ensureSeeded()
        val existingEntity = txDao.getById(updated.id)
            ?: return Result.failure(IllegalArgumentException("Muamala haujapatikana"))
        if (updated.amount <= BigDecimal.ZERO) {
            return Result.failure(IllegalArgumentException("Kiasi lazima kiwe kikubwa kuliko 0"))
        }

        val now = System.currentTimeMillis()
        val existingDomain = existingEntity.toDomain()
        val saved = updated.copy(updatedAtEpochMs = now)

        db.withTransaction {
            txDao.update(saved.toEntity())
            auditDao.insert(
                AuditLogEntity(
                    entityType = "TRANSACTION",
                    entityId = saved.transactionId,
                    action = "MANUAL_EDIT",
                    summary = "Marekebisho ya muamala ${saved.transactionId}",
                    previousValue = "${existingDomain.network.code}|${existingDomain.type.code}|${existingDomain.amount}|${existingDomain.verificationStatus.code}",
                    newValue = "${saved.network.code}|${saved.type.code}|${saved.amount}|${saved.verificationStatus.code}",
                    reason = reason.ifBlank { "Marekebisho ya taarifa" },
                    timestampEpochMs = now
                )
            )
            recalculateAllWalletsLocked()
        }
        return Result.success(Unit)
    }

    override suspend fun linkReversalOrRefund(
        originalTransactionId: String,
        newType: TransactionType,
        amount: BigDecimal,
        network: MobileMoneyNetwork,
        reason: String
    ): Result<Long> {
        ensureSeeded()
        val all = txDao.getAllOnce().map { it.toDomain() }
        val original = all.firstOrNull { it.transactionId.equals(originalTransactionId, ignoreCase = true) }

        val check = txEngine.validateReversalOrRefundLink(original, newType, amount, all)
        if (check.isFailure) return Result.failure(check.exceptionOrNull()!!)

        val now = System.currentTimeMillis()
        val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.US)
        val timeFmt = SimpleDateFormat("HH:mm", Locale.US)
        val prefix = if (newType == TransactionType.REVERSAL) "REV" else "REF"
        val newTxId = "$prefix-${original!!.transactionId}-${now.toString().takeLast(4)}"

        val linkTx = TransactionRecord(
            transactionId = newTxId,
            network = network,
            type = newType,
            amount = MoneyUtils.scale(amount),
            fee = BigDecimal.ZERO,
            senderOrRecipientName = original.senderOrRecipientName,
            senderOrRecipientPhone = original.senderOrRecipientPhone,
            transactionDateText = dateFmt.format(Date(now)),
            transactionTimeText = timeFmt.format(Date(now)),
            timestampEpochMs = now,
            description = "${newType.labelSw} ya muamala ${original.transactionId}",
            providerReportedBalance = null,
            verificationStatus = if (newType == TransactionType.REVERSAL) VerificationStatus.REVERSED else VerificationStatus.REFUNDED,
            verificationNotes = reason.ifBlank { "Imerekodiwa na mmiliki" },
            isManualEntry = true,
            isCreditAdjustment = newType == TransactionType.REFUND,
            relatedTransactionId = original.transactionId,
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )

        val insertedId = db.withTransaction {
            val id = txDao.insert(linkTx.toEntity())
            if (newType == TransactionType.REVERSAL) {
                txDao.update(
                    original.copy(
                        verificationStatus = VerificationStatus.REVERSED,
                        updatedAtEpochMs = now
                    ).toEntity()
                )
            } else if (newType == TransactionType.REFUND) {
                txDao.update(
                    original.copy(
                        verificationStatus = VerificationStatus.REFUNDED,
                        updatedAtEpochMs = now
                    ).toEntity()
                )
            }
            auditDao.insert(
                AuditLogEntity(
                    entityType = "TRANSACTION",
                    entityId = original.transactionId,
                    action = if (newType == TransactionType.REVERSAL) "LINK_REVERSAL" else "LINK_REFUND",
                    summary = "${newType.labelSw} (${MoneyUtils.formatCurrency(amount)}) kwa ${original.transactionId}",
                    previousValue = original.verificationStatus.code,
                    newValue = newTxId,
                    reason = reason.ifBlank { "Uhusiano wa muamala" },
                    timestampEpochMs = now
                )
            )
            recalculateAllWalletsLocked()
            id
        }
        return Result.success(insertedId)
    }

    override suspend fun setOpeningBalance(
        network: MobileMoneyNetwork,
        amount: BigDecimal,
        note: String
    ): Result<Unit> {
        ensureSeeded()
        if (amount < BigDecimal.ZERO) {
            return Result.failure(IllegalArgumentException("Salio la awali haliwezi kuwa hasi"))
        }
        val now = System.currentTimeMillis()
        db.withTransaction {
            val prev = openingDao.getByNetwork(network.code)
            val updatedEntity = OpeningBalanceEntity(
                id = prev?.id ?: 0L,
                network = network.code,
                amountMinor = MoneyUtils.toMinorUnits(amount),
                effectiveDateEpochMs = prev?.effectiveDateEpochMs ?: now,
                note = note.ifBlank { "Salio la awali" },
                updatedAtEpochMs = now
            )
            openingDao.upsert(updatedEntity)
            auditDao.insert(
                AuditLogEntity(
                    entityType = "OPENING_BALANCE",
                    entityId = network.code,
                    action = "SET_OPENING_BALANCE",
                    summary = "Salio la awali la ${network.shortLabel} limewekwa ${MoneyUtils.formatCurrency(amount)}",
                    previousValue = prev?.amountMinor?.let { MoneyUtils.fromMinorUnits(it).toPlainString() },
                    newValue = amount.toPlainString(),
                    reason = note.ifBlank { "Mpangilio wa salio la awali" },
                    timestampEpochMs = now
                )
            )
            recalculateAllWalletsLocked()
        }
        return Result.success(Unit)
    }

    override suspend fun reconcileWallet(
        network: MobileMoneyNetwork,
        providerReportedBalance: BigDecimal,
        adjustCalculatedBalance: Boolean,
        reason: String
    ): Result<Unit> {
        ensureSeeded()
        val now = System.currentTimeMillis()
        db.withTransaction {
            val wallet = walletDao.getByNetwork(network.code)
            val calculatedBefore = wallet?.calculatedBalanceMinor?.let { MoneyUtils.fromMinorUnits(it) }
                ?: BigDecimal.ZERO
            val diff = MoneyUtils.scale(providerReportedBalance).subtract(calculatedBefore)

            if (adjustCalculatedBalance && diff.compareTo(BigDecimal.ZERO) != 0) {
                val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.US)
                val timeFmt = SimpleDateFormat("HH:mm", Locale.US)
                val isCredit = diff > BigDecimal.ZERO
                val adjTx = TransactionRecord(
                    transactionId = "REC-${network.code}-${now.toString().takeLast(5)}",
                    network = network,
                    type = TransactionType.MANUAL_ADJUSTMENT,
                    amount = diff.abs(),
                    fee = BigDecimal.ZERO,
                    senderOrRecipientName = "Usawazishaji wa Salio",
                    senderOrRecipientPhone = null,
                    transactionDateText = dateFmt.format(Date(now)),
                    transactionTimeText = timeFmt.format(Date(now)),
                    timestampEpochMs = now,
                    description = "Usawazishaji kati ya salio la hesabu na salio la mtandao",
                    providerReportedBalance = MoneyUtils.scale(providerReportedBalance),
                    verificationStatus = VerificationStatus.FORMAT_VALIDATED,
                    verificationNotes = reason.ifBlank { "Usawazishaji wa mkono" },
                    isManualEntry = true,
                    isCreditAdjustment = isCredit,
                    reconciliationNote = reason,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now
                )
                txDao.insert(adjTx.toEntity())
            }

            val existingWallet = walletDao.getByNetwork(network.code)
            if (existingWallet != null) {
                walletDao.upsert(
                    existingWallet.copy(
                        providerReportedBalanceMinor = MoneyUtils.toMinorUnits(providerReportedBalance),
                        providerBalanceUpdatedAtEpochMs = now,
                        lastReconciledAtEpochMs = now,
                        updatedAtEpochMs = now
                    )
                )
            }

            auditDao.insert(
                AuditLogEntity(
                    entityType = "WALLET",
                    entityId = network.code,
                    action = "RECONCILE_BALANCE",
                    summary = "Usawazishaji wa ${network.shortLabel}: Mtandao ${MoneyUtils.formatCurrency(providerReportedBalance)} vs Hesabu ${MoneyUtils.formatCurrency(calculatedBefore)}",
                    previousValue = calculatedBefore.toPlainString(),
                    newValue = providerReportedBalance.toPlainString(),
                    reason = reason.ifBlank { "Usawazishaji wa salio" },
                    timestampEpochMs = now
                )
            )
            recalculateAllWalletsLocked()
        }
        return Result.success(Unit)
    }

    override suspend fun updateBusinessSettings(transform: (BusinessSettings) -> BusinessSettings) {
        ensureSeeded()
        val currentEntity = settingsDao.getOnce() ?: return
        val formats = formatDao.getAllOnce()
        val enabled = formats.filter { it.isEnabled }
            .map { MobileMoneyNetwork.fromCode(it.network) }
            .toSet()
        val updated = transform(currentEntity.toDomain(enabled))
        settingsDao.upsert(
            currentEntity.copy(
                businessName = updated.businessName,
                primaryLanguage = updated.primaryLanguage.code,
                currencyCode = updated.currencyCode,
                hideBalancesOnHome = updated.hideBalancesOnHome,
                pinEnabled = updated.pinEnabled,
                biometricEnabled = updated.biometricEnabled,
                autoBackupEnabled = updated.autoBackupEnabled,
                lastBackupEpochMs = updated.lastBackupEpochMs,
                onboardingCompleted = updated.onboardingCompleted,
                smsNotificationsEnabled = updated.smsNotificationsEnabled,
                simSlotFilter = updated.simSlotFilter
            )
        )
    }

    override suspend fun toggleNetworkEnabled(network: MobileMoneyNetwork, enabled: Boolean) {
        ensureSeeded()
        val now = System.currentTimeMillis()
        formatDao.setEnabled(network.code, enabled, now)
        auditDao.insert(
            AuditLogEntity(
                entityType = "NETWORK_FORMAT",
                entityId = network.code,
                action = if (enabled) "ENABLE_NETWORK" else "DISABLE_NETWORK",
                summary = "${network.shortLabel} ${if (enabled) "imewashwa" else "imezimwa"}",
                previousValue = (!enabled).toString(),
                newValue = enabled.toString(),
                reason = "Mpangilio wa mtumiaji",
                timestampEpochMs = now
            )
        )
    }

    override suspend fun updateNetworkSenders(network: MobileMoneyNetwork, senderIdsCsv: String): Result<Unit> {
        ensureSeeded()
        val cleaned = senderIdsCsv
            .split(",")
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(",")
        if (cleaned.isEmpty()) {
            return Result.failure(IllegalArgumentException("Weka angalau kitambulisho kimoja cha mtumaji"))
        }
        val now = System.currentTimeMillis()
        formatDao.updateSenderIdentifiers(network.code, cleaned, now)
        auditDao.insert(
            AuditLogEntity(
                entityType = "NETWORK_FORMAT",
                entityId = network.code,
                action = "UPDATE_SENDER_IDS",
                summary = "Vitambulisho vya ${network.shortLabel} vimeboreshwa: $cleaned",
                previousValue = null,
                newValue = cleaned,
                reason = "Mpangilio wa watumaji wa SMS",
                timestampEpochMs = now
            )
        )
        return Result.success(Unit)
    }

    override suspend fun recalculateAllWallets() {
        ensureSeeded()
        db.withTransaction {
            recalculateAllWalletsLocked()
        }
    }

    private suspend fun recalculateAllWalletsLocked() {
        val now = System.currentTimeMillis()
        val allTxs = txDao.getAllOnce().map { it.toDomain() }
        val openings = openingDao.getAllOnce().associateBy { it.network }
        val existingWallets = walletDao.getAllOnce().associateBy { it.network }

        MobileMoneyNetwork.supportedNetworks.forEach { net ->
            val opening = openings[net.code]?.toDomain()
            val existing = existingWallets[net.code]
            val summary = txEngine.calculateWalletSummary(
                network = net,
                openingBalance = opening,
                allTransactions = allTxs,
                lastReconciledAtEpochMs = existing?.lastReconciledAtEpochMs
            )
            // Preserve manually reconciled provider balance if no newer SMS balance exists
            val preservedProviderBalance = summary.providerReportedBalance
                ?: existing?.providerReportedBalanceMinor?.let { MoneyUtils.fromMinorUnits(it) }
            val preservedProviderTs = summary.providerBalanceUpdatedAtEpochMs
                ?: existing?.providerBalanceUpdatedAtEpochMs

            walletDao.upsert(
                summary.copy(
                    providerReportedBalance = preservedProviderBalance,
                    providerBalanceUpdatedAtEpochMs = preservedProviderTs
                ).toEntity(now)
            )
        }
    }

    override suspend fun deleteArchivedSmsRecords(): Int {
        ensureSeeded()
        val deleted = db.withTransaction {
            val count = smsDao.deleteAll()
            auditDao.insert(
                AuditLogEntity(
                    entityType = "SMS_ARCHIVE",
                    entityId = "ALL",
                    action = "DELETE_SMS_ARCHIVE",
                    summary = "Kumbukumbu za SMS ($count) zimefutwa na mtumiaji",
                    previousValue = count.toString(),
                    newValue = "0",
                    reason = "Ombi la kufuta kumbukumbu za SMS",
                    timestampEpochMs = System.currentTimeMillis()
                )
            )
            count
        }
        return deleted
    }

    override suspend fun deleteAllLocalData(): Result<Unit> {
        return runCatching {
            db.withTransaction {
                txDao.deleteAll()
                smsDao.deleteAll()
                openingDao.deleteAll()
                walletDao.deleteAll()
                auditDao.deleteAll()
                formatDao.deleteAll()
            }
            ensureSeeded()
        }
    }

    override suspend fun exportBackupJson(): String {
        ensureSeeded()
        val root = JSONObject()
        root.put("schemaVersion", 2)
        root.put("exportedAtEpochMs", System.currentTimeMillis())

        settingsDao.getOnce()?.let { s ->
            root.put("businessSettings", JSONObject().apply {
                put("businessName", s.businessName)
                put("primaryLanguage", s.primaryLanguage)
                put("currencyCode", s.currencyCode)
                put("hideBalancesOnHome", s.hideBalancesOnHome)
                put("autoBackupEnabled", s.autoBackupEnabled)
                put("onboardingCompleted", s.onboardingCompleted)
            })
        }

        val openingsArray = JSONArray()
        openingDao.getAllOnce().forEach { o ->
            openingsArray.put(JSONObject().apply {
                put("network", o.network)
                put("amountMinor", o.amountMinor)
                put("effectiveDateEpochMs", o.effectiveDateEpochMs)
                put("note", o.note)
                put("updatedAtEpochMs", o.updatedAtEpochMs)
            })
        }
        root.put("openingBalances", openingsArray)

        val smsArray = JSONArray()
        smsDao.getAllOnce().forEach { sms ->
            smsArray.put(JSONObject().apply {
                put("id", sms.id)
                put("senderIdentifier", sms.senderIdentifier)
                put("originalContent", sms.originalContent)
                put("network", sms.network)
                put("extractedTransactionId", sms.extractedTransactionId)
                put("extractedAmountMinor", sms.extractedAmountMinor)
                put("receivedAtEpochMs", sms.receivedAtEpochMs)
                put("processingStatus", sms.processingStatus)
                put("parseErrorReason", sms.parseErrorReason)
                put("relatedTransactionLocalId", sms.relatedTransactionLocalId)
                put("importSource", sms.importSource)
            })
        }
        root.put("smsArchive", smsArray)

        val txArray = JSONArray()
        txDao.getAllOnce().forEach { tx ->
            txArray.put(JSONObject().apply {
                put("id", tx.id)
                put("transactionId", tx.transactionId)
                put("network", tx.network)
                put("type", tx.type)
                put("amountMinor", tx.amountMinor)
                put("feeMinor", tx.feeMinor)
                put("senderOrRecipientName", tx.senderOrRecipientName)
                put("senderOrRecipientPhone", tx.senderOrRecipientPhone)
                put("transactionDateText", tx.transactionDateText)
                put("transactionTimeText", tx.transactionTimeText)
                put("timestampEpochMs", tx.timestampEpochMs)
                put("description", tx.description)
                put("providerReportedBalanceMinor", tx.providerReportedBalanceMinor)
                put("verificationStatus", tx.verificationStatus)
                put("verificationNotes", tx.verificationNotes)
                put("isManualEntry", tx.isManualEntry)
                put("isCreditAdjustment", tx.isCreditAdjustment)
                put("relatedTransactionId", tx.relatedTransactionId)
                put("smsArchiveId", tx.smsArchiveId)
                put("formatVersion", tx.formatVersion)
                put("reconciliationNote", tx.reconciliationNote)
                put("createdAtEpochMs", tx.createdAtEpochMs)
                put("updatedAtEpochMs", tx.updatedAtEpochMs)
            })
        }
        root.put("transactions", txArray)

        val auditArray = JSONArray()
        auditDao.getAllOnce().forEach { a ->
            auditArray.put(JSONObject().apply {
                put("entityType", a.entityType)
                put("entityId", a.entityId)
                put("action", a.action)
                put("summary", a.summary)
                put("previousValue", a.previousValue)
                put("newValue", a.newValue)
                put("reason", a.reason)
                put("timestampEpochMs", a.timestampEpochMs)
            })
        }
        root.put("auditLogs", auditArray)

        return root.toString()
    }

    override suspend fun restoreBackupJson(jsonPayload: String): Result<Unit> = runCatching {
        val root = JSONObject(jsonPayload)
        require(root.has("transactions") && root.has("openingBalances")) {
            "Muundo wa chelezo haujatambuliwa"
        }

        ensureSeeded()
        val now = System.currentTimeMillis()
        db.withTransaction {
            txDao.deleteAll()
            smsDao.deleteAll()
            openingDao.deleteAll()
            auditDao.deleteAll()

            val settingsObj = root.optJSONObject("businessSettings")
            val currentSettings = settingsDao.getOnce()
            if (settingsObj != null && currentSettings != null) {
                settingsDao.upsert(
                    currentSettings.copy(
                        businessName = settingsObj.optString("businessName", currentSettings.businessName),
                        primaryLanguage = settingsObj.optString("primaryLanguage", currentSettings.primaryLanguage),
                        currencyCode = settingsObj.optString("currencyCode", currentSettings.currencyCode),
                        hideBalancesOnHome = settingsObj.optBoolean("hideBalancesOnHome", false),
                        autoBackupEnabled = settingsObj.optBoolean("autoBackupEnabled", true),
                        lastBackupEpochMs = now,
                        onboardingCompleted = settingsObj.optBoolean("onboardingCompleted", true)
                    )
                )
            }

            val openingsArr = root.optJSONArray("openingBalances") ?: JSONArray()
            for (i in 0 until openingsArr.length()) {
                val o = openingsArr.getJSONObject(i)
                openingDao.upsert(
                    OpeningBalanceEntity(
                        network = o.getString("network"),
                        amountMinor = o.getLong("amountMinor"),
                        effectiveDateEpochMs = o.getLong("effectiveDateEpochMs"),
                        note = o.optString("note", "Salio la awali"),
                        updatedAtEpochMs = o.optLong("updatedAtEpochMs", now)
                    )
                )
            }

            val smsArr = root.optJSONArray("smsArchive") ?: JSONArray()
            val smsList = mutableListOf<SmsArchiveEntity>()
            for (i in 0 until smsArr.length()) {
                val s = smsArr.getJSONObject(i)
                smsList.add(
                    SmsArchiveEntity(
                        id = s.getLong("id"),
                        senderIdentifier = s.getString("senderIdentifier"),
                        originalContent = s.getString("originalContent"),
                        network = s.getString("network"),
                        extractedTransactionId = s.optString("extractedTransactionId").takeIf { it.isNotEmpty() && it != "null" },
                        extractedAmountMinor = if (s.isNull("extractedAmountMinor")) null else s.getLong("extractedAmountMinor"),
                        receivedAtEpochMs = s.getLong("receivedAtEpochMs"),
                        processingStatus = s.getString("processingStatus"),
                        parseErrorReason = s.optString("parseErrorReason").takeIf { it.isNotEmpty() && it != "null" },
                        relatedTransactionLocalId = if (s.isNull("relatedTransactionLocalId")) null else s.getLong("relatedTransactionLocalId"),
                        importSource = s.optString("importSource", "BACKUP_RESTORE")
                    )
                )
            }
            if (smsList.isNotEmpty()) smsDao.insertAll(smsList)

            val txArr = root.optJSONArray("transactions") ?: JSONArray()
            val txList = mutableListOf<TransactionEntity>()
            for (i in 0 until txArr.length()) {
                val t = txArr.getJSONObject(i)
                txList.add(
                    TransactionEntity(
                        id = t.getLong("id"),
                        transactionId = t.getString("transactionId"),
                        network = t.getString("network"),
                        type = t.getString("type"),
                        amountMinor = t.getLong("amountMinor"),
                        feeMinor = t.optLong("feeMinor", 0L),
                        senderOrRecipientName = t.optString("senderOrRecipientName", "Haijulikani"),
                        senderOrRecipientPhone = t.optString("senderOrRecipientPhone").takeIf { it.isNotEmpty() && it != "null" },
                        transactionDateText = t.optString("transactionDateText", ""),
                        transactionTimeText = t.optString("transactionTimeText", ""),
                        timestampEpochMs = t.getLong("timestampEpochMs"),
                        description = t.optString("description", ""),
                        providerReportedBalanceMinor = if (t.isNull("providerReportedBalanceMinor")) null else t.getLong("providerReportedBalanceMinor"),
                        verificationStatus = t.getString("verificationStatus"),
                        verificationNotes = t.optString("verificationNotes", ""),
                        isManualEntry = t.optBoolean("isManualEntry", false),
                        isCreditAdjustment = t.optBoolean("isCreditAdjustment", true),
                        relatedTransactionId = t.optString("relatedTransactionId").takeIf { it.isNotEmpty() && it != "null" },
                        smsArchiveId = if (t.isNull("smsArchiveId")) null else t.getLong("smsArchiveId"),
                        formatVersion = t.optString("formatVersion").takeIf { it.isNotEmpty() && it != "null" },
                        reconciliationNote = t.optString("reconciliationNote").takeIf { it.isNotEmpty() && it != "null" },
                        createdAtEpochMs = t.optLong("createdAtEpochMs", now),
                        updatedAtEpochMs = t.optLong("updatedAtEpochMs", now)
                    )
                )
            }
            if (txList.isNotEmpty()) txDao.insertAllReplace(txList)

            val auditArr = root.optJSONArray("auditLogs") ?: JSONArray()
            val auditList = mutableListOf<AuditLogEntity>()
            for (i in 0 until auditArr.length()) {
                val a = auditArr.getJSONObject(i)
                auditList.add(
                    AuditLogEntity(
                        entityType = a.getString("entityType"),
                        entityId = a.getString("entityId"),
                        action = a.getString("action"),
                        summary = a.getString("summary"),
                        previousValue = a.optString("previousValue").takeIf { it.isNotEmpty() && it != "null" },
                        newValue = a.optString("newValue").takeIf { it.isNotEmpty() && it != "null" },
                        reason = a.optString("reason").takeIf { it.isNotEmpty() && it != "null" },
                        timestampEpochMs = a.getLong("timestampEpochMs")
                    )
                )
            }
            if (auditList.isNotEmpty()) auditDao.insertAll(auditList)

            auditDao.insert(
                AuditLogEntity(
                    entityType = "DATABASE",
                    entityId = "RESTORE",
                    action = "BACKUP_RESTORE",
                    summary = "Data imerejeshwa kutoka faili la chelezo (${txList.size} miamala)",
                    previousValue = null,
                    newValue = txList.size.toString(),
                    reason = "Urejesho wa mtumiaji",
                    timestampEpochMs = now
                )
            )
            recalculateAllWalletsLocked()
        }
    }
}

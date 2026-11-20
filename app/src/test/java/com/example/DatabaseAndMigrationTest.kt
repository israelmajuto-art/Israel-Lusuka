package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.MauzoDatabase
import com.example.data.MauzoRepositoryImpl
import com.example.domain.MobileMoneyNetwork
import com.example.domain.SmsProcessingStatus
import com.example.domain.TransactionType
import com.example.domain.VerificationStatus
import com.example.security.BackupCrypto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DatabaseAndMigrationTest {

    private lateinit var context: Context
    private lateinit var db: MauzoDatabase
    private lateinit var repository: MauzoRepositoryImpl

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = MauzoDatabase.createInMemoryForTest(context)
        repository = MauzoRepositoryImpl(db)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `ingesting sms persists transaction, prevents duplicate, and updates wallet balance`() = runBlocking {
        repository.setOpeningBalance(MobileMoneyNetwork.MPESA, BigDecimal("50000.00"), "Start")

        val sms = "KLM88XYZ12 Confirmed. You have received Tsh25,000.00 from HALIMA SAID 0754112233 on 20/11/2026 at 11:00 AM. New M-Pesa balance is Tsh75,000.00."
        val firstOutcome = repository.ingestIncomingSms("MPESA", sms, 1_795_172_400_000L)
        assertEquals(SmsProcessingStatus.PARSED, firstOutcome.processingStatus)
        assertEquals(VerificationStatus.FORMAT_VALIDATED, firstOutcome.verificationStatus)

        // Duplicate same SMS should be blocked from double-counting
        val dupOutcome = repository.ingestIncomingSms("MPESA", sms, 1_795_172_405_000L)
        assertEquals(SmsProcessingStatus.DUPLICATE, dupOutcome.processingStatus)

        val wallets = repository.observeWallets().first()
        val mpesaWallet = wallets.first { it.network == MobileMoneyNetwork.MPESA }
        assertEquals(BigDecimal("75000.00"), mpesaWallet.calculatedBalance)
        assertEquals(1, repository.observeTransactions().first().size)
        assertEquals(2, repository.observeSmsArchive().first().size)
    }

    @Test
    fun `encrypted backup and restore preserves transactions and balances`() = runBlocking {
        repository.setOpeningBalance(MobileMoneyNetwork.AIRTEL_MONEY, BigDecimal("10000.00"), "Initial")
        val sms = "Muamala umekamilika. Umepokea TZS 40,000 kutoka kwa BAKARI MUSA (0784112233). Kumbukumbu: AM261120.1111.A00001. Ada: TZS 0. Salio lako ni TZS 50,000. Tarehe: 20/11/2026 11:11."
        repository.ingestIncomingSms("AIRTELMONEY", sms, 1_795_173_000_000L)

        val json = repository.exportBackupJson()
        val encrypted = BackupCrypto.encryptBackup(json, "siri1234")
        repository.deleteAllLocalData().getOrThrow()
        assertEquals(0, repository.observeTransactions().first().size)

        val decrypted = BackupCrypto.decryptBackup(encrypted, "siri1234")
        repository.restoreBackupJson(decrypted).getOrThrow()

        val restoredTxs = repository.observeTransactions().first()
        assertEquals(1, restoredTxs.size)
        assertEquals("AM261120.1111.A00001", restoredTxs.first().transactionId)
        val airtelWallet = repository.observeWallets().first().first { it.network == MobileMoneyNetwork.AIRTEL_MONEY }
        assertEquals(BigDecimal("50000.00"), airtelWallet.calculatedBalance)
    }

    @Test
    fun `database migration 1 to 2 preserves existing data and adds new columns`() {
        val dbName = "migration_test_v1.db"
        context.deleteDatabase(dbName)

        // Create v1 schema directly
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS sms_archive (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        senderIdentifier TEXT NOT NULL,
                        originalContent TEXT NOT NULL,
                        network TEXT NOT NULL,
                        extractedTransactionId TEXT,
                        extractedAmountMinor INTEGER,
                        receivedAtEpochMs INTEGER NOT NULL,
                        processingStatus TEXT NOT NULL,
                        parseErrorReason TEXT,
                        relatedTransactionLocalId INTEGER,
                        importSource TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS transactions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        transactionId TEXT NOT NULL,
                        network TEXT NOT NULL,
                        type TEXT NOT NULL,
                        amountMinor INTEGER NOT NULL,
                        feeMinor INTEGER NOT NULL,
                        senderOrRecipientName TEXT NOT NULL,
                        senderOrRecipientPhone TEXT,
                        transactionDateText TEXT NOT NULL,
                        transactionTimeText TEXT NOT NULL,
                        timestampEpochMs INTEGER NOT NULL,
                        description TEXT NOT NULL,
                        providerReportedBalanceMinor INTEGER,
                        verificationStatus TEXT NOT NULL,
                        verificationNotes TEXT NOT NULL,
                        isManualEntry INTEGER NOT NULL,
                        isCreditAdjustment INTEGER NOT NULL,
                        relatedTransactionId TEXT,
                        smsArchiveId INTEGER,
                        formatVersion TEXT,
                        createdAtEpochMs INTEGER NOT NULL,
                        updatedAtEpochMs INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS business_settings (
                        id INTEGER NOT NULL PRIMARY KEY,
                        businessName TEXT NOT NULL,
                        primaryLanguage TEXT NOT NULL,
                        currencyCode TEXT NOT NULL,
                        hideBalancesOnHome INTEGER NOT NULL,
                        pinEnabled INTEGER NOT NULL,
                        biometricEnabled INTEGER NOT NULL,
                        autoBackupEnabled INTEGER NOT NULL,
                        lastBackupEpochMs INTEGER,
                        onboardingCompleted INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("INSERT INTO business_settings VALUES (1, 'Duka La Juma', 'sw', 'TZS', 0, 0, 0, 1, NULL, 1)")
                db.execSQL("INSERT INTO transactions VALUES (1, 'MIG001', 'MPESA', 'MONEY_RECEIVED', 1500000, 0, 'Asha', '+255754000111', '20/11/2026', '10:00', 1700000000000, 'Malipo', NULL, 'FORMAT_VALIDATED', '', 0, 1, NULL, NULL, 'V1', 1000, 1000)")
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(callback)
                .build()
        )
        val v1Db = helper.writableDatabase
        MauzoDatabase.MIGRATION_1_2.migrate(v1Db)
        MauzoDatabase.MIGRATION_2_3.migrate(v1Db)

        val cursor = v1Db.query("SELECT transactionId, reconciliationNote FROM transactions WHERE transactionId = 'MIG001'")
        assertTrue(cursor.moveToFirst())
        assertEquals("MIG001", cursor.getString(0))
        assertTrue(cursor.isNull(1))
        cursor.close()

        val settingsCursor = v1Db.query("SELECT businessName, lastAutoBackupStatus, smsNotificationsEnabled, simSlotFilter FROM business_settings WHERE id = 1")
        assertTrue(settingsCursor.moveToFirst())
        assertEquals("Duka La Juma", settingsCursor.getString(0))
        assertEquals(1, settingsCursor.getInt(2))
        assertEquals(0, settingsCursor.getInt(3))
        settingsCursor.close()
        v1Db.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `custom sender ids and dual sim slot filtering work as configured`() = runBlocking {
        repository.updateNetworkSenders(MobileMoneyNetwork.MPESA, "MPESA,DUKAMPESA,889900").getOrThrow()
        repository.updateBusinessSettings { it.copy(simSlotFilter = 1) } // SIM 1 only

        // SMS on SIM 2 (index 1 -> oneBasedSlot 2) should be ignored when filter = 1
        val ignoredSim2 = repository.ingestIncomingSms(
            sender = "DUKAMPESA",
            body = "DUK99123 Confirmed. You have received Tsh15,000.00 from JOHN DOE 0754000111 on 20/11/2026 at 12:00 PM.",
            receivedAtEpochMs = 1_795_176_000_000L,
            importSource = "SMS_RECEIVER",
            simSlot = 1
        )
        assertEquals(SmsProcessingStatus.IGNORED_NON_TRANSACTION, ignoredSim2.processingStatus)

        // SMS from custom sender ID on SIM 1 (index 0 -> oneBasedSlot 1) should be parsed & format validated
        val acceptedSim1 = repository.ingestIncomingSms(
            sender = "DUKAMPESA",
            body = "DUK99123 Confirmed. You have received Tsh15,000.00 from JOHN DOE 0754000111 on 20/11/2026 at 12:00 PM.",
            receivedAtEpochMs = 1_795_176_000_000L,
            importSource = "SMS_RECEIVER",
            simSlot = 0
        )
        assertEquals(SmsProcessingStatus.PARSED, acceptedSim1.processingStatus)
        assertEquals(VerificationStatus.FORMAT_VALIDATED, acceptedSim1.verificationStatus)
    }
}

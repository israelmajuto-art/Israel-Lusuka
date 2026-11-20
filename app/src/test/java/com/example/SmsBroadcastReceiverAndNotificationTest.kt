package com.example

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.domain.AppLanguage
import com.example.domain.BusinessSettings
import com.example.domain.MobileMoneyNetwork
import com.example.domain.NetworkFormatConfig
import com.example.domain.SmsIngestionOutcome
import com.example.domain.SmsProcessingStatus
import com.example.domain.TransactionRecord
import com.example.domain.TransactionType
import com.example.domain.VerificationStatus
import com.example.sms_reader.ProcessSmsWorker
import com.example.sms_reader.SenderDirectoryCache
import com.example.sms_reader.SmsBroadcastReceiver
import com.example.sms_reader.TransactionNotificationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SmsBroadcastReceiverAndNotificationTest {

    private lateinit var app: Application

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(app, config)
    }

    @Test
    fun `broadcast receiver enqueues mobile money sms with sim slot and ignores otp`() {
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.RECEIVE_SMS)
        SenderDirectoryCache.save(
            app,
            listOf(
                NetworkFormatConfig(
                    network = MobileMoneyNetwork.MPESA,
                    displayName = "Vodacom M-Pesa",
                    senderIdentifiersCsv = "MPESA,CUSTOMPAY",
                    formatVersion = "MPESA_TZ_V2",
                    isEnabled = true
                )
            )
        )

        val receiver = SmsBroadcastReceiver()

        // 1. Valid custom-sender M-Pesa SMS on SIM 1 (slot 0)
        val pduTx = buildGsm7BitDeliverPdu(
            sender = "CUSTOMPAY",
            body = "TX998877 Confirmed. You have received Tsh25,000.00 from HALIMA SAID 0754112233 on 20/11/2026 at 11:00 AM."
        )
        val txIntent = Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION).apply {
            putExtra("pdus", arrayOf(pduTx))
            putExtra("format", "3gpp")
            putExtra("android.telephony.extra.SLOT_INDEX", 0)
        }
        receiver.onReceive(app, txIntent)

        // 2. OTP SMS should be discarded immediately and NOT enqueued
        val pduOtp = buildGsm7BitDeliverPdu(
            sender = "CUSTOMPAY",
            body = "Your verification code OTP is 492811. Do not share this code with anyone."
        )
        val otpIntent = Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION).apply {
            putExtra("pdus", arrayOf(pduOtp))
            putExtra("format", "3gpp")
            putExtra("android.telephony.extra.SLOT_INDEX", 0)
        }
        receiver.onReceive(app, otpIntent)

        val wm = WorkManager.getInstance(app)
        val enqueued = wm.getWorkInfos(
            WorkQuery.fromStates(listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.SUCCEEDED))
        ).get()

        assertEquals(1, enqueued.size)
        val info = enqueued.first()
        assertEquals(1, info.tags.size)
        // Confirm worker class tag matches ProcessSmsWorker
        assertTrue(info.tags.any { it.contains(ProcessSmsWorker::class.java.name) })
    }

    @Test
    fun `broadcast receiver ignores incoming sms when RECEIVE_SMS permission is denied`() {
        Shadows.shadowOf(app).denyPermissions(Manifest.permission.RECEIVE_SMS)
        val receiver = SmsBroadcastReceiver()
        val pduTx = buildGsm7BitDeliverPdu(
            sender = "M-PESA",
            body = "TX887766 Confirmed. You have received Tsh15,000.00 from JOHN DOE 0754001122 on 20/11/2026 at 12:00 PM."
        )
        val txIntent = Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION).apply {
            putExtra("pdus", arrayOf(pduTx))
            putExtra("format", "3gpp")
        }
        receiver.onReceive(app, txIntent)

        val wm = WorkManager.getInstance(app)
        val enqueued = wm.getWorkInfos(
            WorkQuery.fromStates(listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.SUCCEEDED))
        ).get()
        assertEquals(0, enqueued.size)
    }

    @Test
    fun `notification helper respects privacy mode and POST_NOTIFICATIONS permission`() {
        val shadowApp = Shadows.shadowOf(app)
        val notificationManager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNm = Shadows.shadowOf(notificationManager)
        notificationManager.cancelAll()

        val tx = TransactionRecord(
            id = 10L,
            transactionId = "KLM88XYZ12",
            network = MobileMoneyNetwork.MPESA,
            type = TransactionType.MONEY_RECEIVED,
            amount = BigDecimal("25000.00"),
            fee = BigDecimal.ZERO,
            senderOrRecipientName = "HALIMA SAID",
            senderOrRecipientPhone = "0754112233",
            transactionDateText = "20/11/2026",
            transactionTimeText = "11:00",
            timestampEpochMs = 1_795_172_400_000L,
            description = "Umepokea fedha",
            providerReportedBalance = BigDecimal("75000.00"),
            verificationStatus = VerificationStatus.FORMAT_VALIDATED,
            verificationNotes = ""
        )
        val outcome = SmsIngestionOutcome(
            smsArchiveId = 101L,
            transactionLocalId = 10L,
            processingStatus = SmsProcessingStatus.PARSED,
            verificationStatus = VerificationStatus.FORMAT_VALIDATED,
            message = "OK"
        )

        // Denied permission -> no notification posted
        shadowApp.denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        TransactionNotificationHelper.notifyTransactionDetected(
            context = app,
            outcome = outcome,
            transaction = tx,
            settings = BusinessSettings(smsNotificationsEnabled = true)
        )
        assertEquals(0, shadowNm.allNotifications.size)

        // Granted + PIN enabled -> notification posted with hidden amount and name
        shadowApp.grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        TransactionNotificationHelper.notifyTransactionDetected(
            context = app,
            outcome = outcome,
            transaction = tx,
            settings = BusinessSettings(
                primaryLanguage = AppLanguage.SWAHILI,
                pinEnabled = true,
                smsNotificationsEnabled = true
            )
        )
        assertEquals(1, shadowNm.allNotifications.size)
        val privateExtras = shadowNm.allNotifications.first().extras
        val privateText = privateExtras.getString(android.app.Notification.EXTRA_TEXT) ?: ""
        assertFalse(privateText.contains("25,000"))
        assertFalse(privateText.contains("HALIMA"))

        // Granted + unlocked -> shows amount and SMS format validated notice
        notificationManager.cancelAll()
        TransactionNotificationHelper.notifyTransactionDetected(
            context = app,
            outcome = outcome,
            transaction = tx,
            settings = BusinessSettings(
                primaryLanguage = AppLanguage.SWAHILI,
                pinEnabled = false,
                hideBalancesOnHome = false,
                smsNotificationsEnabled = true
            )
        )
        assertEquals(1, shadowNm.allNotifications.size)
        val publicExtras = shadowNm.allNotifications.first().extras
        val publicTitle = publicExtras.getString(android.app.Notification.EXTRA_TITLE) ?: ""
        val publicText = publicExtras.getString(android.app.Notification.EXTRA_TEXT) ?: ""
        assertTrue(publicTitle.contains("Umbizo Limethibitishwa"))
        assertTrue(publicText.contains("25,000"))
        assertTrue(publicText.contains("HALIMA SAID"))
    }

    /**
     * Builds a valid 3GPP SMS-DELIVER PDU with GSM 7-bit alphanumeric sender & body
     * for Robolectric Telephony.Sms.Intents.getMessagesFromIntent parsing.
     */
    private fun buildGsm7BitDeliverPdu(sender: String, body: String): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0x00) // SMSC length = 0
        out.write(0x04) // SMS-DELIVER first octet

        // TP-OA: Alphanumeric sender (type 0xD0)
        val packedSender = packGsm7Bit(sender)
        val senderSemiOctets = ((sender.length * 7 + 3) / 4)
        out.write(senderSemiOctets)
        out.write(0xD0)
        out.write(packedSender)

        out.write(0x00) // TP-PID
        out.write(0x00) // TP-DCS (GSM 7-bit default alphabet)

        // TP-SCTS (7 bytes: YY MM DD HH MM SS TZ in swapped nibbles)
        out.write(byteArrayOf(0x62, 0x11, 0x02, 0x11, 0x00, 0x00, 0x0C))

        // TP-UDL (septet count) & TP-UD
        out.write(body.length)
        out.write(packGsm7Bit(body))
        return out.toByteArray()
    }

    private fun packGsm7Bit(text: String): ByteArray {
        val septets = text.map { ch ->
            when (ch) {
                '@' -> 0x00
                '$' -> 0x02
                '_' -> 0x11
                else -> ch.code and 0x7F
            }
        }
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < septets.size) {
            val shift = i % 8
            if (shift == 7) {
                i++
                continue
            }
            val current = septets[i] ushr shift
            val next = if (i + 1 < septets.size) {
                (septets[i + 1] shl (7 - shift)) and 0xFF
            } else {
                0
            }
            out.write((current or next) and 0xFF)
            i++
        }
        return out.toByteArray()
    }
}

package com.example

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.BackupRestoreManager
import com.example.data.MauzoDatabase
import com.example.data.MauzoRepositoryImpl
import com.example.presentation.MauzoViewModel
import com.example.reporting.FinancialReportGenerator
import com.example.reporting.ReportExporter
import com.example.security.KeystoreManager
import com.example.security.PinManager
import com.example.sms_reader.SmsInboxImporter
import com.example.transaction_engine.FinancialTransactionEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SmsPermissionAndSecurityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `unauthenticated MainActivity displays Google Sign-In gate`() {
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithTag("google_sign_in_btn").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("google_sign_in_btn").assertExists()
    }

    @Test
    fun `clicking grant sms permission in MauzoAppRoot launches request without 16-bit requestCode crash`() {
        val app = ApplicationProvider.getApplicationContext<MauzoApplication>()
        Shadows.shadowOf(app).denyPermissions(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)

        val vm = MauzoViewModel(
            repository = app.repository,
            txEngine = FinancialTransactionEngine(),
            reportGenerator = FinancialReportGenerator(FinancialTransactionEngine()),
            reportExporter = ReportExporter(app),
            backupRestoreManager = BackupRestoreManager(app, app.repository, app.keystoreManager),
            smsInboxImporter = app.smsInboxImporter,
            pinManager = app.pinManager,
            appContext = app
        )

        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MauzoAppRoot(
                    activity = composeRule.activity,
                    viewModel = vm
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithTag("grant_sms_permission_btn").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("grant_sms_permission_btn").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithTag("confirm_sms_permission_btn").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("confirm_sms_permission_btn").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `sms permission status and firebase configuration resources are valid`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = MauzoDatabase.createInMemoryForTest(app)
        val repo = MauzoRepositoryImpl(db)
        val importer = SmsInboxImporter(app, repo)
        val shadowApp = Shadows.shadowOf(app)

        // Verify AndroidManifest declares READ_SMS, RECEIVE_SMS, and INTERNET (for Firebase)
        val pkgInfo = app.packageManager.getPackageInfo(
            app.packageName,
            android.content.pm.PackageManager.GET_PERMISSIONS
        )
        val declared = pkgInfo.requestedPermissions?.toSet().orEmpty()
        assertTrue(declared.contains(Manifest.permission.RECEIVE_SMS))
        assertTrue(declared.contains(Manifest.permission.READ_SMS))
        assertTrue(declared.contains(Manifest.permission.INTERNET))

        // Verify Firebase generated resources (custom database ID & default_web_client_id)
        assertTrue(app.getString(R.string.firestore_database_id).isNotBlank())
        assertTrue(app.getString(R.string.default_web_client_id).isNotBlank())

        // Clear permission request flag so fresh state starts unattempted
        app.getSharedPreferences("mauzo_permission_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        // Initially denied before any request
        shadowApp.denyPermissions(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
        val deniedStatus = importer.getPermissionStatus(composeRule.activity)
        assertFalse(deniedStatus.receiveSmsGranted)
        assertFalse(deniedStatus.readSmsGranted)
        assertFalse(deniedStatus.allGranted)
        assertFalse(deniedStatus.hasAttemptedRequest)
        assertFalse(deniedStatus.isPermanentlyDenied)
        assertTrue(importer.scanInboxForMobileMoneySms().isFailure)

        // After user denied permanently (attempted = true, shouldShowRationale = false)
        importer.markPermissionRequested()
        val permDeniedStatus = importer.getPermissionStatus(composeRule.activity)
        assertTrue(permDeniedStatus.hasAttemptedRequest)
        assertTrue(permDeniedStatus.isPermanentlyDenied)

        // Grant permissions -> permanentlyDenied clears and allGranted becomes true
        shadowApp.grantPermissions(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
        val grantedStatus = importer.getPermissionStatus(composeRule.activity)
        assertTrue(grantedStatus.receiveSmsGranted)
        assertTrue(grantedStatus.readSmsGranted)
        assertTrue(grantedStatus.allGranted)
        assertFalse(grantedStatus.isPermanentlyDenied)
        db.close()
    }

    @Test
    fun `pin manager hashes pin safely and keystore wraps db passphrase`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pinManager = PinManager(context)
        pinManager.setPin("2580").getOrThrow()
        assertTrue(pinManager.isPinConfigured())
        assertTrue(pinManager.verifyPin("2580").success)
        assertFalse(pinManager.verifyPin("1111").success)

        val keystore = KeystoreManager(context)
        val key1 = keystore.getOrCreateDatabasePassphrase()
        val key2 = keystore.getOrCreateDatabasePassphrase()
        assertEquals(32, key1.size)
        assertTrue(key1.contentEquals(key2))
    }
}

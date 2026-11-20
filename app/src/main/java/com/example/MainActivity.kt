package com.example

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.domain.MobileMoneyNetwork
import com.example.domain.NetworkFormatConfig
import com.example.domain.WalletSummary
import com.example.presentation.ExportFormat
import com.example.presentation.MauzoViewModel
import com.example.presentation.localization.LocalAppStrings
import com.example.presentation.localization.stringsForLanguage
import com.example.presentation.navigation.MauzoRoute
import com.example.presentation.screens.AddManualTransactionDialog
import com.example.presentation.screens.AuditLogDialog
import com.example.presentation.screens.AuthScreen
import com.example.presentation.screens.BackupPassphraseDialog
import com.example.presentation.screens.EditNetworkSendersDialog
import com.example.presentation.screens.HomeScreen
import com.example.presentation.screens.LockScreen
import com.example.presentation.screens.ManualSmsImportDialog
import com.example.presentation.screens.OpeningBalanceDialog
import com.example.presentation.screens.ReconcileWalletDialog
import com.example.presentation.screens.ReportsScreen
import com.example.presentation.screens.SetPinDialog
import com.example.presentation.screens.SettingsScreen
import com.example.presentation.screens.SmsArchiveScreen
import com.example.presentation.screens.TransactionDetailScreen
import com.example.presentation.screens.TransactionsScreen
import com.example.security.BiometricAuthHelper
import com.example.security.attemptAutoSignIn
import com.example.security.authStateFlow
import com.example.security.signOutFromFirebase
import com.example.ui.theme.MauzoTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as MauzoApplication
        setContent {
            MauzoAuthGate(
                activity = this,
                app = app
            )
        }
    }
}

@Composable
fun MauzoAuthGate(
    activity: FragmentActivity,
    app: MauzoApplication
) {
    val auth = remember { Firebase.auth }
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val credentialManager = remember(activity) { CredentialManager.create(activity) }
    val scope = rememberCoroutineScope()
    var isCheckingSilentAuth by remember { mutableStateOf(auth.currentUser == null) }

    LaunchedEffect(Unit) {
        if (auth.currentUser == null) {
            attemptAutoSignIn(
                context = activity,
                credentialManager = credentialManager,
                onAuthSuccess = { isCheckingSilentAuth = false },
                onUnauthenticated = { isCheckingSilentAuth = false },
                scope = scope
            )
        } else {
            isCheckingSilentAuth = false
        }
    }

    val user = currentUser
    if (user == null) {
        MauzoTheme {
            CompositionLocalProvider(LocalAppStrings provides stringsForLanguage(com.example.domain.AppLanguage.SWAHILI)) {
                AuthScreen(
                    isCheckingSilentAuth = isCheckingSilentAuth,
                    onAuthSuccess = { isCheckingSilentAuth = false }
                )
            }
        }
    } else {
        key(user.uid) {
            val viewModel: MauzoViewModel = viewModel(
                key = user.uid,
                factory = MauzoViewModel.Factory(
                    repository = app.repository,
                    txEngine = app.transactionEngine,
                    reportGenerator = app.reportGenerator,
                    reportExporter = app.reportExporter,
                    backupRestoreManager = app.backupRestoreManager,
                    smsInboxImporter = app.smsInboxImporter,
                    pinManager = app.pinManager,
                    appContext = app.applicationContext,
                    firestoreSyncRepository = app.firestoreSyncRepository,
                    currentUserId = user.uid,
                    currentUserEmail = user.email
                )
            )
            MauzoAppRoot(
                activity = activity,
                viewModel = viewModel,
                onSignOut = {
                    signOutFromFirebase(
                        credentialManager = credentialManager,
                        scope = scope
                    )
                }
            )
        }
    }
}

private data class BottomNavTab(
    val route: MauzoRoute,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun MauzoAppRoot(
    activity: FragmentActivity,
    viewModel: MauzoViewModel,
    onSignOut: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = remember(uiState.settings.primaryLanguage) {
        stringsForLanguage(uiState.settings.primaryLanguage)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val biometricAvailable = remember { BiometricAuthHelper.isBiometricAvailable(activity) }

    // Modals & Launchers state
    var showSmsPermissionRationale by remember { mutableStateOf(false) }
    var showSmsPermissionDeniedDialog by remember { mutableStateOf(false) }
    var pendingScanAfterPermission by remember { mutableStateOf(false) }
    var showManualSmsDialog by remember { mutableStateOf(false) }
    var showAddManualTxDialog by remember { mutableStateOf(false) }
    var walletToReconcile by remember { mutableStateOf<WalletSummary?>(null) }
    var openingBalanceNetwork by remember { mutableStateOf<MobileMoneyNetwork?>(null) }
    var networkFormatToEdit by remember { mutableStateOf<NetworkFormatConfig?>(null) }
    var showSetPinDialog by remember { mutableStateOf(false) }
    var showAuditLogDialog by remember { mutableStateOf(false) }

    var pendingExportFormat by remember { mutableStateOf<ExportFormat?>(null) }
    var pendingBackupPassphrase by remember { mutableStateOf<String?>(null) }
    var showBackupDialogMode by remember { mutableStateOf<String?>(null) } // "EXPORT" or "RESTORE"

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshSmsPermissions(activity)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(activity) {
        viewModel.refreshSmsPermissions(activity)
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { resultMap ->
        val updated = viewModel.onSmsPermissionRequestResult(activity, resultMap)
        val shouldScan = pendingScanAfterPermission
        pendingScanAfterPermission = false
        if (updated.allGranted) {
            if (shouldScan) {
                viewModel.scanDeviceSmsInbox()
            }
        } else {
            if (shouldScan && updated.readSmsGranted) {
                viewModel.scanDeviceSmsInbox()
            }
            showSmsPermissionDeniedDialog = true
        }
    }

    fun launchSmsPermissions() {
        val permissions = buildList {
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.READ_SMS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        smsPermissionLauncher.launch(permissions)
    }

    fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", activity.packageName, null)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
    }

    fun requestSmsPermissionsFlow(triggerScanAfterGrant: Boolean = false) {
        pendingScanAfterPermission = triggerScanAfterGrant
        viewModel.refreshSmsPermissions(activity)
        val currentStatus = viewModel.uiState.value.smsPermissions
        when {
            currentStatus.allGranted -> {
                if (triggerScanAfterGrant) {
                    pendingScanAfterPermission = false
                    viewModel.scanDeviceSmsInbox()
                }
            }
            currentStatus.isPermanentlyDenied -> {
                showSmsPermissionDeniedDialog = true
            }
            else -> {
                showSmsPermissionRationale = true
            }
        }
    }

    val createReportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        val fmt = pendingExportFormat
        if (uri != null && fmt != null) {
            viewModel.exportReport(uri, fmt)
        }
        pendingExportFormat = null
    }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        val pass = pendingBackupPassphrase
        if (uri != null && !pass.isNullOrBlank()) {
            viewModel.exportEncryptedBackup(uri, pass)
        }
        pendingBackupPassphrase = null
    }

    val openBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val pass = pendingBackupPassphrase
        if (uri != null && !pass.isNullOrBlank()) {
            viewModel.restoreEncryptedBackup(uri, pass)
        }
        pendingBackupPassphrase = null
    }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    MauzoTheme {
        CompositionLocalProvider(LocalAppStrings provides strings) {
            if (uiState.isLocked) {
                LockScreen(
                    businessName = uiState.settings.businessName,
                    biometricEnabled = uiState.settings.biometricEnabled,
                    biometricAvailable = biometricAvailable,
                    pinError = uiState.pinError,
                    onVerifyPin = viewModel::unlockWithPin,
                    onRequestBiometric = {
                        BiometricAuthHelper.authenticate(
                            activity = activity,
                            title = strings.lockScreenTitle,
                            subtitle = uiState.settings.businessName,
                            negativeButtonText = strings.cancel,
                            onSuccess = { viewModel.unlockWithBiometricSuccess() },
                            onError = { /* User falls back to PIN */ }
                        )
                    }
                )
                return@CompositionLocalProvider
            }

            val navController = rememberNavController()
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = backStackEntry?.destination
            val isDetailRoute = currentDestination?.hasRoute<MauzoRoute.TransactionDetail>() == true

            val tabs = remember(strings) {
                listOf(
                    BottomNavTab(MauzoRoute.Nyumbani, strings.navHome, Icons.Filled.Home, Icons.Outlined.Home, "nav_nyumbani"),
                    BottomNavTab(MauzoRoute.Miamala, strings.navTransactions, Icons.Filled.ReceiptLong, Icons.Outlined.ReceiptLong, "nav_miamala"),
                    BottomNavTab(MauzoRoute.Sms, strings.navSms, Icons.Filled.Sms, Icons.Outlined.Sms, "nav_sms"),
                    BottomNavTab(MauzoRoute.Ripoti, strings.navReports, Icons.Filled.BarChart, Icons.Outlined.BarChart, "nav_ripoti"),
                    BottomNavTab(MauzoRoute.Mipangilio, strings.navSettings, Icons.Filled.Settings, Icons.Outlined.Settings, "nav_mipangilio")
                )
            }

            fun navigateToTab(route: MauzoRoute) {
                navController.navigate(route) {
                    popUpTo<MauzoRoute.Nyumbani> { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useNavigationRail = maxWidth >= 600.dp

                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        if (!useNavigationRail && !isDetailRoute) {
                            NavigationBar(windowInsets = WindowInsets.navigationBars) {
                                tabs.forEach { tab ->
                                    val selected = currentDestination?.hasRoute(tab.route::class) == true
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { navigateToTab(tab.route) },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.label
                                            )
                                        },
                                        label = { Text(tab.label, maxLines = 1) },
                                        modifier = Modifier.testTag(tab.testTag)
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (useNavigationRail && !isDetailRoute) {
                            NavigationRail {
                                tabs.forEach { tab ->
                                    val selected = currentDestination?.hasRoute(tab.route::class) == true
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = { navigateToTab(tab.route) },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.label
                                            )
                                        },
                                        label = { Text(tab.label, maxLines = 1) },
                                        modifier = Modifier.testTag(tab.testTag)
                                    )
                                }
                            }
                        }

                        NavHost(
                            navController = navController,
                            startDestination = MauzoRoute.Nyumbani,
                            modifier = Modifier.weight(1f)
                        ) {
                            composable<MauzoRoute.Nyumbani> {
                                HomeScreen(
                                    uiState = uiState,
                                    onTogglePrivacy = viewModel::toggleBalancePrivacy,
                                    onRequestSmsPermission = { requestSmsPermissionsFlow(false) },
                                    onOpenSmsImport = { showManualSmsDialog = true },
                                    onOpenManualTxDialog = { showAddManualTxDialog = true },
                                    onViewAllTransactions = { navigateToTab(MauzoRoute.Miamala) },
                                    onTransactionClick = { txId ->
                                        navController.navigate(MauzoRoute.TransactionDetail(txId))
                                    },
                                    onReconcileWallet = { walletToReconcile = it },
                                    onLockNow = viewModel::lockAppNow
                                )
                            }

                            composable<MauzoRoute.Miamala> {
                                TransactionsScreen(
                                    uiState = uiState,
                                    onUpdateFilters = viewModel::updateFilters,
                                    onOpenAddManualDialog = { showAddManualTxDialog = true },
                                    onTransactionClick = { txId ->
                                        navController.navigate(MauzoRoute.TransactionDetail(txId))
                                    }
                                )
                            }

                            composable<MauzoRoute.Sms> {
                                SmsArchiveScreen(
                                    uiState = uiState,
                                    onSearchQueryChange = viewModel::updateSmsSearchQuery,
                                    onOpenManualSmsImport = { showManualSmsDialog = true },
                                    onScanInbox = viewModel::scanDeviceSmsInbox,
                                    onRequestPermissions = { requestSmsPermissionsFlow(false) },
                                    onRequestScanPermission = { requestSmsPermissionsFlow(true) },
                                    onOpenLinkedTransaction = { txId ->
                                        navController.navigate(MauzoRoute.TransactionDetail(txId))
                                    }
                                )
                            }

                            composable<MauzoRoute.Ripoti> {
                                ReportsScreen(
                                    uiState = uiState,
                                    onSelectPeriod = viewModel::selectReportPeriod,
                                    onExport = { format ->
                                        pendingExportFormat = format
                                        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                                        createReportLauncher.launch("mauzo_report_$stamp.${format.ext}")
                                    }
                                )
                            }

                            composable<MauzoRoute.Mipangilio> {
                                SettingsScreen(
                                    uiState = uiState,
                                    biometricAvailable = biometricAvailable,
                                    onUpdateBusinessName = viewModel::updateBusinessName,
                                    onUpdateLanguage = viewModel::updateLanguage,
                                    onUpdateCurrency = viewModel::updateCurrency,
                                    onToggleNetwork = viewModel::toggleNetworkEnabled,
                                    onOpenEditSendersDialog = { networkFormatToEdit = it },
                                    onToggleSmsNotifications = viewModel::toggleSmsNotifications,
                                    onSetSimSlotFilter = viewModel::setSimSlotFilter,
                                    onOpenOpeningBalanceDialog = { openingBalanceNetwork = it },
                                    onRequestSmsPermissions = { requestSmsPermissionsFlow(false) },
                                    onOpenSetPinDialog = { showSetPinDialog = true },
                                    onDisablePin = viewModel::disablePin,
                                    onToggleBiometric = viewModel::toggleBiometric,
                                    onToggleAutoBackup = viewModel::toggleAutoBackup,
                                    onRunLocalBackupNow = viewModel::runManualAutoBackupNow,
                                    onExportEncryptedBackup = { showBackupDialogMode = "EXPORT" },
                                    onRestoreEncryptedBackup = { showBackupDialogMode = "RESTORE" },
                                    onOpenExportReports = { navigateToTab(MauzoRoute.Ripoti) },
                                    onOpenAuditLogDialog = { showAuditLogDialog = true },
                                    onDeleteArchivedSms = viewModel::deleteArchivedSms,
                                    onDeleteAllData = viewModel::deleteAllLocalData,
                                    onSyncToCloud = viewModel::syncToCloudNow,
                                    onRestoreFromCloud = viewModel::restoreFromCloud,
                                    onSignOut = onSignOut
                                )
                            }

                            composable<MauzoRoute.TransactionDetail> { entry ->
                                val route = entry.toRoute<MauzoRoute.TransactionDetail>()
                                val tx = uiState.allTransactions.firstOrNull { it.id == route.transactionLocalId }
                                val linkedSms = tx?.smsArchiveId?.let { sid ->
                                    uiState.smsArchive.firstOrNull { it.id == sid }
                                }
                                TransactionDetailScreen(
                                    tx = tx,
                                    linkedSms = linkedSms,
                                    currencyCode = uiState.settings.currencyCode,
                                    language = uiState.settings.primaryLanguage,
                                    onBack = { navController.popBackStack() },
                                    onSaveEdit = viewModel::updateTransaction,
                                    onRecordReversalOrRefund = viewModel::recordReversalOrRefund
                                )
                            }
                        }
                    }
                }
            }

            // Permission Rationale Dialog before launching Android SMS permission prompt
            if (showSmsPermissionRationale) {
                AlertDialog(
                    onDismissRequest = {
                        showSmsPermissionRationale = false
                        pendingScanAfterPermission = false
                    },
                    title = { Text(strings.smsPermissionTitle) },
                    text = { Text(strings.smsPermissionDesc) },
                    confirmButton = {
                        Button(
                            onClick = {
                                showSmsPermissionRationale = false
                                launchSmsPermissions()
                            },
                            modifier = Modifier.testTag("confirm_sms_permission_btn")
                        ) {
                            Text(strings.grantSmsPermission)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showSmsPermissionRationale = false
                                pendingScanAfterPermission = false
                            }
                        ) {
                            Text(strings.cancel)
                        }
                    },
                    modifier = Modifier.testTag("sms_permission_rationale_dialog")
                )
            }

            // Permission Denied / Permanently Denied Recovery Dialog
            if (showSmsPermissionDeniedDialog) {
                val permanentlyDenied = uiState.smsPermissions.isPermanentlyDenied
                AlertDialog(
                    onDismissRequest = { showSmsPermissionDeniedDialog = false },
                    title = { Text(strings.smsPermissionDeniedTitle) },
                    text = {
                        Text(
                            if (permanentlyDenied) {
                                strings.smsPermissionPermanentDeniedDesc
                            } else {
                                strings.smsPermissionDeniedDesc
                            }
                        )
                    },
                    confirmButton = {
                        if (permanentlyDenied) {
                            Button(
                                onClick = {
                                    showSmsPermissionDeniedDialog = false
                                    openAppSettings()
                                },
                                modifier = Modifier.testTag("open_app_settings_btn")
                            ) {
                                Text(strings.openAppSettings)
                            }
                        } else {
                            Button(
                                onClick = {
                                    showSmsPermissionDeniedDialog = false
                                    launchSmsPermissions()
                                },
                                modifier = Modifier.testTag("retry_sms_permission_btn")
                            ) {
                                Text(strings.tryAgain)
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showSmsPermissionDeniedDialog = false
                                showManualSmsDialog = true
                            },
                            modifier = Modifier.testTag("fallback_manual_sms_btn")
                        ) {
                            Text(strings.continueManualImport)
                        }
                    },
                    modifier = Modifier.testTag("sms_permission_denied_dialog")
                )
            }

            if (showManualSmsDialog) {
                ManualSmsImportDialog(
                    onDismiss = { showManualSmsDialog = false },
                    onImport = { sender, body ->
                        viewModel.importManualSms(sender, body)
                        showManualSmsDialog = false
                    }
                )
            }

            if (showAddManualTxDialog) {
                AddManualTransactionDialog(
                    onDismiss = { showAddManualTxDialog = false },
                    onSave = { txId, net, type, amt, fee, name, phone, desc, isCredit, reason ->
                        viewModel.addManualTransaction(txId, net, type, amt, fee, name, phone, desc, isCredit, reason)
                        showAddManualTxDialog = false
                    }
                )
            }

            walletToReconcile?.let { w ->
                ReconcileWalletDialog(
                    wallet = w,
                    currencyCode = uiState.settings.currencyCode,
                    onDismiss = { walletToReconcile = null },
                    onConfirm = { providerBal, adjust, reason ->
                        viewModel.reconcileWallet(w.network, providerBal, adjust, reason)
                        walletToReconcile = null
                    }
                )
            }

            openingBalanceNetwork?.let { net ->
                val current = uiState.openingBalances.firstOrNull { it.network == net }?.amount?.toPlainString() ?: "0"
                OpeningBalanceDialog(
                    network = net,
                    currentAmountText = current,
                    onDismiss = { openingBalanceNetwork = null },
                    onSave = { amountText, note ->
                        viewModel.setOpeningBalance(net, amountText, note)
                        openingBalanceNetwork = null
                    }
                )
            }

            if (showSetPinDialog) {
                SetPinDialog(
                    onDismiss = { showSetPinDialog = false },
                    onConfirm = { newPin ->
                        viewModel.configurePin(newPin)
                        showSetPinDialog = false
                    }
                )
            }

            showBackupDialogMode?.let { mode ->
                BackupPassphraseDialog(
                    isRestore = mode == "RESTORE",
                    onDismiss = { showBackupDialogMode = null },
                    onConfirm = { pass ->
                        pendingBackupPassphrase = pass
                        showBackupDialogMode = null
                        if (mode == "EXPORT") {
                            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                            createBackupLauncher.launch("mauzo_backup_$stamp.mauzobak")
                        } else {
                            openBackupLauncher.launch(arrayOf("*/*"))
                        }
                    }
                )
            }

            if (showAuditLogDialog) {
                AuditLogDialog(
                    logs = uiState.auditLogs,
                    onDismiss = { showAuditLogDialog = false }
                )
            }

            networkFormatToEdit?.let { fmt ->
                EditNetworkSendersDialog(
                    networkFormat = fmt,
                    language = uiState.settings.primaryLanguage,
                    onDismiss = { networkFormatToEdit = null },
                    onSave = { csv ->
                        viewModel.updateNetworkSenders(fmt.network, csv)
                        networkFormatToEdit = null
                    }
                )
            }
        }
    }
}

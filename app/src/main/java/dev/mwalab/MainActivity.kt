package dev.mwalab

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mwalab.app.MwaLabComposition
import dev.mwalab.report.DiagnosticReportCacheWriter
import dev.mwalab.report.DiagnosticReportExportUseCase
import dev.mwalab.report.DiagnosticReportFormat
import dev.mwalab.report.DiagnosticReportShareIntentFactory
import dev.mwalab.report.DiagnosticReportSnapshotAssembler
import dev.mwalab.ui.faults.FaultLabScreen
import dev.mwalab.ui.identity.LabIdentityScreen
import dev.mwalab.ui.navigation.AppDestination
import dev.mwalab.ui.navigation.LabNavigationIcon
import dev.mwalab.ui.onboarding.OnboardingScreen
import dev.mwalab.ui.sessions.*
import dev.mwalab.ui.settings.SettingsScreen
import dev.mwalab.ui.settings.ThemeMode
import dev.mwalab.ui.settings.UiPreferences
import dev.mwalab.ui.theme.MWALabTheme

class MainActivity : ComponentActivity() {
    private var homeViewModel: HomeViewModel? = null

    override fun onResume() {
        super.onResume()
        homeViewModel?.refreshWallet()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val uiPreferences = UiPreferences(applicationContext)
        val repository = MwaLabComposition.sessionRepository(applicationContext)
        val faultSelection = MwaLabComposition.faultSelectionRepository(applicationContext)
        val capabilities = MwaLabComposition.capabilitySnapshotRepository(applicationContext)
        val transactions = MwaLabComposition.transactionDiagnosticRepository(applicationContext)
        val simulations = MwaLabComposition.simulationRepository(applicationContext)
        val reportExports = DiagnosticReportExportUseCase(
            DiagnosticReportSnapshotAssembler(repository, capabilities, transactions, simulations),
            DiagnosticReportCacheWriter(applicationContext.cacheDir),
        )
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val model = when (modelClass) {
                    HomeViewModel::class.java -> HomeViewModel(
                        MwaLabComposition.identityRepository(applicationContext),
                        repository,
                        walletService = MwaLabComposition.testWalletService(applicationContext),
                    )
                    SessionsViewModel::class.java -> SessionsViewModel(repository)
                    SessionDetailViewModel::class.java -> SessionDetailViewModel(
                        repository, capabilities, transactionRepository = transactions,
                        simulationRepository = simulations, reportExports = reportExports,
                    )
                    else -> error("Unknown screen model")
                }
                @Suppress("UNCHECKED_CAST")
                return model as T
            }
        }
        val provider = ViewModelProvider(this, factory)
        val home = provider[HomeViewModel::class.java]
        homeViewModel = home
        val sessions = provider[SessionsViewModel::class.java]
        val detail = provider[SessionDetailViewModel::class.java]
        @Suppress("DEPRECATION")
        val version = packageManager.getPackageInfo(packageName, 0).let {
            "${it.versionName ?: "Unknown"} · build ${it.versionCode}"
        }
        val copyAddress: (String) -> Unit = { address ->
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Devnet test address", address))
            Toast.makeText(this, R.string.address_copied, Toast.LENGTH_SHORT).show()
        }
        setContent {
            var themeMode by remember { mutableStateOf(uiPreferences.themeMode) }
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            MWALabTheme(darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }) {
                var onboardingSeen by remember { mutableStateOf(uiPreferences.onboardingSeen) }
                var destination by rememberSaveable { mutableStateOf(AppDestination.HOME) }
                var selectedSession by rememberSaveable { mutableStateOf<String?>(null) }
                val homeState by home.state.collectAsStateWithLifecycle()
                val activeFault by faultSelection.selected.collectAsStateWithLifecycle()
                val sessionsState by sessions.state.collectAsStateWithLifecycle()
                val detailState by detail.state.collectAsStateWithLifecycle()
                val reportState by detail.reportState.collectAsStateWithLifecycle()
                LaunchedEffect(detail) {
                    detail.reportEffects.collect { effect ->
                        try {
                            when (effect) {
                                is ReportExportEffect.Share -> {
                                    if (!detail.isSelectedSession(effect.artifact.sessionId)) return@collect
                                    val chooser = DiagnosticReportShareIntentFactory(applicationContext)
                                        .create(effect.artifact.file, effect.artifact.format)
                                    startActivity(chooser)
                                    detail.reportShareLaunched(effect.artifact.format,
                                        effect.artifact.completeness, effect.artifact.truncated)
                                }
                                is ReportExportEffect.Copy -> {
                                    if (!detail.isSelectedSession(effect.sessionId)) return@collect
                                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Sanitized diagnostic summary", effect.text))
                                    detail.reportCopied(effect.completeness, effect.truncated)
                                }
                            }
                        } catch (_: Exception) { detail.reportDeliveryFailed() }
                    }
                }
                LaunchedEffect(selectedSession) { selectedSession?.let(detail::selectSession) }
                LaunchedEffect(onboardingSeen, destination) {
                    if (onboardingSeen && (
                            destination == AppDestination.HOME ||
                                destination == AppDestination.LAB_IDENTITY
                        )
                    ) {
                        home.refreshWallet()
                    }
                }
                val openSession: (String) -> Unit = { id ->
                    selectedSession = id
                    detail.selectSession(id)
                    destination = AppDestination.SESSION_DETAIL
                }
                BackHandler(enabled = onboardingSeen && destination != AppDestination.HOME) {
                    destination = destination.backDestination()
                }
                if (!onboardingSeen) {
                    OnboardingScreen(activeFault, homeState) {
                        uiPreferences.onboardingSeen = true
                        onboardingSeen = true
                    }
                } else {
                    Scaffold(
                        modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true },
                        bottomBar = {
                            NavigationBar {
                                AppDestination.topLevel.forEach { item ->
                                    NavigationBarItem(
                                        modifier = Modifier.testTag("nav-${item.name}").semantics { contentDescription = item.label },
                                        selected = destination == item ||
                                            destination == AppDestination.SESSION_DETAIL && item == AppDestination.SESSIONS,
                                        onClick = { destination = item },
                                        icon = { LabNavigationIcon(item) },
                                        label = { Text(item.label, maxLines = 1) },
                                        alwaysShowLabel = true,
                                    )
                                }
                            }
                        },
                    ) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding)) {
                            when (destination) {
                                AppDestination.HOME -> HomeScreen(
                                    homeState,
                                    { destination = AppDestination.SESSIONS },
                                    openSession,
                                    home::retry,
                                    activeFault,
                                    { destination = AppDestination.FAULT_LAB },
                                    onIdentity = { destination = AppDestination.LAB_IDENTITY },
                                    onCopyAddress = copyAddress,
                                    onRefreshWallet = home::refreshWallet,
                                    onRequestAirdrop = home::requestDevnetSol,
                                )
                                AppDestination.SESSIONS -> SessionsScreen(sessionsState, openSession, sessions::retry)
                                AppDestination.FAULT_LAB -> FaultLabScreen(activeFault, faultSelection::select)
                                AppDestination.LAB_IDENTITY -> LabIdentityScreen(
                                    identity = homeState.identity,
                                    onCopy = copyAddress,
                                    onRetry = home::retry,
                                    wallet = homeState.wallet,
                                    onRequestAirdrop = home::requestDevnetSol,
                                )
                                AppDestination.SETTINGS -> SettingsScreen(themeMode, { mode ->
                                    themeMode = mode
                                    uiPreferences.themeMode = mode
                                }, version)
                                AppDestination.SESSION_DETAIL -> SessionDetailScreen(
                                    detailState, { destination = AppDestination.SESSIONS }, detail::retry,
                                    detail::retryCapabilities, detail::retryTransactions, detail::retrySimulations,
                                    reportState, { detail.shareReport(DiagnosticReportFormat.MARKDOWN) },
                                    { detail.shareReport(DiagnosticReportFormat.JSON) }, detail::copyReportSummary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

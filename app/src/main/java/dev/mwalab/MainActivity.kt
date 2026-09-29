package dev.mwalab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mwalab.app.MwaLabComposition
import dev.mwalab.ui.sessions.*
import dev.mwalab.ui.theme.MWALabTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = MwaLabComposition.sessionRepository(applicationContext)
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val model = when (modelClass) {
                    HomeViewModel::class.java -> HomeViewModel(MwaLabComposition.identityRepository(applicationContext), repository)
                    SessionsViewModel::class.java -> SessionsViewModel(repository)
                    SessionDetailViewModel::class.java -> SessionDetailViewModel(
                        repository, MwaLabComposition.capabilitySnapshotRepository(applicationContext),
                    )
                    else -> error("Unknown screen model")
                }
                @Suppress("UNCHECKED_CAST")
                return model as T
            }
        }
        val provider = ViewModelProvider(this, factory)
        val home = provider[HomeViewModel::class.java]
        val sessions = provider[SessionsViewModel::class.java]
        val detail = provider[SessionDetailViewModel::class.java]
        setContent {
            MWALabTheme {
                var screen by rememberSaveable { mutableStateOf("Home") }
                var selectedSession by rememberSaveable { mutableStateOf<String?>(null) }
                val homeState by home.state.collectAsStateWithLifecycle()
                val sessionsState by sessions.state.collectAsStateWithLifecycle()
                val detailState by detail.state.collectAsStateWithLifecycle()
                LaunchedEffect(selectedSession) { selectedSession?.let(detail::selectSession) }
                val openSession: (String) -> Unit = { selectedSession = it; detail.selectSession(it); screen = "Detail" }
                BackHandler(enabled = screen != "Home") {
                    screen = if (screen == "Detail") "Sessions" else "Home"
                }
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).semantics { testTagsAsResourceId = true }) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text("MWA LAB TEST ENDPOINT", style = MaterialTheme.typography.titleLarge)
                            Text("SOLANA DEVNET · NO REAL FUNDS", style = MaterialTheme.typography.labelLarge)
                            Row {
                                TextButton(onClick = { screen = "Home" }) { Text("Home") }
                                TextButton(onClick = { screen = "Sessions" }) { Text("Sessions") }
                            }
                        }
                        HorizontalDivider()
                        Box(Modifier.weight(1f)) {
                            when (screen) {
                                "Sessions" -> SessionsScreen(sessionsState, openSession, sessions::retry)
                                "Detail" -> SessionDetailScreen(
                                    detailState, { screen = "Sessions" }, detail::retry, detail::retryCapabilities,
                                )
                                else -> HomeScreen(homeState, { screen = "Sessions" }, openSession, home::retry)
                            }
                        }
                    }
                }
            }
        }
    }
}

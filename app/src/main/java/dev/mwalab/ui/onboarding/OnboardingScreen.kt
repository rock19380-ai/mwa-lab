package dev.mwalab.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.mwalab.R
import dev.mwalab.faults.FaultId
import dev.mwalab.faults.FaultProfile
import dev.mwalab.ui.components.LabSafetyBanner
import dev.mwalab.ui.components.SectionCard

@Composable
fun OnboardingScreen(activeFault: FaultProfile, onEnter: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().systemBarsPadding(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall) }
        item { Text(stringResource(R.string.product_subtitle), style = MaterialTheme.typography.titleLarge) }
        item { LabSafetyBanner() }
        item { Text(if (activeFault.id == FaultId.NORMAL) "Current mode: NORMAL" else
            "FAULT ACTIVE · ${activeFault.displayName} · INTENTIONAL TEST CONDITION") }
        item { Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineSmall) }
        item { SectionCard("TRACE") { Text(stringResource(R.string.onboarding_trace)) } }
        item { SectionCard("REPRODUCE") { Text(stringResource(R.string.onboarding_faults)) } }
        item { SectionCard("SHARE") { Text(stringResource(R.string.onboarding_reports)) } }
        item { Button(onClick = onEnter, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.onboarding_enter)) } }
    }
}

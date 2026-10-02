package dev.mwalab.ui.transaction

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.mwalab.transaction.TransactionSummary

/** An event-scoped expansion in the existing timeline, never a navigation destination. */
fun LazyListScope.transactionInspectorContent(
    summary: TransactionSummary,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val key = "transaction-${summary.eventId}-${summary.payloadIndex}"
    item(key = key) {
        Card(Modifier.fillMaxWidth().padding(start = 12.dp).testTag(key)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Transaction ${summary.payloadIndex + 1}", style = MaterialTheme.typography.titleMedium)
                Text("Version: ${TransactionPresentation.version(summary)} · ${TransactionPresentation.status(summary)}")
                summary.instructions?.firstOrNull()?.programName?.let { Text(it) }
                Text("${summary.instructionCount ?: 0} instructions · read-only diagnostic metadata")
                OutlinedButton(onClick = onToggle) {
                    Text("${if (expanded) "Collapse" else "Inspect"} transaction ${summary.payloadIndex + 1}")
                }
            }
        }
    }
    if (expanded) TransactionPresentation.sections(summary).forEach { section ->
        item(key = "$key-${section.title}") {
            Text(section.title, Modifier.padding(start = 28.dp).testTag("$key-${section.title}"),
                style = MaterialTheme.typography.titleMedium)
        }
        // Individual lazy rows keep long account/reference lists usable on small screens.
        itemsIndexed(section.rows, key = { index, _ -> "$key-${section.title}-$index" }) { _, row ->
            Surface(Modifier.fillMaxWidth().padding(start = 12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow) {
                SelectionContainer {
                    Text(row.text, Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        style = if (row.heading) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

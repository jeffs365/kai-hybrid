package com.inspiredandroid.kai.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inspiredandroid.kai.agent.ExecutionStatus
import kai.composeapp.generated.resources.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

@Composable
internal fun AgentExecutionSettings(state: SettingsUiState, actions: SettingsActions) {
    val prefs = state.agentPreferences
    var apps by remember(prefs.notificationSources) { mutableStateOf(prefs.notificationSources.sorted().joinToString("\n")) }
    var senders by remember(prefs.smsSources) { mutableStateOf(prefs.smsSources.sorted().joinToString("\n")) }
    var retention by remember(prefs.retentionDays) { mutableStateOf(prefs.retentionDays.toString()) }
    var invalid by remember { mutableStateOf(false) }
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringResource(Res.string.safe_agent_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(Res.string.safe_agent_description), style = MaterialTheme.typography.bodySmall)
            Row {
                Text(stringResource(Res.string.safe_agent_title), Modifier.weight(1f))
                Switch(checked = prefs.enabled, onCheckedChange = { actions.onUpdateAgentPreferences(prefs.copy(enabled = it)) })
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(Res.string.event_privacy_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(Res.string.event_privacy_description), style = MaterialTheme.typography.bodySmall)
            Row {
                Text(stringResource(Res.string.event_notification_cloud), Modifier.weight(1f))
                Switch(checked = prefs.notificationCloud, onCheckedChange = { actions.onUpdateAgentPreferences(prefs.copy(notificationCloud = it)) })
            }
            Row {
                Text(stringResource(Res.string.event_sms_cloud), Modifier.weight(1f))
                Switch(checked = prefs.smsCloud, onCheckedChange = { actions.onUpdateAgentPreferences(prefs.copy(smsCloud = it)) })
            }
            Text(stringResource(Res.string.event_sources_hint), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(apps, {
                apps = it.take(20_000)
                invalid = false
            }, label = { Text(stringResource(Res.string.event_notification_sources)) }, modifier = Modifier.fillMaxWidth(), maxLines = 5)
            OutlinedTextField(senders, {
                senders = it.take(8_000)
                invalid = false
            }, label = { Text(stringResource(Res.string.event_sms_sources)) }, modifier = Modifier.fillMaxWidth(), maxLines = 5)
            OutlinedTextField(retention, {
                retention = it.take(2)
                invalid = false
            }, label = { Text(stringResource(Res.string.agent_retention)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (invalid) Text(stringResource(Res.string.agent_invalid_policy), color = MaterialTheme.colorScheme.error)
            Button(onClick = {
                val sources = apps.lines().map(String::trim).filter(String::isNotBlank).toSet()
                val sms = senders.lines().map { it.trim().lowercase() }.filter(String::isNotBlank).toSet()
                val days = retention.toIntOrNull()
                if (days == null || days !in 1..30 || sources.size > 100 || sms.size > 100 || sms.any { it.length > 80 } || sources.any { it.length > 200 || !Regex("[a-zA-Z][a-zA-Z0-9_]*(?:\\.[a-zA-Z0-9_]+)+").matches(it) }) {
                    invalid = true
                } else {
                    actions.onUpdateAgentPreferences(prefs.copy(notificationSources = sources, smsSources = sms, retentionDays = days))
                }
            }) { Text(stringResource(Res.string.agent_save_policy)) }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(Res.string.agent_activity_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(Res.string.agent_history_note), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = actions.onClearAgentHistory) { Text(stringResource(Res.string.agent_clear_history)) }
            if (state.agentHistory.isEmpty()) Text(stringResource(Res.string.agent_activity_empty))
            state.agentHistory.take(20).forEach { entry ->
                val time = Instant.fromEpochMilliseconds(entry.updatedAt).toLocalDateTime(TimeZone.currentSystemDefault()).toString().replace('T', ' ').take(19)
                val name = com.inspiredandroid.kai.data.phoneToolDefinitions.firstOrNull { it.id == entry.tool }?.name
                    ?: if (entry.kind == "run") {
                        "Phone task"
                    } else if (entry.kind == "approval") {
                        "Action review"
                    } else {
                        "Unsupported action"
                    }
                val origin = if (entry.origin == "FOREGROUND_USER") {
                    "AI request"
                } else if (entry.origin == "DIRECT_USER") {
                    "Direct command"
                } else {
                    "Review"
                }
                Text("$name · ${entry.status.name.lowercase().replace('_', ' ')} · $origin", style = MaterialTheme.typography.bodyMedium)
                Text("$time · ${entry.durationMs} ms${entry.errorCode?.let { " · ${it.replace('_', ' ')}" }.orEmpty()}", style = MaterialTheme.typography.bodySmall)
                if (entry.status !in setOf(ExecutionStatus.RUNNING, ExecutionStatus.WAITING_APPROVAL)) {
                    TextButton(onClick = { actions.onDeleteAgentEntry(entry.id) }) { Text(stringResource(Res.string.agent_delete_entry)) }
                }
            }
        }
    }
}

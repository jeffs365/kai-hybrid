package com.inspiredandroid.kai.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inspiredandroid.kai.agent.AgentActivity
import com.inspiredandroid.kai.agent.AgentApproval
import kai.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AgentStatusBanner(activity: AgentActivity?, approval: AgentApproval?, onStop: () -> Unit, decide: (String, Boolean) -> Unit) {
    if (activity == null && approval == null) return
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(stringResource(Res.string.agent_running), style = MaterialTheme.typography.titleSmall)
            activity?.takeIf { it.step > 0 }?.let { Text("${stringResource(Res.string.agent_step)} ${it.step}") }
            Button(onClick = onStop) { Text(stringResource(Res.string.agent_stop)) }
            approval?.let {
                Text(stringResource(Res.string.agent_approval))
                Text(it.label)
                Row {
                    TextButton(onClick = { decide(it.id, false) }) { Text(stringResource(Res.string.agent_deny)) }
                    TextButton(onClick = { decide(it.id, true) }) { Text(stringResource(Res.string.agent_approve)) }
                }
            }
        }
    }
}

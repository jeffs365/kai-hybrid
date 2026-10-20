package com.inspiredandroid.kai.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inspiredandroid.kai.data.AppSettings
import com.inspiredandroid.kai.ui.kaiAdaptiveCardBorder
import com.inspiredandroid.kai.ui.kaiAdaptiveCardColors
import org.koin.compose.koinInject

@Composable
internal fun DeepSeekConsultationSettings() {
    val settings = koinInject<AppSettings>()
    var enabled by remember { mutableStateOf(settings.isDeepSeekConsultationEnabled()) }
    Card(modifier = Modifier.fillMaxWidth(), colors = kaiAdaptiveCardColors(), border = kaiAdaptiveCardBorder()) {
        Column(Modifier.padding(16.dp)) {
            ToggleableHeadline(
                title = "Allow DeepSeek consultation",
                description = "Let your local model ask DeepSeek for help with difficult questions. Selected question and context leave your phone; your provider bills the API usage.",
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    settings.setDeepSeekConsultationEnabled(it)
                },
            )
            Spacer(Modifier.size(12.dp))
            Text(
                "Keep Local Model selected for everyday chat. Add a DeepSeek service below and enter your API key there. Use /deepseek followed by a question to consult it directly.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                "One cloud call per turn · 6,000 input characters · 2,048 output tokens · 45-second limit. Advice only; DeepSeek cannot run actions. With Local Model selected, background checks stay local unless you choose a cloud model for the job.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.size(16.dp))
}

package com.inspiredandroid.kai.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.inspiredandroid.kai.data.AppSettings
import com.inspiredandroid.kai.ui.kaiAdaptiveCardBorder
import com.inspiredandroid.kai.ui.kaiAdaptiveCardColors
import org.koin.compose.koinInject

@Composable
internal fun BraveSearchSettings() {
    val settings = koinInject<AppSettings>()
    val uriHandler = LocalUriHandler.current
    var apiKey by remember { mutableStateOf(settings.getBraveSearchApiKey()) }
    var automatic by remember { mutableStateOf(settings.isBraveSearchAutomatic()) }
    Card(modifier = Modifier.fillMaxWidth(), colors = kaiAdaptiveCardColors(), border = kaiAdaptiveCardBorder()) {
        Column(Modifier.padding(16.dp)) {
            Text("Web Search · Brave", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(12.dp))
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = apiKey,
                onValueChange = {
                    apiKey = it.trim()
                    settings.setBraveSearchApiKey(apiKey)
                },
                label = { Text("Brave Search API key") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
            )
            Text(if (apiKey.isBlank()) "Enter your key here. It is saved automatically on this phone." else "Key saved on this phone. Use /search to test the connection.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { uriHandler.openUri("https://api-dashboard.search.brave.com/app/keys") }) { Text("Get a Brave Search API key") }
            ToggleableHeadline(
                title = "Search automatically",
                description = "Let E2B search for current information. Search queries leave your phone; Brave may charge for usage. The Web Search tool switch must also be on.",
                checked = automatic,
                onCheckedChange = {
                    automatic = it
                    settings.setBraveSearchAutomatic(it)
                },
            )
            Spacer(Modifier.size(8.dp))
            Text("Use /search followed by a query for a manual lookup. One search per turn · up to 5 results · 15-second timeout. E2B summarizes excerpts locally and source links are included. No automatic retries or background searches.", style = MaterialTheme.typography.bodySmall)
        }
    }
    Spacer(Modifier.size(16.dp))
}

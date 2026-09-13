package com.lgka

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar

/// Krankmeldung pre-info — mirrors krankmeldung_info_screen.dart.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KrankmeldungInfoScreen(onBack: () -> Unit, onContinue: () -> Unit) {
    val prefs = LocalContainer.current.prefs
    val haptics = rememberHaptics()
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.krankmeldung_info_header)) },
            navigationIcon = {
                IconButton(onClick = { haptics.light(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.a11y_back)) }
            })
    }) { padding ->
        Column(Modifier.padding(top = padding.calculateTopPadding()).navigationBarsPadding().padding(16.dp)) {
            listOf(Icons.Outlined.WarningAmber to R.string.krankmeldung_disclaimer,
                   Icons.Outlined.SupportAgent to R.string.krankmeldung_contact)
                .forEach { (icon, text) ->
                    Card(shape = CardShape, modifier = Modifier.padding(bottom = 16.dp).semantics(mergeDescendants = true) {}) {
                        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(52.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center) {
                                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.width(18.dp))
                            Text(stringResource(text), style = MaterialTheme.typography.bodyMedium,
                                 color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { haptics.medium(); prefs.krankmeldungInfoShown = true; onContinue() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Outlined.MedicalServices, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.krankmeldung_button), fontWeight = FontWeight.Bold)
            }
        }
    }
}

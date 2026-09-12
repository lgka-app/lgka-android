package com.lgka

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh

/** Card corner radius shared by every list card (DESIGN_GUIDELINES.md §1.3). */
val CardShape = RoundedCornerShape(16.dp)

/** 44dp tinted icon square used across the home cards (decorative). */
@Composable
fun IconTile(icon: ImageVector, alpha: Float = 0.12f) {
    Box(
        Modifier.size(44.dp).background(
            MaterialTheme.colorScheme.primary.copy(alpha = alpha), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}

/** Tappable list card — the whole card is one touch target with Role.Button. */
@Composable
fun HomeCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val row: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, content = content,
        )
    }
    if (onClick != null) {
        Card(onClick = onClick, enabled = enabled, shape = CardShape, modifier = modifier) { row() }
    } else {
        Card(shape = CardShape, modifier = modifier) { row() }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        title, fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 12.dp).semantics { heading() },
    )
}

/** Placeholder row while data loads; announced once to TalkBack. */
@Composable
fun SkeletonRow() {
    val label = stringResource(R.string.loading)
    Card(shape = CardShape, modifier = Modifier.semantics { contentDescription = label }) {
        Row(Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(12.dp)))
            Spacer(Modifier.width(14.dp))
            Column {
                Box(Modifier.size(140.dp, 14.dp).background(
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(4.dp)))
                Spacer(Modifier.height(6.dp))
                Box(Modifier.size(90.dp, 11.dp).background(
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), RoundedCornerShape(4.dp)))
            }
        }
    }
}

/** Retry icon button with a 48dp target and a label for TalkBack. */
@Composable
fun RetryButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Filled.Refresh, stringResource(R.string.a11y_retry),
             tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}

/** Material 3 Expressive loading indicator. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Loading(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.loading)
    LoadingIndicator(modifier = modifier.semantics { contentDescription = label })
}

/** Centered "server connection failed" state with retry. */
@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        androidx.compose.material3.Button(onClick = onRetry) { Text(stringResource(R.string.try_again)) }
    }
}

@Composable
fun cardColors() = CardDefaults.cardColors()

package com.lgka

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import lgka.api.ScheduleItem
import lgka.plan.KurswahlParser
import lgka.plan.StufenplanParser
import lgka.plan.TextBox
import java.io.File

/** Home card for J11 / J12: create the personal plan, or open it as its PDF; long press: edit, rescan, delete. */
@Composable
fun CustomPlanCard(onOpen: (PdfRequest) -> Unit, onCustomPlan: (edit: Boolean) -> Unit) {
    val vm = LocalHomeViewModel.current
    val store = LocalContainer.current.customPlans
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val saved = store.saved
    var menu by remember { mutableStateOf(false) }
    val myTitle = stringResource(R.string.custom_home_title)

    Box {
        HomeCard(
            onClick = {
                if (saved == null) {
                    onCustomPlan(false)
                } else scope.launch {
                    val (current, rebuilt) = CustomPlanSource.refreshed(saved, vm, store)
                    // a new Stufenplan or Halbjahr left courses it could not place: check them before the PDF
                    if (rebuilt && current.plan.checks.issues.isNotEmpty()) {
                        onCustomPlan(true)
                        return@launch
                    }
                    try {
                        onOpen(PdfRequest(CustomPlanSource.pdfFile(context, current.plan), myTitle, null, shareName = myTitle))
                    } catch (e: Exception) {
                        Log.w("CustomPlanCard", "PDF not rendered", e)
                        haptics.error()
                    }
                }
            },
            onLongClick = if (saved != null) ({ menu = true }) else null,
            modifier = Modifier.testTag("home.customPlan")) {
            IconTile(if (saved == null) Icons.Outlined.DocumentScanner else Icons.Outlined.CalendarMonth)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(if (saved == null) stringResource(R.string.custom_home_create) else myTitle, fontWeight = FontWeight.SemiBold)
                Text(saved?.let { stringResource(R.string.custom_home_subtitle, it.plan.stufe, it.plan.checks.totalHours) }
                    ?: stringResource(R.string.custom_home_create_subtitle),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.plan_edit_courses)) }, leadingIcon = { Icon(Icons.Outlined.Checklist, null) },
                onClick = { haptics.light(); menu = false; onCustomPlan(true) })
            DropdownMenuItem(text = { Text(stringResource(R.string.plan_rescan)) }, leadingIcon = { Icon(Icons.Outlined.DocumentScanner, null) },
                onClick = { haptics.light(); menu = false; onCustomPlan(false) })
            DropdownMenuItem(text = { Text(stringResource(R.string.plan_delete), color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                onClick = { haptics.medium(); menu = false; store.delete() })
        }
    }
}

/**
 * Debug builds only: open the custom plan screens without a real scan, for emulator checks, from JSON
 * files readable by the app (e.g. pushed to its external files dir):
 * `--es lgka_debug_custom_review <kurswahl boxes.json> --es lgka_debug_stufenplan <stufenplan words.json>`
 * opens the review; add `--ez lgka_debug_custom_saved true` to save the plan instead;
 * `--ez lgka_debug_custom_setup true` opens the setup screen. A real scan keeps its recognised text in
 * the external files dir (last-scan.json) for replaying.
 */
object DebugCustomPlan {
    var openSetup = false
    private var draft: CustomPlanDraft? = null

    fun takeDraft(): CustomPlanDraft? = draft.also { draft = null }

    fun seed(container: AppContainer, intent: Intent?) {
        if (!BuildConfig.DEBUG || intent == null) return
        if (intent.getBooleanExtra("lgka_debug_custom_setup", false)) openSetup = true
        val kurswahlPath = intent.getStringExtra("lgka_debug_custom_review") ?: return
        val wordsPath = intent.getStringExtra("lgka_debug_stufenplan") ?: return
        try {
            val boxes = ListSerializer(TextBox.serializer())
            val kurswahl = KurswahlParser.parse(PlanJson.decodeFromString(boxes, File(kurswahlPath).readText()))
            val stufenplan = StufenplanParser.parse(PlanJson.decodeFromString(boxes, File(wordsPath).readText()))
            val item = ScheduleItem(title = "Stundenpläne - 2026/2027 - 1.HJ - J11", halbjahr = "1. Halbjahr", gradeLevel = "J11", available = true)
            val next = CustomPlanDraft.fromScan(kurswahl, CustomPlanSource.Loaded(stufenplan, item))
            if (intent.getBooleanExtra("lgka_debug_custom_saved", false)) {
                container.customPlans.save(next.saved)
            } else {
                draft = next
                openSetup = true
            }
        } catch (e: Exception) {
            Log.w("DebugCustomPlan", "seed failed", e)
        }
    }

    /** Keeps the last scan (recognised text, parsed sheet) to replay it on a computer. */
    fun keep(context: Context, scan: KurswahlScanner.Result) {
        if (!BuildConfig.DEBUG) return
        try {
            val dir = context.getExternalFilesDir(null) ?: return
            File(dir, "last-scan.json").writeText(PlanJson.encodeToString(KurswahlScanner.Result.serializer(), scan))
        } catch (e: Exception) {
            Log.w("DebugCustomPlan", "scan not kept", e)
        }
    }
}

package com.lgka

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import lgka.plan.CustomPlan
import lgka.plan.Kurswahl
import java.io.File

/**
 * The saved custom plan with what it was built from, so a newer Stufenplan (or the next Halbjahr)
 * rebuilds it without scanning again. The Kurswahl holds no SchID or birth date.
 */
@Serializable
data class SavedCustomPlan(
    val plan: CustomPlan,
    val kurswahl: Kurswahl? = null,
    /** Title of the Stufenplan link the lessons come from ("Stundenpläne - 2026/2027 - 1.HJ - J11"). */
    val planTitle: String? = null,
)

val PlanJson = Json { ignoreUnknownKeys = true }

/** One custom plan per device, as JSON in the app's files dir. Removed on sign-out. */
class CustomPlanStore(private val file: File) {
    var saved by mutableStateOf(load())
        private set

    /** Set after scanning or editing: Home opens this plan's PDF once it is back on screen. */
    var openRequest by mutableStateOf<SavedCustomPlan?>(null)

    /**
     * The plan's PDF while it is open on Home. Kept here, not in Home's composition: Home is composed
     * again once the back transition from the scan ends, and a PDF held in `remember` would close
     * right after it appeared.
     */
    var shownPdf by mutableStateOf<PdfRequest?>(null)

    private fun load(): SavedCustomPlan? = try {
        if (file.exists()) PlanJson.decodeFromString(SavedCustomPlan.serializer(), file.readText()) else null
    } catch (e: Exception) {
        Log.w(TAG, "custom plan unreadable, starting over", e)
        null
    }

    fun save(value: SavedCustomPlan) {
        try {
            file.parentFile?.mkdirs()
            val temp = File(file.path + ".tmp")
            temp.writeText(PlanJson.encodeToString(SavedCustomPlan.serializer(), value))
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
            saved = value
        } catch (e: Exception) {
            Log.w(TAG, "custom plan not saved", e)
        }
    }

    fun delete() {
        file.delete()
        saved = null
        openRequest = null
        shownPdf = null
    }

    private companion object {
        const val TAG = "CustomPlanStore"
    }
}

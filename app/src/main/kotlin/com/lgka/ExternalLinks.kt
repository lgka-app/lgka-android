package com.lgka

import android.content.Intent
import androidx.core.net.toUri

const val KRANKMELDUNG_URL = "https://drkrankmeldung.lgka-online.de"

/** The Krankmeldung form is the one page that opens in the user's real browser. */
fun openKrankmeldungForm(context: android.content.Context) = openExternally(context, KRANKMELDUNG_URL)

/** Opens a page in the user's own browser (Open-Meteo attribution, Krankmeldung form). */
fun openExternally(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
}

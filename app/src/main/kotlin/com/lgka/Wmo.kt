package com.lgka

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Dehaze
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector

/// WMO -> Material icon mapping (UI layer).
object WmoIcons {
    fun icon(code: Int, isDay: Boolean): ImageVector = when (code) {
        0, 1 -> if (isDay) Icons.Outlined.WbSunny else Icons.Outlined.NightsStay
        2 -> Icons.Outlined.WbCloudy
        3 -> Icons.Outlined.Cloud
        45, 48 -> Icons.Outlined.Dehaze
        in 51..67, in 80..82 -> Icons.Outlined.WaterDrop
        in 71..77, 85, 86 -> Icons.Outlined.AcUnit
        95, 96, 99 -> Icons.Outlined.Thunderstorm
        else -> Icons.Outlined.Cloud
    }
}

/** WMO weather code -> localized description resource. */
fun wmoRes(code: Int): Int = when (code) {
    0 -> R.string.wmo0; 1 -> R.string.wmo1; 2 -> R.string.wmo2; 3 -> R.string.wmo3
    45 -> R.string.wmo45; 48 -> R.string.wmo48
    51 -> R.string.wmo51; 53 -> R.string.wmo53; 55 -> R.string.wmo55; 56 -> R.string.wmo56; 57 -> R.string.wmo57
    61 -> R.string.wmo61; 63 -> R.string.wmo63; 65 -> R.string.wmo65; 66 -> R.string.wmo66; 67 -> R.string.wmo67
    71 -> R.string.wmo71; 73 -> R.string.wmo73; 75 -> R.string.wmo75; 77 -> R.string.wmo77
    80 -> R.string.wmo80; 81 -> R.string.wmo81; 82 -> R.string.wmo82; 85 -> R.string.wmo85; 86 -> R.string.wmo86
    95 -> R.string.wmo95; 96 -> R.string.wmo96; 99 -> R.string.wmo99
    else -> R.string.wmo_unknown
}

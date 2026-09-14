package lgka.plan

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** A point in a camera frame, 0…1, origin top-left. */
data class ScanPoint(val x: Double, val y: Double) {
    fun distanceTo(other: ScanPoint): Double = hypot(x - other.x, y - other.y)
}

/** The detected sheet: four corners in reading order. */
data class ScanQuad(val topLeft: ScanPoint, val topRight: ScanPoint, val bottomRight: ScanPoint, val bottomLeft: ScanPoint) {
    val corners: List<ScanPoint> get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    /** Share of the frame the sheet covers (shoelace formula). */
    val area: Double
        get() {
            val c = corners
            var sum = 0.0
            for (i in c.indices) {
                val a = c[i]
                val b = c[(i + 1) % c.size]
                sum += a.x * b.y - b.x * a.y
            }
            return abs(sum) / 2
        }

    /** Shorter over longer of each pair of opposite sides; 1 for a sheet seen straight on. */
    val squareness: Double
        get() {
            val top = topLeft.distanceTo(topRight)
            val bottom = bottomLeft.distanceTo(bottomRight)
            val left = topLeft.distanceTo(bottomLeft)
            val right = topRight.distanceTo(bottomRight)
            if (max(top, bottom) <= 0 || max(left, right) <= 0) return 0.0
            return min(min(top, bottom) / max(top, bottom), min(left, right) / max(left, right))
        }

    /** A corner lies on (or past) the frame edge: the sheet is cut off. */
    fun touchesEdge(margin: Double): Boolean = corners.any { it.x < margin || it.y < margin || it.x > 1 - margin || it.y > 1 - margin }

    /** Largest corner movement from another detection. */
    fun jitterFrom(other: ScanQuad): Double = corners.zip(other.corners).maxOf { (a, b) -> a.distanceTo(b) }
}

/** One analysed camera frame. */
data class ScanFrame(
    val quad: ScanQuad?,
    /** Mean brightness 0…1 (inside the sheet when one is found). */
    val luma: Double,
    /** Share of clipped, near-white pixels on the sheet. */
    val glare: Double,
    /** Angle between the phone's back and straight down, degrees. */
    val tilt: Double,
    /** Rotation rate plus user acceleration. */
    val motion: Double,
    /** Corner movement since the previous frame (0…1 of the frame). */
    val jitter: Double,
    /** Seconds, monotonic. */
    val time: Double,
    /** Torch level 0…1 while the frame was taken (0 = off). */
    val torch: Double = 0.0,
)

/**
 * Decides the torch on its own: on after the scene stayed too dark for a moment, brighter while it
 * is still too dark, dimmer when it glares on the paper. Once on it stays on for the rest of the
 * session, so the picture never flickers between lit and unlit frames.
 */
class TorchPolicy {
    companion object {
        /** Dark enough for the torch: below what the guidance accepts. */
        val DARK_LUMA = ScanGuidance.MIN_LUMA
        /** How long it has to stay dark before the torch comes on. */
        const val DARK_DELAY = 0.5
        const val START_LEVEL = 0.5
        const val MIN_LEVEL = 0.2
        const val MAX_LEVEL = 1.0
        /** Pause between two level changes, so the exposure can settle. */
        const val ADJUST_INTERVAL = 0.6
    }

    /** 0 = off. */
    var level = 0.0
        private set
    private var darkSince: Double? = null
    private var lastChange = 0.0

    val isOn: Boolean get() = level > 0

    /** The torch level for this frame. */
    fun update(luma: Double, glare: Double, time: Double): Double {
        if (!isOn) {
            if (luma < DARK_LUMA) {
                val since = darkSince ?: time
                darkSince = since
                if (time - since >= DARK_DELAY) {
                    level = START_LEVEL
                    lastChange = time
                }
            } else {
                darkSince = null
            }
            return level
        }
        if (time - lastChange < ADJUST_INTERVAL) return level
        if (glare > ScanGuidance.MAX_GLARE && level > MIN_LEVEL) {
            level = max(MIN_LEVEL, level - 0.2)
            lastChange = time
        } else if (luma < DARK_LUMA && level < MAX_LEVEL) {
            level = min(MAX_LEVEL, level + 0.25)
            lastChange = time
        }
        return level
    }
}

/** What the user should do next, most important first. */
enum class ScanHint { NO_DOCUMENT, TOO_DARK, MOVE_CLOSER, MOVE_BACK, HOLD_PARALLEL, GLARE, HOLD_STILL, READY }

/**
 * Turns analysed frames into one calm instruction and decides when to take the photo.
 *
 * Thresholds come from what the Kurswahlprotokoll needs to be readable: the bracketed course numbers
 * are ~2 mm tall, so the sheet has to fill half the frame; a tilt beyond ~12° makes the table rows
 * slope; a few percent of clipped white hides whole cells.
 */
class ScanGuidance {
    companion object {
        const val MIN_LUMA = 0.22
        /** Half the frame: at A4 that puts the ~2 mm course numbers at a size text recognition reads reliably. */
        const val MIN_AREA = 0.50
        const val EDGE_MARGIN = 0.012
        const val MAX_TILT = 12.0
        const val MIN_SQUARENESS = 0.72
        const val MAX_GLARE = 0.04
        const val MAX_MOTION = 0.35
        const val MAX_JITTER = 0.02
        /** How long "ready" has to hold before the photo is taken. */
        const val READY_DURATION = 0.8
        /** How long a new hint has to hold before it replaces the shown one (no flicker). */
        const val HINT_DELAY = 0.3

        /** With the torch on the sheet itself is lit even when the frame average stays a little lower. */
        fun requiredLuma(torch: Double): Double = if (torch > 0) MIN_LUMA * 0.8 else MIN_LUMA

        /** The instruction for a single frame, without any smoothing. */
        fun hint(frame: ScanFrame): ScanHint {
            val dark = frame.luma < requiredLuma(frame.torch)
            val quad = frame.quad ?: return if (dark) ScanHint.TOO_DARK else ScanHint.NO_DOCUMENT
            return when {
                dark -> ScanHint.TOO_DARK
                quad.touchesEdge(EDGE_MARGIN) -> ScanHint.MOVE_BACK
                quad.area < MIN_AREA -> ScanHint.MOVE_CLOSER
                frame.tilt > MAX_TILT || quad.squareness < MIN_SQUARENESS -> ScanHint.HOLD_PARALLEL
                frame.glare > MAX_GLARE -> ScanHint.GLARE
                frame.motion > MAX_MOTION || frame.jitter > MAX_JITTER -> ScanHint.HOLD_STILL
                else -> ScanHint.READY
            }
        }
    }

    /** [progress] 0…1 while "ready" stabilises; [capture] true exactly once, when "ready" held long enough. */
    data class State(val hint: ScanHint, val progress: Double, val capture: Boolean)

    private var shown = ScanHint.NO_DOCUMENT
    private var pending: ScanHint? = null
    private var pendingSince = 0.0
    private var readySince: Double? = null
    private var fired = false

    fun update(frame: ScanFrame): State {
        val raw = hint(frame)

        // "ready" is never delayed (the progress ring shows it), other hints wait a moment
        if (raw == shown || raw == ScanHint.READY || shown == ScanHint.READY) {
            shown = raw
            pending = null
        } else if (pending != raw) {
            pending = raw
            pendingSince = frame.time
        } else if (frame.time - pendingSince >= HINT_DELAY) {
            shown = raw
            pending = null
        }

        if (raw != ScanHint.READY) {
            readySince = null
            fired = false
            return State(shown, 0.0, false)
        }
        val since = readySince ?: frame.time
        readySince = since
        val progress = min(1.0, (frame.time - since) / READY_DURATION)
        val capture = progress >= 1 && !fired
        if (capture) fired = true
        return State(ScanHint.READY, progress, capture)
    }

    /** Starts over, e.g. after a photo was taken. */
    fun reset() {
        shown = ScanHint.NO_DOCUMENT
        pending = null
        pendingSince = 0.0
        readySince = null
        fired = false
    }
}

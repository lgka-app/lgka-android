package lgka.plan

import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScanGuidanceTest {
    /** A straight-on sheet covering ~56 % of the frame. */
    private val good = ScanQuad(ScanPoint(0.12, 0.12), ScanPoint(0.88, 0.12), ScanPoint(0.88, 0.86), ScanPoint(0.12, 0.86))

    private fun frame(quad: ScanQuad? = good, luma: Double = 0.6, glare: Double = 0.0, tilt: Double = 3.0,
                      motion: Double = 0.05, jitter: Double = 0.002, time: Double = 0.0, torch: Double = 0.0) =
        ScanFrame(quad, luma, glare, tilt, motion, jitter, time, torch)

    @Test
    fun geometry() {
        assertTrue(abs(good.area - 0.5624) < 0.001)
        assertEquals(1.0, good.squareness)
        assertFalse(good.touchesEdge(ScanGuidance.EDGE_MARGIN))
        val cut = ScanQuad(ScanPoint(0.0, 0.1), ScanPoint(0.9, 0.1), ScanPoint(0.9, 0.9), ScanPoint(0.0, 0.9))
        assertTrue(cut.touchesEdge(ScanGuidance.EDGE_MARGIN))
    }

    @Test
    fun eachHintInPriorityOrder() {
        assertEquals(ScanHint.NO_DOCUMENT, ScanGuidance.hint(frame(quad = null)))
        assertEquals(ScanHint.TOO_DARK, ScanGuidance.hint(frame(quad = null, luma = 0.1)))
        assertEquals(ScanHint.TOO_DARK, ScanGuidance.hint(frame(luma = 0.1, glare = 0.5, tilt = 40.0, motion = 3.0)))
        val small = ScanQuad(ScanPoint(0.3, 0.3), ScanPoint(0.7, 0.3), ScanPoint(0.7, 0.7), ScanPoint(0.3, 0.7))
        assertEquals(ScanHint.MOVE_CLOSER, ScanGuidance.hint(frame(quad = small, tilt = 40.0)))
        val cut = ScanQuad(ScanPoint(0.005, 0.1), ScanPoint(0.9, 0.1), ScanPoint(0.9, 0.9), ScanPoint(0.005, 0.9))
        assertEquals(ScanHint.MOVE_BACK, ScanGuidance.hint(frame(quad = cut, tilt = 40.0)))
        assertEquals(ScanHint.HOLD_PARALLEL, ScanGuidance.hint(frame(glare = 0.5, tilt = 20.0)))
        val trapezoid = ScanQuad(ScanPoint(0.3, 0.1), ScanPoint(0.7, 0.1), ScanPoint(0.95, 0.9), ScanPoint(0.05, 0.9))
        assertEquals(ScanHint.HOLD_PARALLEL, ScanGuidance.hint(frame(quad = trapezoid)))
        assertEquals(ScanHint.GLARE, ScanGuidance.hint(frame(glare = 0.1, motion = 3.0)))
        assertEquals(ScanHint.HOLD_STILL, ScanGuidance.hint(frame(motion = 1.0)))
        assertEquals(ScanHint.HOLD_STILL, ScanGuidance.hint(frame(jitter = 0.05)))
        assertEquals(ScanHint.READY, ScanGuidance.hint(frame()))
    }

    @Test
    fun thresholdsAreInclusiveOfGoodValues() {
        assertEquals(ScanHint.READY, ScanGuidance.hint(frame(luma = ScanGuidance.MIN_LUMA)))
        assertEquals(ScanHint.READY, ScanGuidance.hint(frame(tilt = ScanGuidance.MAX_TILT)))
        assertEquals(ScanHint.READY, ScanGuidance.hint(frame(glare = ScanGuidance.MAX_GLARE)))
        assertEquals(ScanHint.READY, ScanGuidance.hint(frame(motion = ScanGuidance.MAX_MOTION)))
    }

    @Test
    fun captureFiresOnceAfterReadyHolds() {
        val guidance = ScanGuidance()
        var captures = 0
        var lastProgress = 0.0
        for (step in 0..12) {
            val state = guidance.update(frame(time = step * 0.1))
            assertEquals(ScanHint.READY, state.hint)
            assertTrue(state.progress >= lastProgress)
            lastProgress = state.progress
            if (state.capture) {
                captures++
                assertTrue(step * 0.1 >= ScanGuidance.READY_DURATION - 1e-9)
            }
        }
        assertEquals(1, captures)
        assertEquals(1.0, lastProgress)
    }

    @Test
    fun movementRestartsTheTimer() {
        val guidance = ScanGuidance()
        guidance.update(frame(time = 0.0))
        assertTrue(guidance.update(frame(time = 0.6)).progress > 0.7)
        val shaken = guidance.update(frame(motion = 2.0, time = 0.7))
        assertEquals(0.0, shaken.progress)
        assertFalse(shaken.capture)
        assertEquals(0.0, guidance.update(frame(time = 0.8)).progress)
        assertFalse(guidance.update(frame(time = 1.5)).capture)
        assertTrue(guidance.update(frame(time = 1.7)).capture)
    }

    @Test
    fun hintsDoNotFlicker() {
        val guidance = ScanGuidance()
        assertEquals(ScanHint.NO_DOCUMENT, guidance.update(frame(quad = null, time = 0.0)).hint)
        assertEquals(ScanHint.NO_DOCUMENT, guidance.update(frame(quad = null, luma = 0.1, time = 0.1)).hint)
        assertEquals(ScanHint.NO_DOCUMENT, guidance.update(frame(quad = null, time = 0.2)).hint)
        guidance.update(frame(quad = null, luma = 0.1, time = 0.3))
        assertEquals(ScanHint.NO_DOCUMENT, guidance.update(frame(quad = null, luma = 0.1, time = 0.5)).hint)
        assertEquals(ScanHint.TOO_DARK, guidance.update(frame(quad = null, luma = 0.1, time = 0.65)).hint)
        assertEquals(ScanHint.READY, guidance.update(frame(time = 0.7)).hint)
    }

    @Test
    fun torchLightsTheSheetEnough() {
        val dim = ScanGuidance.MIN_LUMA * 0.85
        assertEquals(ScanHint.TOO_DARK, ScanGuidance.hint(frame(luma = dim)))
        assertEquals(ScanHint.READY, ScanGuidance.hint(frame(luma = dim, torch = 0.5)))
        assertEquals(ScanHint.TOO_DARK, ScanGuidance.hint(frame(luma = 0.05, torch = 1.0)))
    }

    @Test
    fun torchComesOnAfterAMomentOfDarkness() {
        val torch = TorchPolicy()
        assertEquals(0.0, torch.update(0.1, 0.0, 0.0))
        assertEquals(0.0, torch.update(0.1, 0.0, 0.3))
        assertEquals(0.0, torch.update(0.6, 0.0, 0.4))
        assertEquals(0.0, torch.update(0.1, 0.0, 0.5))
        assertEquals(0.0, torch.update(0.1, 0.0, 0.9))
        assertEquals(TorchPolicy.START_LEVEL, torch.update(0.1, 0.0, 1.0))
    }

    @Test
    fun torchStaysOnAndAdjusts() {
        val torch = TorchPolicy()
        torch.update(0.1, 0.0, 0.0)
        torch.update(0.1, 0.0, 0.5)
        assertTrue(torch.isOn)
        assertEquals(TorchPolicy.START_LEVEL, torch.update(0.1, 0.0, 0.8))
        assertEquals(0.75, torch.update(0.1, 0.0, 1.2))
        assertEquals(0.75, torch.update(0.7, 0.0, 5.0))
        assertTrue(abs(torch.update(0.7, 0.2, 6.0) - 0.55) < 1e-9)
        for (step in 1..10) torch.update(0.7, 0.2, 6.0 + step)
        assertEquals(TorchPolicy.MIN_LEVEL, torch.level)
    }

    @Test
    fun resetStartsOver() {
        val guidance = ScanGuidance()
        for (step in 0..9) guidance.update(frame(time = step * 0.1))
        guidance.reset()
        val state = guidance.update(frame(time = 2.0))
        assertEquals(0.0, state.progress)
        assertFalse(state.capture)
    }
}

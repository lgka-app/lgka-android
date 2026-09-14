package lgka.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignInTest {
    private val pair = Login("user", "pass")

    @Test
    fun verifiedPairIsCommittedOnceAfterTheAnimation() = runTest {
        val events = mutableListOf<String>()
        val ok = verifyAndSignIn(pair,
            check = { events += "check"; true },
            celebrate = { events += "celebrate" },
            commit = { events += "commit:${it.user}"; true })
        assertTrue(ok)
        assertEquals(listOf("check", "celebrate", "commit:user"), events)
    }

    @Test
    fun rejectedOrFailingCheckCommitsNothing() = runTest {
        var commits = 0
        assertFalse(verifyAndSignIn(pair, check = { false }, celebrate = {}, commit = { commits++; true }))
        assertFalse(verifyAndSignIn(pair, check = { throw IOException("offline") }, celebrate = {}, commit = { commits++; true }))
        assertEquals(0, commits)
    }

    @Test
    fun refusedWriteIsAFailure() = runTest {
        assertFalse(verifyAndSignIn(pair, check = { true }, celebrate = {}, commit = { false }))
    }

    @Test
    fun cancellationWhileVerifyingIsRethrownAndCommitsNothing() = runTest {
        var commits = 0
        var result: Boolean? = null
        val checking = CompletableDeferred<Unit>()
        val job = launch {
            result = verifyAndSignIn(pair,
                check = { checking.complete(Unit); awaitCancellation() },
                celebrate = {},
                commit = { commits++; true })
        }
        checking.await()
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(null, result) // no false "login failed" result
        assertEquals(0, commits)
    }

    @Test
    fun leavingDuringTheAnimationStillCommitsTheVerifiedPair() = runTest {
        var commits = 0
        val celebrating = CompletableDeferred<Unit>()
        val job = launch {
            verifyAndSignIn(pair,
                check = { true },
                celebrate = { celebrating.complete(Unit); awaitCancellation() },
                commit = { commits++; true })
        }
        celebrating.await()
        job.cancel(CancellationException("screen left"))
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(1, commits)
    }
}

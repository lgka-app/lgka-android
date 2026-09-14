package lgka.api

import kotlin.coroutines.cancellation.CancellationException

/**
 * The login form's flow, AuthScreen.swift parity: verify the pair with the API, play the
 * success [celebrate] animation, then [commit] it — saving the credentials and flipping the
 * signed-in flag in one step. Nothing is stored before verification succeeds, and a verified
 * pair is committed even when the screen goes away mid-animation (the iOS task outlives its
 * view), so a saved login and the signed-in flag never disagree.
 *
 * Returns false for a rejected pair, a network/server error or a refused write. Cancellation
 * while verifying is not a failure: it is rethrown with nothing stored.
 */
suspend fun verifyAndSignIn(
    login: Login,
    check: suspend (Login) -> Boolean,
    celebrate: suspend () -> Unit,
    commit: (Login) -> Boolean,
): Boolean {
    val verified = try {
        check(login)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false // offline, 403/429/5xx
    }
    if (!verified) return false
    var committed = false
    try {
        celebrate()
    } finally {
        // commit is not suspending, so it still runs when celebrate() was cancelled
        committed = commit(login)
    }
    return committed
}

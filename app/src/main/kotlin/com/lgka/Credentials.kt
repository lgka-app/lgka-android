package com.lgka

import android.content.Context
import android.util.Base64
import androidx.core.content.edit

/**
 * The school website's read-only HTTP basic-auth credentials, entered by the
 * user at login, verified against the server and kept in a private
 * SharedPreferences file that is excluded from backups (see
 * res/xml/backup_rules.xml). Nothing in the source tree contains a password.
 */
class Credentials(context: Context) {
    private val sp = context.getSharedPreferences("lgka-auth", Context.MODE_PRIVATE)

    data class Pair(val user: String, val password: String) {
        val authorizationHeader: String
            get() = "Basic " + Base64.encodeToString("$user:$password".toByteArray(), Base64.NO_WRAP)
    }

    fun load(): Pair? {
        val user = sp.getString("user", null) ?: return null
        val password = sp.getString("password", null) ?: return null
        return Pair(user, password)
    }

    fun save(pair: Pair) = sp.edit { putString("user", pair.user); putString("password", pair.password) }

    fun clear() = sp.edit { clear() }
}

package com.agon.app.data.youtube

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

private val Context.ytAuthStore by preferencesDataStore(name = "redline_yt_auth")

/**
 * Google account sign-in for YouTube Music using the public device-authorization
 * flow ("enter this code at google.com/device"). No YouTube Data API key is used:
 * the app poses as the embedded Android YouTube client, exactly like the open-source
 * clients NewPipe, yt-dlp and muse/Retune do.
 *
 * Only the OAuth access + refresh tokens are stored locally (in DataStore); no
 * username or password ever passes through the app.
 */
class YouTubeAuth(private val context: Context) {

    private companion object {
        const val DEVICE_CODE_URL = "https://www.youtube.com/o/oauth2/device/code"
        const val TOKEN_URL = "https://oauth2.googleapis.com/token"

        // Public credentials of the YouTube Android TV app, embedded in every Android
        // device and used by all open-source YouTube clients for the login flow.
        const val CLIENT_ID = "861556708454-d6dlm3lh05idd8npek18k6be8ba3oc68.apps.googleusercontent.com"
        const val CLIENT_SECRET = "SboVhoG9s0rNafixCSGGKXAT"
        const val SCOPE = "https://www.googleapis.com/auth/youtube"

        const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36"
    }

    private object K {
        val ACCESS = stringPreferencesKey("access_token")
        val REFRESH = stringPreferencesKey("refresh_token")
        val EXPIRES = longPreferencesKey("expires_at_ms")
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    data class DeviceCode(
        val deviceCode: String,
        val userCode: String,
        val verificationUrl: String,
        /** URL with the code pre-filled, so the user doesn't have to type it. */
        val verificationUrlComplete: String,
        val expiresInSec: Long,
        val intervalSec: Long,
    )

    suspend fun isSignedIn(): Boolean = withContext(Dispatchers.IO) {
        context.ytAuthStore.data.first()[K.REFRESH] != null
    }

    /** Starts sign-in: returns the code the user must enter at [DeviceCode.verificationUrl]. */
    suspend fun requestDeviceCode(): DeviceCode? = withContext(Dispatchers.IO) {
        val body = "client_id=${enc(CLIENT_ID)}&scope=${enc(SCOPE)}"
        val response = postForm(DEVICE_CODE_URL, body) ?: return@withContext null
        val deviceCode = response.str("device_code") ?: return@withContext null
        val userCode = response.str("user_code").orEmpty()
        val verificationUrl = response.str("verification_url").orEmpty()
        // Google sometimes returns a verification_uri_complete; when it doesn't, build the
        // equivalent so the browser opens with the code already filled in.
        val complete = response.str("verification_uri_complete")
            ?: if (verificationUrl.isNotEmpty() && userCode.isNotEmpty()) {
                "$verificationUrl?user_code=${enc(userCode)}"
            } else {
                verificationUrl
            }
        DeviceCode(
            deviceCode = deviceCode,
            userCode = userCode,
            verificationUrl = verificationUrl,
            verificationUrlComplete = complete,
            expiresInSec = response.num("expires_in") ?: 1800L,
            intervalSec = response.num("interval") ?: 5L,
        )
    }

    /** Polls the token endpoint until the user authorizes. Returns true once tokens are stored. */
    suspend fun pollForToken(deviceCode: String): Boolean = withContext(Dispatchers.IO) {
        val grant = enc("http://oauth.net/grant_type/device/1.0")
        val body = "client_id=${enc(CLIENT_ID)}&client_secret=${enc(CLIENT_SECRET)}" +
            "&code=${enc(deviceCode)}&grant_type=$grant"
        var authorized = false
        while (true) {
            val response = postForm(TOKEN_URL, body)
            if (response == null) break
            val access = response.str("access_token")
            if (access != null) {
                val refresh = response.str("refresh_token")
                if (refresh != null) {
                    val expiresIn = response.num("expires_in") ?: 3600L
                    saveTokens(access, refresh, System.currentTimeMillis() + expiresIn * 1000L)
                    authorized = true
                }
                break
            }
            when (response.str("error")) {
                "access_denied", "expired_token" -> break
                "slow_down" -> delay(5_000L)
                else -> delay(2_000L) // authorization_pending
            }
        }
        authorized
    }

    /** A valid access token, refreshing it first when expired. Null when signed out or the refresh fails. */
    suspend fun accessToken(): String? = withContext(Dispatchers.IO) {
        val prefs = context.ytAuthStore.data.first()
        val refresh = prefs[K.REFRESH] ?: return@withContext null
        val access = prefs[K.ACCESS]
        val expiresAt = prefs[K.EXPIRES] ?: 0L
        if (access != null && expiresAt - 60_000L > System.currentTimeMillis()) return@withContext access
        val body = "client_id=${enc(CLIENT_ID)}&client_secret=${enc(CLIENT_SECRET)}" +
            "&grant_type=${enc("refresh_token")}&refresh_token=${enc(refresh)}"
        val response = postForm(TOKEN_URL, body) ?: return@withContext null
        val newAccess = response.str("access_token") ?: return@withContext null
        val expiresIn = response.num("expires_in") ?: 3600L
        saveTokens(newAccess, refresh, System.currentTimeMillis() + expiresIn * 1000L)
        newAccess
    }

    suspend fun signOut() {
        context.ytAuthStore.edit { it.clear() }
    }

    private suspend fun saveTokens(access: String, refresh: String, expiresAtMs: Long) {
        context.ytAuthStore.edit {
            it[K.ACCESS] = access
            it[K.REFRESH] = refresh
            it[K.EXPIRES] = expiresAtMs
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun postForm(url: String, formBody: String): JsonObject? {
        var conn: HttpURLConnection? = null
        return try {
            val payload = formBody.toByteArray(Charsets.UTF_8)
            conn = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 20_000
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", UA)
            }
            conn.outputStream.use { it.write(payload) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: return null
            json.parseToJsonElement(text) as? JsonObject
        } catch (_: IOException) {
            null
        } catch (_: RuntimeException) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}

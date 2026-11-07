package ir.pinbom.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

fun resolveServerUrl(server: String?, original: HttpUrl): HttpUrl? {
    val base = server?.trim()?.takeIf { it.isNotEmpty() }?.toHttpUrlOrNull() ?: return null
    if (base.username.isNotEmpty() || base.password.isNotEmpty() || base.query != null || base.fragment != null) return null
    return base.newBuilder().encodedPath(base.encodedPath.trimEnd('/') + original.encodedPath)
        .encodedQuery(original.encodedQuery).fragment(original.fragment).build()
}

object ServerProbe {
    sealed interface Result {
        data object Reachable : Result
        data class Unreachable(val reason: String) : Result
    }
    fun isLoopback(url: String): Boolean = url.trim().toHttpUrlOrNull()?.host?.lowercase() in setOf("localhost", "127.0.0.1", "::1")
    suspend fun check(base: String): Result = withContext(Dispatchers.IO) {
        try {
            val url = base.trim().trimEnd('/').toHttpUrlOrNull() ?: return@withContext Result.Unreachable("Invalid server URL")
            val client = OkHttpClient.Builder().callTimeout(6, TimeUnit.SECONDS).followRedirects(false).build()
            client.newCall(Request.Builder().url(url.newBuilder().addPathSegment("health").build()).build()).execute().use {
                if (it.isSuccessful) Result.Reachable else Result.Unreachable("HTTP ${it.code}")
            }
        } catch (e: CancellationException) { throw e }
          catch (e: Exception) { Result.Unreachable(e.message ?: "Connection failed") }
    }
}

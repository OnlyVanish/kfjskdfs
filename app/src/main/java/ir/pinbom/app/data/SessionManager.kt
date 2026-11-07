package ir.pinbom.app.data

import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.pinbom.app.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Claims are display hints only, never evidence of authentication or permission. */
fun usernameFromToken(token: String): String = runCatching {
    val payload = token.split('.')[1]
    val decoded = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP)
    val obj = Json.parseToJsonElement(decoded.toString(Charsets.UTF_8)).jsonObject
    obj["usr"]?.jsonPrimitive?.contentOrNull ?: obj["username"]?.jsonPrimitive?.contentOrNull ?: ""
}.getOrDefault("")

@Singleton
class SessionManager @Inject constructor(@ApplicationContext context: Context) {
    // App-private storage; manifest disables Android backup. Never log these values.
    private val prefs = context.getSharedPreferences("pinbom_session", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    @Volatile var baseUrl: String = BuildConfig.DEFAULT_BASE_URL.trimEnd('/'); private set
    @Volatile var accessToken: String? = null; private set
    @Volatile var refreshToken: String? = null; private set
    @Volatile var username: String = ""; private set
    private var loaded = false
    private val invalidations = MutableStateFlow(0L)
    val authInvalidations = invalidations.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) { mutex.withLock {
        if (!loaded) {
            baseUrl = prefs.getString("base_url", null)?.takeIf { it.isNotBlank() } ?: BuildConfig.DEFAULT_BASE_URL.trimEnd('/')
            accessToken = prefs.getString("access_token", null)?.takeIf { it.isNotBlank() }
            refreshToken = prefs.getString("refresh_token", null)?.takeIf { it.isNotBlank() }
            username = prefs.getString("username", null)?.takeIf { it.isNotBlank() } ?: accessToken?.let(::usernameFromToken).orEmpty()
            loaded = true
        }
    } }
    fun snapshotServer(): String = baseUrl
    suspend fun setServer(url: String) = withContext(Dispatchers.IO) { mutex.withLock {
        val clean = url.trim().trimEnd('/')
        require(ir.pinbom.app.data.remote.resolveServerUrl(clean, okhttp3.HttpUrl.Builder().scheme("https").host("placeholder.invalid").build()) != null)
        // Do not carry bearer credentials to a different origin.
        if (baseUrl != clean) { accessToken = null; refreshToken = null; username = "" }
        baseUrl = clean
        check(prefs.edit().putString("base_url", clean).putString("access_token", accessToken)
            .putString("refresh_token", refreshToken).putString("username", username).commit())
    } }
    suspend fun setTokens(access: String, refresh: String, name: String = "") = withContext(Dispatchers.IO) { mutex.withLock {
        require(access.isNotBlank()) { "The server did not return an access token." }
        accessToken = access; refreshToken = refresh.takeIf { it.isNotBlank() }
        username = name.ifBlank { usernameFromToken(access) }
        loaded = true
        check(prefs.edit().putString("access_token", accessToken).putString("refresh_token", refreshToken)
            .putString("username", username).commit())
    } }
    suspend fun clear(invalidated: Boolean = false) = withContext(Dispatchers.IO) { mutex.withLock {
        accessToken = null; refreshToken = null; username = ""; loaded = true
        check(prefs.edit().remove("access_token").remove("refresh_token").remove("username").commit())
        if (invalidated) invalidations.value += 1
    } }
}

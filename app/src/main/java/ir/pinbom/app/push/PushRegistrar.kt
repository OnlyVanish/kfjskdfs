package ir.pinbom.app.push

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.pinbom.app.data.SessionManager
import ir.pinbom.app.data.extras.ExtrasRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Registers this device's FCM token with the backend (`POST /v1/devices/`) so the
 * server can push likes, comments and follows.
 *
 * The Firebase SDK is optional. The token comes from
 * `ir.pinbom.app.push.FirebasePushBridge`, a class that only exists when the
 * PINBOM_FCM_* Gradle properties are set (see app/build.gradle.kts). Without it
 * [register] quietly returns false and the app behaves exactly as before.
 */
@Singleton
class PushRegistrar @Inject constructor(
    @ApplicationContext private val context: Context,
    private val extras: ExtrasRepository,
    private val session: SessionManager,
) {
    private val prefs by lazy { context.getSharedPreferences("pinbom_push", Context.MODE_PRIVATE) }

    /** True when the device is (now or already) registered for the signed-in user. */
    suspend fun register(): Boolean {
        val user = session.username
        if (session.accessToken == null || user.isBlank()) return false
        val token = fetchToken() ?: return false
        val marker = "$user|$token"
        if (prefs.getString(KEY_REGISTERED, null) == marker) return true
        return try {
            extras.registerDevice(token, deviceName())
            prefs.edit { putString(KEY_REGISTERED, marker) }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    /** Forget the stored registration (sign-out) so the next account registers afresh. */
    fun forget() = prefs.edit { remove(KEY_REGISTERED) }

    private suspend fun fetchToken(): String? = withTimeoutOrNull(8_000) {
        suspendCancellableCoroutine { cont ->
            try {
                val bridge = Class.forName(BRIDGE)
                val method = bridge.getMethod("fetchToken", Context::class.java, kotlin.jvm.functions.Function1::class.java)
                val callback: (String?) -> Unit = { token -> if (cont.isActive) cont.resume(token) }
                method.invoke(null, context, callback)
            } catch (e: ReflectiveOperationException) {
                if (cont.isActive) cont.resume(null) // Firebase not compiled in
            } catch (e: RuntimeException) {
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    private fun deviceName(): String =
        listOf(Build.MANUFACTURER, Build.MODEL).filter { it.isNotBlank() }.joinToString(" ").take(120)

    private companion object {
        const val BRIDGE = "ir.pinbom.app.push.FirebasePushBridge"
        const val KEY_REGISTERED = "registered"
    }
}

package ir.pinbom.app.push

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import ir.pinbom.app.BuildConfig

/**
 * Compiled only when the PINBOM_FCM_* Gradle properties are set. Initialises
 * Firebase from BuildConfig (no google-services plugin needed) and hands the
 * FCM token to [PushRegistrar] through reflection.
 */
object FirebasePushBridge {
    @JvmStatic
    fun fetchToken(context: Context, onToken: (String?) -> Unit) {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(
                    context,
                    FirebaseOptions.Builder()
                        .setApiKey(BuildConfig.FCM_API_KEY)
                        .setApplicationId(BuildConfig.FCM_APP_ID)
                        .setProjectId(BuildConfig.FCM_PROJECT_ID)
                        .setGcmSenderId(BuildConfig.FCM_SENDER_ID)
                        .build(),
                )
            }
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                onToken(if (task.isSuccessful) task.result else null)
            }
        } catch (e: Exception) {
            onToken(null)
        }
    }
}

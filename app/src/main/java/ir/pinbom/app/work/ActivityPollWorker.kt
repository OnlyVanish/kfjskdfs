package ir.pinbom.app.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import ir.pinbom.app.MainActivity
import ir.pinbom.app.R
import ir.pinbom.app.data.PinBomRepository
import ir.pinbom.app.data.SessionManager
import ir.pinbom.app.FeaturePreferences

/* ────────────────────────────────────────────────────────────────────────────
 *  PinBom · activity notifications
 *  The server has no push channel, so this is the honest substitute: every
 *  ~15 minutes (the floor WorkManager allows) the unread list is fetched and
 *  anything newer than the last one we announced becomes a system notification.
 *  It is quiet when logged out, offline or when the user turned notifications
 *  off — and it never re-announces an id, so reopening the app does not replay.
 * ──────────────────────────────────────────────────────────────────────────── */

@HiltWorker
class ActivityPollWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repo: PinBomRepository,
    private val session: SessionManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        session.load()
        if (session.accessToken.isNullOrBlank()) return Result.success()
        val manager = NotificationManagerCompat.from(applicationContext)
        if (!manager.areNotificationsEnabled()) return Result.success()

        val unread = runCatching { repo.notifications().items }.getOrNull()
            ?.filter { !it.read }
            ?: return Result.retry()

        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val owner = session.username
        val lastKey = "last_id_$owner"
        val seen = prefs.getLong(lastKey, -1L)
        val freshAll = unread.filter { it.id > seen }.sortedBy { it.id }

        if (seen < 0L) {
            // First run for this account: remember the position, announce nothing.
            prefs.edit().putLong(lastKey, unread.maxOfOrNull { it.id } ?: 0L).apply()
            return Result.success()
        }
        if (freshAll.isEmpty()) return Result.success()

        // Advance the checkpoint for every new type, including types the user
        // filtered out. Re-enabling a type must not replay yesterday's activity.
        val latestId = freshAll.last().id
        val enabledTypes = FeaturePreferences(applicationContext).notificationTypes()
        val fresh = freshAll.filter { it.type.lowercase() in enabledTypes }
        if (fresh.isEmpty()) {
            prefs.edit().putLong(lastKey, latestId).apply()
            return Result.success()
        }

        ensureChannel(applicationContext)
        val tap = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val latest = fresh.last()
        val title = latest.actor?.display_name?.ifBlank { null }
            ?: latest.actor?.username?.ifBlank { null }
            ?: applicationContext.getString(R.string.notif_title_fallback)
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(latest.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(latest.body))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setNumber(fresh.size)
        if (fresh.size > 1) builder.setSubText("+${fresh.size - 1}")
        runCatching { manager.notify(NOTIFICATION_ID, builder.build()) }

        prefs.edit().putLong(lastKey, latestId).apply()
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "pinbom_activity_poll"
        private const val CHANNEL_ID = "pinbom_activity"
        private const val NOTIFICATION_ID = 0x8201
        private const val PREFS = "pinbom_activity_poll"

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = context.getString(R.string.notif_channel_desc) },
            )
        }
    }
}

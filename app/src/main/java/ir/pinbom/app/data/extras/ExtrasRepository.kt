package ir.pinbom.app.data.extras

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.pinbom.app.data.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client for the backend routes the original repository never wrapped:
 * followers / following, blocking, user / comment reports, comment edit and
 * delete, board pin remove / move, notification preferences and device tokens.
 *
 * It is deliberately self-contained (its own OkHttp client, reading the server
 * address and the access token from [SessionManager] on every call), so it adds
 * features without changing the existing repository or its Retrofit interface.
 */
@Singleton
class ExtrasRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: SessionManager,
    private val api: ir.pinbom.app.data.remote.ApiClient,
) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /* ── people ───────────────────────────────────────────────────────────── */

    /** GET /v1/users/{username} — the only way to get a numeric id from a username. */
    suspend fun profile(username: String): ProfileLite =
        get("users/${enc(username)}")

    suspend fun followers(userId: Long, cursor: String = ""): PersonPage =
        get("users/$userId/followers", cursor = cursor)

    suspend fun following(userId: Long, cursor: String = ""): PersonPage =
        get("users/$userId/following", cursor = cursor)

    suspend fun blockedUsers(cursor: String = ""): PersonPage =
        get("users/me/blocked", cursor = cursor)

    suspend fun block(userId: Long) = send("POST", "users/$userId/block", "{}")

    suspend fun unblock(userId: Long) = send("DELETE", "users/$userId/block")

    suspend fun reportUser(userId: Long, reason: ReportReason, note: String = "") =
        send("POST", "users/$userId/report", json.encodeToString(ReportBody.serializer(), ReportBody(reason.wire, note.trim())))

    /* ── comments ─────────────────────────────────────────────────────────── */

    suspend fun editComment(pinId: Long, commentId: Long, body: String): EditedComment =
        request(
            "PATCH", "pins/$pinId/comments/$commentId",
            json.encodeToString(CommentBody.serializer(), CommentBody(body.trim())),
        ) { json.decodeFromString(EditedComment.serializer(), it) }

    suspend fun deleteComment(pinId: Long, commentId: Long) =
        send("DELETE", "pins/$pinId/comments/$commentId")

    suspend fun reportComment(pinId: Long, commentId: Long, reason: ReportReason, note: String = "") =
        send(
            "POST", "pins/$pinId/comments/$commentId/report",
            json.encodeToString(ReportBody.serializer(), ReportBody(reason.wire, note.trim())),
        )

    /* ── boards ───────────────────────────────────────────────────────────── */

    suspend fun myBoards(username: String): List<BoardLite> =
        get<BoardLitePage>("users/${enc(username)}/boards").items

    suspend fun removePinFromBoard(boardId: Long, pinId: Long) =
        send("DELETE", "boards/$boardId/pins/$pinId")

    suspend fun movePin(fromBoardId: Long, pinId: Long, toBoardId: Long) =
        send(
            "POST", "boards/$fromBoardId/pins/$pinId/move",
            json.encodeToString(MovePinBody.serializer(), MovePinBody(toBoardId)),
        )

    /* ── notifications and devices ────────────────────────────────────────── */

    suspend fun notificationPrefs(): ServerNotificationPrefs = get("notifications/preferences")

    suspend fun saveNotificationPrefs(prefs: ServerNotificationPrefs): ServerNotificationPrefs =
        request(
            "PUT", "notifications/preferences",
            json.encodeToString(ServerNotificationPrefs.serializer(), prefs),
        ) { json.decodeFromString(ServerNotificationPrefs.serializer(), it) }

    suspend fun registerDevice(token: String, deviceName: String): DeviceRow =
        request(
            "POST", "devices/",
            json.encodeToString(DeviceBody.serializer(), DeviceBody(token = token, device_name = deviceName)),
        ) { json.decodeFromString(DeviceEnvelope.serializer(), it).device }

    suspend fun devices(): List<DeviceRow> =
        get<DevicePage>("devices/").items

    suspend fun removeDevice(id: Long) = send("DELETE", "devices/$id")

    /* ── plumbing ─────────────────────────────────────────────────────────── */

    private suspend inline fun <reified T> get(path: String, cursor: String = ""): T =
        request("GET", path, null, cursor) { json.decodeFromString<T>(it) }

    private suspend fun send(method: String, path: String, body: String? = null) {
        request(method, path, body) { Unit }
    }

    private suspend fun <T> request(
        method: String,
        path: String,
        body: String?,
        cursor: String = "",
        parse: (String) -> T,
    ): T {
        try {
            val text = api.request(method, path, body?.toRequestBody(jsonType),
                query = if (cursor.isBlank()) emptyMap() else mapOf("cursor" to cursor))
            return parse(text)
        } catch (error: retrofit2.HttpException) {
            throw toException(error.code(), error.response()?.errorBody()?.string().orEmpty())
        }
    }

    private fun toException(status: Int, text: String): ExtrasHttpException {
        val envelope = runCatching { json.decodeFromString(ErrorEnvelope.serializer(), text) }.getOrNull()
        val message = envelope?.error?.message?.takeIf { it.isNotBlank() }
            ?: envelope?.detail?.takeIf { it.isNotBlank() }
            ?: "HTTP $status"
        return ExtrasHttpException(status, message, envelope?.error?.code.orEmpty())
    }

    private fun enc(segment: String): String = java.net.URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
}

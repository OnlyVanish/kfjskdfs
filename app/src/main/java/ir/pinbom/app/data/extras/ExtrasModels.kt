package ir.pinbom.app.data.extras

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* ────────────────────────────────────────────────────────────────────────────
 *  PinBom · DTOs for the backend features the first client did not call.
 *  Field names mirror the Go models (backend/internal/models, repositories).
 *  Everything has a default so an older or newer server never breaks parsing.
 * ──────────────────────────────────────────────────────────────────────────── */

/** `models.SearchUser` — the compact user row used by followers / following / blocked. */
@Serializable
data class PersonRow(
    val id: Long = 0,
    val username: String = "",
    val display_name: String = "",
    val bio: String = "",
    val avatar_url: String = "",
)

@Serializable
data class PersonPage(
    val items: List<PersonRow> = emptyList(),
    val next_cursor: String = "",
)

/** `models.UserProfile` — used to turn a username into the numeric id the user routes need. */
@Serializable
data class ProfileLite(
    val id: Long = 0,
    val username: String = "",
    val display_name: String = "",
    val avatar_url: String = "",
    val followers: Int = 0,
    val following: Int = 0,
    val following_me: Boolean = false,
    val me: Boolean = false,
)

/** `models.ReportRequest`; reasons are whitelisted server-side. */
@Serializable
data class ReportBody(val reason: String, val note: String = "")

enum class ReportReason(val wire: String) {
    Spam("spam"),
    Nudity("nudity"),
    Violence("violence"),
    Copyright("copyright"),
    Other("other"),
}

@Serializable
data class CommentBody(val body: String)

/** `models.Comment` as returned by PATCH /pins/{id}/comments/{commentID}. */
@Serializable
data class EditedComment(
    val id: Long = 0,
    val body: String = "",
    val created_at: String = "",
)

@Serializable
data class MovePinBody(val target_board_id: Long)

@Serializable
data class BoardLite(
    val id: Long = 0,
    val title: String = "",
    val cover_url: String = "",
    val private: Boolean = false,
)

@Serializable
data class BoardLitePage(
    val items: List<BoardLite> = emptyList(),
    val next_cursor: String = "",
)

/** `repositories.NotificationPreferences` — server-side push switches. */
@Serializable
data class ServerNotificationPrefs(
    val push_enabled: Boolean = true,
    val likes_enabled: Boolean = true,
    val comments_enabled: Boolean = true,
    val follows_enabled: Boolean = true,
    val mentions_enabled: Boolean = true,
    val broadcasts_enabled: Boolean = true,
)

@Serializable
data class DeviceBody(
    val token: String,
    val platform: String = "android",
    val device_name: String = "",
)

@Serializable
data class DeviceRow(
    val id: Long = 0,
    val platform: String = "",
    val device_name: String = "",
    val created_at: String = "",
    val updated_at: String = "",
)

@Serializable
data class DeviceEnvelope(val device: DeviceRow = DeviceRow())

@Serializable
data class DevicePage(val items: List<DeviceRow> = emptyList())

/** The `{"error": {...}}` envelope every failing call returns. */
@Serializable
data class ErrorEnvelope(val error: ErrorBody = ErrorBody(), val detail: String = "")

@Serializable
data class ErrorBody(
    val code: String = "",
    val message: String = "",
    @SerialName("field_errors") val fieldErrors: Map<String, String> = emptyMap(),
)

/** Thrown for any non-2xx answer; [message] is the server's own sentence when it sent one. */
class ExtrasHttpException(val status: Int, override val message: String, val code: String = "") :
    RuntimeException(message) {
    val isAuth: Boolean get() = status == 401
    val isForbidden: Boolean get() = status == 403
    val isNotFound: Boolean get() = status == 404
}

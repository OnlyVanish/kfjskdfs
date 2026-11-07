@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package ir.pinbom.app.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonNames

/** Reconstructed from the existing UI constructors and repository call sites. */
@Serializable
data class Author(val username: String = "", val display_name: String = "", val avatar_url: String = "", val id: Long = 0)
@Serializable
data class Pin(
    val id: Long = 0, val title: String = "", val description: String = "",
    val source_url: String = "", val width: Int = 0, val height: Int = 0,
    val created_at: String = "", val image_url: String = "", val author: Author? = null,
    val likes: Int = 0, val saves: Int = 0, val comments: Int = 0,
    val liked: Boolean = false, val saved: Boolean = false, val tags: List<String> = emptyList(),
    val author_avatar_url: String = "", val author_id: Long = 0,
)
@Serializable
data class PinPage(val items: List<Pin> = emptyList(), val next_cursor: String = "")
@Serializable
data class Board(val id: Long = 0, val user_id: Long = 0, val title: String = "", val description: String = "",
    val cover_url: String = "", @JsonNames("private") val private_board: Boolean = false, val created_at: String = "", val can_edit: Boolean = false)
@Serializable
data class BoardPage(val items: List<Board> = emptyList(), val next_cursor: String = "")
@Serializable
data class BoardDetail(val id: Long = 0, val user_id: Long = 0, val title: String = "", val description: String = "",
    val cover_url: String = "", @JsonNames("private") val private_board: Boolean = false, val created_at: String = "", val pins: List<Pin> = emptyList(), val can_edit: Boolean = false)
@Serializable
data class UserProfile(val id: Long = 0, val username: String = "", val display_name: String = "", val bio: String = "",
    val avatar_url: String = "", val cover_url: String = "", val followers: Int = 0, val following: Int = 0,
    val pins: Int = 0, val following_me: Boolean = false, val me: Boolean = false)
@Serializable
data class SearchUser(val id: Long = 0, val username: String = "", val display_name: String = "", val bio: String = "", val avatar_url: String = "")
@Serializable
data class SearchBoard(val id: Long = 0, val title: String = "", val description: String = "", val cover_url: String = "", @JsonNames("private") val private_board: Boolean = false)
@Serializable
data class SearchUserPage(val items: List<SearchUser> = emptyList(), val next_cursor: String = "")
@Serializable
data class SearchBoardPage(val items: List<SearchBoard> = emptyList(), val next_cursor: String = "")
@Serializable
data class Comment(val id: Long = 0, val body: String = "", val created_at: String = "", val user: Author = Author())
@Serializable
data class CommentPage(val items: List<Comment> = emptyList(), val next_cursor: String = "")
@Serializable
data class Notification(val id: Long = 0, val type: String = "", val actor_id: Long = 0, val pin_id: Long = 0,
    val body: String = "", val read: Boolean = false, val created_at: String = "", val actor: Author? = null,
    val pin_title: String = "", val pin_image_url: String = "")
@Serializable
data class NotificationPage(val items: List<Notification> = emptyList(), val next_cursor: String = "", val unread: Int = 0)
@Serializable
data class InterestRecord(val id: Long = 0, val name_fa: String = "", val name_en: String = "", val slug: String = "")
@Serializable
data class InterestPage(val items: List<InterestRecord> = emptyList())
@Serializable
data class InterestIds(val ids: List<Long> = emptyList())
@Serializable
data class ActiveSession(val id: Long = 0, val device_name: String = "", val ip: String = "", val last_seen_at: String = "", val created_at: String = "", val current: Boolean = false)
@Serializable
data class SessionPage(val items: List<ActiveSession> = emptyList())
@Serializable
data class MutationResult(val liked: Boolean? = null, val saved: Boolean? = null, val ok: Boolean = true)
@Serializable
data class TokenResponse(val access_token: String = "", val refresh_token: String = "", val user: UserProfile? = null)
@Serializable
data class ApiError(val code: String = "", val message: String = "", val field_errors: Map<String, String> = emptyMap())
@Serializable
data class ApiErrorEnvelope(val error: ApiError = ApiError(), val detail: String = "")

@Serializable
data class StringPage(val items: List<String> = emptyList(), val next_cursor: String = "")
@Serializable
data class BoardMember(val user_id: Long = 0, val username: String = "", val display_name: String = "", val avatar_url: String = "", val role: String = "viewer", val owner: Boolean = false)
@Serializable
data class BoardMemberPage(val items: List<BoardMember> = emptyList())
@Serializable
data class BoardInvitation(val id: Long = 0, val board_id: Long = 0, val board_title: String = "", val inviter_id: Long = 0, val inviter_username: String = "", val role: String = "viewer", val status: String = "pending", val created_at: String = "")
@Serializable
data class BoardInvitationPage(val items: List<BoardInvitation> = emptyList())

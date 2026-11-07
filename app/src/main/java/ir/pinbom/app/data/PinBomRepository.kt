package ir.pinbom.app.data

import ir.pinbom.app.data.local.LocalDb
import ir.pinbom.app.data.local.PinEntity
import ir.pinbom.app.data.remote.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.room.withTransaction
import kotlinx.serialization.json.*
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.io.File
import java.net.URLEncoder
import android.os.Build

/** Real network implementation of the interfaces consumed by the supplied UI.
 * Routes not demonstrated by ExtrasRepository or existing tests are inferred;
 * see RECONSTRUCTION-NOTES.md. No mock data or fake mutation successes. */
@Singleton
class PinBomRepository @Inject constructor(private val api: ApiClient, private val session: SessionManager, private val db: LocalDb) {
    private val cacheLock = Mutex()
    private fun segment(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    private fun cursor(value: String) = mapOf("cursor" to value, "limit" to "20")
    private fun obj(vararg fields: Pair<String, JsonElement>): JsonObject = JsonObject(fields.toMap())
    private suspend fun send(method: String, path: String, value: JsonElement? = null): String = api.request(method, path, value?.let(api::body))
    private fun mutation(text: String): MutationResult = if (text.isBlank()) MutationResult() else api.decode(text)

    suspend fun feed(kind: String, after: String = ""): PinPage = api.get("feed/${segment(kind)}", cursor(after))
    suspend fun search(q: String, after: String = ""): PinPage = api.get("search/pins", cursor(after) + ("q" to q))
    suspend fun searchUsers(q: String): SearchUserPage = api.get("search/users", mapOf("q" to q))
    suspend fun searchBoards(q: String): SearchBoardPage = api.get("search/boards", mapOf("q" to q))
    suspend fun searchSuggestions(q: String): StringPage = api.get("search/suggestions", mapOf("q" to q, "limit" to "8"))
    suspend fun inviteBoard(boardId: Long, username: String, role: String) { send("POST", "boards/$boardId/invitations", obj("username" to JsonPrimitive(username), "role" to JsonPrimitive(role))) }
    suspend fun boardMembers(boardId: Long): BoardMemberPage = api.get("boards/$boardId/members")
    suspend fun boardInvitations(): BoardInvitationPage = api.get("boards/invitations")
    suspend fun respondBoardInvitation(inviteId: Long, accept: Boolean) { if (accept) send("POST", "boards/invitations/$inviteId/accept", JsonObject(emptyMap())) else send("DELETE", "boards/invitations/$inviteId") }
    suspend fun boardFeedback(pinId: Long, action: String) { send("POST", "pins/$pinId/feedback", obj("action" to JsonPrimitive(action))) }
    suspend fun muteUser(userId: Long) { send("POST", "users/$userId/mute", JsonObject(emptyMap())) }
    suspend fun similarPin(pinId: Long, cropX: Float = 0f, cropY: Float = 0f, cropW: Float = 0f, cropH: Float = 0f): PinPage = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("pin_id", pinId.toString())
            .addFormDataPart("crop_x", cropX.toString()).addFormDataPart("crop_y", cropY.toString())
            .addFormDataPart("crop_width", cropW.toString()).addFormDataPart("crop_height", cropH.toString()).build()
        api.decode<PinPage>(api.request("POST", "search/similar", body))
    }
    suspend fun similarImage(file: File): PinPage = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody("image/*".toMediaType())).build()
        api.decode<PinPage>(api.request("POST", "search/similar", body))
    }
    suspend fun pin(id: Long): Pin = api.get("pins/$id")
    suspend fun comments(id: Long, after: String = ""): CommentPage = api.get("pins/$id/comments", cursor(after))
    suspend fun related(id: Long): PinPage = api.get("pins/$id/related")
    suspend fun like(id: Long): MutationResult = mutation(send("POST", "pins/$id/like", JsonObject(emptyMap())))
    suspend fun unlike(id: Long): MutationResult = mutation(send("DELETE", "pins/$id/like"))
    suspend fun save(id: Long, board: Long? = null): MutationResult = mutation(send("POST", "pins/$id/save", buildJsonObject { if (board != null) put("board_id", board) }))
    suspend fun unsave(id: Long) { send("DELETE", "pins/$id/save") }
    suspend fun comment(id: Long, text: String) { send("POST", "pins/$id/comments", obj("body" to JsonPrimitive(text))) }
    suspend fun profile(name: String): UserProfile = api.get("users/${segment(name)}")
    suspend fun verifyCurrentSession() {
        try { api.request("GET", "users/me") }
        catch (error: retrofit2.HttpException) {
            if (error.code() == 403) session.clear(invalidated = true)
            throw error
        }
    }
    suspend fun boards(name: String, after: String = ""): BoardPage = api.get("users/${segment(name)}/boards", cursor(after))
    suspend fun userPins(name: String, after: String = ""): PinPage = api.get("users/${segment(name)}/pins", cursor(after))
    suspend fun savedPins(name: String, after: String = ""): PinPage = api.get("users/${segment(name)}/saved", cursor(after))
    suspend fun follow(id: Long) { send("POST", "users/$id/follow", JsonObject(emptyMap())) }
    suspend fun unfollow(id: Long) { send("DELETE", "users/$id/follow") }
    suspend fun tagPins(tag: String, after: String = ""): PinPage = api.get("tags/${segment(tag)}/pins", cursor(after))
    suspend fun notifications(after: String = ""): NotificationPage = api.get("notifications", cursor(after))
    suspend fun readNotifications() { send("POST", "notifications/read", JsonObject(emptyMap())) }
    suspend fun createBoard(title: String, description: String): Board = api.decode(send("POST", "boards", obj("title" to JsonPrimitive(title), "description" to JsonPrimitive(description))))
    suspend fun patchBoard(id: Long, title: String, description: String) { send("PATCH", "boards/$id", obj("title" to JsonPrimitive(title), "description" to JsonPrimitive(description))) }
    suspend fun deleteBoard(id: Long) { send("DELETE", "boards/$id") }
    suspend fun board(id: Long): BoardDetail = api.get("boards/$id")
    suspend fun reportPin(id: Long, reason: String, note: String) { send("POST", "pins/$id/report", obj("reason" to JsonPrimitive(reason), "note" to JsonPrimitive(note))) }
    suspend fun updatePin(id: Long, title: String, description: String) { send("PATCH", "pins/$id", obj("title" to JsonPrimitive(title), "description" to JsonPrimitive(description))) }
    suspend fun deletePin(id: Long) { send("DELETE", "pins/$id") }
    suspend fun patchMe(name: String, bio: String) { send("PATCH", "users/me", obj("display_name" to JsonPrimitive(name), "bio" to JsonPrimitive(bio))) }
    suspend fun deleteMe() { send("DELETE", "users/me"); session.clear(); clearLocalPinCache() }
    suspend fun interests(): InterestPage = api.get("interests")
    suspend fun myInterests(): InterestIds = api.get("users/me/interests")
    suspend fun replaceMyInterests(ids: List<Long>) { send("PUT", "users/me/interests", obj("ids" to JsonArray(ids.map(::JsonPrimitive)))) }
    suspend fun activeSessions(): SessionPage = api.get("auth/sessions")
    suspend fun revokeSession(id: Long) { send("DELETE", "auth/sessions/$id") }
    suspend fun revokeOtherSessions() { send("DELETE", "auth/sessions/others") }

    suspend fun login(identifier: String, password: String) = authenticate("auth/login", buildJsonObject {
        put("identifier", identifier); put("password", password); put("device_name", "${Build.MANUFACTURER} ${Build.MODEL}")
    })
    suspend fun register(username: String, email: String, password: String) = authenticate("auth/register", buildJsonObject {
        put("username", username); put("email", email); put("password", password); put("device_name", "${Build.MANUFACTURER} ${Build.MODEL}")
    })
    private suspend fun authenticate(path: String, fields: JsonObject) {
        val result = api.decode<TokenResponse>(api.request("POST", path, api.body(fields), authenticated = false))
        session.setTokens(result.access_token, result.refresh_token, result.user?.username.orEmpty())
        clearLocalPinCache()
    }
    suspend fun logout() {
        try {
            if (!session.accessToken.isNullOrBlank()) send("POST", "auth/logout", buildJsonObject { session.refreshToken?.let { put("refresh_token", it) } })
        } finally { session.clear(); clearLocalPinCache() }
    }
    fun cachedPins(): Flow<List<PinEntity>> = db.pins().observe()
    suspend fun cache(page: PinPage) = cacheLock.withLock {
        db.withTransaction {
            db.pins().upsert(page.items.map { pin -> PinEntity(pin.id, pin.title, pin.description, pin.width, pin.height, pin.created_at,
                pin.image_url, pin.author?.display_name.orEmpty(), pin.author_avatar_url.ifBlank { pin.author?.avatar_url.orEmpty() }) })
            db.pins().trim()
        }
    }
    suspend fun clearLocalPinCache() = cacheLock.withLock { db.pins().clear() }

    suspend fun upload(file: File, title: String, description: String, tags: String, boardId: String,
        progress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, ProgressRequestBody(file, "image/jpeg".toMediaType()) { sent, total -> progress(if (total > 0) (sent * 100 / total).toInt().coerceIn(0, 99) else 0) })
            .addFormDataPart("title", title).addFormDataPart("description", description).addFormDataPart("tags", tags)
            .apply { if (boardId.isNotBlank()) addFormDataPart("board_id", boardId) }.build()
        api.request("POST", "pins", body)
        progress(100)
    }
    suspend fun uploadProfileMedia(file: File, kind: String) = withContext(Dispatchers.IO) {
        require(kind == "avatar" || kind == "cover")
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, ProgressRequestBody(file, "image/jpeg".toMediaType()) { _, _ -> }).build()
        api.request("POST", "users/me/$kind", body)
    }
}

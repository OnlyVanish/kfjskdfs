package ir.pinbom.app.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.pinbom.app.R
import ir.pinbom.app.data.PinBomRepository
import ir.pinbom.app.data.SessionManager
import ir.pinbom.app.FeaturePreferences
import ir.pinbom.app.data.remote.ApiErrorEnvelope
import ir.pinbom.app.data.remote.Board
import ir.pinbom.app.data.remote.Comment
import ir.pinbom.app.data.remote.Notification
import ir.pinbom.app.data.remote.Pin
import ir.pinbom.app.data.remote.SearchBoard
import ir.pinbom.app.data.remote.SearchUser
import ir.pinbom.app.data.remote.BoardMember
import ir.pinbom.app.data.remote.BoardInvitation
import ir.pinbom.app.data.remote.UserProfile
import ir.pinbom.app.work.ImagePreparer
import ir.pinbom.app.work.UploadEvents
import ir.pinbom.app.work.UploadKeys
import ir.pinbom.app.work.UploadStage
import ir.pinbom.app.work.UploadWorker
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import retrofit2.HttpException

class FeedSource(
    private val repo: PinBomRepository,
    private val kind: String
) : PagingSource<String, Pin>() {
    override suspend fun load(params: LoadParams<String>): LoadResult<String, Pin> = try {
        val page = repo.feed(kind, params.key.orEmpty())
        repo.cache(page)
        LoadResult.Page(
            data = page.items,
            prevKey = null,
            nextKey = page.next_cursor.ifBlank { null }
        )
    } catch (e: Exception) {
        LoadResult.Error(e)
    }

    override fun getRefreshKey(state: PagingState<String, Pin>): String? = null
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: PinBomRepository
) : ViewModel() {
    private val kind = MutableStateFlow("home")

    /** Additive UI state: the feed chips read which feed is active. */
    val selectedKind: StateFlow<String> = kind.asStateFlow()
    val items = kind
        .flatMapLatest { selected ->
            /* prefetchDistance 40: the next page starts loading two screens before
             * the end instead of one, so fast scrolling rarely meets the spinner. */
            Pager(PagingConfig(pageSize = 20, prefetchDistance = 40, enablePlaceholders = false)) {
                FeedSource(repo, selected)
            }.flow
        }
        .cachedIn(viewModelScope)
    val cached = repo.cachedPins()

    fun setKind(value: String) {
        kind.value = value
    }
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repo: PinBomRepository
) : ViewModel() {
    val query = MutableStateFlow("")
    val type = MutableStateFlow("pins")

    val results: Flow<PagingData<Pin>> = combine(query, type) { q, selectedType -> q to selectedType }
        .debounce(350)
        .distinctUntilChanged()
        .flatMapLatest { (q, selectedType) ->
            if (q.isBlank() || selectedType != "pins") {
                kotlinx.coroutines.flow.flowOf(PagingData.empty())
            } else {
                Pager(PagingConfig(pageSize = 20, prefetchDistance = 40, enablePlaceholders = false)) {
                    object : PagingSource<String, Pin>() {
                        override suspend fun load(params: LoadParams<String>): LoadResult<String, Pin> = try {
                            val page = repo.search(q, params.key.orEmpty())
                            LoadResult.Page(
                                data = page.items,
                                prevKey = null,
                                nextKey = page.next_cursor.ifBlank { null }
                            )
                        } catch (e: Exception) {
                            LoadResult.Error(e)
                        }

                        override fun getRefreshKey(state: PagingState<String, Pin>): String? = null
                    }
                }.flow
            }
        }
        .cachedIn(viewModelScope)

    val users = MutableStateFlow<List<SearchUser>>(emptyList())
    val boards = MutableStateFlow<List<SearchBoard>>(emptyList())

    /** Discovery shown while the field is empty. Built from the trending feed,
     * so it needs no endpoint of its own. */
    val trendingTags = MutableStateFlow<List<String>>(emptyList())
    val suggestions = MutableStateFlow<List<SuggestedUser>>(emptyList())
    val liveSuggestions = MutableStateFlow<List<String>>(emptyList())

    init {
        loadDiscover()
        viewModelScope.launch {
            query.debounce(220).distinctUntilChanged().collectLatest { q ->
                liveSuggestions.value = if (q.trim().length < 2) emptyList()
                else runCatching { repo.searchSuggestions(q.trim()).items }.getOrDefault(emptyList())
            }
        }
    }

    fun loadDiscover() {
        viewModelScope.launch {
            val page = runCatching { repo.feed("trending") }.getOrNull() ?: return@launch
            trendingTags.value = page.items
                .flatMap { it.tags }
                .map { it.trim().trimStart('#') }
                .filter { it.isNotBlank() }
                .groupingBy { it.lowercase() }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(8)
                .map { it.key }
            val names = page.items
                .mapNotNull { it.author?.username }
                .filter { it.isNotBlank() }
                .distinct()
                .take(4)
            val profiles = names
                .map { name -> async { runCatching { repo.profile(name) }.getOrNull() } }
                .awaitAll()
                .filterNotNull()
                .filter { !it.me }
                .take(5)
            suggestions.value = profiles.map { SuggestedUser(it) }
        }
    }

    /** Optimistic follow from the suggestions list; rolls back on failure. */
    fun followSuggestion(userId: Long) {
        fun mark(done: Boolean) {
            suggestions.value = suggestions.value.map {
                if (it.profile.id == userId) it.copy(followed = done) else it
            }
        }
        mark(true)
        viewModelScope.launch {
            runCatching { repo.follow(userId) }.onFailure { mark(false) }
        }
    }

    /** Additive UI state: boards/people lists show a skeleton while loading. */
    val secondaryBusy = MutableStateFlow(false)

    fun setQuery(value: String) {
        query.value = value
    }

    fun setType(value: String) {
        type.value = value
        searchSecondary(query.value, value)
    }

    fun searchSecondary(q: String, selectedType: String) {
        viewModelScope.launch {
            if (q.isBlank()) {
                users.value = emptyList()
                boards.value = emptyList()
                return@launch
            }
            secondaryBusy.value = true
            if (selectedType == "users") {
                runCatching { repo.searchUsers(q) }
                    .onSuccess { users.value = it.items }
            } else if (selectedType == "boards") {
                runCatching { repo.searchBoards(q) }
                    .onSuccess { boards.value = it.items }
            }
            secondaryBusy.value = false
        }
    }
}

@HiltViewModel
class PinViewModel @Inject constructor(
    private val repo: PinBomRepository
) : ViewModel() {
    val pin = MutableStateFlow<Pin?>(null)
    val comments = MutableStateFlow<List<Comment>>(emptyList())
    val commentsCursor = MutableStateFlow("")
    val commentsLoadingMore = MutableStateFlow(false)
    val related = MutableStateFlow<List<Pin>>(emptyList())

    /* Phase 6: additive UI state only — drives the detail skeleton / error view. */
    val loading = MutableStateFlow(false)
    val failed = MutableStateFlow(false)
    private val _actionError = MutableStateFlow(0)
    val actionError: StateFlow<Int> = _actionError.asStateFlow()

    private fun recordActionFailure(error: Throwable) {
        if (error is CancellationException) throw error
        _actionError.value += 1
    }

    fun load(id: Long) {
        viewModelScope.launch {
            loading.value = true
            failed.value = false
            runCatching { repo.pin(id) }
                .onSuccess { pin.value = it }
                .onFailure { failed.value = true }
            runCatching { repo.comments(id) }.onSuccess { comments.value = it.items; commentsCursor.value = it.next_cursor }
            runCatching { repo.related(id) }.onSuccess { related.value = it.items }
            loading.value = false
        }
    }

    fun loadMoreComments() {
        val id = pin.value?.id ?: return
        val cursor = commentsCursor.value
        if (cursor.isBlank() || commentsLoadingMore.value) return
        viewModelScope.launch {
            commentsLoadingMore.value = true
            runCatching { repo.comments(id, cursor) }.onSuccess { page ->
                comments.value = (comments.value + page.items).distinctBy { it.id }
                commentsCursor.value = page.next_cursor
            }
            commentsLoadingMore.value = false
        }
    }

    fun like() {
        val id = pin.value?.id ?: return
        viewModelScope.launch {
            runCatching {
                if (pin.value?.liked == true) repo.unlike(id) else repo.like(id)
            }.onSuccess { result ->
                val old = pin.value ?: return@onSuccess
                val newLiked = result.liked ?: !old.liked
                pin.value = old.copy(
                    liked = newLiked,
                    likes = (old.likes + if (newLiked && !old.liked) 1 else if (!newLiked && old.liked) -1 else 0).coerceAtLeast(0)
                )
            }.onFailure(::recordActionFailure)
        }
    }

    fun save(board: Long? = null) {
        val id = pin.value?.id ?: return
        viewModelScope.launch {
            runCatching { repo.save(id, board) }
                .onSuccess { if (pin.value?.id == id) pin.value = pin.value?.copy(saved = true) }
                .onFailure(::recordActionFailure)
        }
    }

    fun unsave() {
        val id = pin.value?.id ?: return
        viewModelScope.launch {
            runCatching { repo.unsave(id) }
                .onSuccess { if (pin.value?.id == id) pin.value = pin.value?.copy(saved = false) }
                .onFailure(::recordActionFailure)
        }
    }

    fun comment(text: String) {
        val id = pin.value?.id ?: return
        if (text.isBlank()) return
        viewModelScope.launch {
            runCatching {
                repo.comment(id, text)
                repo.comments(id)
            }.onSuccess { comments.value = it.items }
                .onFailure(::recordActionFailure)
        }
    }
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repo: PinBomRepository
) : ViewModel() {
    val busy = MutableStateFlow(false)

    /** Server-provided text, shown exactly as it arrives. */
    val error = MutableStateFlow("")
    val failed = MutableStateFlow(false)
    val blocked = MutableStateFlow(false)
    val blockReason = MutableStateFlow("")
    private val errorJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    fun clearFeedback() {
        error.value = ""
        failed.value = false
        blocked.value = false
        blockReason.value = ""
    }

    fun login(identifier: String, password: String, done: () -> Unit) {
        viewModelScope.launch {
            busy.value = true
            clearFeedback()
            runCatching { repo.login(identifier, password) }
                .onSuccess { done() }
                .onFailure { failure ->
                    if (failure is HttpException) {
                        val raw = runCatching { failure.response()?.errorBody()?.string().orEmpty() }.getOrDefault("")
                        val payload = runCatching { errorJson.decodeFromString<ApiErrorEnvelope>(raw) }.getOrNull()
                        val message = payload?.error?.message?.ifBlank { null } ?: payload?.detail.orEmpty()
                        if (failure.code() == 403) {
                            // The current backend sends only “This account is disabled.”
                            // Keep a real reason only when the server actually supplied one.
                            blocked.value = true
                            blockReason.value = message.takeUnless {
                                it.equals("This account is disabled.", ignoreCase = true) ||
                                    it.equals("This account is disabled", ignoreCase = true) ||
                                    it.equals("Account disabled", ignoreCase = true)
                            }.orEmpty()
                        } else {
                            failed.value = true
                        }
                    } else {
                        error.value = failure.message.orEmpty()
                        failed.value = error.value.isBlank()
                    }
                }
            busy.value = false
        }
    }

    fun register(username: String, email: String, password: String, done: () -> Unit) {
        viewModelScope.launch {
            busy.value = true
            error.value = ""
            failed.value = false
            runCatching { repo.register(username, email, password) }
                .onSuccess { done() }
                .onFailure {
                    // The registration fallback is localized and specifically
                    // suggests trying another username or email.
                    error.value = if (it is HttpException) "" else it.message.orEmpty()
                    failed.value = it is HttpException || it.message.isNullOrBlank()
                }
            busy.value = false
        }
    }
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: PinBomRepository
) : ViewModel() {
    val user = MutableStateFlow<UserProfile?>(null)
    val boards = MutableStateFlow<List<Board>>(emptyList())
    val pins = MutableStateFlow<List<Pin>>(emptyList())
    val saved = MutableStateFlow<List<Pin>>(emptyList())
    val nextBoardsCursor = MutableStateFlow("")
    val nextPinsCursor = MutableStateFlow("")
    val nextSavedCursor = MutableStateFlow("")
    val loadingMore = MutableStateFlow(false)

    /* Phase 7: additive UI state only — drives the profile skeleton / retry. */
    val loading = MutableStateFlow(false)
    val failed = MutableStateFlow(false)
    val mediaBusy = MutableStateFlow(false)
    val mediaMessage = MutableStateFlow("")
    val mediaAction = MutableStateFlow("")

    fun uploadProfileMedia(uri: Uri, kind: String, target: String, refreshProfile: Boolean = true) {
        if (mediaBusy.value || (kind != "avatar" && kind != "cover")) return
        viewModelScope.launch {
            mediaBusy.value = true
            mediaMessage.value = ""
            var prepared: File? = null
            runCatching {
                prepared = withContext(Dispatchers.IO) {
                    ImagePreparer.prepare(
                        context = context,
                        uri = uri,
                        dir = File(context.cacheDir, "profile-media"),
                        name = ImagePreparer.fileNameFor("profile-${kind}-${UUID.randomUUID()}"),
                    )
                }
                repo.uploadProfileMedia(checkNotNull(prepared), kind)
            }.onSuccess {
                mediaAction.value = "saved:${System.nanoTime()}"
                if (refreshProfile) load(target)
            }.onFailure {
                mediaMessage.value = it.message.orEmpty()
                mediaAction.value = "failed:${System.nanoTime()}"
            }
            prepared?.delete()
            mediaBusy.value = false
        }
    }

    fun load(name: String) {
        if (name.isBlank()) {
            /* Nobody to ask for. Say so (the screen's existing retry state) instead of
             * returning silently and leaving the skeleton up for ever. */
            if (user.value == null) failed.value = true
            return
        }
        viewModelScope.launch {
            loading.value = true
            failed.value = false
            runCatching { repo.profile(name) }
                .onSuccess { user.value = it }
                .onFailure { if (user.value == null) failed.value = true }
            runCatching { repo.boards(name) }.onSuccess { boards.value = it.items; nextBoardsCursor.value = it.next_cursor }
            runCatching { repo.userPins(name) }.onSuccess { pins.value = it.items; nextPinsCursor.value = it.next_cursor }
            runCatching { repo.savedPins(name) }.onSuccess { saved.value = it.items; nextSavedCursor.value = it.next_cursor }
            loading.value = false
        }
    }

    fun loadMore(name: String, tabIndex: Int) {
        if (loadingMore.value || name.isBlank()) return
        val cursor = when (tabIndex) { 1 -> nextBoardsCursor.value; 2 -> nextSavedCursor.value; else -> nextPinsCursor.value }
        if (cursor.isBlank()) return
        viewModelScope.launch {
            loadingMore.value = true
            when (tabIndex) {
                1 -> runCatching { repo.boards(name, cursor) }.onSuccess { page -> boards.value = (boards.value + page.items).distinctBy { it.id }; nextBoardsCursor.value = page.next_cursor }
                2 -> runCatching { repo.savedPins(name, cursor) }.onSuccess { page -> saved.value = (saved.value + page.items).distinctBy { it.id }; nextSavedCursor.value = page.next_cursor }
                else -> runCatching { repo.userPins(name, cursor) }.onSuccess { page -> pins.value = (pins.value + page.items).distinctBy { it.id }; nextPinsCursor.value = page.next_cursor }
            }
            loadingMore.value = false
        }
    }

    fun follow() {
        val current = user.value ?: return
        viewModelScope.launch {
            runCatching {
                if (current.following_me) repo.unfollow(current.id) else repo.follow(current.id)
            }.onSuccess {
                user.value = current.copy(
                    following_me = !current.following_me,
                    followers = (current.followers + if (current.following_me) -1 else 1).coerceAtLeast(0)
                )
            }
        }
    }
}

@HiltViewModel
class TagResultsViewModel @Inject constructor(private val repo: PinBomRepository) : ViewModel() {
    val items = MutableStateFlow<List<Pin>>(emptyList())
    val cursor = MutableStateFlow("")
    val loading = MutableStateFlow(false)
    val loadingMore = MutableStateFlow(false)
    val failed = MutableStateFlow(false)
    private var activeTag = ""

    fun load(tag: String) {
        val normalized = tag.trim().removePrefix("#")
        if (normalized.isBlank()) { items.value = emptyList(); cursor.value = ""; failed.value = true; return }
        if (activeTag == normalized && items.value.isNotEmpty()) return
        activeTag = normalized
        viewModelScope.launch {
            loading.value = true; failed.value = false; items.value = emptyList(); cursor.value = ""
            runCatching { repo.tagPins(normalized) }.onSuccess { page -> items.value = page.items; cursor.value = page.next_cursor }.onFailure { failed.value = true }
            loading.value = false
        }
    }

    fun loadMore() {
        val tag = activeTag; val next = cursor.value
        if (tag.isBlank() || next.isBlank() || loadingMore.value) return
        viewModelScope.launch {
            loadingMore.value = true
            runCatching { repo.tagPins(tag, next) }.onSuccess { page -> items.value = (items.value + page.items).distinctBy { it.id }; cursor.value = page.next_cursor }
            loadingMore.value = false
        }
    }
}

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val repo: PinBomRepository
) : ViewModel() {
    val items = MutableStateFlow<List<Notification>>(emptyList())
    val nextCursor = MutableStateFlow("")
    val loadingMore = MutableStateFlow(false)
    val unreadCount = MutableStateFlow(0)

    /* Phase 8: additive UI state only — drives the notifications skeleton / retry. */
    val loading = MutableStateFlow(false)
    val failed = MutableStateFlow(false)

    fun load() {
        viewModelScope.launch {
            loading.value = true
            failed.value = false
            runCatching { repo.notifications() }
                .onSuccess { items.value = it.items; nextCursor.value = it.next_cursor; unreadCount.value = it.unread }
                .onFailure { if (items.value.isEmpty()) failed.value = true }
            loading.value = false
        }
    }

    fun loadMore() {
        val cursor = nextCursor.value
        if (cursor.isBlank() || loadingMore.value) return
        viewModelScope.launch {
            loadingMore.value = true
            runCatching { repo.notifications(cursor) }.onSuccess { page ->
                items.value = (items.value + page.items).distinctBy { it.id }
                nextCursor.value = page.next_cursor
                unreadCount.value = page.unread
            }
            loadingMore.value = false
        }
    }

    fun markRead() {
        viewModelScope.launch { runCatching { repo.readNotifications() }.onSuccess { unreadCount.value = 0; items.value = items.value.map { it.copy(read = true) } } }
    }
}

@HiltViewModel
class BoardsViewModel @Inject constructor(
    private val repo: PinBomRepository,
    private val session: SessionManager
) : ViewModel() {
    val items = MutableStateFlow<List<Board>>(emptyList())
    val message = MutableStateFlow("")

    /* Phase 7: additive UI state only. `lastAction` is a one-shot signal the
     * screens turn into *localized* toasts; the nonce makes two identical
     * results in a row still fire. */
    val loading = MutableStateFlow(false)
    val lastAction = MutableStateFlow("")
    val invitations = MutableStateFlow<List<BoardInvitation>>(emptyList())
    val invitationsBusy = MutableStateFlow(false)
    val currentUserId = MutableStateFlow(0L)

    fun loadInvitations() {
        viewModelScope.launch {
            invitationsBusy.value = true
            runCatching { repo.boardInvitations() }.onSuccess { invitations.value = it.items }
            invitationsBusy.value = false
        }
    }

    fun respondInvitation(id: Long, accept: Boolean) {
        viewModelScope.launch {
            runCatching { repo.respondBoardInvitation(id, accept) }
                .onSuccess { loadInvitations(); if (accept) load() }
                .onFailure { message.value = it.message.orEmpty(); lastAction.value = "failed:${System.nanoTime()}" }
        }
    }

    fun load() {
        val name = session.username
        if (name.isBlank()) return
        viewModelScope.launch {
            loading.value = true
            runCatching { repo.profile(name) }.onSuccess { currentUserId.value = it.id }
            runCatching { repo.boards(name) }.onSuccess { items.value = it.items }
            loading.value = false
        }
    }

    fun create(title: String, description: String) {
        viewModelScope.launch {
            runCatching { repo.createBoard(title, description) }
                .onSuccess {
                    lastAction.value = "created:${System.nanoTime()}"
                    load()
                }
                .onFailure {
                    message.value = it.message.orEmpty()
                    lastAction.value = "failed:${System.nanoTime()}"
                }
        }
    }

    fun update(id: Long, title: String, description: String) {
        viewModelScope.launch {
            runCatching { repo.patchBoard(id, title, description) }
                .onSuccess {
                    lastAction.value = "updated:${System.nanoTime()}"
                    load()
                }
                .onFailure {
                    message.value = it.message.orEmpty()
                    lastAction.value = "failed:${System.nanoTime()}"
                }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            runCatching { repo.deleteBoard(id) }
                .onSuccess {
                    lastAction.value = "deleted:${System.nanoTime()}"
                    load()
                }
                .onFailure {
                    message.value = it.message.orEmpty()
                    lastAction.value = "failed:${System.nanoTime()}"
                }
        }
    }
}

@HiltViewModel
class BoardDetailViewModel @Inject constructor(
    private val repo: PinBomRepository
) : ViewModel() {
    val item = MutableStateFlow<ir.pinbom.app.data.remote.BoardDetail?>(null)

    /* Phase 7: additive UI state only — drives the board skeleton / retry. */
    val loading = MutableStateFlow(false)
    val failed = MutableStateFlow(false)
    val members = MutableStateFlow<List<BoardMember>>(emptyList())
    val inviteBusy = MutableStateFlow(false)
    val inviteMessage = MutableStateFlow("")

    fun loadMembers(id: Long) {
        viewModelScope.launch {
            runCatching { repo.boardMembers(id) }.onSuccess { members.value = it.items }
        }
    }

    fun invite(id: Long, username: String, role: String, onDone: () -> Unit = {}) {
        if (inviteBusy.value) return
        viewModelScope.launch {
            inviteBusy.value = true
            runCatching { repo.inviteBoard(id, username.trim().removePrefix("@"), role) }
                .onSuccess { inviteMessage.value = "sent:${System.nanoTime()}"; loadMembers(id); onDone() }
                .onFailure { inviteMessage.value = "failed:${it.message.orEmpty()}" }
            inviteBusy.value = false
        }
    }

    fun load(id: Long) {
        if (id <= 0) return
        viewModelScope.launch {
            loading.value = true
            failed.value = false
            runCatching { repo.board(id) }
                .onSuccess { item.value = it }
                .onFailure { if (item.value == null) failed.value = true }
            loading.value = false
        }
    }
}

@HiltViewModel
class NavigationProfileViewModel @Inject constructor(
    private val repo: PinBomRepository,
) : ViewModel() {
    val profile = MutableStateFlow<UserProfile?>(null)
    private var loadedUsername: String = ""

    fun load(username: String, force: Boolean = false) {
        if (username.isBlank() || (!force && username == loadedUsername)) return
        loadedUsername = username
        viewModelScope.launch {
            runCatching { repo.profile(username) }
                .onSuccess { profile.value = it }
                .onFailure { /* The profile tab still works with its initials fallback. */ }
        }
    }
}

/** Small, independent mutation path used by feed quick actions; it avoids
 * loading all detail/comments/related data just to toggle one like. */
@HiltViewModel
class PinActionViewModel @Inject constructor(
    private val repo: PinBomRepository,
) : ViewModel() {
    suspend fun toggleLike(pinId: Long, currentlyLiked: Boolean): Boolean? =
        runCatching {
            val result = if (currentlyLiked) repo.unlike(pinId) else repo.like(pinId)
            result.liked ?: !currentlyLiked
        }.getOrNull()

    /** Saves [pinId] to [boardId] (or the default shelf when null). True on success. */
    suspend fun saveTo(pinId: Long, boardId: Long?): Boolean =
        runCatching { repo.save(pinId, boardId) }.isSuccess

    suspend fun report(pinId: Long, reason: String, note: String): Boolean =
        runCatching { repo.reportPin(pinId, reason, note) }.isSuccess

    suspend fun updatePin(pinId: Long, title: String, description: String): Boolean =
        runCatching { repo.updatePin(pinId, title.trim(), description.trim()) }.isSuccess

    suspend fun deletePin(pinId: Long): Boolean =
        runCatching { repo.deletePin(pinId) }.isSuccess

    suspend fun feedback(pinId: Long, action: String): Boolean =
        runCatching { repo.boardFeedback(pinId, action) }.isSuccess

    suspend fun muteUser(userId: Long): Boolean =
        runCatching { repo.muteUser(userId) }.isSuccess
}

@HiltViewModel
class SimilarPinsViewModel @Inject constructor(
    private val repo: PinBomRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val items = MutableStateFlow<List<Pin>>(emptyList())
    val loading = MutableStateFlow(false)
    val failed = MutableStateFlow(false)

    fun searchPin(id: Long, x: Float = 0f, y: Float = 0f, width: Float = 0f, height: Float = 0f) {
        if (id <= 0) return
        viewModelScope.launch {
            loading.value = true; failed.value = false
            runCatching { repo.similarPin(id, x, y, width, height) }
                .onSuccess { items.value = it.items }
                .onFailure { failed.value = true }
            loading.value = false
        }
    }

    fun searchImage(uri: Uri) {
        viewModelScope.launch {
            loading.value = true; failed.value = false
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val mime = context.contentResolver.getType(uri).orEmpty()
                    val suffix = when { mime.contains("png") -> ".png"; mime.contains("webp") -> ".webp"; else -> ".jpg" }
                    val file = File(context.cacheDir, "pinbom-similar-${System.nanoTime()}$suffix")
                    context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
                        ?: error("Could not read the selected image")
                    file
                }
            }.mapCatching { file ->
                try { repo.similarImage(file) } finally { file.delete() }
            }
            result.onSuccess { items.value = it.items }.onFailure { failed.value = true }
            loading.value = false
        }
    }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: PinBomRepository
) : ViewModel() {
    val busy = MutableStateFlow(false)
    val profile = MutableStateFlow<UserProfile?>(null)

    fun loadProfile(username: String) {
        if (username.isBlank()) return
        viewModelScope.launch {
            runCatching { repo.profile(username) }
                .onSuccess { profile.value = it }
        }
    }

    /** Server-provided text, shown exactly as it arrives. */
    val message = MutableStateFlow("")

    /**
     * One-shot result signal ("saved:<nonce>" / "failed:<nonce>"). The screen
     * turns it into a toast worded in the current language; the nonce makes two
     * identical results in a row still fire.
     */
    val lastAction = MutableStateFlow("")

    fun save(name: String, bio: String) {
        viewModelScope.launch {
            busy.value = true
            runCatching { repo.patchMe(name, bio) }
                .onSuccess {
                    message.value = ""
                    profile.value = profile.value?.copy(display_name = name, bio = bio)
                    lastAction.value = "saved:${System.nanoTime()}"
                }
                .onFailure {
                    message.value = it.message.orEmpty()
                    lastAction.value = "failed:${System.nanoTime()}"
                }
            busy.value = false
        }
    }

    fun delete(done: () -> Unit) {
        viewModelScope.launch {
            runCatching { repo.deleteMe() }
                .onSuccess { done() }
                .onFailure { message.value = it.message.orEmpty() }
        }
    }
}

/* ────────────────────────────────────────────────────────────────────────────
 *  PinBom · upload progress
 *  One upload, followed from the button that starts it to the server that
 *  answers it. The screen hands the picked image over, the ViewModel caches a
 *  copy, queues the work and then simply mirrors WorkInfo into a value the
 *  progress card can draw — so the card survives navigation, rotation and the
 *  death of the screen that started it, while the work itself keeps running.
 * ──────────────────────────────────────────────────────────────────────────── */

/** Which face the upload card is showing. */
enum class UploadStatus { Idle, Running, Success, Failed }

/** Everything the card draws, in one immutable value. */
data class UploadUiState(
    val status: UploadStatus = UploadStatus.Idle,
    val percent: Int = 0,
    val stage: UploadStage = UploadStage.Preparing,
    val written: Long = 0L,
    val total: Long = 0L,

    /** Shown exactly as it arrived — server wording is never re-worded. */
    val error: String? = null,

    /** The image on its way up, so the card can show what it is uploading. */
    val thumbnail: String? = null,
    val waitingForWifi: Boolean = false,
)

@HiltViewModel
class UploadViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(UploadUiState())
    val state: StateFlow<UploadUiState> = _state.asStateFlow()

    private var observer: Job? = null
    private var workId: UUID? = null
    private var payload: Data? = null
    private var wifiOnlyEnabled: Boolean = false

    /** What the screen asked for, kept only so a failed *preparation* can be retried. */
    private var lastRequest: StartRequest? = null

    /**
     * Prepares the picked image, queues the upload and starts following it.
     * The six queue keys the worker always read are unchanged; [UploadKeys.FILE]
     * is the extra key that carries the prepared JPEG.
     */
    fun start(uri: Uri, title: String, description: String, tags: String, server: String, boardId: String) {
        val request = StartRequest(uri, title, description, tags, server, boardId)
        lastRequest = request
        enqueue(request)
    }

    /**
     * The publish flow, in order: decode and re-encode the picture on IO, queue the
     * work, then follow it. Nothing is queued unless there is a file to send — a
     * picture this phone cannot decode is one message to the user, not five retries
     * of an upload that could never start.
     */
    private fun enqueue(request: StartRequest) {
        val id = UUID.randomUUID()
        workId = id
        _state.value = UploadUiState(status = UploadStatus.Running, thumbnail = request.uri.toString())

        observer?.cancel()
        observer = viewModelScope.launch {
            wifiOnlyEnabled = FeaturePreferences(context).wifiOnlyUploads()
            val prepared = withContext(Dispatchers.IO) {
                runCatching {
                    ImagePreparer.prepare(
                        context = context,
                        uri = request.uri,
                        dir = ImagePreparer.uploadDir(context),
                        name = ImagePreparer.fileNameFor(id.toString()),
                    )
                }
                    .onFailure { Log.e(TAG, "could not prepare ${request.uri}", it) }
                    .getOrNull()
            }
            if (prepared == null) {
                val sharedSource = request.uri.path?.takeIf {
                    request.uri.scheme == "file" && it.contains("/share_target/")
                }
                sharedSource?.let { path -> withContext(Dispatchers.IO) { File(path).delete() } }
                if (sharedSource != null) FeaturePreferences(context).setPendingSharePath(null)
                payload = null
                _state.value = UploadUiState(
                    status = UploadStatus.Failed,
                    stage = UploadStage.Failed,
                    thumbnail = request.uri.toString(),
                    error = context.getString(R.string.upload_error_unreadable),
                )
                return@launch
            }

            val sharedSource = request.uri.path?.takeIf {
                request.uri.scheme == "file" && it.contains("/share_target/")
            }
            val data = Data.Builder()
                .putString(UploadKeys.URI, request.uri.toString())
                .putString(UploadKeys.TITLE, request.title)
                .putString(UploadKeys.DESCRIPTION, request.description)
                .putString(UploadKeys.TAGS, request.tags)
                .putString(UploadKeys.SERVER, request.server)
                .putString(UploadKeys.BOARD_ID, request.boardId)
                .putString(UploadKeys.FILE, prepared.absolutePath)
                .apply { if (sharedSource != null) putString(UploadKeys.SHARE_SOURCE_FILE, sharedSource) }
                .build()
            payload = data
            if (sharedSource != null) FeaturePreferences(context).setPendingSharePath(null)

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnlyEnabled) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .build()
            val queued = OneTimeWorkRequestBuilder<UploadWorker>()
                .setId(id)
                .setInputData(data)
                .setConstraints(constraints)
                .addTag(tag(id))
                .build()
            manager.enqueue(queued)
            manager.getWorkInfoByIdFlow(id).collect { mirror(it) }
        }
    }

    /**
     * Retry: the very same payload, queued again. A fresh id is used on purpose —
     * WorkManager keeps a finished request's row around, so re-using its id would
     * mean resurrecting a work spec instead of running a new one. The cached copy
     * is still on disk, which is why a failure never deletes it.
     */
    fun retry() {
        val data = payload
        if (data == null) {
            /* The image never became a file, so nothing was queued: start over,
             * preparation included, rather than showing a button that does nothing. */
            lastRequest?.let { enqueue(it) }
            return
        }
        val id = UUID.randomUUID()
        workId = id
        _state.value = _state.value.copy(
            status = UploadStatus.Running,
            percent = 0,
            stage = UploadStage.Preparing,
            written = 0L,
            error = null,
        )
        observer?.cancel()
        observer = viewModelScope.launch {
            wifiOnlyEnabled = FeaturePreferences(context).wifiOnlyUploads()
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnlyEnabled) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .build()
            val queued = OneTimeWorkRequestBuilder<UploadWorker>()
                .setId(id)
                .setInputData(data)
                .setConstraints(constraints)
                .addTag(tag(id))
                .build()
            manager.enqueue(queued)
            manager.getWorkInfoByIdFlow(id).collect { mirror(it) }
        }
    }

    /** The X while uploading: stop the work and drop the half-sent copy. */
    fun cancel() {
        val id = workId ?: return
        manager.cancelWorkById(id)
        discard()
        payload = null
        _state.value = UploadUiState()
    }

    /** The X on the error card: nothing is queued any more, so just clean up. */
    fun dismiss() {
        if (_state.value.status == UploadStatus.Failed) discard()
        payload = null
        _state.value = UploadUiState()
    }

    /** WorkInfo → card state. Nothing here ever blocks the main thread. */
    private fun mirror(info: WorkInfo?) {
        if (info == null) return
        when (info.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> _state.value = _state.value.copy(
                status = UploadStatus.Running,
                percent = 0,
                stage = UploadStage.Preparing,
                written = 0L,
                error = null,
                waitingForWifi = wifiOnlyEnabled && !isUnmeteredNetworkAvailable(),
            )

            WorkInfo.State.RUNNING -> _state.value = _state.value.copy(
                status = UploadStatus.Running,
                /* 100 % belongs to the server's answer, never to the socket. */
                percent = info.progress.getInt(UploadKeys.PROGRESS, 0).coerceIn(0, 99),
                stage = UploadStage.of(info.progress.getString(UploadKeys.STAGE)),
                written = info.progress.getLong(UploadKeys.WRITTEN, 0L),
                total = info.progress.getLong(UploadKeys.TOTAL, 0L),
                error = null,
                waitingForWifi = false,
            )

            WorkInfo.State.SUCCEEDED -> {
                if (_state.value.status == UploadStatus.Success) return
                _state.value = _state.value.copy(
                    status = UploadStatus.Success,
                    percent = 100,
                    stage = UploadStage.Done,
                    written = _state.value.total,
                    error = null,
                )
                UploadEvents.publishCompleted()
                viewModelScope.launch {
                    /* Long enough to read the check mark, short enough not to nag. */
                    delay(SUCCESS_VISIBLE_MS)
                    if (_state.value.status == UploadStatus.Success) _state.value = UploadUiState()
                }
            }

            WorkInfo.State.FAILED -> _state.value = _state.value.copy(
                status = UploadStatus.Failed,
                stage = UploadStage.Failed,
                error = info.outputData.getString(UploadKeys.ERROR),
            )

            WorkInfo.State.CANCELLED -> _state.value = UploadUiState()
        }
    }

    private fun isUnmeteredNetworkAvailable(): Boolean {
        val manager = context.getSystemService(android.net.ConnectivityManager::class.java) ?: return false
        val active = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private val manager get() = WorkManager.getInstance(context)

    private fun tag(id: UUID): String = "$TAG:$id"

    /**
     * Deletes the prepared copy. It is named by the payload, which is what keeps a
     * retry's cache file collectable: the retry gets a fresh work id, but the file
     * it re-sends still carries the id it was written under.
     */
    private fun discard() {
        val named = payload?.getString(UploadKeys.FILE)
        val sharedSource = payload?.getString(UploadKeys.SHARE_SOURCE_FILE)
        val id = workId?.toString()
        viewModelScope.launch(Dispatchers.IO) {
            named?.let { path -> runCatching { File(path).delete() } }
            sharedSource?.let { path -> runCatching { File(path).delete() } }
            id?.let { runCatching { File(ImagePreparer.uploadDir(context), ImagePreparer.fileNameFor(it)).delete() } }
            if (sharedSource != null) FeaturePreferences(context).setPendingSharePath(null)
        }
    }

    /** One publish, as the screen described it. */
    private data class StartRequest(
        val uri: Uri,
        val title: String,
        val description: String,
        val tags: String,
        val server: String,
        val boardId: String,
    )

    private companion object {
        const val TAG = "pinbom_upload"
        const val SUCCESS_VISIBLE_MS = 1_800L
    }
}


/** One row of “people to follow”. [followed] is local: the profile endpoint's
 * follow flag is not used here, so a fresh tap is all this list ever shows. */
data class SuggestedUser(val profile: UserProfile, val followed: Boolean = false)

/* ── Additive onboarding, settings and device-session view models ─────────── */
data class InterestsUiState(
    val items: List<ir.pinbom.app.data.remote.InterestRecord> = emptyList(),
    val selected: Set<Long> = emptySet(),
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class InterestsViewModel @Inject constructor(
    private val repo: PinBomRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _state = MutableStateFlow(InterestsUiState())
    val state: StateFlow<InterestsUiState> = _state.asStateFlow()

    fun load(editMode: Boolean) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                val page = repo.interests()
                val selected = if (editMode) repo.myInterests().ids.toSet() else emptySet()
                page.items to selected
            }.onSuccess { (items, selected) ->
                _state.value = _state.value.copy(items = items, selected = selected, loading = false)
            }.onFailure { error ->
                _state.value = _state.value.copy(loading = false, error = error.message ?: context.getString(R.string.state_error_body))
            }
        }
    }

    fun toggle(id: Long) {
        val old = _state.value
        val updated = InterestSelectionRules.toggle(old.selected, id)
        _state.value = if (updated == old.selected && id !in old.selected && old.selected.size >= InterestSelectionRules.MAX_SELECTION) {
            old.copy(error = context.getString(R.string.interests_maximum))
        } else {
            old.copy(selected = updated, error = null)
        }
    }

    fun save(onDone: () -> Unit) {
        val ids = _state.value.selected.toList().sorted()
        if (ids.size < InterestSelectionRules.MIN_SELECTION) {
            _state.value = _state.value.copy(error = context.getString(R.string.interests_minimum))
            return
        }
        if (ids.size > InterestSelectionRules.MAX_SELECTION) {
            _state.value = _state.value.copy(error = context.getString(R.string.interests_maximum))
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, error = null)
            runCatching {
                repo.replaceMyInterests(ids)
                ir.pinbom.app.FeaturePreferences(context).setInterestsOnboardingComplete(true)
            }.onSuccess {
                _state.value = _state.value.copy(saving = false)
                onDone()
            }.onFailure { error ->
                _state.value = _state.value.copy(saving = false, error = error.message ?: context.getString(R.string.state_error_body))
            }
        }
    }
}

data class ActiveSessionsUiState(
    val items: List<ir.pinbom.app.data.remote.ActiveSession> = emptyList(),
    val loading: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ActiveSessionsViewModel @Inject constructor(
    private val repo: PinBomRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _state = MutableStateFlow(ActiveSessionsUiState())
    val state: StateFlow<ActiveSessionsUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { repo.activeSessions().items }
                .onSuccess { _state.value = _state.value.copy(items = it, loading = false) }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message ?: context.getString(R.string.state_error_body)) }
        }
    }

    fun revoke(session: ir.pinbom.app.data.remote.ActiveSession, onCurrentRevoked: () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null)
            runCatching { repo.revokeSession(session.id) }
                .onSuccess {
                    if (session.current) {
                        _state.value = _state.value.copy(busy = false)
                        onCurrentRevoked()
                    } else {
                        refreshAfterMutation()
                    }
                }
                .onFailure { _state.value = _state.value.copy(busy = false, error = it.message ?: context.getString(R.string.state_error_body)) }
        }
    }

    fun revokeOthers() {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null)
            runCatching { repo.revokeOtherSessions() }
                .onSuccess { refreshAfterMutation() }
                .onFailure { _state.value = _state.value.copy(busy = false, error = it.message ?: context.getString(R.string.state_error_body)) }
        }
    }

    private suspend fun refreshAfterMutation() {
        val result = runCatching { repo.activeSessions().items }
        _state.value = result.fold(
            onSuccess = { _state.value.copy(items = it, busy = false, loading = false) },
            onFailure = { _state.value.copy(busy = false, error = it.message ?: context.getString(R.string.state_error_body)) },
        )
    }
}

data class CacheSettingsUiState(val bytes: Long = 0L, val clearing: Boolean = false, val error: String? = null)

@HiltViewModel
class CacheSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: PinBomRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CacheSettingsUiState())
    val state: StateFlow<CacheSettingsUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(bytes = measureCacheBytes(), error = null)
        }
    }

    fun clear(onComplete: () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(clearing = true, error = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    runCatching { coil3.SingletonImageLoader.get(context).diskCache?.clear() }
                    runCatching { coil3.SingletonImageLoader.get(context).memoryCache?.clear() }
                    repo.clearLocalPinCache()
                }
            }.onSuccess {
                _state.value = _state.value.copy(clearing = false, bytes = withContext(Dispatchers.IO) { measureCacheBytes() })
                onComplete()
            }.onFailure {
                _state.value = _state.value.copy(clearing = false, error = context.getString(R.string.state_error_body))
            }
        }
    }

    private fun measureCacheBytes(): Long {
        val imageCache = File(context.cacheDir, ir.pinbom.app.PinBomApplication.IMAGE_CACHE_DIR)
        val database = context.getDatabasePath("pinbom-cache.db")
        return directoryBytes(imageCache) + listOf(database, File(database.path + "-wal"), File(database.path + "-shm"))
            .sumOf { if (it.isFile) it.length() else 0L }
    }

    private fun directoryBytes(file: File): Long = when {
        !file.exists() -> 0L
        file.isFile -> file.length()
        else -> file.listFiles()?.sumOf(::directoryBytes) ?: 0L
    }
}

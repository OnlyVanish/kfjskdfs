package ir.pinbom.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.pinbom.app.data.SessionManager
import ir.pinbom.app.data.extras.BoardLite
import ir.pinbom.app.data.extras.EditedComment
import ir.pinbom.app.data.extras.ExtrasRepository
import ir.pinbom.app.data.extras.PersonRow
import ir.pinbom.app.data.extras.ReportReason
import ir.pinbom.app.data.extras.ServerNotificationPrefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/* ────────────────────────────────────────────────────────────────────────────
 *  PinBom · view models for the features that were backend-only
 *  (people lists, blocking, reports, comment tools, board pin tools,
 *  server notification preferences).
 * ──────────────────────────────────────────────────────────────────────────── */

/** One-shot result the screens turn into a localized toast. [nonce] makes repeats fire. */
enum class ExtrasEvent {
    Blocked, Unblocked, UserReported, CommentEdited, CommentDeleted, CommentReported,
    PinRemoved, PinMoved, PrefsSaved, Failed,
}

data class ExtrasSignal(val event: ExtrasEvent, val detail: String = "", val nonce: Long = System.nanoTime())

private inline fun <T> runExtras(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

/* ── followers / following ──────────────────────────────────────────────── */

data class PeopleState(
    val items: List<PersonRow> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val failed: Boolean = false,
    val cursor: String = "",
)

@HiltViewModel
class PeopleViewModel @Inject constructor(
    private val extras: ExtrasRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PeopleState())
    val state: StateFlow<PeopleState> = _state.asStateFlow()

    private var userId = 0L
    private var followers = true
    private var loadedFor = ""

    fun load(username: String, kind: String, force: Boolean = false) {
        val key = "$username/$kind"
        if (!force && key == loadedFor) return
        loadedFor = key
        followers = kind != KIND_FOLLOWING
        viewModelScope.launch {
            _state.value = PeopleState(loading = true)
            runExtras {
                userId = extras.profile(username).id
                if (followers) extras.followers(userId) else extras.following(userId)
            }.onSuccess { page ->
                _state.value = PeopleState(items = page.items, cursor = page.next_cursor)
            }.onFailure {
                loadedFor = ""
                _state.value = PeopleState(failed = true)
            }
        }
    }

    fun loadMore() {
        val current = _state.value
        if (current.cursor.isBlank() || current.loadingMore || current.loading || userId == 0L) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            runExtras {
                if (followers) extras.followers(userId, current.cursor) else extras.following(userId, current.cursor)
            }.onSuccess { page ->
                _state.update { s ->
                    val known = s.items.mapTo(HashSet()) { it.id }
                    s.copy(items = s.items + page.items.filter { it.id !in known }, cursor = page.next_cursor, loadingMore = false)
                }
            }.onFailure { _state.update { it.copy(loadingMore = false) } }
        }
    }

    companion object {
        const val KIND_FOLLOWERS = "followers"
        const val KIND_FOLLOWING = "following"
    }
}

/* ── blocked users ──────────────────────────────────────────────────────── */

data class BlockedState(
    val items: List<PersonRow> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false,
    val cursor: String = "",
    val busyId: Long = 0,
)

@HiltViewModel
class BlockedUsersViewModel @Inject constructor(
    private val extras: ExtrasRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(BlockedState())
    val state: StateFlow<BlockedState> = _state.asStateFlow()
    private val _signal = MutableStateFlow<ExtrasSignal?>(null)
    val signal: StateFlow<ExtrasSignal?> = _signal.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, failed = false) }
            runExtras { extras.blockedUsers() }
                .onSuccess { page -> _state.value = BlockedState(items = page.items, loading = false, cursor = page.next_cursor) }
                .onFailure { _state.update { it.copy(loading = false, failed = true) } }
        }
    }

    fun loadMore() {
        val current = _state.value
        if (current.cursor.isBlank() || current.loading) return
        viewModelScope.launch {
            runExtras { extras.blockedUsers(current.cursor) }.onSuccess { page ->
                _state.update { s -> s.copy(items = s.items + page.items, cursor = page.next_cursor) }
            }
        }
    }

    fun unblock(person: PersonRow) {
        if (_state.value.busyId != 0L) return
        viewModelScope.launch {
            _state.update { it.copy(busyId = person.id) }
            runExtras { extras.unblock(person.id) }
                .onSuccess {
                    _state.update { s -> s.copy(items = s.items.filterNot { it.id == person.id }, busyId = 0) }
                    _signal.value = ExtrasSignal(ExtrasEvent.Unblocked)
                }.onFailure {
                    _state.update { it.copy(busyId = 0) }
                    _signal.value = ExtrasSignal(ExtrasEvent.Failed, it.message.orEmpty())
                }
        }
    }
}

/* ── server notification preferences ────────────────────────────────────── */

data class PushPrefsState(
    val prefs: ServerNotificationPrefs = ServerNotificationPrefs(),
    val loading: Boolean = true,
    val failed: Boolean = false,
    val saving: Boolean = false,
)

@HiltViewModel
class PushPrefsViewModel @Inject constructor(
    private val extras: ExtrasRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PushPrefsState())
    val state: StateFlow<PushPrefsState> = _state.asStateFlow()
    private val _signal = MutableStateFlow<ExtrasSignal?>(null)
    val signal: StateFlow<ExtrasSignal?> = _signal.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, failed = false) }
            runExtras { extras.notificationPrefs() }
                .onSuccess { _state.value = PushPrefsState(prefs = it, loading = false) }
                .onFailure { _state.update { s -> s.copy(loading = false, failed = true) } }
        }
    }

    /** Optimistic: the switch moves at once and snaps back if the server refuses. */
    fun change(transform: (ServerNotificationPrefs) -> ServerNotificationPrefs) {
        val before = _state.value.prefs
        val after = transform(before)
        if (after == before) return
        _state.update { it.copy(prefs = after, saving = true) }
        viewModelScope.launch {
            runExtras { extras.saveNotificationPrefs(after) }
                .onSuccess { saved -> _state.update { it.copy(prefs = saved, saving = false) } }
                .onFailure { e ->
                    _state.update { it.copy(prefs = before, saving = false) }
                    _signal.value = ExtrasSignal(ExtrasEvent.Failed, e.message.orEmpty())
                }
        }
    }
}

/* ── block / report from a profile ──────────────────────────────────────── */

data class SafetyState(val userId: Long = 0, val blocked: Boolean = false, val busy: Boolean = false)

@HiltViewModel
class ProfileSafetyViewModel @Inject constructor(
    private val extras: ExtrasRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SafetyState())
    val state: StateFlow<SafetyState> = _state.asStateFlow()
    private val _signal = MutableStateFlow<ExtrasSignal?>(null)
    val signal: StateFlow<ExtrasSignal?> = _signal.asStateFlow()
    private var username = ""

    /** Resolves the numeric id once per profile; blocking needs it. */
    fun bind(name: String) {
        if (name == username || name.isBlank()) return
        username = name
        _state.value = SafetyState()
        viewModelScope.launch {
            runExtras { extras.profile(name).id }.onSuccess { id -> _state.update { it.copy(userId = id) } }
        }
    }

    fun block(onBlocked: () -> Unit = {}) = act(ExtrasEvent.Blocked) {
        extras.block(it)
        _state.update { s -> s.copy(blocked = true) }
        onBlocked()
    }

    fun unblock() = act(ExtrasEvent.Unblocked) {
        extras.unblock(it)
        _state.update { s -> s.copy(blocked = false) }
    }

    fun report(reason: ReportReason, note: String) = act(ExtrasEvent.UserReported) { extras.reportUser(it, reason, note) }

    private fun act(success: ExtrasEvent, block: suspend (Long) -> Unit) {
        val id = _state.value.userId
        if (id == 0L || _state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            runExtras { block(id) }
                .onSuccess { _signal.value = ExtrasSignal(success) }
                .onFailure { _signal.value = ExtrasSignal(ExtrasEvent.Failed, it.message.orEmpty()) }
            _state.update { it.copy(busy = false) }
        }
    }
}

/* ── comment tools ──────────────────────────────────────────────────────── */

@HiltViewModel
class CommentToolsViewModel @Inject constructor(
    private val extras: ExtrasRepository,
) : ViewModel() {
    private val _signal = MutableStateFlow<ExtrasSignal?>(null)
    val signal: StateFlow<ExtrasSignal?> = _signal.asStateFlow()
    val busy = MutableStateFlow(false)

    fun edit(pinId: Long, commentId: Long, body: String, onDone: (EditedComment) -> Unit) = run(ExtrasEvent.CommentEdited) {
        onDone(extras.editComment(pinId, commentId, body))
    }

    fun delete(pinId: Long, commentId: Long, onDone: () -> Unit) = run(ExtrasEvent.CommentDeleted) {
        extras.deleteComment(pinId, commentId)
        onDone()
    }

    fun report(pinId: Long, commentId: Long, reason: ReportReason, note: String) = run(ExtrasEvent.CommentReported) {
        extras.reportComment(pinId, commentId, reason, note)
    }

    private fun run(success: ExtrasEvent, block: suspend () -> Unit) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            runExtras { block() }
                .onSuccess { _signal.value = ExtrasSignal(success) }
                .onFailure { _signal.value = ExtrasSignal(ExtrasEvent.Failed, it.message.orEmpty()) }
            busy.value = false
        }
    }
}

/* ── board pin tools ────────────────────────────────────────────────────── */

@HiltViewModel
class BoardPinToolsViewModel @Inject constructor(
    private val extras: ExtrasRepository,
    private val session: SessionManager,
) : ViewModel() {
    private val _signal = MutableStateFlow<ExtrasSignal?>(null)
    val signal: StateFlow<ExtrasSignal?> = _signal.asStateFlow()
    val boards = MutableStateFlow<List<BoardLite>>(emptyList())
    val busy = MutableStateFlow(false)

    fun loadBoards() {
        val name = session.username
        if (name.isBlank()) return
        viewModelScope.launch { runExtras { extras.myBoards(name) }.onSuccess { boards.value = it } }
    }

    fun remove(boardId: Long, pinId: Long, onDone: () -> Unit) = run(ExtrasEvent.PinRemoved) {
        extras.removePinFromBoard(boardId, pinId); onDone()
    }

    fun move(fromBoardId: Long, pinId: Long, toBoardId: Long, onDone: () -> Unit) = run(ExtrasEvent.PinMoved) {
        extras.movePin(fromBoardId, pinId, toBoardId); onDone()
    }

    private fun run(success: ExtrasEvent, block: suspend () -> Unit) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            runExtras { block() }
                .onSuccess { _signal.value = ExtrasSignal(success) }
                .onFailure { _signal.value = ExtrasSignal(ExtrasEvent.Failed, it.message.orEmpty()) }
            busy.value = false
        }
    }
}

package ir.pinbom.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import ir.pinbom.app.ui.components.icons.PinBomIcons
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import kotlinx.coroutines.flow.distinctUntilChanged
import ir.pinbom.app.R
import ir.pinbom.app.ui.nav.Routes
import ir.pinbom.app.data.SessionManager
import ir.pinbom.app.data.remote.BoardDetail
import ir.pinbom.app.data.remote.Pin
import ir.pinbom.app.ui.BoardDetailViewModel
import ir.pinbom.app.ui.BoardsViewModel
import ir.pinbom.app.ui.components.ActionToastHost
import ir.pinbom.app.ui.components.BoardCoverCollage
import ir.pinbom.app.ui.components.PinBomBottomSheet
import ir.pinbom.app.ui.components.PinBomDialog
import ir.pinbom.app.ui.components.PinBomDialogContent
import ir.pinbom.app.ui.components.PinBomTextField
import ir.pinbom.app.ui.components.PinBomChip
import ir.pinbom.app.ui.components.EmptyState
import ir.pinbom.app.ui.components.ErrorState
import ir.pinbom.app.ui.components.IconCircleButton
import ir.pinbom.app.ui.components.PillButtonSize
import ir.pinbom.app.ui.components.PinCountText
import ir.pinbom.app.ui.components.PinGridSkeleton
import ir.pinbom.app.ui.components.PrimaryPillButton
import ir.pinbom.app.ui.components.RelativeTimeText
import ir.pinbom.app.ui.components.SecondaryPillButton
import ir.pinbom.app.ui.components.SheetHeader
import ir.pinbom.app.ui.components.SkeletonLine
import ir.pinbom.app.ui.components.rememberPinBomToastState
import ir.pinbom.app.ui.components.ContentText
import ir.pinbom.app.ui.components.ContentTextField
import ir.pinbom.app.ui.theme.PinBomShape
import ir.pinbom.app.ui.theme.PinBomText
import ir.pinbom.app.ui.theme.PinBomTheme
import ir.pinbom.app.ui.theme.Dimens
import ir.pinbom.app.ui.theme.skeleton

/* ────────────────────────────────────────────────────────────────────────────
 *  PinBom · یک بورد
 *  The cover collage owns the top; owners get edit/delete actions. The pins
 *  below use the same wall and long-press bubble as everywhere else.
 * ──────────────────────────────────────────────────────────────────────────── */

@Composable
fun BoardScreen(
    nav: NavHostController,
    id: Long,
    session: SessionManager,
    vm: BoardDetailViewModel = hiltViewModel(),
    boardsVm: BoardsViewModel = hiltViewModel(),
    pinTools: ir.pinbom.app.ui.BoardPinToolsViewModel = hiltViewModel(),
) {
    LaunchedEffect(id) { vm.load(id) }
    LaunchedEffect(Unit) { boardsVm.load() }

    val board by vm.item.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val failed by vm.failed.collectAsStateWithLifecycle()
    val myBoards by boardsVm.items.collectAsStateWithLifecycle()
    val currentUserId by boardsVm.currentUserId.collectAsStateWithLifecycle()
    val lastAction by boardsVm.lastAction.collectAsStateWithLifecycle()

    val toast = rememberPinBomToastState()
    var editOpen by remember { mutableStateOf(false) }
    var editTitle by remember { mutableStateOf("") }
    var editDesc by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var inviteOpen by remember { mutableStateOf(false) }
    var inviteUsername by remember { mutableStateOf("") }
    var inviteRole by remember { mutableStateOf("viewer") }
    var pinMenu by remember { mutableStateOf<Pin?>(null) }
    var pinMove by remember { mutableStateOf<Pin?>(null) }
    var pinRemove by remember { mutableStateOf<Pin?>(null) }
    val pinSignal by pinTools.signal.collectAsStateWithLifecycle()
    val pinBusy by pinTools.busy.collectAsStateWithLifecycle()
    val members by vm.members.collectAsStateWithLifecycle()
    val inviteBusy by vm.inviteBusy.collectAsStateWithLifecycle()
    val inviteMessage by vm.inviteMessage.collectAsStateWithLifecycle()
    ExtrasSignalToast(pinSignal, toast)

    /* Ownership is not in the board payload, so we match it against the boards
     * endpoint (one request, no false positives). */
    val mine = board?.user_id == currentUserId && currentUserId > 0
    val invitedToastLabel = stringResource(R.string.board_invite_sent)

    val updatedLabel = stringResource(R.string.board_updated)
    val errorLabel = stringResource(R.string.state_error_title)

    LaunchedEffect(lastAction) {
        when (lastAction.substringBefore(':')) {
            "updated" -> toast.show(updatedLabel)
            "failed" -> toast.show(errorLabel)
        }
    }

    Box(Modifier.fillMaxSize()) {
        val current = board
        when {
            current == null && failed && !loading -> ErrorState(
                onRetry = { vm.load(id) },
                modifier = Modifier.align(Alignment.Center),
            )

            current == null -> BoardSkeleton()

            else -> BoardWall(
                board = current,
                mine = mine,
                canEditPins = current?.can_edit == true,
                baseUrl = session.baseUrl,
                onOpenPin = { pin -> nav.navigate(Routes.pin(pin.id)) },
                onEdit = {
                    editTitle = current.title
                    editDesc = current.description
                    editOpen = true
                },
                onDelete = { confirmDelete = true },
                onInvite = { inviteUsername = ""; inviteOpen = true; vm.loadMembers(id) },
                onPinMore = { pinMenu = it },
            )
        }

        /* Floating chrome — the header scrolls, this never does. */
        IconCircleButton(
            icon = PinBomIcons.ArrowBack,
            contentDescription = stringResource(R.string.action_back),
            onClick = { nav.popBackStack() },
            containerColor = PinBomTheme.colors.surface,
            modifier = Modifier.padding(horizontal = Dimens.sm, vertical = Dimens.sm),
        )

        ActionToastHost(
            state = toast,
            modifier = Modifier.align(Alignment.BottomCenter),
            baseUrl = session.baseUrl,
        )
    }

    LaunchedEffect(inviteMessage) {
        if (inviteMessage.startsWith("sent:")) toast.show(invitedToastLabel)
    }

    if (inviteOpen) {
        PinBomBottomSheet(onDismissRequest = { inviteOpen = false }) {
            SheetHeader(title = stringResource(R.string.board_invite_title))
            ContentTextField(
                value = inviteUsername,
                onValueChange = { inviteUsername = it.take(32) },
                placeholder = stringResource(R.string.board_invite_username),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Dimens.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                PinBomChip(stringResource(R.string.board_role_viewer), inviteRole == "viewer", { inviteRole = "viewer" })
                PinBomChip(stringResource(R.string.board_role_editor), inviteRole == "editor", { inviteRole = "editor" })
            }
            if (inviteMessage.startsWith("failed:")) {
                Text(inviteMessage.removePrefix("failed:"), style = PinBomText.caption, color = PinBomTheme.colors.primary)
            }
            Spacer(Modifier.height(Dimens.md))
            PrimaryPillButton(
                text = stringResource(R.string.board_invite_action),
                enabled = inviteUsername.isNotBlank() && !inviteBusy,
                loading = inviteBusy,
                fillWidth = true,
                onClick = { vm.invite(id, inviteUsername, inviteRole) { inviteUsername = "" } },
            )
            if (members.isNotEmpty()) {
                Spacer(Modifier.height(Dimens.lg))
                SheetHeader(title = stringResource(R.string.board_members_title))
                members.forEach { member ->
                    Text(
                        text = "${member.display_name.ifBlank { member.username }}  ·  ${if (member.owner) stringResource(R.string.board_role_owner) else if (member.role == "editor") stringResource(R.string.board_role_editor) else stringResource(R.string.board_role_viewer)}",
                        style = PinBomText.bodySmall,
                        color = PinBomTheme.colors.textSecondary,
                    )
                }
            }
        }
    }

    pinMenu?.let { target ->
        PinBomBottomSheet(onDismissRequest = { pinMenu = null }) {
            SheetAction(PinBomIcons.BookmarkAdd, stringResource(R.string.board_pin_move)) {
                pinMenu = null
                pinMove = target
            }
            SheetAction(PinBomIcons.Delete, stringResource(R.string.board_pin_remove), destructive = true) {
                pinMenu = null
                pinRemove = target
            }
        }
    }

    pinMove?.let { target ->
        val others = myBoards.filter { it.id != id }
        PinBomBottomSheet(onDismissRequest = { pinMove = null }) {
            SheetHeader(title = stringResource(R.string.board_pin_move_title))
            if (others.isEmpty()) {
                Text(
                    text = stringResource(R.string.board_pin_no_other_boards),
                    style = PinBomText.bodySmall,
                    color = PinBomTheme.colors.textSecondary,
                )
            }
            others.forEach { destination ->
                SheetAction(PinBomIcons.Bookmark, destination.title) {
                    pinMove = null
                    pinTools.move(id, target.id, destination.id) { vm.load(id) }
                }
            }
        }
    }

    pinRemove?.let { target ->
        PinBomDialog(onDismissRequest = { pinRemove = null }) {
            PinBomDialogContent(
                title = stringResource(R.string.board_pin_remove),
                body = stringResource(R.string.board_pin_remove_confirm),
                confirmLabel = stringResource(R.string.board_pin_remove),
                onConfirm = {
                    pinRemove = null
                    pinTools.remove(id, target.id) { vm.load(id) }
                },
                onDismiss = { pinRemove = null },
                dismissLabel = stringResource(R.string.action_cancel),
                destructive = true,
            )
        }
    }

    val current = board
    if (editOpen && current != null) {
        PinBomBottomSheet(onDismissRequest = { editOpen = false }) {
            SheetHeader(title = stringResource(R.string.board_edit_title))
            ContentTextField(
                value = editTitle,
                onValueChange = { editTitle = it },
                placeholder = stringResource(R.string.pin_board_name),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Dimens.sm))
            ContentTextField(
                value = editDesc,
                onValueChange = { editDesc = it },
                placeholder = stringResource(R.string.board_description_hint),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Dimens.xl))
            PrimaryPillButton(
                text = stringResource(R.string.action_save),
                enabled = editTitle.isNotBlank(),
                fillWidth = true,
                onClick = {
                    boardsVm.update(id, editTitle.trim(), editDesc.trim())
                    editOpen = false
                    vm.load(id)
                },
            )
        }
    }

    if (confirmDelete) {
        PinBomDialog(onDismissRequest = { confirmDelete = false }) {
            PinBomDialogContent(
                title = stringResource(R.string.board_delete_title),
                body = stringResource(R.string.board_delete_body),
                confirmLabel = stringResource(R.string.action_delete),
                onConfirm = {
                    confirmDelete = false
                    boardsVm.delete(id)
                    nav.popBackStack()
                },
                onDismiss = { confirmDelete = false },
                dismissLabel = stringResource(R.string.action_cancel),
                destructive = true,
            )
        }
    }
}

/* ── The wall ─────────────────────────────────────────────────────────────── */

@Composable
private fun BoardWall(
    board: BoardDetail,
    mine: Boolean,
    canEditPins: Boolean = false,
    baseUrl: String,
    onOpenPin: (Pin) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onInvite: () -> Unit = {},
    onPinMore: (Pin) -> Unit = {},
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = pinGridColumns(maxWidth)
        val wallState = rememberLazyStaggeredGridState()
        val parallaxScrollY: () -> Float = {
            if (wallState.firstVisibleItemIndex == 0) wallState.firstVisibleItemScrollOffset.toFloat() else 0f
        }
        var sharedPinIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
        LaunchedEffect(wallState) {
            snapshotFlow {
                if (wallState.isScrollInProgress) emptySet()
                else wallState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? Long }.toSet()
            }.distinctUntilChanged().collect { ids -> sharedPinIds = ids }
        }

        LazyVerticalStaggeredGrid(
            state = wallState,
            columns = columns,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Dimens.grid,
                end = Dimens.grid,
                top = Dimens.huge,
                bottom = Dimens.huge,
            ),
            verticalItemSpacing = Dimens.gridGap,
            horizontalArrangement = Arrangement.spacedBy(Dimens.gridGap),
        ) {
            item(span = StaggeredGridItemSpan.FullLine, key = "board-header") {
                BoardHeader(
                    board = board,
                    mine = mine,
                    baseUrl = baseUrl,
                    onEdit = onEdit,
                    onDelete = onDelete,
                    onInvite = onInvite,
                    parallaxScrollY = parallaxScrollY,
                )
            }

            if (board.pins.isEmpty()) {
                item(span = StaggeredGridItemSpan.FullLine, key = "board-empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Dimens.xxxl),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyState(
                            title = stringResource(R.string.state_empty_title),
                            description = stringResource(R.string.state_empty_body),
                            compact = true,
                        )
                    }
                }
            }

            items(items = board.pins, key = { it.id }, contentType = { "pin" }) { pin ->
                Box {
                    PinQuickCard(pin = pin, baseUrl = baseUrl, onOpen = onOpenPin, sharedBounds = pin.id in sharedPinIds)
                    if (canEditPins) {
                        IconCircleButton(
                            icon = PinBomIcons.MoreHoriz,
                            contentDescription = stringResource(R.string.extras_more),
                            onClick = { onPinMore(pin) },
                            size = 32.dp,
                            iconSize = 16.dp,
                            containerColor = PinBomTheme.colors.surface,
                            modifier = Modifier.align(Alignment.TopEnd).padding(Dimens.sm),
                        )
                    }
                }
            }
        }
    }
}

/* ── Header ───────────────────────────────────────────────────────────────── */

@Composable
private fun BoardHeader(
    board: BoardDetail,
    mine: Boolean,
    baseUrl: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onInvite: () -> Unit = {},
    parallaxScrollY: () -> Float = { 0f },
) {
    val colors = PinBomTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.screen, vertical = Dimens.sm),
    ) {
        BoardCoverCollage(
            covers = board.pins.take(3).map { it.image_url },
            seed = board.id,
            baseUrl = baseUrl,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.7f)
                .graphicsLayer { translationY = parallaxScrollY() * 0.14f },
        )
        Spacer(Modifier.height(Dimens.lg))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ContentText(
                text = board.title,
                style = PinBomText.titleLarge,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (board.private_board) {
                Spacer(Modifier.width(Dimens.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = PinBomIcons.Lock,
                        contentDescription = null,
                        tint = colors.textTertiary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(Dimens.xxs))
                    Text(
                        text = stringResource(R.string.board_private),
                        style = PinBomText.caption,
                        color = colors.textTertiary,
                    )
                }
            }
        }
        if (board.description.isNotBlank()) {
            Spacer(Modifier.height(Dimens.xs))
            ContentText(
                text = board.description,
                style = PinBomText.body,
                color = colors.textSecondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(Dimens.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PinCountText(count = board.pins.size)
            Spacer(Modifier.width(Dimens.md))
            RelativeTimeText(iso = board.created_at)
        }
        Spacer(Modifier.height(Dimens.lg))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (mine) {
                SecondaryPillButton(
                    text = stringResource(R.string.board_invite_action),
                    onClick = onInvite,
                    leadingIcon = PinBomIcons.PersonAdd,
                    size = PillButtonSize.Small,
                )
                SecondaryPillButton(
                    text = stringResource(R.string.action_edit),
                    onClick = onEdit,
                    leadingIcon = PinBomIcons.Edit,
                    size = PillButtonSize.Small,
                )
            }
            if (mine) {
                IconCircleButton(
                    icon = PinBomIcons.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    onClick = onDelete,
                    containerColor = colors.surfaceGrey,
                )
            }
        }
    }
}

/* ── Loading ──────────────────────────────────────────────────────────────── */

@Composable
private fun BoardSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.screen, vertical = Dimens.sm),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.7f)
                .skeleton(shape = PinBomShape.boardCover),
        )
        Spacer(Modifier.height(Dimens.lg))
        SkeletonLine(widthFraction = 0.52f, height = 18.dp)
        Spacer(Modifier.height(Dimens.sm))
        SkeletonLine(widthFraction = 0.3f, height = 10.dp)
        Spacer(Modifier.height(Dimens.lg))
        Box(
            modifier = Modifier
                .size(Dimens.buttonSmall)
                .skeleton(shape = PinBomShape.pill),
        )
        Spacer(Modifier.height(Dimens.lg))
        PinGridSkeleton(
            columns = StaggeredGridCells.Fixed(2),
            itemCount = 6,
            modifier = Modifier.weight(1f),
        )
    }
}

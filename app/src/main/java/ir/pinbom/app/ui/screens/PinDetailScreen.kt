package ir.pinbom.app.ui.screens

import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import android.widget.Toast
import ir.pinbom.app.ui.components.icons.PinBomIcons

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import ir.pinbom.app.R
import ir.pinbom.app.data.SessionManager
import ir.pinbom.app.data.remote.Board
import ir.pinbom.app.data.remote.Comment
import ir.pinbom.app.data.remote.Pin
import ir.pinbom.app.ui.BoardsViewModel
import ir.pinbom.app.ui.PinViewModel
import ir.pinbom.app.ui.components.ActionToastHost
import ir.pinbom.app.ui.components.Avatar
import ir.pinbom.app.ui.components.BoardCreateRow
import ir.pinbom.app.ui.components.BoardRow
import ir.pinbom.app.ui.components.PinBomBottomSheet
import ir.pinbom.app.ui.components.PinBomChip
import ir.pinbom.app.ui.components.PinBomDialog
import ir.pinbom.app.ui.components.PinBomDialogContent
import ir.pinbom.app.ui.components.PinBomTextField
import ir.pinbom.app.ui.components.CountText
import ir.pinbom.app.ui.components.ErrorState
import ir.pinbom.app.ui.components.HeartBurst
import ir.pinbom.app.ui.components.fx.PinBomParticles
import ir.pinbom.app.ui.components.fx.ParticleBurstKind
import ir.pinbom.app.ui.components.IconCircleButton
import ir.pinbom.app.ui.components.PillButtonSize
import ir.pinbom.app.ui.components.PinCard
import ir.pinbom.app.ui.components.PinImage
import ir.pinbom.app.ui.components.PrimaryPillButton
import ir.pinbom.app.ui.components.RelativeTimeText
import ir.pinbom.app.ui.components.SecondaryPillButton
import ir.pinbom.app.ui.components.SectionHeader
import ir.pinbom.app.ui.components.SheetHeader
import ir.pinbom.app.ui.components.SkeletonLine
import ir.pinbom.app.ui.components.imageUrl
import ir.pinbom.app.ui.components.rememberPinImageDownloader
import ir.pinbom.app.ui.components.rememberPinBomImageSharer
import ir.pinbom.app.ui.components.PinImagePreferences
import ir.pinbom.app.ui.components.pinAspectRatio
import ir.pinbom.app.ui.components.rememberPinBomToastState
import ir.pinbom.app.ui.components.ContentText
import ir.pinbom.app.ui.components.ContentTextField
import ir.pinbom.app.ui.nav.Routes
import ir.pinbom.app.ui.nav.sharedPinBounds
import ir.pinbom.app.ui.theme.PinBomShape
import ir.pinbom.app.ui.theme.PinBomText
import ir.pinbom.app.ui.theme.PinBomTheme
import ir.pinbom.app.ui.theme.Dimens
import ir.pinbom.app.ui.theme.Motion
import ir.pinbom.app.ui.theme.pinbomClick
import ir.pinbom.app.ui.theme.reduceMotion
import ir.pinbom.app.ui.theme.skeleton

/* ────────────────────────────────────────────────────────────────────────────
 *  PinBom · صفحهٔ پین
 *  One masonry wall: the pin owns the top as a rounded hero, then the author,
 *  the title, the action bar, the comments and finally the same wall of related
 *  pins the feed uses. Double-tap the hero for the heart burst, tap it for the
 *  fullscreen viewer, and save through the sheet — the toast offers «تغییر».
 * ──────────────────────────────────────────────────────────────────────────── */

@Composable
fun PinDetailScreen(
    nav: NavHostController,
    id: Long,
    session: SessionManager,
    vm: PinViewModel = hiltViewModel(),
    boardsVm: BoardsViewModel = hiltViewModel(),
    commentTools: ir.pinbom.app.ui.CommentToolsViewModel = hiltViewModel(),
    actionTools: ir.pinbom.app.ui.PinActionViewModel = hiltViewModel(),
) {
    LaunchedEffect(id) { vm.load(id) }

    val pin by vm.pin.collectAsStateWithLifecycle()
    val comments by vm.comments.collectAsStateWithLifecycle()
    val commentsCursor by vm.commentsCursor.collectAsStateWithLifecycle()
    val commentsLoadingMore by vm.commentsLoadingMore.collectAsStateWithLifecycle()
    val related by vm.related.collectAsStateWithLifecycle()
    val boards by boardsVm.items.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val failed by vm.failed.collectAsStateWithLifecycle()
    val actionError by vm.actionError.collectAsStateWithLifecycle()
    val actionErrorMessage = stringResource(R.string.state_error_body)
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    /* Localized resources (the app language), for text that leaves Compose. */
    val resources = LocalResources.current
    val toast = rememberPinBomToastState()
    val reduce = reduceMotion()

    var saveSheet by remember { mutableStateOf(false) }
    var newBoardDialog by remember { mutableStateOf(false) }
    var newBoardTitle by remember { mutableStateOf("") }
    var viewerOpen by remember { mutableStateOf(false) }
    var likeTrigger by remember { mutableStateOf(0) }
    var saveTrigger by remember { mutableStateOf(0) }
    var draft by remember { mutableStateOf("") }
    var contentMenuOpen by remember { mutableStateOf(false) }
    val actionScope = rememberCoroutineScope()

    /* Comment tools: edit / delete for the author, report for everyone else. */
    var commentMenu by remember { mutableStateOf<Comment?>(null) }
    var commentEdit by remember { mutableStateOf<Comment?>(null) }
    var commentEditText by remember { mutableStateOf("") }
    var commentDelete by remember { mutableStateOf<Comment?>(null) }
    var commentReport by remember { mutableStateOf<Comment?>(null) }
    val commentSignal by commentTools.signal.collectAsStateWithLifecycle()
    val commentBusy by commentTools.busy.collectAsStateWithLifecycle()

    LaunchedEffect(saveSheet) { if (saveSheet) boardsVm.load() }
    LaunchedEffect(actionError) {
        if (actionError > 0) Toast.makeText(context, actionErrorMessage, Toast.LENGTH_SHORT).show()
    }

    /* Hoisted labels: `stringResource` cannot be called from a click lambda. */
    val downloadLabel = stringResource(R.string.action_download)
    val downloader = rememberPinImageDownloader(
        baseUrl = session.baseUrl,
        successLabel = stringResource(R.string.download_success),
        failureLabel = stringResource(R.string.download_failed),
    )
    val shareAction = rememberPinBomImageSharer(
        baseUrl = session.baseUrl,
        failureLabel = stringResource(R.string.share_image_failed),
        chooserLabel = stringResource(R.string.share_image_chooser),
        onFailure = { toast.show(it) },
    )
    val saveLabel = stringResource(R.string.action_save)
    val savedLabel = stringResource(R.string.action_saved)
    val changeLabel = stringResource(R.string.action_change)
    val feedbackSavedText = stringResource(R.string.pin_feedback_saved)
    val mutedUserText = stringResource(R.string.pin_user_muted)
    val genericActionErrorText = stringResource(R.string.state_error_title)

    val item = pin

    /* Hero: a 6% push plus a fade. When SharedTransitionLayout is enabled,
     * the matching pin card bounds morph into this image, and Lite mode skips it. */
    val hero = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(item?.id) {
        if (item != null) {
            if (reduce) {
                hero.snapTo(1f)
            } else {
                hero.snapTo(0f)
                hero.animateTo(1f, tween(durationMillis = Motion.long, easing = Motion.emphasized))
            }
        }
    }

    fun fireToast(message: String) = toast.show(
        message = message,
        coverUrl = item?.image_url,
        coverSeed = item?.id ?: 0L,
        actionLabel = changeLabel,
        onAction = { saveSheet = true },
    )

    Box(Modifier.fillMaxSize()) {
        when {
            item != null -> PinWall(
                pin = item,
                comments = comments,
                commentsHasMore = commentsCursor.isNotBlank(),
                commentsLoadingMore = commentsLoadingMore,
                onLoadMoreComments = { vm.loadMoreComments() },
                myUsername = session.username,
                onCommentMore = { commentMenu = it },
                related = related,
                baseUrl = session.baseUrl,
                hero = { hero.value },
                likeTrigger = likeTrigger,
                saveTrigger = saveTrigger,
                draft = draft,
                onDraftChange = { draft = it },
                onOpenViewer = { viewerOpen = true },
                onDoubleTapHero = {
                    if (!item.liked) vm.like()
                    likeTrigger++
                },
                onTagClick = { tag -> nav.navigate(Routes.tag(tag)) },
                onAuthorClick = {
                    item.author?.username
                        ?.takeIf { it.isNotBlank() }
                        ?.let { nav.navigate(Routes.profile(it)) }
                },
                onSave = { saveSheet = true },
                onLike = {
                    if (!item.liked) likeTrigger++
                    vm.like()
                },
                onSendComment = {
                    vm.comment(draft)
                    draft = ""
                },
                onRelatedClick = { relatedPin -> nav.navigate(Routes.pin(relatedPin.id)) },
                saveLabel = if (item.saved) savedLabel else saveLabel,
            )

            failed && !loading -> ErrorState(
                onRetry = { vm.load(id) },
                modifier = Modifier.align(Alignment.Center),
            )

            else -> PinDetailSkeleton()
        }

        /* Floating chrome — outside the scroll, always reachable. */
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.sm, vertical = Dimens.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconCircleButton(
                icon = PinBomIcons.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                onClick = { nav.popBackStack() },
                containerColor = PinBomTheme.colors.surface,
            )
            Spacer(Modifier.weight(1f))
            if (item != null) {
                if (shareAction.preparing) {
                    Box(
                        modifier = Modifier.size(Dimens.iconButton).clip(CircleShape).background(PinBomTheme.colors.surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = PinBomTheme.colors.primary,
                        )
                    }
                } else {
                    IconCircleButton(
                        icon = PinBomIcons.Share,
                        contentDescription = stringResource(R.string.action_send_image),
                        onClick = { shareAction.share(item.id, item.image_url) },
                        containerColor = PinBomTheme.colors.surface,
                    )
                }
                IconCircleButton(
                    icon = PinBomIcons.Download,
                    contentDescription = downloadLabel,
                    onClick = { downloader(item.id, item.image_url) },
                    containerColor = PinBomTheme.colors.surface,
                )
                IconCircleButton(
                    icon = PinBomIcons.MoreHoriz,
                    contentDescription = stringResource(R.string.extras_more),
                    onClick = { contentMenuOpen = true },
                    containerColor = PinBomTheme.colors.surface,
                )
            }
        }

        ActionToastHost(
            state = toast,
            modifier = Modifier.align(Alignment.BottomCenter),
            baseUrl = session.baseUrl,
        )
    }

    ExtrasSignalToast(commentSignal, toast)

    if (contentMenuOpen && item != null) {
        PinBomBottomSheet(onDismissRequest = { contentMenuOpen = false }) {
            SheetHeader(title = stringResource(R.string.pin_content_controls))
            SheetAction(PinBomIcons.Visibility, stringResource(R.string.pin_not_interested), destructive = true) {
                contentMenuOpen = false
                actionScope.launch {
                    if (actionTools.feedback(item.id, "not_interested")) toast.show(feedbackSavedText)
                    else toast.show(genericActionErrorText)
                }
            }
            SheetAction(PinBomIcons.Tune, stringResource(R.string.pin_less_topic)) {
                contentMenuOpen = false
                actionScope.launch {
                    if (actionTools.feedback(item.id, "less_topic")) toast.show(feedbackSavedText)
                    else toast.show(genericActionErrorText)
                }
            }
            if (item.author_id > 0 && item.author?.username != session.username) {
                SheetAction(PinBomIcons.Person, stringResource(R.string.pin_mute_author), destructive = true) {
                    contentMenuOpen = false
                    actionScope.launch {
                        if (actionTools.muteUser(item.author_id)) toast.show(mutedUserText)
                        else toast.show(genericActionErrorText)
                    }
                }
            }
            SheetAction(PinBomIcons.Search, stringResource(R.string.pin_find_similar)) {
                contentMenuOpen = false
                nav.navigate(Routes.similar(item.id))
            }
        }
    }

    commentMenu?.let { target ->
        val mine = target.user.username == session.username
        PinBomBottomSheet(onDismissRequest = { commentMenu = null }) {
            if (mine) {
                SheetAction(PinBomIcons.Edit, stringResource(R.string.comment_edit)) {
                    commentMenu = null
                    commentEditText = target.body
                    commentEdit = target
                }
                SheetAction(PinBomIcons.Delete, stringResource(R.string.comment_delete), destructive = true) {
                    commentMenu = null
                    commentDelete = target
                }
            } else {
                SheetAction(PinBomIcons.Visibility, stringResource(R.string.comment_report), destructive = true) {
                    commentMenu = null
                    commentReport = target
                }
            }
        }
    }

    commentEdit?.let { target ->
        PinBomDialog(onDismissRequest = { commentEdit = null }) {
            Text(
                text = stringResource(R.string.comment_edit),
                style = PinBomText.title,
                color = PinBomTheme.colors.textPrimary,
            )
            Spacer(Modifier.height(Dimens.md))
            PinBomTextField(
                value = commentEditText,
                onValueChange = { commentEditText = it.take(1000) },
                singleLine = false,
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Dimens.xl))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.sm, Alignment.End),
            ) {
                SecondaryPillButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { commentEdit = null },
                    size = PillButtonSize.Small,
                )
                PrimaryPillButton(
                    text = stringResource(R.string.action_save),
                    enabled = commentEditText.isNotBlank() && commentEditText.trim() != target.body,
                    loading = commentBusy,
                    onClick = {
                        commentTools.edit(id, target.id, commentEditText) { edited ->
                            vm.comments.value = vm.comments.value.map {
                                if (it.id == target.id) it.copy(body = edited.body.ifBlank { commentEditText.trim() }) else it
                            }
                            commentEdit = null
                        }
                    },
                    size = PillButtonSize.Small,
                )
            }
        }
    }

    commentDelete?.let { target ->
        PinBomDialog(onDismissRequest = { commentDelete = null }) {
            PinBomDialogContent(
                title = stringResource(R.string.comment_delete),
                body = stringResource(R.string.comment_delete_confirm),
                confirmLabel = stringResource(R.string.comment_delete),
                onConfirm = {
                    commentDelete = null
                    commentTools.delete(id, target.id) {
                        vm.comments.value = vm.comments.value.filterNot { it.id == target.id }
                        vm.load(id)
                    }
                },
                onDismiss = { commentDelete = null },
                dismissLabel = stringResource(R.string.action_cancel),
                destructive = true,
            )
        }
    }

    commentReport?.let { target ->
        ReportSheet(
            title = stringResource(R.string.comment_report),
            onDismiss = { commentReport = null },
            busy = commentBusy,
            onSubmit = { reason, note ->
                commentReport = null
                commentTools.report(id, target.id, reason, note)
            },
        )
    }

    if (saveSheet && item != null) {
        SaveSheet(
            boards = boards,
            hint = stringResource(R.string.pin_save_hint),
            baseUrl = session.baseUrl,
            coverUrl = imageUrl(session.baseUrl, item.image_url),
            coverSeed = item.id,
            onDismiss = { saveSheet = false },
            onBoard = { board ->
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                vm.save(board.id)
                saveTrigger++
                saveSheet = false
                fireToast(
                    message = resources.getString(R.string.pin_saved_to, board.title),
                )
            },
            onNewBoard = { newBoardDialog = true },
        )
    }

    if (newBoardDialog) {
        PinBomDialog(onDismissRequest = { newBoardDialog = false }) {
            Text(
                text = stringResource(R.string.pin_new_board),
                style = PinBomText.title,
                color = PinBomTheme.colors.textPrimary,
            )
            Spacer(Modifier.height(Dimens.md))
            ContentTextField(
                value = newBoardTitle,
                onValueChange = { newBoardTitle = it },
                placeholder = stringResource(R.string.pin_board_name),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Dimens.xl))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.sm, Alignment.End),
            ) {
                SecondaryPillButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { newBoardDialog = false },
                    size = PillButtonSize.Small,
                )
                PrimaryPillButton(
                    text = stringResource(R.string.action_done),
                    enabled = newBoardTitle.isNotBlank(),
                    onClick = {
                        boardsVm.create(newBoardTitle, "")
                        newBoardTitle = ""
                        newBoardDialog = false
                    },
                    size = PillButtonSize.Small,
                )
            }
        }
    }

    if (viewerOpen && item != null) {
        ImageViewer(
            pin = item,
            baseUrl = session.baseUrl,
            onDismiss = { viewerOpen = false },
        )
    }
}

/* ── The wall ─────────────────────────────────────────────────────────────── */

@Composable
private fun PinWall(
    pin: Pin,
    comments: List<Comment>,
    commentsHasMore: Boolean,
    commentsLoadingMore: Boolean,
    onLoadMoreComments: () -> Unit,
    related: List<Pin>,
    baseUrl: String,
    hero: () -> Float,
    likeTrigger: Int,
    saveTrigger: Int,
    draft: String,
    onDraftChange: (String) -> Unit,
    onOpenViewer: () -> Unit,
    onDoubleTapHero: () -> Unit,
    onAuthorClick: () -> Unit,
    onTagClick: (String) -> Unit,
    onSave: () -> Unit,
    onLike: () -> Unit,
    onSendComment: () -> Unit,
    onRelatedClick: (Pin) -> Unit,
    saveLabel: String,
    myUsername: String = "",
    onCommentMore: (Comment) -> Unit = {},
) {
    val colors = PinBomTheme.colors

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = StaggeredGridCells.Fixed(
            when {
                maxWidth < 600.dp -> 2
                maxWidth < 900.dp -> 3
                else -> 4
            },
        )

        LazyVerticalStaggeredGrid(
            columns = columns,
            modifier = Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(
                start = Dimens.grid,
                end = Dimens.grid,
                bottom = Dimens.huge,
            ),
            verticalItemSpacing = Dimens.gridGap,
            horizontalArrangement = Arrangement.spacedBy(Dimens.gridGap),
        ) {
            item(span = StaggeredGridItemSpan.FullLine) {
                PinHero(
                    pin = pin,
                    baseUrl = baseUrl,
                    hero = hero,
                    likeTrigger = likeTrigger,
                    saveTrigger = saveTrigger,
                    onOpen = onOpenViewer,
                    onDoubleTap = onDoubleTapHero,
                )
            }

            item(span = StaggeredGridItemSpan.FullLine) {
                AuthorRow(pin = pin, baseUrl = baseUrl, onClick = onAuthorClick)
            }

            item(span = StaggeredGridItemSpan.FullLine) {
                Column(Modifier.padding(horizontal = Dimens.screen)) {
                    if (pin.title.isNotBlank()) {
                        ContentText(
                            text = pin.title,
                            style = PinBomText.titleLarge,
                            color = colors.textPrimary,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(Dimens.xs))
                    }
                    if (pin.description.isNotBlank()) {
                        ContentText(
                            text = pin.description,
                            style = PinBomText.body,
                            color = colors.textSecondary,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (pin.tags.isNotEmpty()) {
                        Spacer(Modifier.height(Dimens.sm))
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                            pin.tags.forEach { tag -> PinBomChip(text = "#$tag", selected = false, onClick = { onTagClick(tag) }) }
                        }
                    }
                    Spacer(Modifier.height(Dimens.md))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CountText(
                            count = pin.likes.toLong(),
                            icon = PinBomIcons.Favorite,
                            color = if (pin.liked) colors.primary else colors.textSecondary,
                        )
                        Spacer(Modifier.width(Dimens.md))
                        CountText(
                            count = pin.comments.toLong(),
                            icon = PinBomIcons.ModeComment,
                            color = colors.textTertiary,
                        )
                        Spacer(Modifier.width(Dimens.md))
                        RelativeTimeText(iso = pin.created_at)
                    }
                }
            }

            item(span = StaggeredGridItemSpan.FullLine) {
                ActionBar(
                    saved = pin.saved,
                    liked = pin.liked,
                    saveLabel = saveLabel,
                    onSave = onSave,
                    onLike = onLike,
                )
            }

            item(span = StaggeredGridItemSpan.FullLine) {
                SectionHeader(title = stringResource(R.string.pin_comments))
            }

            if (comments.isEmpty()) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    Text(
                        text = stringResource(R.string.pin_no_comments),
                        style = PinBomText.bodySmall,
                        color = colors.textTertiary,
                        modifier = Modifier.padding(
                            start = Dimens.screen,
                            end = Dimens.screen,
                            bottom = Dimens.md,
                        ),
                    )
                }
            } else {
                items(
                    count = comments.size,
                    key = { index -> comments[index].id },
                    span = { StaggeredGridItemSpan.FullLine },
                ) { index ->
                    CommentRow(comment = comments[index], baseUrl = baseUrl, onMore = { onCommentMore(comments[index]) })
                }
            }

            if (commentsHasMore) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TextButton(onClick = onLoadMoreComments, enabled = !commentsLoadingMore) {
                            Text(stringResource(R.string.action_load_more))
                        }
                    }
                }
            }

            item(span = StaggeredGridItemSpan.FullLine) {
                CommentComposer(
                    value = draft,
                    onValueChange = onDraftChange,
                    onSend = onSendComment,
                )
            }

            item(span = StaggeredGridItemSpan.FullLine) {
                SectionHeader(title = stringResource(R.string.pin_related))
            }

            items(items = related, key = { it.id }, contentType = { "pin" }) { relatedPin ->
                PinCard(
                    pin = relatedPin,
                    baseUrl = baseUrl,
                    onSaveClick = { onRelatedClick(relatedPin) },
                    onClick = { onRelatedClick(relatedPin) },
                )
            }
        }
    }
}

/* ── Hero ─────────────────────────────────────────────────────────────────── */

@Composable
private fun PinHero(
    pin: Pin,
    baseUrl: String,
    hero: () -> Float,
    likeTrigger: Int,
    saveTrigger: Int,
    onOpen: () -> Unit,
    onDoubleTap: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.screen, vertical = Dimens.sm)
            .sharedPinBounds(pin.id)
            /* The entrance value is read *here*, in the layer: reading it in
             * composition would recompose the whole wall on every frame of the
             * 380ms pop-in. */
            .graphicsLayer {
                val progress = hero()
                val scale = 0.94f + 0.06f * progress
                scaleX = scale
                scaleY = scale
                alpha = progress.coerceIn(0f, 1f)
            },
    ) {
        PinImage(
            url = imageUrl(baseUrl, pin.image_url, if (PinImagePreferences.dataSaverEnabled) "medium" else null),
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    hapticFeedbackEnabled = false,
                    onDoubleClick = onDoubleTap,
                    onClick = onOpen,
                ),
            seed = pin.id,
            contentDescription = stringResource(R.string.cd_pin_image, pin.title),
            shape = PinBomShape.pin,
            aspectRatio = pinAspectRatio(pin),
            contentScale = ContentScale.Crop,
        )

        HeartBurst(
            trigger = likeTrigger,
            color = PinBomTheme.colors.primary,
            modifier = Modifier.matchParentSize(),
        )
        /* One native-physics stage for every burst on the hero: hearts + a ring on like,
         * sparkles on save. Idle it costs nothing; it honours reduced motion. */
        val fx = ir.pinbom.app.ui.components.fx.rememberParticleController()
        ir.pinbom.app.ui.components.fx.ParticleStage(fx, Modifier.matchParentSize())
        ir.pinbom.app.ui.components.fx.BurstOnTrigger(likeTrigger, fx, ir.pinbom.app.ui.components.fx.FxPreset.Heart, count = 26)
        ir.pinbom.app.ui.components.fx.BurstOnTrigger(likeTrigger, fx, ir.pinbom.app.ui.components.fx.FxPreset.Ring, count = 16)
        ir.pinbom.app.ui.components.fx.BurstOnTrigger(saveTrigger, fx, ir.pinbom.app.ui.components.fx.FxPreset.Sparkle, count = 24)
        val fxContext = androidx.compose.ui.platform.LocalContext.current
        LaunchedEffect(likeTrigger) { if (likeTrigger > 0) ir.pinbom.app.fx.FxHaptics.pop(fxContext) }
        LaunchedEffect(saveTrigger) { if (saveTrigger > 0) ir.pinbom.app.fx.FxHaptics.tick(fxContext) }
    }
}

/* ── Author ───────────────────────────────────────────────────────────────── */

@Composable
private fun AuthorRow(pin: Pin, baseUrl: String, onClick: () -> Unit) {
    val colors = PinBomTheme.colors
    val handle = pin.author?.username.orEmpty()
    val name = pin.author?.display_name?.ifBlank { handle }.orEmpty()
    val fallback = stringResource(R.string.app_name)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.screen, vertical = Dimens.sm)
            .pinbomClick(
                onClick = onClick,
                indication = ripple(bounded = true, color = colors.textSecondary),
                role = Role.Button,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            name = name.ifBlank { fallback },
            url = pin.author_avatar_url.ifBlank { null },
            baseUrl = baseUrl,
            size = 44.dp,
        )
        Spacer(Modifier.width(Dimens.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = name.ifBlank { fallback },
                style = PinBomText.title,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (handle.isNotBlank()) {
                Text(
                    text = "@$handle",
                    style = PinBomText.caption,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        SecondaryPillButton(
            text = stringResource(R.string.pin_profile),
            onClick = onClick,
            size = PillButtonSize.Small,
        )
    }
}

/* ── Actions ──────────────────────────────────────────────────────────────── */

@Composable
private fun ActionBar(
    saved: Boolean,
    liked: Boolean,
    saveLabel: String,
    onSave: () -> Unit,
    onLike: () -> Unit,
) {
    val colors = PinBomTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.screen, vertical = Dimens.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PrimaryPillButton(
            text = saveLabel,
            onClick = onSave,
            leadingIcon = if (saved) PinBomIcons.Bookmark else PinBomIcons.BookmarkAdd,
        )
        Spacer(Modifier.weight(1f))
        IconCircleButton(
            icon = if (liked) PinBomIcons.Favorite else PinBomIcons.FavoriteBorder,
            contentDescription = stringResource(R.string.cd_like),
            onClick = onLike,
            containerColor = colors.surfaceGrey,
            contentColor = if (liked) colors.primary else colors.textPrimary,
        )
    }
}

/* ── Comments ─────────────────────────────────────────────────────────────── */

@Composable
private fun CommentRow(comment: Comment, baseUrl: String, onMore: () -> Unit = {}) {
    val colors = PinBomTheme.colors
    val author = comment.user.display_name.ifBlank { comment.user.username }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.screen, vertical = Dimens.sm),
    ) {
        Avatar(name = author, url = comment.user.avatar_url.ifBlank { null }, baseUrl = baseUrl, size = 36.dp)
        Spacer(Modifier.width(Dimens.md))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = author,
                    style = PinBomText.caption,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(Dimens.sm))
                RelativeTimeText(iso = comment.created_at)
                Spacer(Modifier.weight(1f))
                IconCircleButton(
                    icon = PinBomIcons.MoreHoriz,
                    contentDescription = stringResource(R.string.extras_more),
                    onClick = onMore,
                    size = 28.dp,
                    iconSize = 16.dp,
                    contentColor = colors.textTertiary,
                )
            }
            Spacer(Modifier.height(Dimens.xxs))
            ContentText(
                text = comment.body,
                style = PinBomText.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CommentComposer(value: String, onValueChange: (String) -> Unit, onSend: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.screen, vertical = Dimens.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ContentTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = stringResource(R.string.pin_comment_hint),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Dimens.sm))
        IconCircleButton(
            icon = PinBomIcons.Send,
            contentDescription = stringResource(R.string.pin_send),
            onClick = onSend,
            enabled = value.isNotBlank(),
            containerColor = PinBomTheme.colors.surfaceGrey,
        )
    }
}

/* ── Save sheet ───────────────────────────────────────────────────────────── */

@Composable
internal fun SaveSheet(
    boards: List<Board>,
    hint: String,
    baseUrl: String,
    coverUrl: String,
    coverSeed: Long,
    onDismiss: () -> Unit,
    onBoard: (Board) -> Unit,
    onNewBoard: () -> Unit,
) {
    PinBomBottomSheet(onDismissRequest = onDismiss) {
        SheetHeader(
            title = stringResource(R.string.pin_save_to_board),
            subtitle = hint.takeIf { it.isNotBlank() },
            trailing = {
                PinImage(
                    url = coverUrl,
                    seed = coverSeed,
                    modifier = Modifier.size(44.dp),
                    shape = PinBomShape.thumb,
                    contentDescription = null,
                )
            },
        )
        if (boards.isEmpty()) {
            Text(
                text = stringResource(R.string.pin_no_boards),
                style = PinBomText.bodySmall,
                color = PinBomTheme.colors.textSecondary,
                modifier = Modifier.padding(bottom = Dimens.md),
            )
        }
        boards.forEach { board ->
            BoardRow(
                title = board.title,
                onClick = { onBoard(board) },
                modifier = Modifier.padding(bottom = Dimens.xs),
                covers = listOf(board.cover_url).filter { it.isNotBlank() },
                seed = board.id,
                baseUrl = baseUrl,
                isPrivate = board.private_board,
            )
        }
        Spacer(Modifier.height(Dimens.sm))
        BoardCreateRow(
            title = stringResource(R.string.pin_new_board),
            onClick = onNewBoard,
        )
    }
}

/* ── Fullscreen viewer ────────────────────────────────────────────────────── */

@Composable
private fun ImageViewer(pin: Pin, baseUrl: String, onDismiss: () -> Unit) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val heightPx = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height.toFloat()
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var leaving by remember { mutableStateOf(false) }

    val zoomed = scale > 1.02f
    /* The backdrop thins out as the picture is pulled away: the gesture reads as
     * “letting go of the photo”, not as a hidden button. */
    val fade = (1f - kotlin.math.abs(offsetY) / (heightPx * 0.45f)).coerceIn(0.15f, 1f)

    fun animateTo(target: Float, get: () -> Float, set: (Float) -> Unit, spec: androidx.compose.animation.core.AnimationSpec<Float>, done: () -> Unit = {}) {
        scope.launch {
            androidx.compose.animation.core.Animatable(get()).animateTo(target, spec) { set(value) }
            done()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PinBomTheme.colors.scrim.let { it.copy(alpha = it.alpha * fade) })
            /* Pull down (or up) to close — only while the photo is at rest size, so
             * panning a zoomed photo never closes it by accident. */
            .pointerInput(zoomed) {
                if (zoomed) return@pointerInput
                detectVerticalDragGestures(
                    onVerticalDrag = { change, amount ->
                        if (!leaving) {
                            change.consume()
                            offsetY += amount
                        }
                    },
                    onDragEnd = {
                        if (kotlin.math.abs(offsetY) > heightPx * 0.16f && !leaving) {
                            leaving = true
                            val target = if (offsetY > 0f) heightPx else -heightPx
                            animateTo(
                                target, { offsetY }, { offsetY = it },
                                tween(durationMillis = 220, easing = Motion.standard),
                            ) { onDismiss() }
                        } else {
                            animateTo(0f, { offsetY }, { offsetY = it }, Motion.layoutSpring)
                        }
                    },
                    onDragCancel = { animateTo(0f, { offsetY }, { offsetY = it }, Motion.layoutSpring) },
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val next = (scale * zoom).coerceIn(1f, 5f)
                    scale = next
                    if (next > 1.02f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else if (zoom != 1f) {
                        offsetX = 0f
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { if (!leaving) onDismiss() },
                    onDoubleTap = {
                        /* One quick zoom in, the next one back out. */
                        val target = if (scale > 1.02f) 1f else 2.5f
                        animateTo(target, { scale }, { scale = it }, tween(Motion.medium, easing = Motion.emphasized))
                        if (target == 1f) {
                            animateTo(0f, { offsetX }, { offsetX = it }, tween(Motion.medium, easing = Motion.emphasized))
                            animateTo(0f, { offsetY }, { offsetY = it }, tween(Motion.medium, easing = Motion.emphasized))
                        }
                    },
                )
            },
    ) {
        AsyncImage(
            model = imageUrl(baseUrl, pin.image_url, if (PinImagePreferences.dataSaverEnabled) "medium" else null),
            contentDescription = stringResource(R.string.cd_pin_image, pin.title),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                },
            contentScale = ContentScale.Fit,
        )

        IconCircleButton(
            icon = PinBomIcons.Close,
            contentDescription = stringResource(R.string.cd_close),
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Dimens.xl)
                .graphicsLayer { alpha = fade },
            containerColor = PinBomTheme.colors.surface,
        )
    }
}

/* ── Loading ──────────────────────────────────────────────────────────────── */

@Composable
private fun PinDetailSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.screen, vertical = Dimens.sm),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.82f)
                .skeleton(shape = PinBomShape.pin),
        )
        Spacer(Modifier.height(Dimens.lg))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .skeleton(shape = PinBomShape.pill),
            )
            Spacer(Modifier.width(Dimens.md))
            Column {
                SkeletonLine(widthFraction = 0.42f)
                Spacer(Modifier.height(Dimens.xs))
                SkeletonLine(widthFraction = 0.26f, height = 10.dp)
            }
        }
        Spacer(Modifier.height(Dimens.lg))
        SkeletonLine(widthFraction = 0.86f, height = 18.dp)
        Spacer(Modifier.height(Dimens.sm))
        SkeletonLine(widthFraction = 0.64f)
        Spacer(Modifier.height(Dimens.xs))
        SkeletonLine(widthFraction = 0.34f)
    }
}

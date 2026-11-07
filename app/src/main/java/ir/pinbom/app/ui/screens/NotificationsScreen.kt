package ir.pinbom.app.ui.screens

import ir.pinbom.app.ui.theme.AbsoluteWhite

import androidx.compose.foundation.layout.offset
import ir.pinbom.app.ui.components.icons.PinBomIcons
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import ir.pinbom.app.ui.nav.Destination
import ir.pinbom.app.ui.nav.ScrollToTopEvents
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import ir.pinbom.app.R
import ir.pinbom.app.data.SessionManager
import ir.pinbom.app.data.remote.Notification
import ir.pinbom.app.ui.NotificationsViewModel
import ir.pinbom.app.ui.components.Avatar
import ir.pinbom.app.ui.components.BadgeDot
import ir.pinbom.app.ui.components.PinBomPullToRefresh
import ir.pinbom.app.ui.components.EmptyState
import ir.pinbom.app.ui.components.ErrorState
import ir.pinbom.app.ui.components.ListRowSkeleton
import ir.pinbom.app.ui.components.RelativeTimeText
import ir.pinbom.app.ui.components.ContentText
import ir.pinbom.app.ui.nav.Routes
import ir.pinbom.app.ui.theme.PinBomShape
import ir.pinbom.app.ui.theme.PinBomText
import ir.pinbom.app.ui.theme.PinBomTheme
import ir.pinbom.app.ui.theme.NotificationSaveAccent
import ir.pinbom.app.ui.theme.NotificationCommentAccent
import ir.pinbom.app.ui.theme.NotificationFollowAccent
import ir.pinbom.app.ui.theme.Dimens
import ir.pinbom.app.ui.theme.pinbomClick

/* ────────────────────────────────────────────────────────────────────────────
 *  PinBom · اعلان‌ها
 *  Unread rows carry a brand ring, a tinted surface and a dot; tapping opens the
 *  pin if the notification points at one, otherwise the person. Reading the
 *  list marks it read on the server, exactly as before.
 * ──────────────────────────────────────────────────────────────────────────── */

@Composable
fun NotificationsScreen(
    nav: NavHostController,
    session: SessionManager,
    vm: NotificationsViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) {
        vm.load()
        vm.markRead()
    }

    val items by vm.items.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val failed by vm.failed.collectAsStateWithLifecycle()
    val nextCursor by vm.nextCursor.collectAsStateWithLifecycle()
    val loadingMore by vm.loadingMore.collectAsStateWithLifecycle()

    var pulling by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        ScrollToTopEvents.events.collect { route ->
            if (route == Destination.Notifications.route) {
                if (listState.firstVisibleItemIndex > 10) listState.scrollToItem(10)
                listState.animateScrollToItem(0)
            }
        }
    }
    LaunchedEffect(loading) { if (!loading) pulling = false }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.topBar)
                .padding(horizontal = Dimens.screen),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.notifications_title),
                style = PinBomText.titleLarge,
                color = PinBomTheme.colors.textPrimary,
            )
        }

        when {
            items.isEmpty() && loading -> NotificationSkeleton()

            items.isEmpty() && failed -> PullableCenter(modifier = Modifier.weight(1f)) {
                ErrorState(onRetry = { vm.load() })
            }

            items.isEmpty() -> PullableCenter(modifier = Modifier.weight(1f)) {
                EmptyState(
                    title = stringResource(R.string.notifications_empty_title),
                    description = stringResource(R.string.notifications_empty_body),
                    icon = PinBomIcons.Notifications,
                )
            }

            else -> PinBomPullToRefresh(
                isRefreshing = pulling && loading,
                onRefresh = {
                    pulling = true
                    vm.load()
                },
                modifier = Modifier.weight(1f),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Dimens.sm,
                        end = Dimens.sm,
                        bottom = Dimens.huge + Dimens.navBarSpace,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Dimens.xxs),
                ) {
                    val fresh = items.filter { !it.read }
                    val earlier = items.filter { it.read }
                    val open: (Notification) -> Unit = { item ->
                        val handle = item.actor?.username.orEmpty()
                        when {
                            item.pin_id > 0 -> nav.navigate(Routes.pin(item.pin_id))
                            handle.isNotBlank() -> nav.navigate(Routes.profile(handle))
                        }
                    }
                    if (fresh.isNotEmpty()) {
                        item(key = "h-new", contentType = "header") {
                            NotificationSection(stringResource(R.string.notif_section_new))
                        }
                        items(items = fresh, key = { it.id }, contentType = { "notification" }) { item ->
                            NotificationRow(item = item, baseUrl = session.baseUrl, onOpen = { open(item) })
                        }
                    }
                    if (earlier.isNotEmpty()) {
                        if (fresh.isNotEmpty()) {
                            item(key = "h-earlier", contentType = "header") {
                                NotificationSection(stringResource(R.string.notif_section_earlier))
                            }
                        }
                        items(items = earlier, key = { it.id }, contentType = { "notification" }) { item ->
                            NotificationRow(item = item, baseUrl = session.baseUrl, onOpen = { open(item) })
                        }
                    }
                    if (nextCursor.isNotBlank()) {
                        item(key = "notifications-load-more") {
                            androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                TextButton(onClick = { vm.loadMore() }, enabled = !loadingMore) {
                                    Text(stringResource(R.string.action_load_more))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationSection(title: String) {
    Text(
        text = title,
        style = PinBomText.caption,
        color = PinBomTheme.colors.textSecondary,
        modifier = Modifier.padding(start = Dimens.md, end = Dimens.md, top = Dimens.md, bottom = Dimens.xs),
    )
}

/* ── Row ──────────────────────────────────────────────────────────────────── */

@Composable
private fun NotificationRow(item: Notification, baseUrl: String, onOpen: () -> Unit) {
    val colors = PinBomTheme.colors
    val fallback = stringResource(R.string.app_name)
    val unreadLabel = stringResource(R.string.cd_unread)
    val handle = item.actor?.username.orEmpty()
    val name = item.actor?.display_name.orEmpty().ifBlank { handle }.ifBlank { fallback }
    val hasTarget = item.pin_id > 0 || handle.isNotBlank()

    /* The server only writes a body for comments; likes, saves and follows
     * arrive empty. The sentence is therefore built here, per type, in the
     * app's language — that is what the row used to be missing. */
    val action = when (item.type) {
        "like" -> stringResource(R.string.notif_like)
        "save" -> stringResource(R.string.notif_save)
        "comment" -> stringResource(R.string.notif_comment)
        "follow" -> stringResource(R.string.notif_follow)
        "mention", "broadcast", "admin_broadcast" -> item.body.ifBlank { stringResource(R.string.notif_generic) }
        else -> item.body.ifBlank { stringResource(R.string.notif_generic) }
    }
    val kind = notificationKind(item.type, colors.primary)
    val quote = if (item.type == "comment") item.body.trim() else ""
    val pinTitle = item.pin_title.trim()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PinBomShape.field)
            .background(if (item.read) colors.background.copy(alpha = 0f) else colors.surfaceGrey)
            .pinbomClick(
                onClick = onOpen,
                enabled = hasTarget,
                indication = ripple(bounded = true, color = colors.textSecondary),
                role = Role.Button,
            )
            .padding(horizontal = Dimens.md, vertical = Dimens.md),
        verticalAlignment = Alignment.Top,
    ) {
        /* The avatar carries a small badge for *what happened*, so the list can be
         * scanned by colour and shape before a word is read. */
        androidx.compose.foundation.layout.Box {
            Avatar(
                name = name,
                url = item.actor?.avatar_url?.ifBlank { null },
                baseUrl = baseUrl,
                size = 44.dp,
            )
            if (kind != null) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 4.dp, y = 4.dp)
                        .size(20.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(colors.background)
                        .padding(2.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(kind.color),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = kind.icon,
                        contentDescription = null,
                        tint = AbsoluteWhite,
                        modifier = Modifier.size(10.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(Dimens.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = androidx.compose.ui.text.buildAnnotatedString {
                    pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                    append(name)
                    pop()
                    append(" ")
                    append(action)
                },
                style = PinBomText.bodySmall,
                color = colors.textPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (quote.isNotBlank()) {
                Spacer(Modifier.height(Dimens.xs))
                ContentText(
                    text = quote,
                    style = PinBomText.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(PinBomShape.field)
                        .background(colors.surface)
                        .padding(horizontal = Dimens.md, vertical = Dimens.sm),
                )
            } else if (pinTitle.isNotBlank() && item.type != "follow") {
                Spacer(Modifier.height(Dimens.xxs))
                ContentText(
                    text = pinTitle,
                    style = PinBomText.caption,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(Dimens.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RelativeTimeText(iso = item.created_at)
                if (!item.read) {
                    Spacer(Modifier.width(Dimens.sm))
                    BadgeDot(modifier = Modifier.semantics { contentDescription = unreadLabel })
                }
            }
        }
        if (item.pin_image_url.isNotBlank()) {
            Spacer(Modifier.width(Dimens.md))
            ir.pinbom.app.ui.components.PinImage(
                url = ir.pinbom.app.ui.components.imageUrl(baseUrl, item.pin_image_url),
                seed = item.pin_id,
                modifier = Modifier.size(Dimens.notificationThumb),
                shape = PinBomShape.thumb,
                contentDescription = null,
            )
        }
    }
}

private class NotificationKind(val icon: androidx.compose.ui.graphics.vector.ImageVector, val color: Color)

private fun notificationKind(type: String, brand: Color): NotificationKind? = when (type) {
    "like" -> NotificationKind(PinBomIcons.Favorite, brand)
    "save" -> NotificationKind(PinBomIcons.Bookmark, NotificationSaveAccent)
    "comment" -> NotificationKind(PinBomIcons.ChatBubble, NotificationCommentAccent)
    "follow" -> NotificationKind(PinBomIcons.PersonAdd, NotificationFollowAccent)
    "mention", "broadcast", "admin_broadcast" -> NotificationKind(PinBomIcons.Notifications, brand)
    else -> NotificationKind(PinBomIcons.Notifications, brand)
}

/* ── Loading ──────────────────────────────────────────────────────────────── */

@Composable
private fun NotificationSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.md, vertical = Dimens.sm),
        verticalArrangement = Arrangement.spacedBy(Dimens.sm),
    ) {
        repeat(6) {
            ListRowSkeleton()
        }
    }
}

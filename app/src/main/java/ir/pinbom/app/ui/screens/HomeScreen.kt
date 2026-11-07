package ir.pinbom.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import ir.pinbom.app.ui.components.icons.PinBomIcons
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.widget.Toast
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import ir.pinbom.app.ui.PinActionViewModel
import ir.pinbom.app.ui.BoardsViewModel
import ir.pinbom.app.ui.components.PinBomChipRow
import ir.pinbom.app.ui.components.CollapsingHeaderColumn
import ir.pinbom.app.ui.components.CardEntranceSeen
import ir.pinbom.app.ui.components.ENTRANCE_MAX_ITEMS
import ir.pinbom.app.ui.components.cardEntrance
import ir.pinbom.app.ui.components.rememberCollapsingHeaderState
import ir.pinbom.app.ui.components.PinBomDialog
import ir.pinbom.app.ui.components.PinBomDialogContent
import ir.pinbom.app.ui.components.ContentTextField
import ir.pinbom.app.ui.components.PrimaryPillButton
import ir.pinbom.app.ui.components.imageUrl
import androidx.compose.material3.Text
import ir.pinbom.app.ui.theme.PinBomText
import androidx.navigation.NavHostController
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import ir.pinbom.app.R
import ir.pinbom.app.data.SessionManager
import ir.pinbom.app.data.local.PinEntity
import ir.pinbom.app.data.remote.Author
import ir.pinbom.app.data.remote.Pin
import ir.pinbom.app.ui.HomeViewModel
import ir.pinbom.app.ui.components.AppendSpinner
import ir.pinbom.app.ui.components.PinBomBrand
import ir.pinbom.app.ui.components.PinBomPullToRefresh
import ir.pinbom.app.ui.components.EmptyState
import ir.pinbom.app.ui.components.ErrorState
import ir.pinbom.app.ui.components.IconCircleButton
import ir.pinbom.app.ui.components.OfflineBanner
import ir.pinbom.app.ui.components.PinCard
import ir.pinbom.app.ui.components.PinBomBottomSheet
import ir.pinbom.app.ui.components.SheetHeader
import ir.pinbom.app.ui.components.SecondaryPillButton
import ir.pinbom.app.ui.components.PillButtonSize
import ir.pinbom.app.ui.components.rememberPinImageDownloader
import ir.pinbom.app.ui.components.PinGridSkeleton
import ir.pinbom.app.ui.components.rememberIsOnline
import ir.pinbom.app.ui.nav.Routes
import ir.pinbom.app.ui.nav.sharedPinBounds
import ir.pinbom.app.ui.theme.PinBomTheme
import ir.pinbom.app.ui.theme.Dimens
import ir.pinbom.app.ui.theme.Motion
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import ir.pinbom.app.work.UploadEvents
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import ir.pinbom.app.ui.nav.Destination
import ir.pinbom.app.ui.nav.ScrollToTopEvents
import kotlinx.coroutines.flow.distinctUntilChanged

/* ────────────────────────────────────────────────────────────────────────────
 *  PinBom · خانه
 *  Brand header and a masonry wall of pins. The wall never
 *  flashes white: the cached Room rows paint first, the skeleton covers the very
 *  first load, and the offline banner explains the rest.
 * ──────────────────────────────────────────────────────────────────────────── */

@Composable
fun HomeScreen(
    nav: NavHostController,
    session: SessionManager,
    vm: HomeViewModel = hiltViewModel(),
) {
    val feed = vm.items.collectAsLazyPagingItems()
    /* Kept as a State object and only *read* when the wall is empty: every page
     * write to Room re-emits this list, and reading it here used to recompose the
     * whole screen (and re-map every row) on each of those emissions. */
    val cachedState = vm.cached.collectAsStateWithLifecycle(initialValue = emptyList())
    val online = rememberIsOnline()
    val context = LocalContext.current
    val actionVm: PinActionViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()
    var quickPin by remember { mutableStateOf<Pin?>(null) }
    var savePin by remember { mutableStateOf<Pin?>(null) }
    var reportPin by remember { mutableStateOf<Pin?>(null) }
    var reportReason by remember { mutableStateOf("spam") }
    var reportNote by remember { mutableStateOf("") }
    var reportBusy by remember { mutableStateOf(false) }
    var editingPin by remember { mutableStateOf<Pin?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var editDescription by remember { mutableStateOf("") }
    var editBusy by remember { mutableStateOf(false) }
    var deletingPin by remember { mutableStateOf<Pin?>(null) }
    var deleteBusy by remember { mutableStateOf(false) }
    var newBoardDialog by remember { mutableStateOf(false) }
    var newBoardTitle by remember { mutableStateOf("") }
    val boardsVm: BoardsViewModel = hiltViewModel()
    val boards by boardsVm.items.collectAsStateWithLifecycle()
    val feedKind by vm.selectedKind.collectAsStateWithLifecycle()
    val saveLabel = stringResource(R.string.action_save)
    val savedToFmt = stringResource(R.string.pin_saved_to, "%1\$s")
    val savedGeneric = stringResource(R.string.action_saved)
    LaunchedEffect(savePin) { if (savePin != null) boardsVm.load() }
    val likedOverrides = remember { mutableStateMapOf<Long, Boolean>() }
    var quickLiked by remember { mutableStateOf(false) }
    var quickLikeBusy by remember { mutableStateOf(false) }
    val likeError = stringResource(R.string.state_error_title)
    val moreLabel = stringResource(R.string.cd_more_options)
    val downloadLabel = stringResource(R.string.action_download)
    val reportReasonKeys = remember { listOf("spam", "nudity", "violence", "copyright", "other") }
    val reportReasonLabels = listOf(
        stringResource(R.string.report_spam),
        stringResource(R.string.report_nudity),
        stringResource(R.string.report_violence),
        stringResource(R.string.report_copyright),
        stringResource(R.string.report_other),
    )
    val downloader = rememberPinImageDownloader(
        baseUrl = session.baseUrl,
        successLabel = stringResource(R.string.download_success),
        failureLabel = stringResource(R.string.download_failed),
    )

    fun openQuickActions(pin: Pin) {
        quickPin = pin
        quickLiked = likedOverrides[pin.id] ?: pin.liked
        quickLikeBusy = false
    }

    /* A finished upload means a new pin exists: pull the wall once, the next
     * time the feed sees it. The nonce makes sure that happens exactly once. */
    val uploaded by UploadEvents.completed.collectAsStateWithLifecycle()
    var appliedUpload by rememberSaveable { mutableStateOf(0L) }
    LaunchedEffect(uploaded) {
        if (uploaded > appliedUpload) {
            appliedUpload = uploaded
            feed.refresh()
        }
    }

    /* One scroll state for the whole wall (cached rows, skeleton hand-over and
     * paged feed all share it), so the screen can be driven from outside. */
    val gridState = rememberLazyStaggeredGridState()
    val headerState = rememberCollapsingHeaderState()
    var sharedPinIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    // Shared bounds exist only for actually visible, settled cards. During any scroll the
    // key set is empty; snapshotFlow + distinctUntilChanged avoids per-frame recomposition.
    LaunchedEffect(gridState) {
        snapshotFlow {
            if (gridState.isScrollInProgress) emptySet()
            else gridState.layoutInfo.visibleItemsInfo.mapNotNull { item -> item.key as? Long }.toSet()
        }.distinctUntilChanged().collect { ids -> sharedPinIds = ids }
    }

    /* Back at the very top (by scrolling, by the tab re-tap or by a feed switch)
     * the header is always shown again. */
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.canScrollBackward }
            .distinctUntilChanged()
            .collect { canScroll -> if (!canScroll) headerState.show() }
    }

    /* Tap "Home" while on Home → back to the top. Jump most of the way, animate
     * the last stretch: a 300-pin scroll to the top should take a moment, not
     * a second and a half. */
    LaunchedEffect(gridState) {
        ScrollToTopEvents.events.collect { route ->
            if (route == Destination.Home.route) {
                if (gridState.firstVisibleItemIndex > 10) gridState.scrollToItem(10)
                gridState.animateScrollToItem(0)
            }
        }
    }

    /* Switching the chip (For you / Following / Trending) starts that feed at
     * its top instead of at whatever offset the previous feed was scrolled to.
     * `shownKind` is saveable, so coming back to the tab never resets a scroll. */
    var shownKind by rememberSaveable { mutableStateOf(feedKind) }
    LaunchedEffect(feedKind) {
        if (shownKind != feedKind) {
            shownKind = feedKind
            gridState.scrollToItem(0)
        }
    }

    /* Warm the disk cache for the pins just below the fold. A tiny 64px decode
     * is enough to pull the encoded file in, so by the time a tile scrolls into
     * view its bitmap comes from disk, not from the network. Memory cache is
     * left alone (these decodes are throw-away), and the high-water mark means
     * every pin is requested once per visit. */
    val appContext = context.applicationContext
    val prefetchBase = session.baseUrl
    LaunchedEffect(gridState, feed, prefetchBase) {
        val loader = SingletonImageLoader.get(appContext)
        var done = -1
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { last ->
                if (last < 0) return@collect
                val from = maxOf(last + 1, done + 1)
                val to = minOf(last + PREFETCH_AHEAD, feed.itemCount - 1)
                for (i in from..to) {
                    val pin = feed.peek(i) ?: continue
                    if (pin.image_url.isBlank()) continue
                    loader.enqueue(
                        ImageRequest.Builder(appContext)
                            .data(imageUrl(prefetchBase, pin.image_url))
                            .size(64, 64)
                            .memoryCachePolicy(CachePolicy.DISABLED)
                            .build(),
                    )
                }
                if (to > done) done = to
            }
    }

    Box(Modifier.fillMaxSize()) {
      Column(Modifier.fillMaxSize().nestedScroll(headerState.connection)) {
       CollapsingHeaderColumn(state = headerState) {
        HomeHeader(onSettings = { nav.navigate(Routes.SETTINGS) { launchSingleTop = true } })

        val feedKinds = remember { listOf("home", "following", "trending") }
        PinBomChipRow(
            labels = listOf(
                stringResource(R.string.feed_for_you),
                stringResource(R.string.feed_following),
                stringResource(R.string.feed_trending),
            ),
            selectedIndex = feedKinds.indexOf(feedKind).coerceAtLeast(0),
            onSelect = { index -> feedKinds.getOrNull(index)?.let(vm::setKind) },
            modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.xs),
        )
       }

        OfflineBanner(
            visible = !online,
            onRetry = { feed.refresh() },
        )

        val refreshing = feed.loadState.refresh is LoadState.Loading && feed.itemCount > 0
        PinBomPullToRefresh(
            isRefreshing = refreshing,
            onRefresh = { feed.refresh() },
            modifier = Modifier.weight(1f),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val columns = pinGridColumns(maxWidth)
                val cachedPins = if (feed.itemCount == 0) {
                    val rows = cachedState.value
                    remember(rows) { rows.map { it.toPin() } }
                } else {
                    emptyList()
                }

                val loading = feed.loadState.refresh is LoadState.Loading
                val failed = feed.loadState.refresh is LoadState.Error

                when {
                    /* Cached rows win over the skeleton: reopening the app offline
                     * shows the wall you already have, not a grey placeholder. */
                    feed.itemCount == 0 && feedKind == "home" && cachedPins.isNotEmpty() && (loading || failed) ->
                        LazyVerticalStaggeredGrid(
                            columns = columns,
                            state = gridState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = Dimens.grid, end = Dimens.grid, top = Dimens.grid, bottom = Dimens.grid + Dimens.navBarSpace),
                            verticalItemSpacing = Dimens.gridGap,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.gridGap),
                        ) {
                            staticPins(
                                pins = cachedPins,
                                baseUrl = session.baseUrl,
                                onPinClick = { nav.navigate(Routes.pin(it.id)) },
                                onMoreClick = ::openQuickActions,
                                sharedPinIds = sharedPinIds,
                            )
                        }

                    feed.itemCount == 0 && loading ->
                        PinGridSkeleton(
                            columns = columns,
                            itemCount = 6,
                            contentPadding = PaddingValues(start = Dimens.grid, end = Dimens.grid, top = Dimens.grid, bottom = Dimens.grid + Dimens.navBarSpace),
                            modifier = Modifier.fillMaxSize(),
                        )

                    feed.itemCount == 0 && failed ->
                        PullableCenter {
                            ErrorState(onRetry = { feed.retry() })
                        }

                    feed.itemCount == 0 ->
                        PullableCenter {
                            EmptyState(
                                title = stringResource(R.string.state_empty_title),
                                description = stringResource(R.string.state_empty_body),
                            )
                        }

                    else -> LazyVerticalStaggeredGrid(
                        columns = columns,
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = Dimens.grid, end = Dimens.grid, top = Dimens.grid, bottom = Dimens.grid + Dimens.navBarSpace),
                        verticalItemSpacing = Dimens.gridGap,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.gridGap),
                    ) {
                        pagedPins(
                            feed = feed,
                            baseUrl = session.baseUrl,
                            appending = feed.loadState.append is LoadState.Loading,
                            onPinClick = { nav.navigate(Routes.pin(it.id)) },
                            onMoreClick = ::openQuickActions,
                            animateEntrance = true,
                            sharedPinIds = sharedPinIds,
                        )
                    }
                }
            }
        }
      }

      val activeQuickPin = quickPin
      if (activeQuickPin != null) {
          PinBomBottomSheet(onDismissRequest = { quickPin = null }) {
              Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
              SheetHeader(title = activeQuickPin.title.ifBlank { moreLabel }, contentDirection = true)
              SecondaryPillButton(
                  text = stringResource(if (quickLiked) R.string.action_unlike else R.string.action_like),
                  onClick = {
                      if (!quickLikeBusy) {
                          quickLikeBusy = true
                          scope.launch {
                              val next = actionVm.toggleLike(activeQuickPin.id, quickLiked)
                              if (next != null) {
                                  quickLiked = next
                                  likedOverrides[activeQuickPin.id] = next
                              } else {
                                  Toast.makeText(context, likeError, Toast.LENGTH_SHORT).show()
                              }
                              quickLikeBusy = false
                          }
                      }
                  },
                  leadingIcon = if (quickLiked) PinBomIcons.Favorite else PinBomIcons.FavoriteBorder,
                  enabled = !quickLikeBusy,
                  loading = quickLikeBusy,
                  fillWidth = true,
                  size = PillButtonSize.Regular,
              )
              Spacer(Modifier.height(Dimens.sm))
              SecondaryPillButton(
                  text = saveLabel,
                  onClick = {
                      savePin = activeQuickPin
                      quickPin = null
                  },
                  leadingIcon = PinBomIcons.BookmarkAdd,
                  fillWidth = true,
                  size = PillButtonSize.Regular,
              )
              Spacer(Modifier.height(Dimens.sm))
              SecondaryPillButton(
                  text = downloadLabel,
                  onClick = {
                      quickPin = null
                      downloader(activeQuickPin.id, activeQuickPin.image_url)
                  },
                  leadingIcon = PinBomIcons.Download,
                  fillWidth = true,
                  size = PillButtonSize.Regular,
              )
              Spacer(Modifier.height(Dimens.sm))
              SecondaryPillButton(
                  text = stringResource(R.string.action_report),
                  onClick = {
                      reportPin = activeQuickPin
                      reportReason = "spam"
                      reportNote = ""
                      quickPin = null
                  },
                  fillWidth = true,
                  size = PillButtonSize.Regular,
              )
              if (activeQuickPin.author?.username == session.username && session.username.isNotBlank()) {
                  Spacer(Modifier.height(Dimens.sm))
                  SecondaryPillButton(
                      text = stringResource(R.string.action_edit_pin),
                      onClick = {
                          editingPin = activeQuickPin
                          editTitle = activeQuickPin.title
                          editDescription = activeQuickPin.description
                          quickPin = null
                      },
                      leadingIcon = PinBomIcons.Edit,
                      fillWidth = true,
                      size = PillButtonSize.Regular,
                  )
                  Spacer(Modifier.height(Dimens.sm))
                  SecondaryPillButton(
                      text = stringResource(R.string.action_delete_pin),
                      onClick = {
                          deletingPin = activeQuickPin
                          quickPin = null
                      },
                      leadingIcon = PinBomIcons.Delete,
                      fillWidth = true,
                      size = PillButtonSize.Regular,
                  )
              }
              Spacer(Modifier.height(Dimens.xs))
              }
          }
      }

      val toSave = savePin
      if (toSave != null) {
          SaveSheet(
              boards = boards,
              hint = stringResource(R.string.pin_save_hint),
              baseUrl = session.baseUrl,
              coverUrl = imageUrl(session.baseUrl, toSave.image_url),
              coverSeed = toSave.id,
              onDismiss = { savePin = null },
              onBoard = { board ->
                  savePin = null
                  scope.launch {
                      val ok = actionVm.saveTo(toSave.id, board.id)
                      Toast.makeText(
                          context,
                          if (ok) savedToFmt.replace("%1\$s", board.title) else likeError,
                          Toast.LENGTH_SHORT,
                      ).show()
                  }
              },
              onNewBoard = { newBoardDialog = true },
          )
      }

      val targetReport = reportPin
      if (targetReport != null) {
          PinBomDialog(
              onDismissRequest = { if (!reportBusy) reportPin = null },
              dismissOnClickOutside = !reportBusy,
          ) {
              Text(
                  text = stringResource(R.string.report_title),
                  style = PinBomText.title,
                  color = PinBomTheme.colors.textPrimary,
              )
              Spacer(Modifier.height(Dimens.sm))
              Text(
                  text = stringResource(R.string.report_reason_prompt),
                  style = PinBomText.bodySmall,
                  color = PinBomTheme.colors.textSecondary,
              )
              Spacer(Modifier.height(Dimens.sm))
              PinBomChipRow(
                  labels = reportReasonLabels,
                  selectedIndex = reportReasonKeys.indexOf(reportReason).coerceAtLeast(0),
                  onSelect = { reportReason = reportReasonKeys[it] },
                  modifier = Modifier.fillMaxWidth(),
                  contentPadding = PaddingValues(horizontal = 0.dp),
                  spacing = Dimens.xs,
              )
              Spacer(Modifier.height(Dimens.md))
              ContentTextField(
                  value = reportNote,
                  onValueChange = { reportNote = it.take(500) },
                  placeholder = stringResource(R.string.report_note_hint),
                  singleLine = false,
                  minLines = 2,
                  modifier = Modifier.fillMaxWidth(),
              )
              Spacer(Modifier.height(Dimens.xl))
              Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(Dimens.sm, Alignment.End),
                  verticalAlignment = Alignment.CenterVertically,
              ) {
                  SecondaryPillButton(
                      text = stringResource(R.string.action_cancel),
                      onClick = { reportPin = null },
                      enabled = !reportBusy,
                      size = PillButtonSize.Small,
                  )
                  PrimaryPillButton(
                      text = stringResource(R.string.action_report),
                      onClick = {
                          if (!reportBusy && reportReason in reportReasonKeys) {
                              reportBusy = true
                              scope.launch {
                                  val success = actionVm.report(targetReport.id, reportReason, reportNote)
                                  reportBusy = false
                                  reportPin = null
                                  Toast.makeText(
                                      context,
                                      context.getString(if (success) R.string.report_success else R.string.report_failed),
                                      Toast.LENGTH_SHORT,
                                  ).show()
                              }
                          } else {
                              Toast.makeText(context, R.string.report_select_reason, Toast.LENGTH_SHORT).show()
                          }
                      },
                      enabled = !reportBusy && reportReason in reportReasonKeys,
                      loading = reportBusy,
                      size = PillButtonSize.Small,
                  )
              }
          }
      }

      val targetEdit = editingPin
      if (targetEdit != null) {
          PinBomDialog(
              onDismissRequest = { if (!editBusy) editingPin = null },
              dismissOnClickOutside = !editBusy,
          ) {
              Text(
                  text = stringResource(R.string.edit_pin_title),
                  style = PinBomText.title,
                  color = PinBomTheme.colors.textPrimary,
              )
              Spacer(Modifier.height(Dimens.md))
              ContentTextField(
                  value = editTitle,
                  onValueChange = { editTitle = it.take(200) },
                  placeholder = stringResource(R.string.edit_pin_title_hint),
                  modifier = Modifier.fillMaxWidth(),
              )
              Spacer(Modifier.height(Dimens.sm))
              ContentTextField(
                  value = editDescription,
                  onValueChange = { editDescription = it.take(2000) },
                  placeholder = stringResource(R.string.edit_pin_description_hint),
                  singleLine = false,
                  minLines = 3,
                  modifier = Modifier.fillMaxWidth(),
              )
              Spacer(Modifier.height(Dimens.xl))
              Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(Dimens.sm, Alignment.End),
                  verticalAlignment = Alignment.CenterVertically,
              ) {
                  SecondaryPillButton(
                      text = stringResource(R.string.action_cancel),
                      onClick = { editingPin = null },
                      enabled = !editBusy,
                      size = PillButtonSize.Small,
                  )
                  PrimaryPillButton(
                      text = stringResource(R.string.action_done),
                      onClick = {
                          if (!editBusy && editTitle.isNotBlank()) {
                              editBusy = true
                              scope.launch {
                                  val success = actionVm.updatePin(targetEdit.id, editTitle, editDescription)
                                  editBusy = false
                                  editingPin = null
                                  if (success) feed.refresh()
                                  Toast.makeText(
                                      context,
                                      context.getString(if (success) R.string.edit_pin_saved else R.string.edit_pin_failed),
                                      Toast.LENGTH_SHORT,
                                  ).show()
                              }
                          }
                      },
                      enabled = !editBusy && editTitle.isNotBlank(),
                      loading = editBusy,
                      size = PillButtonSize.Small,
                  )
              }
          }
      }

      val targetDelete = deletingPin
      if (targetDelete != null) {
          PinBomDialog(
              onDismissRequest = { if (!deleteBusy) deletingPin = null },
              dismissOnClickOutside = !deleteBusy,
          ) {
              PinBomDialogContent(
                  title = stringResource(R.string.delete_pin_title),
                  body = stringResource(R.string.delete_pin_body),
                  confirmLabel = stringResource(R.string.action_delete),
                  dismissLabel = stringResource(R.string.action_cancel),
                  destructive = true,
                  onDismiss = { deletingPin = null },
                  onConfirm = {
                      if (!deleteBusy) {
                          deleteBusy = true
                          scope.launch {
                              val success = actionVm.deletePin(targetDelete.id)
                              deleteBusy = false
                              deletingPin = null
                              if (success) feed.refresh()
                              Toast.makeText(
                                  context,
                                  context.getString(if (success) R.string.delete_pin_success else R.string.delete_pin_failed),
                                  Toast.LENGTH_SHORT,
                              ).show()
                          }
                      }
                  },
              )
          }
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
    }
}

/** How many pins below the last visible one get their image pre-fetched. */
private const val PREFETCH_AHEAD = 8

/* ── Header ───────────────────────────────────────────────────────────────── */

@Composable
private fun HomeHeader(onSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.topBar)
            .padding(horizontal = Dimens.screen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        /* 22sp wordmark → the mark lands at its cap-height + 30% (28dp). */
        PinBomBrand(markSize = 28.dp)
        Spacer(Modifier.weight(1f))
        IconCircleButton(
            icon = PinBomIcons.Settings,
            contentDescription = stringResource(R.string.cd_open_settings),
            onClick = onSettings,
            containerColor = PinBomTheme.colors.surfaceGrey,
        )
    }
}

/* ── Shared grid plumbing (SearchScreen uses these too) ───────────────────── */

/** 2 → 3 → 4 → 5 columns, the same breakpoints the whole app uses. */
internal fun pinGridColumns(width: Dp): StaggeredGridCells = when {
    width < 600.dp -> StaggeredGridCells.Fixed(2)
    width < 900.dp -> StaggeredGridCells.Fixed(3)
    width < 1200.dp -> StaggeredGridCells.Fixed(4)
    else -> StaggeredGridCells.Fixed(5)
}

/** Paged pins + the full-line footer spinner. */
internal fun LazyStaggeredGridScope.pagedPins(
    feed: LazyPagingItems<Pin>,
    baseUrl: String,
    appending: Boolean,
    onPinClick: (Pin) -> Unit,
    onMoreClick: ((Pin) -> Unit)? = null,
    animateEntrance: Boolean = false,
    sharedPinIds: Set<Long> = emptySet(),
) {
    items(
        count = feed.itemCount,
        key = feed.itemKey { it.id },
        contentType = feed.itemContentType { "pin" },
    ) { index ->
        feed[index]?.let { pin ->
            /* Only the first few pins of a wall, and each only once per process:
             * the entrance is a moment, not a cost on every scroll. (Still no
             * animateItem(): a placement spring + fade per tile made every
             * appended page start 40 animations.) */
            val enters = remember(pin.id) {
                animateEntrance && index < ENTRANCE_MAX_ITEMS && CardEntranceSeen.markFirst(pin.id)
            }
            PinQuickCard(
                pin = pin,
                baseUrl = baseUrl,
                onOpen = onPinClick,
                onMoreClick = onMoreClick,
                modifier = Modifier.cardEntrance(enabled = enters, delayMillis = index * 45),
                sharedBounds = pin.id in sharedPinIds,
            )
        }
    }
    if (appending) {
        item(span = StaggeredGridItemSpan.FullLine) {
            AppendSpinner()
        }
    }
}

/** Offline fallback straight from Room — no spinner, no blank wall. */
internal fun LazyStaggeredGridScope.staticPins(
    pins: List<Pin>,
    baseUrl: String,
    onPinClick: (Pin) -> Unit,
    onMoreClick: ((Pin) -> Unit)? = null,
    sharedPinIds: Set<Long> = emptySet(),
) {
    items(items = pins, key = { it.id }, contentType = { "pin" }) { pin ->
        PinQuickCard(
            pin = pin,
            baseUrl = baseUrl,
            onOpen = onPinClick,
            onMoreClick = onMoreClick,
            modifier = Modifier,
            sharedBounds = pin.id in sharedPinIds,
        )
    }
}

/** A light pin card. Home supplies a shared quick-action handler for its menu. */
@Composable
internal fun PinQuickCard(
    pin: Pin,
    baseUrl: String,
    onOpen: (Pin) -> Unit,
    onMoreClick: ((Pin) -> Unit)? = null,
    modifier: Modifier = Modifier,
    sharedBounds: Boolean = false,
) {
    PinCard(
        pin = pin,
        baseUrl = baseUrl,
        modifier = if (sharedBounds) modifier.sharedPinBounds(pin.id) else modifier,
        showQuickActions = onMoreClick == null,
        onSaveClick = if (onMoreClick == null) ({ onOpen(pin) }) else null,
        onMoreClick = onMoreClick?.let { handler -> { handler(pin) } },
        onClick = { onOpen(pin) },
    )
}

/** Centred state view that still answers the pull gesture. */
@Composable
internal fun PullableCenter(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = Dimens.huge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content,
    )
}

/** Room row → the API model the cards understand. */
internal fun PinEntity.toPin(): Pin = Pin(
    id = id,
    title = title,
    description = description,
    width = width,
    height = height,
    created_at = createdAt,
    image_url = imageUrl,
    author = Author(username = "", display_name = authorName),
    author_avatar_url = authorAvatar,
)

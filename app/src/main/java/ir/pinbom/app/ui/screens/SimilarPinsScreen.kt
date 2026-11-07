package ir.pinbom.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import ir.pinbom.app.R
import ir.pinbom.app.data.SessionManager
import ir.pinbom.app.ui.PinViewModel
import ir.pinbom.app.ui.SimilarPinsViewModel
import ir.pinbom.app.ui.components.EmptyState
import ir.pinbom.app.ui.components.ErrorState
import ir.pinbom.app.ui.components.IconCircleButton
import ir.pinbom.app.ui.components.PinCard
import ir.pinbom.app.ui.components.PinImage
import ir.pinbom.app.ui.components.PrimaryPillButton
import ir.pinbom.app.ui.components.SkeletonLine
import ir.pinbom.app.ui.components.imageUrl
import ir.pinbom.app.ui.theme.Dimens
import ir.pinbom.app.ui.theme.PinBomShape
import ir.pinbom.app.ui.theme.PinBomText
import ir.pinbom.app.ui.theme.PinBomTheme

/** Visual similarity search. Drag over the source image to select a region. */
@Composable
fun SimilarPinsScreen(
    nav: NavHostController,
    pinId: Long,
    session: SessionManager,
    vm: SimilarPinsViewModel = hiltViewModel(),
    sourceVm: PinViewModel = hiltViewModel(),
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val failed by vm.failed.collectAsStateWithLifecycle()
    val source by sourceVm.pin.collectAsStateWithLifecycle()
    var cropStart by remember { mutableStateOf<Offset?>(null) }
    var cropRect by remember { mutableStateOf<Rect?>(null) }
    var imageSize by remember { mutableStateOf(IntSize.Zero) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let(vm::searchImage)
    }

    LaunchedEffect(pinId) {
        cropStart = null
        cropRect = null
        if (pinId > 0) {
            sourceVm.load(pinId)
            vm.searchPin(pinId)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(Dimens.topBar).padding(horizontal = Dimens.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconCircleButton(
                icon = ir.pinbom.app.ui.components.icons.PinBomIcons.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                onClick = { nav.popBackStack() },
                containerColor = PinBomTheme.colors.surfaceGrey,
            )
            Spacer(Modifier.size(Dimens.sm))
            Text(stringResource(R.string.pin_find_similar), style = PinBomText.titleLarge, color = PinBomTheme.colors.textPrimary)
        }

        if (source != null && pinId > 0) {
            val original = source!!
            Column(Modifier.fillMaxWidth().padding(horizontal = Dimens.screen)) {
                Text(
                    text = stringResource(R.string.similar_select_area_hint),
                    style = PinBomText.caption,
                    color = PinBomTheme.colors.textSecondary,
                )
                Spacer(Modifier.height(Dimens.sm))
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .aspectRatio((original.width.toFloat() / original.height.coerceAtLeast(1)).coerceIn(0.5f, 1.9f))
                        .clip(RoundedCornerShape(20.dp))
                        .background(PinBomTheme.colors.surfaceGrey)
                        .onSizeChanged { imageSize = it }
                        .pointerInput(original.id, imageSize) {
                            detectDragGestures(
                                onDragStart = { point -> cropStart = point; cropRect = Rect(point, point) },
                                onDrag = { change, _ ->
                                    val start = cropStart ?: change.position
                                    val end = change.position
                                    cropRect = Rect(
                                        minOf(start.x, end.x), minOf(start.y, end.y),
                                        maxOf(start.x, end.x), maxOf(start.y, end.y),
                                    )
                                    change.consume()
                                },
                            )
                        },
                ) {
                    PinImage(
                        url = imageUrl(session.baseUrl, original.image_url, "large"),
                        seed = original.id,
                        modifier = Modifier.fillMaxSize(),
                        shape = PinBomShape.pin,
                        contentScale = ContentScale.FillBounds,
                    )
                    Canvas(Modifier.fillMaxSize()) {
                        cropRect?.let { rect ->
                            drawRect(Color.Black.copy(alpha = 0.24f))
                            drawRect(Color.White, rect.topLeft, rect.size, style = Stroke(width = 3.dp.toPx()))
                        }
                    }
                }
                Spacer(Modifier.height(Dimens.sm))
                if (cropRect != null && imageSize.width > 0 && imageSize.height > 0) {
                    val rect = cropRect!!
                    val x = (rect.left / imageSize.width).coerceIn(0f, 1f)
                    val y = (rect.top / imageSize.height).coerceIn(0f, 1f)
                    val w = (rect.width / imageSize.width).coerceIn(0f, 1f - x)
                    val h = (rect.height / imageSize.height).coerceIn(0f, 1f - y)
                    PrimaryPillButton(
                        text = stringResource(R.string.similar_search_selected_area),
                        fillWidth = true,
                        enabled = w > 0.04f && h > 0.04f && !loading,
                        onClick = { vm.searchPin(pinId, x, y, w, h) },
                    )
                } else {
                    PrimaryPillButton(
                        text = stringResource(R.string.similar_search_whole_pin),
                        fillWidth = true,
                        enabled = !loading,
                        onClick = { vm.searchPin(pinId) },
                    )
                }
                Spacer(Modifier.height(Dimens.sm))
                PrimaryPillButton(
                    text = stringResource(R.string.search_by_image_choose),
                    fillWidth = true,
                    onClick = { picker.launch("image/*") },
                )
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(horizontal = Dimens.screen)) {
                EmptyState(
                    title = stringResource(R.string.search_by_image),
                    description = stringResource(R.string.search_by_image_hint),
                    compact = true,
                )
                PrimaryPillButton(
                    text = stringResource(R.string.search_by_image_choose),
                    fillWidth = true,
                    onClick = { picker.launch("image/*") },
                )
            }
        }

        Spacer(Modifier.height(Dimens.md))
        when {
            loading && items.isEmpty() -> Column(Modifier.padding(horizontal = Dimens.screen), verticalArrangement = Arrangement.spacedBy(Dimens.md)) {
                repeat(3) { SkeletonLine(widthFraction = 0.72f, height = 14.dp) }
            }
            failed && items.isEmpty() -> ErrorState(onRetry = { if (pinId > 0) vm.searchPin(pinId) else picker.launch("image/*") })
            items.isEmpty() -> EmptyState(
                title = stringResource(R.string.search_no_results_title),
                description = stringResource(R.string.search_no_results_body),
                compact = true,
            )
            else -> LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Adaptive(150.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Dimens.grid, vertical = Dimens.sm),
                verticalItemSpacing = Dimens.gridGap,
                horizontalArrangement = Arrangement.spacedBy(Dimens.gridGap),
            ) {
                items(items, key = { it.id }) { pin ->
                    PinCard(pin = pin, baseUrl = session.baseUrl, showQuickActions = false,
                        onClick = { nav.navigate(ir.pinbom.app.ui.nav.Routes.pin(pin.id)) })
                }
            }
        }
    }
}

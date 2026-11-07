package ir.pinbom.app.ui.components

import android.util.Log
import ir.pinbom.app.ui.components.icons.PinBomIcons
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import ir.pinbom.app.BuildConfig
import ir.pinbom.app.data.remote.Pin
import ir.pinbom.app.ui.theme.PinBomShape
import ir.pinbom.app.ui.theme.PinBomTheme
import ir.pinbom.app.ui.theme.Dimens
import ir.pinbom.app.ui.theme.Motion
import ir.pinbom.app.ui.theme.pastelFor

/* ────────────────────────────────────────────────────────────────────────────
 *  PinBom · PinImage
 *  The single image entry point of the app. It guarantees three things:
 *   1. a pin tile is *never* white — a deterministic pastel is painted first,
 *   2. requests are sized to the composable and cached (memory + disk),
 *   3. failures degrade into a quiet grey tile instead of an empty hole.
 * ──────────────────────────────────────────────────────────────────────────── */

/* ────────────────────────────────────────────────────────────────────────────
 *  Resolving an image reference — the one place the app builds an image URL
 *
 *  Three shapes arrive from the API, and all three must work:
 *
 *   1. a routed path          "/media/2/2026/10/<hash>.jpg"   ← what the server sends
 *   2. a bare storage path    "2/2026/10/<hash>.jpg"          ← older servers, seeded rows
 *   3. an absolute URL        "https://cdn.example/a.jpg"     ← externally hosted media
 *
 *  (2) is why this function exists at all: appending a bare path to the base URL
 *  produces `/2/2026/10/...`, which the media route does not serve — every image
 *  came back 404. The media prefix therefore lives here, once, and the other two
 *  shapes are left untouched so nothing is ever prefixed twice.
 * ──────────────────────────────────────────────────────────────────────────── */

/** The route the backend serves uploaded media from (see `models.MediaPrefix`). */
const val MEDIA_PREFIX = "/media"

/** Runtime mirror of the persisted data-saver setting. Only the central URL resolver reads it. */
object PinImagePreferences {
    var dataSaverEnabled by mutableStateOf(false)
}

/** Joins the configured server with whatever form the API returned. Variant selection lives here only. */
fun imageUrl(base: String, path: String, variant: String? = null): String {
    var reference = path.trim()
    if (reference.isEmpty()) return ""
    val preferredVariant = variant ?: if (PinImagePreferences.dataSaverEnabled) "small" else null
    if (preferredVariant in setOf("small", "medium", "large", "original")) {
        val suffix = Regex("_(small|medium|large)\\.([A-Za-z0-9]+)(?:([?#].*)?)$")
        reference = suffix.replace(reference) { match -> "_${preferredVariant}.${match.groupValues[2]}${match.groupValues[3]}" }
    }

    // Absolute URL (http, https, or scheme-relative) — already complete.
    if (reference.startsWith("http://") || reference.startsWith("https://") || reference.startsWith("//")) {
        return reference
    }

    val root = base.trim().trimEnd('/')
    if (root.isEmpty()) return reference // nothing to resolve against; caller renders a placeholder

    // An API-absolute path ("/media/…") is joined as it is.
    if (reference.startsWith("/")) return root + reference

    // A bare storage path gains the media prefix, a stored "media/…" does not.
    return if (reference.startsWith("media/")) {
        root + "/" + reference
    } else {
        root + MEDIA_PREFIX + "/" + reference
    }
}

/** Masonry aspect ratio: width / height, clamped so nothing gets absurd. */
fun pinAspectRatio(width: Int, height: Int): Float =
    (width.toFloat() / height.coerceAtLeast(1).toFloat()).coerceIn(0.5f, 1.9f)

fun pinAspectRatio(pin: Pin): Float = pinAspectRatio(pin.width, pin.height)

/**
 * Crossfading image with a pastel placeholder and a graceful error state.
 *
 * @param url absolute or server-relative URL; blank shows the placeholder only.
 * @param seed id used to pick the deterministic placeholder colour.
 * @param aspectRatio when non-null the composable sizes itself; otherwise the
 *   caller supplies the size (grids, covers, avatars).
 */
@Composable
fun PinImage(
    url: String,
    modifier: Modifier = Modifier,
    seed: Long = 0L,
    contentDescription: String? = null,
    shape: Shape = PinBomShape.pin,
    aspectRatio: Float? = null,
    contentScale: ContentScale = ContentScale.Crop,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
) {
    val colors = PinBomTheme.colors
    val context = LocalContext.current

    var failed by remember(url) { mutableStateOf(false) }

    val request = remember(url, context) {
        if (url.isBlank()) {
            null
        } else {
            /* No `memoryCacheKey` on purpose. Coil derives one from the data
             * *and the resolved decode size*, so the same URL painted at two
             * sizes no longer shares (and therefore mis-sizes) one bitmap. The
             * crossfade length comes from the app's ImageLoader, so every image
             * in PinBom fades the same way — 150ms, not the library default. */
            ImageRequest.Builder(context)
                .data(url)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()
        }
    }

    /* No hand-rolled fade here any more. It used to start at alpha 0 for *every*
     * image that entered composition — cached ones included — and read the
     * animated value in composition, so each tile recomposed on every frame of
     * its fade while the grid was scrolling. Coil's own crossfade (150ms, set on
     * the app's ImageLoader) fades network/disk loads and skips memory-cache hits,
     * and the pastel underneath carries the tile until the bitmap is ready. */

    Box(
        modifier = modifier
            .then(if (aspectRatio != null) Modifier.aspectRatio(aspectRatio) else Modifier)
            .clip(shape)
            .background(pastelFor(seed, colors.isDark)),
    ) {
        if (request != null && !failed) {
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                onError = { state ->
                    failed = true
                    /* One line that separates the three ways an image can fail:
                     * a URL we built wrong (the resolved URL is printed in
                     * full), a server that answered something unusable, or
                     * bytes that could not be decoded. `adb logcat -s PinBomImage`. */
                    if (BuildConfig.DEBUG) {
                        Log.w(
                            "PinBomImage",
                            "failed to load $url — ${state.result.throwable::class.java.simpleName}: " +
                                "${state.result.throwable.message}",
                        )
                    }
                },
            )
        }

        if (failed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.surfaceGrey),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = PinBomIcons.BrokenImage,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        overlay?.invoke(this)
    }
}

/* ── Previews ─────────────────────────────────────────────────────────────── */

@Preview(name = "PinImage · placeholder", backgroundColor = 0xFFFFFFFF, widthDp = 220)
@Composable
private fun PreviewPinImagePlaceholder() {
    PinBomTheme(darkTheme = false) {
        Column(Modifier.padding(Dimens.lg)) {
            PinImage(
                url = "",
                seed = 3,
                modifier = Modifier.fillMaxWidth(),
                aspectRatio = 0.75f,
                contentDescription = "پیش‌نمایش",
            )
            Spacer(Modifier.height(Dimens.md))
            PinImage(
                url = "",
                seed = 7,
                modifier = Modifier.fillMaxWidth(),
                aspectRatio = 1.4f,
                contentDescription = "پیش‌نمایش",
            )
        }
    }
}

@Preview(name = "PinImage · error", backgroundColor = 0xFF121212, widthDp = 220)
@Composable
private fun PreviewPinImageError() {
    PinBomTheme(darkTheme = true) {
        Column(Modifier.padding(Dimens.lg)) {
            // A URL that can never resolve: exercises the broken-image path.
            PinImage(
                url = "https://invalid.invalid/pinbom.jpg",
                seed = 5,
                modifier = Modifier.fillMaxWidth(),
                aspectRatio = 0.8f,
                contentDescription = "خطا",
            )
            Spacer(Modifier.height(Dimens.md))
            PinImage(
                url = "",
                seed = 11,
                modifier = Modifier.width(120.dp).height(160.dp),
                contentDescription = "خالی",
            )
        }
    }
}

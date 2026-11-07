package ir.pinbom.app.ui.components.icons

import ir.pinbom.app.ui.theme.AbsoluteWhite

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/* ────────────────────────────────────────────────────────────────────────────
 *  PinBom · in-app icons
 *  Same family as [PinBomNavIcons]: 24×24 viewport, 1.9 unit line, round caps and
 *  joins, art kept inside a 3-unit margin. Line glyphs are strokes only; the few
 *  "on" states (Favorite, Bookmark, ChatBubble, PersonAdd, Star) are solid with a
 *  hairline round-joined edge so every corner is soft. All vectors are built
 *  lazily once and cached; `Icon()` tints them, so colours come from the caller.
 *  Directional glyphs (ArrowBack, ChevronForward, Send, Logout) auto-mirror in RTL.
 * ──────────────────────────────────────────────────────────────────────────── */

private fun build(name: String, autoMirror: Boolean, add: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = autoMirror,
    ).apply(add).build()

/** Line glyph: each path is (SVG path data, stroke width) or just path data at 1.9. */
private fun line(name: String, autoMirror: Boolean, vararg paths: Any): ImageVector =
    build(name, autoMirror) {
        paths.forEach { p ->
            val (d, w) = if (p is Pair<*, *>) (p.first as String) to (p.second as Float) else (p as String) to 1.9f
            addPath(
                pathData = addPathNodes(d),
                fill = null,
                stroke = SolidColor(AbsoluteWhite),
                strokeLineWidth = w,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }

/** Solid glyph: even-odd fill plus a 1.0 round-joined edge in the same colour. */
private fun solid(name: String, autoMirror: Boolean, vararg paths: String): ImageVector =
    build(name, autoMirror) {
        paths.forEach { d ->
            addPath(
                pathData = addPathNodes(d),
                pathFillType = PathFillType.EvenOdd,
                fill = SolidColor(AbsoluteWhite),
                stroke = SolidColor(AbsoluteWhite),
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }

object PinBomIcons {
    // Shared with the navigation bar, so one glyph never exists in two designs.
    val Search: ImageVector get() = PinBomNavIcons.Search
    val Notifications: ImageVector get() = PinBomNavIcons.Notifications
    val Person: ImageVector get() = PinBomNavIcons.Profile

    val Check: ImageVector by lazy { line("PinBom.Check", false, "M5 12.5l4.5 4.5L19 7.5") }
    val Close: ImageVector by lazy { line("PinBom.Close", false, "M6.5 6.5l11 11M17.5 6.5l-11 11") }
    val Add: ImageVector by lazy { line("PinBom.Add", false, "M12 5v14M5 12h14") }
    val ArrowBack: ImageVector by lazy { line("PinBom.ArrowBack", true, "M19 12H5M11 6l-6 6 6 6") }
    val ChevronForward: ImageVector by lazy { line("PinBom.ChevronForward", true, "M9 5l7 7-7 7") }
    val ChevronLeft: ImageVector by lazy { line("PinBom.ChevronLeft", false, "M15 5l-7 7 7 7") }
    val Lock: ImageVector by lazy { line("PinBom.Lock", false, "M7.5 10.5h9A2.5 2.5 0 0 1 19 13v4.5a2.5 2.5 0 0 1-2.5 2.5h-9A2.5 2.5 0 0 1 5 17.5V13a2.5 2.5 0 0 1 2.5-2.5Z", "M8 10.5V8a4 4 0 0 1 8 0v2.5", "M12 14.5v2") }
    val Edit: ImageVector by lazy { line("PinBom.Edit", false, "M4 20l1-4.2L16.3 4.5a1.9 1.9 0 0 1 2.7 0l.5.5a1.9 1.9 0 0 1 0 2.7L8.2 19 4 20Z", "M14.5 6.3l3.2 3.2") }
    val Delete: ImageVector by lazy { line("PinBom.Delete", false, "M4.5 7h15", "M9.5 7V5.2A1.2 1.2 0 0 1 10.7 4h2.6a1.2 1.2 0 0 1 1.2 1.2V7", "M6.5 7l.8 11.3A1.8 1.8 0 0 0 9.1 20h5.8a1.8 1.8 0 0 0 1.8-1.7L17.5 7", "M10 11v5M14 11v5") }
    val Public: ImageVector by lazy { line("PinBom.Public", false, "M4 12a8 8 0 1 0 16 0a8 8 0 1 0-16 0Z", "M4 12h16", "M12 4c2.2 2.2 3.3 4.9 3.3 8S14.2 17.8 12 20c-2.2-2.2-3.3-4.9-3.3-8S9.8 6.2 12 4Z") }
    val Settings: ImageVector by lazy { line("PinBom.Settings", false, "M9.76 5.47 L10.94 3.06 L13.06 3.06 L14.24 5.47 L15.04 5.8 L17.57 4.93 L19.07 6.43 L18.2 8.96 L18.53 9.76 L20.94 10.94 L20.94 13.06 L18.53 14.24 L18.2 15.04 L19.07 17.57 L17.57 19.07 L15.04 18.2 L14.24 18.53 L13.06 20.94 L10.94 20.94 L9.76 18.53 L8.96 18.2 L6.43 19.07 L4.93 17.57 L5.8 15.04 L5.47 14.24 L3.06 13.06 L3.06 10.94 L5.47 9.76 L5.8 8.96 L4.93 6.43 L6.43 4.93 L8.96 5.8ZM9.2 12a2.8 2.8 0 1 0 5.6 0a2.8 2.8 0 1 0-5.6 0Z") }
    val PhotoCamera: ImageVector by lazy { line("PinBom.PhotoCamera", false, "M4 9.5A2.5 2.5 0 0 1 6.5 7h1.2l1.1-1.7A1.5 1.5 0 0 1 10 4.6h4a1.5 1.5 0 0 1 1.2.7L16.3 7h1.2A2.5 2.5 0 0 1 20 9.5v7a2.5 2.5 0 0 1-2.5 2.5h-11A2.5 2.5 0 0 1 4 16.5Z", "M9 13a3 3 0 1 0 6 0a3 3 0 1 0-6 0Z") }
    val FavoriteBorder: ImageVector by lazy { line("PinBom.FavoriteBorder", false, "M12 19.5C7.5 16.6 4.2 13.6 4.2 9.9A4.1 4.1 0 0 1 8.3 5.8c1.5 0 2.8.7 3.7 1.9.9-1.2 2.2-1.9 3.7-1.9a4.1 4.1 0 0 1 4.1 4.1c0 3.7-3.3 6.7-7.8 9.6Z") }
    val Favorite: ImageVector by lazy { solid("PinBom.Favorite", false, "M12 19.5C7.5 16.6 4.2 13.6 4.2 9.9A4.1 4.1 0 0 1 8.3 5.8c1.5 0 2.8.7 3.7 1.9.9-1.2 2.2-1.9 3.7-1.9a4.1 4.1 0 0 1 4.1 4.1c0 3.7-3.3 6.7-7.8 9.6Z") }
    val BookmarkBorder: ImageVector by lazy { line("PinBom.BookmarkBorder", false, "M7.5 4h9A1.5 1.5 0 0 1 18 5.5V20l-6-4.2L6 20V5.5A1.5 1.5 0 0 1 7.5 4Z") }
    val Bookmark: ImageVector by lazy { solid("PinBom.Bookmark", false, "M7.5 4h9A1.5 1.5 0 0 1 18 5.5V20l-6-4.2L6 20V5.5A1.5 1.5 0 0 1 7.5 4Z") }
    val BookmarkAdd: ImageVector by lazy { line("PinBom.BookmarkAdd", false, "M7.5 4h9A1.5 1.5 0 0 1 18 5.5V20l-6-4.2L6 20V5.5A1.5 1.5 0 0 1 7.5 4Z", "M12 7.5v5M9.5 10h5") }
    val ModeComment: ImageVector by lazy { line("PinBom.ModeComment", false, "M6.5 5h11A2.5 2.5 0 0 1 20 7.5v7a2.5 2.5 0 0 1-2.5 2.5H11l-4 3.3V17h-.5A2.5 2.5 0 0 1 4 14.5v-7A2.5 2.5 0 0 1 6.5 5Z") }
    val ChatBubble: ImageVector by lazy { solid("PinBom.ChatBubble", false, "M6.5 5h11A2.5 2.5 0 0 1 20 7.5v7a2.5 2.5 0 0 1-2.5 2.5H11l-4 3.3V17h-.5A2.5 2.5 0 0 1 4 14.5v-7A2.5 2.5 0 0 1 6.5 5Z") }
    val PersonAdd: ImageVector by lazy { solid("PinBom.PersonAdd", false, "M6.5 8a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0-7 0Z", "M3.5 19.5C3.5 16.5 6.4 14.6 10 14.6s6.5 1.9 6.5 4.9Z", "M17 8h2v2.5h2.5v2H19V15h-2v-2.5h-2.5v-2H17Z") }
    val Download: ImageVector by lazy { line("PinBom.Download", false, "M12 4v11M7.5 10.5L12 15l4.5-4.5M5 19.5h14") }
    val Upload: ImageVector by lazy { line("PinBom.Upload", false, "M12 15V4M7.5 8.5L12 4l4.5 4.5M5 19.5h14") }
    val Devices: ImageVector by lazy { line("PinBom.Devices", false, "M3 7a2 2 0 0 1 2-2h7a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Z", "M6 19.5h5M8.5 16v3.5", "M18.5 9.5h1A1.5 1.5 0 0 1 21 11v6.5a1.5 1.5 0 0 1-1.5 1.5h-1a1.5 1.5 0 0 1-1.5-1.5V11a1.5 1.5 0 0 1 1.5-1.5Z") }
    val Translate: ImageVector by lazy { line("PinBom.Translate", false, "M4 6h9M8.5 4v2M6 6c.5 3 2.5 5.5 5.5 7M11 6c-.5 3-2.5 5.5-6 7.5", "M12.5 19.5l3.5-8 3.5 8M13.8 16.5h4.4") }
    val Tag: ImageVector by lazy { line("PinBom.Tag", false, "M9.5 4.5L8 19.5M16 4.5l-1.5 15M5 9h14.5M4.5 15H19") }
    val Share: ImageVector by lazy { line("PinBom.Share", false, "M12 15V4M8 8l4-4 4 4", "M6.5 11H6a2 2 0 0 0-2 2v5a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-5a2 2 0 0 0-2-2h-.5") }
    val Visibility: ImageVector by lazy { line("PinBom.Visibility", false, "M2.8 12C4.6 8 8 5.8 12 5.8S19.4 8 21.2 12C19.4 16 16 18.2 12 18.2S4.6 16 2.8 12Z", "M9.5 12a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0Z") }
    val VisibilityOff: ImageVector by lazy { line("PinBom.VisibilityOff", false, "M2.8 12C4.6 8 8 5.8 12 5.8S19.4 8 21.2 12C19.4 16 16 18.2 12 18.2S4.6 16 2.8 12Z", "M9.5 12a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0Z", "M4.5 4.5l15 15") }
    val Star: ImageVector by lazy { solid("PinBom.Star", false, "M12 4.2l2.3 4.8 5.2.7-3.8 3.6.9 5.2L12 16l-4.6 2.5.9-5.2L4.5 9.7l5.2-.7Z") }
    val MoreHoriz: ImageVector by lazy { line("PinBom.MoreHoriz", false, ("M6 12h.01M12 12h.01M18 12h.01" to 3.2f)) }
    val Image: ImageVector by lazy { line("PinBom.Image", false, "M6.5 5h11A2.5 2.5 0 0 1 20 7.5v9a2.5 2.5 0 0 1-2.5 2.5h-11A2.5 2.5 0 0 1 4 16.5v-9A2.5 2.5 0 0 1 6.5 5Z", "M4 16l4.5-4.5 3.5 3.5 2.5-2.5L20 17", "M13.7 9a.8.8 0 1 0 1.6 0a.8.8 0 1 0-1.6 0Z") }
    val BrokenImage: ImageVector by lazy { line("PinBom.BrokenImage", false, "M6.5 5h11A2.5 2.5 0 0 1 20 7.5v9a2.5 2.5 0 0 1-2.5 2.5h-11A2.5 2.5 0 0 1 4 16.5v-9A2.5 2.5 0 0 1 6.5 5Z", "M10.5 5L9 10l3 2-1.5 7") }
    val CloudOff: ImageVector by lazy { line("PinBom.CloudOff", false, "M7 18.5h10a4 4 0 0 0 .7-7.94A5.5 5.5 0 0 0 7.2 9.6 4.5 4.5 0 0 0 7 18.5Z", "M4 4l16 16") }
    val Palette: ImageVector by lazy { line("PinBom.Palette", false, "M12 4a8 8 0 1 0 0 16c1.2 0 1.8-.8 1.8-1.6 0-.5-.3-.9-.5-1.3-.3-.4-.5-.8-.5-1.3 0-.9.7-1.6 1.6-1.6H16.5A3.5 3.5 0 0 0 20 10.7C20 7 16.4 4 12 4Z", ("M7.8 11h.01M10.3 7.8h.01M14.3 7.8h.01" to 2.6f)) }
    val Tune: ImageVector by lazy { line("PinBom.Tune", false, "M4 8h9M17 8h3M4 16h3M11 16h9", "M13 8a2 2 0 1 0 4 0a2 2 0 1 0-4 0Z", "M7 16a2 2 0 1 0 4 0a2 2 0 1 0-4 0Z") }
    val DarkMode: ImageVector by lazy { line("PinBom.DarkMode", false, "M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5Z") }
    val LightMode: ImageVector by lazy { line("PinBom.LightMode", false, "M8 12a4 4 0 1 0 8 0a4 4 0 1 0-8 0Z", "M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6l1.4 1.4M17 17l1.4 1.4M5.6 18.4L7 17M17 7l1.4-1.4") }
    val Send: ImageVector by lazy { line("PinBom.Send", true, "M20.5 3.5L10 14M20.5 3.5l-6.5 17-4-6.5-6.5-4 17-6.5Z") }
    val Logout: ImageVector by lazy { line("PinBom.Logout", true, "M9.5 4.5H6.5A2 2 0 0 0 4.5 6.5v11a2 2 0 0 0 2 2h3", "M15 8l4 4-4 4M19 12H9.5") }
}

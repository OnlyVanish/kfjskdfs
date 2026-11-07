package ir.pinbom.app.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import ir.pinbom.app.R
import ir.pinbom.app.ui.theme.AbsoluteWhite
import ir.pinbom.app.ui.theme.AbsoluteBlack
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Busy state and action for the add-on share-image control on the detail screen. */
data class PinImageShareAction(val preparing: Boolean, val share: (Long, String) -> Unit)

@Composable
fun rememberPinBomImageSharer(
    baseUrl: String,
    failureLabel: String,
    chooserLabel: String,
    onFailure: (String) -> Unit,
): PinImageShareAction {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var preparing by remember { mutableStateOf(false) }
    val share: (Long, String) -> Unit = { pinId, path ->
        if (!preparing) {
            scope.launch {
                preparing = true
                try {
                    val file = withContext(Dispatchers.IO) {
                        makeWatermarkedShareFile(context, baseUrl, path, pinId)
                    }
                    withContext(Dispatchers.Main) {
                        val content = FileProvider.getUriForFile(
                            context,
                            context.packageName + ".fileprovider",
                            file,
                        )
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "image/jpeg"
                            putExtra(Intent.EXTRA_STREAM, content)
                            clipData = android.content.ClipData.newUri(context.contentResolver, context.getString(R.string.share_image_label), content)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, chooserLabel).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    }
                } catch (_: Throwable) {
                    onFailure(failureLabel)
                } finally {
                    preparing = false
                }
            }
        }
    }
    return PinImageShareAction(preparing, share)
}

private suspend fun makeWatermarkedShareFile(
    context: Context,
    baseUrl: String,
    path: String,
    pinId: Long,
): File = withContext(Dispatchers.IO) {
    val source = downloadPinBitmap(context, baseUrl, path, "large")
    val output = try {
        val mutable = source.copy(Bitmap.Config.ARGB_8888, true) ?: error("Cannot prepare image")
        val canvas = Canvas(mutable)
        val width = mutable.width.toFloat()
        val height = mutable.height.toFloat()
        val margin = (width * 0.028f).coerceAtLeast(0.5f)
        val fontSize = (width * 0.038f).coerceIn(1.5f, 72f)
        val iconSize = fontSize * 1.18f
        val pad = (fontSize * 0.36f).coerceAtLeast(0.5f)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AbsoluteWhite.toArgb()
            alpha = 225
            textSize = fontSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(fontSize * 0.15f, 0f, fontSize * 0.05f, AbsoluteBlack.toArgb())
        }
        val text = context.getString(R.string.share_watermark_brand)
        val textWidth = textPaint.measureText(text)
        val groupWidth = pad * 3f + iconSize + textWidth
        val groupHeight = pad * 2f + maxOf(iconSize, fontSize)
        val left = (width - margin - groupWidth).coerceAtLeast(0f)
        val top = (height - margin - groupHeight).coerceAtLeast(0f)
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AbsoluteBlack.toArgb()
            alpha = 104
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(left, top, left + groupWidth, top + groupHeight), groupHeight * 0.28f, groupHeight * 0.28f, background)
        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_pinbom)
        drawable?.let {
            val l = (left + pad).toInt()
            val t = (top + (groupHeight - iconSize) / 2f).toInt()
            it.alpha = 218
            it.bounds = Rect(l, t, (l + iconSize).toInt(), (t + iconSize).toInt())
            it.draw(canvas)
        }
        val baseline = top + (groupHeight - fontSize) / 2f - textPaint.fontMetrics.ascent
        canvas.drawText(text, left + pad * 2f + iconSize, baseline, textPaint)
        mutable
    } finally {
        source.recycle()
    }
    val directory = File(context.cacheDir, "share_images")
    check(directory.exists() || directory.mkdirs()) { "Cannot create share cache" }
    val file = File(directory, "PinBom_${pinId}_${System.currentTimeMillis()}.jpg")
    if (!file.outputStream().use { output.compress(Bitmap.CompressFormat.JPEG, 90, it) }) {
        file.delete()
        output.recycle()
        error("Could not encode shared image")
    }
    output.recycle()
    file
}

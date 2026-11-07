package ir.pinbom.app.data

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

class ShareTargetException(val reason: Reason) : Exception(reason.name) {
    enum class Reason { NOT_IMAGE, TOO_LARGE, EMPTY, UNREADABLE }
}
object ShareTargetRules {
    const val MAX_BYTES = 32L * 1024L * 1024L
    data class Payload(val uris: List<Uri>, val imageMime: Boolean, val multiple: Boolean)
    @Suppress("DEPRECATION")
    fun sharedUris(intent: Intent): Payload? {
        if (intent.action !in setOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)) return null
        val multiple = intent.action == Intent.ACTION_SEND_MULTIPLE
        val streams = if (multiple) intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
            else listOfNotNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        val clip = intent.clipData?.let { data -> (0 until data.itemCount).mapNotNull { data.getItemAt(it).uri } }.orEmpty()
        // Only content providers are accepted, never an externally supplied path into private app data.
        return Payload((streams + clip).distinct().filter { it.scheme == "content" }, intent.type?.startsWith("image/") == true, multiple)
    }
}

suspend fun copySharedImageToCache(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
    if (uri.scheme != "content") throw ShareTargetException(ShareTargetException.Reason.UNREADABLE)
    val mime = context.contentResolver.getType(uri)
    if (mime != null && !mime.startsWith("image/")) throw ShareTargetException(ShareTargetException.Reason.NOT_IMAGE)
    val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
    val file = File(dir, "shared-${UUID.randomUUID()}.image")
    try {
        val input = context.contentResolver.openInputStream(uri) ?: throw ShareTargetException(ShareTargetException.Reason.UNREADABLE)
        var size = 0L
        input.use { source -> file.outputStream().use { target ->
            val buf = ByteArray(8192)
            while (true) {
                val n = source.read(buf)
                if (n < 0) break
                size += n
                if (size > ShareTargetRules.MAX_BYTES) throw ShareTargetException(ShareTargetException.Reason.TOO_LARGE)
                target.write(buf, 0, n)
            }
        } }
        if (size == 0L) throw ShareTargetException(ShareTargetException.Reason.EMPTY)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) throw ShareTargetException(ShareTargetException.Reason.NOT_IMAGE)
        file
    } catch (e: Exception) {
        file.delete()
        if (e is CancellationException || e is ShareTargetException) throw e
        throw ShareTargetException(ShareTargetException.Reason.UNREADABLE)
    }
}

fun cleanOldPinBomTempFiles(context: Context) {
    val cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
    // Only our own temporary share copies; do not delete queued WorkManager uploads.
    File(context.cacheDir, "shared_images").listFiles()?.forEach { file ->
        if (file.name.startsWith("shared-") && file.lastModified() < cutoff) runCatching { file.delete() }
    }
}

package ir.pinbom.app.ui.components

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Creates a gallery downloader that saves a full-resolution pin into Pictures/PinBom.
 * Android 10+ uses MediaStore (no broad storage permission); older versions request
 * the legacy write permission only when a download is actually requested.
 */
@Composable
fun rememberPinImageDownloader(
    baseUrl: String,
    successLabel: String,
    failureLabel: String,
): (Long, String) -> Unit {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var pending by remember { mutableStateOf<Pair<Long, String>?>(null) }

    fun runDownload(pinId: Long, path: String) {
        scope.launch {
            val result = runCatching {
                saveImageToPinBom(context, imageUrl(baseUrl, path), pinId)
            }
            Toast.makeText(
                context,
                if (result.isSuccess) successLabel else failureLabel,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val queued = pending
        pending = null
        if (granted && queued != null) {
            runDownload(queued.first, queued.second)
        } else if (!granted) {
            Toast.makeText(context, failureLabel, Toast.LENGTH_SHORT).show()
        }
    }

    return { pinId, path ->
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            pending = pinId to path
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            runDownload(pinId, path)
        }
    }
}

private suspend fun saveImageToPinBom(context: Context, resolvedUrl: String, pinId: Long): Uri =
    withContext(Dispatchers.IO) {
        val fetchUrl = if (resolvedUrl.startsWith("//")) "https:$resolvedUrl" else resolvedUrl
        require(fetchUrl.startsWith("http://") || fetchUrl.startsWith("https://")) {
            "Invalid image URL"
        }
        val connection = (URL(fetchUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Accept", "image/*")
        }
        var inserted: Uri? = null
        try {
            connection.connect()
            check(connection.responseCode in 200..299) { "Image download failed (${connection.responseCode})" }
            val mime = connection.contentType?.substringBefore(';')?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
            val extension = when (mime.lowercase()) {
                "image/png" -> ".png"
                "image/webp" -> ".webp"
                "image/gif" -> ".gif"
                "image/heic" -> ".heic"
                else -> ".jpg"
            }
            val fileName = "PinBom_${pinId}_${System.currentTimeMillis()}$extension"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, mime)
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/PinBom")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("Could not create gallery image")
                inserted = uri
                val destination = resolver.openOutputStream(uri, "w")
                    ?: error("Could not open destination image")
                destination.use { output ->
                    connection.inputStream.use { input -> input.copyTo(output) }
                }
                val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
                uri
            } else {
                val directory = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "PinBom",
                )
                check(directory.exists() || directory.mkdirs()) { "Could not create PinBom folder" }
                val file = File(directory, fileName)
                connection.inputStream.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
                MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mime), null)
                Uri.fromFile(file)
            }
        } catch (error: Throwable) {
            inserted?.let { runCatching { context.contentResolver.delete(it, null, null) } }
            throw error
        } finally {
            connection.disconnect()
        }
    }

/** Downloads a pin through the same HTTP/media path as the gallery downloader, using a requested server variant. */
suspend fun downloadPinBitmap(
    context: android.content.Context,
    baseUrl: String,
    path: String,
    variant: String = "large",
): android.graphics.Bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val resolved = imageUrl(baseUrl, path, variant)
    val fetchUrl = if (resolved.startsWith("//")) "https:$resolved" else resolved
    require(fetchUrl.startsWith("http://") || fetchUrl.startsWith("https://")) { "Invalid image URL" }
    val connection = (java.net.URL(fetchUrl).openConnection() as java.net.HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        instanceFollowRedirects = true
        requestMethod = "GET"
        setRequestProperty("Accept", "image/*")
    }
    try {
        connection.connect()
        check(connection.responseCode in 200..299) { "Image download failed (${connection.responseCode})" }
        val length = connection.contentLengthLong
        require(length <= 50L * 1024L * 1024L || length < 0) { "Image too large to share" }
        val bitmap = connection.inputStream.use { android.graphics.BitmapFactory.decodeStream(it) }
            ?: error("Image could not be decoded")
        bitmap
    } finally {
        connection.disconnect()
    }
}

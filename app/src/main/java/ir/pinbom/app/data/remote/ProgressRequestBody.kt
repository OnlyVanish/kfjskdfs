package ir.pinbom.app.data.remote

import java.io.File
import java.io.IOException
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink

/** Replayable file body, length snapshotted before the request is sent. */
class ProgressRequestBody(private val file: File, private val type: MediaType,
    private val progress: (Long, Long) -> Unit) : RequestBody() {
    private val size = file.length()
    override fun contentType(): MediaType = type
    override fun contentLength(): Long = size
    override fun isOneShot(): Boolean = false
    override fun writeTo(sink: BufferedSink) {
        var sent = 0L
        progress(0L, size)
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (sent < size) {
                val n = input.read(buffer, 0, minOf(buffer.size.toLong(), size - sent).toInt())
                if (n < 0) throw IOException("File changed: sent $sent of $size bytes")
                sink.write(buffer, 0, n)
                sent += n
                progress(sent, size)
            }
            if (input.read() != -1) throw IOException("File grew during upload")
        }
        // Do not close a caller-owned sink. Writes go directly to the supplied sink,
        // so no private buffered tail can be lost before the multipart boundary.
    }
}

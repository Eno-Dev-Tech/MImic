package com.example.mimic.update

import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.coroutineContext

/** A cancelled/incomplete transfer can never become an installable cached APK. */
internal object DownloadTransfer {
    suspend fun receive(input: InputStream, target: File, limit: Long, total: Long?, progress: (Long) -> Unit) {
        val part = File(target.path + ".part")
        part.delete()
        target.delete()
        try {
            var downloaded = 0L
            part.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    coroutineContext.ensureActive()
                    val n = input.read(buffer)
                    if (n < 0) break
                    downloaded += n
                    if (downloaded > limit) throw IOException("Download is larger than expected")
                    output.write(buffer, 0, n)
                    progress(downloaded)
                }
            }
            coroutineContext.ensureActive()
            if (total != null && downloaded != total) throw IOException("Download incomplete: received $downloaded of $total bytes")
            if (!part.renameTo(target)) throw IOException("Couldn't store the update")
        } finally {
            part.delete()
        }
    }
}

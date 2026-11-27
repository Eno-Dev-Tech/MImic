package com.example.mimic

import com.example.mimic.update.DownloadTransfer
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files

class DownloadTransferTest {
    private fun target() = Files.createTempDirectory("mimic-download-test").toFile().resolve("update.apk")
    private suspend fun rejects(target: File, input: InputStream, total: Long, limit: Long = total) {
        try {
            DownloadTransfer.receive(input, target, limit, total) {}
            fail("Transfer should have failed")
        } catch (_: IOException) { }
        assertFalse(target.exists())
        assertFalse(File(target.path + ".part").exists())
    }

    @Test fun interruptedTruncatedAndOversizedDownloadsNeverBecomeApks() = runBlocking {
        val target = target()
        try {
            rejects(target, ByteArrayInputStream(byteArrayOf(1)), 2)
            rejects(target, ByteArrayInputStream(byteArrayOf(1, 2)), 1)
            rejects(target, object : InputStream() { override fun read(): Int = throw IOException("connection lost") }, 2)
        } finally { target.parentFile.deleteRecursively() }
    }

    @Test fun cancellationCleansPartialAndRetryStartsFresh() = runBlocking {
        val target = target()
        try {
            val transfer = launch(start = CoroutineStart.LAZY) {
                DownloadTransfer.receive(ByteArrayInputStream(ByteArray(150_000)), target, 150_000, 150_000) { cancel() }
            }
            transfer.start()
            transfer.join()
            assertTrue(transfer.isCancelled)
            assertFalse(target.exists())
            assertFalse(File(target.path + ".part").exists())
            DownloadTransfer.receive(ByteArrayInputStream(byteArrayOf(7, 8)), target, 2, 2) {}
            assertArrayEquals(byteArrayOf(7, 8), target.readBytes())
        } finally { target.parentFile.deleteRecursively() }
    }
}

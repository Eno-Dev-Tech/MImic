package com.example.mimic

import android.os.Bundle
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mimic.data.AppSettings
import com.example.mimic.player.SponsorBlock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Real Media3 playback of a generated silent WAV. Never uses user audio or network. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class SponsorBlockPlaybackTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun checkPlayback(enabled: Boolean, party: Boolean, shouldSkip: Boolean) {
        val wav = File(context.cacheDir, "sponsorblock-test.wav")
        val cache = File(context.cacheDir, "sponsorblock/TEST_video1").resolveSibling("TEST_video1.json")
        // 11-character synthetic ID; local URI means the lookup must use cache only.
        val id = "TEST_video1"
        val pcmSize = 8 * 8000 * 2
        val buffer = ByteBuffer.allocate(44 + pcmSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(36 + pcmSize).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(8000).putInt(16000).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(pcmSize)
        wav.writeBytes(buffer.array())
        cache.parentFile!!.mkdirs()
        cache.writeText("""[{"category":"sponsor","segment":[1,5]}]""")
        lateinit var player: ExoPlayer
        lateinit var skipper: SponsorBlock
        var playerCreated = false
        var skipperCreated = false
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        try {
            onMain {
                player = ExoPlayer.Builder(context).build()
                playerCreated = true
                skipper = SponsorBlock(context, player, scope, MutableStateFlow(AppSettings(sponsorBlockEnabled = enabled))) { party }
                skipperCreated = true
                player.setMediaItem(MediaItem.Builder().setUri(android.net.Uri.fromFile(wav))
                    .setMediaMetadata(MediaMetadata.Builder().setExtras(Bundle().apply { putString("mimic_youtube_id", id) }).build()).build())
                player.prepare(); player.play()
            }
            val deadline = SystemClock.elapsedRealtime() + 10000
            var playing = false
            while (!playing && SystemClock.elapsedRealtime() < deadline) {
                onMain { playing = player.isPlaying }
                SystemClock.sleep(50)
            }
            assertTrue("Generated local audio must start playing", playing)
            SystemClock.sleep(1800)
            var position = 0L
            onMain { position = player.currentPosition }
            if (shouldSkip) assertTrue("Expected cached SponsorBlock seek, position=$position", position >= 5000)
            else assertTrue("Disabled/party playback must not seek, position=$position", position in 1000..3999)
        } finally {
            onMain { if (skipperCreated) skipper.release(); scope.cancel(); if (playerCreated) player.release() }
            wav.delete(); cache.delete()
        }
    }

    @Test fun offlineCachedSegmentSkipsOnExistingPlayer() = checkPlayback(true, false, true)
    @Test fun disabledSettingKeepsOriginalTimeline() = checkPlayback(false, false, false)
    @Test fun partyPlaybackKeepsOriginalTimeline() = checkPlayback(true, true, false)
}

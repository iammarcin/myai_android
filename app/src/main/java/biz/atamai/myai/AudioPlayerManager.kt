// AudioPlayerManager.kt

package biz.atamai.myai

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.SeekBar
import android.widget.Toast
import java.io.File
import java.io.IOException

class AudioPlayerManager(private val mainHandler: MainHandler) {

    var mediaPlayer: MediaPlayer? = null
    private var handler: Handler = Handler(Looper.getMainLooper())
    private var isPlaying = false
    var currentUri: Uri? = null
    private var onCompletion: (() -> Unit)? = null

    fun playAudio(audioUri: Uri?, seekBar: SeekBar, message: String, onCompletion: () -> Unit) {
        println("AudioPlayerManager.playAudio")
        println("audioUri: $audioUri")
        if (audioUri == null) {
            createToastMessage("Invalid audio file")
            return
        }

        val file = File(audioUri.path)
        val fileSizeInBytes = file.length()
        val fileSizeInKB = fileSizeInBytes / 1024
        val fileSizeInMB = fileSizeInKB / 1024
        println("File size: $fileSizeInBytes bytes, $fileSizeInKB KB, $fileSizeInMB MB")

        println("audioUri.scheme: ${audioUri.scheme}")

        if (currentUri != audioUri) {
            stopAudio()
            currentUri = audioUri
        }
        try {
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer.create(mainHandler.context, audioUri)
                mediaPlayer?.setOnCompletionListener {
                    stopAudio()
                    onCompletion()
                }
            }
            mediaPlayer?.start()
            isPlaying = true
            // it's far from good ... but if i get file from S3 (for example TTS after restoring session or TTS no stream) - there is no way to get duration of audio file... so we're setting it based on text length
            // we estimate duration based on the length of chatItemMessage
            // we came up with 2.3 words per second (so just in case i take little bit less)
            val estimatedDuration =
                (message.split("\\s+".toRegex()).size / 2 * 1000).toInt() // duration in milliseconds

            seekBar.max =
                if (mediaPlayer?.duration!! > 0) mediaPlayer?.duration!! else estimatedDuration
            handler.post(object : Runnable {
                override fun run() {
                    seekBar.progress = mediaPlayer?.currentPosition ?: 0
                    if (isPlaying) {
                        handler.postDelayed(this, 1000)
                    }
                }
            })
            this.onCompletion = onCompletion
            setSeekBar(seekBar)
        } catch (e: IOException) {
            createToastMessage("Error playing audio: ${e.message}")
            stopAudio()
        } catch (e: IllegalStateException) {
            createToastMessage("Error playing audio: ${e.message}")
            stopAudio()
        } catch (e: Exception) {
            createToastMessage("An unexpected error occurred: ${e.message}")
            stopAudio()
        }
    }

    private fun stopAudio() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        isPlaying = false
        handler.removeCallbacksAndMessages(null)
        currentUri = null
    }

    fun pauseAudio() {
        mediaPlayer?.pause()
        isPlaying = false
        handler.removeCallbacksAndMessages(null)
    }

    fun isPlaying(): Boolean {
        return isPlaying
    }

    fun releaseMediaPlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    fun setSeekBar(seekBar: SeekBar) {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer?.seekTo(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    fun createToastMessage(message: String, duration: Int = Toast.LENGTH_SHORT) {
        mainHandler.executeOnUIThread {
            mainHandler.createToastMessage(message)
        }
    }
}

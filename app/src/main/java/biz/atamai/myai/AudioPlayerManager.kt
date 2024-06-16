package biz.atamai.myai

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.SeekBar
import android.widget.Toast

class AudioPlayerManager(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var seekBar: SeekBar? = null
    private var handler: Handler = Handler(Looper.getMainLooper())
    private var isPlaying = false
    var currentUri: Uri? = null
    private var onCompletion: (() -> Unit)? = null

    fun playAudio(audioUri: Uri, onCompletion: () -> Unit) {
        if (currentUri != audioUri) {
            stopAudio()
            currentUri = audioUri
        }
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer.create(context, audioUri)
            mediaPlayer?.setOnCompletionListener {
                stopAudio()
                onCompletion()
            }
        }
        mediaPlayer?.start()
        isPlaying = true
        seekBar?.max = mediaPlayer?.duration ?: 0
        handler.postDelayed(updateSeekBar, 0)
        this.onCompletion = onCompletion
    }

    private fun stopAudio() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        isPlaying = false
        seekBar?.progress = 0
        handler.removeCallbacks(updateSeekBar)
        currentUri = null
    }

    fun pauseAudio() {
        mediaPlayer?.pause()
        isPlaying = false
        handler.removeCallbacks(updateSeekBar)
    }

    private val updateSeekBar = object : Runnable {
        override fun run() {
            seekBar?.progress = mediaPlayer?.currentPosition ?: 0
            handler.postDelayed(this, 1000)
        }
    }

    fun isPlaying(): Boolean {
        return isPlaying
    }

    fun releaseMediaPlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    fun createToastMessage(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(context, message, duration).show()
    }

    fun setSeekBar(seekBar: SeekBar?) {
        this.seekBar = seekBar
    }
}

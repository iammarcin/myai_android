package biz.atamai.myai

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.SeekBar
import android.widget.Toast

class AudioPlayerManager(private val mainHandler: MainHandler) {

    var mediaPlayer: MediaPlayer? = null
    private var handler: Handler = Handler(Looper.getMainLooper())
    private var isPlaying = false
    var currentUri: Uri? = null
    private var onCompletion: (() -> Unit)? = null
    private var currentSeekBar: SeekBar? = null

    fun playAudio(audioUri: Uri, onCompletion: () -> Unit, seekBar: SeekBar, message: String) {
        if (currentUri != audioUri) {
            stopAudio()
            currentUri = audioUri
        }
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer.create(mainHandler.context, audioUri)
            mediaPlayer?.setOnCompletionListener {
                stopAudio()
                onCompletion()
            }
        }
        mediaPlayer?.start()
        isPlaying = true
        currentSeekBar = seekBar
        seekBar.max = mediaPlayer?.duration ?: 0
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
    }

    fun stopAudio() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        isPlaying = false
        handler.removeCallbacksAndMessages(null)
        currentUri = null
        currentSeekBar?.progress = 0
        currentSeekBar = null
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
        mainHandler.createToastMessage(message)
    }
}

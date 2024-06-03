package biz.atamai.myai

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.SeekBar
import android.widget.Toast
import biz.atamai.myai.databinding.ChatItemBinding

class AudioPlayerManager(private val context: Context, private val binding: ChatItemBinding) {

    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var currentUri: Uri? = null
    private var isPrepared = false
    private var audioPlayCounter = 0

    init {
        setupSeekBarChangeListener()
        setupPlayButtonClickListener()
    }

    fun setupMediaPlayer(audioUri: Uri?, autoPlay: Boolean = false) {
        println("setupMediaPlayer EXEC. isPlaying: $audioPlayCounter")
        if (audioPlayCounter > 0) return
        currentUri = audioUri
        releaseMediaPlayer() // Release any existing player
        mediaPlayer = MediaPlayer().apply {
            setDataSource(context, audioUri!!)
            setOnPreparedListener { mp ->
                isPrepared = true
                binding.seekBar.max = mp.duration  // Set maximum value of the seek bar
                binding.playButton.setImageResource(R.drawable.ic_play_arrow_24)
                if (autoPlay && audioPlayCounter == 0 && !mp.isPlaying) {
                    println("EXECUTE autoplay")
                    //incrementAudioPlayCounter()
                    println("4AUDIO PLAYER play click mp.isPlaying: ${mp.isPlaying}")
                    println("5AUDIO PLAYER play click mp.isPlaying: ${mediaPlayer?.isPlaying}")
                    binding.playButton.performClick()
                }
            }

            setOnCompletionListener {
                binding.playButton.setImageResource(R.drawable.ic_play_arrow_24)
                binding.seekBar.progress = 0
                isPrepared = false
                decrementAudioPlayCounter()
                println("COMPLETED!!!!")
            }

            setOnErrorListener { _, what, extra ->
                println("MediaPlayer Error - what: $what, extra: $extra")
                Toast.makeText(context, "Error playing audio 0", Toast.LENGTH_SHORT).show()
                decrementAudioPlayCounter()
                releaseMediaPlayer()
                true
            }

            prepareAsync()
        }
    }

    private fun setupSeekBarChangeListener() {
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer?.seekTo(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                mediaPlayer?.pause()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                mediaPlayer?.start()
            }
        })
    }

    private fun setupPlayButtonClickListener() {
        binding.playButton.setOnClickListener {
            println("3AUDIO PLAYER play click mp.isPlaying: ${mediaPlayer?.isPlaying}")
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    mp.pause()
                    binding.playButton.setImageResource(R.drawable.ic_play_arrow_24)
                    decrementAudioPlayCounter()
                } else {
                    try {
                        if (isPrepared) {
                            println("1AUDIO PLAYER play click mp.isPlaying: ${mp.isPlaying}")
                            println("2AUDIO PLAYER play click mp.isPlaying: ${mediaPlayer?.isPlaying}")
                            if (!mp.isPlaying) {
                                mp.start()
                                binding.playButton.setImageResource(R.drawable.ic_pause_24)
                                handler.post(updateSeekBarTask)  // Start updating the seek bar
                                incrementAudioPlayCounter()
                            }
                        } else {
                            resetAndPrepareMediaPlayer()
                        }
                    } catch (e: IllegalStateException) {
                        // Handle the situation when the media player is in an invalid state
                        Toast.makeText(context, "Error playing audio 1", Toast.LENGTH_SHORT).show()
                        resetAndPrepareMediaPlayer()
                    }
                }
            } ?: run {
                // If mediaPlayer is null, reinitialize it and start playing
                resetAndPrepareMediaPlayer()
            }
        }
    }

    private fun resetAndPrepareMediaPlayer() {
        currentUri?.let { uri ->
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, uri)
                setOnPreparedListener { mp ->
                    if (!mp.isPlaying) {
                        isPrepared = true
                        binding.seekBar.max = mp.duration
                        mp.start()
                        binding.playButton.setImageResource(R.drawable.ic_pause_24)
                        handler.post(updateSeekBarTask)
                        incrementAudioPlayCounter()
                    }
                }
                setOnCompletionListener {
                    binding.playButton.setImageResource(R.drawable.ic_play_arrow_24)
                    binding.seekBar.progress = 0
                    isPrepared = false
                    decrementAudioPlayCounter()
                }
                setOnErrorListener { _, _, _ ->
                    Toast.makeText(context, "Error playing audio 2", Toast.LENGTH_SHORT).show()
                    releaseMediaPlayer()
                    decrementAudioPlayCounter()
                    true
                }
                prepareAsync()
            }
        }
    }

    fun releaseMediaPlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
        isPrepared = false
        handler.removeCallbacks(updateSeekBarTask)  // Stop updating the seek bar
    }

    private val updateSeekBarTask = object : Runnable {
        override fun run() {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    binding.seekBar.progress = mp.currentPosition
                    handler.postDelayed(this, 1000)  // Schedule the next update after 1 second
                }
            }
        }
    }

    private fun incrementAudioPlayCounter() {

        audioPlayCounter++
        println("INcrease! current value: $audioPlayCounter")
    }
    private fun decrementAudioPlayCounter() {

        if (audioPlayCounter > 0)
            audioPlayCounter--

            println("DEcrease! current value: $audioPlayCounter")
    }

    // Check if the media player is playing (used in ChatAdapter in handleTTSCompletedResponse)
    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying == true
    }
}

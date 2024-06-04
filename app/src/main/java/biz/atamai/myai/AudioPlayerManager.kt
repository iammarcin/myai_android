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

    init {
        setupSeekBarChangeListener()
        setupPlayButtonClickListener()
    }

    fun setupMediaPlayer(audioUri: Uri?, autoPlay: Boolean = false) {
        currentUri = audioUri
        println("!!! AUDIO URI: $audioUri")
        releaseMediaPlayer() // Release any existing player
        mediaPlayer = MediaPlayer().apply {
            setDataSource(context, audioUri!!)
            setOnPreparedListener { mp ->
                isPrepared = true
                binding.seekBar.max = mp.duration  // Set maximum value of the seek bar
                binding.playButton.setImageResource(R.drawable.ic_play_arrow_24)
                if (autoPlay && !mp.isPlaying) {
                    binding.playButton.performClick()
                }
            }

            setOnCompletionListener {
                binding.playButton.setImageResource(R.drawable.ic_play_arrow_24)
                binding.seekBar.progress = 0
                isPrepared = false
            }

            setOnErrorListener { _, what, extra ->
                println("MediaPlayer Error - what: $what, extra: $extra")
                Toast.makeText(context, "Error playing audio 0", Toast.LENGTH_SHORT).show()
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
            println("!!! AUDIO URI 222 : $currentUri")
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    mp.pause()
                    binding.playButton.setImageResource(R.drawable.ic_play_arrow_24)
                } else {
                    try {
                        if (isPrepared) {
                            if (!mp.isPlaying) {
                                mp.start()
                                binding.playButton.setImageResource(R.drawable.ic_pause_24)
                                handler.post(updateSeekBarTask)  // Start updating the seek bar
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
                    }
                }
                setOnCompletionListener {
                    binding.playButton.setImageResource(R.drawable.ic_play_arrow_24)
                    binding.seekBar.progress = 0
                    isPrepared = false
                }
                setOnErrorListener { _, _, _ ->
                    Toast.makeText(context, "Error playing audio 2", Toast.LENGTH_SHORT).show()
                    releaseMediaPlayer()
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

    // Check if the media player is playing (used in ChatAdapter in handleTTSCompletedResponse)
    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying == true
    }
}

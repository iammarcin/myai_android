package biz.atamai.myai

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import biz.atamai.myai.databinding.ChatItemBinding
import android.widget.SeekBar

class ChatAdapter(private val chatItems: MutableList<ChatItem>) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {
    private val mediaPlayers: MutableList<MediaPlayer> = mutableListOf()

    fun releaseMediaPlayers() {
        for (mediaPlayer in mediaPlayers) {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop()
            }
            mediaPlayer.release()
        }
        mediaPlayers.clear() // Clear the list after releasing
    }

    inner class ChatViewHolder(private val binding: ChatItemBinding) : RecyclerView.ViewHolder(binding.root) {
        private var mediaPlayer: MediaPlayer? = null
        val handler = Handler(Looper.getMainLooper())
        fun bind(chatItem: ChatItem) {
            binding.messageTextView.text = chatItem.message

            if (chatItem.imageUris.isNotEmpty()) {
                binding.scrollViewImages.visibility = View.VISIBLE
                binding.imageContainer.removeAllViews() // Clear old images
                for (uri in chatItem.imageUris) {
                    val imageView = ImageView(binding.root.context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.MATCH_PARENT
                        ).also {
                            it.marginEnd = 8.dpToPx(binding.root.context)
                        }
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        adjustViewBounds = true
                        setImageURI(uri)
                    }
                    binding.imageContainer.addView(imageView)
                }
            } else {
                binding.scrollViewImages.visibility = View.GONE
            }

            if (chatItem.fileNames.isNotEmpty()) {
                binding.audioPlayer.visibility = View.VISIBLE

                // here we assume this is audio file - as we did not implement anything else
                // if its audio - there will be only single filename in the list
                // and we can process it - either play audio or transcribe
                setupMediaPlayer(chatItem.fileNames[0])
                // set transcribe button
                binding.transcribeButton.visibility = View.VISIBLE
                binding.transcribeButton.setOnClickListener {
                    // take first item from fileNames
                    // as above it should be only single file
                    val fileName = chatItem.fileNames[0]
                    println("AIOHDASIUHDISAUHDIUSAHIUASDHIUDASHIUSD")
                    println(chatItem.fileNames)
                }
            }
        }

        private fun setupMediaPlayer(audioUri: Uri?) {
            releaseMediaPlayer() // Release any existing player
            mediaPlayer = MediaPlayer.create(binding.root.context, audioUri).apply {
                // Add this MediaPlayer to the adapter's list
                mediaPlayers.add(this)

                setOnPreparedListener { mp ->
                    binding.seekBar.max = mp.duration  // Set maximum value of the seek bar

                    // Play/Pause toggle button
                    binding.playButton.setOnClickListener {
                        if (mp.isPlaying) {
                            mp.pause()
                            binding.playButton.setImageResource(R.drawable.baseline_play_arrow_24)
                        } else {
                            mp.start()
                            binding.playButton.setImageResource(R.drawable.baseline_pause_24)
                            handler.post(updateSeekBarTask)  // Start updating the seek bar
                        }
                    }

                    // SeekBar change listener
                    binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                            if (fromUser) {
                                mp.seekTo(progress)
                            }
                        }

                        override fun onStartTrackingTouch(seekBar: SeekBar?) {
                            // Optional: Pause playback while user is dragging the seek bar
                            mp.pause()
                        }

                        override fun onStopTrackingTouch(seekBar: SeekBar?) {
                            // Optional: Resume playback after user releases the seek bar
                            mp.start()
                        }
                    })
                }

                setOnCompletionListener {
                    binding.playButton.setImageResource(R.drawable.baseline_play_arrow_24)
                    binding.seekBar.progress = 0
                    releaseMediaPlayer()  // Automatically release when playback is complete
                }
            }
        }

        private fun releaseMediaPlayer() {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayers.remove(mediaPlayer)  // Remove from the adapter's list
            handler.removeCallbacks(updateSeekBarTask)  // Stop updating the seek bar
            mediaPlayer = null
        }

        protected fun finalize() {
            releaseMediaPlayer()
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
    }

    private fun Int.dpToPx(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatAdapter.ChatViewHolder {
        val binding = ChatItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatAdapter.ChatViewHolder, position: Int) {
        holder.bind(chatItems[position])
    }

    override fun getItemCount(): Int = chatItems.size

}

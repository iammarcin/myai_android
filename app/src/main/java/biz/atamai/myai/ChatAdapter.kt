package biz.atamai.myai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import biz.atamai.myai.databinding.ChatItemBinding
import android.widget.SeekBar
import android.widget.Toast
import androidx.core.content.ContextCompat

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

        init {
            binding.root.setOnLongClickListener { view ->
                showPopupMenu(view, adapterPosition)
                true // Return true to indicate the callback consumed the long click
            }

            //binding.menuButton.setOnClickListener { view ->
            //    showPopupMenu(view)
            //}
        }

        private var mediaPlayer: MediaPlayer? = null
        val handler = Handler(Looper.getMainLooper())
        fun bind(chatItem: ChatItem) {
            binding.messageTextView.text = chatItem.message
            binding.nameTextView.text = if (chatItem.isUserMessage) "USER" else "AI"

            binding.avatarImageView.setImageResource(
                if (chatItem.isUserMessage) R.drawable.user_avatar_placeholder
                else R.drawable.ai_avatar_placeholder
            )

            // if URIs for images are set - those are images
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

            // if filenames are set - those are non images but different kind of files
            // for the moment - audio - but later maybe others
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

        // little popup menu for chat items (when we can edit message etc)
        private fun showPopupMenu(view: View, position: Int) {
            val context = view.context
            val chatItem = chatItems[position]

            val popupMenu = PopupMenu(ContextThemeWrapper(context, R.style.PopupMenuStyle), view)

            //val popupMenu = PopupMenu(context, view)
            if (chatItem.isUserMessage) {
                popupMenu.inflate(R.menu.user_message_menu)
            } else {
                popupMenu.inflate(R.menu.ai_message_menu)
            }
            popupMenu.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.edit -> {
                        // Handle edit action
                        true
                    }
                    R.id.delete -> {
                        // Handle delete action
                        true
                    }
                    R.id.regenerate -> {
                        // Handle regenerate action
                        true
                    }
                    R.id.copy -> {
                        // Copy text to clipboard
                        val clipboard = ContextCompat.getSystemService(context, ClipboardManager::class.java)
                        val clip = ClipData.newPlainText("Copied Message", chatItem.message)
                        clipboard?.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        true
                    }
                    else -> false
                }
            }
            popupMenu.show()
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
                        mediaPlayer?.let { mp ->
                            if (mp.isPlaying) {
                                mp.pause()
                                binding.playButton.setImageResource(R.drawable.baseline_play_arrow_24)
                            } else {
                                try {
                                    mp.start()
                                    binding.playButton.setImageResource(R.drawable.baseline_pause_24)
                                    handler.post(updateSeekBarTask)  // Start updating the seek bar
                                } catch (e: IllegalStateException) {
                                    // Handle the situation when the media player is in an invalid state
                                    Toast.makeText(binding.root.context, "Error playing audio", Toast.LENGTH_SHORT).show()
                                    releaseMediaPlayer()  // Consider releasing and possibly reinitializing the media player
                                }
                            }
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

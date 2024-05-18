package biz.atamai.myai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import biz.atamai.myai.databinding.ChatItemBinding

class ChatAdapter(
    private val chatItems: MutableList<ChatItem>,
    private val onEditMessage: (position: Int, message: String) -> Unit
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {
    private val audioPlayerManagers: MutableList<AudioPlayerManager> = mutableListOf()

    fun releaseMediaPlayers() {
        for (audioPlayerManager in audioPlayerManagers) {
            audioPlayerManager.releaseMediaPlayer()
        }
        audioPlayerManagers.clear() // Clear the list after releasing
    }

    inner class ChatViewHolder(private val binding: ChatItemBinding) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnLongClickListener { view ->
                showPopupMenu(view, adapterPosition)
                true // Return true to indicate the callback consumed the long click
            }
        }

        fun bind(chatItem: ChatItem) {
            binding.messageTextView.text = chatItem.message

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
                val audioPlayerManager = AudioPlayerManager(binding.root.context, binding)
                audioPlayerManager.setupMediaPlayer(chatItem.fileNames[0])
                audioPlayerManagers.add(audioPlayerManager)
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

            if (chatItem.isUserMessage) {
                popupMenu.inflate(R.menu.user_message_menu)
            } else {
                popupMenu.inflate(R.menu.ai_message_menu)
            }
            popupMenu.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.edit -> {
                        // Handle edit action
                        onEditMessage(position, chatItem.message)
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

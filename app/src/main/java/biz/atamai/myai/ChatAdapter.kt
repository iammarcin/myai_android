package biz.atamai.myai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import com.squareup.picasso.Picasso
import io.noties.markwon.Markwon

class ChatAdapter(
    private val chatItems: MutableList<ChatItem>,
    private val onEditMessage: (position: Int, message: String) -> Unit
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {
    private val audioPlayerManagers: MutableList<AudioPlayerManager> = mutableListOf()
    private lateinit var markwon: Markwon

    fun releaseMediaPlayers() {
        for (audioPlayerManager in audioPlayerManagers) {
            audioPlayerManager.releaseMediaPlayer()
        }
        audioPlayerManagers.clear() // Clear the list after releasing
    }

    inner class ChatViewHolder(private val binding: ChatItemBinding) : RecyclerView.ViewHolder(binding.root) {

        init {
            // Initialize Markwon
            markwon = Markwon.create(binding.root.context)
            // long press listener - on messages to show popup menu
            val longClickListener = View.OnLongClickListener { view ->
                showPopupMenu(view, adapterPosition)
                true // Return true to indicate the callback consumed the long click
            }

            // trying multiple ways to set long click listener (multiple items)
            // goal is to have popup menu when click everywhere on the message
            // first one for sure works (of course)
            binding.root.setOnLongClickListener(longClickListener)
            binding.chatItemMessageContainer.setOnLongClickListener(longClickListener)
            binding.imageContainer.setOnLongClickListener(longClickListener)
            binding.scrollViewImages.setOnLongClickListener(longClickListener)
            binding.messageTextView.setOnLongClickListener(longClickListener)
        }

        fun bind(chatItem: ChatItem) {
            // Render message using Markwon
            markwon.setMarkdown(binding.messageTextView, chatItem.message)

            binding.avatarImageView.setImageResource(
                if (chatItem.isUserMessage) R.drawable.user_avatar_placeholder
                else chatItem.aiCharacterImageResId ?: R.drawable.ai_avatar_placeholder
            )

            // if URIs for images are set - those are images
            if (chatItem.imageLocations.isNotEmpty()) {
                binding.scrollViewImages.visibility = View.VISIBLE
                binding.imageContainer.removeAllViews() // Clear old images
                for (url in chatItem.imageLocations) {
                    val imageView = ImageView(binding.root.context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.MATCH_PARENT
                        ).also {
                            it.marginEnd = 8.dpToPx(binding.root.context)
                        }
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        adjustViewBounds = true
                        Picasso.get().load(url.toString()).into(this)
                    }
                    binding.imageContainer.addView(imageView)
                }
            } else {
                binding.scrollViewImages.visibility = View.GONE
            }

            println("TOTOTOTO!!!!2")
            // if filenames are set - those are non images but different kind of files
            // for the moment - audio - but later maybe others
            if (chatItem.fileNames.isNotEmpty()) {
                println("chat item file names: ${chatItem.fileNames}")
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
                    val audioFilePath = chatItem.fileNames[0].path // Ensure the correct path is obtained

                    val apiUrl = (binding.root.context as MainActivity).apiUrl
                    val utilityTools = UtilityTools(
                        context = binding.root.context,
                        onResponseReceived = { response ->
                            (binding.root.context as MainActivity).runOnUiThread {
                                (binding.root.context as MainActivity).handleTextMessage(response)
                            }
                        },
                        onError = { error ->
                            (binding.root.context as MainActivity).runOnUiThread {
                                Toast.makeText(binding.root.context, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    utilityTools.uploadFileToServer(audioFilePath, apiUrl, "chat_audio2text", "speech", "chat")
                }

            } else {
                binding.audioPlayer.visibility = View.GONE
                binding.transcribeButton.visibility = View.GONE
            }
        }

        // little popup menu for chat items (when we can edit message etc)
        private fun showPopupMenu(view: View, position: Int) {
            val context = view.context
            val chatItem = chatItems[position]

            val popupMenu = PopupMenu(ContextThemeWrapper(context, R.style.PopupMenuStyle), view)

            // Check if the message is one of the last two messages
            val isLastTwoMessages = position >= chatItems.size - 2

            if (chatItem.isUserMessage) {
                if (isLastTwoMessages) {
                    popupMenu.inflate(R.menu.user_message_menu)
                } else {
                    popupMenu.menu.add(0, R.id.copy, 0, "Copy")
                    popupMenu.menu.add(0, R.id.newSessionFromHere, 1, "New session from here")
                }
            } else {
                if (isLastTwoMessages) {
                    popupMenu.inflate(R.menu.ai_message_menu)
                } else {
                    popupMenu.menu.add(0, R.id.copy, 0, "Copy")
                    popupMenu.menu.add(0, R.id.newSessionFromHere, 1, "New session from here")
                }
            }

            popupMenu.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.edit -> {
                        // Handle edit action
                        onEditMessage(position, chatItem.message)
                        true
                    }
                    R.id.regenerate -> {
                        // Handle regenerate action
                        true
                    }
                    R.id.newSessionFromHere -> {
                        // Handle new session from here action
                        (context as MainActivity).chatHelper.createNewSessionFromHere(position)
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

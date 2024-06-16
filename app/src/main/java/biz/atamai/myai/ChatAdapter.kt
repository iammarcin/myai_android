// ChatAdapter.kt

package biz.atamai.myai

import android.app.Dialog
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
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import biz.atamai.myai.databinding.ChatItemBinding
import biz.atamai.myai.databinding.DialogFullscreenImagesBinding
import com.squareup.picasso.Picasso
import io.noties.markwon.Markwon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChatAdapter(
    private val chatItems: MutableList<ChatItem>,
    private val apiUrl: String,
    private val characterManager: CharacterManager,
    private val mainHandler: MainHandler,
    private val audioPlayerManager: AudioPlayerManager,
    private val onEditMessage: (position: Int, message: String) -> Unit,
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {
    //private val audioPlayerManagers: MutableList<AudioPlayerManager> = mutableListOf()
    private lateinit var markwon: Markwon
    private var utilityTools: UtilityTools
    private var chatHelperHandler: ChatHelperHandler? = null

    // to track which chat item is playing (to handle proper icon - play/pause changes)
    private var currentPlayingPosition: Int = -1
    // and proper seekbar to be updated
    private var currentPlayingSeekBar: SeekBar? = null

    fun setChatHelperHandler(chatHelperHandler: ChatHelperHandler) {
        this.chatHelperHandler = chatHelperHandler
    }

    init {
        utilityTools = UtilityTools(
            mainHandler = mainHandler
        )
    }

    fun triggerImageGeneration(position: Int) {
        val chatItem = chatItems[position]
        chatItem.imageLocations = listOf("image_placeholder_url")  // Ensure this list is not empty to prevent multiple triggers

        // Notify the item changed to update the UI
        notifyItemChanged(position)
    }

    inner class ChatViewHolder(val binding: ChatItemBinding) : RecyclerView.ViewHolder(binding.root) {
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
            println("EXECUTED BIND")

            // Render message using Markwon
            markwon.setMarkdown(binding.messageTextView, chatItem.message)

            val avatarResId = if (chatItem.isUserMessage) {
                R.drawable.user_avatar_placeholder
            } else {
                chatItem.aiCharacterName?.let { characterName ->
                    characterManager.getCharacterImageResId(characterName)
                } ?: R.drawable.ai_avatar_placeholder
            }

            binding.avatarImageView.setImageResource(avatarResId)
            // if URIs for images are set - those are images
            if (chatItem.imageLocations.isNotEmpty()) {
                binding.scrollViewImages.visibility = View.VISIBLE
                binding.imageContainer.removeAllViews() // Clear old images

                // if we have image - lets add click listener to show it in full screen
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

                        setOnClickListener {
                            showFullScreenImages(chatItem.imageLocations, chatItem.imageLocations.indexOf(url))
                        }
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

                binding.playButton.setOnClickListener {
                    println("PLAY BUTTON CLICKED")
                    val previousPlayingPosition = currentPlayingPosition
                    var fileToPlay: Uri? = chatItem.fileNames.firstOrNull()

                    // helper internal function - as it will be used in two different conditions below
                    // but mainly this is to play audio (downloaded or not) and handle all the stuff like icons etc
                    val playAudio = { uri: Uri ->
                        audioPlayerManager.playAudio(uri, {
                            binding.playButton.setImageResource(R.drawable.ic_play_arrow_24)
                        }, binding.seekBar, chatItem.message)

                        binding.playButton.setImageResource(R.drawable.ic_pause_24)

                        // Update the previously playing item's UI (for example to change icon pause/play and reset seekbar)
                        if (previousPlayingPosition != -1 && previousPlayingPosition != adapterPosition) {
                            notifyItemChanged(previousPlayingPosition)
                        }
                        currentPlayingPosition = adapterPosition
                        currentPlayingSeekBar = binding.seekBar
                    }

                    // this is bit complex but hey - it is what it is
                    // we're checking if:
                    // 1. audio is playing
                    // 2. if we DON"T download files from remote URL - if the file current being played is the same file as in chat item
                    // 3. if we DO download files from remote URL - if the file current being played is the same file as downloaded file from chat item
                    // if any of this is true - it means that we want to pause currently playing audio
                    if (audioPlayerManager.isPlaying()
                        && ((!ConfigurationManager.getDownloadAudioFilesBeforePlaying() && audioPlayerManager.currentUri == chatItem.fileNames.firstOrNull())
                                || (ConfigurationManager.getDownloadAudioFilesBeforePlaying() && audioPlayerManager.currentUri == Uri.fromFile(utilityTools.getDownloadedFileUri(chatItem.fileNames.firstOrNull().toString()))))
                    ) {
                        audioPlayerManager.pauseAudio()
                        binding.playButton.setImageResource(R.drawable.ic_play_arrow_24)
                    } else {
                        // here we check if we want to download audio files - and if the file is remote URL
                        // then we try to download file (if it exists already we won't)
                        if (ConfigurationManager.getDownloadAudioFilesBeforePlaying() && fileToPlay.toString().startsWith("http")) {
                            mainHandler.showProgressBar("Downloading audio")
                            utilityTools.downloadFile(fileToPlay.toString()) { file ->
                                mainHandler.executeOnUIThread {
                                    mainHandler.hideProgressBar("Downloading audio")
                                    if (file != null) {
                                        fileToPlay = Uri.fromFile(file)
                                        playAudio(fileToPlay!!)
                                    } else {
                                        mainHandler.createToastMessage("Error downloading audio")
                                    }
                                }
                            }
                        } else {
                            fileToPlay?.let {
                                playAudio(it)
                            }
                        }
                    }
                }

                // below we handle icons and seekbar - making sure that we do it for proper chat item (as we have multiple chat items with individual audio files)
                // Update play button icon based on current playing item
                if (adapterPosition == currentPlayingPosition && audioPlayerManager.isPlaying()) {
                    binding.playButton.setImageResource(R.drawable.ic_pause_24)
                } else {
                    binding.playButton.setImageResource(R.drawable.ic_play_arrow_24)
                }
                // Update seek bar if this is the current playing item
                if (adapterPosition == currentPlayingPosition) {
                    currentPlayingSeekBar = binding.seekBar
                    audioPlayerManager.setSeekBar(binding.seekBar)
                } else {
                    binding.seekBar.progress = 0
                }

                //val audioPlayerManager = AudioPlayerManager(binding.root.context, binding)
                /*if (!audioPlayerManager.isPlaying()) {
                    audioPlayerManager.setupMediaPlayer(
                        chatItem.fileNames[0],
                        chatItem.isTTS,
                        chatItem.message
                    )
                }*/

                // set transcribe button - but only for uploaded files (non tts)
                // and also there are cases where we want to disable it (via showTranscribeButton) - for example after recording (when auto transcribe is executed)
                if (chatItem.isTTS || !chatItem.showTranscribeButton) {
                    binding.transcribeButton.visibility = View.GONE
                } else {
                    binding.transcribeButton.visibility = View.VISIBLE
                }

                binding.transcribeButton.setOnClickListener {
                    mainHandler.showProgressBar("Transcription")
                    val audioFilePath = chatItem.fileNames[0].path // Ensure the correct path is obtained

                    utilityTools.uploadFileToServer(
                        audioFilePath,
                        apiUrl,
                        "chat_audio2text",
                        "speech",
                        "chat",
                        onResponseReceived = { response ->
                            mainHandler.executeOnUIThread {
                                mainHandler.handleTextMessage(response)
                                mainHandler.hideProgressBar("Transcription")
                            }
                        },
                        onError = { error ->
                            mainHandler.executeOnUIThread {
                                mainHandler.hideProgressBar("Transcription")
                                mainHandler.createToastMessage("Error: ${error.message}")
                            }
                        }
                    )
                }

            } else {
                binding.audioPlayer.visibility = View.GONE
                binding.transcribeButton.visibility = View.GONE
            }

            // reset before setting new values (potentially in artgen)
            binding.messageTextView.visibility = View.VISIBLE

            // if its message (or whole chat) for artgen - we want to allow user to create images
            if (chatItem.aiCharacterName == "tools_artgen" && !chatItem.isUserMessage) {
                binding.imageGenerationView.visibility = View.VISIBLE
                if (!ConfigurationManager.getImageArtgenShowPrompt())
                    binding.messageTextView.visibility = View.GONE

                if (ConfigurationManager.getImageAutoGenerateImage() && chatItem.imageLocations.contains("image_placeholder_url")) {
                    chatItem.imageLocations = emptyList()
                    binding.generateImageButton.visibility = View.VISIBLE

                    // that's bit strange - but when streaming is disabled - we have to wait for the view to be ready to perform click
                    if (!ConfigurationManager.getIsStreamingEnabled()) {
                        binding.root.post {
                            binding.generateImageButton.performClick()
                        }
                    } else {
                        binding.generateImageButton.performClick() // Trigger the image generation
                    }
                }

                binding.generateImageButton.setOnClickListener {
                    mainHandler.showProgressBar("Image")
                    val prompt = chatItem.message

                    utilityTools.sendImageRequest(
                        prompt,
                        apiUrl,
                        { result ->
                            chatItem.imageLocations += result
                            notifyItemChanged(adapterPosition)
                            mainHandler.hideProgressBar("Image")

                            chatHelperHandler?.scrollToEnd()
                            CoroutineScope(Dispatchers.Main).launch {
                                // update DB - in order to preserve image link (if we restore session later)
                                DatabaseHelper.sendDBRequest(
                                    "db_update_session",
                                    mapOf(
                                        "session_id" to (chatHelperHandler?.getCurrentDBSessionID() ?: ""),
                                        "chat_history" to chatItems.map { it.toSerializableMap() }
                                    )
                                )
                            }

                        },
                        { error ->
                            mainHandler.executeOnUIThread {
                                mainHandler.hideProgressBar("Image")
                                println("Error image: ${error.message}")
                                mainHandler.createToastMessage("Error generating image ${error.message}")

                            }
                        }
                    )
                }
            } else {
                binding.imageGenerationView.visibility = View.GONE
            }

            // GPS STUFF
            if (chatItem.isGPSLocationMessage) {
                binding.gpsEnabledView.visibility = View.VISIBLE
                binding.checkGPSLocation.setOnClickListener {
                    val gpsLocation = chatItem.message
                    if (gpsLocation.startsWith("GPS location: ")) {
                        val latLong = gpsLocation.removePrefix("GPS location: ").split(",")
                        val latitude = latLong[0].toDouble()
                        val longitude = latLong[1].toDouble()
                        // Show the location on the map
                        chatHelperHandler?.shareGPSLocation(latitude, longitude)
                    }
                }
            } else {
                binding.gpsEnabledView.visibility = View.GONE
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
                    popupMenu.menu.add(0, R.id.newSessionFromHere, 0, "New session from here")
                    popupMenu.menu.add(0, R.id.copy, 1, "Copy")
                }
            } else {
                if (isLastTwoMessages) {
                    popupMenu.inflate(R.menu.ai_message_menu)
                } else {
                    popupMenu.menu.add(0, R.id.newSessionFromHere, 0, "New session from here")
                    popupMenu.menu.add(0, R.id.tts, 1, "Speak")
                    popupMenu.menu.add(0, R.id.copy, 2, "Copy")
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
                        if (position > 0) {
                            val previousChatItem = chatItems[position - 1]
                            if (previousChatItem.isUserMessage) {
                                val attachedImageLocations = previousChatItem.imageLocations
                                val attachedFiles = previousChatItem.fileNames

                                // Set the editing message position
                                chatHelperHandler?.setEditingMessagePosition(position - 1)

                                // Trigger the regeneration
                                mainHandler.handleTextMessage(
                                    previousChatItem.message,
                                    attachedImageLocations,
                                    attachedFiles
                                )

                                // Reset the editing message position
                                chatHelperHandler?.setEditingMessagePosition(null)
                            }
                        }
                        true
                    }
                    R.id.newSessionFromHere -> {
                        // Handle new session from here action
                        chatHelperHandler?.createNewSessionFromHere(position)
                        true
                    }
                    R.id.tts -> {
                        // Handle tts
                        sendTTSRequest(chatItem.message, position)
                        true
                    }
                    R.id.copy -> {
                        // Copy text to clipboard
                        val clipboard = ContextCompat.getSystemService(context, ClipboardManager::class.java)
                        val clip = ClipData.newPlainText("Copied Message", chatItem.message)
                        clipboard?.setPrimaryClip(clip)
                        mainHandler.createToastMessage("Copied to clipboard")
                        true
                    }
                    else -> false
                }
            }
            popupMenu.show()
        }

    }

    fun sendTTSRequest(message: String, position: Int) {
        println("Sending TTS request - EXECUTED")
        val chatItem = chatItems[position]

        // Check if the chatItem already has a TTS file
        if (chatItem.fileNames.isNotEmpty()) {
            return
        }
        mainHandler.showProgressBar("TTS")

        val apiUrl = ConfigurationManager.getAppModeApiUrl()
        val action = if (ConfigurationManager.getTTSStreaming()) "tts_stream" else "tts_no_stream"

        // Split the message into chunks of 4096 characters or less (this is the limit of OpenAI)
        // TODO one day - handle chunks maybe - because here i just take first (because its super rare to be longer)
        val message4API = message.chunked(4096)[0]
        println("Message for TTS: $message4API")

        utilityTools.sendTTSRequest(
            message4API,
            apiUrl,
            action,
            { result -> handleTTSCompletedResponse(result, position, action) },
            { error ->
                mainHandler.executeOnUIThread {
                    mainHandler.hideProgressBar("TTS")
                    mainHandler.createToastMessage("Error generating TTS: ${error.message}")
                }
            }
        )
    }

    // upon receiving TTS response - we have to update chat item with audio file
    private fun handleTTSCompletedResponse(result: String, position: Int, action: String) {
        mainHandler.executeOnUIThread {
            println("handleTTSCompletedResponse result: $result")
            val chatItem = chatItems[position]
            chatItem.fileNames = listOf(Uri.parse(result))
            chatItem.isTTS = true
            notifyItemChanged(position)
            mainHandler.hideProgressBar("TTS")

            // if its stream - we should upload to S3, if its not stream - its already uploaded via backend
            if (action == "tts_stream") {
                // this already consists of chat session update
                utilityTools.uploadFileToServer(
                    result,
                    apiUrl,
                    "api/aws",
                    "provider.s3",
                    "s3_upload",
                    onResponseReceived = { response ->
                        mainHandler.executeOnUIThread {
                            chatItem.fileNames = listOf(Uri.parse(response))

                            CoroutineScope(Dispatchers.Main).launch {
                                // update DB - in order to preserve TTS link (if we restore session later)
                                DatabaseHelper.sendDBRequest(
                                    "db_update_session",
                                    mapOf(
                                        "session_id" to (chatHelperHandler?.getCurrentDBSessionID() ?: ""),
                                        "chat_history" to chatItems.map { it.toSerializableMap() }
                                    )
                                )
                            }
                        }
                    },
                    onError = { error ->
                        mainHandler.executeOnUIThread {
                            println("Error handleTTSCompletedResponse: ${error.message}")
                            mainHandler.createToastMessage("Error: ${error.message}")
                        }
                    }
                )
            } else {
                // here - we still need to update chat session with new audio file
                CoroutineScope(Dispatchers.Main).launch {
                    // update DB - in order to preserve TTS link (if we restore session later)
                    DatabaseHelper.sendDBRequest(
                        "db_update_session",
                        mapOf(
                            "session_id" to (chatHelperHandler?.getCurrentDBSessionID() ?: ""),
                            "chat_history" to chatItems.map { it.toSerializableMap() }
                        )
                    )
                }
            }
        }
    }

    // create new view for image - when clicked on chat item images
    private fun showFullScreenImages(imageUrls: List<String>, initialPosition: Int) {
        val dialog = Dialog(mainHandler.context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val binding: DialogFullscreenImagesBinding = DialogFullscreenImagesBinding.inflate(LayoutInflater.from(mainHandler.context))
        dialog.setContentView(binding.root)

        val adapter = FullScreenImageAdapter(mainHandler.context, imageUrls)
        binding.viewPager.adapter = adapter
        binding.viewPager.setCurrentItem(initialPosition, false)

        binding.closeButton.setOnClickListener { dialog.dismiss() }

        dialog.show()
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

// ChatAdapter.kt

package biz.atamai.myai

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
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
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import biz.atamai.myai.databinding.ChatItemBinding
import biz.atamai.myai.databinding.DialogFullscreenImagesBinding
import biz.atamai.myai.databinding.DialogSelectChatTextBinding
import com.squareup.picasso.Picasso
import io.noties.markwon.Markwon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChatAdapter(
    private val chatItems: MutableList<ChatItem>,
    private val apiUrl: String,
    private val mainHandler: MainHandler,
    private val audioPlayerManager: AudioPlayerManager,
    private val onEditMessage: (position: Int, message: String) -> Unit,
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>(), ChatAdapterHandler {
    //private val audioPlayerManagers: MutableList<AudioPlayerManager> = mutableListOf()
    private lateinit var markwon: Markwon
    private var utilityTools: UtilityTools
    private var chatHelperHandler: ChatHelperHandler? = null

    // important! leave it here - when auto play is executed there was crash because chat notify was executed too soon
    private val handler = Handler(Looper.getMainLooper())

    // to track which chat item is playing (to handle proper icon - play/pause changes)
    private var currentPlayingPosition: Int = -1
    // and proper seekbar to be updated
    private var currentPlayingSeekBar: SeekBar? = null
    // and to track previous playing item (to reset it when new item is played)
    private var previousPlayingPosition: Int = -1
    // this can be changed via options in top menu
    var fontSize: Int = mainHandler.getConfigurationManager().getTextSizeInUI()

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
            // Set the font size dynamically
            binding.messageTextView.textSize = fontSize.toFloat()
            // Render message using Markwon
            markwon.setMarkdown(binding.messageTextView, chatItem.message)

            val avatarResId = if (chatItem.isUserMessage) {
                R.drawable.user_avatar_placeholder
            } else {
                chatItem.aiCharacterName?.let { characterName ->
                    mainHandler.getMainCharacterManager().getCharacterByNameForAPI(characterName)?.imageResId
                } ?: R.drawable.ai_avatar_placeholder
            }

            // Add click listener to the avatar image
            binding.avatarImageView.setOnClickListener {
                chatItem.aiCharacterName?.let { characterName ->
                    val character = mainHandler.getMainCharacterManager().getCharacterByNameForAPI(characterName)
                    if (character != null) {

                        showFullScreenImages(listOf(character.imageResId.toString()), 0, character.name, character.welcomeMsg)
                    }
                }
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
            if (chatItem.fileNames.isNotEmpty()) {
                // lets see if its pdf or audio
                val pdfFiles = chatItem.fileNames.filter { it.toString().endsWith(".pdf") }
                if (pdfFiles.isNotEmpty()) {
                    binding.audioPlayer.visibility = View.GONE
                    binding.transcribeButton.visibility = View.GONE

                    binding.pdfPlaceholderContainer.visibility = View.VISIBLE
                    binding.pdfPlaceholderContainer.removeAllViews()

                    pdfFiles.forEach { url ->
                        // sometimes file names are really long - lets shorten them
                        val fileName = url.toString().split("/").last().take(15)

                        val displayFileName = "PDF:\n\n$fileName.."

                        val textView = TextView(mainHandler.context).apply {
                            text = displayFileName
                            setTextColor(Color.WHITE)
                            setBackgroundColor(Color.parseColor("#555555"))
                            setPadding(4, 4, 4, 4)
                            textSize = 10f
                            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                                width = 55.dpToPx(context)
                                height = 60.dpToPx(context)
                            }
                        }

                        binding.pdfPlaceholderContainer.addView(textView)
                    }
                } else {
                    binding.pdfPlaceholderContainer.visibility = View.GONE
                    binding.audioPlayer.visibility = View.VISIBLE
                    // here we assume this is audio file - as we did not implement anything else
                    // if its audio - there will be only single filename in the list
                    // and we can process it - either play audio or transcribe

                    binding.playButton.setOnClickListener {
                        previousPlayingPosition = currentPlayingPosition
                        var fileToPlay: Uri? = chatItem.fileNames.firstOrNull()

                        // it was in one long line with conditions - but it got too complex
                        // so i split for my readability - idc if its best practices
                        // we're checking if:
                        // audio is playing, is file remote, should we download it, which item is playing
                        val shouldDownloadFile =
                            mainHandler.getConfigurationManager().getDownloadAudioFilesBeforePlaying()
                        val isRemoteFile = fileToPlay.toString().startsWith("http")
                        val isPlaying = audioPlayerManager.isPlaying()
                        val currentFile = audioPlayerManager.currentUri

                        if (!isPlaying) {
                            if (shouldDownloadFile && isRemoteFile) {
                                downloadAndOptionallyPlayAudio(fileToPlay!!, chatItem.message, true)
                            } else {
                                fileToPlay?.let {
                                    playAudio(it, chatItem.message)
                                }
                            }
                        } else { // if audio player is playing
                            if (shouldDownloadFile) {
                                if (!isRemoteFile && fileToPlay == currentFile) {
                                    pauseAudio()
                                } else if (isRemoteFile && currentFile == Uri.fromFile(
                                        utilityTools.getDownloadedFileUri(
                                            fileToPlay.toString()
                                        )
                                    )
                                ) {
                                    pauseAudio()
                                } else if (isRemoteFile && currentFile != Uri.fromFile(
                                        utilityTools.getDownloadedFileUri(
                                            fileToPlay.toString()
                                        )
                                    )
                                ) {
                                    downloadAndOptionallyPlayAudio(fileToPlay!!, chatItem.message, true)
                                } else {
                                    playAudio(fileToPlay!!, chatItem.message)
                                }
                            } else {
                                if (fileToPlay == currentFile) {
                                    pauseAudio()
                                } else {
                                    playAudio(fileToPlay!!, chatItem.message)
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

                    // if file is TTS generated and auto play is ON
                    if (chatItem.isTTS && chatItem.isAutoPlay) {
                        chatItem.isAutoPlay = false // Reset the flag

                        chatItem.fileNames.firstOrNull()?.let {
                            // if its stream file or not - we might need to download it
                            if (it.toString().startsWith("http") && mainHandler.getConfigurationManager().getDownloadAudioFilesBeforePlaying()) {
                                downloadAndOptionallyPlayAudio(it, chatItem.message, true)
                            } else {
                                playAudio(it, chatItem.message)
                            }
                        }
                    }

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
                        CoroutineScope(Dispatchers.IO).launch {
                            utilityTools.uploadFileToServer(
                                audioFilePath,
                                apiUrl,
                                "chat_audio2text",
                                "speech",
                                "chat",
                                onResponseReceived = { response ->
                                    CoroutineScope(Dispatchers.Main).launch {
                                        mainHandler.handleTextMessage(response)
                                        mainHandler.hideProgressBar("Transcription")
                                    }
                                },
                                onError = { error ->
                                    CoroutineScope(Dispatchers.Main).launch {
                                        mainHandler.hideProgressBar("Transcription")
                                        mainHandler.createToastMessage("Error: ${error.message}")
                                    }
                                }
                            )
                        }
                    }
                } // end if else ift is pdf or audio
            } else {
                binding.audioPlayer.visibility = View.GONE
                binding.transcribeButton.visibility = View.GONE
            }

            // reset before setting new values (potentially in artgen)
            binding.messageTextView.visibility = View.VISIBLE

            // if its message (or whole chat) for artgen - we want to allow user to create images
            if (chatItem.aiCharacterName == "tools_artgen" && !chatItem.isUserMessage) {
                binding.imageGenerationView.visibility = View.VISIBLE
                if (!mainHandler.getConfigurationManager().getImageArtgenShowPrompt())
                    binding.messageTextView.visibility = View.GONE

                if (mainHandler.getConfigurationManager().getImageAutoGenerateImage() && chatItem.imageLocations.contains("image_placeholder_url")) {
                    chatItem.imageLocations = emptyList()
                    binding.generateImageButton.visibility = View.VISIBLE

                    // that's bit strange - but when streaming is disabled - we have to wait for the view to be ready to perform click
                    if (!mainHandler.getConfigurationManager().getIsStreamingEnabled()) {
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

                    val currentSessionID = chatHelperHandler?.getCurrentDBSessionID() ?: ""

                    CoroutineScope(Dispatchers.IO).launch {
                        utilityTools.sendImageRequest(
                            prompt,
                            apiUrl,
                            { result ->
                                CoroutineScope(Dispatchers.Main).launch {
                                    chatItem.imageLocations += result
                                    notifyItemChanged(adapterPosition)
                                    mainHandler.hideProgressBar("Image")

                                    chatHelperHandler?.scrollToEnd()
                                }
                                CoroutineScope(Dispatchers.IO).launch {
                                    // update DB - in order to preserve image link (if we restore session later)
                                    mainHandler.getDatabaseHelper().sendDBRequest(
                                        "db_update_session",
                                        mapOf(
                                            "session_id" to currentSessionID,
                                            "chat_history" to chatItems.map { it.toSerializableMap() }
                                        )
                                    )
                                }

                            },
                            { error ->
                                CoroutineScope(Dispatchers.Main).launch {
                                    mainHandler.hideProgressBar("Image")
                                    println("Error image: ${error.message}")
                                    mainHandler.createToastMessage("Error generating image ${error.message}")

                                }
                            }
                        )
                    }
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
                    popupMenu.menu.add(0, R.id.copy, 0, "Copy")
                    popupMenu.menu.add(0, R.id.selectText, 1, "Select Text")
                    popupMenu.menu.add(0, R.id.remove, 2, "Remove")
                    popupMenu.menu.add(0, R.id.newSessionFromHere, 3, "New session from here")
                    popupMenu.menu.add(0, R.id.forceDBSync, 4, "Force DB sync")
                }
            } else {
                if (isLastTwoMessages) {
                    popupMenu.inflate(R.menu.ai_message_menu)
                } else {
                    popupMenu.menu.add(0, R.id.copy, 0, "Copy")
                    popupMenu.menu.add(0, R.id.selectText, 1, "Select Text")
                    popupMenu.menu.add(0, R.id.tts, 2, "Speak")
                    popupMenu.menu.add(0, R.id.newSessionFromHere, 3, "New session from here")
                    popupMenu.menu.add(0, R.id.forceDBSync, 4, "Force DB sync")
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
                    R.id.forceDBSync -> {
                        // Force DB sync - this might be useful in few cases (for example when poor internet and functions - like transcription - fail)
                        // when new session from here is chosen and something doesn't work
                        CoroutineScope(Dispatchers.IO).launch {
                            val dbMethodToExecute = if (chatHelperHandler?.getCurrentDBSessionID() == "") {
                                "db_new_session"
                            } else {
                                "db_update_session"
                            }

                            // if it's force it means that something was not ok
                            // let's make sure to avoid problems in future - and here we will remove messageId from each chat item
                            // because those numbers for sure are wrong in this case (and if we for example edit message in future it will cause problems)
                            val modifiedChatItems = chatItems.map { chatItem ->
                                if (chatItem.messageId != null) {
                                    chatItem.copy(messageId = null)
                                } else {
                                    chatItem
                                }
                            }

                            mainHandler.getDatabaseHelper().sendDBRequest(
                                dbMethodToExecute,
                                mapOf(
                                    "session_id" to (chatHelperHandler?.getCurrentDBSessionID() ?: ""),
                                    "chat_history" to modifiedChatItems.map { it.toSerializableMap() }
                                )
                            )
                        }
                        true
                    }
                    R.id.selectText -> {
                        // Select text
                        showSelectTextDialog(chatItem.message)
                        true
                    }
                    R.id.remove -> {
                        // Remove the chat item
                        chatItems.removeAt(position)
                        notifyItemRemoved(position)
                        // if next message is AI message - we should remove it too
                        if (position < chatItems.size && !chatItems[position].isUserMessage) {
                            chatItems.removeAt(position)
                            notifyItemRemoved(position)
                        }

                        // if session is empty
                        val dbMethodToExecute = if (chatItems.isEmpty()) {
                            "db_remove_session"
                        } else {
                            "db_update_session"
                        }

                        CoroutineScope(Dispatchers.IO).launch {
                            // update DB - in order to preserve TTS link (if we restore session later)
                            mainHandler.getDatabaseHelper().sendDBRequest(
                                dbMethodToExecute,
                                mapOf(
                                    "session_id" to (chatHelperHandler?.getCurrentDBSessionID() ?: ""),
                                    "chat_history" to chatItems.map { it.toSerializableMap() }
                                )
                            )
                        }
                        true
                    }
                    else -> false
                }
            }
            popupMenu.show()
        }

        // show dialog to be able to select specific text
        private fun showSelectTextDialog(text: String) {
            val dialog = Dialog(mainHandler.context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            val binding: DialogSelectChatTextBinding = DialogSelectChatTextBinding.inflate(LayoutInflater.from(mainHandler.context))
            dialog.setContentView(binding.root)

            markwon.setMarkdown(binding.selectableTextView, text)

            binding.btnBack.setOnClickListener { dialog.dismiss() }

            dialog.show()
        }

        // helper internal function - as it will be used in few different places
        // but mainly this is to play audio (downloaded or not) and handle all the stuff like icons etc
        private fun playAudio(uri: Uri, message: String) {
            audioPlayerManager.playAudio(uri, binding.seekBar, message,) {
                binding.playButton.setImageResource(R.drawable.ic_play_arrow_24)
            }

            binding.playButton.setImageResource(R.drawable.ic_pause_24)

            // Update the previously playing item's UI (for example to change icon pause/play and reset seekbar)
            if (previousPlayingPosition != -1 && previousPlayingPosition != adapterPosition) {
                handler.post {
                    notifyItemChanged(previousPlayingPosition)
                }
            }
            currentPlayingPosition = adapterPosition
            currentPlayingSeekBar = binding.seekBar
        }

        // Function to download file and then (optionally) play it
        private fun downloadAndOptionallyPlayAudio(fileUri: Uri, message: String, shouldPlayFile: Boolean) {
            mainHandler.showProgressBar("Downloading audio")
            CoroutineScope(Dispatchers.IO).launch {
                utilityTools.downloadFile(fileUri.toString()) { file ->
                    CoroutineScope(Dispatchers.Main).launch {
                        mainHandler.hideProgressBar("Downloading audio")
                        if (file != null && shouldPlayFile) {
                            playAudio(Uri.fromFile(file), message)
                        } else {
                            mainHandler.createToastMessage("Error downloading audio")
                        }
                    }
                }
            }
        }

        // Function to pause audio
        private fun pauseAudio() {
            audioPlayerManager.pauseAudio()
            binding.playButton.setImageResource(R.drawable.ic_play_arrow_24)
        }
    }

    fun sendTTSRequest(message: String, position: Int) {
        val chatItem = chatItems[position]

        // Check if the chatItem already has a TTS file
        if (chatItem.fileNames.isNotEmpty()) {
            return
        }
        mainHandler.showProgressBar("TTS")

        val apiUrl = mainHandler.getConfigurationManager().getAppModeApiUrl()
        val action = if (mainHandler.getConfigurationManager().getTTSStreaming()) "tts_stream" else "tts_no_stream"

        // Split the message into chunks of 4096 characters or less (this is the limit of OpenAI)
        // TODO one day - handle chunks maybe - because here i just take first (because its super rare to be longer)
        val message4API = message.chunked(4096)[0]

        val currentSessionID = chatHelperHandler?.getCurrentDBSessionID() ?: ""

        CoroutineScope(Dispatchers.IO).launch {
            utilityTools.sendTTSRequest(
                message4API,
                apiUrl,
                action,
                { result -> handleTTSCompletedResponse(result, position, action, currentSessionID) },
                { error ->
                    CoroutineScope(Dispatchers.Main).launch {
                        mainHandler.hideProgressBar("TTS")
                        mainHandler.createToastMessage("Error generating TTS: ${error.message}")
                    }
                },
            )
        }
    }

    // upon receiving TTS response - we have to update chat item with audio file
    private fun handleTTSCompletedResponse(result: String, position: Int, action: String, currentSessionID: String) {
        CoroutineScope(Dispatchers.Main).launch {
            val chatItem = chatItems[position]
            chatItem.fileNames = listOf(Uri.parse(result))
            chatItem.isTTS = true
            chatItem.isAutoPlay = true
            // if its stream mode - we will notify item changed after uploading to S3 (because other way there is problem with audio player pausing/unpausing)
            if (action != "tts_stream") {
                notifyItemChanged(position)
            }
            mainHandler.hideProgressBar("TTS")

            // if its stream - we should upload to S3, if its not stream - its already uploaded via backend
            if (action == "tts_stream") {
                CoroutineScope(Dispatchers.IO).launch {
                    // this already consists of chat session update
                    utilityTools.uploadFileToServer(
                        result,
                        apiUrl,
                        "api/aws",
                        "provider.s3",
                        "s3_upload",
                        onResponseReceived = { response ->
                            CoroutineScope(Dispatchers.Main).launch {
                                chatItem.fileNames = listOf(Uri.parse(response))
                                notifyItemChanged(position)
                                CoroutineScope(Dispatchers.IO).launch {
                                    // update DB - in order to preserve TTS link (if we restore session later)
                                    mainHandler.getDatabaseHelper().sendDBRequest(
                                        "db_update_session",
                                        mapOf(
                                            "session_id" to currentSessionID,
                                            "chat_history" to chatItems.map { it.toSerializableMap() }
                                        )
                                    )
                                }
                            }
                        },
                        onError = { error ->
                            CoroutineScope(Dispatchers.Main).launch {
                                println("Error handleTTSCompletedResponse: ${error.message}")
                                mainHandler.createToastMessage("Error: ${error.message}")
                            }
                        }
                    )
                }
            } else {
                // here - we still need to update chat session with new audio file
                CoroutineScope(Dispatchers.IO).launch {
                    // update DB - in order to preserve TTS link (if we restore session later)
                    mainHandler.getDatabaseHelper().sendDBRequest(
                        "db_update_session",
                        mapOf(
                            "session_id" to currentSessionID,
                            "chat_history" to chatItems.map { it.toSerializableMap() }
                        )
                    )
                }
            }
        }
    }

    // create new view for image - when clicked on chat item images
    private fun showFullScreenImages(imageUrls: List<String>, initialPosition: Int, characterName: String = "", characterDescription: String = "") {
        val dialog = Dialog(mainHandler.context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val binding: DialogFullscreenImagesBinding = DialogFullscreenImagesBinding.inflate(LayoutInflater.from(mainHandler.context))
        dialog.setContentView(binding.root)

        val adapter = FullScreenImageAdapter(mainHandler.context, imageUrls, characterName, characterDescription)
        binding.viewPager.adapter = adapter
        binding.viewPager.setCurrentItem(initialPosition, false)

        binding.closeButton.setOnClickListener { dialog.dismiss() }

        binding.characterDescription.text = characterDescription
        binding.characterName.text = characterName

        // Make the TextViews visible if they have content
        if (characterName.isNotEmpty()) {
            binding.characterName.visibility = View.VISIBLE
        }
        if (characterDescription.isNotEmpty()) {
            binding.characterDescription.visibility = View.VISIBLE
        }
        dialog.show()
    }

    // Show character full screen with name and description
    /*private fun showCharacterFullScreen(imageResId: Int, characterName: String, characterDescription: String) {
        val dialog = Dialog(mainHandler.context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val binding: DialogFullscreenImagesBinding = DialogFullscreenImagesBinding.inflate(LayoutInflater.from(mainHandler.context))
        dialog.setContentView(binding.root)

        // Set the image, name, and description
        Picasso.get().load(imageResId).into(binding.fullscreenImage)
        binding.characterName.text = characterName
        binding.characterDescription.text = characterDescription

        binding.closeButton.setOnClickListener { dialog.dismiss() }

        dialog.show()
    } */

    // used in chatHelper
    fun resetChatAdapter() {
        currentPlayingPosition = -1
        currentPlayingSeekBar = null
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

    // i set interface to monitor text size changes - (when changed in options via top menu)
    override fun onTextSizeChanged(newSize: Int) {
        fontSize = newSize
        notifyItemRangeChanged(0, chatItems.size)
    }

    override fun onCharacterLongPress(character: CharacterManager.Character) {
        showFullScreenImages(listOf(character.imageResId.toString()), 0, character.name, character.welcomeMsg)
    }

    override fun getItemCount(): Int = chatItems.size
}

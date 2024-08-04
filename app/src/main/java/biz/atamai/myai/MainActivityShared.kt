package biz.atamai.myai

// MainActivityShared.kt

import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivityShared : ViewModel() {
    val message = MutableLiveData<String>()
    val chatItems = MutableLiveData<MutableList<ChatItem>>(mutableListOf())
    val editingMessagePosition = MutableLiveData<Int?>()
    val currentDBSessionID = MutableLiveData<String>("")

    private val ERROR_MESSAGE_FOR_TEXT_GEN = "Error in Text Generator. Try again!"

    private fun getCurrentDate(): String {
        val current = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        return current.format(formatter)
    }

    fun addMessageToChat(message: String, attachedImageLocations: List<String>, attachedFiles: List<Uri>, gpsLocationMessage: Boolean): ChatItem {
        val messageDate = getCurrentDate()
        val chatItem = ChatItem(
            message = message,
            isUserMessage = true,
            imageLocations = attachedImageLocations,
            aiCharacterName = "",
            fileNames = attachedFiles,
            isGPSLocationMessage = gpsLocationMessage,
            dateGenerate = messageDate
        )
        val currentChatItems = chatItems.value ?: mutableListOf()
        currentChatItems.add(chatItem)
        chatItems.value = currentChatItems
        return chatItem
    }

    private fun startStreaming(
        currentSessionId: String,
        responseItemPosition: Int? = null,
        characterManager: CharacterManager,
        chatHelper: ChatHelper,
        chatAdapter: ChatAdapter,
        databaseHelper: DatabaseHelper,
        configurationManager: ConfigurationManager,
        onShowProgressBar: (String) -> Unit,
        onHideProgressBar: (String) -> Unit,
        onToastMessage: (String) -> Unit,
        onScrollToEnd: () -> Unit,
        onUpdateChatItem: (Int, String) -> Unit,
        onNotifyItemInserted: (Int) -> Unit,
        onNotifyItemChanged: (Int) -> Unit
    ) {
        onShowProgressBar("Text generation")

        // collect chat history (needed to send it API to get whole context of chat)
        // (excluding the latest message - as this will be sent via userPrompt), including images if any
        // or excluding 1 or 2 last messages - if its edited user message
        var dropHowMany = 1
        if (responseItemPosition != null) {
            // if it is edited message - we have to drop 2 last messages (user and AI response)
            // but only if it is not the last message in chat
            if (responseItemPosition == chatItems.value!!.size - 1) {
                dropHowMany = 1
            } else {
                dropHowMany = 2
            }
        }
        val chatHistory = chatItems.value!!.dropLast(dropHowMany).map {
            if (it.isUserMessage) {
                val content = mutableListOf<Map<String, Any>>()
                content.add(mapOf("type" to "text", "text" to it.message))
                it.imageLocations.forEach { imageUrl ->
                    content.add(mapOf("type" to "image_url", "image_url" to mapOf("url" to imageUrl)))
                }
                it.fileNames.forEach { fileUri ->
                    if (fileUri.toString().endsWith(".pdf")) {
                        content.add(mapOf("type" to "file_url", "file_url" to mapOf("url" to fileUri.toString())))
                    }
                }
                mapOf("role" to "user", "content" to content)
            } else {
                mapOf("role" to "assistant", "content" to it.message)
            }
        }

        // user prompt preparation
        // checking responseItemPosition - if it's null - it's new message - otherwise it's edited message
        // first lets get user message - either last one or the one that was edited
        val userActiveChatItem = if (responseItemPosition == null) {
            // get the last user message and its images (if exists)
            chatItems.value!!.last()
        } else {
            // if edited message its last -1 (because last is  AI response)
            chatItems.value!![responseItemPosition]
        }
        val userPrompt = mutableListOf<Map<String, Any>>()
        userPrompt.add(mapOf("type" to "text", "text" to userActiveChatItem.message))
        userActiveChatItem.imageLocations.forEach { imageUrl ->
            userPrompt.add(mapOf("type" to "image_url", "image_url" to mapOf("url" to imageUrl)))
        }
        userActiveChatItem.fileNames.forEach { fileUri ->
            if (fileUri.toString().endsWith(".pdf")) {
                userPrompt.add(mapOf("type" to "file_url", "file_url" to mapOf("url" to fileUri.toString())))
            }
        }

        val apiDataModel = APIDataModel(
            category = "text",
            action = "chat",
            userInput = mapOf(
                "prompt" to userPrompt,
                "chat_history" to chatHistory
            ),
            userSettings = configurationManager.getSettingsDict(),
            customerId = 1,
        )

        val streamUrl = configurationManager.getAppModeApiUrl() + "chat"

        val character = characterManager.characters.find { it.nameForAPI == configurationManager.getTextAICharacter() }
        val messageDate = chatHelper.getCurrentDate()

        // needed for chat items placement - if its null - chat hasn't been started, if it has value - this is latest msg
        var currentResponseItemPosition: Int? = null
        if (responseItemPosition == null) {
            val responseItem = ChatItem(message = "", isUserMessage = false, aiCharacterName = character?.nameForAPI, apiAIModelName = configurationManager.getTextModelName(), dateGenerate = messageDate)
            chatItems.value!!.add(responseItem)
            currentResponseItemPosition = chatItems.value!!.size - 1
            onNotifyItemInserted(currentResponseItemPosition!!)
        } else {
            currentResponseItemPosition = responseItemPosition + 1
            if (currentResponseItemPosition > chatItems.value!!.size - 1) {
                val responseItem = ChatItem(message = "", isUserMessage = false, aiCharacterName = character?.nameForAPI)
                chatItems.value!!.add(responseItem)
            }
            chatItems.value!![currentResponseItemPosition].apply {
                message = ""
                aiCharacterName = character?.nameForAPI
                apiAIModelName = configurationManager.getTextModelName()
                dateGenerate = messageDate
            }
            onNotifyItemChanged(currentResponseItemPosition)
        }

        onScrollToEnd()

        val handler = ResponseHandler(
            handlerType = HandlerType.Streaming(
                onChunkReceived = { chunk ->
                    CoroutineScope(Dispatchers.Main).launch {
                        if (currentSessionId == chatHelper.getCurrentDBSessionID()) {
                            currentResponseItemPosition?.let { position ->
                                onUpdateChatItem(position, chunk)
                                onNotifyItemChanged(position)
                                onScrollToEnd()
                            }
                        }
                    }
                },
                onStreamEnd = {
                    CoroutineScope(Dispatchers.Main).launch {
                        onHideProgressBar("Text generation")
                        if (currentSessionId == chatHelper.getCurrentDBSessionID()) {
                            if (configurationManager.getTTSAutoExecute()) {
                                // Assuming chatAdapter is accessible or through a callback
                                chatAdapter.sendTTSRequest(chatItems.value!![currentResponseItemPosition!!].message, currentResponseItemPosition!!)
                            }
                            val currentUserMessage = chatItems.value!![currentResponseItemPosition!! - 1]
                            val currentAIResponse = chatItems.value!![currentResponseItemPosition!!]
                            if (currentAIResponse.aiCharacterName == "tools_artgen" && configurationManager.getImageAutoGenerateImage() && currentAIResponse.imageLocations.isEmpty()) {
                                chatAdapter.triggerImageGeneration(currentResponseItemPosition!!)
                            }
                            if (responseItemPosition == null) {
                                viewModelScope.launch {
                                    databaseHelper.addNewOrEditDBMessage("db_new_message", currentSessionId, currentUserMessage, currentAIResponse)
                                }
                            } else {
                                viewModelScope.launch {
                                    databaseHelper.addNewOrEditDBMessage("db_edit_message", currentSessionId, currentUserMessage, currentAIResponse)
                                }
                            }
                        }
                    }
                }
            ),
            onError = { error ->
                onHideProgressBar("Text generation")
                onToastMessage("Error: $error")
            },
            authToken = configurationManager.getAuthTokenForBackend(),
        )

        handler.sendRequest(streamUrl, apiDataModel)

        if (originalAICharacter != null) {
            configurationManager.setTextAICharacter(originalAICharacter!!)
            originalAICharacter = null
        }
    }


    fun handleTextMessage(
        message: String,
        attachedImageLocations: List<String>,
        attachedFiles: List<Uri>,
        gpsLocationMessage: Boolean,
        characterManager: CharacterManager,
        chatHelper: ChatHelper,
        databaseHelper: DatabaseHelper,
        configurationManager: ConfigurationManager,
        onToastMessage: (String) -> Unit,
        onHideCharacterMainView: () -> Unit,
        onScrollToEnd: () -> Unit
    ) {
        if (message.isEmpty()) {
            return
        }

        val currentModel = configurationManager.getTextModelName()
        // if there is image attached and we use model that does not support images
        if (attachedImageLocations.isNotEmpty() && currentModel !in listOf("GPT-4o", "GPT-4o-mini", "GPT-4", "Claude-3.5")) {
            onToastMessage("Currently chosen model does not support images. Remove image or change the model")
            return
        }

        if (attachedFiles.isNotEmpty() && currentModel !in listOf("GPT-4o", "GPT-4o-mini", "GPT-4", "Claude-3.5")) {
            onToastMessage("In order to process attached files you need to change the model")
            return
        }

        // some characters have autoResponse set to false - if this is the case - we don't stream
        val character = characterManager.getCharacterByNameForAPI(configurationManager.getTextAICharacter())

        val currentSessionId = currentDBSessionID.value ?: ""
        // Add message to chat
        editingMessagePosition.value?.let { position ->
            chatHelper.editMessageInChat(position, message, attachedImageLocations, attachedFiles)

            // some characters have autoResponse set to false - if this is the case - we don't want to get response from AI (it's just data collection)
            if (character?.autoResponse == true) {
                startStreaming(currentSessionId, position)
            } else {
                val currentUserMessage = chatItems.value?.get(position)
                // if we don't stream - we still need to save user message to DB
                viewModelScope.launch {
                    databaseHelper.addNewOrEditDBMessage("db_edit_message", currentSessionId, currentUserMessage, null)
                }
            }
        } ?: run {
            val currentUserMessage = addMessageToChat(message, attachedImageLocations, attachedFiles, gpsLocationMessage)
            if (character?.autoResponse == true) {
                startStreaming(currentSessionId)
            } else {
                // if we don't stream - we still need to save to DB
                viewModelScope.launch {
                    databaseHelper.addNewOrEditDBMessage("db_new_message", currentSessionId, currentUserMessage, null)
                }
            }
        }
        chatHelper.resetInputArea()
        // edit position reset
        editingMessagePosition.value = null
        // hide characters view
        onHideCharacterMainView()
        onScrollToEnd()
    }


    /*fun sendMessage(newMessage: String) {
    // Update the message
    message.value = newMessage

    // Add message to chatItems
    val currentChatItems = chatItems.value ?: mutableListOf()
    currentChatItems.add(ChatItem(message = newMessage, isUserMessage = true))
    chatItems.value = currentChatItems
}*/

    // sending data to chat adapter
    // used from multiple places (main, audio recorder, file attachment)
    /*fun addMessageToChat(message: String, attachedImageLocations: List<String>, attachedFiles: List<Uri>, gpsLocationMessage: Boolean): ChatItem {
        val messageDate = chatHelper.getCurrentDate()
        val chatItem = ChatItem(
            message = message,
            isUserMessage = true,
            imageLocations = attachedImageLocations,
            aiCharacterName = "",
            fileNames = attachedFiles,
            isGPSLocationMessage = gpsLocationMessage,
            dateGenerate = messageDate
        )
        chatItems.add(chatItem)
        chatAdapter.notifyItemInserted(chatItems.size - 1)
        chatHelper.scrollToEnd()
        // return chatItem value - will be used for example when we want to update uuid of the message from DB (in handleTextMessage)
        return chatItem
    }

    // utility method to handle sending text requests for normal UI messages and transcriptions
    // (from ChatAdapter - for regenerate AI message or when transcribe button is clicked (for recordings listed in the chat and audio uploads), from AudioRecorder when recoding is done)
    // and here in Main - same functionality when Send button is clicked
    // gpsLocationMessage - if true - it is GPS location message - so will be handled differently in chatAdapter (will show GPS map button and probably few other things)
    override fun handleTextMessage(message: String, attachedImageLocations: List<String>, attachedFiles: List<Uri>, gpsLocationMessage: Boolean) {
        if (message.isEmpty() ) {
            return
        }

        val currentModel = ConfigurationManager.getTextModelName()
        // if there is image attached and we use model that does not support images
        if (attachedImageLocations != emptyList<String>() && currentModel != "GPT-4o" && currentModel != "GPT-4o-mini" && currentModel != "GPT-4" && currentModel != "Claude-3.5" ) {
            Toast.makeText(this, "Currently chosen model does not support images. Remove image or change the model", Toast.LENGTH_SHORT).show()
            return
        }

        if (attachedFiles != emptyList<Uri>() && currentModel != "GPT-4o" && currentModel != "GPT-4o-mini" && currentModel != "GPT-4" && currentModel != "Claude-3.5" ) {
            Toast.makeText(this, "In order to process attached files you need to change the model", Toast.LENGTH_SHORT).show()
            return
        }

        // some characters have autoResponse set to false - if this is the case - we don't stream
        val character = characterManager.getCharacterByNameForAPI(ConfigurationManager.getTextAICharacter())

        val currentSessionId = chatHelper.getCurrentDBSessionID()
        // Add message to chat
        chatHelper.getEditingMessagePosition()?.let { position ->
            chatHelper.editMessageInChat(position, message, attachedImageLocations, attachedFiles)

            // some characters have autoResponse set to false - if this is the case - we don't want to get response from AI (it's just data collection)
            if (character?.autoResponse == true) {
                startStreaming(currentSessionId, position)
            } else {
                val currentUserMessage = chatItems[position]
                // if we don't stream - we still need to save user message to DB
                CoroutineScope(Dispatchers.IO).launch {
                    DatabaseHelper.addNewOrEditDBMessage("db_edit_message", currentSessionId, currentUserMessage, null)
                }
            }
        } ?: run {
            val currentUserMessage = addMessageToChat(message, attachedImageLocations, attachedFiles, gpsLocationMessage)
            if (character?.autoResponse == true) {
                startStreaming(currentSessionId)
            } else {
                // if we don't stream - we still need to save to DB
                CoroutineScope(Dispatchers.IO).launch {
                    DatabaseHelper.addNewOrEditDBMessage("db_new_message", currentSessionId, currentUserMessage, null)
                }
            }
        }
        chatHelper.resetInputArea()
        // edit position reset
        chatHelper.setEditingMessagePosition(null)
        // hide characters view
        binding.characterMainView.visibility = View.GONE
    }

// streaming request to API - text
    // responseItemPosition - if it's null - it's new message - otherwise it's edited message
    private fun startStreaming(currentSessionId: String, responseItemPosition: Int? = null) {
        showProgressBar("Text generation")

        // collect chat history (needed to send it API to get whole context of chat)
        // (excluding the latest message - as this will be sent via userPrompt), including images if any
        // or excluding 1 or 2 last messages - if its edited user message
        var dropHowMany = 1
        if (responseItemPosition != null) {
            // if it is edited message - we have to drop 2 last messages (user and AI response)
            // but only if it is not the last message in chat
            if (responseItemPosition == chatItems.size - 1) {
                dropHowMany = 1
            } else {
                dropHowMany = 2
            }
        }
        val chatHistory = chatItems.dropLast(dropHowMany).map {
            if (it.isUserMessage) {
                val content = mutableListOf<Map<String, Any>>()
                content.add(mapOf("type" to "text", "text" to it.message))
                it.imageLocations.forEach { imageUrl ->
                    content.add(mapOf("type" to "image_url", "image_url" to mapOf("url" to imageUrl)))
                }
                it.fileNames.forEach { fileUri ->
                    if (fileUri.toString().endsWith(".pdf")) {
                        content.add(mapOf("type" to "file_url", "file_url" to mapOf("url" to fileUri.toString())))
                    }
                }
                mapOf("role" to "user", "content" to content)
            } else {
                mapOf("role" to "assistant", "content" to it.message)
            }
        }

        println("Start streaming")
        println("Chat history: $chatHistory")

        // user prompt preparation
        // checking responseItemPosition - if it's null - it's new message - otherwise it's edited message
        // first lets get user message - either last one or the one that was edited
        val userActiveChatItem = if (responseItemPosition == null) {
            // get the last user message and its images (if exists)
            chatItems.last()
        } else {
            // if edited message its last -1 (because last is  AI response)
            chatItems[responseItemPosition]
        }
        val userPrompt = mutableListOf<Map<String, Any>>()
        userPrompt.add(mapOf("type" to "text", "text" to userActiveChatItem.message))
        userActiveChatItem.imageLocations.forEach { imageUrl ->
            userPrompt.add(mapOf("type" to "image_url", "image_url" to mapOf("url" to imageUrl)))
        }
        userActiveChatItem.fileNames.forEach { fileUri ->
            if (fileUri.toString().endsWith(".pdf")) {
                userPrompt.add(mapOf("type" to "file_url", "file_url" to mapOf("url" to fileUri.toString())))
            }
        }

        println("11111")
        println(chatItems)
        println("22222")
        println(chatHistory)

        val apiDataModel = APIDataModel(
            category = "text",
            action = "chat",
            userInput = mapOf(
                "prompt" to userPrompt,
                "chat_history" to chatHistory
            ),
            userSettings = ConfigurationManager.getSettingsDict(),
            customerId = 1,
        )

        val streamUrl = ConfigurationManager.getAppModeApiUrl() + "chat"

        // having name of character via ConfigurationManager.getTextAICharacter() - lets get whole character from characters
        val character = characterManager.characters.find { it.nameForAPI == ConfigurationManager.getTextAICharacter() }

        // data now in format YYYY-MM-DD HH:MM
        val messageDate = chatHelper.getCurrentDate()

        // adding new or resetting AI response message (so we can add streaming chunks here)
        // checking responseItemPosition - if it's null - it's new message - otherwise it's edited message
        if (responseItemPosition == null) {
            // This is a new message, add a new response item
            val responseItem = ChatItem(message = "", isUserMessage = false, aiCharacterName = character?.nameForAPI, apiAIModelName = ConfigurationManager.getTextModelName(), dateGenerate = messageDate)
            chatItems.add(responseItem)
            currentResponseItemPosition = chatItems.size - 1
            chatAdapter.notifyItemInserted(currentResponseItemPosition!!)
        } else {
            // This is an edited message, replace the existing response item
            // we add +1 everywhere because position is in fact position of user message
            // and here we will edit next item (response) - so we have to add +1
            currentResponseItemPosition = responseItemPosition + 1
            // if there is no response item - we add it
            if (currentResponseItemPosition!! > chatItems.size - 1) {
                val responseItem = ChatItem(message = "", isUserMessage = false, aiCharacterName = character?.nameForAPI)
                chatItems.add(responseItem)
            }
            chatItems[currentResponseItemPosition!!].message = ""  // Clear previous response
            chatItems[currentResponseItemPosition!!].aiCharacterName = character?.nameForAPI
            chatItems[currentResponseItemPosition!!].apiAIModelName = ConfigurationManager.getTextModelName()
            chatItems[currentResponseItemPosition!!].dateGenerate = messageDate
            chatAdapter.notifyItemChanged(currentResponseItemPosition!!)
        }

        chatHelper.scrollToEnd()

        val handler = ResponseHandler(
            handlerType = HandlerType.Streaming(
                onChunkReceived = { chunk ->
                    CoroutineScope(Dispatchers.Main).launch {
                        // only if session has NOT changed - we want to add chunks to chat
                        if (currentSessionId == chatHelper.getCurrentDBSessionID()) {
                            currentResponseItemPosition?.let { position ->
                                chatItems[position].message += chunk
                                chatAdapter.notifyItemChanged(position)
                            }
                            // slight delay to smooth scrolling on adding chunks to UI
                            val handler = Handler(Looper.getMainLooper())
                            handler.postDelayed({
                                currentResponseItemPosition?.let { _ ->
                                    chatHelper.scrollToEnd()
                                }
                            }, 50)
                        }
                    }
                },
                onStreamEnd = {
                    CoroutineScope(Dispatchers.Main).launch {
                        hideProgressBar("Text generation")
                        // only if session has NOT changed - we want to proceed
                        if (currentSessionId == chatHelper.getCurrentDBSessionID()) {
                            if (ConfigurationManager.getTTSAutoExecute()) {
                                chatAdapter.sendTTSRequest(
                                    chatItems[currentResponseItemPosition!!].message,
                                    currentResponseItemPosition!!
                                )
                            }

                            // save to DB
                            // edit is possible only on last message
                            val currentUserMessage = chatItems[currentResponseItemPosition!! - 1]
                            val currentAIResponse = chatItems[currentResponseItemPosition!!]
                            if (currentAIResponse.aiCharacterName == "tools_artgen" && ConfigurationManager.getImageAutoGenerateImage() && currentAIResponse.imageLocations.isEmpty()) {
                                chatAdapter.triggerImageGeneration(currentResponseItemPosition!!)
                            }

                            // as above checking responseItemPosition - if it's null - it's new message - otherwise it's edited message
                            if (responseItemPosition == null) {
                                CoroutineScope(Dispatchers.IO).launch {
                                    DatabaseHelper.addNewOrEditDBMessage(
                                        "db_new_message",
                                        currentSessionId,
                                        currentUserMessage,
                                        currentAIResponse
                                    )
                                }
                            } else {
                                // if it is after user updated their message - AI response also needs to be overwritten in DB
                                CoroutineScope(Dispatchers.IO).launch {
                                    DatabaseHelper.addNewOrEditDBMessage(
                                        "db_edit_message",
                                        currentSessionId,
                                        currentUserMessage,
                                        currentAIResponse
                                    )
                                }
                            }
                        }
                    }
                }
            ),
            onError = { error ->
                runOnUiThread {
                    hideProgressBar("Text generation")
                    Toast.makeText(this, "Error: $error", Toast.LENGTH_LONG).show()
                }
            },
            authToken = ConfigurationManager.getAuthTokenForBackend(),
        )

        handler.sendRequest(streamUrl, apiDataModel)

        // we reset original AI character after message is sent - this is only executed when originalAICharacter is not null
        if (originalAICharacter != null) {
            ConfigurationManager.setTextAICharacter(originalAICharacter!!)
            originalAICharacter = null
        }
    }

     */

}
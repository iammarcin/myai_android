package biz.atamai.myai

import android.content.Context
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import biz.atamai.myai.databinding.ActivityMainBinding
import biz.atamai.myai.databinding.TopLeftMenuChatSessionItemBinding
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding

    private lateinit var fileAttachmentHandler: FileAttachmentHandler
    private lateinit var cameraHandler: CameraHandler

    private val chatItems: MutableList<ChatItem> = mutableListOf()
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var permissionsUtil: PermissionsUtil

    // this is for AI characters in app
    private lateinit var characterManager: CharacterManager

    // api URLs
    lateinit var apiUrl: String

    // needed for chat items placement - if its null - chat hasn't been started, if it has value - this is latest msg
    private var currentResponseItemPosition: Int? = null

    // for editing message - if we choose edit on any message
    private var editingMessagePosition: Int? = null

    // this will be used when mentioning (via @) AI characters for single message
    private var originalAICharacter: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        ConfigurationManager.init(this)

        setContentView(binding.root)

        audioRecorder = AudioRecorder(this, ConfigurationManager.getUseBluetooth())

        fileAttachmentHandler = FileAttachmentHandler(this, binding.imagePreviewContainer, binding.scrollViewPreview)
        cameraHandler = CameraHandler(this, activityResultRegistry)

        setupListeners()
        setupChatAdapter()
        setupRecordButton()
        setupCamera()
        setupPermissions()

        // Initialize TopMenuHandler
        val topMenuHandler = TopMenuHandler(
            this,
            layoutInflater,
            // below 2 functions must be in coroutine scope - because they are sending requests to DB and based on results different UI is displayed (different chat sessions)
            onFetchChatSessions = {
                CoroutineScope(Dispatchers.Main).launch {
                    sendDBRequest("db_all_sessions_for_user")
                }
            },
            onSearchMessages = { query ->
                CoroutineScope(Dispatchers.Main).launch {
                    sendDBRequest("db_search_messages", mapOf("search_text" to query))
                }
            }
        )
        topMenuHandler.setupTopMenus(binding)

        characterManager = CharacterManager(this)
        // on character selection - update character name in chat and set temporary character for single message
        characterManager.setupCharacterCards(binding) { characterName ->
            insertCharacterName(characterName)
        }
        // on purpose (for testing) - we set URL only on start - so switching in running app will not change it
        // mainly later once i have prod - it will be way to handle testing
        apiUrl = if (ConfigurationManager.getAppMode()) {
            "https://fancydomain.com:8000/"
        } else {
            //"http://192.168.23.66:8000/"
            "http://192.168.1.19:8000/"
        }

        // set status bar color (above app -where clock is)
        window.statusBarColor = ContextCompat.getColor(this, R.color.popupmenu_background)

    }

    private fun setupChatAdapter() {
        // chat adapter - but when clicked on message - we can edit it
        // this is done via onEditMessage in chat adapter - we point it to startEditingMessage
        chatAdapter = ChatAdapter(chatItems) { position, message ->
            startEditingMessage(position, message)
        }
        binding.chatContainer.adapter = chatAdapter
    }

    private fun scrollToEnd() {
        binding.chatContainer.scrollToPosition(chatItems.size - 1)
    }

    private fun setupListeners() {
        // this is listener for main chat container (taking most part of the screen)
        binding.chatContainer.setOnTouchListener { _, _ ->
            // idea is that when edit text has focus - recording button etc disappears
            // so this is to clear the focus if we click somewhere on main screen
            binding.editTextMessage.clearFocus()
            // hide mobile keyboard
            hideKeyboard(binding.editTextMessage)
            // Call performClick
            // this is necessary! explained in CustomRecyclerView
            binding.chatContainer.performClick()
            false
        }

        // attach button
        binding.btnAttach.setOnClickListener {
            fileAttachmentHandler.openFileChooser()
        }

        // for situation where we start typing in edit text - we want other stuff to disappear
        binding.editTextMessage.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                manageBottomEditSection("show")
            } else {
                manageBottomEditSection("hide")
                hideKeyboard(view)
            }
        }

        // when user starts typing and uses @ - show character selection area
        binding.editTextMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                //if user starts typing - show bottom edit section (there is a case after submit - when i start typing - letters appear and i don't need to click on edit text)
                if (count > 0 && before == 0) {
                    manageBottomEditSection("show")
                }
                s?.let {
                    val cursorPosition = binding.editTextMessage.selectionStart
                    val atIndex = s.lastIndexOf("@", cursorPosition - 1)
                    if (atIndex != -1) {
                        val query = s.substring(atIndex + 1, cursorPosition).trim()
                        binding.characterHorizontalMainScrollView.visibility = View.VISIBLE
                        scrollToEnd()

                        // while triggering search for new AI character - save original AI character - because new one will be set
                        // very important - that we have to change it only once - because if we chose different character this function is executed every time we type any character
                        // so originalAICharacter would be set many times (to new character) if we didn't have this check
                        if (originalAICharacter == null) {
                            originalAICharacter = ConfigurationManager.getTextAICharacter()
                        }
                        characterManager.filterCharacters(binding, query) { characterName ->
                            insertCharacterName(characterName)
                        }
                    } else {
                        binding.characterHorizontalMainScrollView.visibility = View.GONE
                    }
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // main send button
        binding.btnSend.setOnClickListener {
            handleSendButtonClick()
        }

        // camera
        binding.btnCamera.setOnClickListener {
            cameraHandler.takePhoto()
        }

        // Set up new chat button
        binding.newChatButton.setOnClickListener {
            resetChat()
            CoroutineScope(Dispatchers.Main).launch {
                sendDBRequest("db_new_session")
            }
        }
    }

    // used when user types @ - which opens character selection area... and then this function is triggered upon selection
    private fun insertCharacterName(characterName: String) {
        val currentText = binding.editTextMessage.text.toString()
        val cursorPosition = binding.editTextMessage.selectionStart
        val atIndex = currentText.lastIndexOf("@", cursorPosition - 1)

        if (atIndex != -1) {
            // Remove everything after the '@' character up to the current cursor position
            val newText = StringBuilder(currentText)
                .delete(atIndex + 1, cursorPosition)
                .insert(atIndex + 1, "$characterName ")
                .toString()
            binding.editTextMessage.setText(newText)
            binding.editTextMessage.setSelection(atIndex + characterName.length + 2) // Position cursor after the character name
        }
    }

    // show or hide bottom edit section (manage properly edit text and buttons)
    private fun manageBottomEditSection(action: String) {
        when (action) {
            "show" -> {
                binding.layoutRecord.visibility = View.GONE
                binding.btnSend.visibility = View.VISIBLE
                (binding.editTextMessage.layoutParams as LinearLayout.LayoutParams).weight = 0.7f
                (binding.rightAttachmentBar.layoutParams as LinearLayout.LayoutParams).weight = 0.3f
            }
            "hide" -> {
                binding.layoutRecord.visibility = View.VISIBLE
                binding.btnSend.visibility = View.GONE
                (binding.editTextMessage.layoutParams as LinearLayout.LayoutParams).weight = 0.5f
                (binding.rightAttachmentBar.layoutParams as LinearLayout.LayoutParams).weight = 0.5f
            }
        }
    }

    // when dealing with hasFocus etc for edit text - if we lose hasFocus - keyboard remained on the screen
    private fun hideKeyboard(view: View) {
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }


    // edit any user message
    private fun startEditingMessage(position: Int, message: String) {
        editingMessagePosition = position
        binding.editTextMessage.setText(message)
        binding.editTextMessage.requestFocus()
        binding.editTextMessage.setSelection(message.length)
        binding.editTextMessage.maxLines = 10

        // Show the send button and hide the record button
        manageBottomEditSection("show")
    }

    private fun handleSendButtonClick() {
        val message = binding.editTextMessage.text.toString()
        val attachedImageLocations = mutableListOf<String>()
        val attachedFilePaths = mutableListOf<Uri>()

        for (i in 0 until binding.imagePreviewContainer.childCount) {
            val frameLayout = binding.imagePreviewContainer.getChildAt(i) as FrameLayout
            // if it's an image
            if (frameLayout.getChildAt(0) is ImageView) {
                val imageView = frameLayout.getChildAt(0) as ImageView
                if (imageView.tag == null) {
                    Toast.makeText(this, "Problems with uploading files. Try again", Toast.LENGTH_SHORT).show()
                    continue
                }
                attachedImageLocations.add(imageView.tag as String)
            } else {
                // if it's a file
                val placeholder = frameLayout.getChildAt(0) as View
                attachedFilePaths.add(placeholder.tag as Uri)
            }
        }

        handleTextMessage(message, attachedImageLocations, attachedFilePaths)
    }

    // utility method to handle sending text requests for normal UI messages and transcriptions
    // (from ChatAdapter - when transcribe button is clicked (for recordings listed in the chat and audio uploads), from AudioRecorder when recoding is done)
    // and here in Main - same functionality when Send button is clicked
    fun handleTextMessage(message: String, attachedImageLocations: List<String> = listOf(), attachedFiles: List<Uri> = listOf()) {
        if (message.isEmpty()) {
            return
        }

        println("!")
        println("chatItems : $chatItems")
        // if chatItems is empty
        // create new session on DB - because this is new chat (without any messages)
        if (chatItems.isEmpty()) {
            CoroutineScope(Dispatchers.Main).launch {
                sendDBRequest("db_new_session")
            }
        }

        // Add message to chat
        editingMessagePosition?.let { position ->
            editMessageInChat(position, message, attachedImageLocations, attachedFiles)
            startStreaming(message, position)
            // edit message in DB
            CoroutineScope(Dispatchers.Main).launch {
                updateDBMessage(position, message, attachedImageLocations, attachedFiles)
            }
        } ?: run {
            val newChatItem = addMessageToChat(message, attachedImageLocations, attachedFiles)
            startStreaming(message)
            CoroutineScope(Dispatchers.Main).launch {
                sendDBRequest(
                    "db_new_message",
                    mapOf(
                        "customer_id" to 1,
                        "session_id" to ConfigurationManager.getDBCurrentSessionId(),
                        "sender" to (newChatItem.aiCharacterName ?: "AI"),
                        "message" to newChatItem.message,
                        "image_locations" to newChatItem.imageLocations,
                        "file_locations" to newChatItem.fileNames,
                        "chat_history" to chatItems
                    )
                ) { messageId ->
                    newChatItem.messageId = messageId
                    chatAdapter.notifyItemChanged(chatItems.indexOf(newChatItem))
                }
            }
        }
        resetInputArea()
        binding.characterHorizontalMainScrollView.visibility = View.GONE
    }

    // once message is edited - update it in chat
    private fun editMessageInChat(position: Int, message: String, attachedImageLocations: List<String>, attachedFiles: List<Uri> = listOf()) {
        val chatItem = chatItems[position]
        chatItem.message = message
        chatItem.imageLocations = attachedImageLocations
        chatItem.fileNames = attachedFiles
        chatAdapter.notifyItemChanged(position)
    }

    // when new chat is used - clear everything
    private fun resetChat() {
        val size = chatItems.size
        chatItems.clear()
        chatAdapter.notifyItemRangeRemoved(0, size)
        resetInputArea()

        // show characters again
        binding.characterHorizontalMainScrollView.visibility = View.VISIBLE
    }

    // helper functions - to disable important (send and record buttons) while some activity takes place
    // for sure used when file is uploaded in FileAttachmentHandler
    // - because we want to be sure that file is uploaded to S3 before user can send request
    fun disableActiveButtons() {
        binding.btnSend.isEnabled = false
        binding.btnRecord.isEnabled = false
        binding.newChatButton.isEnabled = false
    }
    fun enableActiveButtons() {
        binding.btnSend.isEnabled = true
        binding.btnRecord.isEnabled = true
        binding.newChatButton.isEnabled = true
    }


    // DB REQUESTS
    private suspend fun sendDBRequest(action: String, userInput: Map<String, Any> = mapOf(), callback: ((Int) -> Unit)? = null) {

        CoroutineScope(Dispatchers.IO).launch {
            val apiDataModel = APIDataModel(
                category = "provider.db",
                action = action,
                userInput = userInput,
                userSettings = ConfigurationManager.getSettingsDict(),
                customerId = 1,
            )

            val dbUrl = apiUrl + "api/db"

            val handler = ResponseHandler(
                handlerType = HandlerType.NonStreaming(
                    onResponseReceived = { response ->
                        CoroutineScope(Dispatchers.Main).launch {
                            handleDBResponse(action, response, callback)
                        }
                    }
                ),
                onError = { error ->
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(this@MainActivity, "Error: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                }
            )

            handler.sendRequest(dbUrl, apiDataModel)
        }
    }

    private suspend fun updateDBMessage(position: Int, message: String, attachedImageLocations: List<String>, attachedFiles: List<Uri>) {
        val chatItem = chatItems[position]
        val messageId = chatItem.messageId ?: return // Ensure messageId is not null

        sendDBRequest(
            "db_edit_message",
            mapOf(
                "session_id" to ConfigurationManager.getDBCurrentSessionId(),
                "message_id" to messageId,
                "update_text" to message,
                "image_locations" to attachedImageLocations,
                "file_locations" to attachedFiles,
                "chat_history" to chatItems,
            )
        )
    }

    // handling response from DB (through API)
    private fun handleDBResponse(action: String, response: String, dbNewMessageCallback: ((Int) -> Unit)? = null) {
        println("DB RESPONSE: $response")
        when (action) {
            "db_new_session" -> {
                ConfigurationManager.setDBCurrentSessionId(JSONObject(response).getJSONObject("message").getString("result"))
            }
            "db_all_sessions_for_user", "db_search_messages" -> {
                val sessions = parseSessions(response)
                displayChatSessions(sessions)
            }
            "db_get_user_session" -> {
                val sessionData = JSONObject(response).getJSONObject("message").getJSONObject("result")
                restoreSessionData(sessionData)
            }
            "db_new_message" -> {
                val messageId = JSONObject(response).getJSONObject("message").getString("result")
                // if messageId is not null and its number - lets change it to integer
                if (messageId.toIntOrNull() != null) {
                    dbNewMessageCallback?.invoke(messageId.toInt())
                }
            }
        }
    }

    // upon receving data from DB - we parse session data to later display them
    // for the moment used in top left menu
    private fun parseSessions(response: String): List<APIChatSession> {
        val jsonObject = JSONObject(response)
        val resultArray = jsonObject.getJSONObject("message").getJSONArray("result")
        val sessions = mutableListOf<APIChatSession>()
        for (i in 0 until resultArray.length()) {
            val sessionObject = resultArray.getJSONObject(i)
            val session = APIChatSession(
                sessionId = sessionObject.getString("session_id"),
                sessionName = sessionObject.getString("session_name") ?: "New chat",
                aiCharacterName = sessionObject.getString("ai_character_name") ?: "Assistant",
                createdAt = sessionObject.getString("created_at") ?: "",
                lastUpdate = sessionObject.getString("last_update") ?: ""
            )
            sessions.add(session)
        }
        return sessions
    }


    // after parsing data from DB - we display it in left top menu
    private fun displayChatSessions(sessions: List<APIChatSession>) {
        val drawerLayout = binding.topLeftMenuNavigationView.findViewById<LinearLayout>(R.id.topLeftMenuChatSessionList)

        drawerLayout.removeAllViews()

        sessions.forEach { session ->
            val sessionViewBinding = TopLeftMenuChatSessionItemBinding.inflate(layoutInflater, drawerLayout, false)
            sessionViewBinding.sessionName.text = session.sessionName
            val aiCharacter = session.aiCharacterName
            // having name of character, lets search its image through CharacterManager
            val character = characterManager.characters.find { it.nameForAPI == aiCharacter }
            sessionViewBinding.sessionAiCharacterImageView.setImageResource(character?.imageResId ?: R.drawable.brainstorm_assistant)
            // last update date in format YYYY/MM/DD
            sessionViewBinding.sessionLastUpdate.text = session.lastUpdate.split("T")[0]
            //handle click on session
            sessionViewBinding.root.setOnClickListener {
                println("Session ID: ${session.sessionId}")
                // get data for this specific session
                CoroutineScope(Dispatchers.Main).launch {
                    sendDBRequest("db_get_user_session", mapOf("session_id" to session.sessionId))
                }
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            }
            drawerLayout.addView(sessionViewBinding.root)
        }
    }

    // once we have all the data regarding session - we restore it in chat
    private fun restoreSessionData(sessionData: JSONObject) {

        println("RESTORE SESSION DATA: $sessionData")
        val chatHistoryString = sessionData.getString("chat_history")
        val chatHistory = JSONArray(chatHistoryString)

        println("CHAT HISTORY: $chatHistory")

        resetChat()

        for (i in 0 until chatHistory.length()) {
            val chatItemJson = chatHistory.getJSONObject(i)
            val chatItem = ChatItem(
                message = chatItemJson.getString("message"),
                isUserMessage = chatItemJson.getBoolean("isUserMessage"),
                imageLocations = chatItemJson.getJSONArray("imageLocations").let { jsonArray ->
                    List(jsonArray.length()) { index -> jsonArray.getString(index) }
                },
                fileNames = chatItemJson.getJSONArray("fileNames").let { jsonArray ->
                    List(jsonArray.length()) { index -> Uri.parse(jsonArray.getString(index)) }
                },
                aiCharacterName = chatItemJson.optString("aiCharacterName", ""),
                aiCharacterImageResId = chatItemJson.optInt("aiCharacterImageResId", R.drawable.brainstorm_assistant)
            )
            chatItems.add(chatItem)
        }

        ConfigurationManager.setDBCurrentSessionId(sessionData.getString("session_id"))
        binding.characterHorizontalMainScrollView.visibility = View.GONE

        chatAdapter.notifyItemRangeInserted(0, chatItems.size)

        scrollToEnd()
    }

    // streaming request to API - text
    private fun startStreaming(userInput: String, responseItemPosition: Int? = null) {
        showProgressBar()

        // collect chat history (needed to send it API to get whole context of chat)
        // (excluding the latest message - as this will be sent via userPrompt), including images if any
        val chatHistory = chatItems.dropLast(1).map {
            if (it.isUserMessage) {
                val content = mutableListOf<Map<String, Any>>()
                content.add(mapOf("type" to "text", "text" to it.message))
                it.imageLocations.forEach { imageUrl ->
                    content.add(mapOf("type" to "image_url", "image_url" to mapOf("url" to imageUrl)))
                }
                mapOf("role" to "user", "content" to content)
            } else {
                mapOf("role" to "assistant", "content" to it.message)
            }
        }

        // get the last user message and its images (if exists)
        val lastChatItem = chatItems.last()
        val userPrompt = mutableListOf<Map<String, Any>>()
        userPrompt.add(mapOf("type" to "text", "text" to lastChatItem.message))
        lastChatItem.imageLocations.forEach { imageUrl ->
            userPrompt.add(mapOf("type" to "image_url", "image_url" to mapOf("url" to imageUrl)))
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

        val streamUrl = apiUrl + "chat"

        // having name of character via ConfigurationManager.getTextAICharacter() - lets get whole character from characters
        val character = characterManager.characters.find { it.nameForAPI == ConfigurationManager.getTextAICharacter() }

        // checking responseItemPosition - if it's null - it's new message - otherwise it's edited message
        if (responseItemPosition == null) {
            // This is a new message, add a new response item
            val responseItem = ChatItem(message = "", isUserMessage = false, aiCharacterName = character?.nameForAPI, aiCharacterImageResId = character?.imageResId)
            chatItems.add(responseItem)
            currentResponseItemPosition = chatItems.size - 1
            chatAdapter.notifyItemInserted(currentResponseItemPosition!!)
        } else {
            // This is an edited message, replace the existing response item
            // we add +1 everywhere because position is in fact position of user message
            // and here we will edit next item (response) - so we have to add +1
            currentResponseItemPosition = responseItemPosition + 1
            chatItems[responseItemPosition + 1].message = ""  // Clear previous response
            chatAdapter.notifyItemChanged(responseItemPosition + 1)
        }

        scrollToEnd()

        val handler = ResponseHandler(
            handlerType = HandlerType.Streaming(
                onChunkReceived = { chunk ->
                    runOnUiThread {
                        currentResponseItemPosition?.let { position ->
                            chatItems[position].message += chunk
                            chatAdapter.notifyItemChanged(position)
                            scrollToEnd()
                        }
                    }
                },
                onStreamEnd = {
                    runOnUiThread {
                        hideProgressBar()
                        // save to DB
                        val currentMessage = chatItems[currentResponseItemPosition!!]

                        CoroutineScope(Dispatchers.Main).launch {
                            // as above checking responseItemPosition - if it's null - it's new message - otherwise it's edited message
                            if (responseItemPosition == null) {
                                sendDBRequest(
                                    "db_new_message",
                                    mapOf(
                                        "customer_id" to 1,
                                        "session_id" to ConfigurationManager.getDBCurrentSessionId(),
                                        "sender" to (currentMessage.aiCharacterName ?: "AI"),
                                        "message" to currentMessage.message,
                                        "image_locations" to currentMessage.imageLocations,
                                        "file_locations" to currentMessage.fileNames,
                                        "chat_history" to chatItems
                                    )
                                ) { messageId ->
                                    chatItems[currentResponseItemPosition!!].messageId = messageId
                                    chatAdapter.notifyItemChanged(currentResponseItemPosition!!)
                                }
                            } else {
                                // if it is after user updated their message - AI response also needs to be overwritten in DB
                                val messageId = currentMessage.messageId ?: return@launch // Ensure messageId is not null

                                sendDBRequest(
                                    "db_edit_message",
                                    mapOf(
                                        "session_id" to ConfigurationManager.getDBCurrentSessionId(),
                                        "message_id" to messageId,
                                        "update_text" to currentMessage.message,
                                        "image_locations" to currentMessage.imageLocations,
                                        "file_locations" to currentMessage.fileNames,
                                        "chat_history" to chatItems,
                                    )
                                )
                            }
                        }


                    }
                }
            ),
            onError = { error ->
                runOnUiThread {
                    hideProgressBar()
                    Toast.makeText(this, "Error: $error", Toast.LENGTH_LONG).show()
                }
            }
        )

        handler.sendRequest(streamUrl, apiDataModel)

        // we reset original AI character after message is sent - this is only executed when originalAICharacter is not null
        if (originalAICharacter != null) {
            ConfigurationManager.setTextAICharacter(originalAICharacter!!)
            originalAICharacter = null
        }
    }

    // sending data to chat adapter
    fun addMessageToChat(message: String, attachedImageLocations: List<String>, attachedFiles: List<Uri> = listOf()): ChatItem {
        val chatItem = ChatItem(message = message, isUserMessage = true, imageLocations = attachedImageLocations, aiCharacterName = "", fileNames = attachedFiles)
        chatItems.add(chatItem)
        chatAdapter.notifyItemInserted(chatItems.size - 1)
        scrollToEnd()
        // return chatItem value - will be used for example when we want to update uuid of the message from DB (in handleTextMessage)
        return chatItem
    }

    // reset after submission - clean input text, images preview and scroll view in general
    private fun resetInputArea() {
        binding.editTextMessage.setText("")
        binding.imagePreviewContainer.removeAllViews()
        binding.scrollViewPreview.visibility = View.GONE

        manageBottomEditSection("hide")
        // release focus of binding.editTextMessage
        binding.editTextMessage.clearFocus()
        // edit position reset
        editingMessagePosition = null
    }

    // AUDIO RECORDER
    private fun setupRecordButton() {
        binding.btnRecord.setOnClickListener {
            audioRecorder.handleRecordButtonClick()
        }
    }

    fun setRecordButtonImageResource(resourceId: Int) {
        binding.btnRecord.setImageResource(resourceId)
    }

    // CAMERA
    private fun setupCamera() {
        cameraHandler.setupTakePictureLauncher(
            onSuccess = { uri ->
                uri?.let {
                    fileAttachmentHandler.addFilePreview(uri, true)
                }
            },
            onFailure = {
                Toast.makeText(this, "Failed to capture image", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // PROGRESS BAR
    fun showProgressBar() {
        binding.progressBar.visibility = View.VISIBLE
    }

    fun hideProgressBar() {
        binding.progressBar.visibility = View.GONE
    }

    // permissions
    private fun setupPermissions() {
        permissionsUtil = PermissionsUtil(this)

        if (!permissionsUtil.checkPermissions()) {
            permissionsUtil.requestPermissions()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionsUtil.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onPause() {
        super.onPause()
        if (isFinishing) {
            chatAdapter.releaseMediaPlayers()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        chatAdapter.releaseMediaPlayers()  // Ensure all media players are released when the activity is destroyed
        audioRecorder.release()  // Release resources held by AudioRecorder
    }
}

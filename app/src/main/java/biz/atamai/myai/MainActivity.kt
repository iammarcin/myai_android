// MainActivity.kt

package biz.atamai.myai

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import biz.atamai.myai.databinding.ActivityMainBinding
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity(), MainHandler {

    lateinit var binding: ActivityMainBinding
    override val context: Context
        get() = this

    private lateinit var fileAttachmentHandler: FileAttachmentHandler
    private lateinit var cameraHandler: CameraHandler

    private val chatItems: MutableList<ChatItem> = mutableListOf()
    override val chatItemsList: MutableList<ChatItem>
        get() = this.chatItems

    private lateinit var chatAdapter: ChatAdapter
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var permissionsUtil: PermissionsUtil

    // this is for AI characters in app
    lateinit var characterManager: CharacterManager

    private lateinit var gpsLocationManager: GPSLocationManager

    // some chat methods
    lateinit var chatHelper: ChatHelper

    // needed for chat items placement - if its null - chat hasn't been started, if it has value - this is latest msg
    private var currentResponseItemPosition: Int? = null

    // this will be used when mentioning (via @) AI characters for single message
    private var originalAICharacter: String? = null

    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        ConfigurationManager.init(this)

        setContentView(binding.root)

        audioRecorder = AudioRecorder(this, ConfigurationManager.getUseBluetooth(), ConfigurationManager.getAppModeApiUrl())

        fileAttachmentHandler = FileAttachmentHandler(this, ConfigurationManager.getAppModeApiUrl())
        cameraHandler = CameraHandler(this, activityResultRegistry)

        // has to BEFORE chat adapter
        characterManager = CharacterManager(this)

        setupListeners()
        setupChatAdapter()
        setupRecordButton()
        setupCamera()
        setupPermissions()

        // Initialize TopMenuHandler
        val topMenuHandler = TopMenuHandler(
            this,
            // below 2 functions must be in coroutine scope - because they are sending requests to DB and based on results different UI is displayed (different chat sessions)
            onFetchChatSessions = {
                CoroutineScope(Dispatchers.Main).launch {
                    DatabaseHelper.loadChatSessions()
                }
            },
            onSearchMessages = { query ->
                CoroutineScope(Dispatchers.Main).launch {
                    DatabaseHelper.sendDBRequest("db_search_messages", mapOf("search_text" to query))
                }
            }
        )
        topMenuHandler.setupTopMenus(binding)

        chatHelper = ChatHelper(this, chatAdapter, chatItems, ConfigurationManager)
        // this is needed - because chatHelper needs chatAdapter and vice versa
        // so first we initialize chatAdapter (without chatHelper as its not yet initialized) and later we set chatHelper to chatAdapter
        chatAdapter.setChatHelperHandler(chatHelper)

        DatabaseHelper.initialize(this, chatHelper)

        gpsLocationManager = GPSLocationManager(this)

        // on character selection - update character name in chat and set temporary character for single message (when using @ in chat)
        characterManager.setupCharacterCards(binding) { characterName ->
            chatHelper.insertCharacterName(characterName)
        }
        // set default character (Assistant) - in case there is some remaining from previous app run
        ConfigurationManager.setTextAICharacter("Assistant")

        // start new chat session
        // i tried many many different ways not to put it here (around 26-27May - check chatgpt if you want ;) )
        // mainly wanted to put it when new message is sent - but there was problem with order of execution (for user request and AI response)
        // and multiple sessions were created anyway
        // so decided to do this way - probably will end up with many empty sessions - but i guess i ignore it (or clean up later)
        CoroutineScope(Dispatchers.Main).launch {
            DatabaseHelper.sendDBRequest("db_new_session",
                mapOf(
                    "session_name" to "New chat",
                    "ai_character_name" to "Assistant",
                    ))
        }

        // set status bar color (above app -where clock is)
        window.statusBarColor = ContextCompat.getColor(this, R.color.popupmenu_background)

    }

    private fun setupChatAdapter() {
        // chat adapter - but when clicked on message - we can edit it
        // this is done via onEditMessage in chat adapter - we point it to startEditingMessage
        chatAdapter = ChatAdapter(
            chatItems,
            ConfigurationManager.getAppModeApiUrl(),
            characterManager,
            this,
        )
            { position, message -> chatHelper.startEditingMessage(position, message) }
            //this,

        binding.chatContainer.adapter = chatAdapter
    }

    private fun setupListeners() {

        // this is listener for main chat container (taking most part of the screen)
        binding.chatContainer.setOnTouchListener { _, _ ->
            // idea is that when edit text has focus - recording button etc disappears
            // so this is to clear the focus if we click somewhere on main screen
            binding.editTextMessage.clearFocus()
            // hide mobile keyboard
            chatHelper.hideKeyboard(binding.editTextMessage)
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
                chatHelper.manageBottomEditSection("show")
            } else {
                chatHelper.manageBottomEditSection("hide")
                chatHelper.hideKeyboard(view)
            }
        }

        // when user starts typing and uses @ - show character selection area
        binding.editTextMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                //if user starts typing - show bottom edit section (there is a case after submit - when i start typing - letters appear and i don't need to click on edit text)
                if (count > 0 && before == 0) {
                    chatHelper.manageBottomEditSection("show")
                }
                s?.let {
                    val cursorPosition = binding.editTextMessage.selectionStart
                    val atIndex = s.lastIndexOf("@", cursorPosition - 1)
                    if (atIndex != -1) {
                        val query = s.substring(atIndex + 1, cursorPosition).trim()
                        binding.characterHorizontalMainScrollView.visibility = View.VISIBLE
                        chatHelper.scrollToEnd()

                        // while triggering search for new AI character - save original AI character - because new one will be set
                        // very important - that we have to change it only once - because if we chose different character this function is executed every time we type any character
                        // so originalAICharacter would be set many times (to new character) if we didn't have this check
                        if (originalAICharacter == null) {
                            originalAICharacter = ConfigurationManager.getTextAICharacter()
                        }
                        characterManager.filterCharacters(binding, query) { characterName ->
                            chatHelper.insertCharacterName(characterName)
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
            chatHelper.resetChat()
            binding.btnShareLocation.visibility = View.GONE
            binding.characterScrollView.visibility = View.VISIBLE
            ConfigurationManager.setTextAICharacter("Assistant")
            CoroutineScope(Dispatchers.Main).launch {
                DatabaseHelper.sendDBRequest("db_new_session",
                    mapOf(
                        "session_name" to "New chat",
                        "ai_character_name" to "Assistant",
                    ))
            }
        }

        // scrollview with sessions list on top left menu
        val scrollView = binding.topLeftMenuChatSessionListScrollView
        scrollView.viewTreeObserver.addOnScrollChangedListener {
            val view = scrollView.getChildAt(scrollView.childCount - 1) as View
            val diff = (view.bottom - (scrollView.height + scrollView.scrollY))

            if (diff == 0) {
                // User has scrolled to the bottom
                DatabaseHelper.loadMoreChatSessions()
            }
        }

        // GPS button
        binding.btnShareLocation.setOnClickListener {
            if (gpsLocationManager.areLocationServicesEnabled()) {
                showProgressBar("GPS location")
                gpsLocationManager.getCurrentLocation { location ->
                    location?.let {
                        val uri = Uri.parse("${it.latitude},${it.longitude}")
                        val message = "GPS location: $uri"
                        hideProgressBar("GPS location")
                         handleTextMessage(message, emptyList(), emptyList(), true)
                    } ?: run {
                        Toast.makeText(this, "Unable to get location", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "GPS Location services are disabled", Toast.LENGTH_SHORT).show()
                // Optionally, you can open the location settings
                //val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                //startActivity(intent)
            }
        }
    }

    // sending data to chat adapter
    // used from multiple places (main, audio recorder, file attachment)
    override fun addMessageToChat(message: String, attachedImageLocations: List<String>, attachedFiles: List<Uri>, gpsLocationMessage: Boolean): ChatItem {
        val chatItem = ChatItem(message = message, isUserMessage = true, imageLocations = attachedImageLocations, aiCharacterName = "", fileNames = attachedFiles, isGPSLocationMessage = gpsLocationMessage)
        chatItems.add(chatItem)
        chatAdapter.notifyItemInserted(chatItems.size - 1)
        chatHelper.scrollToEnd()
        // return chatItem value - will be used for example when we want to update uuid of the message from DB (in handleTextMessage)
        return chatItem
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
    // (from ChatAdapter - for regenerate AI message or when transcribe button is clicked (for recordings listed in the chat and audio uploads), from AudioRecorder when recoding is done)
    // and here in Main - same functionality when Send button is clicked
    // gpsLocationMessage - if true - it is GPS location message - so will be handled differently in chatAdapter (will show GPS map button and probably few other things)
    override fun handleTextMessage(message: String, attachedImageLocations: List<String>, attachedFiles: List<Uri>, gpsLocationMessage: Boolean) {
        if (message.isEmpty()) {
            return
        }

        // if there is image attached and we use model that does not support images
        if (attachedImageLocations != emptyList<String>() && ConfigurationManager.getTextModelName() != "GPT-4o" && ConfigurationManager.getTextModelName() != "GPT-4" ) {
            Toast.makeText(this, "Currently chosen model does not support images. Remove image or change the model", Toast.LENGTH_SHORT).show()
            return
        }

        val character = characterManager.characters.find { it.nameForAPI == ConfigurationManager.getTextAICharacter() }
        // Add message to chat
        chatHelper.getEditingMessagePosition()?.let { position ->
            chatHelper.editMessageInChat(position, message, attachedImageLocations, attachedFiles)
            // some characters have autoResponse set to false - if this is the case - we don't want to get response from AI (it's just data collection)
            if (character?.autoResponse == true) {
                startStreaming(position)
            } else {
                val currentUserMessage = chatItems[position]
                // if we don't stream - we still need to save user message to DB
                CoroutineScope(Dispatchers.Main).launch {
                    DatabaseHelper.addNewOrEditDBMessage("db_edit_message", currentUserMessage, null)
                }
            }
        } ?: run {
            val currentUserMessage = addMessageToChat(message, attachedImageLocations, attachedFiles, gpsLocationMessage)
            if (character?.autoResponse == true) {
                startStreaming()
            } else {
                // if we don't stream - we still need to save to DB
                CoroutineScope(Dispatchers.Main).launch {
                    DatabaseHelper.addNewOrEditDBMessage("db_new_message", currentUserMessage, null)
                }
            }
        }
        chatHelper.resetInputArea()
        // edit position reset
        chatHelper.setEditingMessagePosition(null)
        // hide characters view
        binding.characterHorizontalMainScrollView.visibility = View.GONE
    }

    // streaming request to API - text
    // responseItemPosition - if it's null - it's new message - otherwise it's edited message
    private fun startStreaming(responseItemPosition: Int? = null) {
        showProgressBar("Text generation")

        // collect chat history (needed to send it API to get whole context of chat)
        // (excluding the latest message - as this will be sent via userPrompt), including images if any
        // or excluding 2 last messages - if its edited user message
        var dropHowMany = 1
        if (responseItemPosition != null) {
            dropHowMany = 2
        }
        val chatHistory = chatItems.dropLast(dropHowMany).map {
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

        println("Start streaming")
        println("Chat history: $chatHistory")

        // user prompt prepartion
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

        // adding new or resetting AI response message (so we can add streaming chunks here)
        // checking responseItemPosition - if it's null - it's new message - otherwise it's edited message
        if (responseItemPosition == null) {
            // This is a new message, add a new response item
            val responseItem = ChatItem(message = "", isUserMessage = false, aiCharacterName = character?.nameForAPI)
            chatItems.add(responseItem)
            currentResponseItemPosition = chatItems.size - 1
            chatAdapter.notifyItemInserted(currentResponseItemPosition!!)
        } else {
            // This is an edited message, replace the existing response item
            // we add +1 everywhere because position is in fact position of user message
            // and here we will edit next item (response) - so we have to add +1
            currentResponseItemPosition = responseItemPosition + 1
            chatItems[currentResponseItemPosition!!].message = ""  // Clear previous response
            chatAdapter.notifyItemChanged(currentResponseItemPosition!!)
        }

        chatHelper.scrollToEnd()

        val handler = ResponseHandler(
            handlerType = HandlerType.Streaming(
                onChunkReceived = { chunk ->
                    runOnUiThread {
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
                        }, 50) // Adjust the delay time as needed
                    }
                },
                onStreamEnd = {
                    runOnUiThread {
                        hideProgressBar("Text generation")
                        if (ConfigurationManager.getTTSAutoExecute()) {
                            showProgressBar("TTS")
                            chatAdapter.sendTTSRequest(chatItems[currentResponseItemPosition!!].message, currentResponseItemPosition!!)
                        }

                        // save to DB
                        val currentUserMessage = chatItems[currentResponseItemPosition!! - 1]
                        val currentAIResponse = chatItems[currentResponseItemPosition!!]

                        println("Chatitems: $chatItems")
                        println("currentResponseItemPosition: $currentResponseItemPosition")
                        println("Current user message: $currentUserMessage")
                        println("Current AI response: $currentAIResponse")

                        if (currentAIResponse.aiCharacterName == "Artgen" && ConfigurationManager.getImageAutoGenerateImage() && currentAIResponse.imageLocations.isEmpty()) {
                            chatAdapter.triggerImageGeneration(currentResponseItemPosition!!)
                        }

                        // as above checking responseItemPosition - if it's null - it's new message - otherwise it's edited message
                        if (responseItemPosition == null) {
                            CoroutineScope(Dispatchers.Main).launch {
                                DatabaseHelper.addNewOrEditDBMessage("db_new_message", currentUserMessage, currentAIResponse)
                            }
                        } else {
                            // if it is after user updated their message - AI response also needs to be overwritten in DB
                            CoroutineScope(Dispatchers.Main).launch {
                                DatabaseHelper.addNewOrEditDBMessage("db_edit_message", currentUserMessage, currentAIResponse)
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

    // AUDIO RECORDER
    private fun setupRecordButton() {
        binding.btnRecord.setOnClickListener {
            audioRecorder.handleRecordButtonClick()
        }
    }

    override fun setRecordButtonImageResource(resourceId: Int) {
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

    // needed for chatInterfaces
    // PROGRESS BAR
    override fun showProgressBar(message: String) {
        binding.progressContainer.visibility = View.VISIBLE
        val currentText = binding.progressText.text.toString()
        // append new message to existing text
        var newText = ""
        if (currentText.isNotEmpty()) {
            // make sure not to duplicate the message
            if (!currentText.contains(message)) {
                newText = "$currentText $message"
            }
        } else {
            newText = message
        }
        binding.progressText.text = newText
    }
    override fun hideProgressBar(message: String) {
        val currentText = binding.progressText.text.toString()
        val newText = currentText.replace(message, "").trim()  // Remove the specified message

        if (newText.isEmpty()) {
            binding.progressContainer.visibility = View.GONE
        }

        binding.progressText.text = newText
    }
    override fun getCurrentAICharacter(): String {
        return ConfigurationManager.getTextAICharacter()
    }
    override fun executeOnUIThread(action: Runnable) {
        this@MainActivity.runOnUiThread(action)
    }
    override fun getMainActivity(): Activity {
        return this
    }
    override fun getMainBinding(): ActivityMainBinding {
        return binding
    }
    override fun getMainBindingContext(): Context {
        return binding.root.context
    }
    override fun getMainCharacterManager(): CharacterManager {
        return characterManager
    }
    override val mainLayoutInflaterInstance: LayoutInflater
        get() = this.layoutInflater

    override fun createToastMessage(message: String, duration: Int) {
        Toast.makeText(this, message, duration).show()
    }

    override fun getImagePreviewContainer(): LinearLayout {
        return binding.imagePreviewContainer
    }
    override fun getScrollViewPreview(): HorizontalScrollView {
        return binding.scrollViewPreview
    }
    override fun registerForActivityResult(contract: ActivityResultContracts.StartActivityForResult, callback: (ActivityResult) -> Unit): ActivityResultLauncher<Intent> {
        return super.registerForActivityResult(contract, callback)
    }
    // permissions
    override fun checkSelfPermission(permission: String): Int {
        return ContextCompat.checkSelfPermission(this, permission)
    }

    override fun requestAllPermissions(permissions: Array<String>, requestCode: Int) {
        ActivityCompat.requestPermissions(this, permissions, requestCode)
    }
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
        mediaPlayer?.release()
        mediaPlayer = null
    }
}

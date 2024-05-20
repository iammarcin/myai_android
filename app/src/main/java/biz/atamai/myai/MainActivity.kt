package biz.atamai.myai

import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import biz.atamai.myai.databinding.ActivityMainBinding
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var fileAttachmentHandler: FileAttachmentHandler
    private lateinit var cameraHandler: CameraHandler

    private val chatItems: MutableList<ChatItem> = mutableListOf()
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var permissionsUtil: PermissionsUtil

    // this is for AI characters in app
    private lateinit var characterManager: CharacterManager

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
        val topMenuHandler = TopMenuHandler(this, layoutInflater)
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
            "http://192.168.23.66:8000/"
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
        // attach button
        binding.btnAttach.setOnClickListener {
            fileAttachmentHandler.openFileChooser()
        }

        // for situation where we start typing in edit text - we want other stuff to disappear
        binding.editTextMessage.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                manageBottomEditSection("show")
            } else {
                manageBottomEditSection("hide")
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
        val attachedImageUris = mutableListOf<Uri>()
        val attachedFilePaths = mutableListOf<Uri>()

        for (i in 0 until binding.imagePreviewContainer.childCount) {
            val frameLayout = binding.imagePreviewContainer.getChildAt(i) as FrameLayout
            // if it's an image
            if (frameLayout.getChildAt(0) is ImageView) {
                val imageView = frameLayout.getChildAt(0) as ImageView
                attachedImageUris.add(imageView.tag as Uri)
            } else {
                // if it's a file
                val placeholder = frameLayout.getChildAt(0) as View
                attachedFilePaths.add(placeholder.tag as Uri)
            }
        }

        handleTextMessage(message, attachedImageUris, attachedFilePaths)
    }

    // utility method to handle sending text requests for normal UI messages and transcriptions
    // (from ChatAdapter - when transcribe button is clicked (for recordings listed in the chat and audio uploads), from AudioRecorder when recoding is done)
    // and here in Main - same functionality when Send button is clicked
    fun handleTextMessage(message: String, attachedImageUris: List<Uri> = listOf(), attachedFiles: List<Uri> = listOf()) {
        // Add message to chat
        editingMessagePosition?.let { position ->
            editMessageInChat(position, message, attachedImageUris, attachedFiles)
            startStreaming(message, position)
        } ?: run {
            addMessageToChat(message, attachedImageUris, attachedFiles)
            startStreaming(message)
        }
        resetInputArea()
        binding.characterHorizontalMainScrollView.visibility = View.GONE
    }

    // once message is edited - update it in chat
    private fun editMessageInChat(position: Int, message: String, attachedImageUris: List<Uri>, attachedFiles: List<Uri> = listOf()) {
        val chatItem = chatItems[position]
        chatItem.message = message
        chatItem.imageUris = attachedImageUris
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

    private fun startStreaming(userInput: String, responseItemPosition: Int? = null) {
        // collect chat history (needed to send it API to get whole context of chat)
        val chatHistory = chatItems.map {
            if (it.isUserMessage) {
                mapOf("role" to "user", "content" to it.message)
            } else {
                mapOf("role" to "assistant", "content" to it.message)
            }
        }

        val apiDataModel = APIDataModel(
            category = "text",
            action = "chat",
            userInput = mapOf(
                "prompt" to userInput,
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
            val responseItem = ChatItem("", false, aiCharacterImageResId = character?.imageResId)
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
                        // Add any logic you want to run when the stream ends
                    }
                }
            ),
            onError = { error ->
                runOnUiThread {
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
    fun addMessageToChat(message: String, attachedImageUris: List<Uri>, attachedFiles: List<Uri> = listOf()) {
        val chatItem = ChatItem(message = message, isUserMessage = true, imageUris = attachedImageUris, fileNames = attachedFiles)
        chatItems.add(chatItem)
        chatAdapter.notifyItemInserted(chatItems.size - 1)
        scrollToEnd()
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
                    fileAttachmentHandler.addFilePreview(uri)
                }
            },
            onFailure = {
                Toast.makeText(this, "Failed to capture image", Toast.LENGTH_SHORT).show()
            }
        )
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

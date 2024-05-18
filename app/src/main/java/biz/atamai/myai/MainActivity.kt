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

    private val apiUrl = "http://192.168.23.66:8000/chat"

    // needed for streaming
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

        // set status bar color (above app -where clock is)
        window.statusBarColor = ContextCompat.getColor(this, R.color.popupmenu_background)
    }

    private fun setupChatAdapter() {
        chatAdapter = ChatAdapter(chatItems) { position, message ->
            startEditingMessage(position, message) // Add this line
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
                if (count > 0 && before == 0) { // Detect the first character typed
                    manageBottomEditSection("show")
                }
                s?.let {
                    if (it.contains("@")) {
                        binding.characterHorizontalMainScrollView.visibility = View.VISIBLE
                        scrollToEnd()
                        // save original AI character - because new one will be set
                        // very important - that we have to change it only once - because if we chose different character this function is executed every time we type any character
                        // so originalAICharacter would be set many times (to new character) if we didn't have this check
                        if (originalAICharacter == null) {
                            originalAICharacter = ConfigurationManager.getTextAICharacter()
                        }
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
            val newText = StringBuilder(currentText)
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

        editingMessagePosition?.let { position ->
            editMessageInChat(position, message, attachedImageUris, attachedFilePaths)
        } ?: run {
            addMessageToChat(message, attachedImageUris, attachedFilePaths)
            startStreaming(message)
        }
        resetInputArea()
        // hide character selection area
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

    private fun startStreaming(userInput: String) {
        val apiDataModel = APIDataModel(
            category = "text",
            action = "chat",
            userInput = mapOf("prompt" to userInput),
            userSettings = mapOf(
                "text" to mapOf(
                    "temperature" to ConfigurationManager.getTextTemperature(),
                    "model" to ConfigurationManager.getTextModelName(),
                    "memory_limit" to ConfigurationManager.getTextMemorySize(),
                    "ai_character" to ConfigurationManager.getTextAICharacter(),
                    "streaming" to ConfigurationManager.getIsStreamingEnabled(),
                ),
                "audio" to mapOf(
                    "stability" to ConfigurationManager.getAudioStability(),
                    "similarity_boost" to ConfigurationManager.getAudioSimilarity()
                ),
                "speech" to mapOf(
                    "language" to ConfigurationManager.getSpeechLanguage(),
                    "temperature" to ConfigurationManager.getSpeechTemperature()
                ),
                "general" to mapOf(
                    "returnTestData" to ConfigurationManager.getUseTestData(),
                ),
            ),
            customerId = 1
        )

        val streamUrl = apiUrl

        val responseItem = ChatItem("", false)
        chatItems.add(responseItem)
        currentResponseItemPosition = chatItems.size - 1
        chatAdapter.notifyItemInserted(currentResponseItemPosition!!)
        scrollToEnd()

        StreamingResponseHandler({ chunk ->
            runOnUiThread {
                currentResponseItemPosition?.let { position ->
                    chatItems[position].message += chunk
                    chatAdapter.notifyItemChanged(position)
                    scrollToEnd()
                }
            }
        }, { error ->
            runOnUiThread {
                Toast.makeText(this, "Error: $error", Toast.LENGTH_LONG).show()
            }
        }, {}).startStreaming(streamUrl, apiDataModel)

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

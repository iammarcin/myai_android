package biz.atamai.myai

import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
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

    private val apiUrl = "http://192.168.23.66:8000/chatstream"

    // needed for streaming
    private var currentResponseItemPosition: Int? = null

    var useBluetoothIfConnected = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)

        audioRecorder = AudioRecorder(this).apply {
            useBluetoothIfConnected = this@MainActivity.useBluetoothIfConnected
        }
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
        characterManager.setupCharacterCards(binding)

        // set status bar color (above app -where clock is)
        window.statusBarColor = ContextCompat.getColor(this, R.color.popupmenu_background)
    }

    private fun setupChatAdapter() {
        chatAdapter = ChatAdapter(chatItems)
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
                binding.layoutRecord.visibility = View.GONE
                binding.btnSend.visibility = View.VISIBLE
                (binding.editTextMessage.layoutParams as LinearLayout.LayoutParams).weight = 0.7f
                (binding.rightAttachmentBar.layoutParams as LinearLayout.LayoutParams).weight = 0.3f
            } else {
                resetSendAndRecordArea()
            }
        }

        // main send button
        binding.btnSend.setOnClickListener {
            handleSendButtonClick()
        }

        // camera
        binding.btnCamera.setOnClickListener {
            cameraHandler.takePhoto()
        }
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

        addMessageToChat(message, attachedImageUris, attachedFilePaths)
        startStreaming(message)
        resetInputArea()
    }

    private fun startStreaming(userInput: String) {
        val apiDataModel = APIDataModel(
            action = "text",
            userInput = mapOf("prompt" to userInput),
            userSettings = mapOf("generator" to "openai"),
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
        resetSendAndRecordArea()
        // release focus of binding.editTextMessage
        binding.editTextMessage.clearFocus()
    }

    // those few functions will be used in other places - so additional dedicated function needed
    private fun resetSendAndRecordArea() {
        binding.layoutRecord.visibility = View.VISIBLE
        binding.btnSend.visibility = View.GONE
        (binding.editTextMessage.layoutParams as LinearLayout.LayoutParams).weight = 0.5f
        (binding.rightAttachmentBar.layoutParams as LinearLayout.LayoutParams).weight = 0.5f
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

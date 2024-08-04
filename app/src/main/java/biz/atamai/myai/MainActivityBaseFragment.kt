package biz.atamai.myai

// MainActivityBaseFragment.kt

import android.app.Activity
import androidx.fragment.app.activityViewModels
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import biz.atamai.myai.databinding.ActivityMainBinding
import biz.atamai.myai.databinding.ActivityMainChatStandardBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

abstract class MainActivityBaseFragment : Fragment(), MainHandler {

    protected lateinit var bindingMain: ActivityMainBinding
    protected lateinit var bindingMainBase: ActivityMainChatStandardBinding

    protected val chatSharedViewModel: MainActivityShared by activityViewModels()

    protected lateinit var chatHelper: ChatHelper
    protected val chatItems: MutableList<ChatItem> = mutableListOf()
    protected lateinit var chatAdapter: ChatAdapter

    lateinit var fileAttachmentHandler: FileAttachmentHandler
    private lateinit var cameraHandler: CameraHandler
    private lateinit var audioRecorder: AudioRecorder

    private lateinit var permissionsUtil: PermissionsUtil

    lateinit var characterManager: CharacterManager

    private lateinit var gpsLocationManager: GPSLocationManager

    private lateinit var audioPlayerManager: AudioPlayerManager

    // this is for chat scrolling - if user scrolls - we don't want to scroll to the end
    private var isUserScrolling = false

    // this will be used when mentioning (via @) AI characters for single message
    private var originalAICharacter: String? = null

    override val context: Context
        get() = requireContext()
    override val activity: AppCompatActivity
        get() = requireActivity() as AppCompatActivity

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        bindingMainBase = ActivityMainChatStandardBinding.inflate(inflater, container, false)
        bindingMainBase.viewModel = chatSharedViewModel
        bindingMainBase.lifecycleOwner = viewLifecycleOwner

        ConfigurationManager.init(context)

        fileAttachmentHandler = FileAttachmentHandler(this, ConfigurationManager.getAppModeApiUrl())
        //cameraHandler = CameraHandler(this, activityResultRegistry)
        audioRecorder = AudioRecorder(this, ConfigurationManager.getUseBluetooth(), ConfigurationManager.getAppModeApiUrl())

        // has to BEFORE chat adapter
        characterManager = CharacterManager(this)
        audioPlayerManager = AudioPlayerManager(this)


        setupChatAdapter()
        setupListeners()
        setupRecordButton()

        setupCamera()
        setupPermissions()

        chatHelper = ChatHelper(this, chatAdapter, chatItems)
        // this is needed - because chatHelper needs chatAdapter and vice versa
        // so first we initialize chatAdapter (without chatHelper as its not yet initialized) and later we set chatHelper to chatAdapter
        chatAdapter.setChatHelperHandler(chatHelper)

        DatabaseHelper.initialize(this, chatHelper)

        gpsLocationManager = GPSLocationManager(this)

        characterManager.setChatAdapterHandler(chatAdapter)
        // on character selection - update character name in chat and set temporary character for single message (when using @ in chat)
        characterManager.setupCharacterCards(bindingMain, bindingMainBase) { characterName ->
            chatHelper.insertCharacterName(characterName)
        }

        // set default character (assistant) and session name - in case there is some remaining from previous app run
        ConfigurationManager.setTextAICharacter("assistant")
        ConfigurationManager.setTextCurrentSessionName("New chat")

        // Initialize TopMenuHandler
        val topMenuHandler = TopMenuHandler(
            this,
            chatHelper,
            // below 2 functions must be in coroutine scope - because they are sending requests to DB and based on results different UI is displayed (different chat sessions)
            onFetchChatSessions = {
                CoroutineScope(Dispatchers.IO).launch {
                    DatabaseHelper.loadChatSessions()
                }
            },
            onSearchMessages = { query ->
                CoroutineScope(Dispatchers.IO).launch {
                    DatabaseHelper.sendDBRequest("db_search_messages", mapOf("search_text" to query))
                }
            }
        )
        topMenuHandler.setChatAdapterHandler(chatAdapter)
        topMenuHandler.setupTopMenus(bindingMain)

        // Observe changes in chatItems from ViewModel
        chatSharedViewModel.chatItems.observe(viewLifecycleOwner) { chatItems ->
            chatAdapter.submitList(chatItems)

            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed({
                scrollToEnd()
            }, 50)
        }

        return bindingMain.root
    }

    private fun setupChatAdapter() {
        chatAdapter = ChatAdapter(
            //chatItems,
            chatSharedViewModel.chatItems.value ?: mutableListOf(),
            ConfigurationManager.getAppModeApiUrl(),
            this,
            audioPlayerManager
        ) { position, message -> chatHelper.startEditingMessage(position, message) }

        bindingMainBase.chatContainer.layoutManager = LinearLayoutManager(context)
        bindingMainBase.chatContainer.adapter = chatAdapter
    }

    private fun setupListeners() {
        // this is listener for main chat container (taking most part of the screen)
        bindingMainBase.chatContainer.setOnTouchListener { _, _ ->
            // idea is that when edit text has focus - recording button etc disappears
            // so this is to clear the focus if we click somewhere on main screen
            bindingMainBase.editTextMessage.clearFocus()
            // hide mobile keyboard
            chatHelper.hideKeyboard(bindingMainBase.editTextMessage)
            // Call performClick
            // this is necessary! explained in CustomRecyclerView
            bindingMainBase.chatContainer.performClick()
            false
        }

        // attach button
        bindingMainBase.btnAttach.setOnClickListener {
            fileAttachmentHandler.openFileChooser()
        }

        // for situation where we start typing in edit text - we want other stuff to disappear
        bindingMainBase.editTextMessage.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                chatHelper.manageBottomEditSection("show")
            } else {
                // but if there is text already - lets leave it
                if (bindingMainBase.editTextMessage.text.isEmpty()) {
                    chatHelper.manageBottomEditSection("hide")
                }
                chatHelper.hideKeyboard(view)
            }
        }

        // when user starts typing and uses @ - show character selection area
        bindingMainBase.editTextMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                //if user starts typing - show bottom edit section (there is a case after submit - when i start typing - letters appear and i don't need to click on edit text)
                if (count > 0 && before == 0) {
                    chatHelper.manageBottomEditSection("show")
                }
                s?.let {
                    val cursorPosition = bindingMainBase.editTextMessage.selectionStart
                    val atIndex = s.lastIndexOf("@", cursorPosition - 1)
                    if (atIndex != -1) {
                        val query = s.substring(atIndex + 1, cursorPosition).trim()
                        bindingMain.characterMainView.visibility = View.VISIBLE
                        bindingMain.characterFilterLayout.visibility = View.GONE
                        bindingMain.checkboxShowFilters.visibility = View.GONE
                        chatHelper.scrollToEnd()

                        // while triggering search for new AI character - save original AI character - because new one will be set
                        // very important - that we have to change it only once - because if we chose different character this function is executed every time we type any character
                        // so originalAICharacter would be set many times (to new character) if we didn't have this check
                        if (originalAICharacter == null) {
                            originalAICharacter = ConfigurationManager.getTextAICharacter()
                        }
                        characterManager.filterCharacters(bindingMain, bindingMainBase, query) { characterName ->
                            chatHelper.insertCharacterName(characterName)
                        }
                    } else {
                        bindingMain.characterMainView.visibility = View.GONE
                    }
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // main send button
        /*bindingMainBase.btnSend.setOnClickListener {
            //TODO
            //handleSendButtonClick()
        }*/

        bindingMainBase.btnSend.setOnClickListener {
            val message = bindingMainBase.editTextMessage.text.toString()
            if (message.isNotEmpty()) {
                //TODO?
                //chatHelper.sendMessage(message)
            }
        }

        // camera
        bindingMainBase.btnCamera.setOnClickListener {
            cameraHandler.takePhoto()
        }

        // Set up new chat button
        bindingMain.newChatButton.setOnClickListener {
            chatHelper.resetChat()
            bindingMainBase.btnShareLocation.visibility = View.GONE

            characterManager.initializeCharacterView(bindingMain)

            ConfigurationManager.setTextAICharacter("assistant")
            ConfigurationManager.setTextCurrentSessionName("New chat")
            originalAICharacter = null
            chatHelper.setCurrentDBSessionID("")
        }

        // scrollview with sessions list on top left menu
        val scrollView = bindingMain.topLeftMenuChatSessionListScrollView
        val swipeRefreshLayout = bindingMain.swipeRefreshLayout
        // Check if user has scrolled to the top and requested refresh
        swipeRefreshLayout.setOnRefreshListener {
            // db_search_messages - it is not exactly search of course - but we trigger it with empty search text - so it searches for everything
            // + it has everything we need here - reset chat, load chat sessions etc
            CoroutineScope(Dispatchers.IO).launch {
                DatabaseHelper.sendDBRequest("db_search_messages", mapOf("search_text" to ""))
            }
            swipeRefreshLayout.isRefreshing = false
        }
        scrollView.viewTreeObserver.addOnScrollChangedListener {
            val view = scrollView.getChildAt(scrollView.childCount - 1) as View
            val diff = (view.bottom - (scrollView.height + scrollView.scrollY))

            if (diff == 0) {
                // User has scrolled to the bottom
                DatabaseHelper.loadMoreChatSessions()
            }
        }

        // scroll listener for chat container (to detect if user is scrolling)
        bindingMainBase.chatContainer.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                when (newState) {
                    RecyclerView.SCROLL_STATE_DRAGGING -> isUserScrolling = true
                    RecyclerView.SCROLL_STATE_IDLE -> isUserScrolling = false
                }
            }
        })

        // GPS button
        bindingMainBase.btnShareLocation.setOnClickListener {
            if (gpsLocationManager.areLocationServicesEnabled()) {
                gpsLocationManager.showGPSAccuracyDialog(bindingMainBase.imagePreviewContainer)
            } else {
                Toast.makeText(context, "GPS Location services are disabled", Toast.LENGTH_SHORT).show()
                // Optionally, you can open the location settings
                //val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                //startActivity(intent)
            }
        }
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
                Toast.makeText(context, "Failed to capture image", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun handleSendButtonClick() {
        val message = bindingMainBase.editTextMessage.text.toString()
        if (message.isEmpty()) {
            return
        }
        val attachedImageLocations = mutableListOf<String>()
        val attachedFilePaths = mutableListOf<Uri>()

        for (i in 0 until bindingMainBase.imagePreviewContainer.childCount) {
            val frameLayout = bindingMainBase.imagePreviewContainer.getChildAt(i) as FrameLayout
            // if it's an image
            if (frameLayout.getChildAt(0) is ImageView) {
                val imageView = frameLayout.getChildAt(0) as ImageView
                if (imageView.tag == null) {
                    Toast.makeText(context, "Problems with uploading files 2. Try again", Toast.LENGTH_SHORT).show()
                    continue
                }
                attachedImageLocations.add(imageView.tag as String)
            } else {
                // if it's a file
                val placeholder = frameLayout.getChildAt(0) as View
                val tag = placeholder.tag
                if (tag is Uri) {
                    // in most cases it will be URI
                    attachedFilePaths.add(tag)
                } else if (tag is String && tag.startsWith("http")) {
                    // it can be URL (for example when we earlier upload to S3)
                    attachedFilePaths.add(Uri.parse(tag))
                } else {
                    // Handle other cases if needed
                }
            }
        }

        chatSharedViewModel.handleTextMessage(
            message,
            attachedImageLocations,
            attachedFilePaths,
            // TODO  - check why false
            false, // Assuming gpsLocationMessage is false
            getMainCharacterManager(),
            chatHelper,
            chatAdapter,
            getDatabaseHelper(),
            getConfigurationManager(),
            null,
            { toastMessage -> Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show() },
            { bindingMain.characterMainView.visibility = View.GONE },
            { scrollToEnd() },
            { showProgressBar(it) },
            { hideProgressBar(it) },
            { position, chunk ->
                chatSharedViewModel.chatItems.value?.get(position)?.message += chunk
            },
            onNotifyItemInserted = { position ->
                val updatedList = chatAdapter.currentList.toMutableList()
                updatedList.add(position, chatItems[position])
                chatAdapter.submitList(updatedList)
            },
            onNotifyItemChanged = { position ->
                val updatedList = chatAdapter.currentList.toMutableList()
                updatedList[position] = chatItems[position]
                chatAdapter.submitList(updatedList)
            }
        )
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

    // needed for chatInterfaces
    // Implement MainHandler methods
    override fun getMainActivity(): Activity = requireActivity()

    override fun getMainBinding(): ActivityMainBinding {
        return bindingMain
    }

    override fun getMainBaseBinding(): ActivityMainChatStandardBinding {
        return bindingMainBase
    }
    //override fun getMainBinding(): ActivityMainBinding = binding as ActivityMainBinding
    //override fun getMainBaseBinding(): ActivityMainChatStandardBinding = binding as ActivityMainChatStandardBinding

    override fun getMainBindingContext(): Context = context

    /*override fun getMainBindingContext(): Context {
        return binding.root.context
    }*/

    override val chatItemsList: MutableList<ChatItem>
        get() = chatItems

    override val mainLayoutInflaterInstance: LayoutInflater
        get() = layoutInflater

    override fun getCurrentAICharacter(): String {
        return ConfigurationManager.getTextAICharacter()
    }

    override fun createToastMessage(message: String, duration: Int) {
        Toast.makeText(context, message, duration).show()
    }

    override fun getImagePreviewContainer(): LinearLayout = bindingMainBase.imagePreviewContainer

    override fun getScrollViewPreview(): HorizontalScrollView = bindingMainBase.scrollViewPreview

    override fun registerForActivityResult(contract: ActivityResultContracts.StartActivityForResult, callback: (ActivityResult) -> Unit): ActivityResultLauncher<Intent> {
        return registerForActivityResult(contract, callback)
    }

    override fun checkSelfPermission(permission: String): Int {
        return ContextCompat.checkSelfPermission(context, permission)
    }

    override fun requestAllPermissions(permissions: Array<String>, requestCode: Int) {
        //ActivityCompat.requestPermissions(permissions, requestCode)
        //ActivityCompat.requestPermissions(this, permissions, requestCode)
        ActivityCompat.requestPermissions(requireActivity(), permissions, requestCode)
    }

    override fun getMainCharacterManager(): CharacterManager {
        return characterManager
    }

    override fun getFullCharacterData(characterName: String): CharacterManager.Character {
        return characterManager.getCharacterByNameForAPI(characterName)!!
    }

    override fun resizeImage(filePath: String, maxDimension: Int): File? {
        return ImageUtils.resizeImage(filePath, maxDimension)
    }

    override fun releaseMediaPlayer() {
        audioPlayerManager.releaseMediaPlayer()
    }

    override fun getDatabaseHelper(): DatabaseHelper {
        return DatabaseHelper
    }

    override fun getConfigurationManager(): ConfigurationManager {
        return ConfigurationManager
    }

    override fun getIsUserScrolling(): Boolean {
        return isUserScrolling
    }

    // PROGRESS BAR
    override fun showProgressBar(message: String) {
        bindingMainBase.progressContainer.visibility = View.VISIBLE
        val currentText = bindingMainBase.progressText.text.toString()
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
        bindingMainBase.progressText.text = newText
    }
    override fun hideProgressBar(message: String) {
        val currentText = bindingMainBase.progressText.text.toString()
        val newText = currentText.replace(message, "").trim()  // Remove the specified message

        if (newText.isEmpty()) {
            bindingMainBase.progressContainer.visibility = View.GONE
        }

        bindingMainBase.progressText.text = newText
    }

    // AUDIO RECORDER
    private fun setupRecordButton() {
        bindingMainBase.btnRecord.setOnClickListener {
            audioRecorder.handleRecordButtonClick()
        }
    }

    override fun setRecordButtonImageResource(resourceId: Int) {
        bindingMainBase.btnRecord.setImageResource(resourceId)
    }

    // utility method to handle sending text requests for normal UI messages and transcriptions
    // (from ChatAdapter - for regenerate AI message or when transcribe button is clicked (for recordings listed in the chat and audio uploads), from AudioRecorder when recoding is done)
    // and here in Main - same functionality when Send button is clicked
    // gpsLocationMessage - if true - it is GPS location message - so will be handled differently in chatAdapter (will show GPS map button and probably few other things)
    override fun handleTextMessage(message: String, attachedImageLocations: List<String>, attachedFiles: List<Uri>, gpsLocationMessage: Boolean) {
        chatSharedViewModel.handleTextMessage(
            message,
            attachedImageLocations,
            attachedFiles,
            gpsLocationMessage,
            getMainCharacterManager(),
            chatHelper,
            chatAdapter,
            getDatabaseHelper(),
            getConfigurationManager(),
            null,
            { toastMessage -> Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show() },
            { bindingMain.characterMainView.visibility = View.GONE },
            { scrollToEnd() },
            { showProgressBar(it) },
            { hideProgressBar(it) },
            { position, chunk ->
                chatSharedViewModel.chatItems.value?.get(position)?.message += chunk
            },
            onNotifyItemInserted = { position ->
                val updatedList = chatAdapter.currentList.toMutableList()
                updatedList.add(position, chatItems[position])
                chatAdapter.submitList(updatedList)
            },
            onNotifyItemChanged = { position ->
                val updatedList = chatAdapter.currentList.toMutableList()
                updatedList[position] = chatItems[position]
                chatAdapter.submitList(updatedList)
            }
        )
    }

    // sending data to chat adapter
    // used from multiple places (main, audio recorder, file attachment)
    override fun addMessageToChat(message: String, attachedImageLocations: List<String>, attachedFiles: List<Uri>, gpsLocationMessage: Boolean): ChatItem {
        return chatSharedViewModel.addMessageToChat(message, attachedImageLocations, attachedFiles, gpsLocationMessage)
    }

    private fun scrollToEnd() {
        if (getIsUserScrolling()) {
            return
        }
        val layoutManager = bindingMainBase.chatContainer.layoutManager as LinearLayoutManager
        val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()

        if (lastVisiblePosition >= chatItemsList.size - 2) {
            bindingMainBase.chatContainer.post {
                bindingMainBase.chatContainer.scrollToPosition(chatItemsList.size - 1)
            }
        }

        /*
        // slight delay to smooth scrolling on adding chunks to UI
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            currentResponseItemPosition?.let { _ ->
                chatHelper.scrollToEnd()
            }
        }, 50)
        */
    }

    override fun onPause() {
        super.onPause()
        releaseMediaPlayer()
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseMediaPlayer()  // Ensure all media players are released when the activity is destroyed
        audioRecorder.release()  // Release resources held by AudioRecorder
    }

    /*
    private fun handleSendButtonClick() {
        val message = binding.editTextMessage.text.toString()
        if (message.isEmpty()) {
            return
        }
        val attachedImageLocations = mutableListOf<String>()
        val attachedFilePaths = mutableListOf<Uri>()

        for (i in 0 until binding.imagePreviewContainer.childCount) {
            val frameLayout = binding.imagePreviewContainer.getChildAt(i) as FrameLayout
            // if it's an image
            if (frameLayout.getChildAt(0) is ImageView) {
                val imageView = frameLayout.getChildAt(0) as ImageView
                if (imageView.tag == null) {
                    Toast.makeText(this, "Problems with uploading files 2. Try again", Toast.LENGTH_SHORT).show()
                    continue
                }
                attachedImageLocations.add(imageView.tag as String)
            } else {
                // if it's a file
                val placeholder = frameLayout.getChildAt(0) as View
                val tag = placeholder.tag
                if (tag is Uri) {
                    // in most cases it will be URI
                    attachedFilePaths.add(tag)
                } else if (tag is String && tag.startsWith("http")) {
                    // it can be URL (for example when we earlier upload to S3)
                    attachedFilePaths.add(Uri.parse(tag))
                } else {
                    // Handle other cases if needed
                }
            }
        }

        handleTextMessage(message, attachedImageLocations, attachedFilePaths)
    }

     */

}
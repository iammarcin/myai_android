// ChatHelper.kt

package biz.atamai.myai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.squareup.picasso.Picasso
import org.json.JSONArray
import org.json.JSONObject

/// TODO LIMITATION OF createNewSessionFromHere - read below
// but mainly it can lead to errors if we try to edit message that is just restored

class ChatHelper(
    private val mainHandler: MainHandler,
    private val chatAdapter: ChatAdapter,
    private val chatItems: MutableList<ChatItem>,
    private val configurationManager: ConfigurationManager,
    private val characterManager: CharacterManager,
) : ChatHelperHandler {
    // same string set in backend in config.py
    private val ERROR_MESSAGE_FOR_TEXT_GEN = "Error in Text Generator. Try again!"

    // This will store the position of the message being edited
    private var editingMessagePosition: Int? = null

    // this is to store DB session ID - so when submitting/updating DB messages - they will be assigned to proper session
    private var currentDBSessionID: String? = ""

    // helper functions for editingMessagePosition
    fun getEditingMessagePosition(): Int? {
        return editingMessagePosition
    }
    override fun setEditingMessagePosition(position: Int?) {
        editingMessagePosition = position
    }



    // helper functions for currentDBSessionID
    override fun getCurrentDBSessionID(): String? {
        return currentDBSessionID
    }
    override fun setCurrentDBSessionID(sessionID: String) {
        currentDBSessionID = sessionID
    }

    // edit any user message
    fun startEditingMessage(position: Int, message: String) {
        val chatItem = chatItems[position]
        setEditingMessagePosition(position)
        mainHandler.getMainBinding().editTextMessage.setText(message)
        mainHandler.getMainBinding().editTextMessage.requestFocus()
        mainHandler.getMainBinding().editTextMessage.setSelection(message.length)
        mainHandler.getMainBinding().editTextMessage.maxLines = 10

        // Show the keyboard
        showKeyboard(mainHandler.getMainBinding().editTextMessage)

        // Show the send button and hide the record button
        manageBottomEditSection("show")

        // Clear existing previews
        mainHandler.getMainBinding().imagePreviewContainer.removeAllViews()

        // Add image previews
        if (chatItem.imageLocations.isNotEmpty()) {
            mainHandler.getMainBinding().scrollViewPreview.visibility = View.VISIBLE
            for (imageUrl in chatItem.imageLocations) {
                val imageView = ImageView(mainHandler.context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    adjustViewBounds = true
                    Picasso.get().load(imageUrl).into(this)
                    tag = imageUrl
                }

                // Wrap ImageView in FrameLayout
                val frameLayout = FrameLayout(mainHandler.context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.MATCH_PARENT
                    ).also {
                        it.marginEnd = 8.dpToPx(context)
                    }
                    addView(imageView)
                }

                mainHandler.getMainBinding().imagePreviewContainer.addView(frameLayout)
            }
        } else {
            mainHandler.getMainBinding().scrollViewPreview.visibility = View.GONE
        }
    }

    // when dealing with hasFocus etc for edit text - if we lose hasFocus - keyboard remained on the screen
    fun hideKeyboard(view: View) {
        val inputMethodManager = mainHandler.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }
    private fun showKeyboard(view: View) {
        if (view.requestFocus()) {
            val inputMethodManager2 = mainHandler.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager2.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
        }
    }

    // once message is edited - update it in chat
    fun editMessageInChat(position: Int, message: String, attachedImageLocations: List<String>, attachedFiles: List<Uri> = listOf()) {
        val chatItem = chatItems[position]
        chatItem.message = message
        chatItem.imageLocations = attachedImageLocations
        chatItem.fileNames = attachedFiles
        chatAdapter.notifyItemChanged(position)
    }

    // when new chat is used - clear everything
    fun resetChat() {
        val size = chatItems.size
        chatItems.clear()
        chatAdapter.notifyItemRangeRemoved(0, size)
        resetInputArea()
        mainHandler.getMainBinding().characterHorizontalMainScrollView.visibility = View.VISIBLE
        mainHandler.releaseMediaPlayer()
    }

    // once we have all the data regarding session - we restore it in chat
    override fun restoreSessionData(sessionData: JSONObject) {
        println("RESTORE SESSION DATA: $sessionData")
        val chatHistoryString = sessionData.getString("chat_history")
        val chatHistory = JSONArray(chatHistoryString)

        println("CHAT HISTORY: $chatHistory")
        resetChat()

        for (i in 0 until chatHistory.length()) {
            val chatItemJson = chatHistory.getJSONObject(i)

            // Extract message, imageLocations, and fileNames
            val message = chatItemJson.getString("message")
            val imageLocations = chatItemJson.getJSONArray("imageLocations").let { jsonArray ->
                List(jsonArray.length()) { index -> jsonArray.getString(index) }
            }
            val fileNames = chatItemJson.getJSONArray("fileNames").let { jsonArray ->
                List(jsonArray.length()) { index ->
                    val fileName = jsonArray.getString(index)
                    if (fileName.isNotEmpty() && fileName != "{}") {
                        Uri.parse(fileName)
                    } else {
                        null
                    }
                }.filterNotNull()
            }

            // this is not great but well
            // if there is generate error from API (ERROR_MESSAGE_FOR_TEXT_GEN) - we skip it
            // so we need to skip also user's message that is right before it
            // if not it can cause some troubles here and there
            // (for example editing this message will overwrite next user message with AI response)
            if (i + 1 < chatHistory.length()) {
                val nextMessage = chatHistory.getJSONObject(i + 1).getString("message")
                if (nextMessage == ERROR_MESSAGE_FOR_TEXT_GEN) {
                    continue
                }
            }

            // Check if message is empty and imageLocations and fileNames are also empty
            if ((message.isEmpty() || message == ERROR_MESSAGE_FOR_TEXT_GEN ) && imageLocations.isEmpty() && fileNames.isEmpty()) {
                continue
            }

            // if message is empty, but files are present - it means that it is attached audio file or recording that was transcribed... so we don't need it
            if (message.isEmpty() && fileNames.isNotEmpty()) {
                continue
            }

            // Create and add ChatItem if it passes the check
            val chatItem = ChatItem(
                message = message,
                isUserMessage = chatItemJson.getBoolean("isUserMessage"),
                imageLocations = imageLocations,
                fileNames = fileNames,
                aiCharacterName = chatItemJson.optString("aiCharacterName", ""),
                isTTS = chatItemJson.optBoolean("isTTS", false),
                isGPSLocationMessage = chatItemJson.optBoolean("isGPSLocationMessage", false)
            )

            // Conditionally set messageId if it exists in chatItemJson
            if (chatItemJson.has("messageId") && chatItemJson.getInt("messageId") != 0) {
                chatItem.messageId = chatItemJson.getInt("messageId")
            }

            chatItems.add(chatItem)
        }

        configurationManager.setTextAICharacter(sessionData.getString("ai_character_name"))

        setCurrentDBSessionID(sessionData.getString("session_id") ?: "")
        mainHandler.getMainBinding().characterHorizontalMainScrollView.visibility = View.GONE

        mainHandler.getMainBinding().btnShareLocation.visibility = View.GONE
        if (characterManager.getCharacterByName(configurationManager.getTextAICharacter())?.showGPSButton == true) {
            mainHandler.getMainBinding().btnShareLocation.visibility = View.VISIBLE
        }

        chatAdapter.notifyItemRangeInserted(0, chatItems.size)
        scrollToEnd()
    }

    // in popup menu when we click on AI or user message there are few options... one is newSessionFromHere
    // its where we create new session from selected message (and copy all previous)
    // IMPORTANT - it is way simplified version - as we don't save those new messages in DB - we just recreate chatItems
    // maybe one day we can work on it - downside is that we cannot edit properly previous messages
    override fun createNewSessionFromHere(position: Int) {
        val selectedChatItems = chatItems.subList(0, position + 1).toMutableList()
        resetChat()
        // resetting DB session - it should create new one later in backend
        setCurrentDBSessionID("")
        mainHandler.getMainBinding().characterScrollView.visibility = View.GONE

        // Add the selected chat items to the new session
        for (chatItem in selectedChatItems) {
            chatItem.messageId = null
            chatItems.add(chatItem)
            chatAdapter.notifyItemInserted(chatItems.size - 1)
        }
        scrollToEnd()
    }


    override fun scrollToEnd() {
        //ensure that the scroll operation happens after the RecyclerView has completed any pending layout passes (to smooth scrolling)
        mainHandler.getMainBinding().chatContainer.post {
            mainHandler.getMainBinding().chatContainer.scrollToPosition(chatItems.size - 1)
        }
    }

    // reset after submission - clean input text, images preview and scroll view in general
    fun resetInputArea() {
        mainHandler.getMainBinding().editTextMessage.setText("")
        mainHandler.getMainBinding().imagePreviewContainer.removeAllViews()
        mainHandler.getMainBinding().scrollViewPreview.visibility = View.GONE
        manageBottomEditSection("hide")
        // release focus of binding.editTextMessage
        mainHandler.getMainBinding().editTextMessage.clearFocus()
    }

    // show or hide bottom edit section (manage properly edit text and buttons)
    fun manageBottomEditSection(action: String) {
        when (action) {
            "show" -> {
                mainHandler.getMainBinding().layoutRecord.visibility = View.GONE
                mainHandler.getMainBinding().btnSend.visibility = View.VISIBLE
                (mainHandler.getMainBinding().editTextMessage.layoutParams as LinearLayout.LayoutParams).weight = 0.7f
                (mainHandler.getMainBinding().rightAttachmentBar.layoutParams as LinearLayout.LayoutParams).weight = 0.3f
            }
            "hide" -> {
                mainHandler.getMainBinding().layoutRecord.visibility = View.VISIBLE
                mainHandler.getMainBinding().btnSend.visibility = View.GONE
                (mainHandler.getMainBinding().editTextMessage.layoutParams as LinearLayout.LayoutParams).weight = 0.55f
                (mainHandler.getMainBinding().rightAttachmentBar.layoutParams as LinearLayout.LayoutParams).weight = 0.7f
            }
        }
    }

    // used when user types @ - which opens character selection area... and then this function is triggered upon selection
    fun insertCharacterName(characterName: String) {
        val currentText = mainHandler.getMainBinding().editTextMessage.text.toString()
        val cursorPosition = mainHandler.getMainBinding().editTextMessage.selectionStart
        val atIndex = currentText.lastIndexOf("@", cursorPosition - 1)

        if (atIndex != -1) {
            // Remove everything after the '@' character up to the current cursor position
            val newText = StringBuilder(currentText)
                .delete(atIndex + 1, cursorPosition)
                .insert(atIndex + 1, "$characterName ")
                .toString()
            mainHandler.getMainBinding().editTextMessage.setText(newText)
            mainHandler.getMainBinding().editTextMessage.setSelection(atIndex + characterName.length + 2) // Position cursor after the character name
        }
    }

    override fun shareGPSLocation(latitude: Double, longitude: Double) {
        //val uri = Uri.parse("geo:${latitude},${longitude}")
        val uri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        if (intent.resolveActivity(mainHandler.context.packageManager) != null) {
            mainHandler.context.startActivity(intent)
        } else {
            mainHandler.createToastMessage("No application available to share location")
        }
    }

    private fun Int.dpToPx(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }

}

package biz.atamai.myai

import android.content.Context
import android.net.Uri
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import biz.atamai.myai.databinding.ActivityMainBinding
import com.squareup.picasso.Picasso
import org.json.JSONArray
import org.json.JSONObject

class ChatHelper(
    private val context: Context,
    private val binding: ActivityMainBinding,
    private val chatAdapter: ChatAdapter,
    private val chatItems: MutableList<ChatItem>,
    private var currentDBSessionID: String,
) {
    // same string set in backend in config.py
    private val ERROR_MESSAGE_FOR_TEXT_GEN = "Error in Text Generator. Try again!"

    // This will store the position of the message being edited
    private var editingMessagePosition: Int? = null

    // helper functions for editingMessagePosition
    fun getEditingMessagePosition(): Int? {
        return editingMessagePosition
    }
    fun clearEditingMessagePosition() {
        editingMessagePosition = null
    }

    // edit any user message
    fun startEditingMessage(position: Int, message: String) {
        val chatItem = chatItems[position]
        editingMessagePosition = position
        binding.editTextMessage.setText(message)
        binding.editTextMessage.requestFocus()
        binding.editTextMessage.setSelection(message.length)
        binding.editTextMessage.maxLines = 10

        // Show the send button and hide the record button
        manageBottomEditSection("show")

        // Clear existing previews
        binding.imagePreviewContainer.removeAllViews()

        // Add image previews
        if (chatItem.imageLocations.isNotEmpty()) {
            binding.scrollViewPreview.visibility = View.VISIBLE
            for (imageUrl in chatItem.imageLocations) {
                val imageView = ImageView(context).apply {
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
                val frameLayout = FrameLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.MATCH_PARENT
                    ).also {
                        it.marginEnd = 8.dpToPx(context)
                    }
                    addView(imageView)
                }

                binding.imagePreviewContainer.addView(frameLayout)
            }
        } else {
            binding.scrollViewPreview.visibility = View.GONE
        }
    }

    // when dealing with hasFocus etc for edit text - if we lose hasFocus - keyboard remained on the screen
    fun hideKeyboard(view: View) {
        val inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
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
        binding.characterHorizontalMainScrollView.visibility = View.VISIBLE
    }

    // once we have all the data regarding session - we restore it in chat
    fun restoreSessionData(sessionData: JSONObject) {
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

            // Create and add ChatItem if it passes the check
            val chatItem = ChatItem(
                message = message,
                isUserMessage = chatItemJson.getBoolean("isUserMessage"),
                imageLocations = imageLocations,
                fileNames = fileNames,
                aiCharacterName = chatItemJson.optString("aiCharacterName", ""),
                aiCharacterImageResId = chatItemJson.optInt("aiCharacterImageResId", R.drawable.brainstorm_assistant)
            )
            chatItems.add(chatItem)
        }

        currentDBSessionID = sessionData.getString("session_id") ?: ""
        binding.characterHorizontalMainScrollView.visibility = View.GONE

        chatAdapter.notifyItemRangeInserted(0, chatItems.size)
        scrollToEnd()
    }

    fun scrollToEnd() {
        binding.chatContainer.scrollToPosition(chatItems.size - 1)
    }

    // reset after submission - clean input text, images preview and scroll view in general
    fun resetInputArea() {
        binding.editTextMessage.setText("")
        binding.imagePreviewContainer.removeAllViews()
        binding.scrollViewPreview.visibility = View.GONE
        manageBottomEditSection("hide")
        // release focus of binding.editTextMessage
        binding.editTextMessage.clearFocus()
    }

    // show or hide bottom edit section (manage properly edit text and buttons)
    fun manageBottomEditSection(action: String) {
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

    // used when user types @ - which opens character selection area... and then this function is triggered upon selection
    fun insertCharacterName(characterName: String) {
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

    private fun Int.dpToPx(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }

}

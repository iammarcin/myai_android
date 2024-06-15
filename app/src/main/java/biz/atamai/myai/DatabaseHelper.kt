// DatabaseHelper.kt

package biz.atamai.myai

import android.app.Dialog
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.view.GravityCompat
import biz.atamai.myai.databinding.TopLeftMenuChatSessionItemBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// sealed class because same we can have different type of DBResponses
sealed class DBResponse {
    data class SessionId(val sessionId: String) : DBResponse()
    data class MessageId(val messageId: Int) : DBResponse()
    data class MessageIds(val userMessageId: Int, val aiMessageId: Int) : DBResponse()
}

object DatabaseHelper {
    private lateinit var mainHandler: MainHandler
    private lateinit var chatHelperHandler: ChatHelperHandler

    // these 3 will be needed for pagination of session list on top left menu
    private var isLoading = false
    private var limit = 15
    private var offset = 0

    // Add this flag to check if sessions have been loaded (to avoid adding them to the list multiple times)
    private var sessionsLoaded = false

    fun initialize(mainHandler: MainHandler, chatHelperHandler: ChatHelperHandler) {
        this.mainHandler = mainHandler
        this.chatHelperHandler = chatHelperHandler
    }

    // (DBResponse) - callback type (it's sealed class set above)
    suspend fun sendDBRequest(action: String, userInput: Map<String, Any> = mapOf(), callback: ((DBResponse) -> Unit)? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            // serialization of data (uri -> string) explained in ChatItem
            val serializableChatItems = mainHandler.chatItemsList.map { it.toSerializableMap() }

            val apiDataModel = APIDataModel(
                category = "provider.db",
                action = action,
                userInput = userInput + ("chat_history" to serializableChatItems),
                userSettings = ConfigurationManager.getSettingsDict(),
                customerId = 1,
            )

            val dbUrl = ConfigurationManager.getAppModeApiUrl() + "api/db"

            val handler = ResponseHandler(
                handlerType = HandlerType.NonStreaming(
                    onResponseReceived = { response ->
                        println("DB RESPONSE: $response")
                        CoroutineScope(Dispatchers.Main).launch {
                            handleDBResponse(action, response, callback)
                        }
                    }
                ),
                onError = { error ->
                    println("!!!!!!! DB Error")
                    println(error)
                    CoroutineScope(Dispatchers.Main).launch {
                        mainHandler.createToastMessage("Error with DB operation")
                    }
                },
                authToken = ConfigurationManager.getAuthTokenForBackend()
            )

            handler.sendRequest(dbUrl, apiDataModel)
        }
    }

    private fun handleDBResponse(action: String, response: String, callback: ((DBResponse) -> Unit)? = null) {
        val jsonResponse = JSONObject(response)

        when (action) {
            "db_new_session" -> {
                println("Db_new_session response: $response")
                val sessionId = jsonResponse.getJSONObject("message").getString("result")
                chatHelperHandler.setCurrentDBSessionID(sessionId)

                callback?.invoke(DBResponse.SessionId(sessionId))
            }
            "db_all_sessions_for_user", "db_search_messages" -> {
                if (action == "db_search_messages")
                    sessionsLoaded = false
                val sessions = parseSessions(response)
                displayChatSessions(sessions, action)
            }
            "db_get_user_session" -> {
                val sessionData = jsonResponse.getJSONObject("message").getJSONObject("result")
                chatHelperHandler.restoreSessionData(sessionData)
            }
            "db_edit_message" -> {
                val messageContent = jsonResponse.getJSONObject("message").getJSONObject("result")
                val aiMessageId = messageContent.getString("aiMessageId").toInt()

                callback?.invoke(DBResponse.MessageId(aiMessageId))
            }
            "db_new_message" -> {
                val messageContent = jsonResponse.getJSONObject("message").getJSONObject("result")
                val userMessageId = messageContent.getString("userMessageId").toInt()
                val aiMessageId = messageContent.getString("aiMessageId").toInt()

                // if current DB session is empty it means that its new chat, so we have to set it - so messages are assigned to proper session in DB
                // and in fact db_new_message in such case should return additional value - session_id
                if (chatHelperHandler.getCurrentDBSessionID().isNullOrEmpty())
                    chatHelperHandler.setCurrentDBSessionID(messageContent.getString("sessionId"))

                // this returns 2 values - userMessageId and aiMessageId
                callback?.invoke(DBResponse.MessageIds(userMessageId, aiMessageId))
            }
        }
    }

    suspend fun addNewOrEditDBMessage(method: String, userMessage: ChatItem, aiResponse: ChatItem?) {
        // there might be case (when character has autoResponse = false , so we're only collecting data)
        // that there is no aiResponse - but we still need to save user message
        val userInput = mutableMapOf<String, Any>(
            "customer_id" to 1,
            "session_id" to (chatHelperHandler.getCurrentDBSessionID() ?: ""),
            "userMessage" to mapOf(
                "sender" to "User",
                "message" to userMessage.message,
                "message_id" to userMessage.messageId,
                "image_locations" to userMessage.imageLocations,
                "file_locations" to userMessage.fileNames,
            ),
            "chat_history" to mainHandler.chatItemsList
        )

        aiResponse?.let {
            userInput["aiResponse"] = mapOf(
                "sender" to "AI",
                "message" to it.message,
                "message_id" to it.messageId,
                "image_locations" to it.imageLocations,
                "file_locations" to it.fileNames
            )
        }

        sendDBRequest(method, userInput) { response ->
            // if its new message - update message id in chatItem
            // if its edit - we dont need to
            if (method == "db_new_message") {
                if (response is DBResponse.MessageIds) {
                    val (userMessageId, aiMessageId) = response
                    userMessage.messageId = userMessageId
                    // if its 0 - it means there was problem with text generation
                    if (aiMessageId > 0) {
                        aiResponse?.messageId = aiMessageId
                    }
                }
                // if its not new message (so we edit) - but we don't see message_id (because there was API error etc) - we generate new message id on the backend (and here we overwrite it in ChatItem)
            } else if (method == "db_edit_message" && response is DBResponse.MessageId) {
                if (response.messageId > 0)
                    aiResponse?.messageId = response.messageId
            }
        }
    }

    private suspend fun fetchChatSessions(limit: Int = 30, offset: Int = 0) {
        sendDBRequest(
            action = "db_all_sessions_for_user",
            userInput = mapOf("limit" to limit, "offset" to offset)
        ) { _ ->
            // handle response if necessary
        }
    }

    fun loadChatSessions() {
        println("Load chat sessions. sessionsLoaded: $sessionsLoaded")
        if (sessionsLoaded) return
        CoroutineScope(Dispatchers.Main).launch {
            isLoading = true
            fetchChatSessions(limit, offset)
            isLoading = false
            sessionsLoaded = true
        }
    }

    fun loadMoreChatSessions() {
        offset += limit
        sessionsLoaded = false
        loadChatSessions()
    }

    // upon receiving data from DB - we parse session data to later display them
    // for the moment used in top left menu
    private fun parseSessions(response: String): List<ChatSessionForTopLeftMenu> {
        val jsonObject = JSONObject(response)
        val resultArray = jsonObject.getJSONObject("message").getJSONArray("result")
        val sessions = mutableListOf<ChatSessionForTopLeftMenu>()
        for (i in 0 until resultArray.length()) {
            val sessionObject = resultArray.getJSONObject(i)
            val session = ChatSessionForTopLeftMenu(
                sessionId = sessionObject.getString("session_id"),
                sessionName = sessionObject.getString("session_name") ?: "New chat",
                aiCharacterName = sessionObject.getString("ai_character_name") ?: "assistant",
                createdAt = sessionObject.getString("created_at") ?: "",
                lastUpdate = sessionObject.getString("last_update") ?: ""
            )
            sessions.add(session)
        }
        return sessions
    }

    // after parsing data from DB - we display it in left top menu
    private fun displayChatSessions(sessions: List<ChatSessionForTopLeftMenu>, action: String) {
        val drawerLayout = mainHandler.getMainBinding().topLeftMenuNavigationView.findViewById<LinearLayout>(R.id.topLeftMenuChatSessionList)

        // only for search - because we want to remove all and display only search results
        // and for example for db_all_sessions_for_user - we want to keep previous as well - because of loadMoreSessions - when scrolling we will keep loading more
        if (action == "db_search_messages") {
            drawerLayout.removeAllViews()
            sessionsLoaded = true
        }

        sessions.forEach { session ->
            val sessionViewBinding = TopLeftMenuChatSessionItemBinding.inflate(mainHandler.mainLayoutInflaterInstance, drawerLayout, false)
            sessionViewBinding.sessionName.text = session.sessionName
            val aiCharacter = session.aiCharacterName
            // having name of character, lets search its image through CharacterManager
            val character = mainHandler.getMainCharacterManager().characters.find { it.nameForAPI == aiCharacter }
            sessionViewBinding.sessionAiCharacterImageView.setImageResource(character?.imageResId ?: R.drawable.assistant)
            // last update date in format YYYY/MM/DD HH:MM
            sessionViewBinding.sessionLastUpdate.text = formatDateTime(session.lastUpdate)

            //handle click on session
            sessionViewBinding.root.setOnClickListener {
                // get data for this specific session
                CoroutineScope(Dispatchers.Main).launch {
                    sendDBRequest("db_get_user_session", mapOf("session_id" to session.sessionId))
                }
                mainHandler.getMainBinding().drawerLayout.closeDrawer(GravityCompat.START)
            }

            // Handle long-press to rename session
            sessionViewBinding.root.setOnLongClickListener {

                showSessionRenameDialog(session)
                true
            }

            drawerLayout.addView(sessionViewBinding.root)
        }
    }

    private fun showSessionRenameDialog(session: ChatSessionForTopLeftMenu) {
        val dialog = Dialog(mainHandler.getMainActivity())
        dialog.setContentView(R.layout.dialog_rename_session)
        dialog.window?.setLayout(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        val editText = dialog.findViewById<EditText>(R.id.editTextNewSessionName)
        val buttonSubmit = dialog.findViewById<Button>(R.id.buttonSubmitNewSessionName)

        editText.setText(session.sessionName)

        buttonSubmit.setOnClickListener {
            val newName = editText.text.toString().trim()
            if (newName.isNotEmpty()) {
                CoroutineScope(Dispatchers.Main).launch {
                    sendDBRequest(
                        "db_update_session",
                        mapOf("session_id" to session.sessionId, "new_session_name" to newName)
                    )
                }
                // Here you can handle the rename action (e.g., send it to the server)
                dialog.dismiss()
            } else {
                mainHandler.createToastMessage("Name cannot be empty")
            }
        }

        dialog.show()
    }

    private fun formatDateTime(input: String): String {
        val inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
        val outputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val date = LocalDateTime.parse(input, inputFormatter)
        return date.format(outputFormatter)
    }
}

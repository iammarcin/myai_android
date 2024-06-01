package biz.atamai.myai

import android.net.Uri
import android.widget.LinearLayout
import android.widget.Toast
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
    private lateinit var mainActivity: MainActivity
    private lateinit var getCurrentDBSessionID: () -> String?
    private lateinit var setCurrentDBSessionID: (String) -> Unit

    fun initialize(activity: MainActivity, getSessionID: () -> String?, setSessionID: (String) -> Unit) {
        mainActivity = activity
        getCurrentDBSessionID = getSessionID
        setCurrentDBSessionID = setSessionID
    }

    // (DBResponse) - callback type (it's sealed class set above)
    suspend fun sendDBRequest(action: String, userInput: Map<String, Any> = mapOf(), callback: ((DBResponse) -> Unit)? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            // serialization of data (uri -> string) explained in ChatItem
            val serializableChatItems = mainActivity.chatItems.map { it.toSerializableMap() }

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
                        Toast.makeText(mainActivity, "Error with saving data in DB", Toast.LENGTH_LONG).show()
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
                setCurrentDBSessionID(sessionId)

                callback?.invoke(DBResponse.SessionId(sessionId))
            }
            "db_all_sessions_for_user", "db_search_messages" -> {
                val sessions = parseSessions(response)
                displayChatSessions(sessions)
            }
            "db_get_user_session" -> {
                val sessionData = jsonResponse.getJSONObject("message").getJSONObject("result")
                mainActivity.chatHelper.restoreSessionData(sessionData)
            }
            "db_new_message" -> {
                println("Db_new_message response: $response")
                val messageContent = jsonResponse.getJSONObject("message").getJSONObject("result")
                val userMessageId = messageContent.getString("userMessageId").toInt()
                val aiMessageId = messageContent.getString("aiMessageId").toInt()

                // if current DB session is empty it means that its new chat, so we have to set it - so messages are assigned to proper session in DB
                // and in fact db_new_message in such case should return additional value - session_id
                if (getCurrentDBSessionID().isNullOrEmpty())
                    setCurrentDBSessionID(messageContent.getString("sessionId"))

                // this returns 2 values - userMessageId and aiMessageId
                callback?.invoke(DBResponse.MessageIds(userMessageId, aiMessageId))
            }
        }
    }

    suspend fun addNewOrEditDBMessage(method: String, userMessage: ChatItem, aiResponse: ChatItem) {
        sendDBRequest(
            method,
            mapOf(
                "customer_id" to 1,
                "session_id" to (getCurrentDBSessionID() ?: ""),
                "userMessage" to mapOf(
                        "sender" to "User",
                        "message" to userMessage.message,
                        "message_id" to userMessage.messageId,
                        "image_locations" to userMessage.imageLocations,
                        "file_locations" to userMessage.fileNames,
                    ),
                "aiResponse" to mapOf(
                        "sender" to "AI",
                        "message" to aiResponse.message,
                        "message_id" to aiResponse.messageId,
                        "image_locations" to aiResponse.imageLocations,
                        "file_locations" to aiResponse.fileNames,
                    ),
                "chat_history" to mainActivity.chatItems,
            )
        ) { response ->
            // if its new message - update message id in chatItem
            // if its edit - we dont need to
            if (method == "db_new_message" ) {
                if (response is DBResponse.MessageIds) {
                    val (userMessageId, aiMessageId) = response
                    userMessage.messageId = userMessageId
                    // if its 0 - it means there was problem with text generation
                    if (aiMessageId > 0)
                        aiResponse.messageId = aiMessageId
                }
            }
        }
    }

    // update DB message with new message and attached files
    /*suspend fun updateDBMessage(userMessage: ChatItem, aiResponse: ChatItem) {
        sendDBRequest(
            "db_edit_message",
            mapOf(
                "session_id" to ( getCurrentDBSessionID() ?: ""),
                "message_id" to messageId,
                "update_text" to message,
                "image_locations" to attachedImageLocations,
                "file_locations" to attachedFiles,
                "chat_history" to mainActivity.chatItems,
            )
        )
    }*/

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
                aiCharacterName = sessionObject.getString("ai_character_name") ?: "Assistant",
                createdAt = sessionObject.getString("created_at") ?: "",
                lastUpdate = sessionObject.getString("last_update") ?: ""
            )
            sessions.add(session)
        }
        return sessions
    }

    // after parsing data from DB - we display it in left top menu
    private fun displayChatSessions(sessions: List<ChatSessionForTopLeftMenu>) {
        val drawerLayout = mainActivity.binding.topLeftMenuNavigationView.findViewById<LinearLayout>(R.id.topLeftMenuChatSessionList)

        drawerLayout.removeAllViews()

        sessions.forEach { session ->
            val sessionViewBinding = TopLeftMenuChatSessionItemBinding.inflate(mainActivity.layoutInflater, drawerLayout, false)
            sessionViewBinding.sessionName.text = session.sessionName
            val aiCharacter = session.aiCharacterName
            // having name of character, lets search its image through CharacterManager
            val character = mainActivity.characterManager.characters.find { it.nameForAPI == aiCharacter }
            sessionViewBinding.sessionAiCharacterImageView.setImageResource(character?.imageResId ?: R.drawable.brainstorm_assistant)
            // last update date in format YYYY/MM/DD HH:MM
            sessionViewBinding.sessionLastUpdate.text = formatDateTime(session.lastUpdate)

            //handle click on session
            sessionViewBinding.root.setOnClickListener {
                // get data for this specific session
                CoroutineScope(Dispatchers.Main).launch {
                    sendDBRequest("db_get_user_session", mapOf("session_id" to session.sessionId))
                }
                mainActivity.binding.drawerLayout.closeDrawer(GravityCompat.START)
            }
            drawerLayout.addView(sessionViewBinding.root)
        }
    }

    private fun formatDateTime(input: String): String {
        val inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
        val outputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val date = LocalDateTime.parse(input, inputFormatter)
        return date.format(outputFormatter)
    }
}

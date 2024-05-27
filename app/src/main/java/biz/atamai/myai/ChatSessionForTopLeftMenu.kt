package biz.atamai.myai

// will be used to display chat sessions in the UI (left top menu)
data class ChatSessionForTopLeftMenu(
    val sessionId: String,
    val sessionName: String = "",
    val aiCharacterName: String = "Assistant",
    val createdAt: String = "",
    val lastUpdate: String = "",
)

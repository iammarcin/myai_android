package biz.atamai.myai

// will be used to display chat sessions in the UI (left top menu)
data class APIChatSession(
    val sessionId: String,
    val sessionName: String,
    val createdAt: String,
    val lastUpdate: String
)

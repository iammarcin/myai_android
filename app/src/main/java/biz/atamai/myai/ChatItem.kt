package biz.atamai.myai

import android.net.Uri

data class ChatItem(
    var message: String,
    val isUserMessage: Boolean,
    val imageUris: List<Uri> = listOf(),
    val fileName: String? = null // if needed
)


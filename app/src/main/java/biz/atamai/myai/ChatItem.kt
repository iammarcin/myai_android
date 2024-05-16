package biz.atamai.myai

import android.net.Uri

data class ChatItem(
    var message: String,
    val isUserMessage: Boolean,
    var imageUris: List<Uri> = listOf(),
    var fileNames: List<Uri> = listOf()
)


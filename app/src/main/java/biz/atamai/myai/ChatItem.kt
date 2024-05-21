package biz.atamai.myai

import android.net.Uri

data class ChatItem(
    var message: String,
    val isUserMessage: Boolean,
    var imageLocations: List<String> = listOf(),
    var fileNames: List<Uri> = listOf(),
    var aiCharacterImageResId: Int? = null
)


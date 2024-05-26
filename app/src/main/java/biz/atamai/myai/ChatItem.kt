package biz.atamai.myai

import android.net.Uri

data class ChatItem(
    var message: String,
    val isUserMessage: Boolean,
    var imageLocations: List<String> = listOf(),
    var fileNames: List<Uri> = listOf(),
    var aiCharacterName: String?, // character name - used for API (f.e. to store data in DB)
    var aiCharacterImageResId: Int? = null // character image resource id - used for UI (f.e. to display character image)
)


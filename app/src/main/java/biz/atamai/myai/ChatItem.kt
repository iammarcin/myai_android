package biz.atamai.myai

import android.net.Uri

data class ChatItem(
    var message: String,
    val isUserMessage: Boolean,
    var imageLocations: List<String> = listOf(),
    var fileNames: List<Uri> = listOf(),
    var aiCharacterName: String?, // character name - used for API (f.e. to store data in DB)
    var aiCharacterImageResId: Int? = null, // character image resource id - used for UI (f.e. to display character image)
    var messageId: Int? = null, // id of the chat item - from DB - its null when its new item, it is set to some value if its existing item (and we want ot f.e. edit it)
    var isTTS: Boolean = false, // this is to differentiate if audio file is coming from TTS or from user upload (if its TTS - user can set to autoplay or transcribe button is not needed so its hidden)
)


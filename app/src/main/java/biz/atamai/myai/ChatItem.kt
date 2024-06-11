// ChatItem.kt

package biz.atamai.myai

import android.net.Uri

data class ChatItem(
    var message: String,
    val isUserMessage: Boolean,
    var imageLocations: List<String> = listOf(),
    var fileNames: List<Uri> = listOf(),
    var aiCharacterName: String?, // character name - used for API (f.e. to store data in DB)
    var messageId: Int? = null, // id of the chat item - from DB - its null when its new item, it is set to some value if its existing item (and we want ot f.e. edit it)
    var isTTS: Boolean = false, // this is to differentiate if audio file is coming from TTS or from user upload (if its TTS - user can set to autoplay or transcribe button is not needed so its hidden)
    var showTranscribeButton: Boolean = true, // this is to show or hide transcribe button (for example to avoid double transcribe executions)
    var isGPSLocationMessage: Boolean = false, // this is to show or hide GPS button (when GPS location is shared by user - we can show it on the map)
) { // not big fan of that - but its chagpt suggestion
    // mainly fileNames must be List of uri - because we sometimes use http url, but sometimes it is really uri (for example in AudioRecorder)
    // so below function is to convert it to serializable map so later in fastapi all values are correct (without it list of filenames was empty)
    // (in fastapi we dont need URIs)
    fun toSerializableMap(): Map<String, Any?> {
        return mapOf(
            "message" to message,
            "isUserMessage" to isUserMessage,
            "imageLocations" to imageLocations,
            "fileNames" to fileNames.map { it.toString() }, // Convert Uri to String
            "aiCharacterName" to aiCharacterName,
            "messageId" to messageId,
            "isTTS" to isTTS,
            "showTranscribeButton" to showTranscribeButton,
            "isGPSLocationMessage" to isGPSLocationMessage
        )
    }
}


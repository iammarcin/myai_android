package biz.atamai.myai

import android.content.Context
import android.widget.Toast

// COmmon place for common tools?!
class UtilityTools(
    private val context: Context,
    private val apiUrl: String,
    private val onResponseReceived: (String) -> Unit,
    private val onError: (Exception) -> Unit
) {

    // used in stopRecording in AudioRecorder and binding.transcribeButton.setOnClickListener in ChatAdapter
    fun sendAudioFile(audioFilePath: String?) {
        if (audioFilePath == null) {
            Toast.makeText(context, "Audio file path is null", Toast.LENGTH_SHORT).show()
            return
        }

        val fullApiUrl = apiUrl + "chat_audio2text"
        val apiDataModel = APIDataModel(
            category = "speech",
            action = "chat",
            userInput = emptyMap(),
            userSettings = ConfigurationManager.getSettingsDict(),
            customerId = 1
        )

        val handler = ResponseHandler(
            handlerType = HandlerType.FileUpload(onResponseReceived = { response ->
                onResponseReceived(response)
            }),
            onError = { error ->
                onError(error)
            }
        )

        handler.sendFileRequest(fullApiUrl, apiDataModel, audioFilePath)
    }
}
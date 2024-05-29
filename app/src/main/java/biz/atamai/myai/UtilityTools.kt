package biz.atamai.myai

import android.content.Context
import android.widget.Toast
import org.json.JSONException
import org.json.JSONObject

// Common place for common tools?!
class UtilityTools(
    private val context: Context,
    private val onResponseReceived: (String) -> Unit,
    private val onError: (Exception) -> Unit
) {

    // audio files (sent for transcriptions) used in stopRecording in AudioRecorder and binding.transcribeButton.setOnClickListener in ChatAdapter
    fun uploadFileToServer(
        filePath: String?,
        apiUrl: String?,
        apiEndpoint: String?,
        apiCategory: String?,
        apiAction: String?,
        userInput: Map<String, Any>? = emptyMap(),
    ) {
        if (filePath == null) {
            Toast.makeText(context, "File path is null", Toast.LENGTH_SHORT).show()
            return
        }

        val fullApiUrl = apiUrl + apiEndpoint
        val apiDataModel = APIDataModel(
            category = apiCategory ?: "",
            action = apiAction ?: "",
            userInput = userInput ?: emptyMap(),
            userSettings = ConfigurationManager.getSettingsDict(),
            customerId = 1
        )

        val handler = ResponseHandler(
            handlerType = HandlerType.FileUpload(onResponseReceived = { response ->
                try {
                    val jsonResponse = JSONObject(response)
                    // TODO - i think if something's wrong with backend - this error here is not shown properly
                    val finalResponse = jsonResponse.getJSONObject("message").getString("result")
                    onResponseReceived(finalResponse)
                } catch (e: JSONException) {
                    Toast.makeText(context, "Error parsing response", Toast.LENGTH_SHORT).show()
                    // raise error
                    onError(e)
                }
            }),
            onError = { error ->
                onError(error)
            },
            authToken = ConfigurationManager.getAuthTokenForBackend()
        )

        handler.sendFileRequest(fullApiUrl, apiDataModel, filePath)
    }

    fun sendTTSRequest(message: String, apiUrl: String) {
        val apiEndpoint = "tts"
        val fullApiUrl = apiUrl + apiEndpoint
        val apiDataModel = APIDataModel(
            category = "audio",
            action = "tts",
            userInput = mapOf("text" to message),
            userSettings = ConfigurationManager.getSettingsDict(),
            customerId = 1
        )

        val handler = ResponseHandler(
            handlerType = HandlerType.NonStreaming(onResponseReceived = { response ->
                try {
                    val jsonResponse = JSONObject(response)
                    val audioUrl = jsonResponse.getString("audio_url")
                    onResponseReceived(audioUrl)
                } catch (e: JSONException) {
                    onError(e)
                }
            }),
            onError = onError,
            authToken = ConfigurationManager.getAuthTokenForBackend()
        )

        handler.sendRequest(fullApiUrl, apiDataModel)
    }

}
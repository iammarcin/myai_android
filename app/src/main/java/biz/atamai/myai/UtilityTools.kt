package biz.atamai.myai

import android.content.Context
import android.widget.Toast
import org.json.JSONException
import org.json.JSONObject

// Common place for common tools?!
class UtilityTools(
    private val context: Context,
    private val apiUrl: String,
    private val onResponseReceived: (String) -> Unit,
    private val onError: (Exception) -> Unit
) {

    // audio files (sent for transcriptions) used in stopRecording in AudioRecorder and binding.transcribeButton.setOnClickListener in ChatAdapter
    fun uploadFileToServer(
        filePath: String?,
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
                    val finalResponse = jsonResponse.getJSONObject("message").getString("result")
                    onResponseReceived(finalResponse)
                } catch (e: JSONException) {
                    Toast.makeText(context, "Error parsing response", Toast.LENGTH_SHORT).show()
                }
            }),
            onError = { error ->
                onError(error)
            }
        )

        handler.sendFileRequest(fullApiUrl, apiDataModel, filePath)
    }


}
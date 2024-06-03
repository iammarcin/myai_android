package biz.atamai.myai

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

// Common place for common tools?!
class UtilityTools(
    private val context: Context,
    private val onResponseReceived: (String) -> Unit,
    private val onError: (Exception) -> Unit
) {

    private val storageDir = (context as MainActivity).getExternalFilesDir(Environment.DIRECTORY_MUSIC)
    private val customerId = 1

    private var audioFile = File(storageDir, "streamed_audio.$customerId.opus")
    //private var audioFile = File.createTempFile("audio", ".opus", context.cacheDir)
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

    fun sendTTSRequest(
        message: String,
        apiUrl: String,
        action: String,
        onResponseReceived: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val apiEndpoint = if (action == "tts_no_stream") "generate" else "tts"
        val fullApiUrl = apiUrl + apiEndpoint
        val apiDataModel = APIDataModel(
            category = "tts",
            action = action,
            userInput = mapOf("text" to message),
            userSettings = ConfigurationManager.getSettingsDict(),
            customerId = 1
        )

        // remove audioFile (so there are no remaining from prev session)
        audioFile.delete()

        val handler = if (action == "tts_stream") {
            ResponseHandler(
                handlerType = HandlerType.AudioStreaming(
                    onAudioChunkReceived = { chunk ->
                        saveChunkToFile(chunk)
                    },
                    onStreamEnd = {
                        val audioUri = Uri.fromFile(audioFile).toString()
                        onResponseReceived(audioUri)
                    }
                ),
                onError = { e ->
                    e.printStackTrace()
                },
                authToken = ConfigurationManager.getAuthTokenForBackend()
            )
        } else {
            ResponseHandler(
                handlerType = HandlerType.NonStreaming(onResponseReceived = { response ->
                    try {
                        val jsonResponse = JSONObject(response)
                        val audioUrl = jsonResponse.getJSONObject("message").getString("result")
                        onResponseReceived(audioUrl)
                    } catch (e: JSONException) {
                        onError(e)
                    }
                }),
                onError = onError,
                authToken = ConfigurationManager.getAuthTokenForBackend()
            )
        }

        handler.sendRequest(fullApiUrl, apiDataModel)
    }

    private fun saveChunkToFile(chunk: ByteArray) {
        try {
            val fileOutputStream = FileOutputStream(audioFile, true)
            fileOutputStream.write(chunk)
            fileOutputStream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
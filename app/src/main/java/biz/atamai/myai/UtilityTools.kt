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

// LIMITATIONS / BUGS
// no really streaming (see comments in sendTTSRequest)

// Common place for common tools?!
class UtilityTools(
    private val mainHandler: MainHandler,
) {

    private val storageDir = mainHandler.getMainBindingContext().getExternalFilesDir(Environment.DIRECTORY_MUSIC)
    private val customerId = 1

    private var audioFile = File(storageDir, "streamed_audio.$customerId.opus")

    //private var audioFile = File.createTempFile("audio", ".opus", context.cacheDir)
    //private var audioUri = Uri.fromFile(audioFile).toString()

    // audio files (sent for transcriptions) used in stopRecording in AudioRecorder and binding.transcribeButton.setOnClickListener in ChatAdapter
    fun uploadFileToServer(
        filePath: String?,
        apiUrl: String?,
        apiEndpoint: String?,
        apiCategory: String?,
        apiAction: String?,
        userInput: Map<String, Any>? = emptyMap(),
        onResponseReceived: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (filePath == null) {
            mainHandler.createToastMessage("File path is null")
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
                    mainHandler.createToastMessage("Error parsing response")
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

    // UNFORTUNATELY - this is not really streaming
    // backend generated chunks in streaming and here we receive chunk by chunk
    // but after quite a bit of time i did not make it work... i tried with exoplayer - with custom data sources, with audio player itself - but everytime it failed
    // so here even though we're streaming it is just waiting until full file is received
    // maybe one day
    fun sendTTSRequest(
        message: String,
        apiUrl: String,
        action: String,
        onResponseReceived: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val apiEndpoint = "generate"
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
                        //onResponseReceived(audioUri)
                    },
                    onStreamEnd = {
                        onResponseReceived(audioFile.absolutePath)
                    }
                ),
                onError = onError,
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

    fun sendImageRequest(
        prompt: String,
        apiUrl: String,
        onResponseReceived: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val apiEndpoint = "generate"
        val fullApiUrl = apiUrl + apiEndpoint
        val apiDataModel = APIDataModel(
            category = "image",
            action = "generate",
            userInput = mapOf("text" to prompt),
            userSettings = ConfigurationManager.getSettingsDict(),
            customerId = 1
        )

        val handler = ResponseHandler(
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
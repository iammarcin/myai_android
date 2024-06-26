// UtilityTools.kt

package biz.atamai.myai

import android.os.Environment
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL

// LIMITATIONS / BUGS
// no really streaming (see comments in sendTTSRequest)

// Common place for common tools?!
class UtilityTools(
    private val mainHandler: MainHandler,
) {
    private val storageDir = mainHandler.getMainBindingContext().getExternalFilesDir("Files")
    private val customerId = 1

    private var audioFile = File(storageDir, "streamed_audio.${customerId}.opus")

    // Helper function to get the downloaded file URI - used here in downloadFile, but also in chat adapter to get the location of downloaded file
    fun getDownloadedFileUri(url: String): File {
        if (url.isEmpty()) {
            throw IllegalArgumentException("URL is empty")
        }
        val fileName = url.substring(url.lastIndexOf('/') + 1)
        val file = File(mainHandler.context.cacheDir, fileName)
        // check if file exists
        return file
    }

    // used for example when playing audio via audio player
    fun downloadFile(fileUrl: String, callback: (File?) -> Unit) {
        Thread {
            try {
                val url = URL(fileUrl)

                // Extract the file name from the URL
                val file = getDownloadedFileUri(fileUrl)

                // Check if the file already exists
                if (file.exists()) {
                    callback(file)
                    return@Thread
                }

                val connection = url.openConnection()
                connection.connect()

                val inputStream: InputStream = url.openStream()
                val outputStream = FileOutputStream(file)
                val buffer = ByteArray(1024)
                var len: Int

                while (inputStream.read(buffer).also { len = it } != -1) {
                    outputStream.write(buffer, 0, len)
                }

                outputStream.close()
                inputStream.close()

                callback(file)
            } catch (e: Exception) {
                e.printStackTrace()
                callback(null)
            }
        }.start()
    }

    // audio files (sent for transcriptions) used in stopRecording in AudioRecorder and binding.transcribeButton.setOnClickListener in ChatAdapter
    // in s3 upload in FileAttachmentHandler
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
            userSettings = mainHandler.getConfigurationManager().getSettingsDict(),
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
            authToken = mainHandler.getConfigurationManager().getAuthTokenForBackend()
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
            userSettings = mainHandler.getConfigurationManager().getSettingsDict(),
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
                authToken = mainHandler.getConfigurationManager().getAuthTokenForBackend()
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
                authToken = mainHandler.getConfigurationManager().getAuthTokenForBackend()
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
            userSettings = mainHandler.getConfigurationManager().getSettingsDict(),
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
                authToken = mainHandler.getConfigurationManager().getAuthTokenForBackend()
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
package biz.atamai.myai

import okhttp3.*
import com.google.gson.GsonBuilder
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okio.Buffer
import java.io.File
import java.io.IOException
import okhttp3.MultipartBody

// sealed class because same class ResponseHandler is used for both streaming and non-streaming responses
// so we need to differentiate between the two types
sealed class HandlerType {
    data class Streaming(
        val onChunkReceived: (String) -> Unit,
        val onStreamEnd: () -> Unit
    ) : HandlerType()

    data class NonStreaming(
        val onResponseReceived: (String) -> Unit
    ) : HandlerType()

    data class AudioUpload(
        val onResponseReceived: (String) -> Unit
    ) : HandlerType()
}

class ResponseHandler(
    private val handlerType: HandlerType,
    private val onError: (Exception) -> Unit
) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val client = OkHttpClient()
    private val gson = GsonBuilder().create()

    fun sendRequest(url: String, apiDataModel: APIDataModel) {
        coroutineScope.launch {
            try {
                val requestBody = RequestBody.create("application/json".toMediaType(), gson.toJson(apiDataModel).toByteArray())
                val request = Request.Builder().url(url).post(requestBody).build()
                client.newCall(request).enqueue(object : Callback {

                    override fun onFailure(call: Call, e: IOException) {
                        e.printStackTrace()
                        coroutineScope.launch(Dispatchers.Main) {
                            onError(Exception("Network error: ${e.message}"))
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.body?.let { body ->
                            when (handlerType) {
                                is HandlerType.Streaming -> handleStreamingResponse(body)
                                is HandlerType.NonStreaming -> handleNonStreamingResponse(body)
                                is HandlerType.AudioUpload -> handleAudioUploadResponse(body)
                            }
                        } ?: run {
                            coroutineScope.launch(Dispatchers.Main) {
                                onError(Exception("Empty response"))
                            }
                        }
                    }
                })
            } catch (e: Exception) {
                coroutineScope.launch(Dispatchers.Main) {
                    onError(e)
                }
            }
        }
    }

    private fun handleStreamingResponse(body: ResponseBody) {
        coroutineScope.launch {
            body.source().use { source ->
                val buffer = Buffer()
                while (true) {
                    val read = source.read(buffer, 1024)
                    if (read == -1L) break
                    var chunk = buffer.clone().readString(Charsets.UTF_8)
                    chunk = cleanChunk(chunk)
                    coroutineScope.launch(Dispatchers.Main) {
                        (handlerType as HandlerType.Streaming).onChunkReceived(chunk)
                    }
                    buffer.clear()
                }
            }
            coroutineScope.launch(Dispatchers.Main) {
                (handlerType as HandlerType.Streaming).onStreamEnd()
            }
        }
    }

    private fun handleNonStreamingResponse(body: ResponseBody) {
        coroutineScope.launch(Dispatchers.Main) {
            val responseText = body.string()
            (handlerType as HandlerType.NonStreaming).onResponseReceived(responseText)
        }
    }

    private fun handleAudioUploadResponse(body: ResponseBody) {
        coroutineScope.launch(Dispatchers.Main) {
            val responseText = body.string()
            (handlerType as HandlerType.AudioUpload).onResponseReceived(responseText)
        }
    }

    fun sendAudioRequest(url: String, apiDataModel: APIDataModel, audioFilePath: String) {
        coroutineScope.launch {
            try {
                val audioFile = File(audioFilePath)
                val audioRequestBody = audioFile.asRequestBody("audio/mpeg".toMediaType())
                val audioPart = MultipartBody.Part.createFormData("audio", audioFile.name, audioRequestBody)

                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addPart(audioPart)
                    .addFormDataPart("action", apiDataModel.action)
                    .addFormDataPart("category", apiDataModel.category)
                    .addFormDataPart("userInput", gson.toJson(apiDataModel.userInput))
                    .addFormDataPart("userSettings", gson.toJson(apiDataModel.userSettings))
                    .addFormDataPart("customerId", apiDataModel.customerId.toString())
                    .build()

                val request = Request.Builder().url(url).post(requestBody).build()
                client.newCall(request).enqueue(object : Callback {

                    override fun onFailure(call: Call, e: IOException) {
                        e.printStackTrace()
                        coroutineScope.launch(Dispatchers.Main) {
                            onError(Exception("Network error: ${e.message}"))
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.body?.let { body ->
                            coroutineScope.launch(Dispatchers.Main) {
                                (handlerType as HandlerType.AudioUpload).onResponseReceived(body.string())
                            }
                        } ?: run {
                            coroutineScope.launch(Dispatchers.Main) {
                                onError(Exception("Empty response"))
                            }
                        }
                    }
                })
            } catch (e: Exception) {
                coroutineScope.launch(Dispatchers.Main) {
                    onError(e)
                }
            }
        }
    }

    fun cancelRequest() {
        coroutineScope.cancel()
    }

    private fun cleanChunk(chunk: String): String {
        // Remove the "data: " prefix and trim newlines
        //return chunk.replace("data: ", " ").replace("\n", "").trim()
        // it looks like /n/n is standard format, and with additonal 3rd \n - we want to keep it - because AI wants new line
        // also there are 2 spaces always - so we will trim 1 (but we cannot use trim() - as it will trim all spaces)
        return chunk.replace("data: ", " ").replace("\n\n", "").replaceFirst("^\\s".toRegex(), "")
    }
}
package biz.atamai.myai

import android.webkit.MimeTypeMap
import okhttp3.*
import com.google.gson.GsonBuilder
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okio.Buffer
import java.io.File
import java.io.IOException
import okhttp3.MultipartBody
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

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

    data class FileUpload(
        val onResponseReceived: (String) -> Unit
    ) : HandlerType()

    data class AudioStreaming(
        val onAudioChunkReceived: (ByteArray) -> Unit,
        val onStreamEnd: () -> Unit
    ) : HandlerType()
}

class ResponseHandler(
    private val handlerType: HandlerType,
    private val onError: (Exception) -> Unit,
    private val authToken: String
) {
    private val timeoutInSecs = 60L
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(timeoutInSecs, TimeUnit.SECONDS)
        .readTimeout(timeoutInSecs, TimeUnit.SECONDS)
        .writeTimeout(timeoutInSecs, TimeUnit.SECONDS)
        .build()
    private val gson = GsonBuilder().create()

    fun sendRequest(url: String, apiDataModel: APIDataModel) {
        coroutineScope.launch {
            try {
                val requestBody = RequestBody.create("application/json".toMediaType(), gson.toJson(apiDataModel).toByteArray())
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .addHeader("Authorization", "Bearer $authToken")
                    .build()
                client.newCall(request).enqueue(object : Callback {

                    override fun onFailure(call: Call, e: IOException) {
                        e.printStackTrace()
                        coroutineScope.launch(Dispatchers.Main) {
                            if (e is SocketTimeoutException) {
                                onError(Exception("Request timed out: ${e.message}"))
                            } else {
                                onError(Exception("Network error: ${e.message}"))
                            }
                            println("ERROR! ${e.message}")
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (!response.isSuccessful) {
                            coroutineScope.launch(Dispatchers.Main) {
                                onError(Exception("HTTP error: ${response.code} ${response.message}"))
                            }
                            return
                        }

                        response.body?.let { body ->
                            when (handlerType) {
                                is HandlerType.Streaming -> handleStreamingResponse(body)
                                is HandlerType.NonStreaming -> handleNonStreamingResponse(body)
                                is HandlerType.FileUpload -> handleAudioUploadResponse(body)
                                is HandlerType.AudioStreaming -> handleAudioStreamingResponse(body)
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
                    println("ERROR! ${e.message}")
                }
            }
        }
    }

    private fun handleAudioStreamingResponse(body: ResponseBody) {
        coroutineScope.launch {
            body.source().use { source ->
                val buffer = Buffer()
                while (true) {
                    val read = source.read(buffer, 1024)
                    if (read == -1L) break
                    val chunk = buffer.readByteArray()
                    coroutineScope.launch(Dispatchers.Main) {
                        println("Chunk received: ${chunk.size} ")
                        println(chunk)
                        (handlerType as HandlerType.AudioStreaming).onAudioChunkReceived(chunk)
                    }
                    buffer.clear()
                }
            }
            coroutineScope.launch(Dispatchers.Main) {
                (handlerType as HandlerType.AudioStreaming).onStreamEnd()
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
                    val chunk = buffer.clone().readString(Charsets.UTF_8)
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
        coroutineScope.launch {
            val responseText = body.string() // Perform this operation in the IO thread
            withContext(Dispatchers.Main) {
                (handlerType as HandlerType.NonStreaming).onResponseReceived(responseText)
            }
        }
    }

    private fun handleAudioUploadResponse(body: ResponseBody) {
        coroutineScope.launch(Dispatchers.Main) {
            val responseText = body.string()
            (handlerType as HandlerType.FileUpload).onResponseReceived(responseText)
        }
    }

    // request to upload the file to my API
    fun sendFileRequest(url: String, apiDataModel: APIDataModel, filePath: String) {
        coroutineScope.launch {
            try {
                val file = File(filePath)
                val mimeType = getMimeType(filePath)
                val fileRequestBody = file.asRequestBody(mimeType.toMediaType())
                val audioPart = MultipartBody.Part.createFormData("file", file.name, fileRequestBody)

                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addPart(audioPart)
                    .addFormDataPart("action", apiDataModel.action)
                    .addFormDataPart("category", apiDataModel.category)
                    .addFormDataPart("userInput", gson.toJson(apiDataModel.userInput))
                    .addFormDataPart("userSettings", gson.toJson(apiDataModel.userSettings))
                    .addFormDataPart("customerId", apiDataModel.customerId.toString())
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .addHeader("Authorization", "Bearer $authToken")
                    .build()
                client.newCall(request).enqueue(object : Callback {

                    override fun onFailure(call: Call, e: IOException) {
                        e.printStackTrace()
                        coroutineScope.launch(Dispatchers.Main) {
                            if (e is SocketTimeoutException) {
                                onError(Exception("Request timed out: ${e.message}"))
                            } else {
                                onError(Exception("Network error: ${e.message}"))
                            }
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (!response.isSuccessful) {
                            coroutineScope.launch(Dispatchers.Main) {
                                onError(Exception("HTTP error: ${response.code} ${response.message}"))
                            }
                            return
                        }

                        response.body?.let { body ->
                            coroutineScope.launch(Dispatchers.Main) {
                                (handlerType as HandlerType.FileUpload).onResponseReceived(body.string())
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

    // used in sendFileRequest, making our function dynamic - depending on file type
    private fun getMimeType(filePath: String): String {
        val extension = MimeTypeMap.getFileExtensionFromUrl(filePath)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }

    fun cancelRequest() {
        coroutineScope.cancel()
    }
}
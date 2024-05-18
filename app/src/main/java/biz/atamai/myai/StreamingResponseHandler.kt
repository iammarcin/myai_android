package biz.atamai.myai

import okhttp3.*
import com.google.gson.GsonBuilder
import okhttp3.MediaType.Companion.toMediaType
import okio.Buffer
import java.io.IOException
import kotlinx.coroutines.*

class StreamingResponseHandler(
    private val onChunkReceived: (String) -> Unit,
    private val onError: (Exception) -> Unit,
    private val onStreamEnd: () -> Unit
) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val client = OkHttpClient()
    private val gson = GsonBuilder().create()

    fun startStreaming(url: String, apiDataModel: APIDataModel) {
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
                            body.source().use { source ->
                                val buffer = Buffer()
                                while (true) {
                                    val read = source.read(buffer, 1024)
                                    if (read == -1L) break
                                    var chunk = buffer.clone().readString(Charsets.UTF_8)
                                    chunk = cleanChunk(chunk)
                                    coroutineScope.launch(Dispatchers.Main) {
                                        onChunkReceived(chunk)
                                    }
                                    buffer.clear()
                                }
                            }
                        }
                        coroutineScope.launch(Dispatchers.Main) {
                            onStreamEnd()
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

    fun cancelStreaming() {
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

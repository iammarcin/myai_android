package biz.atamai.myai

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.database.Cursor
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.squareup.picasso.Picasso
import java.io.File

class FileAttachmentHandler(
    private val mainHandler: MainHandler,
    private val apiUrl: String
) {
    // this will be used in case where multiple files are being attached / uploaded
    // it caused some troubles - like disabling progress bar or enabling send button too soon
    // (after first successful upload)
    // so we introduce this counter
    private var uploadCounter = 0
    private val fileChooserLauncher: ActivityResultLauncher<Intent> = mainHandler.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        handleActivityResult(result)
    }

    fun openFileChooser() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "audio/mpeg", "audio/mp3", "audio/mp4", "audio/mpeg", "audio/x-wav"))
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        fileChooserLauncher.launch(Intent.createChooser(intent, "Select File"))
    }

    private fun handleActivityResult(result: ActivityResult) {
        if (result.resultCode == AppCompatActivity.RESULT_OK) {
            val clipData = result.data?.clipData
            if (clipData != null) {
                for (i in 0 until clipData.itemCount) {
                    val uri = clipData.getItemAt(i).uri
                    addFilePreview(uri, true)
                }
            } else {
                result.data?.data?.let { uri ->
                    addFilePreview(uri, true)
                }
            }
        }
    }

    // those functions are here as file preview (at the bottom when attaching files, but before sending them)
    fun addFilePreview(uri: Uri, incrementCounter: Boolean = false) {
        if (incrementCounter) {
            incrementUploadCounter()
        }
        val mimeType = mainHandler.getMainActivityContext().contentResolver.getType(uri)
        val frameLayout = FrameLayout(mainHandler.getMainActivityContext()).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 80.toPx()).apply {
                marginStart = 8.toPx()
                marginEnd = 8.toPx()
            }
        }

        if (mimeType?.startsWith("audio/") == true) {
            val filePath = getFilePathFromUri(uri)
            if (filePath != null) {
                val fileUri = Uri.fromFile(File(filePath))
                val chatItem = mainHandler.addMessageToChat("", listOf(), listOf(fileUri))
                chatItem.isTTS = false // when uploaded we set it it to false on purpose (we treat TTS diff way)
                mainHandler.hideProgressBar()
                decrementUploadCounter()
                return
            }
            // maybe one day we can handle potential error here
            println("ERROR: Could not get file path from URI")
            decrementUploadCounter()
            return
        } else if (mimeType?.startsWith("image/") == true) {
            val imageView = ImageView(mainHandler.getMainActivityContext()).apply {
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT)
                scaleType = ImageView.ScaleType.CENTER_CROP
                adjustViewBounds = true
                //setImageURI(uri) - disabling here (temp?) because when s3 upload fails later it will be seen as image upload is OK (but its only local uri)
                //tag = uri // disabling here because we don't wanna work with URIs, but public URLs (because anyway we needed later for openai)
            }

            imageView.setImageResource(R.drawable.image_temp_before_s3_upload)
            frameLayout.addView(imageView)

            val filePath = getFilePathFromUri(uri)
            val utilityTools = UtilityTools(
                context = mainHandler.getMainActivityContext(),
                onResponseReceived = { response ->
                    mainHandler.executeOnUIThread {
                        imageView.setImageURI(null) // Clear local URI
                        // set imageview tag as response - so we can use it to remove image from preview
                        imageView.tag = response
                        Picasso.get().load(response).into(imageView)
                        decrementUploadCounter()
                    }
                },
                onError = { error ->
                    mainHandler.executeOnUIThread {
                        decrementUploadCounter()
                        mainHandler.createToastMessage("Error: ${error.message}")
                    }
                }
            )
            // upload to S3 - so sending request to nodejs API
            utilityTools.uploadFileToServer(filePath, apiUrl, "api/aws", "provider.s3", "s3_upload")
        } else {
            val placeholder = View(mainHandler.getMainActivityContext()).apply {
                layoutParams = FrameLayout.LayoutParams(50.toPx(), 50.toPx()).apply {
                    gravity = Gravity.CENTER
                }
                setBackgroundColor(
                    ContextCompat.getColor(
                        mainHandler.getMainActivityContext(),
                        R.color.attached_file_placeholder
                    )
                )
                tag = uri
            }
            frameLayout.addView(placeholder)
            decrementUploadCounter()
        }

        val removeButton = ImageButton(mainHandler.getMainActivityContext()).apply {
            layoutParams = FrameLayout.LayoutParams(24.toPx(), 24.toPx()).apply {
                gravity = Gravity.TOP or Gravity.END
            }
            setImageResource(R.drawable.ic_close)
            background = ContextCompat.getDrawable(mainHandler.getMainActivityContext(), R.drawable.rounded_button_background)
            setOnClickListener {
                mainHandler.getImagePreviewContainer().removeView(frameLayout)
                if (mainHandler.getImagePreviewContainer().childCount == 0) {
                    mainHandler.getScrollViewPreview().visibility = View.GONE
                }
            }
        }

        frameLayout.addView(removeButton)
        mainHandler.getImagePreviewContainer().addView(frameLayout)
        mainHandler.getScrollViewPreview().visibility = View.VISIBLE
    }

    // there was problem with getting file path from URI (when i wanted to transcribe audio file) - so we need to save it to cache and get path from there
    private fun getFilePathFromUri(uri: Uri): String? {
        var filePath: String? = null
        val cursor: Cursor? = mainHandler.getMainActivityContext().contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val displayName: String = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                val inputStream = mainHandler.getMainActivityContext().contentResolver.openInputStream(uri)
                val file = File(mainHandler.getMainActivityContext().cacheDir, displayName)
                file.outputStream().use { output ->
                    inputStream?.copyTo(output)
                }
                filePath = file.absolutePath
            }
        }
        return filePath
    }

    // helper function to handle uploadCounter -- explained above in private var uploadCounter
    private fun incrementUploadCounter() {
        uploadCounter++
        disableActiveButtons()
        mainHandler.showProgressBar("Uploading files")
    }
    private fun decrementUploadCounter() {
        uploadCounter--
        if (uploadCounter <= 0) {
            enableActiveButtons()
            mainHandler.hideProgressBar()
        }
    }

    // helper functions - to disable important (send and record buttons) while some activity takes place
    // for sure used when file is uploaded in FileAttachmentHandler
    // - because we want to be sure that file is uploaded to S3 before user can send request
    private fun disableActiveButtons() {
        mainHandler.getMainBinding().btnSend.isEnabled = false
        mainHandler.getMainBinding().btnRecord.isEnabled = false
        mainHandler.getMainBinding().newChatButton.isEnabled = false
    }
    private fun enableActiveButtons() {
        mainHandler.getMainBinding().btnSend.isEnabled = true
        mainHandler.getMainBinding().btnRecord.isEnabled = true
        mainHandler.getMainBinding().newChatButton.isEnabled = true
    }

    private fun Int.toPx(): Int = (this * mainHandler.getMainActivityContext().resources.displayMetrics.density).toInt()
}

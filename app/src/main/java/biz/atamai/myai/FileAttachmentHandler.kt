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
import com.squareup.picasso.Picasso
import java.io.File

class FileAttachmentHandler(
    private val activity: MainActivity,
    private val imagePreviewContainer: LinearLayout,
    private val scrollViewPreview: HorizontalScrollView
) {
    private val fileChooserLauncher: ActivityResultLauncher<Intent> = activity.registerForActivityResult(
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
        if (result.resultCode == Activity.RESULT_OK) {
            val clipData = result.data?.clipData
            if (clipData != null) {
                for (i in 0 until clipData.itemCount) {
                    val uri = clipData.getItemAt(i).uri
                    addFilePreview(uri)
                }
            } else {
                result.data?.data?.let { uri ->
                    addFilePreview(uri)
                }
            }
        }
    }

    // those functions are here as file preview (at the bottom when attaching files, but before sending them)
    fun addFilePreview(uri: Uri) {
        val mimeType = activity.contentResolver.getType(uri)
        val frameLayout = FrameLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 80.toPx()).apply {
                marginStart = 8.toPx()
                marginEnd = 8.toPx()
            }
        }

        if (mimeType?.startsWith("audio/") == true) {
            val filePath = getFilePathFromUri(uri)
            if (filePath != null) {
                val fileUri = Uri.fromFile(File(filePath))
                activity.addMessageToChat("", listOf(), listOf(fileUri))
                return
            }
            // maybe one day we can handle potential error here
            println("ERROR: Could not get file path from URI")
            return
        } else if (mimeType?.startsWith("image/") == true) {
            val imageView = ImageView(activity).apply {
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT)
                scaleType = ImageView.ScaleType.CENTER_CROP
                adjustViewBounds = true
                setImageURI(uri)
                tag = uri
            }
            frameLayout.addView(imageView)

            val filePath = getFilePathFromUri(uri)
            val utilityTools = UtilityTools(
                context = activity,
                apiUrl = activity.apiUrl,
                onResponseReceived = { response ->
                    activity.runOnUiThread {
                        println("1111111")
                        println(response)
                        imageView.setImageURI(null) // Clear local URI
                        Picasso.get().load(response).into(imageView)
                        // Update chat with S3 URL
                        activity.addMessageToChat("", listOf(Uri.parse(response)), listOf())
                    }
                },
                onError = { error ->
                    activity.runOnUiThread {
                        Toast.makeText(activity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            // upload to S3 - so sending request to nodejs API
            utilityTools.uploadFileToServer(filePath, activity.apiNodeUrl, "api/sendToS3", "doesNotMatter", "doesNotMatter")
        } else {
            val placeholder = View(activity).apply {
                layoutParams = FrameLayout.LayoutParams(50.toPx(), 50.toPx()).apply {
                    gravity = Gravity.CENTER
                }
                setBackgroundColor(
                    ContextCompat.getColor(
                        activity,
                        R.color.attached_file_placeholder
                    )
                )
                tag = uri
            }
            frameLayout.addView(placeholder)
        }

        val removeButton = ImageButton(activity).apply {
            layoutParams = FrameLayout.LayoutParams(24.toPx(), 24.toPx()).apply {
                gravity = Gravity.TOP or Gravity.END
            }
            setImageResource(R.drawable.ic_close)
            background = ContextCompat.getDrawable(activity, R.drawable.rounded_button_background)
            setOnClickListener {
                imagePreviewContainer.removeView(frameLayout)
                if (imagePreviewContainer.childCount == 0) {
                    scrollViewPreview.visibility = View.GONE
                }
            }
        }

        frameLayout.addView(removeButton)
        imagePreviewContainer.addView(frameLayout)
        scrollViewPreview.visibility = View.VISIBLE
    }

    // there was problem with getting file path from URI (when i wanted to transcribe audio file) - so we need to save it to cache and get path from there
    private fun getFilePathFromUri(uri: Uri): String? {
        var filePath: String? = null
        val cursor: Cursor? = activity.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val displayName: String = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                val inputStream = activity.contentResolver.openInputStream(uri)
                val file = File(activity.cacheDir, displayName)
                file.outputStream().use { output ->
                    inputStream?.copyTo(output)
                }
                filePath = file.absolutePath
            }
        }
        return filePath
    }

    private fun Int.toPx(): Int = (this * activity.resources.displayMetrics.density).toInt()
}

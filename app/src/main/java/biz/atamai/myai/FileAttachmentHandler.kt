package biz.atamai.myai

import android.app.Activity
import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

class FileAttachmentHandler(private val activity: MainActivity) {
    private val fileChooserLauncher: ActivityResultLauncher<Intent> = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        handleActivityResult(result)
    }

    fun openFileChooser() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "audio/*"))
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)  // Allow multiple selection
        }
        fileChooserLauncher.launch(Intent.createChooser(intent, "Select File"))
    }

    private fun handleActivityResult(result: ActivityResult) {
        if (result.resultCode == Activity.RESULT_OK) {
            val clipData = result.data?.clipData

            if (clipData != null) { // Multiple items selected
                for (i in 0 until clipData.itemCount) {
                    val uri = clipData.getItemAt(i).uri
                    activity.addFilePreview(uri)
                }
            } else { // Single item selected
                result.data?.data?.let { uri ->
                    activity.addFilePreview(uri)
                }
            }
        }
    }
}

package biz.atamai.myai

import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File

class CameraHandler(private val mainHandler: MainHandler, private val registry: ActivityResultRegistry) {
    private var photoUri: Uri? = null

    // This launcher is initialized in the activity where CameraHandler is used
    private lateinit var takePictureLauncher: ActivityResultLauncher<Uri>

    fun setupTakePictureLauncher(onSuccess: (Uri?) -> Unit, onFailure: () -> Unit) {
        takePictureLauncher = registry.register("takePictureKey", mainHandler.activity, ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                onSuccess(photoUri)
            } else {
                onFailure()
            }
        }
    }

    fun takePhoto() {
        // Ensure the external storage permission is granted before calling this method
        val photoFile: File = createImageFile()
        photoUri = FileProvider.getUriForFile(mainHandler.activity, "${mainHandler.activity.packageName}.provider", photoFile)
        takePictureLauncher.launch(photoUri)
    }

    private fun createImageFile(): File {
        val storageDir: File = mainHandler.activity.getExternalFilesDir("Images") ?: throw IllegalStateException("External Storage is unavailable")
        return File.createTempFile("JPEG_${System.currentTimeMillis()}_", ".jpg", storageDir)
    }

}

package biz.atamai.myai

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File

class CameraHandler(private val activity: AppCompatActivity, private val registry: ActivityResultRegistry) {


    private var photoUri: Uri? = null

    // This launcher is initialized in the activity where CameraHandler is used
    lateinit var takePictureLauncher: ActivityResultLauncher<Uri>

    fun setupTakePictureLauncher(onSuccess: () -> Unit, onFailure: () -> Unit) {
        takePictureLauncher = registry.register("takePictureKey", activity, ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                onSuccess()
            } else {
                onFailure()
            }
        }
    }

    fun takePhoto() {
        // Ensure the external storage permission is granted before calling this method
        val photoFile: File = createImageFile()
        photoUri = FileProvider.getUriForFile(activity, "${activity.packageName}.provider", photoFile)
        takePictureLauncher.launch(photoUri)
    }

    private fun createImageFile(): File {
        val storageDir: File = activity.getExternalFilesDir("Images") ?: throw IllegalStateException("External Storage is unavailable")
        return File.createTempFile("JPEG_${System.currentTimeMillis()}_", ".jpg", storageDir)
    }

}

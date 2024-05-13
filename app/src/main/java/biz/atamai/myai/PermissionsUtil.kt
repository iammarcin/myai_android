package biz.atamai.myai

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionsUtil(private val activity: Activity) {
    companion object {
        const val REQUEST_AUDIO_PERMISSION_CODE = 1
    }

    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    fun checkPermissions(): Boolean {
        val neededPermissions = requiredPermissions.any {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }
        return !neededPermissions
    }

    fun requestPermissions() {
        ActivityCompat.requestPermissions(
            activity,
            requiredPermissions,
            REQUEST_AUDIO_PERMISSION_CODE
        )
    }

    fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_AUDIO_PERMISSION_CODE) {
            // Convert grantResults from IntArray to List<Int> to allow zipping
            val grantsList = grantResults.toList()
            val permissionsMap = permissions.zip(grantsList).toMap()

            val isRecordAudioGranted = permissionsMap[Manifest.permission.RECORD_AUDIO] ?: PackageManager.PERMISSION_DENIED
            val isWriteStorageGranted = permissionsMap[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: PackageManager.PERMISSION_DENIED

            if (isRecordAudioGranted == PackageManager.PERMISSION_GRANTED && isWriteStorageGranted == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(activity, "All permissions granted", Toast.LENGTH_SHORT).show()
                // Permissions are granted, continue with functionality that needs permissions
            } else {
                if (isRecordAudioGranted != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(activity, "Record Audio permission denied", Toast.LENGTH_SHORT).show()
                }
                if (isWriteStorageGranted != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(activity, "Storage permission denied", Toast.LENGTH_SHORT).show()
                }
                // Inform the user that permissions were not granted
            }
        }
    }
}

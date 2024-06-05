package biz.atamai.myai

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionsUtil(private val mainHandler: MainHandler) {
    companion object {
        const val REQUEST_AUDIO_PERMISSION_CODE = 1
    }

    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.CAMERA,
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.MODIFY_AUDIO_SETTINGS
    )

    /* maybe later

    */

    fun checkPermissions(): Boolean {
        val neededPermissions = requiredPermissions.any {
            mainHandler.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        return !neededPermissions
    }

    fun requestPermissions() {
        val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.plus(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            requiredPermissions
        }
        mainHandler.requestAllPermissions(permissionsToRequest, REQUEST_AUDIO_PERMISSION_CODE)
    }

    fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_AUDIO_PERMISSION_CODE) {
            // Convert grantResults from IntArray to List<Int> to allow zipping
            val grantsList = grantResults.toList()
            val permissionsMap = permissions.zip(grantsList).toMap()

            val isRecordAudioGranted = permissionsMap[Manifest.permission.RECORD_AUDIO] ?: PackageManager.PERMISSION_DENIED
            val isWriteStorageGranted = permissionsMap[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: PackageManager.PERMISSION_DENIED
            val isCameraGranted = permissionsMap[Manifest.permission.CAMERA] == PackageManager.PERMISSION_GRANTED
            val isBluetoothGranted = permissionsMap[Manifest.permission.BLUETOOTH] == PackageManager.PERMISSION_GRANTED
            val isBluetoothAdminGranted = permissionsMap[Manifest.permission.BLUETOOTH_ADMIN] == PackageManager.PERMISSION_GRANTED
            val isModifyAudioSettingsGranted = permissionsMap[Manifest.permission.MODIFY_AUDIO_SETTINGS] == PackageManager.PERMISSION_GRANTED


            if (isRecordAudioGranted == PackageManager.PERMISSION_GRANTED && isWriteStorageGranted == PackageManager.PERMISSION_GRANTED) {
                //Toast.makeText(activity, "All permissions granted", Toast.LENGTH_SHORT).show()
                println("All permissions granted")
                // Permissions are granted, continue with functionality that needs permissions
            } else {
                if (isRecordAudioGranted != PackageManager.PERMISSION_GRANTED) {
                    mainHandler.createToastMessage("Record Audio permission denied")
                }
                if (isWriteStorageGranted != PackageManager.PERMISSION_GRANTED) {
                    mainHandler.createToastMessage("Storage permission denied")
                }
                if (!isCameraGranted) {
                    mainHandler.createToastMessage("Camera permission denied")
                }
                if (!isBluetoothGranted) {
                    mainHandler.createToastMessage("Bluetooth permission denied")
                }
                if (!isBluetoothAdminGranted) {
                    mainHandler.createToastMessage("Bluetooth Admin permission denied")
                }
                if (!isModifyAudioSettingsGranted) {
                    mainHandler.createToastMessage("Modify Audio Settings permission denied")
                }
                // Inform the user that permissions were not granted
            }
        }
    }
}

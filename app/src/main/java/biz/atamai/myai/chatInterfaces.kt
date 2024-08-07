// chatInterfaces.kt

package biz.atamai.myai

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import biz.atamai.myai.databinding.ActivityMainBinding
import org.json.JSONObject
import java.io.File

// THESE ARE USED because passing between different classes (files) became quite challenging
// we have multiple rounded dependencies and we need to pass data between them

interface MainHandler {
    fun getMainActivity(): Activity
    val context: Context
    val activity: AppCompatActivity
    fun getMainBinding(): ActivityMainBinding
    fun getMainBindingContext(): Context
    val chatItemsList: MutableList<ChatItem>
    val mainLayoutInflaterInstance: LayoutInflater
    fun handleTextMessage(message: String, attachedImageLocations: List<String> = listOf(), attachedFiles: List<Uri> = listOf(), gpsLocationMessage: Boolean = false)
    fun addMessageToChat(message: String, attachedImageLocations: List<String>, attachedFiles: List<Uri>, gpsLocationMessage: Boolean): ChatItem
    fun showProgressBar(message: String = "")
    fun hideProgressBar(message: String = "")
    fun getCurrentAICharacter(): String
    fun setRecordButtonImageResource(resourceId: Int)
    fun createToastMessage(message: String, duration: Int = Toast.LENGTH_SHORT)
    fun getImagePreviewContainer(): LinearLayout
    fun getScrollViewPreview(): HorizontalScrollView
    fun registerForActivityResult(contract: ActivityResultContracts.StartActivityForResult, callback: (ActivityResult) -> Unit): ActivityResultLauncher<Intent>
    fun checkSelfPermission(permission: String): Int
    fun requestAllPermissions(permissions: Array<String>, requestCode: Int)
    fun getMainCharacterManager(): CharacterManager
    fun getFullCharacterData(characterName: String): CharacterManager.Character
    fun resizeImage(filePath: String, maxDimension: Int): File?
    fun releaseMediaPlayer()
    fun getDatabaseHelper(): DatabaseHelper
    fun getConfigurationManager(): ConfigurationManager
    fun getIsUserScrolling(): Boolean
    //fun getMainLayoutInflater(): LayoutInflater
}

interface ChatHelperHandler {
    fun getCurrentDBSessionID(): String
    fun setCurrentDBSessionID(sessionID: String)
    fun setEditingMessagePosition(position: Int?)
    fun createNewSessionFromHere(position: Int)
    fun restoreSessionData(sessionData: JSONObject)
    fun scrollToEnd(autoToEnd: Boolean = false)
    fun shareGPSLocation(latitude: Double, longitude: Double)
    fun getCurrentDate(): String
}

interface ChatAdapterHandler {
    fun onTextSizeChanged(newSize: Int)
    fun onCharacterLongPress(character: CharacterManager.Character)
}

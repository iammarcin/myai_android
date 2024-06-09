package biz.atamai.myai

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import biz.atamai.myai.databinding.ActivityMainBinding
import biz.atamai.myai.databinding.ChatItemBinding
import org.json.JSONObject

// THESE ARE USED because passing between different classes (files) became quite challenging
// we have multiple rounded dependencies and we need to pass data between them

interface MainHandler {
    fun getMainActivity(): Activity
    val context: Context
    fun getMainBinding(): ActivityMainBinding
    fun getMainBindingContext(): Context
    val chatItemsList: MutableList<ChatItem>
    val mainLayoutInflaterInstance: LayoutInflater
    fun handleTextMessage(message: String, attachedImageLocations: List<String> = listOf(), attachedFiles: List<Uri> = listOf())
    fun addMessageToChat(message: String, attachedImageLocations: List<String> = listOf(), attachedFiles: List<Uri> = listOf()): ChatItem
    fun showProgressBar(message: String = "")
    fun hideProgressBar(message: String = "")
    fun executeOnUIThread(action: Runnable)
    fun getCurrentAICharacter(): String
    fun setRecordButtonImageResource(resourceId: Int)
    fun createToastMessage(message: String, duration: Int = Toast.LENGTH_SHORT)
    fun getImagePreviewContainer(): LinearLayout
    fun getScrollViewPreview(): HorizontalScrollView
    fun registerForActivityResult(contract: ActivityResultContracts.StartActivityForResult, callback: (ActivityResult) -> Unit): ActivityResultLauncher<Intent>
    fun checkSelfPermission(permission: String): Int
    fun requestAllPermissions(permissions: Array<String>, requestCode: Int)
    fun getMainCharacterManager(): CharacterManager
    //fun getMainLayoutInflater(): LayoutInflater
}

interface ChatHelperHandler {
    fun getCurrentDBSessionID(): String?
    fun setCurrentDBSessionID(sessionID: String)
    fun setEditingMessagePosition(position: Int?)
    fun createNewSessionFromHere(position: Int)
    fun restoreSessionData(sessionData: JSONObject)
}

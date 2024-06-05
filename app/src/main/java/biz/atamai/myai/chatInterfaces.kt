package biz.atamai.myai

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import biz.atamai.myai.databinding.ActivityMainBinding

// THESE ARE USED because passing between different classes (files) became quite challenging
// we have multiple rounded dependencies and we need to pass data between them

interface MainHandler {
    fun getMainActivity(): Activity
    fun getMainActivityContext(): Context
    fun getMainBinding(): ActivityMainBinding
    fun getMainBindingContext(): Context
    fun handleTextMessage(message: String, attachedImageLocations: List<String> = listOf(), attachedFiles: List<Uri> = listOf())
    fun addMessageToChat(message: String, attachedImageLocations: List<String> = listOf(), attachedFiles: List<Uri> = listOf()): ChatItem
    fun showProgressBar(message: String = "")
    fun hideProgressBar()
    fun executeOnUIThread(action: Runnable)
    fun getCurrentAICharacter(): String
    fun setRecordButtonImageResource(resourceId: Int)
    fun createToastMessage(message: String, duration: Int = Toast.LENGTH_SHORT)
    fun getImagePreviewContainer(): LinearLayout
    fun getScrollViewPreview(): HorizontalScrollView
    fun registerForActivityResult(contract: ActivityResultContracts.StartActivityForResult, callback: (ActivityResult) -> Unit): ActivityResultLauncher<Intent>

}

interface ChatHelperHandler {
    fun getCurrentDBSessionID(): String?
    fun setEditingMessagePosition(position: Int?)
    fun createNewSessionFromHere(position: Int)
}

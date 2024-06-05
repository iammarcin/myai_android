package biz.atamai.myai

import android.content.Context
import android.net.Uri
import android.widget.Toast
import biz.atamai.myai.databinding.ActivityMainBinding

// THESE ARE USED because passing between different classes (files) became quite challenging
// we have multiple rounded dependencies and we need to pass data between them

interface MainHandler {
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

}

interface ChatHelperHandler {
    fun getCurrentDBSessionID(): String?
    fun setEditingMessagePosition(position: Int?)
    fun createNewSessionFromHere(position: Int)
}

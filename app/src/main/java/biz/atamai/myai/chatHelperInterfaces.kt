package biz.atamai.myai

import android.net.Uri

interface MainHandler {
    fun handleTextMessage(message: String, attachedImageLocations: List<String> = listOf(), attachedFiles: List<Uri> = listOf())
    fun showProgressBar(message: String = "")
    fun hideProgressBar()
    fun executeOnUIThread(action: Runnable)

}

interface ChatHelperHandler {
    fun getCurrentDBSessionID(): String?
    fun setEditingMessagePosition(position: Int?)
    fun createNewSessionFromHere(position: Int)
}

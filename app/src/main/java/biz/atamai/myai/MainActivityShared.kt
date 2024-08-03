package biz.atamai.myai

// MainActivityShared.kt

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MainActivityShared : ViewModel() {

    val message = MutableLiveData<String>()
    // Other shared data and functions

    fun sendMessage() {
        println("Sending message: ${message.value}")
        // Handle send message logic
    }

}
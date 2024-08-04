package biz.atamai.myai

// MainActivityChatStandard.kt

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.activity.viewModels
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

import biz.atamai.myai.databinding.ActivityMainChatStandardBinding

class MainActivityChatStandard : MainActivityBaseFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = super.onCreateView(inflater, container, savedInstanceState)

        // Customize the UI for standard chat mode
        //binding.chatContainer.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT

        // Other customizations

        return view
    }

    override fun handleTextMessage(
        message: String,
        attachedImageLocations: List<String>,
        attachedFiles: List<Uri>,
        gpsLocationMessage: Boolean
    ) {
        TODO("Not yet implemented")
    }

    override fun addMessageToChat(
        message: String,
        attachedImageLocations: List<String>,
        attachedFiles: List<Uri>,
        gpsLocationMessage: Boolean
    ): ChatItem {
        TODO("Not yet implemented")
    }


}
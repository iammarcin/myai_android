package biz.atamai.myai

// MainActivityChatStandard.kt

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.activity.viewModels
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

import biz.atamai.myai.databinding.ActivityMainChatStandardBinding

class MainActivityChatStandard(private val mainHandler: MainHandler) : Fragment(){

    private lateinit var binding: ActivityMainChatStandardBinding
    private val chatViewModel: MainActivityShared by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ActivityMainChatStandardBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner

        setupListeners()
        setupChatAdapter()

        return binding.root
    }

    private fun setupListeners() {
        binding.btnSend.setOnClickListener {
            chatViewModel.sendMessage()
        }

        binding.btnAttach.setOnClickListener {
            (activity as MainActivity).fileAttachmentHandler.openFileChooser()
        }

        // Other listeners...
    }

    fun setChatAdapterHandler(chatAdapterHandler: ChatAdapterHandler) {
        this.chatAdapterHandler = chatAdapterHandler
    }

    private fun setupChatAdapter() {
        val chatAdapter = ChatAdapter(
            mainHandler.chatItems,
            ConfigurationManager.getAppModeApiUrl(),
            requireContext(),
            (activity as MainActivity).audioPlayerManager,
        ) { position, message ->
            (activity as MainActivity).chatHelper.startEditingMessage(position, message)
        }

        binding.chatContainer.adapter = chatAdapter
    }

}
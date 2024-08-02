package biz.atamai.myai

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import biz.atamai.myai.databinding.ActivityMainChatStandardBinding

class MainActivityChatStandard : Fragment(){

    private lateinit var binding: ActivityMainChatStandardBinding
    private val chatViewModel: MainActivityShared by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ActivityMainChatStandardBinding.inflate(inflater, container, false)
        binding.viewModel = chatViewModel
        binding.lifecycleOwner = viewLifecycleOwner
        return binding.root
    }

}
package biz.atamai.myai

import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import biz.atamai.myai.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val fileAttachmentHandler = FileAttachmentHandler(this)
    private val chatItems: MutableList<ChatItem> = mutableListOf()
    private lateinit var chatAdapter: ChatAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)

        setupListeners()
        setupChatAdapter()
    }

    private fun setupChatAdapter() {
        chatAdapter = ChatAdapter(chatItems)
        binding.chatContainer.adapter = chatAdapter
    }

    private fun scrollToEnd() {
        binding.chatContainer.scrollToPosition(chatItems.size - 1)
    }

    // those functions are here as file preview (at the bottom when attaching files, but before sending them)

    fun addFilePreview(uri: Uri) {
        val mimeType = contentResolver.getType(uri)

        val frameLayout = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 80.toPx()).apply {
                marginStart = 8.toPx()
                marginEnd = 8.toPx()
            }
        }

        if (mimeType?.startsWith("image/") == true) {
            // Handle image files
            val imageView = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT)
                scaleType = ImageView.ScaleType.CENTER_CROP
                adjustViewBounds = true
                setImageURI(uri)
                tag = uri
            }
            frameLayout.addView(imageView)
        } else {
            // Handle non-image files
            // if its audio - add it directly to chat - so we can listen to it or transcribe it
            if (mimeType?.startsWith("audio/") == true) {
                addMessageToChat("", listOf(), listOf(uri))
                return
            } else {
                // Handle other files
                val placeholder = View(this).apply {
                    layoutParams = FrameLayout.LayoutParams(50.toPx(), 50.toPx()).apply {
                        gravity = Gravity.CENTER
                    }
                    setBackgroundColor(
                        ContextCompat.getColor(
                            context,
                            R.color.attached_file_placeholder
                        )
                    ) // Replace with your placeholder color
                    tag = uri
                }
                frameLayout.addView(placeholder)
            }
        }

        val removeButton = ImageButton(this).apply {
            layoutParams = FrameLayout.LayoutParams(24.toPx(), 24.toPx()).apply {
                gravity = Gravity.TOP or Gravity.END
            }
            setImageResource(R.drawable.ic_close)  // Ensure you have a 'ic_close' drawable in your resources
            background = ContextCompat.getDrawable(context, R.drawable.rounded_button_background) // Optional: customize as needed
            setOnClickListener {
                binding.imagePreviewContainer.removeView(frameLayout)
                if (binding.imagePreviewContainer.childCount == 0) {
                    binding.scrollViewPreview.visibility = View.GONE
                }
            }
        }

        frameLayout.addView(removeButton)
        binding.imagePreviewContainer.addView(frameLayout)
        binding.scrollViewPreview.visibility = View.VISIBLE
    }

    private fun setupListeners() {
        // attach button
        binding.btnAttach.setOnClickListener {
            fileAttachmentHandler.openFileChooser()
        }

        // for situation where we start typing in edit text - we want other stuff to disappear
        binding.editTextMessage.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.layoutRecord.visibility = View.GONE
                binding.btnSend.visibility = View.VISIBLE
                (binding.editTextMessage.layoutParams as LinearLayout.LayoutParams).weight = 0.7f
                (binding.rightAttachmentBar.layoutParams as LinearLayout.LayoutParams).weight = 0.3f
            }
        }

        // main send button
        binding.btnSend.setOnClickListener {
            val message = binding.editTextMessage.text.toString()
            val attachedImageUris = mutableListOf<Uri>()
            val attachedFilePaths = mutableListOf<Uri>()

            for (i in 0 until binding.imagePreviewContainer.childCount) {
                val frameLayout = binding.imagePreviewContainer.getChildAt(i) as FrameLayout
                // if it's an image
                if (frameLayout.getChildAt(0) is ImageView) {
                    val imageView = frameLayout.getChildAt(0) as ImageView
                    attachedImageUris.add(imageView.tag as Uri)
                } else {
                    // if it's a file
                    val placeholder = frameLayout.getChildAt(0) as View
                    attachedFilePaths.add(placeholder.tag as Uri)

                }
            }

            addMessageToChat(message, attachedImageUris, attachedFilePaths)

            binding.editTextMessage.setText("")
            binding.imagePreviewContainer.removeAllViews()
            binding.scrollViewPreview.visibility = View.GONE
        }


    }

    fun Int.toPx(): Int = (this * resources.displayMetrics.density).toInt()
    // sending data to chat adapter
    private fun addMessageToChat(message: String, attachedImageUris: List<Uri>, attachedFiles: List<Uri> = listOf()) {
        val chatItem = ChatItem(message = message, isUserMessage = true, imageUris = attachedImageUris, fileNames = attachedFiles)
        chatItems.add(chatItem)
        chatAdapter.notifyItemInserted(chatItems.size - 1)
        scrollToEnd()
    }
}

package biz.atamai.myai

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class FileAttachmentHandler(
    private val activity: MainActivity,
    private val imagePreviewContainer: LinearLayout,
    private val scrollViewPreview: HorizontalScrollView
) {
    private val fileChooserLauncher: ActivityResultLauncher<Intent> = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        handleActivityResult(result)
    }

    fun openFileChooser() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "audio/*"))
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        fileChooserLauncher.launch(Intent.createChooser(intent, "Select File"))
    }

    private fun handleActivityResult(result: ActivityResult) {
        if (result.resultCode == Activity.RESULT_OK) {
            val clipData = result.data?.clipData
            if (clipData != null) {
                for (i in 0 until clipData.itemCount) {
                    val uri = clipData.getItemAt(i).uri
                    addFilePreview(uri)
                }
            } else {
                result.data?.data?.let { uri ->
                    addFilePreview(uri)
                }
            }
        }
    }

    // those functions are here as file preview (at the bottom when attaching files, but before sending them)
    private fun addFilePreview(uri: Uri) {
        val mimeType = activity.contentResolver.getType(uri)
        val frameLayout = FrameLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 80.toPx()).apply {
                marginStart = 8.toPx()
                marginEnd = 8.toPx()
            }
        }

        if (mimeType?.startsWith("image/") == true) {
            val imageView = ImageView(activity).apply {
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
                activity.addMessageToChat("", listOf(), listOf(uri))
                return
            } else {
                val placeholder = View(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(50.toPx(), 50.toPx()).apply {
                        gravity = Gravity.CENTER
                    }
                    setBackgroundColor(
                        ContextCompat.getColor(
                            activity,
                            R.color.attached_file_placeholder
                        )
                    )
                    tag = uri
                }
                frameLayout.addView(placeholder)
            }
        }

        val removeButton = ImageButton(activity).apply {
            layoutParams = FrameLayout.LayoutParams(24.toPx(), 24.toPx()).apply {
                gravity = Gravity.TOP or Gravity.END
            }
            setImageResource(R.drawable.ic_close)
            background = ContextCompat.getDrawable(activity, R.drawable.rounded_button_background)
            setOnClickListener {
                imagePreviewContainer.removeView(frameLayout)
                if (imagePreviewContainer.childCount == 0) {
                    scrollViewPreview.visibility = View.GONE
                }
            }
        }

        frameLayout.addView(removeButton)
        imagePreviewContainer.addView(frameLayout)
        scrollViewPreview.visibility = View.VISIBLE
    }

    private fun Int.toPx(): Int = (this * activity.resources.displayMetrics.density).toInt()
}

/*
class FileAttachmentHandler(private val activity: MainActivity, private val imagePreviewContainer: LinearLayout, private val scrollViewPreview: HorizontalScrollView) {

    private val fileChooserLauncher: ActivityResultLauncher<Intent> = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        handleActivityResult(result)
    }

    fun openFileChooser() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "* / *"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "audio/*"))
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)  // Allow multiple selection
        }
        fileChooserLauncher.launch(Intent.createChooser(intent, "Select File"))
    }

    private fun handleActivityResult(result: ActivityResult) {
        if (result.resultCode == Activity.RESULT_OK) {
            val clipData = result.data?.clipData

            if (clipData != null) { // Multiple items selected
                for (i in 0 until clipData.itemCount) {
                    val uri = clipData.getItemAt(i).uri
                    addFilePreview(uri)
                }
            } else { // Single item selected
                result.data?.data?.let { uri ->
                    addFilePreview(uri)
                }
            }
        }
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
                // and we dont need to do anything else here
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
}
*/
package biz.atamai.myai

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import biz.atamai.myai.databinding.ChatItemBinding

class ChatAdapter(private val chatItems: MutableList<ChatItem>) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    inner class ChatViewHolder(private val binding: ChatItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(chatItem: ChatItem) {
            binding.messageTextView.text = chatItem.message

            if (chatItem.imageUris.isNotEmpty()) {
                binding.scrollViewImages.visibility = View.VISIBLE
                binding.imageContainer.removeAllViews() // Clear old images
                for (uri in chatItem.imageUris) {
                    val imageView = ImageView(binding.root.context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.MATCH_PARENT
                        ).also {
                            it.marginEnd = 8.dpToPx(binding.root.context)
                        }
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        adjustViewBounds = true
                        setImageURI(uri)
                    }
                    binding.imageContainer.addView(imageView)
                }
            } else {
                binding.scrollViewImages.visibility = View.GONE
            }
        }
    }

    private fun Int.dpToPx(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatAdapter.ChatViewHolder {
        val binding = ChatItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatAdapter.ChatViewHolder, position: Int) {
        holder.bind(chatItems[position])
    }

    override fun getItemCount(): Int = chatItems.size
}

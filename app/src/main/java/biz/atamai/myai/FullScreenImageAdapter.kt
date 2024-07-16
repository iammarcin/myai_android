package biz.atamai.myai

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso

//used for full screen image preview when there are images in chat items
class FullScreenImageAdapter(
    private val context: Context,
    private val imageUrls: List<String>,
    private val characterName: String,
    private val characterDescription: String,
) : RecyclerView.Adapter<FullScreenImageAdapter.ImageViewHolder>() {

    inner class ImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.fullscreen_image)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_fullscreen_single_image_preview, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val imageUrl = imageUrls[position]
        // imageUrl can be either URL (attached image) or resource ID (default character image)
        if (imageUrl.startsWith("http")) {
            Picasso.get().load(imageUrl).into(holder.imageView)
        } else {
            holder.imageView.setImageResource(imageUrl.toInt())
        }
    }

    override fun getItemCount(): Int = imageUrls.size
}

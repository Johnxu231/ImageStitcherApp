package com.example.imagestitcherapp

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
// import java.io.File // This import is not actually used in this adapter, can remove if not needed

class ImageAdapter(
    private val imageUris: MutableList<Uri>,
    private val onItemClick: (position: Int) -> Unit // Add a lambda for item clicks
) : RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {

    class ImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.imageViewItem)
        val textViewFileName: TextView = view.findViewById(R.id.textViewFileName)
        val btnRemove: ImageView = view.findViewById(R.id.btnRemove)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val uri = imageUris[position]

        // Use Glide or other image loading library to load the image
        Glide.with(holder.imageView.context)
            .load(uri)
            .into(holder.imageView)

        // Display file name (optional, but good for user identification)
        // You might need to get actual file name from ContentResolver for robustness
        holder.textViewFileName.text = uri.lastPathSegment ?: "未知文件"

        // Set click listener for the remove button
        holder.btnRemove.setOnClickListener {
            val currentPosition = holder.adapterPosition
            if (currentPosition != RecyclerView.NO_POSITION) {
                imageUris.removeAt(currentPosition)
                notifyItemRemoved(currentPosition)
            }
        }

        // Set click listener for the entire item view
        holder.itemView.setOnClickListener {
            onItemClick(holder.adapterPosition) // Pass the clicked position back to MainActivity
        }
    }

    override fun getItemCount(): Int = imageUris.size

    /**
     * Method to move items in the list for drag-and-drop sorting.
     * @param fromPosition Original position
     * @param toPosition Target position
     */
    fun moveItem(fromPosition: Int, toPosition: Int) {
        // Prevent index out of bounds
        if (fromPosition < 0 || fromPosition >= imageUris.size ||
            toPosition < 0 || toPosition >= imageUris.size) {
            return
        }

        val movedItem = imageUris.removeAt(fromPosition)
        imageUris.add(toPosition, movedItem)
        notifyItemMoved(fromPosition, toPosition)
    }
}
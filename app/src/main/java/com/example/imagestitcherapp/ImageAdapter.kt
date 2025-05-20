package com.example.imagestitcherapp

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide // 推荐使用 Glide 或 Coil 来加载图片，这里以 Glide 为例，你需要添加其依赖

class ImageAdapter(private val imageUris: MutableList<Uri>) :
    RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {

    class ImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.imageViewItem)
        val btnRemove: ImageView = view.findViewById(R.id.btnRemove)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val uri = imageUris[position]
        // 使用 Glide 或其他图片加载库加载图片
        Glide.with(holder.imageView.context)
            .load(uri)
            .into(holder.imageView)

        holder.btnRemove.setOnClickListener {
            val currentPosition = holder.adapterPosition
            if (currentPosition != RecyclerView.NO_POSITION) {
                imageUris.removeAt(currentPosition)
                notifyItemRemoved(currentPosition)
            }
        }
    }

    override fun getItemCount(): Int = imageUris.size

    // 用于拖拽排序的方法（可选，需要集成 ItemTouchHelper）
    fun moveItem(fromPosition: Int, toPosition: Int) {
        val movedItem = imageUris.removeAt(fromPosition)
        imageUris.add(toPosition, movedItem)
        notifyItemMoved(fromPosition, toPosition)
    }
}
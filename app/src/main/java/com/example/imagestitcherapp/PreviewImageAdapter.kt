package com.example.imagestitcherapp

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class PreviewImageAdapter(private val imageUris: List<Uri>) :
    RecyclerView.Adapter<PreviewImageAdapter.PreviewImageViewHolder>() {

    class PreviewImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.imageViewPreviewItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreviewImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_preview_image, parent, false) // Use a simple item layout
        return PreviewImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PreviewImageViewHolder, position: Int) {
        val uri = imageUris[position]
        Glide.with(holder.imageView.context)
            .load(uri)
            .into(holder.imageView)
    }

    override fun getItemCount(): Int = imageUris.size
}
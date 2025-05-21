package com.example.imagestitcherapp

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View // Make sure this is imported
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.imagestitcherapp.databinding.ActivityImagePreviewBinding

class ImagePreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImagePreviewBinding
    private lateinit var previewAdapter: PreviewImageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        binding = ActivityImagePreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val imageUris = intent.getParcelableArrayListExtra<Uri>(EXTRA_IMAGE_URIS)
        val startPosition = intent.getIntExtra(EXTRA_START_POSITION, 0) // Get starting position

        if (imageUris.isNullOrEmpty()) {
            finish()
            return
        }

        setupRecyclerView(imageUris, startPosition) // Pass startPosition to setup
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            val controller = window.insetsController
            controller?.let {
                it.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN)
        }
    }


    private fun setupRecyclerView(imageUris: ArrayList<Uri>, startPosition: Int) {
        previewAdapter = PreviewImageAdapter(imageUris)
        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerViewPreview.layoutManager = layoutManager
        binding.recyclerViewPreview.adapter = previewAdapter

        // Scroll to the clicked position
        if (startPosition >= 0 && startPosition < imageUris.size) {
            binding.recyclerViewPreview.scrollToPosition(startPosition)
        }
    }

    companion object {
        const val EXTRA_IMAGE_URIS = "extra_image_uris"
        const val EXTRA_START_POSITION = "extra_start_position" // New extra for starting position
    }
}
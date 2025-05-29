package com.example.imagestitcherapp

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.imagestitcherapp.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt // 导入 roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val selectedImageUris = mutableListOf<Uri>() // 使用 mutableListOf 而不是 ArrayList
    private lateinit var imageAdapter: ImageAdapter

    // Define a target width for downsampling. This could be screen width or a fixed value.
    // 假设我们希望拼接后的图片的宽度不超过屏幕宽度，或者一个合理的最大值，例如 1080px
    private val TARGET_STITCH_WIDTH = 1080 // You can adjust this value

    // 权限请求启动器
    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions.entries.all { it.value }
            if (granted) {
                Toast.makeText(this, getString(R.string.permissions_granted), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.permissions_denied), Toast.LENGTH_LONG).show()
            }
        }

    // 图片选择启动器
    private val pickImagesLauncher =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri>? ->
            uris?.let {
                val newUrisToAdd = it.filter { newUri -> !selectedImageUris.contains(newUri) }
                val oldSize = selectedImageUris.size
                selectedImageUris.addAll(newUrisToAdd)

                if (newUrisToAdd.isNotEmpty()) {
                    imageAdapter.notifyItemRangeInserted(oldSize, newUrisToAdd.size)
                } else {
                    Toast.makeText(this, getString(R.string.no_new_images_added), Toast.LENGTH_SHORT).show() // This string should also be extracted
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        checkAndRequestPermissions()

        binding.btnSelectImages.setOnClickListener {
            pickImages()
        }

        binding.btnPreviewImages.setOnClickListener {
            if (selectedImageUris.isEmpty()) {
                Toast.makeText(this, getString(R.string.no_images_selected), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // 启动 ImagePreviewActivity 来全屏预览
            val intent = Intent(this, ImagePreviewActivity::class.java).apply {
                // 将 ArrayList<Uri> 放入 Intent
                putParcelableArrayListExtra(ImagePreviewActivity.EXTRA_IMAGE_URIS, ArrayList(selectedImageUris))
            }
            startActivity(intent)
        }

        binding.btnStitchImages.setOnClickListener {
            if (selectedImageUris.size < 2) {
                Toast.makeText(this, getString(R.string.at_least_two_images), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            stitchImages(stitchForSaving = true)
        }
    }

    private fun setupRecyclerView() {
        imageAdapter = ImageAdapter(selectedImageUris) { position ->
            launchPreviewActivity(position)
        }
        binding.recyclerViewImages.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewImages.adapter = imageAdapter

        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition
                imageAdapter.moveItem(fromPosition, toPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Not used for swiping
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.alpha = 0.5f
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.alpha = 1.0f
            }
        }
        val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
        itemTouchHelper.attachToRecyclerView(binding.recyclerViewImages)
    }

    private fun launchPreviewActivity(startPosition: Int) {
        if (selectedImageUris.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_images_to_preview), Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, ImagePreviewActivity::class.java).apply {
            putParcelableArrayListExtra(ImagePreviewActivity.EXTRA_IMAGE_URIS, ArrayList(selectedImageUris))
            putExtra(ImagePreviewActivity.EXTRA_START_POSITION, startPosition)
        }
        startActivity(intent)
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) // Consider video if needed
                != PackageManager.PERMISSION_GRANTED) {
                // permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun pickImages() {
        pickImagesLauncher.launch("image/*")
    }

    // Data class to hold calculated image dimensions and inSampleSize
    private data class ImageDecodeInfo(
        val uri: Uri,
        val originalWidth: Int,
        val originalHeight: Int,
        val inSampleSize: Int,
        val sampledWidth: Int,
        val sampledHeight: Int
    )

    // 核心拼接逻辑，可选择是否保存到文件
    private fun stitchImages(stitchForSaving: Boolean) {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnStitchImages.isEnabled = false
        binding.btnPreviewImages.isEnabled = false
        binding.btnSelectImages.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            var stitchedBitmap: Bitmap? = null
            try {
                val imageDecodeInfoList = mutableListOf<ImageDecodeInfo>()
                var maxWidthAfterSampling = 0
                var totalHeightAfterSampling = 0

                // Phase 1: Get original dimensions and calculate inSampleSize for each image
                for (uri in selectedImageUris) {
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true // Decode only bounds, not pixels
                    }
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        BitmapFactory.decodeStream(inputStream, null, options)
                    }

                    val originalWidth = options.outWidth
                    val originalHeight = options.outHeight

                    if (originalWidth == 0 || originalHeight == 0) {
                        Log.e("ImageStitcher", getString(R.string.error_loading_image, uri.toString()))
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, getString(R.string.unable_to_load_image), Toast.LENGTH_SHORT).show()
                        }
                        restoreUiState()
                        // Ensure no partial state is left
                        stitchedBitmap?.recycle()
                        return@launch
                    }

                    // Calculate inSampleSize
                    var inSampleSize = 1
                    if (originalWidth > TARGET_STITCH_WIDTH) {
                        inSampleSize = (originalWidth.toFloat() / TARGET_STITCH_WIDTH.toFloat()).roundToInt()
                    }
                    inSampleSize = inSampleSize.coerceAtLeast(1) // Ensure inSampleSize is at least 1

                    // Calculate sampled dimensions
                    val sampledWidth = originalWidth / inSampleSize
                    val sampledHeight = originalHeight / inSampleSize

                    imageDecodeInfoList.add(ImageDecodeInfo(uri, originalWidth, originalHeight, inSampleSize, sampledWidth, sampledHeight))

                    // Update max width and total height based on sampled dimensions
                    maxWidthAfterSampling = maxOf(maxWidthAfterSampling, sampledWidth)
                    totalHeightAfterSampling += sampledHeight
                }

                if (imageDecodeInfoList.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, getString(R.string.no_stitchable_images), Toast.LENGTH_SHORT).show()
                    }
                    restoreUiState()
                    return@launch
                }

                // Phase 2: Create the stitched bitmap and draw sampled images
                stitchedBitmap = Bitmap.createBitmap(maxWidthAfterSampling, totalHeightAfterSampling, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(stitchedBitmap)
                var currentY = 0

                for (info in imageDecodeInfoList) {
                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = info.inSampleSize // Use the pre-calculated inSampleSize
                    }

                    // Re-open InputStream for actual decoding
                    val bitmap = contentResolver.openInputStream(info.uri)?.use { inputStream ->
                        BitmapFactory.decodeStream(inputStream, null, decodeOptions)
                    }

                    if (bitmap != null) {
                        // Scale to the common maximum width after sampling, maintaining aspect ratio
                        val finalScaledBitmap = if (bitmap.width != maxWidthAfterSampling) {
                            val newHeight = (bitmap.height * (maxWidthAfterSampling.toFloat() / bitmap.width)).roundToInt()
                            Bitmap.createScaledBitmap(bitmap, maxWidthAfterSampling, newHeight, true)
                        } else {
                            bitmap
                        }

                        canvas.drawBitmap(finalScaledBitmap, 0f, currentY.toFloat(), null)
                        currentY += finalScaledBitmap.height

                        // Recycle bitmaps
                        if (finalScaledBitmap != bitmap) { // If scaling created a new bitmap, recycle the original decoded one
                            bitmap.recycle()
                        }
                        finalScaledBitmap.recycle() // Always recycle the bitmap used for drawing
                    } else {
                        Log.e("ImageStitcher", getString(R.string.error_loading_image, info.uri.toString()))
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, getString(R.string.unable_to_load_image), Toast.LENGTH_SHORT).show()
                        }
                        restoreUiState()
                        stitchedBitmap?.recycle() // Also recycle any partially created stitched bitmap
                        return@launch
                    }
                }

                withContext(Dispatchers.Main) {
                    if (stitchForSaving) {
                        val savedUri = saveBitmapToGallery(stitchedBitmap!!)
                        if (savedUri != null) {
                            Toast.makeText(this@MainActivity, getString(R.string.save_successful), Toast.LENGTH_LONG).show()
                            selectedImageUris.clear()
                            imageAdapter.notifyDataSetChanged()
                        } else {
                            Toast.makeText(this@MainActivity, getString(R.string.save_failed), Toast.LENGTH_SHORT).show()
                        }
                    }
                    restoreUiState()
                }

            } catch (e: Exception) {
                Log.e("ImageStitcher", getString(R.string.error_stitching_or_saving, e.message ?: "Unknown error"), e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, getString(R.string.operation_failed, e.message ?: "Unknown error"), Toast.LENGTH_LONG).show()
                    restoreUiState()
                }
            } finally {
                stitchedBitmap?.recycle() // Ensure stitchedBitmap is recycled even on error
            }
        }
    }

    private fun restoreUiState() {
        binding.btnStitchImages.isEnabled = true
        binding.btnPreviewImages.isEnabled = true
        binding.btnSelectImages.isEnabled = true
        binding.progressBar.visibility = View.GONE
    }


    private fun saveBitmapToGallery(bitmap: Bitmap): Uri? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "stitched_image_$timestamp.png"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/StitchedImages")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        var uri: Uri? = null
        var outputStream: OutputStream? = null
        try {
            val contentResolver = application.contentResolver
            uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri == null) {
                throw IOException("Failed to create new MediaStore record.")
            }

            outputStream = contentResolver.openOutputStream(uri)
            if (outputStream == null) {
                throw IOException("Failed to get output stream for $uri")
            }

            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.flush()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(uri, contentValues, null, null)
            }
            return uri
        } catch (e: IOException) {
            Log.e("SaveBitmap", getString(R.string.error_stitching_or_saving, e.message ?: "Unknown error"), e)
            uri?.let {
                application.contentResolver.delete(it, null, null)
            }
            return null
        } finally {
            try {
                outputStream?.close()
            } catch (e: IOException) {
                Log.e("SaveBitmap", getString(R.string.error_closing_stream, e.message ?: "Unknown error"), e)
            }
        }
    }
}
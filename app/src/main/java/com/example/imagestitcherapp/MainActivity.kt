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

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val selectedImageUris = mutableListOf<Uri>()
    private lateinit var imageAdapter: ImageAdapter

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
                selectedImageUris.clear()
                selectedImageUris.addAll(it)
                imageAdapter.notifyDataSetChanged()
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
            stitchImages(stitchForSaving = true) // 调用拼接并保存的方法
        }
    }

    private fun setupRecyclerView() {
        // Initialize ImageAdapter with a click listener
        imageAdapter = ImageAdapter(selectedImageUris) { position ->
            // This lambda is called when an item in the RecyclerView is clicked
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

            // Corrected line
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
            putExtra(ImagePreviewActivity.EXTRA_START_POSITION, startPosition) // Pass starting position
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

    // 核心拼接逻辑，可选择是否保存到文件
    private fun stitchImages(stitchForSaving: Boolean) {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnStitchImages.isEnabled = false
        binding.btnPreviewImages.isEnabled = false
        binding.btnSelectImages.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            var stitchedBitmap: Bitmap? = null
            try {
                val bitmaps = mutableListOf<Bitmap>()
                var maxWidth = 0
                var totalHeight = 0

                // 1. 加载所有图片并计算总尺寸
                for (uri in selectedImageUris) {
                    val bitmap = contentResolver.openInputStream(uri)?.use { inputStream ->
                        BitmapFactory.decodeStream(inputStream)
                    }
                    if (bitmap != null) {
                        bitmaps.add(bitmap)
                        maxWidth = maxOf(maxWidth, bitmap.width)
                        totalHeight += bitmap.height
                    } else {
                        Log.e("ImageStitcher", getString(R.string.error_loading_image, uri.toString()))
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, getString(R.string.unable_to_load_image), Toast.LENGTH_SHORT).show()
                        }
                        restoreUiState()
                        bitmaps.forEach { it.recycle() }
                        return@launch
                    }
                }

                if (bitmaps.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, getString(R.string.no_stitchable_images), Toast.LENGTH_SHORT).show()
                    }
                    restoreUiState()
                    return@launch
                }

                // 2. 创建新的拼接位图
                stitchedBitmap = Bitmap.createBitmap(maxWidth, totalHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(stitchedBitmap)
                var currentY = 0

                // 3. 将所有图片绘制到拼接位图上
                for (bitmap in bitmaps) {
                    val scaledBitmap = if (bitmap.width != maxWidth) {
                        Bitmap.createScaledBitmap(bitmap, maxWidth, (bitmap.height * maxWidth.toFloat() / bitmap.width).toInt(), true)
                    } else {
                        bitmap
                    }
                    canvas.drawBitmap(scaledBitmap, 0f, currentY.toFloat(), null)
                    currentY += scaledBitmap.height
                    if (scaledBitmap != bitmap) {
                        bitmap.recycle()
                    }
                    scaledBitmap.recycle()
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
                Log.e("ImageStitcher", getString(R.string.error_stitching_or_saving, e.message ?: "未知错误"), e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, getString(R.string.operation_failed, e.message ?: "未知错误"), Toast.LENGTH_LONG).show()
                    restoreUiState()
                }
            } finally {
                stitchedBitmap?.recycle()
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
            Log.e("SaveBitmap", getString(R.string.error_stitching_or_saving, e.message ?: "未知错误"), e)
            uri?.let {
                application.contentResolver.delete(it, null, null)
            }
            return null
        } finally {
            try {
                outputStream?.close()
            } catch (e: IOException) {
                Log.e("SaveBitmap", getString(R.string.error_closing_stream, e.message ?: "未知错误"), e)
            }
        }
    }
}
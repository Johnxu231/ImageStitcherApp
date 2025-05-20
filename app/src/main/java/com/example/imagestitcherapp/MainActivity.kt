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
                Toast.makeText(this, "存储权限已授予", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "存储权限被拒绝，无法保存图片", Toast.LENGTH_LONG).show()
            }
        }

    // 图片选择启动器
    private val pickImagesLauncher =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri>? ->
            uris?.let {
                selectedImageUris.clear() // 清空之前选择的，或者 append 都可以
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

        binding.btnStitchImages.setOnClickListener {
            if (selectedImageUris.size < 2) {
                Toast.makeText(this, "请至少选择两张图片进行拼接", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            stitchImages()
        }
    }

    private fun setupRecyclerView() {
        imageAdapter = ImageAdapter(selectedImageUris)
        binding.recyclerViewImages.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewImages.adapter = imageAdapter

        // 添加拖拽排序功能
        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
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
                // 不处理滑动删除
            }
        }
        val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
        itemTouchHelper.attachToRecyclerView(binding.recyclerViewImages)
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) { // Android 9 及以下
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        // Android 10 (Q) 及更高版本，READ_EXTERNAL_STORAGE 已足够，WRITE_EXTERNAL_STORAGE 几乎不再需要
        // Android 13 (TIRAMISU) 及更高版本，READ_MEDIA_IMAGES 优先于 READ_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // Android 10, 11, 12
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
        // "image/*" 允许选择所有图片类型
        // ActivityResultContracts.GetMultipleContents() 会自动处理文件 URI 权限
        pickImagesLauncher.launch("image/*")
    }

    private fun stitchImages() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnStitchImages.isEnabled = false
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
                        Log.e("ImageStitcher", "无法加载图片: $uri")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "部分图片加载失败，请重试", Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }
                }

                // 2. 创建新的拼接位图
                stitchedBitmap = Bitmap.createBitmap(maxWidth, totalHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(stitchedBitmap)
                var currentY = 0

                // 3. 将所有图片绘制到拼接位图上
                for (bitmap in bitmaps) {
                    // 如果图片宽度与最大宽度不一致，可以居中或拉伸
                    val left = (maxWidth - bitmap.width) / 2
                    canvas.drawBitmap(bitmap, left.toFloat(), currentY.toFloat(), null)
                    currentY += bitmap.height
                    bitmap.recycle() // 及时回收源位图
                }

                // 4. 保存拼接后的图片
                val savedUri = saveBitmapToGallery(stitchedBitmap)

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    if (savedUri != null) {
                        Toast.makeText(this@MainActivity, "长图已保存到相册", Toast.LENGTH_LONG).show()
                        // 可以在这里显示拼接后的图片，例如：
                        // binding.imageViewResult.setImageURI(savedUri)
                        // binding.imageViewResult.visibility = View.VISIBLE
                        selectedImageUris.clear() // 清空列表
                        imageAdapter.notifyDataSetChanged()
                    } else {
                        Toast.makeText(this@MainActivity, "保存长图失败", Toast.LENGTH_SHORT).show()
                    }
                    binding.btnStitchImages.isEnabled = true
                    binding.btnSelectImages.isEnabled = true
                }

            } catch (e: Exception) {
                Log.e("ImageStitcher", "拼接或保存图片时出错: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "拼接失败: ${e.message}", Toast.LENGTH_LONG).show()
                    binding.btnStitchImages.isEnabled = true
                    binding.btnSelectImages.isEnabled = true
                }
            } finally {
                stitchedBitmap?.recycle() // 确保最终回收拼接位图
            }
        }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap): Uri? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "stitched_image_$timestamp.png"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            // 对于 Android Q (API 29) 及更高版本，使用 MediaStore.MediaColumns.RELATIVE_PATH
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/StitchedImages")
                put(MediaStore.Images.Media.IS_PENDING, 1) // 标记为待处理
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
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0) // 取消待处理标记
                contentResolver.update(uri, contentValues, null, null)
            }
            return uri
        } catch (e: IOException) {
            Log.e("SaveBitmap", "保存失败: ${e.message}", e)
            // 如果出错，删除部分创建的 MediaStore 记录
            uri?.let {
                application.contentResolver.delete(it, null, null)
            }
            return null
        } finally {
            try {
                outputStream?.close()
            } catch (e: IOException) {
                Log.e("SaveBitmap", "关闭流失败: ${e.message}", e)
            }
        }
    }
}
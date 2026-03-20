package com.example.androidmusic3.ui

import android.Manifest
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import com.example.androidmusic3.MediaManager
import com.example.androidmusic3.R
import com.example.androidmusic3.model.AudioFile
import com.example.androidmusic3.util.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImportActivity : AppCompatActivity(), LoaderManager.LoaderCallbacks<android.database.Cursor> {

    private lateinit var listView: android.widget.ListView
    private var audioFiles = mutableListOf<AudioFile>()
    private lateinit var adapter: AudioFileAdapter
    private var currentFolder: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_import)

        listView = findViewById(R.id.listViewAudio)
        val btnBack = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnBack)
        val btnImport = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnImport)
        val btnBrowse = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnBrowse)

        adapter = AudioFileAdapter()
        listView.adapter = adapter

        btnBack.setOnClickListener {
            finish()
        }

        btnImport.setOnClickListener {
            importSelectedFiles()
        }

        btnBrowse.setOnClickListener {
            browseForFiles()
        }

        // Check permissions
        if (!PermissionHelper.checkStoragePermissions(this)) {
            PermissionHelper.requestStoragePermissions(this)
        } else {
            loadAudioFiles()
        }
    }

    private fun loadAudioFiles() {
        LoaderManager.getInstance(this).initLoader(0, null, this)
    }

    private fun browseForFiles() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "audio/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(Intent.createChooser(intent, "Select Audio Files"), REQUEST_PICK_FILES)
    }

    private fun importSelectedFiles() {
        val selectedFiles = mutableListOf<AudioFile>()
        for (i in 0 until listView.count) {
            if (i < audioFiles.size && listView.isItemChecked(i)) {
                selectedFiles.add(audioFiles[i])
            }
        }

        if (selectedFiles.isEmpty()) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("No Files Selected")
                .setMessage("Please select at least one audio file to import.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        // 在后台线程处理文件复制
        CoroutineScope(Dispatchers.IO).launch {
            val mediaManager = MediaManager.getInstance(this@ImportActivity)
            try {
                mediaManager.addAudioFiles(selectedFiles)

                withContext(Dispatchers.Main) {
                    androidx.appcompat.app.AlertDialog.Builder(this@ImportActivity)
                        .setTitle("Import Complete")
                        .setMessage("Successfully imported ${selectedFiles.size} audio file(s).")
                        .setPositiveButton("OK") { _, _ ->
                            finish()
                        }
                        .show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    androidx.appcompat.app.AlertDialog.Builder(this@ImportActivity)
                        .setTitle("Import Failed")
                        .setMessage("Failed to import files: ${e.message}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<android.database.Cursor> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )

        return CursorLoader(
            this,
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            if (currentFolder != null) "${MediaStore.Audio.Media.DATA} LIKE ?" else null,
            if (currentFolder != null) arrayOf("$currentFolder%") else null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )
    }

    override fun onLoadFinished(loader: Loader<android.database.Cursor>, data: android.database.Cursor?) {
        audioFiles.clear()
        data?.let { cursor ->
            try {
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                while (cursor.moveToNext()) {
                    try {
                        val id = cursor.getLong(idColumn)
                        val uri = android.net.Uri.withAppendedPath(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            id.toString()
                        )
                        val title = cursor.getString(titleColumn)
                        val artist = cursor.getString(artistColumn)
                        val duration = cursor.getLong(durationColumn)
                        val filePath = cursor.getString(dataColumn)

                        audioFiles.add(
                            AudioFile(
                                id = id,
                                uri = uri,
                                title = title,
                                artist = artist,
                                duration = duration,
                                filePath = filePath,
                                isExtracted = false
                            )
                        )
                    } catch (e: Exception) {
                        // Skip invalid audio records
                    }
                }
            } catch (e: Exception) {
                // Handle cursor columns error
            }
        }
        adapter.notifyDataSetChanged()
    }

    override fun onLoaderReset(loader: Loader<android.database.Cursor>) {
        audioFiles.clear()
        adapter.notifyDataSetChanged()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_FILES && resultCode == RESULT_OK) {
            data?.let {
                val uris = mutableListOf<android.net.Uri>()
                try {
                    it.clipData?.let { clipData ->
                        for (i in 0 until clipData.itemCount) {
                            uris.add(clipData.getItemAt(i).uri)
                        }
                    } ?: it.data?.let { uri ->
                        uris.add(uri)
                    }

                    if (uris.isEmpty()) {
                        return@let
                    }

                    // 在后台线程处理文件复制
                    CoroutineScope(Dispatchers.IO).launch {
                        val mediaManager = MediaManager.getInstance(this@ImportActivity)
                        val files = mutableListOf<AudioFile>()
                        val failedFiles = mutableListOf<String>()

                        uris.forEach { uri ->
                            try {
                                android.util.Log.d("ImportActivity", "Processing URI: $uri")

                                // 复制文件到应用私有目录
                                val copiedFile = copyFileToAppDir(uri)
                                if (copiedFile != null) {
                                    android.util.Log.d("ImportActivity", "File copied successfully: ${copiedFile.title}")
                                    files.add(copiedFile)
                                } else {
                                    android.util.Log.w("ImportActivity", "Failed to copy file, trying to use original URI")
                                    // 如果复制失败，尝试使用原始URI
                                    try {
                                        val audioFile = createAudioFileFromUri(uri)
                                        if (audioFile != null) {
                                            files.add(audioFile)
                                        } else {
                                            failedFiles.add(uri.toString())
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("ImportActivity", "Failed to create AudioFile from URI", e)
                                        failedFiles.add(uri.toString())
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("ImportActivity", "Error processing file: ${e.message}", e)
                                failedFiles.add(uri.toString())
                            }
                        }

                        android.util.Log.d("ImportActivity", "Total files to import: ${files.size}, Failed: ${failedFiles.size}")

                        withContext(Dispatchers.Main) {
                            if (files.isNotEmpty()) {
                                try {
                                    mediaManager.addAudioFiles(files)

                                    val message = if (failedFiles.isEmpty()) {
                                        "Successfully imported ${files.size} audio file(s)."
                                    } else {
                                        "Successfully imported ${files.size} audio file(s).\n\nFailed to import ${failedFiles.size} file(s)."
                                    }

                                    androidx.appcompat.app.AlertDialog.Builder(this@ImportActivity)
                                        .setTitle("Import Complete")
                                        .setMessage(message)
                                        .setPositiveButton("OK") { _, _ ->
                                            finish()
                                        }
                                        .show()
                                } catch (e: Exception) {
                                    android.util.Log.e("ImportActivity", "Failed to add files to media manager: ${e.message}", e)
                                    androidx.appcompat.app.AlertDialog.Builder(this@ImportActivity)
                                        .setTitle("Import Failed")
                                        .setMessage("Failed to import files: ${e.message}")
                                        .setPositiveButton("OK", null)
                                        .show()
                                }
                            } else {
                                android.util.Log.w("ImportActivity", "No valid files to import")
                                val errorMessage = if (failedFiles.isNotEmpty()) {
                                    "Unable to process the selected audio files. The files may not be supported or are corrupted.\n\nError: ${failedFiles.size} file(s) failed to import."
                                } else {
                                    "No valid audio files were selected. Please try again."
                                }
                                androidx.appcompat.app.AlertDialog.Builder(this@ImportActivity)
                                    .setTitle("Import Failed")
                                    .setMessage(errorMessage)
                                    .setPositiveButton("OK", null)
                                    .show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Handle any errors
                }
            }
        }
    }

    private fun copyFileToAppDir(uri: android.net.Uri): AudioFile? {
        return try {
            android.util.Log.d("ImportActivity", "Copying file to app dir: $uri")

            // 打开输入流
            val inputStream = contentResolver.openInputStream(uri)
            if (inputStream == null) {
                android.util.Log.e("ImportActivity", "Failed to open input stream for URI: $uri")
                return null
            }

            inputStream.use { input ->
                // 创建输出文件
                val originalFileName = getFileNameFromUri(uri)
                val fileExtension = if (originalFileName.contains(".")) {
                    originalFileName.substringAfterLast('.')
                } else {
                    "mp3" // 默认扩展名
                }
                val fileName = "${System.currentTimeMillis()}_${originalFileName}"
                val outputFile = java.io.File(filesDir, fileName)

                android.util.Log.d("ImportActivity", "Output file path: ${outputFile.absolutePath}")

                // 复制文件
                outputFile.outputStream().use { output ->
                    val bytesCopied = input.copyTo(output)
                    android.util.Log.d("ImportActivity", "Copied $bytesCopied bytes")
                }

                android.util.Log.d("ImportActivity", "File copied successfully, getting metadata...")

                // 先尝试从原始URI获取元数据
                var title = originalFileName.substringBeforeLast('.')
                var artist = "Unknown"
                var duration = 0L

                val projection = arrayOf(
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.DURATION
                )

                // 尝试从原始URI获取元数据
                var cursor = contentResolver.query(uri, projection, null, null, null)
                if (cursor != null) {
                    cursor.use {
                        if (it.moveToFirst()) {
                            val titleIndex = it.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                            val artistIndex = it.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                            val durationIndex = it.getColumnIndex(MediaStore.Audio.Media.DURATION)

                            if (titleIndex >= 0) {
                                val displayName = it.getString(titleIndex)
                                title = displayName.substringBeforeLast('.')
                            }
                            if (artistIndex >= 0) {
                                it.getString(artistIndex)?.let { a -> artist = a }
                            }
                            if (durationIndex >= 0) {
                                duration = it.getLong(durationIndex)
                            }
                        }
                    }
                }

                // 如果没有获取到有效的duration，使用MediaMetadataRetriever
                if (duration == 0L) {
                    try {
                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(this@ImportActivity, uri)
                        val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        if (durationStr != null) {
                            duration = durationStr.toLong()
                            android.util.Log.d("ImportActivity", "Got duration from MediaMetadataRetriever: $duration ms")
                        }
                        retriever.release()
                    } catch (e: Exception) {
                        android.util.Log.e("ImportActivity", "Failed to get duration from MediaMetadataRetriever", e)
                    }
                }

                // 如果没有获取到有效的元数据，尝试使用文件URI
                if (artist == "Unknown" || duration == 0L) {
                    val fileUri = android.net.Uri.fromFile(outputFile)
                    cursor = contentResolver.query(fileUri, projection, null, null, null)
                    if (cursor != null) {
                        cursor.use {
                            if (it.moveToFirst()) {
                                val artistIndex = it.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                                val durationIndex = it.getColumnIndex(MediaStore.Audio.Media.DURATION)

                                if (artistIndex >= 0) {
                                    it.getString(artistIndex)?.let { a -> if (artist == "Unknown") artist = a }
                                }
                                if (durationIndex >= 0) {
                                    val d = it.getLong(durationIndex)
                                    if (duration == 0L) duration = d
                                }
                            }
                        }
                    }
                }

                // 如果仍然没有获取到duration，再次尝试从复制的文件获取
                if (duration == 0L) {
                    try {
                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(outputFile.absolutePath)
                        val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        if (durationStr != null) {
                            duration = durationStr.toLong()
                            android.util.Log.d("ImportActivity", "Got duration from copied file: $duration ms")
                        }
                        retriever.release()
                    } catch (e: Exception) {
                        android.util.Log.e("ImportActivity", "Failed to get duration from copied file", e)
                    }
                }

                android.util.Log.d("ImportActivity", "Audio metadata - Title: $title, Artist: $artist, Duration: $duration ms")

                AudioFile(
                    id = System.currentTimeMillis(),
                    uri = android.net.Uri.fromFile(outputFile),
                    title = title,
                    artist = artist,
                    duration = duration,
                    filePath = outputFile.absolutePath,
                    isExtracted = true
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("ImportActivity", "Error copying file: ${e.message}", e)
            null
        }
    }

    private fun createAudioFileFromUri(uri: android.net.Uri): AudioFile? {
        return try {
            android.util.Log.d("ImportActivity", "Creating AudioFile from URI (fallback): $uri")

            // 获取文件名作为标题
            val fileName = getFileNameFromUri(uri)
            val title = fileName.substringBeforeLast('.')

            var artist = "Unknown"
            var duration = 0L

            // 尝试获取元数据
            val projection = arrayOf(
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION
            )
            var finalTitle = title
            var gotMetadata = false

            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    gotMetadata = true
                    val titleIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                    val artistIndex = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                    val durationIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)

                    val displayName = if (titleIndex >= 0) cursor.getString(titleIndex) else title
                    finalTitle = if (displayName != null) displayName.substringBeforeLast('.') else title

                    if (artistIndex >= 0) {
                        cursor.getString(artistIndex)?.let { a -> artist = a }
                    }
                    if (durationIndex >= 0) {
                        duration = cursor.getLong(durationIndex)
                    }

                    android.util.Log.d("ImportActivity", "Fallback - Title: $finalTitle, Artist: $artist, Duration: $duration ms")
                }
            }

            // 如果没有获取到duration，使用MediaMetadataRetriever
            if (duration == 0L) {
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(this@ImportActivity, uri)
                    val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    if (durationStr != null) {
                        duration = durationStr.toLong()
                        android.util.Log.d("ImportActivity", "Got duration from MediaMetadataRetriever (fallback): $duration ms")
                    }
                    retriever.release()
                } catch (e: Exception) {
                    android.util.Log.e("ImportActivity", "Failed to get duration from MediaMetadataRetriever (fallback)", e)
                }
            }

            AudioFile(
                id = System.currentTimeMillis(),
                uri = uri,
                title = finalTitle,
                artist = artist,
                duration = duration,
                filePath = "",
                isExtracted = false
            )
        } catch (e: Exception) {
            android.util.Log.e("ImportActivity", "Error in fallback method: ${e.message}", e)
            null
        }
    }

    private fun getFileNameFromUri(uri: android.net.Uri): String {
        var fileName = "audio.mp3"
        contentResolver.query(uri, arrayOf(MediaStore.Audio.Media.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                val name = cursor.getString(nameIndex)
                if (!name.isNullOrEmpty()) {
                    fileName = name
                }
            }
        }
        return fileName
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PermissionHelper.PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }) {
                loadAudioFiles()
            } else {
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Permission Required")
                    .setMessage("Storage permission is required to browse and import audio files.")
                    .setPositiveButton("OK") { _, _ ->
                        finish()
                    }
                    .show()
            }
        }
    }

    private inner class AudioFileAdapter : BaseAdapter() {
        override fun getCount(): Int = audioFiles.size

        override fun getItem(position: Int): Any = audioFiles[position]

        override fun getItemId(position: Int): Long = audioFiles[position].id

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(this@ImportActivity)
                .inflate(R.layout.item_audio_file, parent, false)

            val txtTitle = view.findViewById<TextView>(R.id.txtTitle)
            val txtArtist = view.findViewById<TextView>(R.id.txtArtist)
            val txtDuration = view.findViewById<TextView>(R.id.txtDuration)

            val file = audioFiles[position]
            txtTitle.text = file.title
            txtArtist.text = file.artist
            txtDuration.text = formatTime(file.duration)

            return view
        }

        private fun formatTime(ms: Long): String {
            val seconds = (ms / 1000) % 60
            val minutes = (ms / (1000 * 60)) % 60
            val hours = ms / (1000 * 60 * 60)
            return if (hours > 0) {
                String.format("%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }
    }

    companion object {
        private const val REQUEST_PICK_FILES = 1001
    }
}

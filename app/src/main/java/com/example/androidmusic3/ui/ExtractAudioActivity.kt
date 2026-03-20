package com.example.androidmusic3.ui

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.content.DialogInterface
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.androidmusic3.MediaManager
import com.example.androidmusic3.R
import com.example.androidmusic3.model.AudioFile
import com.example.androidmusic3.util.AudioExtractor
import com.example.androidmusic3.util.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExtractAudioActivity : AppCompatActivity() {

    private lateinit var txtStatus: androidx.appcompat.widget.AppCompatTextView
    private lateinit var progressBar: android.widget.ProgressBar
    private lateinit var btnSelectVideo: com.google.android.material.button.MaterialButton
    private lateinit var btnExtract: com.google.android.material.button.MaterialButton
    private lateinit var btnSave: com.google.android.material.button.MaterialButton
    private lateinit var btnShare: com.google.android.material.button.MaterialButton
    private lateinit var btnBack: com.google.android.material.button.MaterialButton
    private lateinit var txtSelectedFile: androidx.appcompat.widget.AppCompatTextView

    private var selectedVideoUri: Uri? = null
    private var extractedAudioPath: String? = null
    private var extractedDurationMs: Long = 0L
    private val audioExtractor = AudioExtractor(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_extract_audio)

        initViews()
        setupClickListeners()

        // Check permissions
        if (!PermissionHelper.checkStoragePermissions(this)) {
            PermissionHelper.requestStoragePermissions(this)
        }
    }

    private fun initViews() {
        txtStatus = findViewById(R.id.txtStatus)
        progressBar = findViewById(R.id.progressBar)
        btnSelectVideo = findViewById(R.id.btnSelectVideo)
        btnExtract = findViewById(R.id.btnExtract)
        btnSave = findViewById(R.id.btnSave)
        btnShare = findViewById(R.id.btnShare)
        btnBack = findViewById(R.id.btnBack)
        txtSelectedFile = findViewById(R.id.txtSelectedFile)

        progressBar.visibility = View.GONE
        btnExtract.isEnabled = false
        btnSave.isEnabled = false
        btnShare.isEnabled = false
    }

    private fun setupClickListeners() {
        btnSelectVideo.setOnClickListener {
            selectVideo()
        }

        btnExtract.setOnClickListener {
            extractAudio()
        }

        btnSave.setOnClickListener {
            saveAudioToLibrary()
        }

        btnShare.setOnClickListener {
            shareAudio()
        }

        btnBack.setOnClickListener {
            finish()
        }
    }

    private fun selectVideo() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "video/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(Intent.createChooser(intent, "Select Video"), REQUEST_PICK_VIDEO)
    }

    private fun extractAudio() {
        selectedVideoUri?.let { uri ->
            txtStatus.text = "Extracting audio..."
            progressBar.visibility = View.VISIBLE
            btnExtract.isEnabled = false

            val originalFileName = getFileName(uri) ?: "extracted_audio"
            val baseName = originalFileName.substringBeforeLast('.')
            val outputFileName = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date()) + "_" + baseName + ".m4a"

            android.util.Log.d("ExtractAudioActivity", "Starting extraction from: $uri")
            android.util.Log.d("ExtractAudioActivity", "Output file name: $outputFileName")

            CoroutineScope(Dispatchers.IO).launch {
                val result = audioExtractor.extractAudioToAAC(
                    uri,
                    outputFileName
                ) { progress ->
                    runOnUiThread {
                        progressBar.progress = progress.toInt()
                        txtStatus.text = "Extracting... ${String.format("%.1f", progress)}%"
                    }
                }

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    btnExtract.isEnabled = true

                    result.onSuccess { extractionResult ->
                        android.util.Log.d("ExtractAudioActivity", "Extraction successful: ${extractionResult.filePath}, duration: ${extractionResult.durationMs}")
                        extractedAudioPath = extractionResult.filePath
                        extractedDurationMs = extractionResult.durationMs
                        val file = File(extractionResult.filePath)
                        txtStatus.text = "Extraction complete!\n${file.name}"
                        btnSave.isEnabled = true
                        btnShare.isEnabled = true
                    }.onFailure { error ->
                        android.util.Log.e("ExtractAudioActivity", "Extraction failed", error)
                        txtStatus.text = "Extraction failed: ${error.message}\n\nPlease try selecting a different video file."
                    }
                }
            }
        }
    }

    private fun saveAudioToLibrary() {
        extractedAudioPath?.let { path ->
            val file = File(path)
            if (!file.exists()) {
                showAlertDialog("Error", "Extracted file not found")
                return
            }

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Determine MIME type based on file extension
                    val mimeType = when {
                        path.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg"
                        path.endsWith(".m4a", ignoreCase = true) -> "audio/mp4"
                        else -> "audio/mp4" // Default for AAC/M4A
                    }

                    android.util.Log.d("ExtractAudioActivity", "Saving file with MIME type: $mimeType")

                    val contentValues = ContentValues().apply {
                        put(MediaStore.Audio.Media.DISPLAY_NAME, file.name)
                        put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                        put(MediaStore.Audio.Media.RELATIVE_PATH, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            Environment.DIRECTORY_MUSIC + "/Extracted"
                        } else {
                            "Music/Extracted"
                        })
                        put(MediaStore.Audio.Media.IS_PENDING, 1)
                    }

                    val uri = contentResolver.insert(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                        } else {
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                        },
                        contentValues
                    )

                    uri?.let {
                        contentResolver.openOutputStream(it)?.use { output ->
                            file.inputStream().use { input ->
                                input.copyTo(output)
                            }
                        }

                        contentValues.clear()
                        contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                        contentResolver.update(it, contentValues, null, null)

                        // Add to MediaManager using fromPath with correct duration
                        val title = file.nameWithoutExtension
                        val audioFile = AudioFile.fromPath(file.absolutePath, title, extractedDurationMs)
                        val mediaManager = MediaManager.getInstance(this@ExtractAudioActivity)
                        mediaManager.addAudioFile(audioFile)

                        withContext(Dispatchers.Main) {
                            showAlertDialog(
                                "Saved",
                                "Audio saved to library successfully!"
                            ) { _, _ ->
                                finish()
                            }
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        showAlertDialog("Error", "Failed to save audio: ${e.message}")
                    }
                }
            }
        }
    }

    private fun shareAudio() {
        extractedAudioPath?.let { path ->
            val file = File(path)
            if (!file.exists()) {
                showAlertDialog("Error", "Extracted file not found")
                return
            }

            try {
                val uri = FileProvider.getUriForFile(
                    this,
                    "${applicationContext.packageName}.fileprovider",
                    file
                )

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "audio/mp4"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                startActivity(Intent.createChooser(intent, "Share Audio"))
            } catch (e: Exception) {
                showAlertDialog("Error", "Failed to share: ${e.message}")
            }
        }
    }

    private fun getFileName(uri: Uri): String? {
        var fileName: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                fileName = cursor.getString(nameIndex)
            }
        }
        return fileName
    }

    private fun showAlertDialog(title: String, message: String, onPositive: ((androidx.appcompat.app.AlertDialog, Int) -> Unit)? = null) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", DialogInterface.OnClickListener { dialog, _ -> dialog.dismiss() })
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_VIDEO && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                selectedVideoUri = uri
                val fileName = getFileName(uri) ?: "Unknown"
                txtSelectedFile.text = "Selected: $fileName"
                btnExtract.isEnabled = true
                btnSave.isEnabled = false
                btnShare.isEnabled = false
                extractedAudioPath = null
                txtStatus.text = "Video selected. Click 'Extract' to extract audio."
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PermissionHelper.PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }) {
                // Permission granted
            } else {
                showAlertDialog("Permission Required", "Storage permission is required to extract and save audio.")
            }
        }
    }

    companion object {
        private const val REQUEST_PICK_VIDEO = 2001
    }
}

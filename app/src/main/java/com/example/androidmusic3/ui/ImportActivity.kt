package com.example.androidmusic3.ui

import android.Manifest
import android.content.Intent
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
                                filePath = filePath
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

                    val files = uris.mapNotNull { uri ->
                        try {
                            val projection = arrayOf(
                                MediaStore.Audio.Media.DISPLAY_NAME,
                                MediaStore.Audio.Media.ARTIST
                            )
                            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    val titleIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                                    val artistIndex = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                                    val title = cursor.getString(titleIndex)
                                    val artist = if (artistIndex >= 0) cursor.getString(artistIndex) else "Unknown"
                                    AudioFile.fromUri(uri, title, artist)
                                } else null
                            }
                        } catch (e: Exception) {
                            // Skip invalid URIs
                            null
                        }
                    }

                    if (files.isNotEmpty()) {
                        CoroutineScope(Dispatchers.IO).launch {
                            val mediaManager = MediaManager.getInstance(this@ImportActivity)
                            try {
                                mediaManager.addAudioFiles(files)

                                withContext(Dispatchers.Main) {
                                    androidx.appcompat.app.AlertDialog.Builder(this@ImportActivity)
                                        .setTitle("Import Complete")
                                        .setMessage("Successfully imported ${files.size} audio file(s).")
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
                } catch (e: Exception) {
                    // Handle any errors
                }
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

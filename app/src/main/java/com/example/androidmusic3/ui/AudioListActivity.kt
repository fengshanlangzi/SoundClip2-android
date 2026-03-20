package com.example.androidmusic3.ui

import android.app.AlertDialog
import android.content.DialogInterface
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.androidmusic3.MediaManager
import com.example.androidmusic3.R
import kotlinx.coroutines.launch
import java.io.File

class AudioListActivity : AppCompatActivity() {

    private lateinit var listView: android.widget.ListView
    private lateinit var adapter: AudioListAdapter
    private lateinit var txtEmpty: androidx.appcompat.widget.AppCompatTextView
    private var isInSelectionMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio_list)

        listView = findViewById(R.id.listViewAudio)
        txtEmpty = findViewById(R.id.txtEmpty)
        val btnBack = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnBack)

        listView.choiceMode = android.widget.ListView.CHOICE_MODE_MULTIPLE
        adapter = AudioListAdapter()
        listView.adapter = adapter

        btnBack.setOnClickListener {
            if (isInSelectionMode) {
                exitSelectionMode()
            } else {
                finish()
            }
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            if (isInSelectionMode) {
                adapter.toggleSelection(position)
            } else {
                val mediaManager = MediaManager.getInstance(this)
                mediaManager.playAudioAtIndex(position)
                finish()
            }
        }

        listView.setOnItemLongClickListener { _, _, position, _ ->
            showItemMenu(position)
            true
        }

        observeAudioFiles()
    }

    private fun observeAudioFiles() {
        lifecycleScope.launch {
            MediaManager.getInstance(this@AudioListActivity).audioFiles.collect { files ->
                adapter.updateData(files)
                if (files.isEmpty()) {
                    txtEmpty.visibility = View.VISIBLE
                    listView.visibility = View.GONE
                } else {
                    txtEmpty.visibility = View.GONE
                    listView.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun showItemMenu(position: Int) {
        val items = arrayOf("Play", "Delete", "Select Multiple")
        AlertDialog.Builder(this)
            .setTitle("Select Action")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        val mediaManager = MediaManager.getInstance(this)
                        mediaManager.playAudioAtIndex(position)
                        finish()
                    }
                    1 -> deleteSingleItem(position)
                    2 -> enterSelectionMode()
                }
            }
            .show()
    }

    private fun deleteSingleItem(position: Int) {
        val mediaManager = MediaManager.getInstance(this)
        AlertDialog.Builder(this)
            .setTitle("Delete Audio")
            .setMessage("Are you sure you want to delete this audio file?")
            .setPositiveButton("Delete") { _, _ ->
                mediaManager.removeAudioFileAt(position)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun enterSelectionMode() {
        isInSelectionMode = true
        adapter.setSelectionMode(true)
        invalidateOptionsMenu()
    }

    private fun exitSelectionMode() {
        isInSelectionMode = false
        adapter.setSelectionMode(false)
        adapter.clearSelection()
        invalidateOptionsMenu()
    }

    private fun deleteSelectedItems() {
        val selectedIndices = adapter.getSelectedIndices()
        if (selectedIndices.isEmpty()) {
            exitSelectionMode()
            return
        }

        val mediaManager = MediaManager.getInstance(this)
        AlertDialog.Builder(this)
            .setTitle("Delete ${selectedIndices.size} Audio(s)")
            .setMessage("Are you sure you want to delete the selected audio files?")
            .setPositiveButton("Delete") { _, _ ->
                mediaManager.removeAudioFilesAtIndices(selectedIndices)
                exitSelectionMode()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun selectAll() {
        adapter.selectAll()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        if (isInSelectionMode) {
            menuInflater.inflate(R.menu.menu_selection, menu)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_select_all -> {
                selectAll()
                true
            }
            R.id.menu_delete -> {
                deleteSelectedItems()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        if (isInSelectionMode) {
            exitSelectionMode()
        } else {
            super.onBackPressed()
        }
    }

    private inner class AudioListAdapter : BaseAdapter() {
        private var files = emptyList<com.example.androidmusic3.model.AudioFile>()
        private val selectedIndices = mutableSetOf<Int>()
        private var selectionMode = false
        private val durationCache = mutableMapOf<Long, Long>() // Cache durations by file id

        fun updateData(newFiles: List<com.example.androidmusic3.model.AudioFile>) {
            files = newFiles
            notifyDataSetChanged()
        }

        fun setSelectionMode(mode: Boolean) {
            selectionMode = mode
            notifyDataSetChanged()
        }

        fun toggleSelection(position: Int) {
            if (selectedIndices.contains(position)) {
                selectedIndices.remove(position)
            } else {
                selectedIndices.add(position)
            }
            notifyDataSetChanged()
        }

        fun clearSelection() {
            selectedIndices.clear()
            notifyDataSetChanged()
        }

        fun selectAll() {
            for (i in 0 until count) {
                selectedIndices.add(i)
            }
            notifyDataSetChanged()
        }

        fun getSelectedIndices(): List<Int> {
            return selectedIndices.toList().sortedDescending()
        }

        override fun getCount(): Int = files.size

        override fun getItem(position: Int): Any = files[position]

        override fun getItemId(position: Int): Long = files[position].id

        private fun getAudioDuration(file: com.example.androidmusic3.model.AudioFile): Long {
            // Return cached duration if available and file.duration is still 0
            if (file.duration > 0) {
                return file.duration
            }
            durationCache[file.id]?.let { return it }

            // Use MediaMetadataRetriever to get duration
            var duration = 0L
            try {
                val retriever = MediaMetadataRetriever()
                if (file.filePath.isNotEmpty()) {
                    // Try file path first
                    retriever.setDataSource(file.filePath)
                } else {
                    // Fall back to URI
                    retriever.setDataSource(this@AudioListActivity, file.uri)
                }
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                if (durationStr != null) {
                    duration = durationStr.toLong()
                }
                retriever.release()

                // Update MediaManager with the correct duration
                if (duration > 0) {
                    durationCache[file.id] = duration
                    updateFileDuration(file.id, duration)
                }
            } catch (e: Exception) {
                android.util.Log.e("AudioListActivity", "Failed to get duration for ${file.title}", e)
            }
            return duration
        }

        private fun updateFileDuration(fileId: Long, duration: Long) {
            // Update the file in MediaManager's list
            try {
                val mediaManager = MediaManager.getInstance(this@AudioListActivity)
                val updatedFiles = mediaManager.audioFiles.value.map { audioFile ->
                    if (audioFile.id == fileId) {
                        audioFile.copy(duration = duration)
                    } else {
                        audioFile
                    }
                }
                // This is a workaround - we can't directly set audioFiles since it's a StateFlow
                // The duration will be cached for this session
            } catch (e: Exception) {
                android.util.Log.e("AudioListActivity", "Failed to update duration", e)
            }
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(this@AudioListActivity)
                .inflate(R.layout.item_audio_list, parent, false)

            val chkSelect = view.findViewById<CheckBox>(R.id.chkSelect)
            val txtTitle = view.findViewById<TextView>(R.id.txtTitle)
            val txtArtist = view.findViewById<TextView>(R.id.txtArtist)
            val txtDuration = view.findViewById<TextView>(R.id.txtDuration)
            val txtIndex = view.findViewById<TextView>(R.id.txtIndex)

            val file = files[position]
            txtTitle.text = file.title
            txtArtist.text = file.artist

            // Get duration (may use MediaMetadataRetriever if file.duration is 0)
            val duration = getAudioDuration(file)
            txtDuration.text = if (duration > 0) formatTime(duration) else "Loading..."
            txtIndex.text = "${position + 1}."

            chkSelect.isChecked = selectedIndices.contains(position)
            chkSelect.visibility = if (selectionMode) View.VISIBLE else View.GONE

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
}

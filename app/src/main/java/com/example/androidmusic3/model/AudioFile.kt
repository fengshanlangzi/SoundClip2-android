package com.example.androidmusic3.model

import android.net.Uri
import android.media.MediaMetadataRetriever
import android.content.Context
import java.io.File

data class AudioFile(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String = "Unknown",
    val duration: Long = 0,
    val filePath: String = "",
    val isExtracted: Boolean = false
) {
    companion object {
        fun fromUri(uri: Uri, title: String, artist: String = "Unknown"): AudioFile {
            return AudioFile(
                id = System.currentTimeMillis(),
                uri = uri,
                title = title,
                artist = artist
            )
        }

        fun fromPath(context: Context, filePath: String, title: String): AudioFile {
            val file = File(filePath)
            var duration = 0L

            // Try to get duration from the file
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(filePath)
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                if (durationStr != null) {
                    duration = durationStr.toLong()
                }
                retriever.release()
            } catch (e: Exception) {
                // Ignore errors, duration will be 0
            }

            return AudioFile(
                id = System.currentTimeMillis(),
                uri = android.net.Uri.fromFile(file),
                title = title,
                artist = "Unknown",
                duration = duration,
                filePath = filePath,
                isExtracted = true
            )
        }
    }
}

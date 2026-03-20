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

        fun fromPath(filePath: String, title: String, duration: Long = 0L): AudioFile {
            val file = File(filePath)
            var actualDuration = duration

            // Try to get duration from the file if not provided
            if (actualDuration <= 0) {
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(filePath)
                    val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    if (durationStr != null) {
                        actualDuration = durationStr.toLong()
                    }
                    retriever.release()
                } catch (e: Exception) {
                    // Ignore errors, duration will be 0
                }
            }

            return AudioFile(
                id = System.currentTimeMillis(),
                uri = android.net.Uri.fromFile(file),
                title = title,
                artist = "Unknown",
                duration = actualDuration,
                filePath = filePath,
                isExtracted = true
            )
        }
    }
}

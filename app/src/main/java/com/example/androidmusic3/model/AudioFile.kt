package com.example.androidmusic3.model

import android.net.Uri

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
    }
}

package com.example.androidmusic3.model

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0,
    val duration: Long = 0,
    val playbackSpeed: Float = 1.0f,
    val currentAudioIndex: Int = 0,
    val isLooping: Boolean = false,
    val loopCount: Int = 1,
    val currentLoop: Int = 1,
    val playbackRange: PlaybackRange? = null
)

data class PlaybackRange(
    val startMs: Long = 0,
    val durationMs: Long = -1 // -1 means play to end of file
) {
    fun isValid(): Boolean = startMs >= 0 && durationMs >= -1

    // 计算实际的结束时间
    fun getEndMs(totalDuration: Long): Long {
        return if (durationMs == -1L) totalDuration else startMs + durationMs
    }
}

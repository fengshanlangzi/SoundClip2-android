package com.example.androidmusic3

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.example.androidmusic3.model.AudioFile
import com.example.androidmusic3.model.PlaybackRange
import com.example.androidmusic3.model.PlaybackState
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MediaManager private constructor(private val context: Context) {

    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var rangeCheckRunnable: Runnable? = null
    private val rangeCheckInterval = 100L // Check every 100ms

    private val _audioFiles = MutableStateFlow<List<AudioFile>>(emptyList())
    val audioFiles: StateFlow<List<AudioFile>> = _audioFiles.asStateFlow()

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentAudioFile = MutableStateFlow<AudioFile?>(null)
    val currentAudioFile: StateFlow<AudioFile?> = _currentAudioFile.asStateFlow()

    private val _playbackRange = MutableStateFlow<PlaybackRange?>(null)
    val playbackRange: StateFlow<PlaybackRange?> = _playbackRange.asStateFlow()

    private var currentLoopCount = 1
    private var loopCount = -1  // -1表示无限循环，默认值
    private var savedCurrentIndex = 0
    private var savedCurrentPosition = 0L

    init {
        setupPlayerListener()
        loadSavedAudioFiles()
        loadSettings()
    }

    private fun setupPlayerListener() {
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_IDLE -> {
                        _playbackState.value = _playbackState.value.copy(isPlaying = false)
                        stopRangeCheck()
                    }
                    Player.STATE_BUFFERING -> {
                        _playbackState.value = _playbackState.value.copy(isPlaying = false)
                    }
                    Player.STATE_READY -> {
                        _playbackState.value = _playbackState.value.copy(
                            duration = exoPlayer.duration,
                            isPlaying = exoPlayer.isPlaying
                        )
                        // Apply range if exists
                        _playbackRange.value?.let { range ->
                            if (exoPlayer.duration > 0) {
                                // Seek to start of range if needed
                                if (exoPlayer.currentPosition < range.startMs) {
                                    exoPlayer.seekTo(range.startMs)
                                }
                                // Start range check if playing
                                if (exoPlayer.isPlaying) {
                                    startRangeCheck()
                                }
                            }
                        } ?: run {
                            stopRangeCheck()
                        }
                    }
                    Player.STATE_ENDED -> {
                        stopRangeCheck()
                        // Check if we reached end
                        _playbackRange.value?.let { range ->
                            // 如果是无限循环(loopCount == -1)或者还有循环次数，继续循环
                            if (loopCount == -1 || currentLoopCount < loopCount) {
                                currentLoopCount++
                                _playbackState.value = _playbackState.value.copy(currentLoop = currentLoopCount)
                                exoPlayer.seekTo(range.startMs)
                                exoPlayer.play()
                            } else {
                                _playbackState.value = _playbackState.value.copy(isPlaying = false)
                            }
                        } ?: run {
                            // 没有播放区间的情况
                            if (loopCount == -1 || currentLoopCount < loopCount) {
                                currentLoopCount++
                                _playbackState.value = _playbackState.value.copy(currentLoop = currentLoopCount)
                                exoPlayer.seekTo(0)
                                exoPlayer.play()
                            } else {
                                _playbackState.value = _playbackState.value.copy(isPlaying = false)
                            }
                        }
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playbackState.value = _playbackState.value.copy(isPlaying = isPlaying)
                // Start or stop range check based on playing state
                if (isPlaying && _playbackRange.value != null) {
                    startRangeCheck()
                } else {
                    stopRangeCheck()
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                // 更新当前音频索引
                _playbackState.value = _playbackState.value.copy(
                    currentAudioIndex = exoPlayer.currentMediaItemIndex,
                    currentLoop = 1
                )
                currentLoopCount = 1
                updateCurrentAudioFile()

                // 如果切换到新的音频文件，应用区间
                if (exoPlayer.currentMediaItemIndex != oldPosition.mediaItemIndex &&
                    _playbackState.value.isPlaying) {
                    _playbackRange.value?.let { range ->
                        mainHandler.postDelayed({
                            exoPlayer.seekTo(range.startMs)
                        }, 200)
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                _playbackState.value = _playbackState.value.copy(isPlaying = false)

                // 检查是否是权限错误
                if (error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED) {
                    // 尝试重新准备播放器
                    mainHandler.postDelayed({
                        if (_audioFiles.value.isNotEmpty()) {
                            try {
                                // 重新加载所有音频文件
                                exoPlayer.clearMediaItems()
                                _audioFiles.value.forEach { file ->
                                    try {
                                        exoPlayer.addMediaItem(MediaItem.fromUri(file.uri))
                                    } catch (e: Exception) {
                                        // 忽略无法添加的文件
                                    }
                                }
                                exoPlayer.prepare()

                                // 如果之前正在播放，尝试重新播放
                                if (_playbackState.value.isPlaying) {
                                    play()
                                }
                            } catch (e: Exception) {
                                // 如果仍然失败，停止播放
                                _playbackState.value = _playbackState.value.copy(isPlaying = false)
                            }
                        }
                    }, 1000)
                }
            }
        })
    }

    private fun updateCurrentAudioFile() {
        val currentIndex = exoPlayer.currentMediaItemIndex
        if (currentIndex >= 0 && currentIndex < _audioFiles.value.size) {
            _currentAudioFile.value = _audioFiles.value[currentIndex]
        }
    }

    private fun loadSavedAudioFiles() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val fileCount = prefs.getInt(KEY_AUDIO_FILE_COUNT, 0)

        // 加载上次播放的状态
        savedCurrentIndex = prefs.getInt(KEY_CURRENT_AUDIO_INDEX, 0)
        savedCurrentPosition = prefs.getLong(KEY_CURRENT_POSITION, 0)

        if (fileCount > 0) {
            val savedFiles = mutableListOf<AudioFile>()
            for (i in 0 until fileCount) {
                try {
                    val uriString = prefs.getString("$KEY_AUDIO_FILE_URI$i", null)
                    val title = prefs.getString("$KEY_AUDIO_FILE_TITLE$i", null)
                    val artist = prefs.getString("$KEY_AUDIO_FILE_ARTIST$i", "Unknown")
                    val duration = prefs.getLong("$KEY_AUDIO_FILE_DURATION$i", 0)
                    val filePath = prefs.getString("$KEY_AUDIO_FILE_PATH$i", "")
                    val isExtracted = prefs.getBoolean("$KEY_AUDIO_FILE_EXTRACTED$i", false)
                    val id = prefs.getLong("$KEY_AUDIO_FILE_ID$i", System.currentTimeMillis())

                    if (uriString != null && title != null) {
                        val uri = Uri.parse(uriString)
                        // 对于华为设备，尝试使用更安全的方式打开文件
                        if (!filePath.isNullOrEmpty()) {
                            // 如果有文件路径，使用文件路径创建URI
                            val fileUri = Uri.fromFile(java.io.File(filePath))
                            savedFiles.add(AudioFile(
                                id = id,
                                uri = fileUri,
                                title = title,
                                artist = artist ?: "Unknown",
                                duration = duration,
                                filePath = filePath,
                                isExtracted = isExtracted
                            ))
                        } else {
                            // 使用原始URI
                            savedFiles.add(AudioFile(
                                id = id,
                                uri = uri,
                                title = title,
                                artist = artist ?: "Unknown",
                                duration = duration,
                                filePath = "",
                                isExtracted = isExtracted
                            ))
                        }
                    }
                } catch (e: Exception) {
                    // Skip invalid entries
                }
            }
            _audioFiles.value = savedFiles

            // Add loaded files to player
            mainHandler.post {
                savedFiles.forEach { file ->
                    try {
                        exoPlayer.addMediaItem(MediaItem.fromUri(file.uri))
                    } catch (e: Exception) {
                        // 如果添加失败，尝试使用ContentResolver
                        try {
                            val contextUri = if (file.filePath.isNotEmpty()) {
                                Uri.fromFile(java.io.File(file.filePath))
                            } else {
                                file.uri
                            }
                            exoPlayer.addMediaItem(MediaItem.fromUri(contextUri))
                        } catch (e2: Exception) {
                            // 忽略无法添加的文件
                        }
                    }
                }
                exoPlayer.prepare()

                // Load saved playback state (we'll handle these values later on main thread)

                if (savedFiles.isNotEmpty() && savedCurrentIndex in savedFiles.indices) {
                    exoPlayer.seekToDefaultPosition(savedCurrentIndex)
                    updateCurrentAudioFile()
                    _playbackState.value = _playbackState.value.copy(
                        currentAudioIndex = savedCurrentIndex
                    )
                    // Delay seek to position after player is ready
                    exoPlayer.addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_READY) {
                                exoPlayer.seekTo(savedCurrentPosition)
                                exoPlayer.removeListener(this)
                            }
                        }
                    })
                }
            }
        }
    }

    fun addAudioFiles(files: List<AudioFile>) {
        // This method might be called from background thread
        val newFiles = _audioFiles.value.toMutableList()
        val filesToAdd = mutableListOf<AudioFile>()

        files.forEach { file ->
            if (newFiles.none { it.uri == file.uri }) {
                newFiles.add(file)
                filesToAdd.add(file)
            }
        }
        _audioFiles.value = newFiles

        // Save files on current thread
        saveAudioFiles()

        // Add to player on main thread
        mainHandler.post {
            filesToAdd.forEach { file ->
                try {
                    exoPlayer.addMediaItem(MediaItem.fromUri(file.uri))
                } catch (e: Exception) {
                    // 如果添加失败，尝试使用文件路径
                    if (file.filePath.isNotEmpty()) {
                        try {
                            val fileUri = Uri.fromFile(java.io.File(file.filePath))
                            exoPlayer.addMediaItem(MediaItem.fromUri(fileUri))
                        } catch (e2: Exception) {
                            // 忽略无法添加的文件
                        }
                    }
                }
            }
            exoPlayer.prepare()
        }
    }

    fun addAudioFile(file: AudioFile) {
        addAudioFiles(listOf(file))
    }

    fun removeAudioFile(file: AudioFile) {
        val index = _audioFiles.value.indexOf(file)
        if (index >= 0) {
            _audioFiles.value = _audioFiles.value.toMutableList().apply { remove(file) }
            saveAudioFiles()
            mainHandler.post {
                exoPlayer.removeMediaItem(index)
            }
        }
    }

    fun removeAudioFileAt(position: Int) {
        if (position >= 0 && position < _audioFiles.value.size) {
            _audioFiles.value = _audioFiles.value.toMutableList().apply { removeAt(position) }
            saveAudioFiles()
            mainHandler.post {
                exoPlayer.removeMediaItem(position)
            }
        }
    }

    fun removeAudioFilesAtIndices(indices: List<Int>) {
        indices.sortedDescending().forEach { index ->
            if (index >= 0 && index < _audioFiles.value.size) {
                _audioFiles.value = _audioFiles.value.toMutableList().apply { removeAt(index) }
                exoPlayer.removeMediaItem(index)
            }
        }
        saveAudioFiles()
    }

    private fun saveAudioFiles() {
        // 这个方法现在调用saveAllState来统一保存所有状态
        saveAllState()
    }

    private fun saveAllState() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        // Clear old audio file data
        val oldFileCount = prefs.getInt(KEY_AUDIO_FILE_COUNT, 0)
        for (i in 0 until oldFileCount) {
            editor.remove("$KEY_AUDIO_FILE_URI$i")
            editor.remove("$KEY_AUDIO_FILE_TITLE$i")
            editor.remove("$KEY_AUDIO_FILE_ARTIST$i")
            editor.remove("$KEY_AUDIO_FILE_DURATION$i")
            editor.remove("$KEY_AUDIO_FILE_PATH$i")
            editor.remove("$KEY_AUDIO_FILE_EXTRACTED$i")
            editor.remove("$KEY_AUDIO_FILE_ID$i")
        }

        // Save current audio files
        val files = _audioFiles.value
        editor.putInt(KEY_AUDIO_FILE_COUNT, files.size)
        files.forEachIndexed { index, file ->
            editor.putString("$KEY_AUDIO_FILE_URI$index", file.uri.toString())
            editor.putString("$KEY_AUDIO_FILE_TITLE$index", file.title)
            editor.putString("$KEY_AUDIO_FILE_ARTIST$index", file.artist)
            editor.putLong("$KEY_AUDIO_FILE_DURATION$index", file.duration)
            editor.putString("$KEY_AUDIO_FILE_PATH$index", file.filePath)
            editor.putBoolean("$KEY_AUDIO_FILE_EXTRACTED$index", file.isExtracted)
            editor.putLong("$KEY_AUDIO_FILE_ID$index", file.id)
        }

        // Save settings (loop count, range, speed)
        editor.putInt(KEY_LOOP_COUNT, loopCount)
        editor.putLong(KEY_RANGE_START, _playbackRange.value?.startMs ?: 0)
        editor.putLong(KEY_RANGE_DURATION, _playbackRange.value?.durationMs ?: -1)
        editor.putBoolean(KEY_HAS_RANGE, _playbackRange.value != null)
        editor.putFloat(KEY_PLAYBACK_SPEED, _playbackState.value.playbackSpeed)

        // Apply the basic settings immediately
        editor.apply()

        // Save current playback state on main thread (only if audio files exist)
        mainHandler.post {
            val playbackEditor = prefs.edit()
            if (_audioFiles.value.isNotEmpty()) {
                playbackEditor.putInt(KEY_CURRENT_AUDIO_INDEX, exoPlayer.currentMediaItemIndex.coerceAtMost(_audioFiles.value.size - 1))
                playbackEditor.putLong(KEY_CURRENT_POSITION, exoPlayer.currentPosition)
            } else {
                playbackEditor.putInt(KEY_CURRENT_AUDIO_INDEX, 0)
                playbackEditor.putLong(KEY_CURRENT_POSITION, 0)
            }
            playbackEditor.apply()
        }
    }

    fun playAudioAtIndex(index: Int) {
        if (index >= 0 && index < _audioFiles.value.size) {
            exoPlayer.seekToDefaultPosition(index)
            currentLoopCount = 1
            _playbackState.value = _playbackState.value.copy(
                currentLoop = 1,
                currentAudioIndex = index
            )
            play()
        }
    }

    fun play() {
        mainHandler.post {
            try {
                if (_audioFiles.value.isNotEmpty()) {
                    if (exoPlayer.duration <= 0 && _audioFiles.value.isNotEmpty()) {
                        // 如果播放器还没有准备好，先准备
                        exoPlayer.prepare()
                    }
                    exoPlayer.play()
                    // Start range check if a range is set
                    if (_playbackRange.value != null) {
                        startRangeCheck()
                    }
                }
            } catch (e: Exception) {
                // 捕获异常并重试
                mainHandler.postDelayed({
                    play()
                }, 500)
            }
        }
    }

    fun pause() {
        mainHandler.post {
            exoPlayer.pause()
            stopRangeCheck()
        }
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun seekTo(positionMs: Long) {
        mainHandler.post {
            exoPlayer.seekTo(positionMs)
        }
    }

    fun seekToPrevious() {
        mainHandler.post {
            if (exoPlayer.currentPosition > 3000) {
                seekTo(0)
            } else {
                exoPlayer.seekToPrevious()
                // 延迟一下再播放，确保切换完成
                mainHandler.postDelayed({
                    if (_audioFiles.value.isNotEmpty()) {
                        play()
                    }
                }, 100)
            }
        }
    }

    fun seekToNext() {
        mainHandler.post {
            exoPlayer.seekToNext()
            // 延迟一下再播放，确保切换完成
            mainHandler.postDelayed({
                if (_audioFiles.value.isNotEmpty()) {
                    play()
                }
            }, 100)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        mainHandler.post {
            exoPlayer.setPlaybackSpeed(speed)
            _playbackState.value = _playbackState.value.copy(playbackSpeed = speed)
            saveSettings()
        }
    }

    fun setLoopCount(count: Int) {
        loopCount = count
        currentLoopCount = 1
        _playbackState.value = _playbackState.value.copy(
            loopCount = count,
            currentLoop = 1
        )
        saveSettings()
    }

    fun setPlaybackRange(startMs: Long, durationMs: Long) {
        val range = PlaybackRange(startMs, durationMs)
        if (range.isValid()) {
            _playbackRange.value = range
            _playbackState.value = _playbackState.value.copy(playbackRange = range)

            // Apply range to player immediately
            mainHandler.post {
                if (_audioFiles.value.isNotEmpty()) {
                    // 如果正在播放，启动区间检查并seek到开始位置
                    if (exoPlayer.isPlaying) {
                        exoPlayer.seekTo(range.startMs)
                        startRangeCheck()
                    } else {
                        // 如果没有播放，准备后应用区间
                        if (exoPlayer.duration <= 0) {
                            exoPlayer.prepare()
                        }
                        exoPlayer.seekTo(range.startMs)
                    }
                    saveSettings()
                } else {
                    // 如果没有音频文件，清除区间
                    _playbackRange.value = null
                    _playbackState.value = _playbackState.value.copy(playbackRange = null)
                }
            }
        }
    }

    fun clearPlaybackRange() {
        _playbackRange.value = null
        _playbackState.value = _playbackState.value.copy(playbackRange = null)

        // 停止range检查并重置到音频开始
        mainHandler.post {
            stopRangeCheck()
            seekTo(0)
            saveSettings()
        }
    }

    fun getCurrentPosition(): Long {
        return exoPlayer.currentPosition
    }

    fun getDuration(): Long {
        return exoPlayer.duration
    }

    fun getAudioFileCount(): Int {
        return _audioFiles.value.size
    }

    fun getAudioFileAtIndex(index: Int): AudioFile? {
        return _audioFiles.value.getOrNull(index)
    }

    fun getCurrentAudioIndex(): Int {
        return exoPlayer.currentMediaItemIndex
    }

    private fun startRangeCheck() {
        stopRangeCheck() // Stop any existing check first

        val range = _playbackRange.value ?: return

        rangeCheckRunnable = object : Runnable {
            override fun run() {
                if (!exoPlayer.isPlaying) {
                    stopRangeCheck()
                    return
                }

                val currentPosition = exoPlayer.currentPosition
                val endMs = if (range.durationMs == -1L) {
                    exoPlayer.duration
                } else {
                    range.startMs + range.durationMs
                }

                // Check if we've passed the end of the range
                if (currentPosition >= endMs) {
                    // Check loop count
                    if (loopCount == -1 || currentLoopCount < loopCount) {
                        // Loop: increment count and seek to start
                        if (currentLoopCount < loopCount) {
                            currentLoopCount++
                        }
                        _playbackState.value = _playbackState.value.copy(currentLoop = currentLoopCount)
                        exoPlayer.seekTo(range.startMs)
                    } else {
                        // No more loops: stop playback
                        exoPlayer.pause()
                        stopRangeCheck()
                    }
                }

                // Schedule next check
                if (exoPlayer.isPlaying) {
                    mainHandler.postDelayed(this, rangeCheckInterval)
                }
            }
        }
        mainHandler.post(rangeCheckRunnable!!)
    }

    private fun stopRangeCheck() {
        rangeCheckRunnable?.let {
            mainHandler.removeCallbacks(it)
            rangeCheckRunnable = null
        }
    }

    fun release() {
        stopRangeCheck()
        exoPlayer.release()
    }

    private fun saveSettings() {
        // 统一调用saveAllState来保存所有状态
        saveAllState()
    }

    private fun loadSettings() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loopCount = prefs.getInt(KEY_LOOP_COUNT, -1)  // 默认-1表示无限循环
        currentLoopCount = 1
        val playbackSpeed = prefs.getFloat(KEY_PLAYBACK_SPEED, 1.0f)

        // Load range setting
        val hasRange = prefs.getBoolean(KEY_HAS_RANGE, false)
        if (hasRange) {
            val startMs = prefs.getLong(KEY_RANGE_START, 0)
            val durationMs = prefs.getLong(KEY_RANGE_DURATION, -1)
            val range = PlaybackRange(startMs, durationMs)
            if (range.isValid()) {
                _playbackRange.value = range
                _playbackState.value = _playbackState.value.copy(
                    loopCount = loopCount,
                    playbackSpeed = playbackSpeed,
                    playbackRange = range
                )
            }
        } else {
            _playbackRange.value = null
            _playbackState.value = _playbackState.value.copy(
                loopCount = loopCount,
                playbackSpeed = playbackSpeed
            )
        }
    }

    companion object {
        private const val PREFS_NAME = "media_manager_prefs"
        private const val KEY_AUDIO_FILE_COUNT = "audio_file_count"
        private const val KEY_AUDIO_FILE_URI = "audio_file_uri"
        private const val KEY_AUDIO_FILE_TITLE = "audio_file_title"
        private const val KEY_AUDIO_FILE_ARTIST = "audio_file_artist"
        private const val KEY_AUDIO_FILE_DURATION = "audio_file_duration"
        private const val KEY_AUDIO_FILE_PATH = "audio_file_path"
        private const val KEY_AUDIO_FILE_EXTRACTED = "audio_file_extracted"
        private const val KEY_AUDIO_FILE_ID = "audio_file_id"
        private const val KEY_LOOP_COUNT = "loop_count"
        private const val KEY_RANGE_START = "range_start"
        private const val KEY_RANGE_DURATION = "range_duration"
        private const val KEY_HAS_RANGE = "has_range"
        private const val KEY_CURRENT_AUDIO_INDEX = "current_audio_index"
        private const val KEY_CURRENT_POSITION = "current_position"
        private const val KEY_PLAYBACK_SPEED = "playback_speed"

        @Volatile
        private var instance: MediaManager? = null

        fun getInstance(context: Context): MediaManager {
            return instance ?: synchronized(this) {
                instance ?: MediaManager(context.applicationContext).also { instance = it }
            }
        }
    }
}

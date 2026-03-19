package com.example.androidmusic3.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.androidmusic3.MediaManager
import com.example.androidmusic3.R
import com.example.androidmusic3.service.PlayerService
import com.example.androidmusic3.util.PermissionHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PlayerActivity : AppCompatActivity() {

    private lateinit var mediaManager: MediaManager
    private var updateProgressJob: Job? = null

    // Views
    private lateinit var txtCurrentTrack: androidx.appcompat.widget.AppCompatTextView
    private lateinit var txtArtist: androidx.appcompat.widget.AppCompatTextView
    private lateinit var txtCurrentTime: androidx.appcompat.widget.AppCompatTextView
    private lateinit var txtDuration: androidx.appcompat.widget.AppCompatTextView
    private lateinit var progressBar: SeekBar
    private lateinit var btnPlayPause: com.google.android.material.button.MaterialButton
    private lateinit var btnPrevious: com.google.android.material.button.MaterialButton
    private lateinit var btnNext: com.google.android.material.button.MaterialButton
    private lateinit var btnSpeed: com.google.android.material.button.MaterialButton
    private lateinit var btnLoop: com.google.android.material.button.MaterialButton
    private lateinit var btnRange: com.google.android.material.button.MaterialButton
    private lateinit var txtLoopCount: androidx.appcompat.widget.AppCompatTextView
    private lateinit var btnImport: com.google.android.material.button.MaterialButton
    private lateinit var btnExtract: com.google.android.material.button.MaterialButton
    private lateinit var btnList: com.google.android.material.button.MaterialButton

    private val playbackSpeeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        mediaManager = MediaManager.getInstance(this)

        initViews()
        setupClickListeners()
        observePlaybackState()
        startProgressUpdate()

        // Check permissions
        if (!PermissionHelper.checkStoragePermissions(this)) {
            PermissionHelper.requestStoragePermissions(this)
        }
        if (!PermissionHelper.checkPostNotificationPermission(this)) {
            PermissionHelper.requestPostNotificationPermission(this)
        }

        // Start foreground service
        startPlayerService()
    }

    private fun initViews() {
        txtCurrentTrack = findViewById(R.id.txtCurrentTrack)
        txtArtist = findViewById(R.id.txtArtist)
        txtCurrentTime = findViewById(R.id.txtCurrentTime)
        txtDuration = findViewById(R.id.txtDuration)
        progressBar = findViewById(R.id.progressBar)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnPrevious = findViewById(R.id.btnPrevious)
        btnNext = findViewById(R.id.btnNext)
        btnSpeed = findViewById(R.id.btnSpeed)
        btnLoop = findViewById(R.id.btnLoop)
        btnRange = findViewById(R.id.btnRange)
        txtLoopCount = findViewById(R.id.txtLoopCount)
        btnImport = findViewById(R.id.btnImport)
        btnExtract = findViewById(R.id.btnExtract)
        btnList = findViewById(R.id.btnList)
    }

    private fun setupClickListeners() {
        btnPlayPause.setOnClickListener {
            // 只有在有音频文件时才允许播放
            if (mediaManager.getAudioFileCount() > 0) {
                mediaManager.togglePlayPause()
            }
        }

        btnPrevious.setOnClickListener {
            mediaManager.seekToPrevious()
        }

        btnNext.setOnClickListener {
            mediaManager.seekToNext()
        }

        btnSpeed.setOnClickListener {
            showSpeedDialog()
        }

        btnLoop.setOnClickListener {
            showLoopDialog()
        }

        btnRange.setOnClickListener {
            showRangeDialog()
        }

        btnImport.setOnClickListener {
            startActivity(Intent(this, ImportActivity::class.java))
        }

        btnExtract.setOnClickListener {
            startActivity(Intent(this, ExtractAudioActivity::class.java))
        }

        btnList.setOnClickListener {
            startActivity(Intent(this, AudioListActivity::class.java))
        }

        progressBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaManager.seekTo(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                mediaManager.seekTo(progressBar.progress.toLong())
            }
        })
    }

    private fun observePlaybackState() {
        lifecycleScope.launch {
            mediaManager.playbackState.collect { state ->
                updateUI(state)
            }
        }

        lifecycleScope.launch {
            mediaManager.currentAudioFile.collect { file ->
                file?.let {
                    txtCurrentTrack.text = it.title
                    txtArtist.text = it.artist
                }
            }
        }

        // 监听音频文件数量
        lifecycleScope.launch {
            mediaManager.audioFiles.collect { files ->
                // 更新按钮状态
                btnPlayPause.isEnabled = files.isNotEmpty()
                btnPrevious.isEnabled = files.isNotEmpty()
                btnNext.isEnabled = files.isNotEmpty()
                progressBar.isEnabled = files.isNotEmpty()

                // 如果没有文件，显示提示
                if (files.isEmpty()) {
                    txtCurrentTrack.text = "请先导入音频文件"
                    txtArtist.text = ""
                    txtCurrentTime.text = "00:00"
                    txtDuration.text = "00:00"
                    progressBar.progress = 0
                }
            }
        }

        // 监听播放区间变化
        lifecycleScope.launch {
            mediaManager.playbackRange.collect { range ->
                if (range != null) {
                    val startSec = range.startMs / 1000
                    val durationSec = if (range.durationMs == -1L) "" else (range.durationMs / 1000).toString()
                    val text = if (durationSec.isEmpty()) "Range: ${startSec}s" else "Range: ${startSec}s +${durationSec.toInt()}s"
                    btnRange.text = text
                } else {
                    btnRange.text = "Set Range"
                }
            }
        }
    }

    private fun updateUI(state: com.example.androidmusic3.model.PlaybackState) {
        btnPlayPause.text = if (state.isPlaying) "Pause" else "Play"
        btnPlayPause.icon = getDrawable(
            if (state.isPlaying) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )

        txtDuration.text = formatTime(state.duration)

        // 当loopCount为-1时显示"∞"表示无限循环
        val loopCountDisplay = if (state.loopCount == -1) "∞" else state.loopCount.toString()
        txtLoopCount.text = "${state.currentLoop}/$loopCountDisplay"

        // 更新播放速度显示
        btnSpeed.text = "${state.playbackSpeed}x"
    }

    private fun startProgressUpdate() {
        updateProgressJob = lifecycleScope.launch {
            while (true) {
                val currentPosition = mediaManager.getCurrentPosition()
                val duration = mediaManager.getDuration()

                progressBar.max = if (duration > 0) duration.toInt() else 0
                progressBar.progress = currentPosition.toInt()
                txtCurrentTime.text = formatTime(currentPosition)

                delay(1000)
            }
        }
    }

    private fun stopProgressUpdate() {
        updateProgressJob?.cancel()
    }

    private fun showSpeedDialog() {
        val speeds = playbackSpeeds.map { "${it}x" }.toTypedArray()
        // 根据当前播放速度找到对应的索引
        val currentSpeed = mediaManager.playbackState.value.playbackSpeed
        val index = playbackSpeeds.indexOf(currentSpeed)
        val initialSelection = if (index >= 0) index else 2

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Select Playback Speed")
            .setSingleChoiceItems(speeds, initialSelection) { _, which ->
                mediaManager.setPlaybackSpeed(playbackSpeeds[which])
                btnSpeed.text = "${playbackSpeeds[which]}x"
            }
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showLoopDialog() {
        val loopOptions = arrayOf("Infinite Loop", "1 time", "2 times", "3 times", "5 times", "10 times", "Custom")
        val loopValues = intArrayOf(-1, 1, 2, 3, 5, 10, -2) // -1表示无限循环，-2表示自定义输入

        // Create custom view with manual input
        val dialogView = layoutInflater.inflate(R.layout.dialog_loop, null)
        val editTextCustomLoop = dialogView.findViewById<android.widget.EditText>(R.id.editTextCustomLoop)
        editTextCustomLoop.visibility = android.view.View.GONE

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Set Loop Count")
            .setView(dialogView)
            .setSingleChoiceItems(loopOptions, 0) { _, which ->
                if (loopValues[which] == -2) {
                    // 自定义输入
                    editTextCustomLoop.visibility = android.view.View.VISIBLE
                } else {
                    // 直接设置循环次数（包括无限循环-1）
                    editTextCustomLoop.visibility = android.view.View.GONE
                    mediaManager.setLoopCount(loopValues[which])
                }
            }
            .setPositiveButton("OK") { _, _ ->
                if (editTextCustomLoop.visibility == android.view.View.VISIBLE) {
                    val customValue = editTextCustomLoop.text.toString().toIntOrNull()
                    if (customValue != null && customValue >= 0) {
                        mediaManager.setLoopCount(customValue)
                    } else {
                        android.widget.Toast.makeText(
                            this,
                            "Please enter a valid number (>= 0)",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
    }

    private fun showRangeDialog() {
        val duration = mediaManager.getDuration()
        if (duration <= 0) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Playback Range")
                .setMessage("No audio loaded")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val durationSeconds = (duration / 1000).toInt()

        val dialogView = layoutInflater.inflate(R.layout.dialog_range, null)
        val editTextStart = dialogView.findViewById<android.widget.EditText>(R.id.editTextStart)
        val editTextDuration = dialogView.findViewById<android.widget.EditText>(R.id.editTextDuration)

        // Load current range if exists
        val currentRange = mediaManager.playbackRange.value
        if (currentRange != null) {
            val currentStartSeconds = (currentRange.startMs / 1000).toInt().coerceAtMost(durationSeconds)
            val currentDurationSeconds = if (currentRange.durationMs == -1L) {
                durationSeconds - currentStartSeconds
            } else {
                (currentRange.durationMs / 1000).toInt()
            }
            editTextStart.setText(currentStartSeconds.toString())
            editTextDuration.setText(currentDurationSeconds.toString())
        } else {
            editTextStart.setText("0")
            editTextDuration.setText(durationSeconds.toString())
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Set Playback Range")
            .setView(dialogView)
            .setPositiveButton("Set") { _, _ ->
                val startSeconds = editTextStart.text.toString().toIntOrNull()
                val durationSecs = editTextDuration.text.toString().toIntOrNull()

                if (startSeconds != null && durationSecs != null && startSeconds >= 0 && durationSecs > 0) {
                    mediaManager.setPlaybackRange(startSeconds * 1000L, durationSecs * 1000L)
                    // Show success message
                    android.widget.Toast.makeText(
                        this,
                        "Range set: start ${startSeconds}s, duration ${durationSecs}s",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                } else {
                    android.widget.Toast.makeText(
                        this,
                        "Please enter valid seconds (Start < End)",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Clear") { _, _ ->
                mediaManager.clearPlaybackRange()
                android.widget.Toast.makeText(
                    this,
                    "Range cleared",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun startPlayerService() {
        val intent = Intent(this, PlayerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun formatTime(ms: Long): String {
        if (ms < 0) return "00:00"
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        val hours = ms / (1000 * 60 * 60)
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    override fun onResume() {
        super.onResume()
        startProgressUpdate()
    }

    override fun onPause() {
        super.onPause()
        stopProgressUpdate()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopProgressUpdate()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PermissionHelper.PERMISSION_REQUEST_CODE) {
            // Handle permission results
        }
    }
}

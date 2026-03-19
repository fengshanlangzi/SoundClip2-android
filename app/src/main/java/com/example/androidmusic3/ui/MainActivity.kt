package com.example.androidmusic3.ui

import android.content.Intent
import android.os.Bundle
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import com.example.androidmusic3.MediaManager
import com.example.androidmusic3.R

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 检查是否有保存的音频文件
        if (!hasSavedAudioFiles()) {
            // 如果没有保存的音频文件，直接跳转到导入页面
            navigateToImport()
        } else {
            // 有保存的文件，跳转到播放器
            navigateToPlayer()
        }
    }

    private fun hasSavedAudioFiles(): Boolean {
        val prefs: SharedPreferences = getSharedPreferences("media_manager_prefs", MODE_PRIVATE)
        return prefs.getInt("audio_file_count", 0) > 0
    }

    private fun navigateToPlayer() {
        val intent = Intent(this, PlayerActivity::class.java)
        startActivity(intent)
        finish()
    }

    fun navigateToImport() {
        val intent = Intent(this, ImportActivity::class.java)
        startActivity(intent)
        finish()
    }

    fun navigateToExtractAudio() {
        val intent = Intent(this, ExtractAudioActivity::class.java)
        startActivity(intent)
    }

    fun navigateToAudioList() {
        val intent = Intent(this, AudioListActivity::class.java)
        startActivity(intent)
    }
}

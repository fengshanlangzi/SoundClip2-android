package com.example.androidmusic3.util

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

class AudioExtractor(private val context: Context) {

    suspend fun extractAudioFromVideo(
        videoUri: Uri,
        outputFileName: String,
        progressCallback: (Float) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val outputDir = File(context.getExternalFilesDir(null), "extracted_audio")
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }
            val outputFile = File(outputDir, outputFileName)

            android.util.Log.d("AudioExtractor", "Starting extraction from: $videoUri")
            android.util.Log.d("AudioExtractor", "Output file: ${outputFile.absolutePath}")

            val extractor = MediaExtractor()
            extractor.setDataSource(context, videoUri, null)

            // Find audio track
            var audioTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                android.util.Log.d("AudioExtractor", "Track $i: MIME = $mime")
                if (mime?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    android.util.Log.d("AudioExtractor", "Selected audio track $i with MIME: $mime")
                    break
                }
            }

            if (audioTrackIndex == -1) {
                extractor.release()
                android.util.Log.e("AudioExtractor", "No audio track found")
                return@withContext Result.failure(Exception("No audio track found in video"))
            }

            val format = extractor.getTrackFormat(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            val duration = format.getLong(MediaFormat.KEY_DURATION)

            android.util.Log.d("AudioExtractor", "Audio format: MIME=$mime, Duration=$duration")

            extractor.selectTrack(audioTrackIndex)

            // Determine output extension based on audio format
            val actualOutputFile = if (mime.contains("mp4a") || mime.contains("aac")) {
                File(outputFile.parentFile, outputFileName.substringBeforeLast(".") + ".aac")
            } else if (mime.contains("mp3")) {
                File(outputFile.parentFile, outputFileName.substringBeforeLast(".") + ".mp3")
            } else {
                // Default to m4a for other formats
                File(outputFile.parentFile, outputFileName.substringBeforeLast(".") + ".m4a")
            }

            android.util.Log.d("AudioExtractor", "Actual output file: ${actualOutputFile.absolutePath}")

            // Write audio data to file
            var totalBytesWritten = 0L
            val maxBufferSize = 1024 * 1024 // 1MB buffer

            FileOutputStream(actualOutputFile).use { fos ->
                val buffer = ByteBuffer.allocate(maxBufferSize)
                var lastProgress = -1

                while (true) {
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize <= 0) {
                        android.util.Log.d("AudioExtractor", "End of samples")
                        break
                    }

                    val presentationTimeUs = extractor.sampleTime

                    val data = ByteArray(sampleSize)
                    buffer.position(0)
                    buffer.get(data)
                    fos.write(data)
                    totalBytesWritten += sampleSize

                    // Update progress
                    if (duration > 0) {
                        val progress = (presentationTimeUs.toFloat() / duration * 100).coerceIn(0f, 100f)
                        if (progress.toInt() != lastProgress) {
                            lastProgress = progress.toInt()
                            android.util.Log.d("AudioExtractor", "Progress: ${lastProgress}%")
                            progressCallback(progress)
                        }
                    }
                    extractor.advance()
                }
            }

            extractor.release()

            android.util.Log.d("AudioExtractor", "Extraction complete. Total bytes: $totalBytesWritten")

            Result.success(actualOutputFile.absolutePath)
        } catch (e: Exception) {
            android.util.Log.e("AudioExtractor", "Extraction failed", e)
            Result.failure(e)
        }
    }

    suspend fun extractAudioToAAC(
        videoUri: Uri,
        outputFileName: String,
        progressCallback: (Float) -> Unit = {}
    ): Result<String> = extractAudioFromVideo(videoUri, outputFileName, progressCallback)
}

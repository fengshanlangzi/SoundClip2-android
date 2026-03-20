package com.example.androidmusic3.util

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

data class AudioExtractionResult(
    val filePath: String,
    val durationMs: Long,
    val format: String
)

class AudioExtractor(private val context: Context) {

    suspend fun extractAudioFromVideo(
        videoUri: Uri,
        outputFileName: String,
        progressCallback: (Float) -> Unit = {}
    ): Result<AudioExtractionResult> = withContext(Dispatchers.IO) {
        var outputFile: File? = null
        var extractor: MediaExtractor? = null

        try {
            val outputDir = File(context.getExternalFilesDir(null), "extracted_audio")
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            android.util.Log.d("AudioExtractor", "Starting extraction from: $videoUri")

            extractor = MediaExtractor()
            extractor.setDataSource(context, videoUri, null)

            // Find audio track
            var audioTrackIndex = -1
            var inputFormat: MediaFormat? = null
            var inputMime: String = ""
            var duration = 0L

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                android.util.Log.d("AudioExtractor", "Track $i: MIME = $mime")

                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    inputFormat = format
                    inputMime = mime
                    duration = format.getLong(MediaFormat.KEY_DURATION, 0)
                    android.util.Log.d("AudioExtractor", "Selected audio track $i with MIME: $mime")
                    break
                }
            }

            if (audioTrackIndex == -1) {
                android.util.Log.e("AudioExtractor", "No audio track found")
                return@withContext Result.failure(Exception("No audio track found in video"))
            }

            extractor.selectTrack(audioTrackIndex)

            android.util.Log.d("AudioExtractor", "Audio format: MIME=$inputMime, Duration=$duration")

            // Determine output format and extension
            // Priority: MP3 > M4A (not AAC)
            val (outputFormatExt, outputFormatName) = when {
                inputMime.contains("mp3") -> {
                    android.util.Log.d("AudioExtractor", "Input is MP3, using MP3 format")
                    Pair(".mp3", "mp3")
                }
                else -> {
                    // Use M4A format for AAC and other formats
                    android.util.Log.d("AudioExtractor", "Input is $inputMime, using M4A format")
                    Pair(".m4a", "m4a")
                }
            }

            outputFile = File(outputDir, outputFileName.substringBeforeLast(".") + outputFormatExt)
            android.util.Log.d("AudioExtractor", "Output file: ${outputFile.absolutePath}")

            // Extract audio data
            var totalBytesWritten = 0L
            val maxBufferSize = 1024 * 1024 // 1MB buffer
            var lastProgress = -1

            FileOutputStream(outputFile).use { fos ->
                val buffer = ByteBuffer.allocate(maxBufferSize)

                while (true) {
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize <= 0) {
                        android.util.Log.d("AudioExtractor", "End of samples, total bytes: $totalBytesWritten")
                        break
                    }

                    val presentationTimeUs = extractor.sampleTime
                    val sampleFlags = extractor.sampleFlags

                    // Write sample data
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
                            withContext(Dispatchers.Main) {
                                progressCallback(progress)
                            }
                        }
                    }

                    // Advance to next sample
                    extractor.advance()

                    // Safety check: prevent infinite loop
                    if (totalBytesWritten > 500 * 1024 * 1024) { // 500MB limit
                        android.util.Log.w("AudioExtractor", "File size exceeded 500MB, stopping extraction")
                        break
                    }
                }
            }

            // Verify the output file
            if (outputFile == null || !outputFile.exists()) {
                android.util.Log.e("AudioExtractor", "Output file doesn't exist")
                return@withContext Result.failure(Exception("Failed to create audio file"))
            }

            if (outputFile.length() < 100) {
                android.util.Log.e("AudioExtractor", "Output file is too small: ${outputFile.length()} bytes")
                outputFile.delete()
                return@withContext Result.failure(Exception("Extracted audio file is too small"))
            }

            // Get actual duration from the extracted file using MediaMetadataRetriever
            var actualDuration = 0L
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(outputFile.absolutePath)
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                if (durationStr != null) {
                    actualDuration = durationStr.toLong()
                    android.util.Log.d("AudioExtractor", "Actual duration from file: ${actualDuration}ms (${actualDuration / 1000}s)")
                }
                retriever.release()
            } catch (e: Exception) {
                android.util.Log.e("AudioExtractor", "Failed to get actual duration", e)
                // Fall back to original duration
                actualDuration = duration
            }

            android.util.Log.d("AudioExtractor", "Extraction complete. File: ${outputFile.absolutePath}, Size: ${outputFile.length()} bytes, Duration: ${actualDuration}ms")

            Result.success(AudioExtractionResult(outputFile.absolutePath, actualDuration, outputFormatName))
        } catch (e: Exception) {
            android.util.Log.e("AudioExtractor", "Extraction failed", e)

            // Clean up output file on error
            outputFile?.let {
                if (it.exists()) {
                    android.util.Log.d("AudioExtractor", "Cleaning up output file on error")
                    it.delete()
                }
            }

            Result.failure(e)
        } finally {
            extractor?.release()
        }
    }

    suspend fun extractAudioToAAC(
        videoUri: Uri,
        outputFileName: String,
        progressCallback: (Float) -> Unit = {}
    ): Result<AudioExtractionResult> = extractAudioFromVideo(videoUri, outputFileName, progressCallback)
}

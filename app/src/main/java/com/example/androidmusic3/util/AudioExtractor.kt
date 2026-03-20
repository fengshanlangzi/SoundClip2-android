package com.example.androidmusic3.util

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

data class AudioExtractionResult(
    val filePath: String,
    val durationMs: Long,
    val format: String
)

class AudioExtractor(private val context: Context) {

    // Supported output formats
    private val OUTPUT_MIME_TYPE = "audio/mp4a-latm" // AAC in M4A container
    private val OUTPUT_FORMAT_EXT = ".m4a"
    private val OUTPUT_FORMAT_NAME = "m4a"

    suspend fun extractAudioFromVideo(
        videoUri: Uri,
        outputFileName: String,
        progressCallback: (Float) -> Unit = {}
    ): Result<AudioExtractionResult> = withContext(Dispatchers.IO) {
        var outputFile: File? = null
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null

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
            var inputSampleRate = 0
            var inputChannelCount = 0

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                android.util.Log.d("AudioExtractor", "Track $i: MIME = $mime")

                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    inputFormat = format
                    inputMime = mime
                    duration = format.getLong(MediaFormat.KEY_DURATION, 0)
                    inputSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE, 44100)
                    inputChannelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT, 2)
                    android.util.Log.d("AudioExtractor", "Selected audio track $i with MIME: $mime")
                    android.util.Log.d("AudioExtractor", "Input: sampleRate=$inputSampleRate, channels=$inputChannelCount, duration=$duration")
                    break
                }
            }

            if (audioTrackIndex == -1) {
                android.util.Log.e("AudioExtractor", "No audio track found")
                return@withContext Result.failure(Exception("No audio track found in video"))
            }

            extractor.selectTrack(audioTrackIndex)

            android.util.Log.d("AudioExtractor", "Audio format: MIME=$inputMime, Duration=$duration")

            outputFile = File(outputDir, outputFileName.substringBeforeLast(".") + OUTPUT_FORMAT_EXT)
            android.util.Log.d("AudioExtractor", "Output file: ${outputFile.absolutePath}")

            // Create MediaMuxer with M4A format
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // Setup encoder with same sample rate and channel count as input
            val encoderFormat = MediaFormat.createAudioFormat(OUTPUT_MIME_TYPE, inputSampleRate, inputChannelCount).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 128000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8192 * 4)
                // Set duration so muxer can write correct duration
                setLong(MediaFormat.KEY_DURATION, duration)
            }
            android.util.Log.d("AudioExtractor", "Encoder configured with: sampleRate=$inputSampleRate, channels=$inputChannelCount")

            encoder = MediaCodec.createEncoderByType(OUTPUT_MIME_TYPE)
            encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            // Setup decoder
            decoder = MediaCodec.createDecoderByType(inputMime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            val inputBuffers = decoder.inputBuffers
            val outputBuffers = decoder.outputBuffers
            val encoderInputBuffers = encoder.inputBuffers
            val encoderOutputBuffers = encoder.outputBuffers

            // Track variables
            var audioTrackIndexInMuxer = -1
            var sawInputEOS = false
            var sawOutputEOS = false
            var encoderSawInputEOS = false
            var encoderSawOutputEOS = false
            var totalBytesProcessed = 0L
            var totalSamples = 0
            var lastProgress = -1
            var decoderOutputBufferInfo = MediaCodec.BufferInfo()
            var encoderOutputBufferInfo = MediaCodec.BufferInfo()

            // Count total samples for progress
            var totalSampleCount = 0
            extractor.unselectTrack(audioTrackIndex)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    extractor.selectTrack(i)
                    break
                }
            }
            while (true) {
                val sampleSize = extractor.readSampleData(ByteBuffer.allocate(8192), 0)
                if (sampleSize <= 0) break
                totalSampleCount++
                extractor.advance()
            }
            extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            android.util.Log.d("AudioExtractor", "Total samples to process: $totalSampleCount")

            var processedSamples = 0

            while (!sawOutputEOS || !encoderSawOutputEOS) {
                // Feed input to decoder
                if (!sawInputEOS) {
                    val inputBufferIndex = decoder.dequeueInputBuffer(10000)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = inputBuffers[inputBufferIndex]
                        inputBuffer.clear()

                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize > 0) {
                            val presentationTimeUs = extractor.sampleTime
                            decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, presentationTimeUs, 0)
                            extractor.advance()

                            // Update progress
                            processedSamples++
                            if (totalSampleCount > 0 && duration > 0) {
                                val progress = (processedSamples.toFloat() / totalSampleCount * 100).coerceIn(0f, 100f)
                                if (progress.toInt() != lastProgress) {
                                    lastProgress = progress.toInt()
                                    android.util.Log.d("AudioExtractor", "Progress: ${lastProgress}%")
                                    withContext(Dispatchers.Main) {
                                        progressCallback(progress)
                                    }
                                }
                            }
                        } else {
                            decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEOS = true
                        }
                    }
                }

                // Get decoded data and feed to encoder
                val decoderOutputIndex = decoder.dequeueOutputBuffer(decoderOutputBufferInfo, 10000)
                when {
                    decoderOutputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {}
                    decoderOutputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {}
                    decoderOutputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        android.util.Log.d("AudioExtractor", "Decoder output format changed")
                    }
                    else -> {
                        if (decoderOutputBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            sawOutputEOS = true
                        }

                        // Feed decoded data to encoder
                        if (!encoderSawInputEOS) {
                            val encoderInputIndex = encoder.dequeueInputBuffer(10000)
                            if (encoderInputIndex >= 0) {
                                val encoderInputBuffer = encoderInputBuffers[encoderInputIndex]

                                if (decoderOutputBufferInfo.size > 0) {
                                    decoder.getOutputBuffer(decoderOutputIndex)?.let { decodedData ->
                                        decodedData.position(decoderOutputBufferInfo.offset)
                                        decodedData.limit(decoderOutputBufferInfo.offset + decoderOutputBufferInfo.size)
                                        encoderInputBuffer.clear()
                                        encoderInputBuffer.put(decodedData)
                                        encoder.queueInputBuffer(
                                            encoderInputIndex,
                                            0,
                                            decoderOutputBufferInfo.size,
                                            decoderOutputBufferInfo.presentationTimeUs,
                                            0
                                        )
                                        totalBytesProcessed += decoderOutputBufferInfo.size
                                    }
                                } else if (sawOutputEOS) {
                                    encoder.queueInputBuffer(
                                        encoderInputIndex,
                                        0,
                                        0,
                                        0,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                    )
                                    encoderSawInputEOS = true
                                }
                                decoder.releaseOutputBuffer(decoderOutputIndex, false)
                            }
                        }
                    }
                }

                // Get encoded data and write to muxer
                val encoderOutputIndex = encoder.dequeueOutputBuffer(encoderOutputBufferInfo, 10000)
                when {
                    encoderOutputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {}
                    encoderOutputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {}
                    encoderOutputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        // Add track to muxer when we get the format
                        val newFormat = encoder.outputFormat
                        audioTrackIndexInMuxer = muxer.addTrack(newFormat)
                        muxer.start()
                        android.util.Log.d("AudioExtractor", "Added audio track to muxer: $audioTrackIndexInMuxer")
                    }
                    else -> {
                        if (encoderOutputBufferInfo.size > 0 && audioTrackIndexInMuxer >= 0) {
                            val encodedData = encoder.getOutputBuffer(encoderOutputIndex)
                            encodedData?.apply {
                                position(encoderOutputBufferInfo.offset)
                                limit(encoderOutputBufferInfo.offset + encoderOutputBufferInfo.size)
                                muxer.writeSampleData(audioTrackIndexInMuxer, this, encoderOutputBufferInfo)
                                totalSamples++
                            }
                        }

                        if (encoderOutputBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            encoderSawOutputEOS = true
                        }
                        encoder.releaseOutputBuffer(encoderOutputIndex, false)
                    }
                }
            }

            // Stop and release
            encoder.stop()
            decoder.stop()
            muxer.stop()

            android.util.Log.d("AudioExtractor", "Extraction complete. File: ${outputFile.absolutePath}, Size: ${outputFile.length()} bytes")

            // Get actual duration from the extracted file
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
                actualDuration = duration
            }

            Result.success(AudioExtractionResult(outputFile.absolutePath, actualDuration, OUTPUT_FORMAT_NAME))
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
            encoder?.release()
            decoder?.release()
            muxer?.release()
            extractor?.release()
        }
    }

    suspend fun extractAudioToAAC(
        videoUri: Uri,
        outputFileName: String,
        progressCallback: (Float) -> Unit = {}
    ): Result<AudioExtractionResult> = extractAudioFromVideo(videoUri, outputFileName, progressCallback)
}

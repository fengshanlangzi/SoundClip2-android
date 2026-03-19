package com.example.androidmusic3.util

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class AudioExtractor(private val context: Context) {

    suspend fun extractAudioFromVideo(
        videoUri: Uri,
        outputFileName: String,
        progressCallback: (Float) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val outputFile = File(context.getExternalFilesDir("extracted_audio"), outputFileName)

            val extractor = MediaExtractor()
            extractor.setDataSource(context, videoUri, null)

            // Find audio track
            var audioTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    break
                }
            }

            if (audioTrackIndex == -1) {
                extractor.release()
                return@withContext Result.failure(Exception("No audio track found in video"))
            }

            val format = extractor.getTrackFormat(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            val duration = format.getLong(MediaFormat.KEY_DURATION)

            extractor.selectTrack(audioTrackIndex)

            // Write raw audio data to file
            FileOutputStream(outputFile).use { fos ->
                val maxBufferSize = 1024 * 1024 // 1MB buffer
                val buffer = java.nio.ByteBuffer.allocate(maxBufferSize)
                val bufferInfo = MediaCodec.BufferInfo()

                var totalBytesRead = 0L

                while (true) {
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize <= 0) break

                    val presentationTimeUs = extractor.sampleTime
                    bufferInfo.offset = 0
                    bufferInfo.size = sampleSize
                    bufferInfo.presentationTimeUs = presentationTimeUs
                    bufferInfo.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                        MediaCodec.BUFFER_FLAG_KEY_FRAME
                    } else {
                        0
                    }

                    val data = ByteArray(bufferInfo.size)
                    buffer.position(bufferInfo.offset)
                    buffer.get(data)
                    fos.write(data)

                    // Update progress
                    val progress = if (duration > 0) {
                        (presentationTimeUs.toFloat() / duration * 100)
                    } else {
                        0f
                    }
                    progressCallback(progress)

                    extractor.advance()
                    totalBytesRead += sampleSize
                }
            }

            extractor.release()

            Result.success(outputFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun extractAudioToAAC(
        videoUri: Uri,
        outputFileName: String,
        progressCallback: (Float) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val outputFile = File(context.getExternalFilesDir("extracted_audio"), outputFileName)

            val extractor = MediaExtractor()
            extractor.setDataSource(context, videoUri, null)

            // Find audio track
            var audioTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    break
                }
            }

            if (audioTrackIndex == -1) {
                extractor.release()
                return@withContext Result.failure(Exception("No audio track found in video"))
            }

            val inputFormat = extractor.getTrackFormat(audioTrackIndex)
            val duration = inputFormat.getLong(MediaFormat.KEY_DURATION)

            extractor.selectTrack(audioTrackIndex)

            // Create decoder
            val decoder = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME)!!)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            // Create encoder for AAC
            val outputFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            )
            outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128000)
            outputFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)

            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            // Write to file
            FileOutputStream(outputFile).use { fos ->
                val decoderBufferInfo = MediaCodec.BufferInfo()
                val encoderBufferInfo = MediaCodec.BufferInfo()

                var inputDone = false
                var outputDone = false
                var totalProcessed = 0L

                while (!outputDone) {
                    // Feed decoder
                    if (!inputDone) {
                        val inputBufferIndex = decoder.dequeueInputBuffer(10000)
                        if (inputBufferIndex >= 0) {
                            val sampleSize = extractor.readSampleData(decoder.getInputBuffer(inputBufferIndex)!!, 0)
                            if (sampleSize > 0) {
                                decoder.queueInputBuffer(
                                    inputBufferIndex,
                                    0,
                                    sampleSize,
                                    extractor.sampleTime,
                                    extractor.sampleFlags
                                )
                                extractor.advance()
                            } else {
                                decoder.queueInputBuffer(
                                    inputBufferIndex,
                                    0,
                                    0,
                                    0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            }
                        }
                    }

                    // Get decoder output
                    val decoderOutputIndex = decoder.dequeueOutputBuffer(decoderBufferInfo, 10000)
                    if (decoderOutputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        // Continue
                    } else if (decoderOutputIndex >= 0) {
                        if (decoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            encoder.signalEndOfInputStream()
                            inputDone = true
                        }

                        val encoderInputIndex = encoder.dequeueInputBuffer(10000)
                        if (encoderInputIndex >= 0) {
                            if (decoderBufferInfo.size > 0) {
                                val data = ByteArray(decoderBufferInfo.size)
                                decoder.getOutputBuffer(decoderOutputIndex)!!.get(data)
                                encoder.getInputBuffer(encoderInputIndex)!!.put(data)
                                encoder.queueInputBuffer(
                                    encoderInputIndex,
                                    0,
                                    data.size,
                                    decoderBufferInfo.presentationTimeUs,
                                    0
                                )
                                totalProcessed += decoderBufferInfo.presentationTimeUs
                                progressCallback(if (duration > 0) totalProcessed.toFloat() / duration * 100 else 0f)
                            } else {
                                encoder.queueInputBuffer(
                                    encoderInputIndex,
                                    0,
                                    0,
                                    0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                            }
                        }
                        decoder.releaseOutputBuffer(decoderOutputIndex, false)
                    }

                    // Get encoder output
                    val encoderOutputIndex = encoder.dequeueOutputBuffer(encoderBufferInfo, 10000)
                    if (encoderOutputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        // Continue
                    } else if (encoderOutputIndex >= 0) {
                        if (encoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            // Codec config data - ADTS header
                            val codecConfigData = ByteArray(encoderBufferInfo.size)
                            encoder.getOutputBuffer(encoderOutputIndex)!!.get(codecConfigData)
                        } else {
                            val data = ByteArray(encoderBufferInfo.size)
                            encoder.getOutputBuffer(encoderOutputIndex)!!.get(data)

                            // Add ADTS header
                            val adtsHeader = createADTSHeader(data.size + 7, outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE))
                            fos.write(adtsHeader)
                            fos.write(data)
                        }

                        if (encoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }

                        encoder.releaseOutputBuffer(encoderOutputIndex, false)
                    }
                }
            }

            decoder.stop()
            decoder.release()
            encoder.stop()
            encoder.release()
            extractor.release()

            Result.success(outputFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun createADTSHeader(frameLength: Int, sampleRate: Int): ByteArray {
        val samplingRates = intArrayOf(96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350)
        var sampleRateIndex = 4 // Default to 44100
        for (i in samplingRates.indices) {
            if (samplingRates[i] == sampleRate) {
                sampleRateIndex = i
                break
            }
        }

        val header = ByteArray(7)
        header[0] = 0xFF.toByte() // Syncword
        header[1] = 0xF1.toByte() // Syncword + ID + Layer + Protection absent
        header[2] = ((sampleRateIndex shl 2) or 0x40).toByte() // Profile + Sampling frequency index + Private bit
        header[3] = 0x80.toByte() // Channel configuration + Original/Copy
        header[4] = ((frameLength shr 3) and 0xFF).toByte() // Frame length
        header[5] = ((frameLength shl 5) and 0xFF).toByte()
        header[6] = 0xFC.toByte() // Buffer fullness + Number of AAC frames

        return header
    }
}

package com.example.live

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class AudioEngine(
    private val context: Context,
    private val onAudioChunkCaptured: (String) -> Unit,
    private val onVolumeLevelChanged: (Float) -> Unit,
    private val onWakeWordDetected: () -> Unit
) {
    companion object {
        private const val TAG = "AudioEngine"
        private const val SAMPLE_RATE_IN = 16000
        private const val SAMPLE_RATE_OUT = 24000
    }

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var recordJob: Job? = null
    @Volatile private var isRecording = false
    @Volatile private var isPlaying = false

    fun startRecording(scope: CoroutineScope) {
        if (isRecording) return

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Cannot start recording: RECORD_AUDIO permission not granted")
            return
        }

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_IN,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val bufferSize = maxOf(minBufferSize, 3200)

        try {
            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE_IN,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord state is not initialized")
                record.release()
                return
            }

            audioRecord = record
            record.startRecording()
            isRecording = true
            initAudioTrack()

            recordJob = scope.launch(Dispatchers.IO) {
                val buffer = ByteArray(1024)
                while (isActive && isRecording) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (read > 0) {
                        // Calculate RMS for live visualizer level
                        val rms = calculateRMS(buffer, read)
                        val normLevel = (rms / 3000f).coerceIn(0f, 1f)
                        onVolumeLevelChanged(normLevel)

                        // Light wake word / energy spike check when idle
                        if (rms > 8000f) {
                            onWakeWordDetected()
                        }

                        // Convert to Base64 PCM for Gemini WebSocket
                        val pcmChunk = buffer.copyOf(read)
                        val base64Data = Base64.encodeToString(pcmChunk, Base64.NO_WRAP)
                        onAudioChunkCaptured(base64Data)
                    }
                }
            }
            Log.d(TAG, "Audio recording started successfully")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting AudioRecord: missing permissions", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioRecord", e)
        }
    }

    fun stopRecording() {
        isRecording = false
        recordJob?.cancel()
        recordJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audioRecord", e)
        }
        audioRecord = null
    }

    private fun initAudioTrack() {
        if (audioTrack != null) return
        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE_OUT,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE_OUT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBufferSize, 8192))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
            isPlaying = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioTrack", e)
        }
    }

    fun playAudioPcm(pcmBytes: ByteArray) {
        if (audioTrack == null || !isPlaying) {
            initAudioTrack()
        }
        try {
            audioTrack?.write(pcmBytes, 0, pcmBytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to AudioTrack", e)
        }
    }

    fun stopPlaybackAndFlush() {
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Error flushing AudioTrack", e)
        }
    }

    fun release() {
        stopRecording()
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioTrack", e)
        }
        audioTrack = null
        isPlaying = false
    }

    private fun calculateRMS(buffer: ByteArray, readSize: Int): Float {
        var sum = 0.0
        var i = 0
        while (i < readSize - 1) {
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            val sampleShort = sample.toShort()
            sum += sampleShort * sampleShort
            i += 2
        }
        return if (readSize > 0) sqrt(sum / (readSize / 2)).toFloat() else 0f
    }
}

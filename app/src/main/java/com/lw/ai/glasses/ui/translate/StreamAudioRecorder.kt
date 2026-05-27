package com.lw.ai.glasses.ui.translate

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class StreamAudioRecorder(private val context: Context) {

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null

    private var fileOutputStream: FileOutputStream? = null
    private var currentFilePath: String? = null
    private var acousticEchoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var automaticGainControl: AutomaticGainControl? = null
    private var previousAudioMode: Int? = null

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    @SuppressLint("MissingPermission")
    fun start(fileName: String, onAudioData: (ByteArray) -> Unit) {
        if (isRecording) return

        enableCommunicationMode()

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        ) * 2

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            minBufferSize
        )
        enableAudioEffects(audioRecord?.audioSessionId ?: 0)

        val file = File(context.cacheDir, "$fileName.pcm")
        LogUtils.d("录音文件地址：$file")
        currentFilePath = file.absolutePath
        fileOutputStream = FileOutputStream(file)

        try {
            audioRecord?.startRecording()
            isRecording = true
        } catch (e: Exception) {
            e.printStackTrace()
            releaseAudioRecordResources()
            return
        }

        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(minBufferSize)

            while (isActive && isRecording) {
                val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0

                if (readSize > 0) {
                    val validBytes = buffer.copyOf(readSize)

                    onAudioData(validBytes)

                    try {
                        fileOutputStream?.write(validBytes)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    suspend fun stop(): String? {
        if (!isRecording) return null

        isRecording = false

        try {
            audioRecord?.stop()
            recordingJob?.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            releaseAudioRecordResources()
        }

        return withContext(Dispatchers.IO) {
            convertPcmToWav(currentFilePath)
        }
    }

    fun getCurrentAmplitude(): Float {
        return 0f
    }

    private fun enableCommunicationMode() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        previousAudioMode = audioManager.mode
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
    }

    private fun restoreAudioMode() {
        val audioMode = previousAudioMode ?: return
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = audioMode
        previousAudioMode = null
    }

    private fun enableAudioEffects(audioSessionId: Int) {
        if (audioSessionId == 0) return

        try {
            if (AcousticEchoCanceler.isAvailable()) {
                acousticEchoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply {
                    enabled = true
                }
                LogUtils.d("AcousticEchoCanceler enabled=${acousticEchoCanceler?.enabled}")
            }
        } catch (e: Exception) {
            LogUtils.e("Enable AcousticEchoCanceler failed", e)
        }
        try {
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply {
                    enabled = true
                }
                LogUtils.d("NoiseSuppressor enabled=${noiseSuppressor?.enabled}")
            }
        } catch (e: Exception) {
            LogUtils.e("Enable NoiseSuppressor failed", e)
        }
        try {
            if (AutomaticGainControl.isAvailable()) {
                automaticGainControl = AutomaticGainControl.create(audioSessionId)?.apply {
                    enabled = true
                }
                LogUtils.d("AutomaticGainControl enabled=${automaticGainControl?.enabled}")
            }
        } catch (e: Exception) {
            LogUtils.e("Enable AutomaticGainControl failed", e)
        }
    }

    private fun releaseAudioRecordResources() {
        try {
            acousticEchoCanceler?.release()
            acousticEchoCanceler = null
            noiseSuppressor?.release()
            noiseSuppressor = null
            automaticGainControl?.release()
            automaticGainControl = null

            audioRecord?.release()
            audioRecord = null

            fileOutputStream?.flush()
            fileOutputStream?.close()
            fileOutputStream = null
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            restoreAudioMode()
        }
    }

    private fun convertPcmToWav(pcmPath: String?): String? {
        if (pcmPath == null) return null
        val pcmFile = File(pcmPath)
        if (!pcmFile.exists()) return null

        val wavFile = File(pcmPath.replace(".pcm", ".wav"))

        try {
            val pcmStream = FileInputStream(pcmFile)
            val wavStream = FileOutputStream(wavFile)

            val totalAudioLen = pcmStream.channel.size()
            val totalDataLen = totalAudioLen + 36
            val longSampleRate = SAMPLE_RATE.toLong()
            val channels = 1
            val byteRate = (SAMPLE_RATE * 16 * channels / 8).toLong()

            writeWavHeader(wavStream, totalAudioLen, totalDataLen, longSampleRate, channels, byteRate)

            val data = ByteArray(1024)
            var length: Int
            while (pcmStream.read(data).also { length = it } != -1) {
                wavStream.write(data, 0, length)
            }

            pcmStream.close()
            wavStream.close()

            pcmFile.delete()

            return wavFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun writeWavHeader(
        out: FileOutputStream, totalAudioLen: Long,
        totalDataLen: Long, longSampleRate: Long, channels: Int, byteRate: Long
    ) {
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte()
        header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (longSampleRate and 0xff).toByte()
        header[25] = (longSampleRate shr 8 and 0xff).toByte()
        header[26] = (longSampleRate shr 16 and 0xff).toByte()
        header[27] = (longSampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (2 * 16 / 8).toByte()
        header[33] = 0
        header[34] = 16
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = (totalAudioLen shr 8 and 0xff).toByte()
        header[42] = (totalAudioLen shr 16 and 0xff).toByte()
        header[43] = (totalAudioLen shr 24 and 0xff).toByte()
        out.write(header, 0, 44)
    }
}
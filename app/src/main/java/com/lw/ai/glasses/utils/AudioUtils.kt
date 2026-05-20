package com.lw.ai.glasses.utils

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object AudioUtils {
    /**
     * Convert PCM16LE to WAV and delete the original PCM file.
     *
     * @return wav absolute path, or null on failure.
     */
    fun convertPcmToWav(
        pcmPath: String?,
        sampleRate: Int = 16000,
        channels: Int = 1,
        bitsPerSample: Int = 16,
    ): String? {
        if (pcmPath.isNullOrBlank()) return null
        val pcmFile = File(pcmPath)
        if (!pcmFile.exists()) return null
        val wavFile = File(pcmFile.parentFile, pcmFile.nameWithoutExtension + ".wav")

        return try {
            FileInputStream(pcmFile).use { pcmStream ->
                FileOutputStream(wavFile).use { wavStream ->
                    val totalAudioLen = pcmStream.channel.size()
                    val totalDataLen = totalAudioLen + 36
                    val byteRate = (sampleRate * bitsPerSample * channels / 8).toLong()
                    writeWavHeader(
                        wavStream,
                        totalAudioLen,
                        totalDataLen,
                        sampleRate.toLong(),
                        channels,
                        byteRate,
                        bitsPerSample,
                    )

                    val data = ByteArray(4096)
                    while (true) {
                        val read = pcmStream.read(data)
                        if (read <= 0) break
                        wavStream.write(data, 0, read)
                    }
                }
            }
            pcmFile.delete()
            wavFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private fun writeWavHeader(
        out: FileOutputStream,
        totalAudioLen: Long,
        totalDataLen: Long,
        longSampleRate: Long,
        channels: Int,
        byteRate: Long,
        bitsPerSample: Int,
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
        header[32] = ((channels * bitsPerSample) / 8).toByte()
        header[33] = 0
        header[34] = bitsPerSample.toByte()
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


package com.lw.ai.glasses.ui.live

import com.fission.wear.glasses.sdk.util.FissionLogUtils
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory

private const val TAG = "LyRtspSocketFactory"


private const val SESSION_TIMEOUT_SECONDS = 10


internal class RtspPlayRewriteSocketFactory(
    private val delegate: SocketFactory,
) : SocketFactory() {

    override fun createSocket(host: String, port: Int): Socket =
        RewritingSocket(delegate.createSocket(host, port))

    override fun createSocket(
        host: String,
        port: Int,
        localHost: InetAddress,
        localPort: Int,
    ): Socket = RewritingSocket(delegate.createSocket(host, port, localHost, localPort))

    override fun createSocket(host: InetAddress, port: Int): Socket =
        RewritingSocket(delegate.createSocket(host, port))

    override fun createSocket(
        host: InetAddress,
        port: Int,
        localAddr: InetAddress,
        localPort: Int,
    ): Socket = RewritingSocket(delegate.createSocket(host, port, localAddr, localPort))

    /** 同一条 RTSP 连接内，入向捕获 Content-Base、出向据此改写，二者共享该状态。 */
    private class RewriteState {
        @Volatile
        var contentBase: String? = null
    }

    /** 仅重写 [getInputStream]/[getOutputStream]，其余方法透传给真实（已绑定 AP 网络的）socket。 */
    private class RewritingSocket(private val delegate: Socket) : Socket() {
        private val state = RewriteState()
        private val wrappedInput: InputStream by lazy {
            RtspResponseRewritingInputStream(delegate.getInputStream(), state)
        }
        private val wrappedOutput: OutputStream by lazy {
            PlayUrlRewritingOutputStream(delegate.getOutputStream(), state)
        }

        override fun getInputStream(): InputStream = wrappedInput
        override fun getOutputStream(): OutputStream = wrappedOutput
        override fun close() = delegate.close()
        override fun isConnected(): Boolean = delegate.isConnected
        override fun isClosed(): Boolean = delegate.isClosed
        override fun isBound(): Boolean = delegate.isBound
        override fun getInetAddress(): InetAddress? = delegate.inetAddress
        override fun getLocalAddress(): InetAddress = delegate.localAddress
        override fun getPort(): Int = delegate.port
        override fun getLocalPort(): Int = delegate.localPort
        override fun setSoTimeout(timeout: Int) = delegate.setSoTimeout(timeout)
        override fun getSoTimeout(): Int = delegate.soTimeout
        override fun setTcpNoDelay(on: Boolean) = delegate.setTcpNoDelay(on)
        override fun getTcpNoDelay(): Boolean = delegate.tcpNoDelay
        override fun setKeepAlive(on: Boolean) = delegate.setKeepAlive(on)
        override fun getKeepAlive(): Boolean = delegate.keepAlive
        override fun setReceiveBufferSize(size: Int) = delegate.setReceiveBufferSize(size)
        override fun getReceiveBufferSize(): Int = delegate.receiveBufferSize
        override fun setSendBufferSize(size: Int) = delegate.setSendBufferSize(size)
        override fun getSendBufferSize(): Int = delegate.sendBufferSize
        override fun shutdownInput() = delegate.shutdownInput()
        override fun shutdownOutput() = delegate.shutdownOutput()
        override fun isInputShutdown(): Boolean = delegate.isInputShutdown
        override fun isOutputShutdown(): Boolean = delegate.isOutputShutdown
        override fun toString(): String = delegate.toString()
    }

    /**
     * 入向：信令阶段按行处理响应——
     *  1) 捕获 `Content-Base` 的值存入 [state]（原样输出，供 media3 拼 SETUP 的 track URI）；
     *  2) 把 `Session: <id>` 改写为 `Session: <id>;timeout=[SESSION_TIMEOUT_SECONDS]`，令 media3 的
     *     keep-alive 间隔 = timeout/2（RtspClient: intervalMs = sessionTimeoutMs / 2）。服务器默认不下发
     *     timeout → media3 用 60s → 间隔 30s，恰好撞上眼镜 ~30s 看门狗；改小后每 5s 保活一次，持续压住看门狗。
     * 一旦遇到 interleaved RTP 二进制帧（行首 `$`）或扫描超限，即切换为纯透传，避免行缓冲拖延 RTP。
     */
    private class RtspResponseRewritingInputStream(
        source: InputStream,
        private val state: RewriteState,
    ) : FilterInputStream(source) {

        private val line = ArrayList<Int>(160)
        private var out: ByteArray = ByteArray(0)
        private var outPos = 0
        private var rawMode = false
        private var scanned = 0
        private var sessionLogged = false

        override fun read(): Int {
            while (true) {
                if (outPos < out.size) return out[outPos++].toInt() and 0xFF
                if (rawMode) return `in`.read()
                if (!fillLine()) return -1
            }
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            while (true) {
                if (outPos < out.size) {
                    val n = minOf(len, out.size - outPos)
                    System.arraycopy(out, outPos, b, off, n)
                    outPos += n
                    return n
                }
                if (rawMode) return `in`.read(b, off, len)
                if (!fillLine()) return -1
            }
        }

        override fun available(): Int {
            val buffered = (out.size - outPos).coerceAtLeast(0)
            return buffered + `in`.available()
        }

        /** 读取并处理下一行；返回 false 表示真正 EOF。进入 RTP 二进制阶段后切换为透传。 */
        private fun fillLine(): Boolean {
            line.clear()
            val first = `in`.read()
            if (first == -1) {
                rawMode = true
                return false
            }
            scanned++
            if (first == DOLLAR) {
                // interleaved RTP/RTCP 二进制帧开始：本字节起全部透传，不再行缓冲。
                rawMode = true
                out = byteArrayOf(first.toByte())
                outPos = 0
                return true
            }
            line.add(first)
            while (true) {
                val v = `in`.read()
                if (v == -1) {
                    rawMode = true
                    break
                }
                scanned++
                line.add(v)
                if (v == LF) break
                if (scanned > SCAN_LIMIT) {
                    rawMode = true
                    break
                }
            }
            out = processLine(line)
            outPos = 0
            return true
        }

        private fun processLine(bytes: ArrayList<Int>): ByteArray {
            if (matchesPrefix(bytes, CONTENT_BASE_PREFIX)) {
                val base = headerValue(bytes, CONTENT_BASE_PREFIX.size, bytes.size)
                if (base.isNotEmpty() && state.contentBase == null) {
                    state.contentBase = base
                    FissionLogUtils.i(TAG, "捕获 RTSP Content-Base=$base，PLAY/keep-alive 将改写为该真实流地址")
                }
                return toBytes(bytes) // Content-Base 原样保留（media3 需要它拼 SETUP 的 track URI）
            }
            if (matchesPrefix(bytes, SESSION_PREFIX)) {
                return rewriteSessionLine(bytes)
            }
            return toBytes(bytes)
        }

        /** `Session: <id>[;timeout=x]` + EOL → `Session: <id>;timeout=[SESSION_TIMEOUT_SECONDS]` + 原 EOL。 */
        private fun rewriteSessionLine(bytes: ArrayList<Int>): ByteArray {
            var valueEnd = bytes.size
            for (i in bytes.indices) {
                val c = (bytes[i] and 0xFF).toChar()
                if (c == '\r' || c == '\n') {
                    valueEnd = i
                    break
                }
            }
            val id = headerValue(bytes, SESSION_PREFIX.size, valueEnd).substringBefore(';').trim()
            if (id.isEmpty()) return toBytes(bytes)
            val eol = StringBuilder()
            for (i in valueEnd until bytes.size) eol.append((bytes[i] and 0xFF).toChar())
            if (!sessionLogged) {
                sessionLogged = true
                FissionLogUtils.i(
                    TAG,
                    "改写 RTSP Session timeout=${SESSION_TIMEOUT_SECONDS}s → keep-alive 间隔≈${SESSION_TIMEOUT_SECONDS / 2}s（压制 30s 看门狗）",
                )
            }
            return ("Session: $id;timeout=$SESSION_TIMEOUT_SECONDS" + eol).toByteArray(Charsets.US_ASCII)
        }

        private fun headerValue(bytes: ArrayList<Int>, from: Int, to: Int): String {
            val sb = StringBuilder()
            for (i in from until to) {
                val c = (bytes[i] and 0xFF).toChar()
                if (c == '\r' || c == '\n') break
                sb.append(c)
            }
            return sb.toString().trim()
        }

        private fun matchesPrefix(bytes: ArrayList<Int>, prefix: CharArray): Boolean {
            if (bytes.size < prefix.size) return false
            for (i in prefix.indices) {
                if ((bytes[i] and 0xFF).toChar().lowercaseChar() != prefix[i]) return false
            }
            return true
        }

        private fun toBytes(bytes: ArrayList<Int>): ByteArray =
            ByteArray(bytes.size) { bytes[it].toByte() }

        private companion object {
            private const val LF = 0x0A
            private const val DOLLAR = 0x24 // '$'：interleaved RTP/RTCP 二进制帧首字节
            private const val SCAN_LIMIT = 16384
            private val CONTENT_BASE_PREFIX = "content-base:".toCharArray()
            private val SESSION_PREFIX = "session:".toCharArray()
        }
    }

    /**
     * 出向：media3 每个 RTSP 请求以单次 [write] 完整写出（RtspMessageChannel.Sender 用
     * convertMessageToByteArray 一次性 write 整条消息），据此对请求行做原子改写。
     *
     * 改写条件：已捕获 Content-Base、是可改写方法、且 URL 尚未落在该 base 下（即仍是 xxx.mov）。
     * 其余一律原样透传，包括：DESCRIBE 前的请求（base 未捕获）、SETUP 的 `00000001/trackN`
     * （已在 base 下）、以及 interleaved 二进制 RTCP 帧（首字节 `$`）。
     */
    private class PlayUrlRewritingOutputStream(
        sink: OutputStream,
        private val state: RewriteState,
    ) : FilterOutputStream(sink) {

        override fun write(b: Int) {
            out.write(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            val base = state.contentBase
            if (base == null || len <= 0 || (b[off].toInt() and 0xFF) == DOLLAR) {
                out.write(b, off, len)
                return
            }
            val rewritten = rewriteRequestLine(b, off, len, base)
            if (rewritten != null) {
                out.write(rewritten)
            } else {
                out.write(b, off, len)
            }
        }

        override fun flush() {
            out.flush()
        }

        override fun close() {
            out.close()
        }

        /** 命中改写条件时返回改写后的整段字节；否则返回 null（表示原样透传）。 */
        private fun rewriteRequestLine(
            b: ByteArray,
            off: Int,
            len: Int,
            base: String,
        ): ByteArray? {
            var lineEnd = -1
            for (i in 0 until len) {
                if ((b[off + i].toInt() and 0xFF) == LF) {
                    lineEnd = i
                    break
                }
            }
            if (lineEnd < 0) return null
            val firstLine = String(b, off, lineEnd + 1, Charsets.US_ASCII)
            val parts = firstLine.split(' ')
            if (parts.size < 3) return null
            val method = parts[0]
            val url = parts[1]
            if (!REWRITABLE_METHODS.contains(method)) return null
            if (url.startsWith(base)) return null
            val crlf = if (firstLine.endsWith("\r\n")) "\r\n" else "\n"
            val newFirstLine = "$method $base $RTSP_VERSION$crlf"
            val newLineBytes = newFirstLine.toByteArray(Charsets.US_ASCII)
            val restStart = off + lineEnd + 1
            val restLen = len - (lineEnd + 1)
            val result = ByteArray(newLineBytes.size + restLen)
            System.arraycopy(newLineBytes, 0, result, 0, newLineBytes.size)
            if (restLen > 0) {
                System.arraycopy(b, restStart, result, newLineBytes.size, restLen)
            }
            FissionLogUtils.i(TAG, "改写 RTSP 请求 URL：$method $url → $base")
            return result
        }

        private companion object {
            private const val LF = 0x0A
            private const val DOLLAR = 0x24 // '$'：interleaved RTP/RTCP 二进制帧首字节
            private const val RTSP_VERSION = "RTSP/1.0"
            private val REWRITABLE_METHODS =
                setOf("PLAY", "OPTIONS", "GET_PARAMETER", "PAUSE", "SET_PARAMETER", "TEARDOWN")
        }
    }
}

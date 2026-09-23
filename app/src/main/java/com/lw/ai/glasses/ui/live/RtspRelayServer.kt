package com.lw.ai.glasses.ui.live

import android.content.Context
import android.net.Network
import android.util.Base64
import com.fission.wear.glasses.sdk.util.FissionLogUtils
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext
import java.io.*
import java.net.*
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "RtspRelayServer"
private const val SESSION_TIMEOUT_SECONDS = 10

/**
 * 本地 RTSP 中继：将眼镜 RTSP 流转为 localhost TCP interleaved RTSP。
 *
 * 用途：T 系列 RTSP 服务端仅支持 UDP RTP，中继服务通过 [apNetwork] 绑定 AP 网络拉流，
 * 在 localhost 以 TCP interleaved 方式提供 RTSP，ExoPlayer 连 localhost 即可，
 * 无需 bindProcessToNetwork，蜂窝推流不受影响。
 */
internal class RtspRelayServer(
    private val appContext: Context,
    private val upstreamUrl: String,
    private val apNetwork: Network,
    /** 是否保留音频轨（默认 false：仅视频预览）。 */
    private val enableAudio: Boolean = false,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val sessions = ConcurrentHashMap<Int, RelaySession>()
    private var serverSocket: ServerSocket? = null
    private var localPort = 0

    /** T 系列 SDP 视频轨缺 sprop-parameter-sets，探测缓存的 SPS,PPS（base64，逗号分隔）。 */
    private var videoSprop: String? = null
    /** 探测得到的 profile-level-id（HEX），可能为空。 */
    private var videoProfileLevelId: String? = null
    /** [T 系列] 探测上游是否支持 RTSP-over-TCP interleaved；支持则用 TCP 拉流，规避 UDP 空口丢包（花屏/卡顿根因）。 */
    private var upstreamSupportsTcp = false

    /** 启动中继，返回 localhost RTSP URL（如 `rtsp://127.0.0.1:PORT/<path>`）。 */
    suspend fun start(): String = withContext(Dispatchers.IO) {
        val path = extractPath(upstreamUrl)
        val ss = ServerSocket()
        ss.reuseAddress = true
        ss.bind(InetSocketAddress("127.0.0.1", 0))
        serverSocket = ss
        localPort = ss.localPort
        FissionLogUtils.i(TAG, "relay listening on 127.0.0.1:$localPort → $upstreamUrl")
        // T 系列：ExoPlayer 连接前先探测 SPS/PPS，供 SDP 注入使用
        probeVideoSprop()
        // T 系列：探测上游是否支持 TCP interleaved；支持则改用 TCP 拉流，从根源规避 UDP 空口丢包
        probeTcpSupport()
        scope.launch { acceptLoop(ss, path) }
        "rtsp://127.0.0.1:$localPort$path"
    }

    fun stop() {
        FissionLogUtils.d(TAG, "stopping relay")
        scope.cancel()
        runCatching { serverSocket?.close() }
        sessions.values.forEach { it.close() }
        sessions.clear()
    }

    // ── accept loop ──────────────────────────────────────────────────

    private suspend fun acceptLoop(ss: ServerSocket, upstreamPath: String) {
        while (coroutineContext.isActive) {
            try {
                val client = ss.accept()
                FissionLogUtils.d(TAG, "client accepted from ${client.localPort}")
                val session = RelaySession(client, upstreamUrl, upstreamPath, apNetwork, appContext, localPort, videoSprop, videoProfileLevelId, enableAudio, upstreamSupportsTcp)
                sessions[client.localPort] = session
                scope.launch {
                    try {
                        session.run()
                    } catch (e: Exception) {
                        FissionLogUtils.w(TAG, "session error: ${e.message}")
                    } finally {
                        sessions.remove(client.localPort)
                        session.close()
                        FissionLogUtils.d(TAG, "session removed")
                    }
                }
            } catch (_: SocketException) {
                break
            }
        }
    }

    private fun extractPath(url: String): String {
        val afterScheme = url.substringAfter("://", url)
        val slashIdx = afterScheme.indexOf('/')
        return if (slashIdx >= 0) afterScheme.substring(slashIdx) else "/"
    }

    // ── SPS/PPS 探测（T 系列 SDP 缺 sprop-parameter-sets） ──────────

    /**
     * T 系列眼镜的 H264 SDP 不含 `a=fmtp:...;sprop-parameter-sets=`，而 ExoPlayer(media3)
     * 强制要求该属性作为解码初始化数据（csd）。此处在 ExoPlayer 连接前，独立发起一次
     * OPTIONS→DESCRIBE→SETUP(UDP)→PLAY，从 RTP 流中嗅探 SPS(NAL 7)/PPS(NAL 8)，
     * 完成后 TEARDOWN 并关闭，将结果缓存供 [RtspRelaySession]/rewriteSdp 注入。
     */
    private fun probeVideoSprop() {
        if (!upstreamUrl.contains("/h264", ignoreCase = true)) return // 仅 T 系列
        var probeSocket: Socket? = null
        var rtpSock: DatagramSocket? = null
        var rtcpSock: DatagramSocket? = null
        try {
            val sf = apNetwork.socketFactory
            probeSocket = sf.createSocket(probeHost(upstreamUrl), probePort(upstreamUrl))
            probeSocket.soTimeout = 6000
            val out = probeSocket.getOutputStream()
            val inp = probeSocket.getInputStream()

            var cseq = 0
            writeRtsp(out, "OPTIONS $upstreamUrl RTSP/1.0", ++cseq, emptyMap())
            readRtspMessage(inp)

            writeRtsp(out, "DESCRIBE $upstreamUrl RTSP/1.0", ++cseq, mapOf("Accept" to "application/sdp"))
            val desc = readRtspMessage(inp)
            val control = parseVideoControl(desc.second) ?: "track0"

            val pair = allocateProbeUdp()
            rtpSock = pair.first
            rtcpSock = pair.second
            val rtpPort = rtpSock.localPort
            val rtcpPort = rtcpSock.localPort
            val setupUri = if (control.startsWith("rtsp://", ignoreCase = true)) control
            else upstreamUrl.trimEnd('/') + "/" + control

            writeRtsp(
                out, "SETUP $setupUri RTSP/1.0", ++cseq,
                mapOf("Transport" to "RTP/AVP;unicast;client_port=$rtpPort-$rtcpPort"),
            )
            val setupResp = readRtspMessage(inp)
            val session = parseHeader(setupResp.first, "Session")?.substringBefore(";")?.trim() ?: ""

            writeRtsp(
                out, "PLAY $upstreamUrl RTSP/1.0", ++cseq,
                mapOf("Session" to session, "Range" to "npt=0.000-"),
            )
            readRtspMessage(inp)

            val sprop = collectSpsPps(rtpSock, 6000)
            if (sprop != null) {
                videoSprop = sprop.first
                videoProfileLevelId = sprop.second
                FissionLogUtils.i(
                    TAG,
                    "probed sprop-parameter-sets=${sprop.first.take(32)}... profile-level-id=${sprop.second}",
                )
            } else {
                FissionLogUtils.w(TAG, "probe found no SPS/PPS in RTP stream")
            }

            writeRtsp(out, "TEARDOWN $upstreamUrl RTSP/1.0", ++cseq, mapOf("Session" to session))
            runCatching { readRtspMessage(inp) }
        } catch (e: Exception) {
            FissionLogUtils.w(TAG, "probe SPS/PPS error: ${e.message}")
        } finally {
            runCatching { rtpSock?.close() }
            runCatching { rtcpSock?.close() }
            runCatching { probeSocket?.close() }
        }
    }

    /**
     * [T 系列] 探测上游 RTSP 服务端是否接受 TCP interleaved SETUP。
     * T 系列默认仅用 UDP RTP，但开音轨后视频+音频挤占 AP 上行会导致空口丢包（花屏/卡顿），
     * 而 TCP 有重传可根治。此处独立发起 OPTIONS→DESCRIBE→SETUP(TCP)，若返回 200 且响应
     * Transport 含 interleaved，则判定支持，[forwardSetup] 改走 TCP 透传；否则维持 UDP。
     */
    private fun probeTcpSupport() {
        if (!upstreamUrl.contains("/h264", ignoreCase = true)) return // 仅 T 系列
        var s: Socket? = null
        try {
            val sf = apNetwork.socketFactory
            s = sf.createSocket(probeHost(upstreamUrl), probePort(upstreamUrl))
            s.soTimeout = 4000
            val out = s.getOutputStream()
            val inp = s.getInputStream()
            var cseq = 0
            writeRtsp(out, "OPTIONS $upstreamUrl RTSP/1.0", ++cseq, emptyMap())
            readRtspMessage(inp)
            writeRtsp(out, "DESCRIBE $upstreamUrl RTSP/1.0", ++cseq, mapOf("Accept" to "application/sdp"))
            val desc = readRtspMessage(inp)
            val control = parseVideoControl(desc.second) ?: "track0"
            val setupUri = if (control.startsWith("rtsp://", ignoreCase = true)) control
            else upstreamUrl.trimEnd('/') + "/" + control
            writeRtsp(
                out, "SETUP $setupUri RTSP/1.0", ++cseq,
                mapOf("Transport" to "RTP/AVP/TCP;unicast;interleaved=0-1"),
            )
            val resp = readRtspMessage(inp)
            val statusLine = resp.first.lineSequence().firstOrNull() ?: ""
            val transport = parseHeader(resp.first, "Transport") ?: ""
            upstreamSupportsTcp = statusLine.contains("200") && transport.contains("interleaved", ignoreCase = true)
            val session = parseHeader(resp.first, "Session")?.substringBefore(";")?.trim() ?: ""
            if (session.isNotEmpty()) {
                writeRtsp(out, "TEARDOWN $upstreamUrl RTSP/1.0", ++cseq, mapOf("Session" to session))
                runCatching { readRtspMessage(inp) }
            }
            FissionLogUtils.i(
                TAG,
                "T-series TCP interleaved probe: supported=$upstreamSupportsTcp (status='$statusLine' transport='$transport')",
            )
        } catch (e: Exception) {
            FissionLogUtils.w(TAG, "probe TCP support error: ${e.message}")
        } finally {
            runCatching { s?.close() }
        }
    }

    private fun writeRtsp(out: OutputStream, requestLine: String, cseq: Int, extra: Map<String, String>) {
        val sb = StringBuilder()
        sb.append(requestLine).append("\r\n")
        sb.append("CSeq: ").append(cseq).append("\r\n")
        for ((k, v) in extra) sb.append(k).append(": ").append(v).append("\r\n")
        sb.append("\r\n")
        out.write(sb.toString().toByteArray(Charsets.US_ASCII))
        out.flush()
    }

    /** 读取一条完整 RTSP 消息，返回 (headers, body)。 */
    private fun readRtspMessage(inp: InputStream): Pair<String, String> {
        val headerLines = mutableListOf<String>()
        val cur = StringBuilder()
        var contentLength = 0
        while (true) {
            val b = inp.read()
            if (b == -1) break
            when {
                b == '\n'.code -> {
                    val line = cur.toString()
                    cur.setLength(0)
                    if (line.isEmpty()) {
                        if (headerLines.isNotEmpty()) break
                    } else {
                        headerLines.add(line)
                        if (line.startsWith("Content-Length:", ignoreCase = true)) {
                            contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                        }
                    }
                }
                b != '\r'.code -> cur.append(b.toChar())
            }
        }
        var body = ""
        if (contentLength > 0) {
            val buf = ByteArray(contentLength)
            var off = 0
            while (off < contentLength) {
                val n = inp.read(buf, off, contentLength - off)
                if (n == -1) break
                off += n
            }
            body = String(buf, 0, off, Charsets.UTF_8)
        }
        return Pair(headerLines.joinToString("\r\n"), body)
    }

    private fun parseVideoControl(sdp: String): String? {
        var inVideo = false
        for (raw in sdp.lines()) {
            val l = raw.trim()
            if (l.startsWith("m=")) {
                inVideo = l.startsWith("m=video")
            } else if (inVideo && l.startsWith("a=control:", ignoreCase = true)) {
                return l.substringAfter(":").trim()
            }
        }
        return null
    }

    private fun parseHeader(headers: String, name: String): String? {
        for (line in headers.split("\r\n")) {
            if (line.startsWith("$name:", ignoreCase = true)) return line.substringAfter(":").trim()
        }
        return null
    }

    private fun allocateProbeUdp(): Pair<DatagramSocket, DatagramSocket> {
        for (p in 20000..60000 step 2) {
            try {
                val rtp = DatagramSocket(null)
                apNetwork.bindSocket(rtp)
                rtp.bind(InetSocketAddress("0.0.0.0", p))
                try {
                    val rtcp = DatagramSocket(null)
                    apNetwork.bindSocket(rtcp)
                    rtcp.bind(InetSocketAddress("0.0.0.0", p + 1))
                    return Pair(rtp, rtcp)
                } catch (e: Exception) {
                    rtp.close()
                }
            } catch (_: Exception) {
            }
        }
        throw IOException("probe: cannot allocate UDP port pair")
    }

    /** 从 RTP 流收集 SPS/PPS，返回 (base64SPS,base64PPS 组合串, profileLevelId)。 */
    private fun collectSpsPps(sock: DatagramSocket, timeoutMs: Long): Pair<String, String>? {
        sock.soTimeout = timeoutMs.toInt()
        val buf = ByteArray(65536)
        val deadline = System.currentTimeMillis() + timeoutMs
        var sps: ByteArray? = null
        var pps: ByteArray? = null
        while (System.currentTimeMillis() < deadline && (sps == null || pps == null)) {
            val pkt = DatagramPacket(buf, buf.size)
            try {
                sock.receive(pkt)
            } catch (_: SocketTimeoutException) {
                break
            } catch (_: SocketException) {
                break
            }
            for (nal in extractH264Nals(pkt.data, pkt.offset, pkt.length)) {
                if (nal.isEmpty()) continue
                when (nal[0].toInt() and 0x1F) {
                    7 -> sps = nal
                    8 -> pps = nal
                }
            }
        }
        val s = sps
        val p = pps
        if (s == null || p == null) return null
        val spsB64 = Base64.encodeToString(s, Base64.NO_WRAP)
        val ppsB64 = Base64.encodeToString(p, Base64.NO_WRAP)
        val plid = if (s.size >= 4) {
            String.format("%02X%02X%02X", s[1].toInt() and 0xFF, s[2].toInt() and 0xFF, s[3].toInt() and 0xFF)
        } else {
            ""
        }
        return Pair("$spsB64,$ppsB64", plid)
    }

    /** 解析 RTP 包，返回其中的 H264 NAL（含 NAL 头字节，无起始码）。支持单 NAL 与 STAP-A。 */
    private fun extractH264Nals(data: ByteArray, offset: Int, length: Int): List<ByteArray> {
        val nals = mutableListOf<ByteArray>()
        if (length < 12) return nals
        var pos = offset
        val b0 = data[pos].toInt() and 0xFF
        val cc = b0 and 0x0F
        val hasExt = (b0 and 0x10) != 0
        pos += 12 + cc * 4
        val end = offset + length
        if (hasExt) {
            if (pos + 4 > end) return nals
            val extLen = ((data[pos + 2].toInt() and 0xFF) shl 8) or (data[pos + 3].toInt() and 0xFF)
            pos += 4 + extLen * 4
        }
        if (pos >= end) return nals
        when (val nalType = data[pos].toInt() and 0x1F) {
            in 1..23 -> nals.add(data.copyOfRange(pos, end))
            24 -> { // STAP-A
                pos += 1
                while (pos + 2 <= end) {
                    val size = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
                    pos += 2
                    if (size <= 0 || pos + size > end) break
                    nals.add(data.copyOfRange(pos, pos + size))
                    pos += size
                }
            }
        }
        return nals
    }

    private fun probeHost(url: String): String {
        val afterScheme = url.substringAfter("://", url)
        return afterScheme.substringBefore(":").substringBefore("/")
    }

    private fun probePort(url: String): Int {
        val afterScheme = url.substringAfter("://", url)
        val hostPart = afterScheme.substringBefore("/")
        val colonIdx = hostPart.indexOf(':')
        return if (colonIdx > 0) hostPart.substring(colonIdx + 1).toIntOrNull() ?: 554 else 554
    }

    // ── relay session ────────────────────────────────────────────────

    private class RelaySession(
        private val clientSocket: Socket,
        private val upstreamUrl: String,
        private val upstreamPath: String,
        private val apNetwork: Network,
        private val appContext: Context,
        private val localPort: Int,
        private val videoSprop: String?,
        private val videoProfileLevelId: String?,
        private val enableAudio: Boolean,
        /** [T 系列] 上游是否支持 TCP interleaved（探测结果）。 */
        private val upstreamSupportsTcp: Boolean = false,
    ) {
        /** S 系列 (LIVE555) 需要 PLAY URL 改写 + Content-Base 改写 + Session timeout 改写。 */
        private val isSLive = !upstreamUrl.contains("/h264", ignoreCase = true)
        /** 是否走上游 TCP interleaved 拉流：S 系列原生支持；T 系列探测支持时也用 TCP（规避 UDP 空口丢包）。 */
        private val useUpstreamTcp = isSLive || upstreamSupportsTcp
        private var contentBase: String? = null
        private val relayHostPort = "127.0.0.1:${localPort}"
        private val writeLock = Any()
        private var clientOut: OutputStream = clientSocket.getOutputStream()
        private var upstreamSocket: Socket? = null
        private var upstreamOut: OutputStream? = null
        private val udpSockets = mutableListOf<DatagramSocket>()
        private var sessionScope: CoroutineScope? = null

        /** SDP 中音频轨的 control（如 `track2`），DESCRIBE 时解析。 */
        private var audioControlPath: String? = null
        /** 音频轨的 interleaved RTP 通道集合，SETUP 时记录。 */
        private val audioChannels = mutableSetOf<Int>()

        suspend fun run() = withContext(Dispatchers.IO) {
            sessionScope = this

            // 1. connect upstream (glasses) via AP network
            val sf = apNetwork.socketFactory
            val host = extractHost(upstreamUrl)
            val port = extractPort(upstreamUrl)
            val us = sf.createSocket(host, port)
            upstreamSocket = us
            upstreamOut = us.getOutputStream()
            FissionLogUtils.d(TAG, "upstream connected: $upstreamUrl")

            // 2. bidirectional RTSP command relay
            launch { relayClientToUpstream() }
            launch { relayUpstreamToClient() }
        }

        // ── client → upstream (RTSP requests) ───────────────────────

        private suspend fun relayClientToUpstream() {
            val clientIn = clientSocket.getInputStream()
            while (true) {
                // read request line
                val requestLine = readLine(clientIn) ?: break
                FissionLogUtils.d(TAG, "C→U: $requestLine")
                val parts = requestLine.split(" ")
                val method = parts.getOrElse(0) { "" }
                val clientPath = if (parts.size >= 2) extractClientPath(parts[1]) else "/"
                val cseq = readHeadersAndForward(clientIn, method, clientPath)
                FissionLogUtils.d(TAG, "forwarded $method CSeq=$cseq")
            }
        }

        /**
         * Read all headers from client, parse method-specific info,
         * rewrite if needed, and forward upstream.
         * Returns the CSeq value.
         */
        private suspend fun readHeadersAndForward(
            clientIn: InputStream,
            method: String,
            clientPath: String,
        ): String {
            val headers = linkedMapOf<String, String>()
            var cseq = ""
            while (true) {
                val line = readLine(clientIn) ?: break
                if (line.isEmpty()) break
                val colonIdx = line.indexOf(':')
                if (colonIdx > 0) {
                    val name = line.substring(0, colonIdx).trim()
                    val value = line.substring(colonIdx + 1).trim()
                    headers[name] = value
                    if (name.equals("CSeq", ignoreCase = true)) cseq = value
                }
            }

            val out = upstreamOut ?: return cseq
            when {
                method.equals("SETUP", ignoreCase = true) -> {
                    // S 系列：SETUP URL 需从 xxx.mov/trackN 改写为 00000001/trackN
                    val setupUri = if (isSLive) {
                        rewriteSetupUri(buildUpstreamUri(clientPath))
                    } else {
                        buildUpstreamUri(clientPath)
                    }
                    forwardSetup(headers, out, setupUri)
                }
                method.equals("TEARDOWN", ignoreCase = true) -> {
                    forwardLine(out, "TEARDOWN ${buildUpstreamUri(clientPath)} RTSP/1.0")
                    for ((k, v) in headers) forwardHeader(out, k, v)
                    forwardCrlf(out)
                }
                else -> {
                    // OPTIONS / DESCRIBE / PLAY / PAUSE / GET_PARAMETER
                    val uri = buildUpstreamUri(clientPath)
                    // S 系列：PLAY/OPTIONS/PAUSE/TEARDOWN 的 URL 从 xxx.mov 改写为 00000001/
                    val rewrittenUri = if (isSLive && isPlayRewriteMethod(method)) {
                        rewritePlayUrl(uri)
                    } else {
                        uri
                    }
                    forwardLine(out, "$method $rewrittenUri RTSP/1.0")
                    for ((k, v) in headers) forwardHeader(out, k, v)
                    forwardCrlf(out)
                }
            }
            return cseq
        }

        private fun forwardSetup(headers: Map<String, String>, out: OutputStream, setupUri: String) {
            val transport = headers["Transport"] ?: headers["transport"] ?: ""
            // parse interleaved=X-Y
            val interleavedMatch = Regex("interleaved=(\\d+)-(\\d+)").find(transport)
            val rtpChannel = interleavedMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val rtcpChannel = interleavedMatch?.groupValues?.get(2)?.toIntOrNull() ?: (rtpChannel + 1)

            // 记录音频轨 RTP 通道（用于补置 marker 位与 ADTS 剥离）。SETUP URI 末段与 SDP 音频 control 匹配即为音频。
            if (isAudioSetupUri(setupUri)) {
                audioChannels.add(rtpChannel)
            }

            if (useUpstreamTcp) {
                // 上游支持 RTSP-over-TCP interleaved：直接透传 TCP，上游按客户端请求的 channel 号
                // 回传 $CH[LEN][DATA]，由 relayUpstreamToClient 原样转发给客户端。S 系列原生支持；
                // T 系列探测支持时也走此路径，从根源规避 UDP 空口丢包（花屏/卡顿）。
                forwardLine(out, "SETUP $setupUri RTSP/1.0")
                for ((k, v) in headers) {
                    if (k.equals("Transport", ignoreCase = true)) {
                        forwardHeader(
                            out, k,
                            "RTP/AVP/TCP;unicast;interleaved=$rtpChannel-$rtcpChannel",
                        )
                    } else {
                        forwardHeader(out, k, v)
                    }
                }
                forwardCrlf(out)
                FissionLogUtils.i(
                    TAG,
                    "SETUP → upstream TCP interleaved=$rtpChannel-$rtcpChannel (pass-through isSLive=$isSLive)",
                )
                return
            }

            // T 系列：RTSP 服务端仅支持 UDP RTP，分配 UDP 端口收流后转成 interleaved 回传。
            val (rtpSock, rtcpSock) = allocateUdpPair()
            val rtpPort = rtpSock.localPort
            val rtcpPort = rtcpSock.localPort

            // start UDP receivers → client interleaved
            sessionScope?.launch { receiveUdp(rtpSock, rtpChannel) }
            sessionScope?.launch { receiveUdp(rtcpSock, rtcpChannel) }

            // forward SETUP to glasses with UDP transport
            forwardLine(out, "SETUP $setupUri RTSP/1.0")
            for ((k, v) in headers) {
                if (k.equals("Transport", ignoreCase = true)) {
                    forwardHeader(
                        out, k,
                        "RTP/AVP;unicast;client_port=$rtpPort-$rtcpPort;server_port=$rtpPort-$rtcpPort",
                    )
                } else {
                    forwardHeader(out, k, v)
                }
            }
            forwardCrlf(out)
            FissionLogUtils.i(
                TAG,
                "SETUP → upstream UDP rtp=$rtpPort rtcp=$rtcpPort " +
                    "interleaved=$rtpChannel-$rtcpChannel",
            )
        }

        // ── upstream → client (RTSP responses + interleaved RTP) ────

        private suspend fun relayUpstreamToClient() {
            val upstreamIn = upstreamSocket?.getInputStream() ?: return
            while (true) {
                val firstByte = upstreamIn.read()
                if (firstByte == -1) break
                when (firstByte.toChar()) {
                    '$' -> forwardInterleavedFrame(upstreamIn)
                    'R', 'r', 'S', 's' -> {
                        // start of RTSP response — read full response
                        val sb = StringBuilder().append(firstByte.toChar())
                        forwardResponse(sb, upstreamIn)
                    }
                    else -> {
                        // might be partial "RTSP/1.0" split across reads
                        val sb = StringBuilder().append(firstByte.toChar())
                        forwardResponse(sb, upstreamIn)
                    }
                }
            }
        }

        /** Read an interleaved frame (3 more header bytes + data) and forward to client. */
        private fun forwardInterleavedFrame(upstreamIn: InputStream) {
            val header = ByteArray(3)
            if (readFully(upstreamIn, header) != 3) return
            val channel = header[0].toInt() and 0xFF
            val length = ((header[1].toInt() and 0xFF) shl 8) or (header[2].toInt() and 0xFF)
            val data = ByteArray(length)
            if (length > 0 && readFully(upstreamIn, data) != length) return

            var outData = data
            var outLen = length
            if (channel in audioChannels && length >= 12) {
                // 防御性：单 AU AAC 包应置 marker=1（实测 S 系列本就为 1）。
                data[1] = ((data[1].toInt() and 0xFF) or 0x80).toByte()
                // 核心修复：S 系列在 AAC-hbr RTP 载荷里发的是带 ADTS 头的 AAC，但 SDP
                // config=1408 声明为 raw AAC，media3 按 raw AAC 配置解码器，收到 ADTS 会解码
                // 失败→无声→音频主时钟饥饿拖垮视频。此处剥离 ADTS 头转为 raw AAC。
                val stripped = stripAdtsFromAudioRtp(data, length)
                if (stripped != null) {
                    outData = stripped
                    outLen = stripped.size
                    header[1] = ((outLen shr 8) and 0xFF).toByte()
                    header[2] = (outLen and 0xFF).toByte()
                }
            }
            synchronized(writeLock) {
                try {
                    clientOut.write(0x24) // '$'
                    clientOut.write(header)
                    if (outLen > 0) clientOut.write(outData, 0, outLen)
                    clientOut.flush()
                } catch (_: IOException) {
                }
            }
        }

        /**
         * 剥离 AAC-hbr RTP 载荷中单个 AU 的 ADTS 头，转为 raw AAC（与 SDP config 声明一致）。
         * 仅处理单 AU 包（auHeadersBitLength==16，实测 S 系列均如此），同步修正 AU-size。
         * @return 改写后的完整 RTP 包；若非 ADTS 或多 AU（不处理）则返回 null（原样转发）。
         */
        private fun stripAdtsFromAudioRtp(data: ByteArray, length: Int): ByteArray? {
            if (length < 16) return null
            val cc = data[0].toInt() and 0x0F
            val hasExt = (data[0].toInt() and 0x10) != 0
            var p = 12 + cc * 4
            if (hasExt) {
                if (p + 4 > length) return null
                val extLen = ((data[p + 2].toInt() and 0xFF) shl 8) or (data[p + 3].toInt() and 0xFF)
                p += 4 + extLen * 4
            }
            if (p + 4 > length) return null
            val auHeadersBitLen = ((data[p].toInt() and 0xFF) shl 8) or (data[p + 1].toInt() and 0xFF)
            if (auHeadersBitLen != 16) return null // 仅处理单 AU
            val auHeaderOff = p + 2
            val auDataOff = auHeaderOff + 2
            if (auDataOff + 9 > length) return null
            // 校验 ADTS sync word 0xFFF
            if ((data[auDataOff].toInt() and 0xFF) != 0xFF ||
                (data[auDataOff + 1].toInt() and 0xF0) != 0xF0
            ) {
                return null // 非 ADTS，原样转发
            }
            val protectionAbsent = data[auDataOff + 1].toInt() and 0x01
            val adtsLen = if (protectionAbsent == 1) 7 else 9
            if (auDataOff + adtsLen >= length) return null
            val auHeader16 = ((data[auHeaderOff].toInt() and 0xFF) shl 8) or (data[auHeaderOff + 1].toInt() and 0xFF)
            val auIndex = auHeader16 and 0x07
            val oldSize13 = (auHeader16 shr 3) and 0x1FFF
            val newAuSize = oldSize13 - adtsLen
            if (newAuSize <= 0) return null
            val out = ByteArray(length - adtsLen)
            // RTP 头 + AU-headers-length 原样拷贝
            System.arraycopy(data, 0, out, 0, auHeaderOff)
            // 修正 AU-header：[AU-size(13) | AU-index(3)]
            val newHeader = (newAuSize shl 3) or auIndex
            out[auHeaderOff] = ((newHeader shr 8) and 0xFF).toByte()
            out[auHeaderOff + 1] = (newHeader and 0xFF).toByte()
            // 拷贝 raw AAC（跳过 ADTS 头）
            System.arraycopy(data, auDataOff + adtsLen, out, auDataOff, length - auDataOff - adtsLen)
            return out
        }

        /** Read a full RTSP response (status + headers + optional body) and forward. */
        private suspend fun forwardResponse(
            sb: StringBuilder,
            upstreamIn: InputStream,
        ) {
            // read until \r\n\r\n
            var consecutiveNewlines = 0
            val headerBytes = ByteArrayOutputStream()
            // write the first char(s) already read
            headerBytes.write(sb.toString().toByteArray(Charsets.US_ASCII))

            var contentLength = 0
            var isDescribeResponse = false

            while (true) {
                val b = upstreamIn.read()
                if (b == -1) break
                headerBytes.write(b)
                val c = b.toChar()
                when {
                    c == '\r' -> { /* skip, wait for \n */ }
                    c == '\n' -> {
                        consecutiveNewlines++
                        if (consecutiveNewlines >= 2) break
                    }
                    else -> consecutiveNewlines = 0
                }
            }

            val headerStr = String(headerBytes.toByteArray(), Charsets.US_ASCII)
            val headerLines = headerStr.split("\r\n")
            if (headerLines.isEmpty()) return

            // detect DESCRIBE response (has SDP body)
            val statusLine = headerLines[0]
            if (statusLine.contains("200", ignoreCase = true)) {
                // check if this might be a DESCRIBE response by looking for Content-Type: application/sdp
                for (line in headerLines) {
                    if (line.contains("application/sdp", ignoreCase = true)) {
                        isDescribeResponse = true
                    }
                    if (line.startsWith("Content-Length:", ignoreCase = true) ||
                        line.startsWith("Content-length:", ignoreCase = true)
                    ) {
                        contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                    }
                }
            }

            if (isDescribeResponse && contentLength > 0) {
                // read SDP body
                val bodyBytes = ByteArray(contentLength)
                readFully(upstreamIn, bodyBytes)
                val sdp = String(bodyBytes, Charsets.UTF_8)
                audioControlPath = parseAudioControl(sdp)
                val rewrittenSdp = rewriteSdp(sdp)
                val rewrittenBody = rewrittenSdp.toByteArray(Charsets.UTF_8)

                // rebuild response: rewrite Content-Base to relay address (S 系列), update Content-Length
                val newHeaders = buildString {
                    for (line in headerLines) {
                        when {
                            line.startsWith("Content-Base:", ignoreCase = true) -> {
                                // S 系列：保留 Content-Base 但改写地址为 relay localhost，
                                // 迫使 ExoPlayer 用 Content-Base 路径解析 SETUP URL
                                val cb = line.substringAfter(":").trim()
                                val cbPath = cb.substringAfter("://").substringAfter("/", "")
                                val rewrittenCb = "rtsp://$relayHostPort/$cbPath"
                                contentBase = rewrittenCb
                                append("Content-Base: $rewrittenCb\r\n")
                                FissionLogUtils.d(TAG, "rewrite Content-Base: $cb → $rewrittenCb")
                            }
                            line.startsWith("Content-Length:", ignoreCase = true) ||
                                line.startsWith("Content-length:", ignoreCase = true) ->
                                append("Content-Length: ${rewrittenBody.size}\r\n")
                            line.isEmpty() -> { /* skip, we add our own CRLF */ }
                            else -> append(line).append("\r\n")
                        }
                    }
                    append("\r\n")
                }
                synchronized(writeLock) {
                    try {
                        clientOut.write(newHeaders.toByteArray(Charsets.US_ASCII))
                        clientOut.write(rewrittenBody)
                        clientOut.flush()
                    } catch (_: IOException) {
                    }
                }
                FissionLogUtils.d(TAG, "DESCRIBE response SDP rewritten (${sdp.length}→${rewrittenSdp.length})")
            } else {
                // non-DESCRIBE response: rewrite Session timeout (保活), forward
                val rewrittenHeaders = rewriteResponseHeaders(headerLines)
                synchronized(writeLock) {
                    try {
                        clientOut.write(rewrittenHeaders.toByteArray(Charsets.US_ASCII))
                        if (contentLength > 0) {
                            val body = ByteArray(contentLength)
                            readFully(upstreamIn, body)
                            clientOut.write(body)
                        }
                        clientOut.flush()
                    } catch (_: IOException) {
                    }
                }
            }
        }

        // ── UDP receiver → TCP interleaved ──────────────────────────

        /**
         * 接收上游 UDP RTP/RTCP，封装为 TCP interleaved 帧（$CH[LEN][DATA]）转发给客户端。
         * 预留 4 字节 interleaved 头，RTP 数据落在 offset=4，转发时单次 write（减少 syscall 与锁持有时间，
         * 避免接收循环被写入阻塞而导致内核 UDP 缓冲溢出丢包）。
         * 仅在 T 系列 TCP 探测失败回退 UDP 时使用。
         */
        private suspend fun receiveUdp(socket: DatagramSocket, channel: Int) {
            val buf = ByteArray(65536 + 4)
            buf[0] = 0x24 // '$'
            buf[1] = channel.toByte()
            try {
                while (!socket.isClosed) {
                    val packet = DatagramPacket(buf, 4, buf.size - 4)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketException) {
                        break
                    }
                    val len = packet.length
                    buf[2] = ((len shr 8) and 0xFF).toByte()
                    buf[3] = (len and 0xFF).toByte()
                    synchronized(writeLock) {
                        try {
                            clientOut.write(buf, 0, len + 4) // 单次 write：interleaved 头 + RTP 数据
                            clientOut.flush()
                        } catch (_: IOException) {
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        // ── SDP rewriting ───────────────────────────────────────────

        /**
         * Rewrite SDP:
         * - 剔除音频轨（m=audio 段），ExoPlayer 要求 AAC 有 a=fmtp 而 T 系列音频轨可能缺失
         * - 移除 `decode_buf` 等非标准厂商属性
         * - T 系列视频轨注入探测到的 sprop-parameter-sets（ExoPlayer H264 硬要求）
         */
        private fun rewriteSdp(sdp: String): String {
            val lines = sdp.lines().map { it.trimEnd() }
            val result = mutableListOf<String>()
            var skipAudio = false
            for (line in lines) {
                when {
                    line.startsWith("m=audio") -> skipAudio = true
                    line.startsWith("m=") -> skipAudio = false
                }
                // enableAudio=false 时剔除整个音频段；true 时保留音轨。
                if (!enableAudio && skipAudio) continue
                if (line.startsWith("a=decode_buf", ignoreCase = true)) continue
                result.add(line)
            }
            if (!videoSprop.isNullOrEmpty()) {
                injectVideoFmtp(result)
            }
            return result.joinToString("\r\n") + "\r\n"
        }

        /** 解析 SDP 中音频轨（m=audio 段）的 a=control 值，返回如 `track2`。 */
        private fun parseAudioControl(sdp: String): String? {
            var inAudio = false
            for (raw in sdp.lines()) {
                val l = raw.trim()
                if (l.startsWith("m=")) {
                    inAudio = l.startsWith("m=audio")
                } else if (inAudio && l.startsWith("a=control:", ignoreCase = true)) {
                    return l.substringAfter(":").trim()
                }
            }
            return null
        }

        /** SETUP URI 末段是否与 SDP 音频 control 匹配（判定该 SETUP 是否为音频轨）。 */
        private fun isAudioSetupUri(uri: String): Boolean {
            val ac = audioControlPath ?: return false
            val acLast = ac.trimEnd('/').substringAfterLast('/')
            val uriLast = uri.trimEnd('/').substringAfterLast('/')
            return acLast.isNotEmpty() && acLast.equals(uriLast, ignoreCase = true)
        }

        /** 在视频轨（若无 a=fmtp:<pt>）的 rtpmap 行后注入 sprop-parameter-sets。 */
        private fun injectVideoFmtp(lines: MutableList<String>) {
            var videoStart = -1
            var videoPt: String? = null
            for (i in lines.indices) {
                if (lines[i].startsWith("m=video")) {
                    videoStart = i
                    videoPt = lines[i].split(" ").getOrNull(3)
                    break
                }
            }
            if (videoStart < 0 || videoPt.isNullOrEmpty()) return
            var videoEnd = lines.size
            for (i in (videoStart + 1) until lines.size) {
                if (lines[i].startsWith("m=")) { videoEnd = i; break }
            }
            // 已有 fmtp 则不注入
            for (i in videoStart until videoEnd) {
                if (lines[i].startsWith("a=fmtp:$videoPt", ignoreCase = true)) return
            }
            var insertAt = videoStart + 1
            for (i in videoStart until videoEnd) {
                if (lines[i].startsWith("a=rtpmap:$videoPt", ignoreCase = true)) { insertAt = i + 1; break }
            }
            val plid = if (!videoProfileLevelId.isNullOrEmpty()) "profile-level-id=$videoProfileLevelId;" else ""
            val fmtp = "a=fmtp:$videoPt packetization-mode=1;${plid}sprop-parameter-sets=$videoSprop"
            lines.add(insertAt, fmtp)
            FissionLogUtils.i(TAG, "injected video fmtp: ${fmtp.take(64)}...")
        }

        // ── S 系列协议改写 ──────────────────────────────────────────

        /** S 系列：改写 Session 头 timeout=10s（keep-alive 5s，避免 30s 看门狗断流）。 */
        private fun rewriteResponseHeaders(headerLines: List<String>): String {
            return buildString {
                for (line in headerLines) {
                    if (line.startsWith("Session:", ignoreCase = true)) {
                        val sessionId = line.substringAfter(":").substringBefore(";").trim()
                        append("Session: $sessionId;timeout=$SESSION_TIMEOUT_SECONDS\r\n")
                    } else if (line.isEmpty()) {
                        // skip, we add our own CRLF
                    } else {
                        append(line).append("\r\n")
                    }
                }
                append("\r\n")
            }
        }

        private fun isPlayRewriteMethod(method: String): Boolean {
            val m = method.uppercase()
            return m == "PLAY" || m == "OPTIONS" || m == "PAUSE" || m == "TEARDOWN"
        }

        /** S 系列：将 xxx.mov/trackN 改写为 00000001/trackN（Content-Base 指向的真实流路径）。 */
        private fun rewriteSetupUri(uri: String): String {
            val cb = contentBase ?: return uri
            val basePath = cb.substringAfter("://").substringAfter("/", "")
            if (basePath.isEmpty()) return uri
            if (uri.contains(basePath)) return uri // 已改写过
            // 提取 track 后缀（如 track1）
            val clientPath = uri.substringAfter("://").substringAfter("/", "")
            val trackSuffix = clientPath.substringAfter("/", "")
            val scheme = uri.substringBefore("://")
            val hostPort = uri.substringAfter("://").substringBefore("/")
            return if (trackSuffix.isNotEmpty()) {
                "$scheme://$hostPort/$basePath$trackSuffix"
            } else {
                "$scheme://$hostPort/$basePath"
            }
        }

        /** S 系列：将 xxx.mov 改写为 00000001/（Content-Base 指向的真实流路径）。 */
        private fun rewritePlayUrl(uri: String): String {
            val cb = contentBase ?: return uri
            // Content-Base 形如 rtsp://ip/00000001/，提取路径部分
            val basePath = cb.substringAfter("://").substringAfter("/", "")
            if (uri.contains(basePath)) return uri // 已改写过
            return if (basePath.isNotEmpty()) {
                val scheme = uri.substringBefore("://")
                val hostPort = uri.substringAfter("://").substringBefore("/")
                "$scheme://$hostPort/$basePath"
            } else {
                uri
            }
        }

        // ── UDP port allocation ─────────────────────────────────────

        private fun allocateUdpPair(): Pair<DatagramSocket, DatagramSocket> {
            for (p in 10000..60000 step 2) {
                try {
                    val rtp = DatagramSocket(null)
                    apNetwork.bindSocket(rtp)
                    rtp.bind(InetSocketAddress("0.0.0.0", p))
                    // 增大内核接收缓冲，吸收视频突发；避免 relay 写入繁忙时 UDP 丢包（花屏根因之一）。
                    runCatching { rtp.receiveBufferSize = 4 * 1024 * 1024 }
                    var rtcp: DatagramSocket? = null
                    try {
                        rtcp = DatagramSocket(null)
                        apNetwork.bindSocket(rtcp)
                        rtcp.bind(InetSocketAddress("0.0.0.0", p + 1))
                        runCatching { rtcp.receiveBufferSize = 1024 * 1024 }
                        udpSockets.add(rtp)
                        udpSockets.add(rtcp)
                        FissionLogUtils.i(
                            TAG,
                            "UDP pair allocated rtp=$p rcvBuf=${rtp.receiveBufferSize} rtcp=${p + 1}",
                        )
                        return Pair(rtp, rtcp)
                    } catch (e: Exception) {
                        rtp.close()
                        rtcp?.close()
                    }
                } catch (_: Exception) {
                }
            }
            throw IOException("Cannot allocate UDP port pair")
        }

        // ── helpers ─────────────────────────────────────────────────

        /** 用上游的 scheme://host:port + 客户端请求的 path 构建上游 URI。 */
        private fun buildUpstreamUri(clientPath: String): String {
            val upstreamScheme = upstreamUrl.substringBefore("://")
            val upstreamHostPort = upstreamUrl.substringAfter("://").substringBefore("/")
            return "$upstreamScheme://$upstreamHostPort$clientPath"
        }

        private fun forwardLine(out: OutputStream, line: String) {
            out.write((line + "\r\n").toByteArray(Charsets.US_ASCII))
        }

        private fun forwardHeader(out: OutputStream, name: String, value: String) {
            out.write("$name: $value\r\n".toByteArray(Charsets.US_ASCII))
        }

        private fun forwardCrlf(out: OutputStream) {
            out.write("\r\n".toByteArray(Charsets.US_ASCII))
            out.flush()
        }

        fun close() {
            runCatching { clientSocket.close() }
            runCatching { upstreamSocket?.close() }
            udpSockets.forEach { runCatching { it.close() } }
            udpSockets.clear()
        }

        private companion object {
            fun readLine(input: InputStream): String? {
                val sb = StringBuilder()
                while (true) {
                    val b = input.read()
                    if (b == -1) return if (sb.isEmpty()) null else sb.toString()
                    if (b == '\r'.code) continue
                    if (b == '\n'.code) return sb.toString()
                    sb.append(b.toChar())
                }
            }

            fun readFully(input: InputStream, buf: ByteArray): Int {
                var offset = 0
                while (offset < buf.size) {
                    val n = input.read(buf, offset, buf.size - offset)
                    if (n == -1) return offset
                    offset += n
                }
                return offset
            }

            fun extractHost(url: String): String {
                val afterScheme = url.substringAfter("://", url)
                return afterScheme.substringBefore(":").substringBefore("/")
            }

            fun extractPort(url: String): Int {
                val afterScheme = url.substringAfter("://", url)
                val hostPart = afterScheme.substringBefore("/")
                val colonIdx = hostPart.indexOf(':')
                return if (colonIdx > 0) hostPart.substring(colonIdx + 1).toIntOrNull() ?: 554
                else 554
            }

            fun extractClientPath(uri: String): String {
                val afterScheme = uri.substringAfter("://", uri)
                val slashIdx = afterScheme.indexOf('/')
                return if (slashIdx >= 0) afterScheme.substring(slashIdx) else "/"
            }
        }
    }
}

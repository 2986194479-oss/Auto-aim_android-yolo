package team.maodie.aimbot.remote

import android.util.Log
import java.io.DataInputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 主机 TCP 客户端：连接副机推理服务，收检测帧 + 心跳超时判定。
 *
 * 协议见 RemoteProtocol。
 * 工作线程：connect -> 循环读帧 -> 按类型分派（DETECT 回调 / HEARTBEAT 刷新时间戳）
 * 超时：超过 TIMEOUT_MS 没收到任何帧则判定断线，触发 onDisconnect 并退出。
 */
class RemoteDetectClient(
    private val host: String,
    private val port: Int,
    private val onDetect: (RemoteProtocol.DetectFrame) -> Unit,
    private val onDisconnect: () -> Unit
) {
    companion object { private const val TAG = "RemoteDetectClient" }

    private val running = AtomicBoolean(false)
    @Volatile private var socket: Socket? = null
    @Volatile private var thread: Thread? = null
    @Volatile private var lastRecvMs = 0L
    @Volatile private var seq = 0

    fun start() {
        if (running.getAndSet(true)) return
        lastRecvMs = System.currentTimeMillis()
        val t = Thread({ runLoop() }, "RemoteDetectClient")
        t.isDaemon = true
        thread = t
        t.start()
    }

    fun stop() {
        running.set(false)
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        thread?.interrupt()
        thread = null
    }

    private fun runLoop() {
        try {
            val s = Socket()
            s.tcpNoDelay = true
            s.connect(InetSocketAddress(host, port), 3000)
            socket = s
            Log.d(TAG, "已连接副机 $host:$port")
            val input = DataInputStream(s.getInputStream())
            val header = ByteArray(RemoteProtocol.HEADER_LEN)
            while (running.get()) {
                // 读满 8 字节帧头
                input.readFully(header)
                val parsed = RemoteProtocol.parseHeader(header) ?: continue
                val (type, frameLen) = parsed
                val payloadLen = frameLen - RemoteProtocol.HEADER_LEN
                if (payloadLen < 0 || payloadLen > 65535) continue
                val payload = if (payloadLen > 0) ByteArray(payloadLen).also { input.readFully(it) } else ByteArray(0)
                lastRecvMs = System.currentTimeMillis()
                when (type) {
                    RemoteProtocol.TYPE_DETECT -> {
                        val frame = RemoteProtocol.parseDetect(payload)
                        if (frame != null) onDetect(frame)
                    }
                    RemoteProtocol.TYPE_HEARTBEAT -> { /* 仅刷新时间戳 */ }
                    else -> { }
                }
            }
        } catch (e: IOException) {
            if (running.get()) Log.w(TAG, "IO 异常: ${e.message}")
        } catch (e: Exception) {
            if (running.get()) Log.e(TAG, "runLoop 异常: ${e.message}")
        } finally {
            try { socket?.close() } catch (_: Exception) {}
            socket = null
            val wasRunning = running.getAndSet(false)
            if (wasRunning) {
                Log.w(TAG, "副机连接断开")
                onDisconnect()
            }
        }
    }
}

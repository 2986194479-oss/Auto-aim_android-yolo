package team.maodie.aimbot.remote

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 双机协作二进制帧协议（主副两端共用）
 * 帧头 8 字节（小端）：
 *   [0]   magic0 = 0xA5
 *   [1]   magic1 = 0x5A
 *   [2-3] frame_len uint16（含帧头）
 *   [4]   type uint8: 0x01=检测帧 0x02=心跳 0x03=握手
 *   [5]   seq uint8
 *   [6-7] reserved uint16 = 0
 */
object RemoteProtocol {
    const val MAGIC0: Byte = 0xA5.toByte()
    const val MAGIC1: Byte = 0x5A.toByte()
    const val HEADER_LEN = 8
    const val DEFAULT_PORT = 8899
    const val HEARTBEAT_INTERVAL_MS = 2000L
    const val TIMEOUT_MS = 6000L

    const val TYPE_DETECT: Int = 0x01
    const val TYPE_HEARTBEAT: Int = 0x02
    const val TYPE_HANDSHAKE: Int = 0x03

    /** 单个检测目标载荷长度 */
    const val DET_ENTRY_LEN = 10
    /** 检测帧统计段长度 */
    const val DET_STAT_LEN = 12

    /** 一个检测目标 */
    data class RemoteDetection(
        val classId: Int,
        val confidence: Float,
        val cx: Float, val cy: Float,
        val w: Float, val h: Float
    )

    /** 解码后的检测帧 */
    data class DetectFrame(
        val streamW: Int,
        val streamH: Int,
        val tsMs: Long,
        val detections: List<RemoteDetection>
    )

    /** 构建心跳帧（8字节） */
    fun buildHeartbeat(seq: Int): ByteArray {
        val buf = ByteBuffer.allocate(HEADER_LEN).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(MAGIC0); buf.put(MAGIC1)
        buf.putShort(HEADER_LEN.toShort())
        buf.put(TYPE_HEARTBEAT.toByte())
        buf.put(seq.toByte())
        buf.putShort(0)
        return buf.array()
    }

    /** 构建检测帧 */
    fun buildDetect(
        seq: Int,
        streamW: Int, streamH: Int,
        tsMs: Long,
        dets: List<RemoteDetection>
    ): ByteArray {
        val n = dets.size
        val total = HEADER_LEN + DET_STAT_LEN + DET_ENTRY_LEN * n
        val buf = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(MAGIC0); buf.put(MAGIC1)
        buf.putShort(total.toShort())
        buf.put(TYPE_DETECT.toByte())
        buf.put(seq.toByte())
        buf.putShort(0)
        buf.putShort(streamW.toShort())
        buf.putShort(streamH.toShort())
        buf.putInt((tsMs and 0xFFFFFFFFL).toInt())
        buf.putShort(n.toShort())
        for (d in dets) {
            buf.put(d.classId.toByte())
            buf.put((d.confidence.coerceIn(0f, 1f) * 255f).toInt().toByte())
            buf.putShort(d.cx.toInt().toShort())
            buf.putShort(d.cy.toInt().toShort())
            buf.putShort(d.w.toInt().toShort())
            buf.putShort(d.h.toInt().toShort())
        }
        return buf.array()
    }

    /** 校验并解析帧头，返回 type / frameLen；失败返回 null */
    fun parseHeader(head: ByteArray): Pair<Int, Int>? {
        if (head.size < HEADER_LEN) return null
        if (head[0] != MAGIC0 || head[1] != MAGIC1) return null
        val buf = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN)
        val frameLen = buf.getShort(2).toInt() and 0xFFFF
        val type = head[4].toInt() and 0xFF
        return Pair(type, frameLen)
    }

    /** 解析检测帧载荷（不含帧头） */
    fun parseDetect(payload: ByteArray): DetectFrame? {
        if (payload.size < DET_STAT_LEN) return null
        val buf = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val streamW = buf.getShort(0).toInt() and 0xFFFF
        val streamH = buf.getShort(2).toInt() and 0xFFFF
        val tsMs = buf.getInt(4).toLong() and 0xFFFFFFFFL
        val n = buf.getShort(8).toInt() and 0xFFFF
        val dets = ArrayList<RemoteDetection>(n)
        var off = DET_STAT_LEN
        for (i in 0 until n) {
            if (off + DET_ENTRY_LEN > payload.size) break
            val classId = payload[off].toInt() and 0xFF
            val conf = (payload[off + 1].toInt() and 0xFF) / 255f
            val cx = buf.getShort(off + 2).toInt() and 0xFFFF
            val cy = buf.getShort(off + 4).toInt() and 0xFFFF
            val w = buf.getShort(off + 6).toInt() and 0xFFFF
            val h = buf.getShort(off + 8).toInt() and 0xFFFF
            dets.add(RemoteDetection(classId, conf, cx.toFloat(), cy.toFloat(), w.toFloat(), h.toFloat()))
            off += DET_ENTRY_LEN
        }
        return DetectFrame(streamW, streamH, tsMs, dets)
    }
}

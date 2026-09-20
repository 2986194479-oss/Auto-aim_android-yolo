package team.maodie.aimbot.remote

import android.graphics.RectF
import android.util.Log
import team.maodie.aimbot.model.DetectionInfo
import java.util.concurrent.atomic.AtomicReference

/**
 * 远程推理源：从副机 TCP 收检测框，转成 DetectionInfo 列表供主机使用。
 *
 * 桥接设计：
 *  - RemoteDetectClient 是回调式（网络线程 push）
 *  - InferenceManager 的循环是拉取式（每帧 pull）
 *  中间用 AtomicReference 做双缓冲，避免阻塞网络线程。
 */
class RemoteInferenceSource(
    private val host: String,
    private val port: Int
) : InferenceSource {

    companion object { private const val TAG = "RemoteInferenceSource" }

    override val isPush: Boolean get() = true

    /** 最近一帧（已转成 DetectionInfo），拉取后清空 */
    private val latest = AtomicReference<List<DetectionInfo>?>(null)

    @Volatile private var client: RemoteDetectClient? = null

    override fun start() {
        if (client != null) return
        val c = RemoteDetectClient(
            host = host,
            port = port,
            onDetect = { frame ->
                try {
                    val list = ArrayList<DetectionInfo>(frame.detections.size)
                    for (d in frame.detections) {
                        val rect = RectF(
                            d.cx - d.w / 2f,
                            d.cy - d.h / 2f,
                            d.cx + d.w / 2f,
                            d.cy + d.h / 2f
                        )
                        list.add(DetectionInfo(rect, d.classId, "cls${d.classId}"))
                    }
                    latest.set(list)
                } catch (e: Exception) {
                    Log.e(TAG, "onDetect 转换异常: ${e.message}")
                }
            },
            onDisconnect = { Log.w(TAG, "副机连接断开") }
        )
        client = c
        c.start()
        Log.d(TAG, "RemoteInferenceSource started -> $host:$port")
    }

    override fun stop() {
        client?.stop()
        client = null
        latest.set(null)
        Log.d(TAG, "RemoteInferenceSource stopped")
    }

    override fun pollDetections(captureW: Int, captureH: Int): List<DetectionInfo>? {
        return latest.getAndSet(null)
    }
}

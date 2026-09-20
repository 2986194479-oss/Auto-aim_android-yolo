package team.maodie.aimbot.remote

import team.maodie.aimbot.model.DetectionInfo

/**
 * 推理源抽象。
 * 原项目只有本地推理（JniCallBack）；双机改造后，主机可选：
 *  - LocalInferenceSource：本地推理（保持原逻辑，占位用，实际仍走 JniCallBack）
 *  - RemoteInferenceSource：从副机 TCP 收检测框
 * InferenceManager 的推理循环通过该接口拉取检测结果，实现“可插拔”。
 */
interface InferenceSource {
    /** true 表示数据由外部推送（远程），循环需从 pollDetections() 拉取 */
    val isPush: Boolean

    /** 启动（建立连接等）。本地源可空实现 */
    fun start()

    /** 停止并释放资源 */
    fun stop()

    /**
     * 拉取最近一帧检测结果（远程模式用）。
     * @param captureW 主机画面宽（用于坐标映射）
     * @param captureH 主机画面高
     * @return 检测列表；无数据返回 null
     */
    fun pollDetections(captureW: Int, captureH: Int): List<DetectionInfo>?
}

/** 本地推理源占位（实际推理仍在 InferenceManager 里走 JniCallBack） */
class LocalInferenceSource : InferenceSource {
    override val isPush: Boolean get() = false
    override fun start() {}
    override fun stop() {}
    override fun pollDetections(captureW: Int, captureH: Int): List<DetectionInfo>? = null
}

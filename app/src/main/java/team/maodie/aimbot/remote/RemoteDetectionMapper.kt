package team.maodie.aimbot.remote

import android.graphics.RectF

/**
 * 副机画面坐标 → 主机屏幕坐标映射。
 *
 * 副机回传的检测框坐标基于副机采集画面（streamW x streamH）。
 * 主机收到后需要等比映射到主机自己的 captureW x captureH，
 * 才能送进 AimController。保持长宽比、居中，避免拉伸导致偏移。
 */
object RemoteDetectionMapper {

    /**
     * 把副机检测框映射到主机画面。
     * @param srcW 副机画面宽
     * @param srcH 副机画面高
     * @param dstW 主机采集画面宽
     * @param dstH 主机采集画面高
     * @param det  副机检测框（cx/cy/w/h，单位为副机像素）
     * @return 映射后的主机屏幕 RectF（像素）
     */
    fun mapToHost(
        srcW: Int, srcH: Int,
        dstW: Int, dstH: Int,
        det: RemoteProtocol.RemoteDetection
    ): RectF {
        if (srcW <= 0 || srcH <= 0 || dstW <= 0 || dstH <= 0) return RectF()
        // 等比缩放因子（取较小者，保证完整显示 + 留黑边）
        val scale = minOf(dstW.toFloat() / srcW, dstH.toFloat() / srcH)
        // 居中偏移
        val offX = (dstW - srcW * scale) / 2f
        val offY = (dstH - srcH * scale) / 2f
        // 副机坐标系下的四角
        val left = (det.cx - det.w / 2f) * scale + offX
        val top = (det.cy - det.h / 2f) * scale + offY
        val right = (det.cx + det.w / 2f) * scale + offX
        val bottom = (det.cy + det.h / 2f) * scale + offY
        return RectF(left, top, right, bottom)
    }
}

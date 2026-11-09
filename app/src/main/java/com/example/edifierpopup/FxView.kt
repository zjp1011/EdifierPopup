package com.example.edifierpopup

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 纯 Canvas 绘制的动画弹窗基类：用一个 0 → totalSec 的时间值 t 驱动所有动画，
 * 子类只需在 onDraw 里根据 t 画画面。
 */
abstract class FxView(
    context: Context,
    protected val deviceName: String,
    protected val battery: Int
) : View(context) {

    protected val dp: Float = resources.displayMetrics.density
    protected var t: Float = 0f

    open val totalSec: Float = 7.5f
    /** 窗口高度（dp），0 表示全屏 */
    open val windowHeightDp: Int = 0
    open val gravityFlag: Int = Gravity.CENTER
    open val marginBottomDp: Int = 0
    /** 是否拦截触摸（true 时点击可关闭；false 时触摸穿透，不挡操作） */
    open val touchable: Boolean = false

    var onFinished: (() -> Unit)? = null
    private var animator: ValueAnimator? = null

    fun start() {
        val va = ValueAnimator.ofFloat(0f, 1f)
        va.duration = (totalSec * 1000f).toLong()
        va.interpolator = LinearInterpolator()
        va.addUpdateListener {
            t = it.animatedFraction * totalSec
            invalidate()
        }
        va.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: Animator) { cancelled = true }
            override fun onAnimationEnd(animation: Animator) {
                if (!cancelled) onFinished?.invoke()
            }
        })
        animator = va
        va.start()
    }

    fun stop() {
        animator?.cancel()
        animator = null
    }

    // ---------- 动画工具 ----------

    /** 从 start 秒开始、持续 dur 秒的进度 0..1 */
    protected fun seg(start: Float, dur: Float): Float = ((t - start) / dur).coerceIn(0f, 1f)

    protected fun easeOut(x: Float): Float = 1f - (1f - x).pow(3)

    protected fun easeInOut(x: Float): Float =
        if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f

    /** 带回弹的缓动，值会短暂超过 1 */
    protected fun overshoot(x: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        return 1f + c3 * (x - 1f).pow(3) + c1 * (x - 1f).pow(2)
    }

    protected fun lerp(a: Float, b: Float, k: Float): Float = a + (b - a) * k

    /** 结尾 0.5 秒整体淡出：1 → 0 */
    protected fun fadeOut(): Float = 1f - seg(totalSec - 0.5f, 0.5f)

    protected fun argb(color: Int, a: Float): Int {
        val alpha = (255f * a.coerceIn(0f, 1f)).toInt()
        return (color and 0x00FFFFFF) or (alpha shl 24)
    }

    /** 电量数值（带滚动动画），读不到返回 -1 */
    protected fun batteryValue(): Int =
        if (battery in 0..100) (battery * easeOut(seg(1.0f, 1.0f))).roundToInt() else -1

    protected fun batteryText(): String {
        val v = batteryValue()
        return if (v >= 0) "$v%" else "--"
    }

    // ---------- 绘制工具 ----------

    private val budPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** 画一只耳机，size 为高度（像素），rot 为旋转角度，mirror 为左右镜像 */
    protected fun drawBud(
        c: Canvas, cx: Float, cy: Float, size: Float,
        rot: Float, mirror: Boolean, alpha: Float
    ) {
        val a = (255f * alpha.coerceIn(0f, 1f)).toInt()
        if (a <= 0) return
        c.save()
        c.translate(cx, cy)
        c.rotate(rot)
        val s = size / 78f
        c.scale(if (mirror) -s else s, s)
        c.translate(-22f, -39f)

        budPaint.color = 0xFFE3E5EA.toInt()
        budPaint.alpha = a
        c.drawRoundRect(RectF(17f, 38f, 27f, 76f), 5f, 5f, budPaint)

        budPaint.color = 0xFFF4F5F7.toInt()
        budPaint.alpha = a
        c.drawOval(RectF(4f, 4f, 40f, 44f), budPaint)

        budPaint.color = 0xFFC9CCD4.toInt()
        budPaint.alpha = a
        c.drawCircle(22f, 24f, 6f, budPaint)
        c.restore()
    }

    /** 以 cy 为垂直中线画文字 */
    protected fun drawTextCenterY(c: Canvas, s: String, x: Float, cy: Float, p: Paint) {
        val fm = p.fontMetrics
        c.drawText(s, x, cy - (fm.ascent + fm.descent) / 2f, p)
    }

    /** 文字过长时截断并加省略号 */
    protected fun fitText(text: String, p: Paint, maxW: Float): String {
        if (maxW <= 0f || p.measureText(text) <= maxW) return text
        val n = p.breakText(text, true, maxW - p.measureText("…"), null)
        return text.substring(0, n.coerceAtLeast(0)) + "…"
    }
}

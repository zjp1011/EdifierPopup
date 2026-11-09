package com.example.edifierpopup

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.view.Gravity
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// ============================================================
// 样式 1：灵动岛 —— 屏幕顶部的黑色胶囊弹性展开
// ============================================================
class IslandFx(context: Context, name: String, battery: Int) : FxView(context, name, battery) {

    override val windowHeightDp = 150
    override val gravityFlag = Gravity.TOP
    override val totalSec = 6.5f

    private val green = 0xFF3DDC97.toInt()
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 16 * dp
        typeface = Typeface.DEFAULT_BOLD
    }
    private val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12 * dp }
    private val pct = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10 * dp
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val grow = overshoot(seg(0.15f, 0.7f))
        val shrink = easeInOut(seg(totalSec - 0.7f, 0.5f))
        val k = (grow * (1f - shrink)).coerceAtLeast(0f)
        if (k <= 0.001f) return

        val full = min(w - 32 * dp, 340 * dp)
        val pw = lerp(34 * dp, full, k)
        val ph = lerp(34 * dp, 78 * dp, k)
        val top = 28 * dp
        val cx = w / 2f
        val rect = RectF(cx - pw / 2f, top, cx + pw / 2f, top + ph)
        val rad = ph / 2f

        // 发光边缘
        val pulse = 0.6f + 0.4f * sin(t * 5f)
        for (i in 1..4) {
            val g = i * 1.5f * dp
            glow.color = argb(green, 0.22f * pulse / i * min(1f, k * 2f))
            glow.strokeWidth = 3f * dp
            c.drawRoundRect(
                RectF(rect.left - g, rect.top - g, rect.right + g, rect.bottom + g),
                rad + g, rad + g, glow
            )
        }
        c.drawRoundRect(rect, rad, rad, bg)

        // 内容
        val ca = seg(0.75f, 0.4f) * (1f - seg(totalSec - 0.9f, 0.3f))
        if (ca <= 0.01f) return
        val cy = rect.centerY()

        val slide = (1f - easeOut(seg(0.8f, 0.6f))) * 14 * dp
        val bx = rect.left + 34 * dp
        drawBud(c, bx - 9 * dp - slide, cy, 40 * dp, -12f, false, ca)
        drawBud(c, bx + 9 * dp + slide, cy, 40 * dp, 12f, true, ca)

        val tx = rect.left + 70 * dp
        val maxW = rect.right - 76 * dp - tx
        title.color = argb(Color.WHITE, ca)
        drawTextCenterY(c, fitText(deviceName, title, maxW), tx, cy - 10 * dp, title)
        sub.color = argb(green, ca)
        drawTextCenterY(c, "已连接", tx, cy + 12 * dp, sub)

        // 电量环
        val rr = 17 * dp
        val rx = rect.right - 34 * dp
        ring.strokeWidth = 4 * dp
        ring.color = argb(0xFF33363F.toInt(), ca)
        c.drawCircle(rx, cy, rr, ring)
        val bv = batteryValue()
        if (bv >= 0) {
            ring.color = argb(green, ca)
            c.drawArc(RectF(rx - rr, cy - rr, rx + rr, cy + rr), -90f, 360f * bv / 100f, false, ring)
        }
        pct.color = argb(Color.WHITE, ca)
        drawTextCenterY(c, if (bv >= 0) "$bv" else "--", rx, cy, pct)
    }
}

// ============================================================
// 样式 2：霓虹光环 —— 全屏暗场 + 旋转渐变光环 + 涟漪
// ============================================================
class NeonFx(context: Context, name: String, battery: Int) : FxView(context, name, battery) {

    override val totalSec = 5.5f

    private val colors = intArrayOf(
        0xFF00E5FF.toInt(), 0xFF7C4DFF.toInt(), 0xFFFF4081.toInt(), 0xFF00E5FF.toInt()
    )
    private var sweep: SweepGradient? = null
    private val rotM = Matrix()

    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val ripple = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 22 * dp
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14 * dp
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.3f
    }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 34 * dp
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        sweep = SweepGradient(w / 2f, h * 0.42f, colors, null)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val a = seg(0f, 0.5f) * fadeOut()
        if (a <= 0f) return
        c.drawColor(argb(0xFF05060A.toInt(), 0.82f * a))

        val cx = w / 2f
        val cy = h * 0.42f

        // 涟漪
        for (i in 0..2) {
            val ph = (t * 0.55f + i / 3f) % 1f
            ripple.color = argb(colors[i], 0.5f * (1f - ph) * a)
            ripple.strokeWidth = 2 * dp
            c.drawCircle(cx, cy, 80 * dp + ph * 230 * dp, ripple)
        }

        // 旋转渐变光环（叠几层做发光）
        val sw = sweep
        if (sw != null) {
            rotM.setRotate(t * 140f, cx, cy)
            sw.setLocalMatrix(rotM)
            ring.shader = sw
            val pop = overshoot(seg(0.1f, 0.7f))
            val rad = 80 * dp * pop * (1f + 0.025f * sin(t * 4f))
            for (j in 0..2) {
                ring.strokeWidth = (22f - 7f * j) * dp
                ring.alpha = (255f * a * (0.10f + 0.10f * j)).toInt()
                c.drawCircle(cx, cy, rad, ring)
            }
            ring.strokeWidth = 5 * dp
            ring.alpha = (255f * a).toInt()
            c.drawCircle(cx, cy, rad, ring)
        }

        // 耳机从两侧飞入
        val bp = overshoot(seg(0.3f, 0.8f))
        val off = (1f - bp) * 150 * dp
        val ba = seg(0.3f, 0.4f) * a
        drawBud(c, cx - 17 * dp - off, cy, 70 * dp, -14f, false, ba)
        drawBud(c, cx + 17 * dp + off, cy, 70 * dp, 14f, true, ba)

        // 文字
        val ta = seg(0.9f, 0.5f) * fadeOut()
        title.color = argb(Color.WHITE, ta)
        c.drawText(fitText(deviceName, title, w - 48 * dp), cx, cy + 150 * dp, title)
        sub.color = argb(colors[0], ta)
        c.drawText("已 连 接", cx, cy + 182 * dp, sub)

        big.color = argb(Color.WHITE, ta)
        c.drawText(batteryText(), cx, cy + 238 * dp, big)

        // 电量条
        val bv = batteryValue()
        val bw = 160 * dp
        val bt = cy + 256 * dp
        bar.color = argb(0xFF33363F.toInt(), ta)
        c.drawRoundRect(RectF(cx - bw / 2f, bt, cx + bw / 2f, bt + 5 * dp), 3 * dp, 3 * dp, bar)
        if (bv > 0) {
            bar.color = argb(colors[0], ta)
            c.drawRoundRect(
                RectF(cx - bw / 2f, bt, cx - bw / 2f + bw * bv / 100f, bt + 5 * dp),
                3 * dp, 3 * dp, bar
            )
        }
    }
}

// ============================================================
// 样式 3：声波律动 —— 渐变卡片 + 跳动的均衡器声波
// ============================================================
class WaveFx(context: Context, name: String, battery: Int) : FxView(context, name, battery) {

    override val windowHeightDp = 210
    override val gravityFlag = Gravity.BOTTOM
    override val marginBottomDp = 16
    override val touchable = true
    override val totalSec = 7f

    private var grad: LinearGradient? = null
    private val card = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG)
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 20 * dp
        typeface = Typeface.DEFAULT_BOLD
    }
    private val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 14 * dp }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        grad = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(0xFF6C5CE7.toInt(), 0xFFA855F7.toInt(), 0xFFFF6B9D.toInt()),
            null, Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val inP = easeOut(seg(0f, 0.6f))
        val outP = easeInOut(seg(totalSec - 0.5f, 0.5f))
        val dy = (1f - inP) * h * 1.2f + outP * h * 1.2f

        c.save()
        c.translate(0f, dy)

        card.shader = grad
        c.drawRoundRect(RectF(16 * dp, 0f, w - 16 * dp, h), 28 * dp, 28 * dp, card)

        val left = 40 * dp
        val ta = seg(0.4f, 0.5f)

        title.color = argb(Color.WHITE, ta)
        drawTextCenterY(c, fitText(deviceName, title, w - 230 * dp), left, 46 * dp, title)
        sub.color = argb(Color.WHITE, 0.85f * ta)
        val bv = batteryValue()
        drawTextCenterY(c, if (bv >= 0) "已连接  ·  $bv%" else "已连接", left, 74 * dp, sub)

        // 右上角耳机
        val bp = overshoot(seg(0.2f, 0.8f))
        val off = (1f - bp) * 90 * dp
        drawBud(c, w - 100 * dp - 16 * dp - off, 56 * dp, 62 * dp, -12f, false, ta)
        drawBud(c, w - 100 * dp + 16 * dp + off, 56 * dp, 62 * dp, 12f, true, ta)

        // 声波
        val env = seg(0.5f, 0.6f) * (1f - seg(totalSec - 1.2f, 0.7f))
        val n = 26
        val areaL = 40 * dp
        val areaW = w - 80 * dp
        val barW = 5 * dp
        val gap = (areaW - n * barW) / (n - 1)
        val cy = h - 46 * dp
        bar.color = argb(Color.WHITE, 0.85f)
        for (i in 0 until n) {
            val v = abs(sin(t * 3.4f + i * 0.55f)) * (0.6f + 0.4f * sin(t * 1.4f + i * 1.3f))
            val hh = (3f + 30f * v * env) * dp
            val x = areaL + i * (barW + gap)
            c.drawRoundRect(RectF(x, cy - hh, x + barW, cy + hh), barW / 2f, barW / 2f, bar)
        }
        c.restore()
    }
}

// ============================================================
// 样式 4：星空爆发 —— 全屏暗场，彩色粒子从中心炸开
// ============================================================
class StarsFx(context: Context, name: String, battery: Int) : FxView(context, name, battery) {

    override val totalSec = 6f

    private class P(
        val ang: Float, val speed: Float, val size: Float,
        val color: Int, val delay: Float, val life: Float, val ph: Float
    )

    private class S(val x: Float, val y: Float, val r: Float, val ph: Float)

    private val rnd = Random(7)
    private val palette = intArrayOf(
        0xFFFFD166.toInt(), 0xFFEF476F.toInt(), 0xFF06D6A0.toInt(),
        0xFF4CC9F0.toInt(), 0xFFFFFFFF.toInt(), 0xFFC77DFF.toInt()
    )
    private val ps = List(110) {
        P(
            rnd.nextFloat() * 6.2832f,
            90f + rnd.nextFloat() * 330f,
            1.5f + rnd.nextFloat() * 3f,
            palette[rnd.nextInt(palette.size)],
            rnd.nextFloat() * 0.25f,
            2.2f + rnd.nextFloat() * 2.2f,
            rnd.nextFloat() * 6.28f
        )
    }
    private val stars = List(70) {
        S(rnd.nextFloat(), rnd.nextFloat(), 0.8f + rnd.nextFloat() * 1.6f, rnd.nextFloat() * 6.28f)
    }

    private val gold = 0xFFFFD166.toInt()
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24 * dp
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14 * dp
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.3f
    }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 30 * dp
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val a = seg(0f, 0.4f) * fadeOut()
        if (a <= 0f) return
        c.drawColor(argb(0xFF07070F.toInt(), 0.82f * a))

        val cx = w / 2f
        val cy = h * 0.42f

        // 背景星点闪烁
        for (s in stars) {
            val tw = 0.25f + 0.35f * (0.5f + 0.5f * sin(t * 2.5f + s.ph))
            dot.color = argb(Color.WHITE, a * tw)
            c.drawCircle(s.x * w, s.y * h, s.r * dp, dot)
        }

        // 冲击波
        val sp = seg(0.5f, 1.1f)
        if (sp > 0.001f && sp < 0.999f) {
            ring.color = argb(gold, (1f - sp) * 0.8f * a)
            ring.strokeWidth = 3 * dp
            c.drawCircle(cx, cy, easeOut(sp) * w * 0.75f, ring)
        }

        // 中心光晕
        val gp = seg(0.4f, 0.8f)
        for (i in 3 downTo 1) {
            dot.color = argb(gold, 0.10f * gp * a)
            c.drawCircle(cx, cy, (48 + i * 14) * dp * (1f + 0.04f * sin(t * 3f)), dot)
        }

        // 粒子
        for (p in ps) {
            val tau = t - 0.5f - p.delay
            if (tau <= 0f) continue
            val life = tau / p.life
            if (life >= 1f) continue
            val d = p.speed * dp * (1f - exp(-2.2f * tau)) / 2.2f
            val x = cx + cos(p.ang) * d
            val y = cy + sin(p.ang) * d + 18 * dp * tau * tau
            val al = (1f - life) * (0.65f + 0.35f * sin(tau * 12f + p.ph)) * a
            dot.color = argb(p.color, al * 0.25f)
            c.drawCircle(x, y, p.size * dp * 2.4f, dot)
            dot.color = argb(p.color, al)
            c.drawCircle(x, y, p.size * dp, dot)
        }

        // 耳机
        val pop = overshoot(seg(0.45f, 0.6f))
        val ba = seg(0.45f, 0.3f) * a
        val size = 72 * dp * pop.coerceAtLeast(0f)
        drawBud(c, cx - 16 * dp, cy, size, -12f, false, ba)
        drawBud(c, cx + 16 * dp, cy, size, 12f, true, ba)

        // 文字
        val ta = seg(1.0f, 0.5f) * fadeOut()
        title.color = argb(Color.WHITE, ta)
        c.drawText(fitText(deviceName, title, w - 48 * dp), cx, cy + 110 * dp, title)
        sub.color = argb(gold, ta)
        c.drawText("已 连 接", cx, cy + 140 * dp, sub)
        big.color = argb(Color.WHITE, ta)
        c.drawText(batteryText(), cx, cy + 190 * dp, big)
    }
}

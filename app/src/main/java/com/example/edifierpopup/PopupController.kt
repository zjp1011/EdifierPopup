package com.example.edifierpopup

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ProgressBar
import android.widget.TextView

object PopupController {

    const val PREFS = "edifier_prefs"
    private const val KEY_STYLE = "style"
    private const val KEY_KEYWORDS = "keywords"
    const val DEFAULT_KEYWORDS = "EDIFIER,漫步者"

    val STYLE_NAMES = listOf("经典卡片", "灵动岛", "霓虹光环", "声波律动", "星空爆发")

    private val handler = Handler(Looper.getMainLooper())
    private var currentView: View? = null
    private var currentFx: FxView? = null
    private val classicAnimators = mutableListOf<Animator>()

    // ---------- 设置读写 ----------

    fun getStyle(ctx: Context): Int =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_STYLE, 0)

    fun setStyle(ctx: Context, style: Int) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_STYLE, style).apply()
    }

    fun getKeywords(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_KEYWORDS, DEFAULT_KEYWORDS)
            ?: DEFAULT_KEYWORDS

    fun setKeywords(ctx: Context, s: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_KEYWORDS, s).apply()
    }

    /** 设备名是否命中关键词；关键词留空表示所有蓝牙设备都弹 */
    fun matches(ctx: Context, deviceName: String): Boolean {
        val words = getKeywords(ctx)
            .split(',', '，', ' ')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        return words.any { deviceName.contains(it, ignoreCase = true) }
    }

    /** 读取总电量：系统隐藏接口，读不到时返回 -1 */
    fun readBattery(device: BluetoothDevice): Int = try {
        device.javaClass.getMethod("getBatteryLevel").invoke(device) as Int
    } catch (_: Throwable) {
        -1
    }

    // ---------- 弹窗 ----------

    fun show(context: Context, name: String, battery: Int, style: Int = -1) {
        val ctx = context.applicationContext
        if (!Settings.canDrawOverlays(ctx)) return
        val s = if (style >= 0) style else getStyle(ctx)

        dismiss(immediately = true)
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val fx: FxView? = when (s) {
            1 -> IslandFx(ctx, name, battery)
            2 -> NeonFx(ctx, name, battery)
            3 -> WaveFx(ctx, name, battery)
            4 -> StarsFx(ctx, name, battery)
            else -> null
        }
        if (fx == null) showClassic(ctx, wm, name, battery) else showFx(ctx, wm, fx)
    }

    private fun showFx(ctx: Context, wm: WindowManager, fx: FxView) {
        val dp = ctx.resources.displayMetrics.density
        val fullScreen = fx.windowHeightDp == 0
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!fx.touchable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (fullScreen) flags = flags or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            if (fullScreen) WindowManager.LayoutParams.MATCH_PARENT else (fx.windowHeightDp * dp).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = fx.gravityFlag
        lp.y = (fx.marginBottomDp * dp).toInt()

        if (fx.touchable) fx.setOnClickListener { dismiss(immediately = true) }
        fx.onFinished = { dismiss(immediately = true) }

        try {
            wm.addView(fx, lp)
        } catch (_: Exception) {
            return
        }
        currentView = fx
        currentFx = fx
        fx.start()
    }

    private fun showClassic(ctx: Context, wm: WindowManager, name: String, battery: Int) {
        val view = LayoutInflater.from(ctx).inflate(R.layout.popup_edifier, null)
        val dp = ctx.resources.displayMetrics.density

        view.findViewById<TextView>(R.id.deviceName).text = name
        val batteryText = view.findViewById<TextView>(R.id.batteryText)
        val batteryBar = view.findViewById<ProgressBar>(R.id.batteryBar)
        val status = view.findViewById<TextView>(R.id.status)
        val card = view.findViewById<View>(R.id.card)
        val budL = view.findViewById<View>(R.id.budLeft)
        val budR = view.findViewById<View>(R.id.budRight)
        val timer = view.findViewById<View>(R.id.timerBar)
        view.findViewById<View>(R.id.closeBtn).setOnClickListener { dismiss() }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.BOTTOM
        lp.y = (16 * dp).toInt()

        try {
            wm.addView(view, lp)
        } catch (_: Exception) {
            return
        }
        currentView = view

        card.translationY = 500 * dp
        card.alpha = 0f
        budL.translationX = -160 * dp; budL.rotation = -30f
        budR.translationX = 160 * dp; budR.rotation = 30f
        status.alpha = 0f
        batteryBar.progress = 0
        timer.pivotX = 0f

        classicAnimators += ObjectAnimator.ofFloat(card, View.TRANSLATION_Y, 0f).apply {
            duration = 600; interpolator = OvershootInterpolator(1.1f); start()
        }
        classicAnimators += ObjectAnimator.ofFloat(card, View.ALPHA, 1f).apply { duration = 300; start() }

        classicAnimators += ObjectAnimator.ofFloat(budL, View.TRANSLATION_X, -14 * dp).apply {
            duration = 800; startDelay = 250; interpolator = OvershootInterpolator(1.3f); start()
        }
        classicAnimators += ObjectAnimator.ofFloat(budL, View.ROTATION, -12f).apply {
            duration = 800; startDelay = 250; start()
        }
        classicAnimators += ObjectAnimator.ofFloat(budR, View.TRANSLATION_X, 14 * dp).apply {
            duration = 800; startDelay = 250; interpolator = OvershootInterpolator(1.3f); start()
        }
        classicAnimators += ObjectAnimator.ofFloat(budR, View.ROTATION, 12f).apply {
            duration = 800; startDelay = 250; start()
        }

        val ringIds = listOf(R.id.ring1, R.id.ring2, R.id.ring3)
        for (i in ringIds.indices) {
            val ring = view.findViewById<View>(ringIds[i])
            val va = ValueAnimator.ofFloat(0f, 1f)
            va.duration = 2000
            va.startDelay = i * 600L
            va.repeatCount = ValueAnimator.INFINITE
            va.interpolator = DecelerateInterpolator()
            va.addUpdateListener {
                val p = it.animatedValue as Float
                val sc = 0.6f + 1.6f * p
                ring.scaleX = sc; ring.scaleY = sc; ring.alpha = 0.8f * (1f - p)
            }
            va.start()
            classicAnimators += va
        }

        classicAnimators += ObjectAnimator.ofFloat(status, View.ALPHA, 1f).apply {
            duration = 400; startDelay = 900; start()
        }
        if (battery in 0..100) {
            val va = ValueAnimator.ofInt(0, battery)
            va.duration = 1000
            va.startDelay = 1000
            va.addUpdateListener {
                val v = it.animatedValue as Int
                batteryText.text = "$v%"
                batteryBar.progress = v
            }
            va.start()
            classicAnimators += va
        } else {
            batteryText.text = "--"
        }

        classicAnimators += ObjectAnimator.ofFloat(timer, View.SCALE_X, 1f, 0f).apply {
            duration = 6000; startDelay = 1500; interpolator = LinearInterpolator(); start()
        }
        handler.postDelayed({ dismiss() }, 7500)
    }

    fun dismiss(immediately: Boolean = false) {
        handler.removeCallbacksAndMessages(null)
        currentFx?.stop()
        currentFx = null
        for (a in classicAnimators) a.cancel()
        classicAnimators.clear()

        val view = currentView ?: return
        currentView = null
        val wm = view.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val card: View? = view.findViewById(R.id.card)
        if (immediately || card == null) {
            try { wm.removeView(view) } catch (_: Exception) {}
            return
        }
        card.animate()
            .translationY(500 * view.resources.displayMetrics.density)
            .alpha(0f)
            .setDuration(300)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    try { wm.removeView(view) } catch (_: Exception) {}
                }
            })
            .start()
    }
}

package com.example.edifierpopup

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat

class PopupService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var popupView: View? = null
    private val animators = mutableListOf<Animator>()

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return
            val device = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            } ?: return
            val name = try { device.name } catch (_: SecurityException) { null } ?: return
            if (name.contains("EDIFIER", ignoreCase = true) || name.contains("漫步者")) {
                showPopup(name, readBattery(device))
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        ContextCompat.registerReceiver(
            this, receiver,
            IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 主界面的「测试弹窗」按钮
        if (intent?.getBooleanExtra(EXTRA_TEST, false) == true) {
            showPopup("EDIFIER W820NB Plus", 86)
        }
        return START_STICKY
    }

    /** 读取总电量：系统隐藏接口，部分耳机/系统读不到时返回 -1 */
    private fun readBattery(device: BluetoothDevice): Int = try {
        device.javaClass.getMethod("getBatteryLevel").invoke(device) as Int
    } catch (_: Throwable) { -1 }

    private fun startAsForeground() {
        val channelId = "edifier_listener"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(channelId, "耳机连接监听", NotificationManager.IMPORTANCE_MIN)
        )
        val n = Notification.Builder(this, channelId)
            .setContentTitle("正在等待漫步者耳机连接")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else startForeground(1, n)
    }

    private fun showPopup(name: String, battery: Int) {
        handler.post {
            if (!Settings.canDrawOverlays(this)) return@post
            dismissPopup(immediately = true)

            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val view = LayoutInflater.from(this).inflate(R.layout.popup_edifier, null)
            popupView = view

            view.findViewById<TextView>(R.id.deviceName).text = name
            val batteryText = view.findViewById<TextView>(R.id.batteryText)
            val batteryBar = view.findViewById<ProgressBar>(R.id.batteryBar)
            val status = view.findViewById<TextView>(R.id.status)
            val card = view.findViewById<View>(R.id.card)
            val budL = view.findViewById<View>(R.id.budLeft)
            val budR = view.findViewById<View>(R.id.budRight)
            val timer = view.findViewById<View>(R.id.timerBar)
            val dp = resources.displayMetrics.density

            view.findViewById<View>(R.id.closeBtn).setOnClickListener { dismissPopup() }

            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM
                y = (16 * dp).toInt()
            }
            wm.addView(view, lp)

            // 初始状态
            card.translationY = 500 * dp
            card.alpha = 0f
            budL.translationX = -160 * dp; budL.rotation = -30f
            budR.translationX = 160 * dp; budR.rotation = 30f
            status.alpha = 0f
            batteryBar.progress = 0
            timer.pivotX = 0f

            // 1) 卡片弹起
            track(ObjectAnimator.ofFloat(card, View.TRANSLATION_Y, 0f).apply {
                duration = 600; interpolator = OvershootInterpolator(1.1f); start()
            })
            track(ObjectAnimator.ofFloat(card, View.ALPHA, 1f).apply { duration = 300; start() })

            // 2) 左右耳机飞入
            track(ObjectAnimator.ofFloat(budL, View.TRANSLATION_X, -14 * dp).apply {
                duration = 800; startDelay = 250; interpolator = OvershootInterpolator(1.3f); start()
            })
            track(ObjectAnimator.ofFloat(budL, View.ROTATION, -12f).apply {
                duration = 800; startDelay = 250; start()
            })
            track(ObjectAnimator.ofFloat(budR, View.TRANSLATION_X, 14 * dp).apply {
                duration = 800; startDelay = 250; interpolator = OvershootInterpolator(1.3f); start()
            })
            track(ObjectAnimator.ofFloat(budR, View.ROTATION, 12f).apply {
                duration = 800; startDelay = 250; start()
            })

            // 3) 三圈光环循环扩散
            listOf(R.id.ring1, R.id.ring2, R.id.ring3).forEachIndexed { i, id ->
                val ring = view.findViewById<View>(id)
                track(ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 2000; startDelay = i * 600L
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = DecelerateInterpolator()
                    addUpdateListener {
                        val p = it.animatedValue as Float
                        val s = 0.6f + 1.6f * p
                        ring.scaleX = s; ring.scaleY = s; ring.alpha = 0.8f * (1f - p)
                    }
                    start()
                })
            }

            // 4) “已连接”淡入 + 电量滚动
            track(ObjectAnimator.ofFloat(status, View.ALPHA, 1f).apply {
                duration = 400; startDelay = 900; start()
            })
            if (battery in 0..100) {
                track(ValueAnimator.ofInt(0, battery).apply {
                    duration = 1000; startDelay = 1000
                    addUpdateListener {
                        val v = it.animatedValue as Int
                        batteryText.text = "$v%"; batteryBar.progress = v
                    }
                    start()
                })
            } else {
                batteryText.text = "--"
            }

            // 5) 倒计时条 + 自动消失
            track(ObjectAnimator.ofFloat(timer, View.SCALE_X, 1f, 0f).apply {
                duration = 6000; startDelay = 1500; interpolator = android.view.animation.LinearInterpolator(); start()
            })
            handler.postDelayed({ dismissPopup() }, 7500)
        }
    }

    private fun track(a: Animator) { animators += a }

    private fun dismissPopup(immediately: Boolean = false) {
        handler.removeCallbacksAndMessages(null)
        val view = popupView ?: return
        popupView = null
        animators.forEach { it.cancel() }
        animators.clear()
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        fun remove() { try { wm.removeView(view) } catch (_: Exception) {} }
        if (immediately) { remove(); return }
        val card = view.findViewById<View>(R.id.card)
        card.animate().translationY(500 * resources.displayMetrics.density).alpha(0f)
            .setDuration(300).setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { remove() }
            }).start()
    }

    override fun onDestroy() {
        dismissPopup(immediately = true)
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object { const val EXTRA_TEST = "test" }
}

package com.example.edifierpopup

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

class BtReceiver : BroadcastReceiver() {

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val isAcl = action == BluetoothDevice.ACTION_ACL_CONNECTED
        val isProfile = action == A2DP_ACTION || action == HEADSET_ACTION
        if (!isAcl && !isProfile) return

        val shortAction = when {
            isAcl -> "ACL连接"
            action == A2DP_ACTION -> "A2DP状态"
            else -> "耳机协议状态"
        }

        // 配置文件广播里，只处理「已连接」状态
        if (isProfile) {
            val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
            if (state != BluetoothProfile.STATE_CONNECTED) return
        }

        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        if (device == null) {
            DiagLog.add(context, "$shortAction：没有设备信息")
            return
        }

        var name: String? = null
        try {
            name = device.name
        } catch (_: SecurityException) {
            // 没有蓝牙权限，读不到设备名
        }
        if (name == null) {
            DiagLog.add(context, "$shortAction：读不到设备名（检查蓝牙权限）")
            return
        }
        if (!PopupController.matches(context, name)) {
            DiagLog.add(context, "$shortAction：$name 与关键词不匹配，已忽略")
            return
        }

        // 同一台设备 15 秒内只弹一次（连接时会连续触发多次广播）
        val addr = device.address ?: name
        val now = SystemClock.elapsedRealtime()
        val prev = last[addr]
        if (prev != null && now - prev < 15000L) {
            DiagLog.add(context, "$shortAction：$name 重复通知，已忽略")
            return
        }
        last[addr] = now

        DiagLog.add(context, "$shortAction：$name，准备弹窗")

        // 延迟 1 秒再弹：此时耳机的电量信息通常已经上报
        val pending = goAsync()
        val app = context.applicationContext
        val finalName: String = name
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val ok = PopupController.show(app, finalName, PopupController.readBattery(device))
                DiagLog.add(app, if (ok) "弹窗已显示" else "弹窗失败：悬浮窗权限未开或被系统拦截")
            } finally {
                pending.finish()
            }
        }, 1000L)
    }

    companion object {
        private const val A2DP_ACTION = "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED"
        private const val HEADSET_ACTION = "android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED"
        private val last = HashMap<String, Long>()
    }
}

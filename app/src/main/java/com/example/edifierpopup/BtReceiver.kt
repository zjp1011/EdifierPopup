package com.example.edifierpopup

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
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
        if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return

        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        if (device == null) return

        var name: String? = null
        try {
            name = device.name
        } catch (_: SecurityException) {
            // 没有蓝牙权限，读不到设备名
        }
        if (name == null) return
        if (!PopupController.matches(context, name)) return

        // 同一台设备 15 秒内只弹一次（连接时会连续触发多次广播）
        val addr = device.address ?: name
        val now = SystemClock.elapsedRealtime()
        val prev = last[addr]
        if (prev != null && now - prev < 15000L) return
        last[addr] = now

        // 延迟 1 秒再弹：此时耳机的电量信息通常已经上报
        val pending = goAsync()
        val app = context.applicationContext
        val finalName: String = name
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                PopupController.show(app, finalName, PopupController.readBattery(device))
            } finally {
                pending.finish()
            }
        }, 1000L)
    }

    companion object {
        private val last = HashMap<String, Long>()
    }
}

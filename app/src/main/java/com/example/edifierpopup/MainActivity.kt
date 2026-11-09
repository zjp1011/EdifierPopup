package com.example.edifierpopup

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var info: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        info = findViewById(R.id.info)

        findViewById<Button>(R.id.btnPerm).setOnClickListener { requestRuntimePermissions() }
        findViewById<Button>(R.id.btnOverlay).setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }
        findViewById<Button>(R.id.btnAppInfo).setOnClickListener {
            // vivo：权限管理 → 后台弹出界面 / 自启动 / 电池 都从应用详情页进入
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        }
        findViewById<Button>(R.id.btnStart).setOnClickListener { startListening(test = false) }
        findViewById<Button>(R.id.btnTest).setOnClickListener { startListening(test = true) }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun startListening(test: Boolean) {
        if (!Settings.canDrawOverlays(this)) { refreshStatus(); return }
        val i = Intent(this, PopupService::class.java).putExtra(PopupService.EXTRA_TEST, test)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
    }

    private fun requestRuntimePermissions() {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) list += Manifest.permission.BLUETOOTH_CONNECT
        if (Build.VERSION.SDK_INT >= 33) list += Manifest.permission.POST_NOTIFICATIONS
        if (list.isNotEmpty()) requestPermissions(list.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(code: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(code, p, r)
        refreshStatus()
    }

    private fun granted(p: String) =
        checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun refreshStatus() {
        val bt = Build.VERSION.SDK_INT < 31 || granted(Manifest.permission.BLUETOOTH_CONNECT)
        val nt = Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)
        val ov = Settings.canDrawOverlays(this)
        fun m(ok: Boolean) = if (ok) "✅" else "❌"
        info.text = """
            ${m(bt)} 蓝牙连接权限
            ${m(nt)} 通知权限
            ${m(ov)} 悬浮窗权限

            还需在「应用详情 → 权限」里手动放行（vivo）：
            · 后台弹出界面 → 允许
            · 自启动 → 允许
            · 电池 → 后台高耗电 / 不限制
        """.trimIndent()
    }
}

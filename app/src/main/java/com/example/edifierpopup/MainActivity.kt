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
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var info: TextView
    private lateinit var keywords: EditText
    private val demoName = "EDIFIER W820NB Plus"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        info = findViewById(R.id.info)
        keywords = findViewById(R.id.keywords)
        keywords.setText(PopupController.getKeywords(this))

        // 弹窗样式选择：点一下就会立刻预览
        val group = findViewById<RadioGroup>(R.id.styleGroup)
        val current = PopupController.getStyle(this)
        PopupController.STYLE_NAMES.forEachIndexed { i, n ->
            val rb = RadioButton(this)
            rb.id = 1000 + i
            rb.text = n
            rb.textSize = 16f
            rb.setPadding(8, 16, 8, 16)
            group.addView(rb)
            if (i == current) rb.isChecked = true
        }
        group.setOnCheckedChangeListener { _, checkedId ->
            val idx = checkedId - 1000
            PopupController.setStyle(this, idx)
            PopupController.show(this, demoName, 86, idx)
        }

        findViewById<Button>(R.id.btnPerm).setOnClickListener { requestBluetoothPermission() }
        findViewById<Button>(R.id.btnOverlay).setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }
        findViewById<Button>(R.id.btnAppInfo).setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        }
        findViewById<Button>(R.id.btnTest).setOnClickListener {
            PopupController.show(this, demoName, 86)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        findViewById<TextView>(R.id.diag).text = DiagLog.read(this)
    }

    override fun onPause() {
        super.onPause()
        PopupController.setKeywords(this, keywords.text.toString())
    }

    private fun requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= 31) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1)
        }
    }

    override fun onRequestPermissionsResult(code: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(code, p, r)
        refreshStatus()
    }

    private fun refreshStatus() {
        val bt = Build.VERSION.SDK_INT < 31 ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        val ov = Settings.canDrawOverlays(this)
        fun m(ok: Boolean) = if (ok) "✅" else "❌"
        info.text = "${m(bt)} 蓝牙连接权限\n${m(ov)} 悬浮窗权限\n\n" +
            "还需在「应用详情」里手动放行（vivo）：\n" +
            "· 权限 → 后台弹出界面 → 允许\n" +
            "· 自启动 → 允许\n" +
            "· 电池 → 后台高耗电 / 不限制\n\n" +
            "全部设置好后，不需要保持 App 运行，耳机连上就会自动弹出。\n" +
            "注意：不要在「应用详情」里点「强行停止」，那样系统会暂停接收连接通知，需重新打开一次 App。"
    }
}

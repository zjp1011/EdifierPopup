package com.example.edifierpopup

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 记录最近的蓝牙连接事件，方便排查「为什么没弹」 */
object DiagLog {
    private const val KEY = "diag_log"
    private const val MAX = 12

    fun add(ctx: Context, msg: String) {
        val prefs = ctx.getSharedPreferences(PopupController.PREFS, Context.MODE_PRIVATE)
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val lines = (prefs.getString(KEY, "") ?: "")
            .split("\n")
            .filter { it.isNotEmpty() }
            .toMutableList()
        lines.add("$time  $msg")
        val kept = if (lines.size > MAX) lines.subList(lines.size - MAX, lines.size) else lines
        prefs.edit().putString(KEY, kept.joinToString("\n")).apply()
    }

    fun read(ctx: Context): String {
        val s = ctx.getSharedPreferences(PopupController.PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "") ?: ""
        return if (s.isEmpty()) "（还没有记录。连一次耳机，再回到这里看）" else s.split("\n").reversed().joinToString("\n")
    }
}

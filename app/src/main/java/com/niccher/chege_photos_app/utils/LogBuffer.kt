package com.niccher.chege_photos_app.utils

object LogBuffer {
    private val logs = mutableListOf<String>()
    private val maxLines = 500

    fun add(line: String) {
        synchronized(this) {
            logs.add(java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()) + " " + line)
            if (logs.size > maxLines) {
                logs.removeAt(0)
            }
        }
    }

    fun getLogs(): List<String> = synchronized(this) { logs.toList() }

    fun clear() { synchronized(this) { logs.clear() } }
}

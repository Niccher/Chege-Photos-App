package com.niccher.chege_photos_app.utils

import android.content.Context
import android.content.SharedPreferences

class SessionManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "auth_prefs"
        private const val KEY_TOKEN = "access_token"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_ACCOUNT_CREATED = "account_created"
        private const val KEY_LAST_LOGIN = "last_login"
        private const val KEY_LAST_UPLOAD = "last_upload"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_APP_THEME = "app_theme"
        private const val KEY_CONFIG_MAX_UPLOAD_MB = "cfg_max_upload_mb"
        private const val KEY_CONFIG_ALLOWED_EXTS = "cfg_allowed_exts"
        private const val KEY_CONFIG_APP_NAME = "cfg_app_name"
        private const val KEY_CONFIG_PHOTO_EDITING = "cfg_photo_editing"
    }

    fun saveTheme(theme: String) {
        prefs.edit().putString(KEY_APP_THEME, theme).apply()
    }

    fun getTheme(): String {
        return prefs.getString(KEY_APP_THEME, "DEFAULT") ?: "DEFAULT"
    }

    fun saveAuthToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun getAuthToken(): String? {
        return prefs.getString(KEY_TOKEN, null)
    }

    fun saveUserProfile(id: Int, email: String, username: String? = null, created_at: String? = null, last_upload: String? = null) {
        prefs.edit().putInt(KEY_USER_ID, id)
             .putString(KEY_USER_EMAIL, email)
             .putString(KEY_USERNAME, username)
             .putString(KEY_ACCOUNT_CREATED, created_at)
             .putString(KEY_LAST_UPLOAD, last_upload)
             .apply()
    }
    
    fun saveLastUploadFromServer(timestamp: String?) {
        prefs.edit().putString(KEY_LAST_UPLOAD, timestamp).apply()
    }
    
    fun updateLastLogin() {
        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        prefs.edit().putString(KEY_LAST_LOGIN, formatter.format(java.util.Date())).apply()
    }
    
    fun updateLastUpload() {
        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        prefs.edit().putString(KEY_LAST_UPLOAD, formatter.format(java.util.Date())).apply()
    }

    fun getUserDetails(): Map<String, String> {
        return mapOf(
            "email" to (prefs.getString(KEY_USER_EMAIL, "Unknown") ?: "Unknown"),
            "username" to (prefs.getString(KEY_USERNAME, "Not set") ?: "Not set"),
            "created" to (prefs.getString(KEY_ACCOUNT_CREATED, "Unknown") ?: "Unknown"),
            "last_login" to (prefs.getString(KEY_LAST_LOGIN, "Never") ?: "Never"),
            "last_upload" to (prefs.getString(KEY_LAST_UPLOAD, "Never") ?: "Never"),
            "biometric_enabled" to prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false).toString()
        )
    }

    fun isBiometricEnabled(): Boolean {
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    fun isBackupAutoEnabled(): Boolean {
        return prefs.getBoolean("backup_auto_enabled", true)
    }

    fun setBackupAutoEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("backup_auto_enabled", enabled).apply()
    }

    fun isBackupOnlyWifi(): Boolean {
        return prefs.getBoolean("backup_only_wifi", true)
    }

    fun setBackupOnlyWifi(onlyWifi: Boolean) {
        prefs.edit().putBoolean("backup_only_wifi", onlyWifi).apply()
    }

    fun isBackupOnlyCharging(): Boolean {
        return prefs.getBoolean("backup_only_charging", true)
    }

    fun setBackupOnlyCharging(onlyCharging: Boolean) {
        prefs.edit().putBoolean("backup_only_charging", onlyCharging).apply()
    }

    fun saveServerConfig(config: com.niccher.chege_photos_app.models.ServerConfigData) {
        prefs.edit()
            .putInt(KEY_CONFIG_MAX_UPLOAD_MB, config.max_upload_size_mb)
            .putString(KEY_CONFIG_ALLOWED_EXTS, config.allowed_extensions)
            .putString(KEY_CONFIG_APP_NAME, config.app_name ?: "Chege Photos")
            .putBoolean(KEY_CONFIG_PHOTO_EDITING, config.capabilities?.photo_editing ?: false)
            .apply()
    }

    fun getMaxUploadSizeMb(): Int {
        return prefs.getInt(KEY_CONFIG_MAX_UPLOAD_MB, 500)
    }

    fun getAllowedExtensions(): List<String> {
        val raw = prefs.getString(KEY_CONFIG_ALLOWED_EXTS, "jpg,jpeg,png,webp,heic,mp4,mov,webm,mkv") ?: ""
        return raw.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
    }

    fun isPhotoEditingSupported(): Boolean {
        return prefs.getBoolean(KEY_CONFIG_PHOTO_EDITING, false)
    }

    fun getServerAppName(): String {
        return prefs.getString(KEY_CONFIG_APP_NAME, "Chege Photos") ?: "Chege Photos"
    }

    fun getDeviceUuid(): String {
        var uuid = prefs.getString("device_uuid", null)
        if (uuid == null) {
            uuid = DeviceFingerprint.getCompositeDeviceKey(context)
            prefs.edit().putString("device_uuid", uuid).apply()
        }
        return uuid
    }

    fun clearSession() {
        val uuid = prefs.getString("device_uuid", null)
        prefs.edit().clear().apply()
        if (uuid != null) {
            prefs.edit().putString("device_uuid", uuid).apply()
        }
    }

    fun isLoggedIn(): Boolean {
        return getAuthToken() != null
    }
}

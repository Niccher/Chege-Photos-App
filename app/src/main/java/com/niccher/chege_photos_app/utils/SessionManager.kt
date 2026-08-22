package com.niccher.chege_photos_app.utils

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
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

    fun clearSession() {
        prefs.edit().clear().apply()
    }

    fun isLoggedIn(): Boolean {
        return getAuthToken() != null
    }
}

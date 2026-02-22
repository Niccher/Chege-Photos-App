package com.niccher.prjphotos.models

import kotlinx.serialization.Serializable

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class AuthResponse(
    val status: String? = null,
    val access_token: String? = null,
    val user: UserInfo? = null,
    val message: JsonElement? = null
) {
    val messageText: String?
        get() = try {
            message?.jsonPrimitive?.content ?: message?.toString()
        } catch (e: Exception) {
            message?.toString()
        }
}

@Serializable
data class UserInfo(
    val id: Int,
    val email: String,
    val username: String? = null
)

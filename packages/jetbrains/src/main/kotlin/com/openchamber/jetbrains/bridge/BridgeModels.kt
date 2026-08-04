package com.openchamber.jetbrains.bridge

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class BridgeRequest(
    val id: String,
    val type: String,
    val payload: JsonElement? = null
)

@Serializable
data class BridgeResponse(
    val id: String,
    val type: String,
    val success: Boolean,
    val data: JsonElement? = null,
    val error: String? = null
)

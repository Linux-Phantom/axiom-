package com.apple.axiom.model

import kotlinx.serialization.Serializable

@Serializable
data class AgentResponse(
    val agentName: String,
    val message: String,
    val actionPayload: String? = null,
)

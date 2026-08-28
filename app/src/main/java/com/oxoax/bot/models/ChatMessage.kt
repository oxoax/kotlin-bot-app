package com.oxoax.bot.models

data class ChatMessage(
    val time: String,
    val type: String,
    val username: String? = null,
    val userId: String? = null,
    val content: String? = null,
    val isAt: Boolean = false,
    val isSelf: Boolean = false,
    val messageScene: String? = null,
    val attachments: List<Any?>? = null,
    val messageId: String? = null,
    val msgIdx: String? = null,
    val buttonData: Map<String, Any?>? = null,
    val quote: Map<String, Any?>? = null,
    val rawData: Map<String, Any?>? = null,
    val rawPayload: Map<String, Any?>? = null,
    val sentAt: Long? = null,
    val role: String = "member",  // member, admin, owner
    val isBot: Boolean = false
)

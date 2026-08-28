package com.oxoax.bot.models

data class Group(
    val name: String,
    val group: String,
    val official: String = "",
    val groupOpenid: String = "",
    val groupNumber: String = "",
    val avatar: String = ""
) {
    val avatarUrl: String
        get() {
            if (avatar.isNotEmpty()) return avatar
            if (group.matches(Regex("^\\d+$"))) return "http://p.qlogo.cn/gh/$group/$group/100/"
            return ""
        }

    companion object {
        fun fromLocal(m: Map<String, String>) = Group(
            name = m["name"] ?: "",
            group = m["group"] ?: "",
            official = m["official"] ?: "",
            groupOpenid = m["group_openid"] ?: "",
            groupNumber = m["group_number"] ?: "",
            avatar = m["avatar"] ?: ""
        )
    }
}

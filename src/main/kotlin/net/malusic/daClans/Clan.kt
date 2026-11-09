package net.malusic.daclans

import java.util.UUID

class Clan(
    val id: UUID,
    var name: String,
    var leader: UUID,
    val createdAt: Long,
    var bank: Double = 0.0,
    var open: Boolean = false,
    var inviteLeaderOnly: Boolean = true,
    var maxMembers: Int = 10,
    val members: MutableMap<UUID, String> = mutableMapOf(),
    val allies: MutableSet<UUID> = mutableSetOf(),
    val enemies: MutableSet<UUID> = mutableSetOf(),
    val perms: MutableMap<String, String> = mutableMapOf(),
    var color: String = ""
) {
    fun coloredName(): String {
        if (color.isBlank()) return name
        val tag = color.removePrefix("<").removeSuffix(">")
        return "$color$name</$tag>"
    }
}
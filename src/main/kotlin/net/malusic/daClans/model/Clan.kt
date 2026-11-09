package net.malusic.daclans.model

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
    val perms: MutableMap<String, String> = mutableMapOf()
)

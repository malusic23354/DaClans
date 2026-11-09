package net.malusic.daclans

import java.util.UUID

class Ranks(
    private val levels: Map<String, Int>,
    private val defaults: Map<String, String>,
    private val displayNames: Map<String, String>
) {

    constructor(plugin: DaClans) : this(
        plugin.config.getConfigurationSection("ranks")?.getKeys(false)
            ?.associateWith { plugin.config.getInt("ranks.$it.level") } ?: emptyMap(),
        plugin.config.getConfigurationSection("default-permissions")?.getKeys(false)
            ?.associateWith { plugin.config.getString("default-permissions.$it", "leader")!! } ?: emptyMap(),
        plugin.config.getConfigurationSection("ranks")?.getKeys(false)
            ?.associateWith { plugin.config.getString("ranks.$it.displayname", it)!! } ?: emptyMap()
    )

    val order: List<String> = levels.entries.sortedBy { it.value }.map { it.key }

    val default: String get() = order.firstOrNull() ?: "member"

    fun nonLeader(): List<String> = order.filter { it != "leader" }

    fun level(rank: String): Int = levels[rank] ?: 0

    fun minRank(clan: Clan, action: String): String =
        clan.perms[action] ?: defaults[action] ?: "leader"

    fun canDo(clan: Clan, player: UUID, action: String): Boolean {
        if (clan.leader == player) return true
        val rank = clan.members[player] ?: return false
        val need = levels[minRank(clan, action)] ?: return false
        return level(rank) >= need
    }

    fun display(rank: String): String = displayNames[rank] ?: rank
}

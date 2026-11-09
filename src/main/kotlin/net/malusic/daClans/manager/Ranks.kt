package net.malusic.daclans

import java.util.UUID

class Ranks(private val plugin: DaClans) {

    private val levels: Map<String, Int>
    val order: List<String>

    init {
        val section = plugin.config.getConfigurationSection("ranks")!!
        levels = section.getKeys(false).associateWith { section.getInt("$it.level") }
        order = levels.entries.sortedBy { it.value }.map { it.key }
    }

    val default: String get() = order.first()

    fun nonLeader(): List<String> = order.filter { it != "leader" }

    fun level(rank: String): Int = levels[rank] ?: 0

    fun minRank(clan: Clan, action: String): String =
        clan.perms[action] ?: plugin.config.getString("default-permissions.$action", "leader")!!

    fun canDo(clan: Clan, player: UUID, action: String): Boolean {
        if (clan.leader == player) return true
        val rank = clan.members[player] ?: return false
        val need = levels[minRank(clan, action)] ?: return false
        return level(rank) >= need
    }

    fun display(rank: String): String = plugin.config.getString("ranks.$rank.displayname", rank)!!
}

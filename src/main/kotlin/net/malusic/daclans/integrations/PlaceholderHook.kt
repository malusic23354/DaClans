package net.malusic.daclans

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import net.malusic.daclans.util.Money
import net.malusic.daclans.util.TimeFormat
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer

class PlaceholderHook(private val plugin: DaClans) : PlaceholderExpansion() {

    override fun getIdentifier(): String = "daclans"

    override fun getAuthor(): String = "Malusic"

    override fun getVersion(): String = plugin.pluginMeta.version

    override fun persist(): Boolean = true

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        if (params.startsWith("top_")) return top(params)
        val p = player ?: return ""
        val clan = plugin.clans.ofPlayer(p.uniqueId) ?: return ""
        return when (params) {
            "name" -> clan.coloredName()
            "plain_name" -> clan.name
            "tag" -> clan.color
            "leader" -> Bukkit.getOfflinePlayer(clan.leader).name ?: ""
            "members" -> clan.members.size.toString()
            "max_members" -> clan.maxMembers.toString()
            "online_members" -> clan.members.keys.count { Bukkit.getPlayer(it) != null }.toString()
            "bank" -> Money.format(clan.bankCents)
            "bank_cents" -> clan.bankCents.toString()
            "rank" -> plugin.ranks.display(clan.members[p.uniqueId] ?: "")
            "allies" -> clan.allies.size.toString()
            "enemies" -> clan.enemies.size.toString()
            "description" -> clan.description
            "created" -> TimeFormat.date(clan.createdAt)
            "last_seen" -> TimeFormat.date(clan.lastSeen[p.uniqueId] ?: 0L)
            "last_seen_ago" -> TimeFormat.ago(clan.lastSeen[p.uniqueId] ?: 0L)
            "position" -> plugin.clans.top(plugin.clans.sort).indexOfFirst { it.id == clan.id }
                .takeIf { it >= 0 }?.plus(1)?.toString() ?: ""
            else -> null
        }
    }

    private fun top(params: String): String? {
        val parts = params.split("_")
        if (parts.size < 3) return null
        val n = parts[1].toIntOrNull() ?: return null
        var type = plugin.clans.sort
        var field = "value"
        val rest = parts.drop(2)
        if (rest.size == 1) {
            if (rest[0] in ClanManager.TYPES) {
                type = rest[0]
            } else {
                field = rest[0]
            }
        } else {
            if (rest[0] in ClanManager.TYPES) {
                type = rest[0]
                field = rest.drop(1).joinToString("_")
            } else {
                field = rest.joinToString("_")
            }
        }
        val clan = plugin.clans.top(type).getOrNull(n - 1) ?: return ""
        return when (field) {
            "name", "clan" -> clan.coloredName()
            "value" -> plugin.clans.topValue(clan, type)
            "members" -> clan.members.size.toString()
            "allies" -> clan.allies.size.toString()
            "enemies" -> clan.enemies.size.toString()
            "bank" -> Money.format(clan.bankCents)
            "leader" -> Bukkit.getOfflinePlayer(clan.leader).name ?: ""
            "description" -> clan.description
            else -> null
        }
    }
}

object PlaceholderBinding {

    private var expansion: PlaceholderHook? = null

    fun register(plugin: DaClans) {
        val hook = PlaceholderHook(plugin)
        hook.register()
        expansion = hook
    }

    fun unregister() {
        expansion?.unregister()
        expansion = null
    }
}

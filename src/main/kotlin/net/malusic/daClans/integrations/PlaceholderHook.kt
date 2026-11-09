package net.malusic.daclans

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer

class PlaceholderHook(private val plugin: DaClans) : PlaceholderExpansion() {

    override fun getIdentifier(): String = "daclans"

    override fun getAuthor(): String = "Malusic"

    override fun getVersion(): String = plugin.pluginMeta.version

    override fun persist(): Boolean = true

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        if (params.startsWith("top_")) {
            val parts = params.split("_")
            if (parts.size != 3) return null
            val n = parts[1].toIntOrNull() ?: return null
            val clan = plugin.clans.top().getOrNull(n - 1) ?: return ""
            return when (parts[2]) {
                "name" -> clan.coloredName()
                "value" -> plugin.clans.topValue(clan)
                "members" -> clan.members.size.toString()
                else -> null
            }
        }
        val p = player ?: return ""
        val clan = plugin.clans.ofPlayer(p.uniqueId) ?: return ""
        return when (params) {
            "name" -> clan.coloredName()
            "leader" -> Bukkit.getOfflinePlayer(clan.leader).name ?: ""
            "members" -> clan.members.size.toString()
            "max_members" -> clan.maxMembers.toString()
            "bank" -> ClanManager.money(clan.bank)
            "rank" -> clan.members[p.uniqueId] ?: ""
            "allies" -> clan.allies.size.toString()
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

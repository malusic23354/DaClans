package net.malusic.daclans.util

import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.malusic.daclans.DaClans
import net.malusic.daclans.Clan
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender

class Messages(private val plugin: DaClans) {

    private val mm = MiniMessage.miniMessage()

    fun send(sender: CommandSender, key: String, vararg values: Pair<String, String>) {
        val raw = plugin.config.getString("messages.$key") ?: return
        val resolvers = values.map { Placeholder.unparsed(it.first, it.second) }
        sender.sendMessage(mm.deserialize(raw, *resolvers.toTypedArray()))
    }

    fun sendParsed(sender: CommandSender, key: String, vararg values: Pair<String, String>) {
        val raw = plugin.config.getString("messages.$key") ?: return
        val resolvers = values.map { Placeholder.parsed(it.first, it.second) }
        sender.sendMessage(mm.deserialize(raw, *resolvers.toTypedArray()))
    }

    fun clan(clan: Clan, key: String, vararg values: Pair<String, String>) {
        clan.members.keys.forEach { id -> Bukkit.getPlayer(id)?.let { send(it, key, *values) } }
    }

    fun clanParsed(clan: Clan, key: String, vararg values: Pair<String, String>) {
        clan.members.keys.forEach { id -> Bukkit.getPlayer(id)?.let { sendParsed(it, key, *values) } }
    }
}

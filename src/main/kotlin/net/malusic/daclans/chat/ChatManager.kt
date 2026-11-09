package net.malusic.daclans.chat

import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.malusic.daclans.Clan
import net.malusic.daclans.DaClans
import net.malusic.daclans.api.ClanChatEvent
import org.bukkit.entity.Player
import java.util.UUID

class ChatManager(private val plugin: DaClans) {

    private var enabled = true
    private var carbonActive = false

    fun reload() {
        enabled = plugin.config.getBoolean("chat.enabled", true)
        carbonActive = false
        if (!plugin.config.getBoolean("chat.carbon.enabled", true)) return
        if (!plugin.hook("carbonchat", "CarbonChat")) return
        carbonActive = try {
            CarbonChatHook.register(plugin)
            true
        } catch (e: Throwable) {
            plugin.logger.warning("CarbonChat hook failed: ${e.message}")
            false
        }
    }

    fun sendClan(player: Player, message: String): Boolean {
        val clan = plugin.clans.ofPlayer(player.uniqueId)
        if (clan == null) {
            plugin.messages.send(player, "not-in-clan")
            return false
        }
        return send(player, clan, message, false)
    }

    fun sendAlly(player: Player, message: String): Boolean {
        val clan = plugin.clans.ofPlayer(player.uniqueId)
        if (clan == null) {
            plugin.messages.send(player, "not-in-clan")
            return false
        }
        return send(player, clan, message, true)
    }

    fun sendToClan(clan: Clan, text: String, ally: Boolean = false) {
        val recipients = if (ally) allyRecipients(clan) else clan.members.keys
        val format = plugin.config.getString(if (ally) "chat.ally-format" else "chat.clan-format", "") ?: return
        val component = MiniMessage.miniMessage().deserialize(
            format,
            Placeholder.unparsed("player", "Server"),
            Placeholder.unparsed("clan", clan.name),
            Placeholder.unparsed("message", text)
        )
        recipients.mapNotNull { plugin.server.getPlayer(it) }.forEach { it.sendMessage(component) }
    }

    fun carbonActive(): Boolean = carbonActive

    private fun send(player: Player, clan: Clan, message: String, ally: Boolean): Boolean {
        if (!enabled) {
            plugin.messages.send(player, "chat-disabled")
            return false
        }
        val event = ClanChatEvent(clan, player.uniqueId, message, ally)
        plugin.server.pluginManager.callEvent(event)
        if (event.isCancelled) return false
        val recipients = if (ally) allyRecipients(clan) else clan.members.keys
        val online = recipients.mapNotNull { plugin.server.getPlayer(it) }
        if (online.isEmpty()) {
            plugin.messages.send(player, "chat-no-recipients")
            return false
        }
        val format = plugin.config.getString(if (ally) "chat.ally-format" else "chat.clan-format", "") ?: return false
        val component = MiniMessage.miniMessage().deserialize(
            format,
            Placeholder.unparsed("player", player.name),
            Placeholder.unparsed("clan", clan.name),
            Placeholder.unparsed("message", message)
        )
        online.forEach { it.sendMessage(component) }
        return true
    }

    private fun allyRecipients(clan: Clan): Set<UUID> {
        val ids = LinkedHashSet<UUID>()
        ids.addAll(clan.members.keys)
        clan.allies.forEach { ally ->
            plugin.clans.get(ally)?.members?.keys?.let { ids.addAll(it) }
        }
        return ids
    }
}

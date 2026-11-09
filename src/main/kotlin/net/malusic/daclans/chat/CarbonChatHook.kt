package net.malusic.daclans.chat

import net.draycia.carbon.api.CarbonChatProvider
import net.draycia.carbon.api.channels.ChannelPermissionResult
import net.draycia.carbon.api.channels.ChannelPermissions
import net.draycia.carbon.api.channels.ChannelRegistry
import net.draycia.carbon.api.channels.ChatChannel
import net.draycia.carbon.api.event.CarbonEventSubscriber
import net.draycia.carbon.api.event.events.CarbonChannelRegisterEvent
import net.draycia.carbon.api.users.CarbonPlayer
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.malusic.daclans.Clan
import net.malusic.daclans.DaClans
import java.util.UUID

object CarbonChatHook {

    private val clanKey = Key.key("daclans", "clan")
    private val allyKey = Key.key("daclans", "ally")

    @Volatile
    private var subscribed = false

    fun register(plugin: DaClans) {
        val carbon = CarbonChatProvider.carbonChat()
        registerChannels(plugin, carbon.channelRegistry())
        if (subscribed) return
        subscribed = true
        carbon.eventHandler().subscribe(
            CarbonChannelRegisterEvent::class.java,
            object : CarbonEventSubscriber<CarbonChannelRegisterEvent> {
                override fun on(event: CarbonChannelRegisterEvent) {
                    registerChannels(plugin, event.channelRegistry())
                }
            }
        )
    }

    private fun registerChannels(plugin: DaClans, registry: ChannelRegistry) {
        registerChannel(plugin, registry, clanKey, false)
        registerChannel(plugin, registry, allyKey, true)
    }

    private fun registerChannel(plugin: DaClans, registry: ChannelRegistry, key: Key, ally: Boolean) {
        val existing = registry.channel(key)
        if (existing == null) {
            registry.register(BaseChannel(plugin, ally))
        } else if (existing !is WrappedChannel) {
            registry.register(WrappedChannel(plugin, key, existing, ally))
        }
    }

    private fun clanPermissions(plugin: DaClans): ChannelPermissions = ChannelPermissions.uniformDynamic { player ->
        if (plugin.clans.ofPlayer(player.uuid()) != null) {
            ChannelPermissionResult.allowed()
        } else {
            ChannelPermissionResult.denied(Component.text("You are not in a clan."))
        }
    }

    private fun clanRecipients(plugin: DaClans, sender: CarbonPlayer, ally: Boolean): List<Audience> {
        val clan = plugin.clans.ofPlayer(sender.uuid()) ?: return emptyList()
        val ids = LinkedHashSet<UUID>()
        ids.addAll(clan.members.keys)
        if (ally) {
            clan.allies.forEach { id ->
                plugin.clans.get(id)?.members?.keys?.let { ids.addAll(it) }
            }
        }
        return plugin.server.onlinePlayers.filter { it.uniqueId in ids }
    }

    private class BaseChannel(private val plugin: DaClans, private val ally: Boolean) : ChatChannel {

        override fun key(): Key = if (ally) allyKey else clanKey

        override fun permissions(): ChannelPermissions = clanPermissions(plugin)

        override fun recipients(sender: CarbonPlayer): List<Audience> = clanRecipients(plugin, sender, ally)

        override fun quickPrefix(): String = ""

        override fun shouldRegisterCommands(): Boolean = true

        override fun commandName(): String = if (ally) "allychat" else "clanchat"

        override fun commandAliases(): List<String> = emptyList()

        override fun radius(): Double = -1.0

        override fun emptyRadiusRecipientsMessage(): Boolean = false

        override fun cooldown(): Long = 0

        override fun playerCooldown(player: CarbonPlayer): Long = 0

        override fun startCooldown(player: CarbonPlayer): Long = 0

        override fun shouldCrossServer(): Boolean = false

        override fun render(
            sender: CarbonPlayer,
            viewer: Audience,
            message: Component,
            originalMessage: Component
        ): Component {
            val clan = plugin.clans.ofPlayer(sender.uuid())
            val format = plugin.config.getString(
                if (ally) "chat.ally-format" else "chat.clan-format",
                if (ally) "<gray>[Ally] <white><player>: <message>" else "<gray>[Clan] <white><player>: <message>"
            ) ?: ""
            return MiniMessage.miniMessage().deserialize(
                format,
                Placeholder.unparsed("player", sender.username()),
                Placeholder.unparsed("clan", clan?.name ?: ""),
                Placeholder.component("message", message)
            )
        }
    }

    private class WrappedChannel(
        private val plugin: DaClans,
        private val key: Key,
        private val delegate: ChatChannel,
        private val ally: Boolean
    ) : ChatChannel {

        override fun key(): Key = key

        override fun permissions(): ChannelPermissions = clanPermissions(plugin)

        override fun recipients(sender: CarbonPlayer): List<Audience> = clanRecipients(plugin, sender, ally)

        override fun quickPrefix(): String? = delegate.quickPrefix()

        override fun shouldRegisterCommands(): Boolean = delegate.shouldRegisterCommands()

        override fun commandName(): String = delegate.commandName()

        override fun commandAliases(): List<String> = delegate.commandAliases()

        override fun radius(): Double = delegate.radius()

        override fun emptyRadiusRecipientsMessage(): Boolean = delegate.emptyRadiusRecipientsMessage()

        override fun cooldown(): Long = delegate.cooldown()

        override fun playerCooldown(player: CarbonPlayer): Long = delegate.playerCooldown(player)

        override fun startCooldown(player: CarbonPlayer): Long = delegate.startCooldown(player)

        override fun shouldCrossServer(): Boolean = delegate.shouldCrossServer()

        override fun render(
            sender: CarbonPlayer,
            viewer: Audience,
            message: Component,
            originalMessage: Component
        ): Component = delegate.render(sender, viewer, message, originalMessage)
    }
}

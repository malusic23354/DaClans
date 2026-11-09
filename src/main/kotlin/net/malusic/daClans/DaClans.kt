package net.malusic.daclans

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import net.malusic.daclans.command.ClanCommand
import net.malusic.daclans.gui.SettingsGui
import net.malusic.daclans.integrations.PlaceholderHook
import net.malusic.daclans.integrations.VaultHook
import net.malusic.daclans.listener.ClanListener
import net.malusic.daclans.manager.ClanManager
import net.malusic.daclans.manager.Ranks
import net.malusic.daclans.storage.ClanStorage
import net.malusic.daclans.storage.StorageFactory
import net.malusic.daclans.util.Messages
import org.bukkit.plugin.java.JavaPlugin

class DaClans : JavaPlugin() {

    lateinit var storage: ClanStorage
        private set
    lateinit var clans: ClanManager
        private set
    lateinit var messages: Messages
        private set
    lateinit var ranks: Ranks
        private set
    lateinit var gui: SettingsGui
        private set
    var economy: VaultHook? = null
        private set

    fun hook(key: String, pluginName: String): Boolean =
        config.getBoolean("hooks.$key", false) && server.pluginManager.isPluginEnabled(pluginName)

    override fun onEnable() {
        saveDefaultConfig()
        messages = Messages(this)
        ranks = Ranks(this)
        storage = StorageFactory.create(this)
        clans = ClanManager(storage)
        clans.sort = config.getString("leaderboard.sort", "bank")!!
        clans.load()
        clans.refreshTop()
        gui = SettingsGui(this)
        server.pluginManager.registerEvents(gui, this)
        server.pluginManager.registerEvents(ClanListener(this), this)
        if (hook("vault", "Vault")) economy = VaultHook()
        if (hook("placeholderapi", "PlaceholderAPI")) registerPlaceholders()
        val period = config.getLong("leaderboard.refresh-seconds", 60L) * 20L
        server.scheduler.runTaskTimer(this, Runnable { clans.refreshTop() }, period, period)
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            ClanCommand(this).register(event.registrar())
        }
        logger.info("DaClans ${pluginMeta.version} enabled with ${clans.all().size} clans")
    }

    override fun onDisable() {
        if (::storage.isInitialized) storage.close()
    }

    private fun registerPlaceholders() {
        PlaceholderHook(this).register()
    }
}

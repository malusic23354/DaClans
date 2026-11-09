package net.malusic.daclans

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import net.malusic.daclans.util.Messages
import org.bukkit.event.HandlerList
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask

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

    private var clanListener: ClanListener? = null
    private var topTask: BukkitTask? = null
    private var placeholdersActive = false

    fun hook(key: String, pluginName: String): Boolean =
        config.getBoolean("hooks.$key", false) && server.pluginManager.isPluginEnabled(pluginName)

    override fun onEnable() {
        saveDefaultConfig()
        messages = Messages(this)
        ranks = Ranks(this)
        storage = StorageFactory.create(this)
        clans = ClanManager(storage)
        clans.load()
        gui = SettingsGui(this)
        server.pluginManager.registerEvents(gui, this)
        applyConfig()
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            ClanCommand(this).register(event.registrar())
        }
        logger.info("DaClans ${pluginMeta.version} enabled with ${clans.all().size} clans")
    }

    override fun onDisable() {
        if (::storage.isInitialized) storage.close()
    }

    fun reloadAll() {
        reloadConfig()
        ranks = Ranks(this)
        gui.reload()
        applyConfig()
    }

    private fun applyConfig() {
        clans.sort = config.getString("leaderboard.sort", "bank")!!
        clans.refreshTop()

        economy = if (hook("vault", "Vault")) VaultHook() else null
        syncPlaceholders()

        clanListener?.let { HandlerList.unregisterAll(it) }
        clanListener = ClanListener(this).also { server.pluginManager.registerEvents(it, this) }

        topTask?.cancel()
        val period = config.getLong("leaderboard.refresh-seconds", 60L) * 20L
        topTask = server.scheduler.runTaskTimer(this, Runnable { clans.refreshTop() }, period, period)
    }

    private fun syncPlaceholders() {
        val wanted = hook("placeholderapi", "PlaceholderAPI")
        if (wanted && !placeholdersActive) {
            PlaceholderBinding.register(this)
            placeholdersActive = true
        } else if (!wanted && placeholdersActive) {
            PlaceholderBinding.unregister()
            placeholdersActive = false
        }
    }
}
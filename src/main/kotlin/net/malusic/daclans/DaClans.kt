package net.malusic.daclans

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import net.malusic.daclans.api.ClanLeaderChangeEvent
import net.malusic.daclans.api.DaClansApi
import net.malusic.daclans.api.DaClansProvider
import net.malusic.daclans.chat.ChatManager
import net.malusic.daclans.util.Messages
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.event.HandlerList
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.UUID

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
    lateinit var chat: ChatManager
        private set
    lateinit var api: DaClansApi
        private set
    var economy: VaultHook? = null
        private set

    private var clanListener: ClanListener? = null
    private val backgroundTasks = mutableListOf<BukkitTask>()
    private var topTask: BukkitTask? = null
    private var placeholdersActive = false

    fun hook(key: String, pluginName: String): Boolean =
        config.getBoolean("hooks.$key", false) && server.pluginManager.isPluginEnabled(pluginName)

    override fun onEnable() {
        saveDefaultConfig()
        updateConfig()
        messages = Messages(this)
        ranks = Ranks(this)
        storage = StorageFactory.create(this)
        clans = ClanManager(storage)
        clans.load()
        gui = SettingsGui(this)
        chat = ChatManager(this)
        api = DaClansApi(this)
        DaClansProvider.register(api)
        server.pluginManager.registerEvents(gui, this)
        applyConfig()
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            ClanCommand(this).register(event.registrar())
        }
        logger.info("DaClans ${pluginMeta.version} enabled with ${clans.all().size} clans")
    }

    override fun onDisable() {
        DaClansProvider.unregister()
        if (::storage.isInitialized) storage.close()
    }

    fun reloadAll() {
        reloadConfig()
        updateConfig()
        ranks = Ranks(this)
        gui.reload()
        chat.reload()
        applyConfig()
    }

    private fun updateConfig() {
        val stream = getResource("config.yml") ?: return
        val defaults = YamlConfiguration.loadConfiguration(InputStreamReader(stream, StandardCharsets.UTF_8))
        val existing = config.getKeys(true)
        var changed = false
        for (path in defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(path)) continue
            if (path !in existing) {
                config.set(path, defaults.get(path))
                changed = true
            }
        }
        if (changed) saveConfig()
    }

    private fun applyConfig() {
        clans.sort = config.getString("leaderboard.sort", "bank")!!
        clans.bankLogLimit = config.getInt("clan.bank-logs.max", 20).coerceAtLeast(1)
        clans.bankLogRetentionDays = config.getLong("clan.bank-logs.retention-days", 30L).coerceAtLeast(1L)
        clans.trimLogs()
        clans.refreshTop()

        economy = if (hook("vault", "Vault")) VaultHook() else null
        chat.reload()
        syncPlaceholders()

        clanListener?.let { HandlerList.unregisterAll(it) }
        clanListener = ClanListener(this).also { server.pluginManager.registerEvents(it, this) }

        topTask?.cancel()
        backgroundTasks.forEach { it.cancel() }
        backgroundTasks.clear()

        val refreshTicks = (config.getLong("leaderboard.refresh-seconds", 60L) * 20L).coerceAtLeast(20L)
        topTask = server.scheduler.runTaskTimer(this, Runnable { clans.refreshTop() }, refreshTicks, refreshTicks)

        val cleanupTicks = (config.getLong("clan.cleanup-seconds", 60L) * 20L).coerceAtLeast(200L)
        backgroundTasks += server.scheduler.runTaskTimer(this, Runnable { clans.cleanupExpired() }, cleanupTicks, cleanupTicks)

        if (config.getBoolean("clan.inactivity.enabled", false)) {
            val checkTicks = (config.getLong("clan.inactivity.check-minutes", 30L) * 1200L).coerceAtLeast(1200L)
            backgroundTasks += server.scheduler.runTaskTimer(this, Runnable { handleInactiveLeaders() }, checkTicks, checkTicks)
        }
    }

    private fun handleInactiveLeaders() {
        if (!config.getBoolean("clan.inactivity.enabled", false)) return
        val days = config.getLong("clan.inactivity.days", 30L)
        if (days <= 0) return
        val cutoff = System.currentTimeMillis() - days * 86_400_000L
        val action = config.getString("clan.inactivity.action", "transfer")?.lowercase() ?: "transfer"
        clans.all().toList().forEach { clan ->
            val leaderSeen = clans.lastSeen(clan, clan.leader)
            if (server.getPlayer(clan.leader) != null || leaderSeen <= 0 || leaderSeen >= cutoff) return@forEach
            val candidates = clan.members.keys.filter { it != clan.leader }
            if (candidates.isEmpty() || action == "disband") {
                val name = clan.name
                clans.remove(clan)
                server.onlinePlayers.forEach { messages.send(it, "inactive-clan-disbanded", "name" to name) }
                return@forEach
            }
            val newLeader = candidates.maxWithOrNull(
                compareBy<UUID> { ranks.level(clan.members[it] ?: "") }
                    .thenBy { clans.lastSeen(clan, it) }
            ) ?: return@forEach
            val oldLeader = clan.leader
            clan.members[oldLeader] = ranks.default
            clan.leader = newLeader
            clan.members[newLeader] = "leader"
            clans.save(clan)
            server.pluginManager.callEvent(ClanLeaderChangeEvent(clan, oldLeader, newLeader, "inactivity"))
            messages.clan(clan, "leader-inactivity-transferred", "player" to (server.getOfflinePlayer(newLeader).name ?: "?"))
        }
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

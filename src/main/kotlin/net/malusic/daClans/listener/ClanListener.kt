package net.malusic.daclans

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ClanListener(private val plugin: DaClans) : Listener {

    private class Hit(val attacker: UUID, val clan: UUID, val time: Long)

    private val config = plugin.config
    private val reduceClan = config.getDouble("damage-reduction.clan", 0.0)
    private val reduceAllies = config.getDouble("damage-reduction.allies", 0.0)
    private val antiTeam = config.getBoolean("anti-teaming.enabled", false)
    private val threshold = config.getInt("anti-teaming.threshold", 2)
    private val windowMs = config.getLong("anti-teaming.window-seconds", 15L) * 1000L
    private val blockBounty = plugin.hook("bountylite", "BountyLite") && !config.getBoolean("bounty.allow-on-teammates", false)
    private val bountyCommands = config.getStringList("bounty.commands").map { it.lowercase() }.toSet()
    private val bountySubs = config.getStringList("bounty.blocked-subcommands").map { it.lowercase() }.toSet()
    private val hits = ConcurrentHashMap<UUID, MutableList<Hit>>()
    private val warned = ConcurrentHashMap<UUID, Long>()

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onDamage(e: EntityDamageByEntityEvent) {
        val victim = e.entity as? Player ?: return
        val attacker = (when (val d = e.damager) {
            is Player -> d
            is Projectile -> d.shooter as? Player
            else -> null
        }) ?: return
        if (attacker.uniqueId == victim.uniqueId) return
        val ac = plugin.clans.ofPlayer(attacker.uniqueId) ?: return
        val vc = plugin.clans.ofPlayer(victim.uniqueId)
        if (vc != null) {
            val pct = when {
                ac.id == vc.id -> reduceClan
                vc.id in ac.allies -> reduceAllies
                else -> 0.0
            }
            if (pct > 0) e.damage = e.damage * (1.0 - minOf(pct, 100.0) / 100.0)
        } else if (antiTeam) {
            track(victim, attacker, ac)
        }
    }

    private fun track(victim: Player, attacker: Player, clan: Clan) {
        val now = System.currentTimeMillis()
        val list = hits.computeIfAbsent(victim.uniqueId) { mutableListOf() }
        list.removeIf { now - it.time > windowMs || it.attacker == attacker.uniqueId }
        list.add(Hit(attacker.uniqueId, clan.id, now))
        val group = list.filter { it.clan == clan.id || it.clan in clan.allies }
        if (group.size < threshold) return
        var issued = false
        for (hit in group) {
            val p = Bukkit.getPlayer(hit.attacker) ?: continue
            if (now - (warned[p.uniqueId] ?: 0L) <= windowMs) continue
            warned[p.uniqueId] = now
            issued = true
            plugin.messages.send(p, "anti-team-warning")
        }
        if (issued) {
            Bukkit.getOnlinePlayers().filter { it.hasPermission("daclans.antiteam.notify") }
                .forEach { plugin.messages.send(it, "anti-team-staff", "victim" to victim.name, "clan" to clan.name) }
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onCommand(e: PlayerCommandPreprocessEvent) {
        if (!blockBounty) return
        val parts = e.message.trim().removePrefix("/").split(" ")
        if (parts[0].substringAfter(":").lowercase() !in bountyCommands) return
        if (parts.size < 2 || parts[1].lowercase() !in bountySubs) return
        if (plugin.clans.ofPlayer(e.player.uniqueId) == null) return
        for (arg in parts.drop(1)) {
            val target = Bukkit.getOfflinePlayerIfCached(arg) ?: continue
            if (plugin.clans.areTeammates(e.player.uniqueId, target.uniqueId)) {
                e.isCancelled = true
                plugin.messages.send(e.player, "bounty-blocked")
                return
            }
        }
    }

    @EventHandler
    fun onQuit(e: PlayerQuitEvent) {
        hits.remove(e.player.uniqueId)
        warned.remove(e.player.uniqueId)
    }
}

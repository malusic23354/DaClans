package net.malusic.daclans

import net.malusic.daclans.util.TimeFormat
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.ceil

class WarManager(private val plugin: DaClans) {

    class War(val caller: UUID, val victim: UUID, val callerName: String, val victimName: String) {
        var active = false
        var sideA: Set<UUID> = setOf(caller)
        var sideB: Set<UUID> = setOf(victim)
        var killsA = 0
        var killsB = 0
        var task: BukkitTask? = null
    }

    private val wars = CopyOnWriteArrayList<War>()

    private val killsToWin get() = plugin.config.getInt("wars.kills", 100).coerceAtLeast(1)
    private val allowAlly get() = plugin.config.getBoolean("wars.allowally", true)
    private val threshold get() = plugin.config.getInt("wars.threshold", 10)
    private val delayMs get() = TimeFormat.parse(plugin.config.getString("wars.time"), 300_000L)
    private val limitMs get() = TimeFormat.parse(plugin.config.getString("wars.limit"), 5_400_000L)

    fun activeCount(): Int = wars.count { it.active }

    fun inWar(clanId: UUID): Boolean = wars.any { it.active && (clanId in it.sideA || clanId in it.sideB) }

    fun shutdown() {
        wars.forEach { it.task?.cancel() }
        wars.clear()
    }

    private fun busy(id: UUID): Boolean =
        wars.any { it.caller == id || it.victim == id || (it.active && (id in it.sideA || id in it.sideB)) }

    private fun side(clan: Clan): Set<UUID> = if (allowAlly) clan.allies + clan.id else setOf(clan.id)

    private fun broadcast(ids: Collection<UUID>, key: String, vararg values: Pair<String, String>) {
        ids.forEach { id -> plugin.clans.get(id)?.let { plugin.messages.clan(it, key, *values) } }
    }

    fun call(player: Player, clan: Clan, other: Clan) {
        when {
            other.id == clan.id -> plugin.messages.send(player, "invalid-target")
            minOf(clan.members.size, other.members.size) < threshold ->
                plugin.messages.send(player, "war-too-small", "threshold" to threshold.toString())
            busy(clan.id) || busy(other.id) -> plugin.messages.send(player, "war-busy")
            else -> {
                val war = War(clan.id, other.id, clan.name, other.name)
                wars += war
                val minutes = ceil(delayMs / 60_000.0).toInt().toString()
                broadcast(side(clan) + side(other), "war-called", "clan" to clan.name, "victim" to other.name, "time" to minutes)
                war.task = plugin.server.scheduler.runTaskLater(plugin, Runnable { start(war) }, delayMs / 50L)
            }
        }
    }

    fun resign(player: Player, clan: Clan) {
        val war = wars.firstOrNull { it.caller == clan.id || it.victim == clan.id }
        if (war == null) {
            plugin.messages.send(player, "war-none")
            return
        }
        finish(war, if (war.caller == clan.id) war.victim else war.caller, "war-resigned", clan.name)
    }

    fun onKill(killer: UUID, victim: UUID) {
        val war = wars.firstOrNull {
            it.active && ((killer in it.sideA && victim in it.sideB) || (killer in it.sideB && victim in it.sideA))
        } ?: return
        if (killer in war.sideA) war.killsA++ else war.killsB++
        when {
            war.killsA >= killsToWin -> finish(war, war.caller, "war-won")
            war.killsB >= killsToWin -> finish(war, war.victim, "war-won")
        }
    }

    private fun start(war: War) {
        val a = plugin.clans.get(war.caller)
        val b = plugin.clans.get(war.victim)
        if (a == null || b == null) {
            wars -= war
            return
        }
        val shared = side(a).intersect(side(b))
        war.sideA = side(a) - shared + a.id
        war.sideB = side(b) - shared + b.id
        war.active = true
        broadcast(war.sideA + war.sideB, "war-initiated")
        war.task = plugin.server.scheduler.runTaskLater(plugin, Runnable { timeout(war) }, limitMs / 50L)
    }

    private fun timeout(war: War) {
        val winner = when {
            war.killsA > war.killsB -> war.caller
            war.killsB > war.killsA -> war.victim
            else -> null
        }
        finish(war, winner, if (winner == null) "war-draw" else "war-won")
    }

    private fun finish(war: War, winner: UUID?, key: String, resigner: String = "") {
        war.task?.cancel()
        wars -= war
        val winnerName = when (winner) {
            war.caller -> war.callerName
            war.victim -> war.victimName
            else -> ""
        }
        val loserName = if (winner == war.caller) war.victimName else war.callerName
        val targets = if (war.active) war.sideA + war.sideB else setOf(war.caller, war.victim)
        broadcast(
            targets, key,
            "clan" to war.callerName, "victim" to war.victimName,
            "winner" to winnerName, "loser" to loserName, "resigner" to resigner
        )
    }
}
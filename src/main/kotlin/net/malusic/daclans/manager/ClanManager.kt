package net.malusic.daclans

import net.malusic.daclans.util.Money
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ClanManager(
    private val storage: ClanStorage,
    var bankLogLimit: Int = 20,
    var bankLogRetentionDays: Long = 30
) {

    companion object {
        val TYPES = listOf("bank", "members", "allies")

        fun money(value: Long): String = Money.format(value)
    }

    private val idIndex = ConcurrentHashMap<UUID, Clan>()
    private val nameIndex = ConcurrentHashMap<String, Clan>()
    private val playerIndex = ConcurrentHashMap<UUID, Clan>()
    private val invites = ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, Long>>()
    private val allyRequests = ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, Long>>()

    @Volatile
    private var topCaches: Map<String, List<Clan>> = emptyMap()

    var sort = "bank"

    fun load() {
        storage.loadAll().forEach(::index)
        idIndex.values.forEach {
            it.logs.sortByDescending { log -> log.createdAt }
            while (it.logs.size > bankLogLimit) {
                val removed = it.logs.removeAt(it.logs.lastIndex)
                storage.deleteLog(removed.id)
            }
        }
        refreshTop()
    }

    fun all(): Collection<Clan> = idIndex.values

    fun get(id: UUID): Clan? = idIndex[id]

    fun byName(name: String): Clan? = nameIndex[name.lowercase()]

    fun ofPlayer(player: UUID): Clan? = playerIndex[player]

    fun areTeammates(a: UUID, b: UUID): Boolean {
        val ca = playerIndex[a] ?: return false
        val cb = playerIndex[b] ?: return false
        return ca.id == cb.id || cb.id in ca.allies
    }

    fun add(clan: Clan) {
        index(clan)
        storage.save(clan)
        refreshTop()
    }

    fun remove(clan: Clan) {
        idIndex.remove(clan.id)
        nameIndex.remove(clan.name.lowercase())
        clan.members.keys.forEach { playerIndex.remove(it) }
        clan.logs.forEach { storage.deleteLog(it.id) }
        storage.delete(clan.id)
        invites.values.forEach { it.remove(clan.id) }
        invites.entries.removeIf { it.value.isEmpty() }
        allyRequests.remove(clan.id)
        allyRequests.values.forEach { it.remove(clan.id) }
        allyRequests.entries.removeIf { it.value.isEmpty() }
        idIndex.values.forEach {
            val a = it.allies.remove(clan.id)
            val e = it.enemies.remove(clan.id)
            if (a || e) storage.save(it)
        }
        refreshTop()
    }

    fun save(clan: Clan) {
        storage.save(clan)
        refreshTop()
    }

    fun rename(clan: Clan, newName: String) {
        nameIndex.remove(clan.name.lowercase())
        clan.name = newName
        nameIndex[newName.lowercase()] = clan
        storage.save(clan)
        refreshTop()
    }

    fun setOwner(clan: Clan, newLeader: UUID, formerRank: String) {
        clan.members[clan.leader] = formerRank
        clan.leader = newLeader
        clan.members[newLeader] = "leader"
        clan.lastSeen.putIfAbsent(newLeader, System.currentTimeMillis())
        storage.save(clan)
    }

    fun addMember(clan: Clan, player: UUID, role: String) {
        clan.members[player] = role
        clan.lastSeen[player] = System.currentTimeMillis()
        playerIndex[player] = clan
        invites.remove(player)
        storage.save(clan)
        refreshTop()
    }

    fun removeMember(clan: Clan, player: UUID) {
        clan.members.remove(player)
        clan.lastSeen.remove(player)
        playerIndex.remove(player)
        storage.save(clan)
        refreshTop()
    }

    fun shiftRank(clan: Clan, player: UUID, delta: Int, order: List<String>): String? {
        val current = clan.members[player] ?: return null
        val i = order.indexOf(current)
        val j = i + delta
        if (i < 0 || j !in order.indices) return null
        clan.members[player] = order[j]
        storage.save(clan)
        return order[j]
    }

    fun invite(target: UUID, clan: Clan, ttlMillis: Long) {
        val expiry = if (ttlMillis <= 0) Long.MAX_VALUE else System.currentTimeMillis() + ttlMillis
        invites.computeIfAbsent(target) { ConcurrentHashMap() }[clan.id] = expiry
    }

    fun hasInvite(target: UUID, clan: Clan): Boolean {
        val expiry = invites[target]?.get(clan.id) ?: return false
        return expiry > System.currentTimeMillis()
    }

    fun pendingInvites(target: UUID): List<Clan> {
        val map = invites[target] ?: return emptyList()
        val now = System.currentTimeMillis()
        map.values.removeIf { it <= now }
        return map.keys.mapNotNull { idIndex[it] }
    }

    fun requestAlly(from: Clan, to: Clan, ttlMillis: Long) {
        val expiry = if (ttlMillis <= 0) Long.MAX_VALUE else System.currentTimeMillis() + ttlMillis
        allyRequests.computeIfAbsent(to.id) { ConcurrentHashMap() }[from.id] = expiry
    }

    fun hasAllyRequest(from: Clan, to: Clan): Boolean {
        val expiry = allyRequests[to.id]?.get(from.id) ?: return false
        return expiry > System.currentTimeMillis()
    }

    fun makeAllies(a: Clan, b: Clan) {
        a.allies.add(b.id)
        b.allies.add(a.id)
        a.enemies.remove(b.id)
        b.enemies.remove(a.id)
        allyRequests[a.id]?.remove(b.id)
        allyRequests[b.id]?.remove(a.id)
        storage.save(a)
        storage.save(b)
        refreshTop()
    }

    fun breakAlly(a: Clan, b: Clan) {
        a.allies.remove(b.id)
        b.allies.remove(a.id)
        storage.save(a)
        storage.save(b)
        refreshTop()
    }

    fun setEnemy(a: Clan, b: Clan) {
        a.enemies.add(b.id)
        a.allies.remove(b.id)
        b.allies.remove(a.id)
        storage.save(a)
        storage.save(b)
        refreshTop()
    }

    fun clearEnemy(a: Clan, b: Clan) {
        a.enemies.remove(b.id)
        storage.save(a)
    }

    fun updateLastSeen(player: UUID, time: Long = System.currentTimeMillis()) {
        val clan = playerIndex[player] ?: return
        clan.lastSeen[player] = time
        storage.save(clan)
    }

    fun lastSeen(clan: Clan, player: UUID): Long =
        clan.lastSeen[player] ?: 0L

    fun addBankLog(clan: Clan, player: UUID?, type: BankLog.Type, amountCents: Long, balanceCents: Long) {
        val log = BankLog(
            clanId = clan.id,
            player = player,
            type = type,
            amountCents = amountCents,
            balanceCents = balanceCents
        )
        clan.logs.add(0, log)
        while (clan.logs.size > bankLogLimit) {
            val removed = clan.logs.removeAt(clan.logs.lastIndex)
            storage.deleteLog(removed.id)
        }
        storage.appendLog(log)
        storage.deleteLogsBefore(clan.id, System.currentTimeMillis() - bankLogRetentionDays * 86_400_000L)
    }

    fun trimLogs() {
        idIndex.values.forEach { clan ->
            while (clan.logs.size > bankLogLimit) {
                val removed = clan.logs.removeAt(clan.logs.lastIndex)
                storage.deleteLog(removed.id)
            }
        }
    }

    fun cleanupExpired() {
        val now = System.currentTimeMillis()
        invites.entries.forEach { (target, map) ->
            map.values.removeIf { it <= now }
            map.keys.removeIf { idIndex[it] == null }
            if (map.isEmpty()) invites.remove(target)
        }
        allyRequests.entries.forEach { (target, map) ->
            map.values.removeIf { it <= now }
            map.keys.removeIf { idIndex[it] == null }
            if (map.isEmpty()) allyRequests.remove(target)
        }
        idIndex.values.forEach { clan ->
            val cutoff = now - bankLogRetentionDays * 86_400_000L
            val before = clan.logs.size
            clan.logs.removeIf { it.createdAt < cutoff }
            if (clan.logs.size != before) storage.deleteLogsBefore(clan.id, cutoff)
        }
    }

    fun refreshTop() {
        topCaches = TYPES.associateWith { type ->
            idIndex.values.sortedWith(
                compareByDescending<Clan> { sortValue(it, type) }
                    .thenBy { it.name.lowercase() }
            )
        }
    }

    fun top(type: String = sort): List<Clan> =
        topCaches[type] ?: topCaches[sort] ?: emptyList()

    fun topValue(clan: Clan, type: String = sort): String = when (type) {
        "members" -> clan.members.size.toString()
        "allies" -> clan.allies.size.toString()
        else -> money(clan.bankCents)
    }

    fun typeOrSort(type: String?): String {
        val value = type?.lowercase()
        return if (value in TYPES) value!! else sort
    }

    private fun sortValue(clan: Clan, type: String): Long = when (type) {
        "members" -> clan.members.size.toLong()
        "allies" -> clan.allies.size.toLong()
        else -> clan.bankCents
    }

    private fun index(clan: Clan) {
        idIndex[clan.id] = clan
        nameIndex[clan.name.lowercase()] = clan
        clan.members.keys.forEach { playerIndex[it] = clan }
    }
}

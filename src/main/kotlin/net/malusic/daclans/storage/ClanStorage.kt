package net.malusic.daclans

import java.util.UUID

interface ClanStorage {
    fun loadAll(): List<Clan>
    fun save(clan: Clan)
    fun delete(id: UUID)
    fun appendLog(log: BankLog)
    fun deleteLog(id: UUID)
    fun deleteLogsBefore(clanId: UUID, before: Long)
    fun close()
}

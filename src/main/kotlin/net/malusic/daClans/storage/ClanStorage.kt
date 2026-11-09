package net.malusic.daclans.storage

import net.malusic.daclans.model.Clan
import java.util.UUID

interface ClanStorage {
    fun loadAll(): List<Clan>
    fun save(clan: Clan)
    fun delete(id: UUID)
    fun close()
}

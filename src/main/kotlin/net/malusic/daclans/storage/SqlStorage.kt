package net.malusic.daclans

import java.sql.Connection
import java.sql.Driver
import java.util.Properties
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger

class SqlStorage(
    private val driver: Driver,
    private val url: String,
    private val user: String?,
    private val password: String?,
    private val logger: Logger
) : ClanStorage {

    private val executor = Executors.newSingleThreadExecutor { Thread(it, "DaClans-Storage").apply { isDaemon = true } }
    private var connection: Connection? = null

    private data class Snapshot(
        val id: String,
        val name: String,
        val leader: String,
        val bankCents: Long,
        val createdAt: Long,
        val open: Boolean,
        val inviteLeaderOnly: Boolean,
        val maxMembers: Int,
        val allies: String,
        val enemies: String,
        val perms: String,
        val color: String,
        val description: String,
        val members: List<Triple<String, String, Long>>
    )

    init {
        executor.submit {
            val c = conn()
            createTables(c)
            migrate(c)
        }.get()
    }

    private fun createTables(c: Connection) {
        c.createStatement().use { st ->
            st.execute(
                "CREATE TABLE IF NOT EXISTS clans (id VARCHAR(36) PRIMARY KEY, name VARCHAR(32) NOT NULL UNIQUE, leader VARCHAR(36) NOT NULL, " +
                    "bank_cents BIGINT NOT NULL DEFAULT 0, created_at BIGINT NOT NULL, is_open TINYINT NOT NULL DEFAULT 0, " +
                    "invite_leader_only TINYINT NOT NULL DEFAULT 1, max_members INT NOT NULL DEFAULT 10, allies TEXT, enemies TEXT, " +
                    "perms TEXT, color VARCHAR(32) NOT NULL DEFAULT '', description VARCHAR(255) NOT NULL DEFAULT '')"
            )
            st.execute(
                "CREATE TABLE IF NOT EXISTS members (clan_id VARCHAR(36) NOT NULL, player VARCHAR(36) NOT NULL PRIMARY KEY, " +
                    "role VARCHAR(32) NOT NULL, last_seen BIGINT NOT NULL DEFAULT 0)"
            )
            st.execute(
                "CREATE TABLE IF NOT EXISTS clan_bank_logs (id VARCHAR(36) PRIMARY KEY, clan_id VARCHAR(36) NOT NULL, " +
                    "player VARCHAR(36), type VARCHAR(16) NOT NULL, amount_cents BIGINT NOT NULL, balance_cents BIGINT NOT NULL, " +
                    "created_at BIGINT NOT NULL, note VARCHAR(255) NOT NULL DEFAULT '')"
            )
            st.execute("CREATE TABLE IF NOT EXISTS schema_version (version INT NOT NULL)")
        }
    }

    private fun migrate(c: Connection) {
        ensureColumn(c, "clans", "color", "VARCHAR(32) NOT NULL DEFAULT ''")
        ensureColumn(c, "clans", "description", "VARCHAR(255) NOT NULL DEFAULT ''")
        if ("bank_cents" !in columns(c, "clans")) {
            c.createStatement().use { it.execute("ALTER TABLE clans ADD COLUMN bank_cents BIGINT NOT NULL DEFAULT 0") }
            if ("bank" in columns(c, "clans")) {
                val rows = mutableListOf<Pair<String, Double>>()
                c.createStatement().use { st ->
                    st.executeQuery("SELECT id, bank FROM clans").use { rs ->
                        while (rs.next()) rows += rs.getString(1) to rs.getDouble(2)
                    }
                }
                c.prepareStatement("UPDATE clans SET bank_cents = ? WHERE id = ?").use { ps ->
                    rows.forEach {
                        ps.setLong(1, Math.round(it.second * 100.0))
                        ps.setString(2, it.first)
                        ps.addBatch()
                    }
                    ps.executeBatch()
                }
            }
        }
        ensureColumn(c, "members", "last_seen", "BIGINT NOT NULL DEFAULT 0")
        if ("bank" in columns(c, "clans")) {
            runCatching {
                c.createStatement().use { it.execute("ALTER TABLE clans DROP COLUMN bank") }
            }.onFailure {
                logger.warning("Could not drop legacy clans.bank column: ${it.message}")
            }
        }
        c.createStatement().use { st ->
            val current = st.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version").use {
                if (it.next()) it.getInt(1) else 0
            }
            if (current < 1) st.executeUpdate("INSERT INTO schema_version (version) VALUES (1)")
        }
    }

    private fun ensureColumn(c: Connection, table: String, column: String, definition: String) {
        if (column !in columns(c, table)) {
            c.createStatement().use { it.execute("ALTER TABLE $table ADD COLUMN $column $definition") }
        }
    }

    private fun columns(c: Connection, table: String): Set<String> =
        c.createStatement().use { st ->
            st.executeQuery("SELECT * FROM $table WHERE 1 = 0").use { rs ->
                val meta = rs.metaData
                (1..meta.columnCount).map { meta.getColumnName(it).lowercase() }.toSet()
            }
        }

    private fun conn(): Connection {
        val current = connection
        if (current != null && !current.isClosed && current.isValid(2)) return current
        val props = Properties().apply {
            user?.let { setProperty("user", it) }
            password?.let { setProperty("password", it) }
        }
        val created = driver.connect(url, props) ?: error("Driver rejected url")
        connection = created
        return created
    }

    private fun run(block: (Connection) -> Unit) {
        executor.execute {
            try {
                block(conn())
            } catch (e: Exception) {
                logger.log(Level.SEVERE, "Storage operation failed", e)
            }
        }
    }

    private fun ids(raw: String?): List<UUID> =
        raw.orEmpty().split(",").filter { it.isNotBlank() }.map { UUID.fromString(it) }

    override fun loadAll(): List<Clan> = executor.submit<List<Clan>> {
        val c = conn()
        val clans = HashMap<UUID, Clan>()
        c.createStatement().use { st ->
            st.executeQuery(
                "SELECT id, name, leader, bank_cents, created_at, is_open, invite_leader_only, max_members, allies, enemies, perms, color, description FROM clans"
            ).use { rs ->
                while (rs.next()) {
                    val clan = Clan(
                        UUID.fromString(rs.getString(1)),
                        rs.getString(2),
                        UUID.fromString(rs.getString(3)),
                        rs.getLong(5),
                        rs.getLong(4),
                        rs.getBoolean(6),
                        rs.getBoolean(7),
                        rs.getInt(8)
                    )
                    clan.allies.addAll(ids(rs.getString(9)))
                    clan.enemies.addAll(ids(rs.getString(10)))
                    rs.getString(11).orEmpty().split(";").filter { it.contains("=") }.forEach {
                        val parts = it.split("=", limit = 2)
                        clan.perms[parts[0]] = parts[1]
                    }
                    clan.color = rs.getString(12).orEmpty()
                    clan.description = rs.getString(13).orEmpty()
                    clans[clan.id] = clan
                }
            }
        }
        c.createStatement().use { st ->
            st.executeQuery("SELECT clan_id, player, role, last_seen FROM members").use { rs ->
                while (rs.next()) {
                    val clan = clans[UUID.fromString(rs.getString(1))] ?: continue
                    val player = UUID.fromString(rs.getString(2))
                    clan.members[player] = rs.getString(3)
                    clan.lastSeen[player] = rs.getLong(4)
                }
            }
        }
        c.createStatement().use { st ->
            st.executeQuery(
                "SELECT id, clan_id, player, type, amount_cents, balance_cents, created_at, note FROM clan_bank_logs ORDER BY created_at DESC"
            ).use { rs ->
                while (rs.next()) {
                    val clan = clans[UUID.fromString(rs.getString(2))] ?: continue
                    val type = runCatching { BankLog.Type.valueOf(rs.getString(4)) }.getOrDefault(BankLog.Type.DEPOSIT)
                    clan.logs += BankLog(
                        UUID.fromString(rs.getString(1)),
                        clan.id,
                        rs.getString(3)?.let(UUID::fromString),
                        type,
                        rs.getLong(5),
                        rs.getLong(6),
                        rs.getLong(7),
                        rs.getString(8).orEmpty()
                    )
                }
            }
        }
        clans.values.toList()
    }.get()

    override fun save(clan: Clan) {
        val snapshot = Snapshot(
            clan.id.toString(),
            clan.name,
            clan.leader.toString(),
            clan.bankCents,
            clan.createdAt,
            clan.open,
            clan.inviteLeaderOnly,
            clan.maxMembers,
            clan.allies.joinToString(","),
            clan.enemies.joinToString(","),
            clan.perms.entries.joinToString(";") { "${it.key}=${it.value}" },
            clan.color,
            clan.description,
            clan.members.entries.map {
                Triple(it.key.toString(), it.value, clan.lastSeen[it.key] ?: 0L)
            }
        )
        run { c ->
            c.autoCommit = false
            try {
                c.prepareStatement(
                    "REPLACE INTO clans (id, name, leader, bank_cents, created_at, is_open, invite_leader_only, max_members, allies, enemies, perms, color, description) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                ).use {
                    it.setString(1, snapshot.id)
                    it.setString(2, snapshot.name)
                    it.setString(3, snapshot.leader)
                    it.setLong(4, snapshot.bankCents)
                    it.setLong(5, snapshot.createdAt)
                    it.setBoolean(6, snapshot.open)
                    it.setBoolean(7, snapshot.inviteLeaderOnly)
                    it.setInt(8, snapshot.maxMembers)
                    it.setString(9, snapshot.allies)
                    it.setString(10, snapshot.enemies)
                    it.setString(11, snapshot.perms)
                    it.setString(12, snapshot.color)
                    it.setString(13, snapshot.description)
                    it.executeUpdate()
                }
                c.prepareStatement("DELETE FROM members WHERE clan_id = ?").use {
                    it.setString(1, snapshot.id)
                    it.executeUpdate()
                }
                c.prepareStatement("REPLACE INTO members (clan_id, player, role, last_seen) VALUES (?, ?, ?, ?)").use {
                    for ((player, role, lastSeen) in snapshot.members) {
                        it.setString(1, snapshot.id)
                        it.setString(2, player)
                        it.setString(3, role)
                        it.setLong(4, lastSeen)
                        it.addBatch()
                    }
                    it.executeBatch()
                }
                c.commit()
            } catch (e: Exception) {
                c.rollback()
                throw e
            } finally {
                c.autoCommit = true
            }
        }
    }

    override fun delete(id: UUID) {
        val key = id.toString()
        run { c ->
            c.autoCommit = false
            try {
                c.prepareStatement("DELETE FROM members WHERE clan_id = ?").use {
                    it.setString(1, key)
                    it.executeUpdate()
                }
                c.prepareStatement("DELETE FROM clan_bank_logs WHERE clan_id = ?").use {
                    it.setString(1, key)
                    it.executeUpdate()
                }
                c.prepareStatement("DELETE FROM clans WHERE id = ?").use {
                    it.setString(1, key)
                    it.executeUpdate()
                }
                c.commit()
            } catch (e: Exception) {
                c.rollback()
                throw e
            } finally {
                c.autoCommit = true
            }
        }
    }

    override fun appendLog(log: BankLog) {
        run { c ->
            c.prepareStatement(
                "REPLACE INTO clan_bank_logs (id, clan_id, player, type, amount_cents, balance_cents, created_at, note) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
            ).use {
                it.setString(1, log.id.toString())
                it.setString(2, log.clanId.toString())
                it.setString(3, log.player?.toString())
                it.setString(4, log.type.name)
                it.setLong(5, log.amountCents)
                it.setLong(6, log.balanceCents)
                it.setLong(7, log.createdAt)
                it.setString(8, log.note)
                it.executeUpdate()
            }
        }
    }

    override fun deleteLog(id: UUID) {
        run { c ->
            c.prepareStatement("DELETE FROM clan_bank_logs WHERE id = ?").use {
                it.setString(1, id.toString())
                it.executeUpdate()
            }
        }
    }

    override fun deleteLogsBefore(clanId: UUID, before: Long) {
        run { c ->
            c.prepareStatement("DELETE FROM clan_bank_logs WHERE clan_id = ? AND created_at < ?").use {
                it.setString(1, clanId.toString())
                it.setLong(2, before)
                it.executeUpdate()
            }
        }
    }

    override fun close() {
        executor.shutdown()
        executor.awaitTermination(10, TimeUnit.SECONDS)
        connection?.close()
    }
}

package net.malusic.daclans.storage

import net.malusic.daclans.model.Clan
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

    init {
        executor.submit {
            conn().createStatement().use {
                it.execute(
                    "CREATE TABLE IF NOT EXISTS clans (id VARCHAR(36) PRIMARY KEY, name VARCHAR(32) NOT NULL UNIQUE, leader VARCHAR(36) NOT NULL, " +
                        "bank DOUBLE NOT NULL, created_at BIGINT NOT NULL, is_open TINYINT NOT NULL DEFAULT 0, invite_leader_only TINYINT NOT NULL DEFAULT 1, " +
                        "max_members INT NOT NULL DEFAULT 10, allies TEXT, enemies TEXT, perms TEXT)"
                )
                it.execute("CREATE TABLE IF NOT EXISTS members (clan_id VARCHAR(36) NOT NULL, player VARCHAR(36) NOT NULL PRIMARY KEY, role VARCHAR(32) NOT NULL)")
            }
        }.get()
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
            st.executeQuery("SELECT id, name, leader, bank, created_at, is_open, invite_leader_only, max_members, allies, enemies, perms FROM clans").use { rs ->
                while (rs.next()) {
                    val clan = Clan(
                        UUID.fromString(rs.getString(1)),
                        rs.getString(2),
                        UUID.fromString(rs.getString(3)),
                        rs.getLong(5),
                        rs.getDouble(4),
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
                    clans[clan.id] = clan
                }
            }
        }
        c.createStatement().use { st ->
            st.executeQuery("SELECT clan_id, player, role FROM members").use { rs ->
                while (rs.next()) {
                    clans[UUID.fromString(rs.getString(1))]
                        ?.members?.put(UUID.fromString(rs.getString(2)), rs.getString(3))
                }
            }
        }
        clans.values.toList()
    }.get()

    override fun save(clan: Clan) {
        val id = clan.id.toString()
        val name = clan.name
        val leader = clan.leader.toString()
        val bank = clan.bank
        val created = clan.createdAt
        val open = clan.open
        val leaderOnly = clan.inviteLeaderOnly
        val max = clan.maxMembers
        val allies = clan.allies.joinToString(",")
        val enemies = clan.enemies.joinToString(",")
        val perms = clan.perms.entries.joinToString(";") { "${it.key}=${it.value}" }
        val members = clan.members.entries.map { it.key.toString() to it.value }
        run { c ->
            c.autoCommit = false
            try {
                c.prepareStatement(
                    "REPLACE INTO clans (id, name, leader, bank, created_at, is_open, invite_leader_only, max_members, allies, enemies, perms) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                ).use {
                    it.setString(1, id)
                    it.setString(2, name)
                    it.setString(3, leader)
                    it.setDouble(4, bank)
                    it.setLong(5, created)
                    it.setBoolean(6, open)
                    it.setBoolean(7, leaderOnly)
                    it.setInt(8, max)
                    it.setString(9, allies)
                    it.setString(10, enemies)
                    it.setString(11, perms)
                    it.executeUpdate()
                }
                c.prepareStatement("DELETE FROM members WHERE clan_id = ?").use {
                    it.setString(1, id)
                    it.executeUpdate()
                }
                c.prepareStatement("REPLACE INTO members (clan_id, player, role) VALUES (?, ?, ?)").use {
                    for ((player, role) in members) {
                        it.setString(1, id)
                        it.setString(2, player)
                        it.setString(3, role)
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

    override fun close() {
        executor.shutdown()
        executor.awaitTermination(10, TimeUnit.SECONDS)
        connection?.close()
    }
}

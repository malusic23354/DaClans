package net.malusic.daclans

import org.bukkit.plugin.java.JavaPlugin
import java.io.File

object StorageFactory {

    fun create(plugin: JavaPlugin): ClanStorage {
        val config = plugin.config
        return when (config.getString("storage.type", "sqlite")!!.lowercase()) {
            "mysql" -> {
                val host = config.getString("storage.mysql.host", "localhost")
                val port = config.getInt("storage.mysql.port", 3306)
                val database = config.getString("storage.mysql.database", "daclans")
                SqlStorage(
                    com.mysql.cj.jdbc.Driver(),
                    "jdbc:mysql://$host:$port/$database?characterEncoding=utf8",
                    config.getString("storage.mysql.username", "root"),
                    config.getString("storage.mysql.password", ""),
                    plugin.logger
                )
            }
            else -> {
                plugin.dataFolder.mkdirs()
                val file = File(plugin.dataFolder, config.getString("storage.sqlite.file", "clans.db")!!)
                SqlStorage(org.sqlite.JDBC(), "jdbc:sqlite:${file.absolutePath}", null, null, plugin.logger)
            }
        }
    }
}

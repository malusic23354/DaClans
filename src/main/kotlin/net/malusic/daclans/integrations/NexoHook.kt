package net.malusic.daclans

import org.bukkit.inventory.ItemStack

object NexoHook {

    fun item(plugin: DaClans, id: String): ItemStack? {
        if (!plugin.hook("nexo", "Nexo")) return null
        return try {
            val cls = Class.forName("com.nexomc.nexo.api.NexoItems")
            val instance = try {
                cls.getField("INSTANCE").get(null)
            } catch (e: Exception) {
                null
            }
            val builder = cls.getMethod("itemFromId", String::class.java).invoke(instance, id) ?: return null
            builder.javaClass.getMethod("build").invoke(builder) as? ItemStack
        } catch (e: Exception) {
            null
        }
    }
}

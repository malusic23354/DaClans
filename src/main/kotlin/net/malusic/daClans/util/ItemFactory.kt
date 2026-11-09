package net.malusic.daclans.util

import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.format.TextDecoration
import net.malusic.daclans.DaClans
import net.malusic.daclans.integrations.NexoHook
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.inventory.ItemStack

object ItemFactory {

    private val mm = MiniMessage.miniMessage()

    fun build(
        plugin: DaClans,
        section: ConfigurationSection?,
        values: List<Pair<String, String>> = emptyList(),
        name: String? = null,
        extraLore: List<String> = emptyList()
    ): ItemStack {
        val resolver = TagResolver.resolver(values.map { Placeholder.unparsed(it.first, it.second) })
        val nexoId = section?.getString("nexoId")?.takeIf { it.isNotBlank() }
        val item = nexoId?.let { NexoHook.item(plugin, it) }
            ?: ItemStack(section?.getString("material")?.let { Material.matchMaterial(it) } ?: Material.STONE)
        val meta = item.itemMeta ?: return item
        (name ?: section?.getString("displayname"))?.let { meta.displayName(line(it, resolver)) }
        val lore = extraLore + (section?.getStringList("lore") ?: emptyList())
        if (lore.isNotEmpty()) meta.lore(lore.map { line(it, resolver) })
        if (section?.getBoolean("glowing") == true) meta.setEnchantmentGlintOverride(true)
        item.itemMeta = meta
        return item
    }

    private fun line(text: String, resolver: TagResolver) =
        mm.deserialize(text, resolver).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)
}

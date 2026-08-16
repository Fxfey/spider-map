package com.spidermap

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Server
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import java.util.UUID

/**
 * `/claims editor add|remove|list` — the whole editor-management surface.
 *
 * Gated by the `spidermap.command.claims` permission, which plugin.yml defaults
 * to op. That works on a bare Paper install and still lets a server that *does*
 * run a permission plugin delegate it to staff who aren't full operators.
 */
class EditorCommand(
    private val editors: EditorStore,
    private val server: Server,
) : CommandExecutor, TabCompleter {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>,
    ): Boolean {
        if (args.size < 2 || !args[0].equals("editor", ignoreCase = true)) {
            if (args.size == 1 && args[0].equals("editor", ignoreCase = true)) {
                listEditors(sender)
                return true
            }
            return false // Bukkit prints the usage line from plugin.yml.
        }

        when (args[1].lowercase()) {
            "list" -> listEditors(sender)
            "add" -> changeEditor(sender, args, adding = true)
            "remove" -> changeEditor(sender, args, adding = false)
            else -> return false
        }

        return true
    }

    private fun listEditors(sender: CommandSender) {
        val current = editors.load()

        if (current.isEmpty()) {
            sender.sendMessage(
                info("No map editors yet — add one with ")
                    .append(Component.text("/claims editor add <player>", NamedTextColor.WHITE)),
            )
            return
        }

        sender.sendMessage(info("Map editors (${current.size}):"))
        for (uuid in current) {
            // A name only if the server has seen this player; the UUID is the
            // real record, so it is always shown.
            val name = server.getOfflinePlayer(uuid).name
            sender.sendMessage(
                Component.text("  • ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(name ?: "unknown player", NamedTextColor.WHITE))
                    .append(Component.text("  $uuid", NamedTextColor.DARK_GRAY)),
            )
        }
    }

    private fun changeEditor(sender: CommandSender, args: Array<out String>, adding: Boolean) {
        if (args.size < 3) {
            sender.sendMessage(problem("Usage: /claims editor ${if (adding) "add" else "remove"} <player>"))
            return
        }

        val target = args[2]
        val uuid = resolve(target)

        if (uuid == null) {
            sender.sendMessage(
                problem("Don't know a player called '$target'. ") ,
            )
            sender.sendMessage(
                Component.text(
                    "They must have joined this server at least once, or give their UUID instead.",
                    NamedTextColor.GRAY,
                ),
            )
            return
        }

        val displayName = server.getOfflinePlayer(uuid).name ?: uuid.toString()

        val changed = if (adding) editors.add(uuid) else editors.remove(uuid)

        if (!changed) {
            sender.sendMessage(
                problem(
                    if (adding) "$displayName is already a map editor."
                    else "$displayName is not a map editor.",
                ),
            )
            return
        }

        sender.sendMessage(
            success(if (adding) "$displayName can now edit claims." else "$displayName can no longer edit claims."),
        )
    }

    /**
     * Name or UUID to UUID, without ever blocking the main thread.
     *
     * `getOfflinePlayer(String)` would hit Mojang's API for an unknown name and
     * freeze the server while it waited, so it is deliberately not used. A UUID
     * is accepted directly, which is the escape hatch for a player the server
     * has never seen.
     */
    private fun resolve(target: String): UUID? {
        server.getPlayerExact(target)?.let { return it.uniqueId }
        server.getOfflinePlayerIfCached(target)?.let { return it.uniqueId }
        return runCatching { UUID.fromString(target) }.getOrNull()
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>,
    ): List<String> = when (args.size) {
        1 -> listOf("editor").filter { it.startsWith(args[0], ignoreCase = true) }
        2 -> listOf("add", "remove", "list").filter { it.startsWith(args[1], ignoreCase = true) }
        3 -> when (args[1].lowercase()) {
            // Completing "remove" from the editor list rather than from online
            // players: the person being removed may well be offline.
            "remove" -> editors.load()
                .mapNotNull { server.getOfflinePlayer(it).name }
                .filter { it.startsWith(args[2], ignoreCase = true) }

            "add" -> server.onlinePlayers
                .map { it.name }
                .filter { it.startsWith(args[2], ignoreCase = true) }

            else -> emptyList()
        }

        else -> emptyList()
    }

    private fun info(text: String) = Component.text(text, NamedTextColor.GRAY)

    private fun success(text: String) =
        Component.text(text, NamedTextColor.GREEN, TextDecoration.BOLD)

    private fun problem(text: String) = Component.text(text, NamedTextColor.RED)
}

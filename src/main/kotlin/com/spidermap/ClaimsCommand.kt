package com.spidermap

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

/**
 * The `/claims` command, dispatching to its subcommands.
 *
 * Bukkit allows one executor per command, so this exists purely to route
 * `editor` and `weblogin` to the classes that own them. Permissions are checked
 * per subcommand rather than on the command as a whole: managing editors is an
 * operator's job, but *being* an editor is deliberately independent of op
 * (SDLC §2), so a non-op editor must still be able to run `weblogin`.
 */
class ClaimsCommand(
    private val editorCommand: EditorCommand,
    private val webLoginCommand: WebLoginCommand,
) : CommandExecutor, TabCompleter {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>,
    ): Boolean = when (args.firstOrNull()?.lowercase()) {
        "editor" -> {
            if (sender.hasPermission(MANAGE_EDITORS)) {
                editorCommand.handle(sender, args.drop(1))
            } else {
                sender.sendMessage(
                    Component.text("You don't have permission to manage map editors.", NamedTextColor.RED),
                )
                true
            }
        }

        // No permission gate: the command itself tells a non-editor they aren't
        // one, which is more useful than Bukkit's generic refusal.
        "weblogin" -> webLoginCommand.handle(sender)

        // false makes Bukkit print the usage line from plugin.yml.
        else -> false
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>,
    ): List<String> {
        if (args.size == 1) {
            return buildList {
                if (sender.hasPermission(MANAGE_EDITORS)) add("editor")
                add("weblogin")
            }.filter { it.startsWith(args[0], ignoreCase = true) }
        }

        if (args.firstOrNull()?.equals("editor", ignoreCase = true) == true &&
            sender.hasPermission(MANAGE_EDITORS)
        ) {
            return editorCommand.complete(args.drop(1))
        }

        return emptyList()
    }

    private companion object {
        const val MANAGE_EDITORS = "spidermap.editors.manage"
    }
}

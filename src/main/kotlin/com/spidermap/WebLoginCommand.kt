package com.spidermap

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.time.Duration

/**
 * `/claims weblogin` — hands the player a code to type into the web UI.
 *
 * This command is the whole reason the browser never has to be trusted with an
 * identity (SDLC §2). It runs server-side, where the player's UUID is already
 * Mojang-verified, so the code is issued *to a known player*. The browser sends
 * back only the code; the server resolves it to the UUID itself.
 */
class WebLoginCommand(
    private val codes: LoginCodeStore,
    private val editors: EditorStore,
    private val publicUrl: String,
    private val codeLifetime: Duration,
) {

    fun handle(sender: CommandSender): Boolean {
        // A console operator has no UUID, so there is no identity to bind a
        // code to — the flow is meaningless from the console.
        val player = sender as? Player
        if (player == null) {
            sender.sendMessage(
                Component.text("/claims weblogin has to be run by a player.", NamedTextColor.RED),
            )
            return true
        }

        // Non-editors could be issued a session, but it would be able to do
        // nothing — every write endpoint checks editor status. Saying so is
        // more useful than handing over a code that silently fails later.
        if (!editors.isEditor(player.uniqueId)) {
            player.sendMessage(
                Component.text("You are not a map editor.", NamedTextColor.RED),
            )
            player.sendMessage(
                Component.text("An operator can add you with ", NamedTextColor.GRAY)
                    .append(Component.text("/claims editor add ${player.name}", NamedTextColor.WHITE)),
            )
            return true
        }

        val code = codes.issue(player.uniqueId)
        val minutes = codeLifetime.toMinutes()

        player.sendMessage(Component.empty())
        player.sendMessage(
            Component.text("  Web login code  ", NamedTextColor.GRAY)
                .append(
                    Component.text(code, NamedTextColor.AQUA, TextDecoration.BOLD)
                        // Saves retyping it into the browser, and mistyping a
                        // digit is the most likely way this flow goes wrong.
                        .clickEvent(ClickEvent.copyToClipboard(code))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to copy"))),
                ),
        )
        player.sendMessage(
            Component.text("  Open ", NamedTextColor.GRAY)
                .append(
                    Component.text(publicUrl, NamedTextColor.GREEN, TextDecoration.UNDERLINED)
                        // The client shows its own "are you sure" prompt before
                        // following this, which is the client's call, not ours.
                        .clickEvent(ClickEvent.openUrl(publicUrl))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to open the map"))),
                ),
        )
        player.sendMessage(
            Component.text(
                "  Expires in $minutes min · single use",
                NamedTextColor.DARK_GRAY,
            ),
        )
        player.sendMessage(Component.empty())

        return true
    }
}

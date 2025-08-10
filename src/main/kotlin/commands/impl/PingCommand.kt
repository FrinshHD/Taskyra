package de.frinshy.commands.impl

import de.frinshy.commands.Command
import de.frinshy.commands.SlashCommand
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent

@SlashCommand(name = "ping", description = "Check if the bot is responsive")
class PingCommand : Command {
    override suspend fun execute(event: ChatInputCommandInteractionCreateEvent) {
        val interaction = event.interaction
        val deferredResponse = interaction.deferEphemeralResponse()

        val startTime = System.currentTimeMillis()

        deferredResponse.respond {
            content = "🏓 Pong! Bot is responsive. Response time: ${System.currentTimeMillis() - startTime}ms"
        }
    }
}

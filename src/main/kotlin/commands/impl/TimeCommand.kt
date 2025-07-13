package de.frinshy.commands.impl

import de.frinshy.commands.Command
import de.frinshy.commands.SlashCommand
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@SlashCommand(name = "time", description = "Shows the current server time")
class TimeCommand : Command {
    override suspend fun execute(event: ChatInputCommandInteractionCreateEvent) {
        val currentTime = Clock.System.now().toLocalDateTime(TimeZone.UTC)

        event.interaction.deferPublicResponse().respond {
            content = "🕐 Current server time: $currentTime UTC"
        }
    }
}

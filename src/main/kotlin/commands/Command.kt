package de.frinshy.commands

import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.ChatInputCreateBuilder

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class SlashCommand(
    val name: String,
    val description: String
)

interface Command {
    suspend fun execute(event: ChatInputCommandInteractionCreateEvent)

    fun defineOptions(builder: ChatInputCreateBuilder) {}
}

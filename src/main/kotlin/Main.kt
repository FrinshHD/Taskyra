package de.frinshy

import commands.impl.TaskManager
import de.frinshy.Main.Companion.bot
import de.frinshy.commands.CommandHandler
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.core.event.interaction.ComponentInteractionCreateEvent
import dev.kord.core.event.interaction.ModalSubmitInteractionCreateEvent
import dev.kord.core.on
import io.github.cdimascio.dotenv.dotenv
import kotlinx.datetime.Clock
import utils.ButtonRegistry
import utils.handleButtonClick

object BotEventHandler {
    fun registerEvents() {
        bot.on<ReadyEvent> { handleReady(this) }
        bot.on<ChatInputCommandInteractionCreateEvent> { handleChatInputCommand(this) }
        bot.on<ComponentInteractionCreateEvent> { handleComponentInteraction(this) }
    }

    private suspend fun handleReady(event: ReadyEvent) {
        println("✅ Logged in as ${event.self.username}")

        CommandHandler.registerCommands()
        println("🔧 Command registration complete!")
    }

    private suspend fun handleChatInputCommand(event: ChatInputCommandInteractionCreateEvent) {
        CommandHandler.handleCommand(event)
    }

    private suspend fun handleComponentInteraction(event: ComponentInteractionCreateEvent) {
        try {
            handleButtonClick(event).also { hasWorked ->
                if (!hasWorked) println("⚠️ No handler found for button click: ${event.interaction.componentId}")
            }

        } catch (e: Exception) {
            println("❌ Error executing command '${event.interaction.componentId}': ${e.message}")
        }
    }

    // Modal Handlers
    suspend fun handleAssignUserModal(event: ModalSubmitInteractionCreateEvent, bot: Kord) {
        val interaction = event.interaction
        val taskId = interaction.modalId.removePrefix("assign-user-modal-")
        val userInput = interaction.textInputs["user-id"]?.value?.trim()
        if (userInput.isNullOrBlank()) {
            interaction.respondEphemeral { content = "❌ Please provide a valid user ID, mention, or name." }
            return
        }
        val userId = if (userInput.startsWith("<@") && userInput.endsWith(">")) {
            userInput.removePrefix("<@").removeSuffix(">").removePrefix("!")
        } else userInput
        val task = TaskManager.getTaskById(taskId)
        if (task == null) {
            interaction.respondEphemeral { content = "❌ Task not found." }
            return
        }
        val success = TaskManager.assignUserToTask(taskId, userId)
        val displayName = if (userId.matches(Regex("\\d+"))) "<@$userId>" else "\"$userId\""
        if (success) {
            TaskManager.getTaskById(taskId)?.let { TaskManager.updateTaskEmbed(it) }
            interaction.respondEphemeral { content = "✅ Successfully assigned $displayName to task \"${task.title}\"!" }
        } else {
            interaction.respondEphemeral { content = "⚠️ $displayName is already assigned to task \"${task.title}\"." }
        }
    }

    suspend fun handleEditTaskModal(event: ModalSubmitInteractionCreateEvent, bot: Kord) {
        val interaction = event.interaction
        val taskId = interaction.modalId.removePrefix("edit-task-modal-")
        val newTitle = interaction.textInputs["title"]?.value?.trim()
        val newDescription = interaction.textInputs["description"]?.value?.trim()
        if (newTitle.isNullOrBlank() || newDescription.isNullOrBlank()) {
            interaction.respondEphemeral { content = "❌ Please provide both title and description." }
            return
        }
        val task = TaskManager.getTaskById(taskId)
        if (task == null) {
            interaction.respondEphemeral { content = "❌ Task not found." }
            return
        }
        TaskManager.updateTask(taskId, newTitle, newDescription)
        TaskManager.getTaskById(taskId)?.let { TaskManager.updateTaskEmbed(it) }
        interaction.respondEphemeral { content = "✅ Task \"${newTitle}\" has been updated successfully!" }
    }
}

class Main() {

    companion object {
        lateinit var bot: Kord
            private set
    }

    suspend fun main() {
        val dotenv = dotenv()
        val token = dotenv["BOT_TOKEN"] ?: error("DISCORD_TOKEN not found in .env")

        bot = Kord(token)

        BotEventHandler.registerEvents()
        ButtonRegistry.registerButtons()
        run()
    }

    suspend fun run() {
        bot.on<ModalSubmitInteractionCreateEvent> {
            val interaction = this.interaction
            try {
                val modalId = interaction.modalId
                val interactionAge = Clock.System.now() - interaction.id.timestamp
                if (interactionAge.inWholeMinutes > 14) {
                    println("⚠️ Ignoring expired modal interaction: $modalId")
                    return@on
                }
                when {
                    modalId.startsWith("assign-user-modal-") -> BotEventHandler.handleAssignUserModal(this, bot)
                    modalId.startsWith("edit-task-modal-") -> BotEventHandler.handleEditTaskModal(this, bot)
                    else -> interaction.respondEphemeral { content = "❌ Unknown modal submission." }
                }
            } catch (e: Exception) {
                println("❌ Error handling modal submission: ${e.message}")
                e.printStackTrace()
                interaction.respondEphemeral { content = "❌ An error occurred while processing your request." }
            }
        }
        bot.login()
    }
}

suspend fun main() {
    Main().main()
}

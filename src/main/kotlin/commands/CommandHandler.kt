package de.frinshy.commands

import de.frinshy.Main.Companion.bot
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance

object CommandHandler {
    private val commands = mutableMapOf<String, Command>()

    suspend fun registerCommands() {
        println("🔍 Automatically discovering commands...")

        try {
            val reflections = Reflections("de.frinshy.commands", Scanners.TypesAnnotated)
            val commandClasses = reflections.getTypesAnnotatedWith(SlashCommand::class.java)

            // Single scan: collect commands to register and prepare instances
            val newCommands = mutableMapOf<String, Pair<Command, SlashCommand>>()

            commandClasses.forEach { clazz ->
                try {
                    if (!Command::class.java.isAssignableFrom(clazz)) {
                        println("⚠️  ${clazz.simpleName} is annotated with @SlashCommand but doesn't implement Command")
                        return@forEach
                    }

                    @Suppress("UNCHECKED_CAST")
                    val kotlinClass = clazz.kotlin as KClass<out Command>
                    val annotation = clazz.getAnnotation(SlashCommand::class.java)
                    val instance = kotlinClass.createInstance()

                    newCommands[annotation.name] = instance to annotation

                } catch (e: Exception) {
                    println("❌ Failed to prepare ${clazz.simpleName}: ${e.message}")
                }
            }

            // Remove outdated Discord commands
            println("🧹 Checking for outdated commands...")
            bot.rest.interaction.getGlobalApplicationCommands(bot.selfId).toList().forEach { existingCommand ->
                if (existingCommand.name !in newCommands) {
                    bot.rest.interaction.deleteGlobalApplicationCommand(bot.selfId, existingCommand.id)
                    println("🗑️ Removed outdated: ${existingCommand.name}")
                } else {
                    println("♻️ Updating: ${existingCommand.name}")
                }
            }

            // Register all commands
            newCommands.forEach { (name, commandData) ->
                try {
                    val (instance, annotation) = commandData

                    bot.createGlobalChatInputCommand(annotation.name, annotation.description) {
                        instance.defineOptions(this)
                    }

                    commands[name] = instance
                    println("✅ Registered: $name")

                } catch (e: Exception) {
                    println("❌ Failed to register $name: ${e.message}")
                }
            }

            println("🎉 Registration complete! Active commands: ${commands.size}")

        } catch (e: Exception) {
            println("❌ Error during command discovery: ${e.message}")
            e.printStackTrace()
        }
    }

    suspend fun handleCommand(event: ChatInputCommandInteractionCreateEvent) {
        val commandName = event.interaction.invokedCommandName
        val userId = event.interaction.user.id
        val interactionId = event.interaction.id

        println("🎯 Handling command: '$commandName' from user: $userId, interaction: $interactionId")

        val command = commands[commandName]

        if (command != null) {
            try {
                command.execute(event)
            } catch (e: Exception) {
                println("❌ Error executing command '$commandName': ${e.message}")
                e.printStackTrace()

                // Check if this is an interaction already acknowledged error
                if (e.message?.contains("already been acknowledged") == true ||
                    e.message?.contains("Unknown interaction") == true
                ) {
                    println("⚠️ Command interaction already handled or expired")
                    return
                }

                try {
                    event.interaction.deferEphemeralResponse()
                } catch (responseError: Exception) {
                    println("⚠️ Could not send error response to user: ${responseError.message}")
                }
            }
        } else {
            println("⚠️ Unknown command: $commandName")
        }
    }
}

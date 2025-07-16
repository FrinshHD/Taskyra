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

            for (clazz in commandClasses) {
                try {
                    if (!Command::class.java.isAssignableFrom(clazz)) {
                        println("⚠️  Class ${clazz.simpleName} is annotated with @SlashCommand but doesn't implement Command interface")
                        continue
                    }

                    val kotlinClass = clazz.kotlin as KClass<out Command>
                    val annotation = clazz.getAnnotation(SlashCommand::class.java)

                    val commandInstance = kotlinClass.createInstance()

                    bot.createGlobalChatInputCommand(annotation.name, annotation.description) {
                        commandInstance.defineOptions(this)
                    }

                    commands[annotation.name] = commandInstance

                    println("✅ Auto-registered command: ${annotation.name} (${clazz.simpleName})")

                } catch (e: Exception) {
                    println("❌ Failed to register command ${clazz.simpleName}: ${e.message}")
                }
            }

            println("🎉 Command auto-registration complete! Registered ${commands.size} commands.")

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

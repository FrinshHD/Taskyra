package de.frinshy.commands

import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance

class CommandHandler(private val bot: Kord) {
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
        val command = commands[commandName]

        if (command != null) {
            try {
                command.execute(event)
            } catch (e: Exception) {
                println("❌ Error executing command '$commandName': ${e.message}")
                e.printStackTrace()
                try {
                    event.interaction.deferEphemeralResponse().respond {
                        content = "❌ An error occurred while executing the command."
                    }
                } catch (responseError: Exception) {
                    println("⚠️  Could not send error response to user: ${responseError.message}")
                }
            }
        } else {
            println("⚠️  Unknown command: $commandName")
        }
    }
}

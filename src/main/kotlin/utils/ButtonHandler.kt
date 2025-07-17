package utils

import commands.impl.Task
import commands.impl.TaskManager
import dev.kord.common.entity.ButtonStyle
import dev.kord.core.event.interaction.ComponentInteractionCreateEvent
import dev.kord.rest.builder.component.ActionRowBuilder
import org.reflections.Reflections

enum class ButtonType {
    START_TASK,
    COMPLETE_TASK,
    SELECT_USERS,
    DELETE_TASK,
    PAUSE_TASK,
    ASSIGN_ME,
    REOPEN_TASK,
    EDIT_TASK
}

abstract class TaskButton(
    open val type: ButtonType,
    open val label: String,
    open val style: ButtonStyle
) {
    open val id: String get() = type.name.lowercase().replace("_", "-")

    open suspend fun handle(task: Task, event: ComponentInteractionCreateEvent) {
        event.interaction.deferPublicMessageUpdate()
        handle(task, event.interaction.data.guildId?.value.toString() ?: return)
    }

    open suspend fun handle(task: Task, guildId: String) {
        handle(task)
    }

    open suspend fun handle(task: Task) {}

    fun addToActionRow(taskId: String, row: ActionRowBuilder) {
        row.interactionButton(style, "$id-$taskId") { label = this@TaskButton.label }
    }
}

object ButtonRegistry {
    private var _buttons: List<TaskButton>? = null

    fun registerButtons() {
        val reflections = Reflections("utils", org.reflections.scanners.SubTypesScanner(false))
        val buttonClasses: Set<Class<out TaskButton>> =
            reflections.getSubTypesOf(TaskButton::class.java)
        _buttons = buttonClasses.mapNotNull {
            try {
                it.getDeclaredConstructor().newInstance()
            } catch (e: Exception) {
                null
            }
        }
    }

    val buttons: List<TaskButton>
        get() = _buttons ?: emptyList()

    fun getButtonByType(type: ButtonType): TaskButton? =
        buttons.find { type == it.type }
}

suspend fun handleButtonClick(event: ComponentInteractionCreateEvent): Boolean {
    val componentId = event.interaction.componentId

    val button: TaskButton? = ButtonRegistry.buttons
        .filter { componentId.startsWith(it.id + "-") }
        .maxByOrNull { it.id.length }

    if (button == null) {
        return false
    }

    val taskId = componentId.removePrefix(button.id + "-")
    val task = TaskManager.getTaskById(taskId) ?: return false

    button.handle(task, event)

    return true
}

package utils

import commands.impl.Task
import commands.impl.TaskManager
import commands.impl.TaskState
import config.MessageHelper
import de.frinshy.config.BotConfig
import de.frinshy.utils.updateChannelSummary
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.TextInputStyle
import dev.kord.core.behavior.interaction.modal
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.event.interaction.ComponentInteractionCreateEvent

class StartTaskTaskButton : TaskButton(
    type = ButtonType.START_TASK,
    label = "Start Task",
    style = ButtonStyle.Primary
) {
    override suspend fun handle(task: Task) {
        TaskManager.moveTaskToCategory(task.id, TaskState.IN_PROGRESS)
        updateChannelSummary(TaskState.PENDING)
        updateChannelSummary(TaskState.IN_PROGRESS)
    }
}

class CompleteTaskTaskButton : TaskButton(
    type = ButtonType.COMPLETE_TASK,
    label = "Complete",
    style = ButtonStyle.Success
) {
    override suspend fun handle(task: Task) {
        TaskManager.moveTaskToCategory(task.id, TaskState.COMPLETED)
        updateChannelSummary(TaskState.PENDING)
        updateChannelSummary(TaskState.IN_PROGRESS)
        updateChannelSummary(TaskState.COMPLETED)
    }
}

class PauseTaskTaskButton : TaskButton(
    type = ButtonType.PAUSE_TASK,
    label = "Pause",
    style = ButtonStyle.Secondary
) {
    override suspend fun handle(task: Task) {
        TaskManager.moveTaskToCategory(task.id, TaskState.PENDING)
        updateChannelSummary(TaskState.PENDING)
        updateChannelSummary(TaskState.IN_PROGRESS)
    }
}

class ReopenTaskTaskButton : TaskButton(
    type = ButtonType.REOPEN_TASK,
    label = "Reopen",
    style = ButtonStyle.Secondary
) {
    override suspend fun handle(task: Task) {
        TaskManager.moveTaskToCategory(task.id, TaskState.PENDING)
        updateChannelSummary(TaskState.PENDING)
        updateChannelSummary(TaskState.COMPLETED)
    }
}

class DeleteTaskTaskButton : TaskButton(
    type = ButtonType.DELETE_TASK,
    label = "Delete",
    style = ButtonStyle.Danger
) {
    override suspend fun handle(task: Task) {
        val task = TaskManager.getTaskById(task.id) ?: return

        when (task.state) {
            TaskState.PENDING -> BotConfig.instance.pendingTasksChannelId
            TaskState.IN_PROGRESS -> BotConfig.instance.inProgressTasksChannelId
            TaskState.COMPLETED -> BotConfig.instance.completedTasksChannelId
        }?.let { channelId ->
            val message = MessageHelper.getMessage(channelId, task.messageId) ?: return@let
            message.delete()
        }

        TaskManager.removeTask(task.id)

        updateChannelSummary(task.state)
    }
}

class SelectUsersTaskButton : TaskButton(
    type = ButtonType.SELECT_USERS,
    label = "Select Users",
    style = ButtonStyle.Secondary
) {
    override suspend fun handle(task: Task, event: ComponentInteractionCreateEvent) {
        event.interaction.modal("Assign User to Task", "assign-user-modal-${task.id}") {
            actionRow {
                textInput(TextInputStyle.Short, "user-id", "User ID, Mention, or Name") {
                    placeholder = "@username, user ID, or any text"
                    required = true
                    allowedLength = 1..100
                }
            }
        }
    }
}

class AssignMeTaskButton : TaskButton(
    type = ButtonType.ASSIGN_ME,
    label = "Assign to Me",
    style = ButtonStyle.Secondary
) {
    override suspend fun handle(task: Task, event: ComponentInteractionCreateEvent) {
        val userId = event.interaction.user.id.toString()

        val isCurrentlyAssigned = task.assignedUsers.contains(userId)

        if (isCurrentlyAssigned) {
            TaskManager.unassignUserFromTask(task.id, userId)
            TaskManager.updateTaskEmbed(task)

            event.interaction.respondEphemeral {
                content = "✅ Successfully unassigned yourself from task \"${task.title}\"!"
            }

            return
        }

        TaskManager.assignUserToTask(task.id, userId)
        TaskManager.updateTaskEmbed(task)

        event.interaction.respondEphemeral {
            content = "✅ Successfully assigned yourself to task \"${task.title}\"!"
        }
    }
}

class EditTaskButton : TaskButton(
    type = ButtonType.EDIT_TASK,
    label = "Edit Task",
    style = ButtonStyle.Secondary
) {
    override suspend fun handle(task: Task, event: ComponentInteractionCreateEvent) {
        event.interaction.modal("Edit Task", "edit-task-modal-${task.id}") {
            actionRow {
                textInput(TextInputStyle.Short, "title", "Task Title") {
                    this.placeholder = "Enter task title"
                    this.value = task.title
                    this.required = true
                }
            }
            actionRow {
                textInput(
                    TextInputStyle.Paragraph,
                    "description",
                    "Task Description"
                ) {
                    this.placeholder = "Enter task description"
                    this.value = task.description
                    this.required = true
                }
            }
        }
    }
}

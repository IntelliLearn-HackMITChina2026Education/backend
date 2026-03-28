package net.sfls.lh.intellilearn.orm

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.dao.id.UIntIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock


enum class TaskType {
    ANALYZE_PAPER,
    ANALYZE_GROUP,
    ANALYZE_STUDENT,
    ANALYZE_SINGLE,
}

enum class TaskStatus {
    PENDING,
    PROCESSING,
    SUCCESS,
    FAILED
}

@Serializable
data class Task(
    val id: UInt,
    val taskType: TaskType,
    val payload: String,
    val status: TaskStatus,
    val attempts: Int,
    val result: String?,
    val error: String?
)

@Serializable
data class AnalyzePaperPayload(val examId: UInt)

@Serializable
data class AnalyzeGroupPayload(val examId: UInt, val groupId: UInt)

@Serializable
data class AnalyzeStudentPayload(val examId: UInt, val student: String)

@Serializable
data class AnalyzeSinglePayload(val content: String)

object TaskTable : UIntIdTable("ollama_tasks") {
    val taskType = enumeration<TaskType>("task_type")
    val payload = text("payload")
    val status = enumeration<TaskStatus>("status").default(TaskStatus.PENDING).index()
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
    val attempts = integer("attempts").default(0)
    val result = text("result").nullable()
    val error = text("error").nullable()
    val startedAt = datetime("started_at").nullable()
    val finishedAt = datetime("finished_at").nullable()
}

class TaskQueueService(private val database: Database) {

    suspend fun enqueue(taskType: TaskType, payload: String) = transaction(database) {
        TaskTable.insertAndGetId {
            it[TaskTable.taskType] = taskType
            it[TaskTable.payload] = payload
        }
    }

    suspend fun dequeue(): Task? = transaction(database) {
        val row = TaskTable
            .selectAll()
            .where { TaskTable.status eq TaskStatus.PENDING }
            .orderBy(TaskTable.createdAt)
            .forUpdate()
            .limit(1)
            .singleOrNull()

        row?.let {
            TaskTable.update({ TaskTable.id eq it[TaskTable.id] }) { table ->
                table[status] = TaskStatus.PROCESSING
                table[startedAt] = Clock.System.now()
                    .toLocalDateTime(TimeZone.UTC)
                table[attempts] = it[attempts] + 1
            }
            Task(
                id = it[TaskTable.id].value,
                taskType = it[TaskTable.taskType],
                payload = it[TaskTable.payload],
                status = TaskStatus.PROCESSING,
                attempts = it[TaskTable.attempts] + 1,
                result = it[TaskTable.result],
                error = it[TaskTable.error]
            )
        }
    }

    suspend fun markSuccess(taskId: UInt, result: String) = transaction(database) {
        TaskTable.update({ TaskTable.id eq taskId }) {
            it[status] = TaskStatus.SUCCESS
            it[finishedAt] = Clock.System.now()
                .toLocalDateTime(TimeZone.UTC)
            it[this.result] = result
        }
    }

    suspend fun markFailed(taskId: UInt, errorMsg: String, maxAttempts: Int = 3) = transaction(database) {
        val task = TaskTable.selectAll().where { TaskTable.id eq taskId }.single()
        val attempts = task[TaskTable.attempts]

        if (attempts >= maxAttempts) {
            TaskTable.update({ TaskTable.id eq taskId }) {
                it[status] = TaskStatus.FAILED
                it[error] = errorMsg
                it[finishedAt] = Clock.System.now()
                    .toLocalDateTime(TimeZone.UTC)
            }
        } else {
            TaskTable.update({ TaskTable.id eq taskId }) {
                it[status] = TaskStatus.PENDING
                it[error] = errorMsg
                it[startedAt] = null
            }
        }
    }
}
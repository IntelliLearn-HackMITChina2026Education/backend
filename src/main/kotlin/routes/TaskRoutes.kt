package net.sfls.lh.intellilearn.routes

import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.sfls.lh.intellilearn.orm.*
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

@Serializable
data class ActiveExamResponse(
    val id: Int,
    val name: String,
    val status: String   // "processing" | "pending"
)

@Serializable
data class ExamTaskResponse(
    val name: String,
    val desc: String,
    val status: String   // "processing" | "pending" | "completed" | "failed"
)

fun Route.processingRoutes() {
    route("/processing") {
        // 获取所有有未完成任务的考试
        get("/exams") {
            val activeExams = transaction {
                // 查询所有 PENDING 或 PROCESSING 状态的 ANALYZE_PAPER 任务
                val tasks = TaskTable
                    .selectAll()
                    .where {
                        (TaskTable.taskType eq TaskType.ANALYZE_PAPER) and
                                (TaskTable.status inList listOf(TaskStatus.PENDING, TaskStatus.PROCESSING))
                    }
                    .toList()

                tasks.mapNotNull { task ->
                    val payload =
                        Json.decodeFromString<AnalyzePaperPayload>(task[TaskTable.payload])
                    val exam = ExamTable
                        .selectAll()
                        .where { ExamTable.id eq payload.examId }
                        .firstOrNull()
                    exam?.let {
                        ActiveExamResponse(
                            id = payload.examId.toInt(),
                            name = it[ExamTable.name],
                            status = when (task[TaskTable.status]) {
                                TaskStatus.PENDING -> "pending"
                                TaskStatus.PROCESSING -> "processing"
                                else -> "processing" // fallback
                            }
                        )
                    }
                }
            }
            call.respond(activeExams)
        }

        // 获取指定考试下的所有子任务进度
        get("/exam/{examId}/tasks") {
            val examId = call.parameters["examId"]?.toUIntOrNull()
                ?: throw IllegalArgumentException("Invalid examId")

            val tasks = transaction {
                // 1. 查找该考试的 ANALYZE_PAPER 父任务
                val parentTask = TaskTable
                    .selectAll()
                    .where {
                        (TaskTable.taskType eq TaskType.ANALYZE_PAPER) and
                                (TaskTable.payload eq "{\"examId\":$examId}")
                    }
                    .firstOrNull()

                if (parentTask == null) {
                    return@transaction emptyList()
                }

                // 2. 从父任务的结果中获取子任务ID列表（逗号分隔）
                val childIds = parentTask[TaskTable.result]?.split(",")?.mapNotNull { it.toUIntOrNull() } ?: emptyList()

                // 3. 查询这些子任务
                val childTasks = if (childIds.isNotEmpty()) {
                    TaskTable
                        .selectAll()
                        .where { TaskTable.id inList childIds }
                        .toList()
                } else {
                    emptyList()
                }

                // 4. 构建前端需要的列表
                val result = mutableListOf<ExamTaskResponse>()

                // 添加父任务本身（可作为整体分析进度）
                result.add(
                    ExamTaskResponse(
                        name = "整体试卷分析",
                        desc = "正在分析整张试卷",
                        status = when (parentTask[TaskTable.status]) {
                            TaskStatus.PENDING -> "pending"
                            TaskStatus.PROCESSING -> "processing"
                            TaskStatus.SUCCESS -> "completed"
                            TaskStatus.FAILED -> "failed"
                        }
                    )
                )

                // 添加每个子任务（单题分析）
                childTasks.forEachIndexed { index, task ->
                    val payload = Json.decodeFromString<AnalyzeSinglePayload>(task[TaskTable.payload])
                    result.add(
                        ExamTaskResponse(
                            name = "第 ${index + 1} 题分析",
                            desc = payload.content.take(50) + "...", // 截取题干前50字符
                            status = when (task[TaskTable.status]) {
                                TaskStatus.PENDING -> "pending"
                                TaskStatus.PROCESSING -> "processing"
                                TaskStatus.SUCCESS -> "completed"
                                TaskStatus.FAILED -> "failed"
                            }
                        )
                    )
                }

                result
            }
            call.respond(tasks)
        }
    }
}
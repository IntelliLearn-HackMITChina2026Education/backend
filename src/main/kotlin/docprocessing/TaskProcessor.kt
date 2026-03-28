package net.sfls.lh.intellilearn.docprocessing

import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import net.sfls.lh.intellilearn.orm.*
import kotlin.time.Duration.Companion.milliseconds

class TaskProcessor(
    private val queueService: TaskQueueService,
    private val concurrency: Int = 2,
    private val pollInterval: Long = 1000
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        repeat(concurrency) {
            scope.launch {
                while (true) {
                    try {
                        val task = queueService.dequeue()
                        if (task != null) {
                            processTask(task)
                        } else {
                            delay(pollInterval.milliseconds)
                        }
                    } catch (e: Exception) {
                        // 记录错误，继续运行
                        e.printStackTrace()
                        delay(pollInterval.milliseconds)
                    }
                }
            }
        }
    }

    private suspend fun processTask(task: Task) {
        try {
            val result = when (task.taskType) {
                TaskType.ANALYZE_PAPER -> {
                    val payload = Json.decodeFromString<AnalyzePaperPayload>(task.payload)
                    val questions = Analyzer.separateProblems(payload.examId).split("---")
                    val ids = mutableListOf<UInt>()
                    questions.forEach { questionText ->
                        queueService.enqueue(
                            TaskType.ANALYZE_SINGLE,
                            Json.encodeToString(AnalyzeSinglePayload(questionText))
                        ).let { ids.addLast(it.value) }
                    }
                    ids.joinToString(separator = ",")
                }

                TaskType.ANALYZE_GROUP -> {
                    val payload = Json.decodeFromString<AnalyzeGroupPayload>(task.payload)
                    Analyzer.analyzeGroup(payload.examId, payload.groupId)
                }

                TaskType.ANALYZE_STUDENT -> {
                    val payload = Json.decodeFromString<AnalyzeStudentPayload>(task.payload)
                    Analyzer.analyzeStudent(payload.examId, payload.student)
                }

                TaskType.ANALYZE_SINGLE -> {
                    val payload = Json.decodeFromString<AnalyzeSinglePayload>(task.payload)
                    Analyzer.analyzeSingleProblem(payload.content)
                }
            }
            queueService.markSuccess(task.id, result)
        } catch (e: Exception) {
            queueService.markFailed(task.id, e.message ?: "Unknown error")
        }
    }
}
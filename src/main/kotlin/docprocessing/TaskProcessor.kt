package net.sfls.lh.intellilearn.docprocessing

// TaskProcessor.kt
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
                    val questions = getQuestionsForExam(payload.examId)
                    Analyzer.analyzePaper(payload.examId, questions)
                }

                TaskType.ANALYZE_GROUP -> {
                    val payload = Json.decodeFromString<AnalyzeGroupPayload>(task.payload)
                    val groupData = collectGroupData(payload.examId, payload.groupId)
                    Analyzer.analyzeGroup(groupData)
                }

                TaskType.ANALYZE_STUDENT -> {
                    val payload = Json.decodeFromString<AnalyzeStudentPayload>(task.payload)
                    val studentData = collectStudentData(payload.examId, payload.studentId)
                    Analyzer.analyzeStudent(studentData)
                }
            }
            storeResult(task, result)
            queueService.markSuccess(task.id, result)
        } catch (e: Exception) {
            queueService.markFailed(task.id, e.message ?: "Unknown error")
        }
    }

    private suspend fun getQuestionsForExam(examId: UInt): List<String> = TODO()
    private suspend fun collectGroupData(examId: UInt, groupId: UInt): String = TODO()
    private suspend fun collectStudentData(examId: UInt, studentId: UInt): String = TODO()
    private suspend fun storeResult(task: Task, result: String): Unit = TODO()
}
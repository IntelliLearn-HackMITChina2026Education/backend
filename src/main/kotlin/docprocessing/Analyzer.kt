package net.sfls.lh.intellilearn.docprocessing

import kotlinx.serialization.json.Json
import net.blophy.nova.kollama.KOllamaClient
import net.sfls.lh.intellilearn.orm.ExamTable
import net.sfls.lh.intellilearn.orm.TaskTable
import net.sfls.lh.intellilearn.uploadService
import net.sfls.lh.intellilearn.utils.convertToMarkdown
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object Analyzer {

    private val ollama = KOllamaClient()
    private val json = Json { encodeDefaults = false; prettyPrint = false }

    suspend fun separateProblems(examId: UInt): String {
        val fileId = transaction {
            return@transaction ExamTable
                .selectAll()
                .where { ExamTable.id eq examId }
                .first()[ExamTable.paper]
        }
        val file = uploadService.getFile(fileId)
        val fileContent = convertToMarkdown(file!!.first)
        val prompt = """
            请帮我分割该试卷上的每一道题目，要求包含题号及完整题干，选择题还要包含完整选项，不要改变任何文本内容，并将切割出来的题目以markdown格式输出，题目和题目之间用---分割
            下为试卷内容：
            $fileContent
        """.trimIndent()
        return ollama.generate("qwen3.5", prompt).response
    }

    suspend fun analyzeSingleProblem(content: String): String {
        val prompt = """
            分析该题目，依据教材归纳该题目的知识点，对归纳出来的知识点进行概括，如果该题目下只有一个问题，只用几个字概括题干考察的知识点即可，只输出概况内容，然后使用---分割，然后输出“本题考查...知识点，考验学生的...能力”，然后使用---分割，如果该题目下不只有一个问题，每个小题分别完成一下操作：用几个字概括题干考察的知识点，只输出概况内容，然后使用---分割，然后输出“本题考查...知识点，考验学生的...能力”，然后使用---分割
            下为题目内容：
            $content
        """.trimIndent()
        return ollama.generate("qwen3.5", prompt).response
    }

    suspend fun analyzeGroup(groupData: String): String {
        val prompt = """
            分析以下小组的学习数据，给出整体学习情况评价和针对性学习建议。
            数据：$groupData
            返回 JSON 格式：{"summary": "...", "suggestions": ["建议1","建议2"]}
        """.trimIndent()
        return ollama.generate("qwen3.5", prompt).response
    }

    suspend fun analyzeStudent(examId: UInt, student: String): String {
        val fileId = transaction {
            return@transaction ExamTable
                .selectAll()
                .where { ExamTable.id eq examId }
                .first()[ExamTable.grade]
        }
        val examAna = transaction {
            TaskTable
                .selectAll()
                .where { TaskTable.id eq examId }
                .singleOrNull()
                ?.get(TaskTable.result)
                ?.split(",")
                ?.map { it.toUInt() }
                ?.let { ids ->
                    TaskTable
                        .selectAll()
                        .where { TaskTable.id inList ids }
                        .associate { it[TaskTable.id].value to it[TaskTable.result] }
                        .let { resultMap ->
                            ids
                                .filter { resultMap[it] != null }
                                .map { Pair(it, resultMap[it]) }
                        }
                } ?: emptyList()
        }
        val file = uploadService.getFile(fileId)
        val studentData = convertXlsToJson(file!!.first).find { it.name == student }!!
        val studentDataString = json.encodeToString(studentData)
        val problemDataString = json.encodeToString(examAna)
        val prompt = """
            分析以下学生的学习数据（成绩趋势和知识点掌握变化），给出个性化学习建议。
            $studentDataString
            试卷分析：
            $problemDataString
            返回 JSON 格式：{"trendAnalysis": "...", "knowledgeWeakness": ["知识点1","知识点2"], "suggestions": ["建议1","建议2"]}
        """.trimIndent()
        return ollama.generate("qwen3.5", prompt).response
    }
}
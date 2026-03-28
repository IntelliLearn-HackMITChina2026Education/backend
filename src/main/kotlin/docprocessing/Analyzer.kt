package net.sfls.lh.intellilearn.docprocessing

import net.blophy.nova.kollama.KOllamaClient

object Analyzer {

    private val ollama = KOllamaClient()

    suspend fun analyzePaper(examId: UInt, questions: List<String>): String {
        val prompt = """
            你是一个教育分析专家。请分析以下考试题目，为每道题提取出对应的知识点（用简洁的术语，如“二次函数”、“定语从句”等）。
            请以 JSON 格式返回，格式为 [{"questionIndex": 0, "knowledgePoints": ["点1","点2"]}, ...]。
            题目列表：
            ${questions.joinToString("\n") { it }}
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

    suspend fun analyzeStudent(studentData: String): String {
        val prompt = """
            分析以下学生的学习数据（成绩趋势和知识点掌握变化），给出个性化学习建议。
            数据：$studentData
            返回 JSON 格式：{"trendAnalysis": "...", "knowledgeWeakness": ["知识点1","知识点2"], "suggestions": ["建议1","建议2"]}
        """.trimIndent()
        return ollama.generate("qwen3.5", prompt).response
    }
}
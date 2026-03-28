package net.sfls.lh.intellilearn.orm

import org.jetbrains.exposed.v1.core.dao.id.UIntIdTable

enum class AnalyzedTypes {
    SINGLE,
    STUDENT,
    GROUP
}

object AnalyzedTable : UIntIdTable("analyzed") {
    val type = enumeration<AnalyzedTypes>("type")
    val exam = uinteger("exam")
    val student = text("student")
    val problem = uinteger("problem")
    val content = text("content")

    init {
        index(false, exam, student, problem)
    }
}
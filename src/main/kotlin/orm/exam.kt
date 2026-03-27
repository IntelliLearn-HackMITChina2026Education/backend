package net.sfls.lh.intellilearn.orm

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.UIntIdTable

@Serializable
data class NewExam(
    val examName: String,
    val subject: String,
    val examPaper: String,
    val gradeSheet: String,
    val targetClass: String
)

object ExamTable : UIntIdTable("exam") {
    val name = text("name")
    val subject = text("subject")
    val paper = text("paper")
    val grade = text("grade")
    val tclass = text("class").index()
}

@Serializable
data class Exam(
    val id: UInt,
    val name: String,
    val subject: String,
    val paper: String,
    val grade: String
)

fun ResultRow.toExam() = Exam(
    id = this[ExamTable.id].value,
    name = this[ExamTable.name],
    subject = this[ExamTable.subject],
    paper = this[ExamTable.paper],
    grade = this[ExamTable.grade]
)

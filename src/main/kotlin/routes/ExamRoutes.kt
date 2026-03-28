package net.sfls.lh.intellilearn.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.sfls.lh.intellilearn.orm.ExamTable
import net.sfls.lh.intellilearn.orm.NewExam
import net.sfls.lh.intellilearn.orm.toExam
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

fun Route.examRoutes() {
    route("/exam") {
        get("/list") {
            val target = call.request.queryParameters["targetClass"]
            val exams = transaction {
                val query = ExamTable.selectAll()
                if (target != null) {
                    query.where { ExamTable.tclass eq target }
                }
                return@transaction query.orderBy(ExamTable.id to SortOrder.DESC).map { it.toExam() }
            }
            call.respond(exams)
        }
        get("/{id}") {
            val id = call.parameters["id"]!!.toUInt()
            val exam = transaction {
                ExamTable.selectAll().where { ExamTable.id eq id }.firstOrNull()?.toExam()
            }
            if (exam == null) return@get call.respond(HttpStatusCode.NotFound, "未找到考试")
            call.respond(exam)
        }
        post("/new") {
            val new = call.receive<NewExam>()
            val id = transaction {
                return@transaction ExamTable.insertAndGetId {
                    it[tclass] = new.targetClass
                    it[name] = new.examName
                    it[subject] = new.subject
                    it[paper] = new.examPaper
                    it[grade] = new.gradeSheet
                }
            }.value
            //val payload = AnalyzePaperPayload(id)
            //val taskId = queueService.enqueue(TaskType.ANALYZE_PAPER, Json.encodeToString(payload)).value
            //call.respond(HttpStatusCode.Created, listOf(id, taskId))
            call.respond(HttpStatusCode.Created, id)
        }
    }
}
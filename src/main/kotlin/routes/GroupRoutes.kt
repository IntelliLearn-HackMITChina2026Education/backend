package net.sfls.lh.intellilearn.routes

import io.ktor.http.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.sfls.lh.intellilearn.NotFoundException
import net.sfls.lh.intellilearn.orm.*
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

fun Route.groupRoutes() {
    route("/groups") {
        get {
            val groups = transaction {
                GroupsTable.selectAll().map {
                    GroupResponse(
                        id = it[GroupsTable.id].value,
                        name = it[GroupsTable.name],
                        students = it[GroupsTable.members]
                    )
                }
            }.reversed()
            call.respond(groups)
        }

        get("/{groupId}") {
            val groupId = call.parameters["groupId"]?.toUIntOrNull()
                ?: throw BadRequestException("Invalid groupId")
            val group = transaction {
                GroupsTable.selectAll().where { GroupsTable.id eq groupId }.singleOrNull()
            } ?: throw NotFoundException("Group not found")

            val memberIds = transaction {
                GroupsTable
                    .selectAll()
                    .where { GroupsTable.id eq groupId }
                    .singleOrNull()?.get(GroupsTable.members) ?: emptyList()
            }

            call.respond(
                GroupResponse(
                    id = group[GroupsTable.id].value,
                    name = group[GroupsTable.name],
                    students = memberIds
                )
            )
        }

        get("/{groupId}/analysis") {
            val groupId = call.parameters["groupId"]?.toUIntOrNull() ?: throw BadRequestException("groupId required")
            val examId =
                call.request.queryParameters["examId"]?.toUIntOrNull() ?: throw BadRequestException("examId required")

            /*val studentIds = transaction {
                // 使用 crossJoin 绕过 Exposed 的外键检查，靠 where 实现 Join 逻辑
                (GroupsTable crossJoin UsersTable)
                    .select(UsersTable.id)
                    .where {
                        (GroupsTable.id eq groupId) and
                                // anyFrom 会生成 PostgreSQL 的 ANY() 语法
                                (UsersTable.id eq anyFrom(GroupsTable.members))
                    }
                    .map { it[UsersTable.id] }
            }

            val analysisData = transaction {
                AnalyzedTable
                    .selectAll()
                    .where {
                        (AnalyzedTable.exam eq examId) and
                        (AnalyzedTable.student inList studentIds.map { it.toString() }) and
                        (AnalyzedTable.type eq AnalyzedTypes.GROUP)
                    }
                    .single()[AnalyzedTable.content]
            }*/

            val analysisData = transaction {
                AnalyzedTable
                    .selectAll()
                    .where {
                        (AnalyzedTable.exam eq examId) and
                                (AnalyzedTable.student eq groupId.toString()) and
                                (AnalyzedTable.type eq AnalyzedTypes.GROUP)
                    }
                    .single()[AnalyzedTable.content]
            }

            call.respondText(analysisData, ContentType.Application.Json)
        }

        get("/{groupId}/trend") {
            val groupId = call.parameters["groupId"]?.toUIntOrNull() ?: throw BadRequestException("groupId required")
            val memberIds = transaction {
                GroupsTable
                    .selectAll()
                    .where { GroupsTable.id eq groupId }
                    .single()[GroupsTable.members]
            }
            val exams = transaction {
                ExamTable.selectAll().orderBy(ExamTable.id to SortOrder.DESC).toList()
            }

            val examScores = mutableMapOf<UInt, MutableList<Double>>()
            for (exam in exams) {
                val examId = exam[ExamTable.id].value
                val scores = memberIds.map { studentId ->
                    // TODO getStudentTotalScore(studentId, examId)
                    0.0
                }
                examScores[examId] = scores.toMutableList()
            }

            val trend = exams.map { exam ->
                val avg = examScores[exam[ExamTable.id].value]?.average() ?: 0.0
                GroupTrendResponse.TrendItem(exam = exam[ExamTable.name], avgScore = avg)
            }
            val currentAvg = trend.lastOrNull()?.avgScore ?: 0.0
            val previousAvg = trend.getOrNull(trend.size - 2)?.avgScore ?: currentAvg
            val change = currentAvg - previousAvg

            call.respond(
                GroupTrendResponse(
                    id = groupId,
                    name = transaction {
                        GroupsTable
                            .selectAll()
                            .where { GroupsTable.id eq groupId }
                            .single()[GroupsTable.name]
                    },
                    currentAvg = currentAvg,
                    change = change,
                    trend = trend
                )
            )
        }

        post("/{groupId}/members") {
            val groupId = call.parameters["groupId"]?.toUIntOrNull() ?: throw BadRequestException("groupId required")
            val body = call.receive<Map<String, Int>>()
            val studentId = body["studentId"] ?: throw BadRequestException("studentId required")

            transaction {
                // TODO
                /*GroupsTable.insert {
                    it[groupId] = groupId
                    it[studentId] = studentId
                }*/
            }
            call.respond(status = HttpStatusCode.Created, Unit)
        }

        delete("/{groupId}/members/{studentId}") {
            val groupId = call.parameters["groupId"]?.toUIntOrNull() ?: throw BadRequestException("groupId required")
            val studentId =
                call.parameters["studentId"]?.toUIntOrNull() ?: throw BadRequestException("studentId required")
            transaction {
                // TODO
                /*GroupsTable.deleteWhere {
                    (GroupsTable.id eq groupId) and
                            (GroupsTable.studentId eq studentId)
                }*/
            }
            call.respond(status = HttpStatusCode.NoContent, Unit)
        }
    }
}
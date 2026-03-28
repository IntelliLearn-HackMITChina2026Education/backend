package net.sfls.lh.intellilearn.routes

import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.sfls.lh.intellilearn.orm.StudentResponse
import net.sfls.lh.intellilearn.orm.UsersTable
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

fun Route.studentRoutes() {
    get("/students/search") {
        val keyword = call.request.queryParameters["keyword"] ?: ""
        val students = transaction {
            UsersTable
                .selectAll()
                .where { UsersTable.name like "%$keyword%" }
                .limit(10)
                .map {
                    StudentResponse(
                        id = it[UsersTable.id].value,
                        name = it[UsersTable.name],
                        className = it[UsersTable.classes][0]
                    )
                }
        }
        call.respond(students)
    }

    post("/students/batch") {
        val ids = call.receive<List<UInt>>()
        val students = transaction {
            UsersTable
                .selectAll()
                .where { UsersTable.id inList ids }
                .map {
                    StudentResponse(
                        id = it[UsersTable.id].value,
                        name = it[UsersTable.name],
                        className = it[UsersTable.classes][0]
                    )
                }
        }
        call.respond(students)
    }
}
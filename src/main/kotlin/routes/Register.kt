package net.sfls.lh.intellilearn.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.sfls.lh.intellilearn.orm.UserRegistration
import net.sfls.lh.intellilearn.orm.UsersTable
import net.sfls.lh.intellilearn.utils.Argon2idUtil
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.orWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

fun Route.registerRoute() {
    route("register") {
        get("/enabled") {
            if (!registerEnabled) {
                call.respond(HttpStatusCode(418, "I'm a teapot"), "Registration not enabled for this instance")
            } else {
                call.respond(HttpStatusCode.Accepted, "Registration enabled for this instance")
            }
        }
        post {
            val info = call.receive<UserRegistration>()
            val existed = transaction {
                return@transaction UsersTable
                    .select(UsersTable.name, UsersTable.email)
                    .where { UsersTable.name eq info.name }
                    .orWhere { UsersTable.email eq info.email }
                    .firstOrNull() != null
            }
            if (existed) return@post call.respond(HttpStatusCode.Conflict, "User already exists")
            transaction {
                UsersTable.insertAndGetId {
                    it[name] = info.name
                    it[classes] = info.classes
                    it[email] = info.email
                    it[password] = Argon2idUtil.hash(info.password)
                    it[phone] = info.phone
                    it[role] = info.role
                }
            }
            call.respond(HttpStatusCode.Created, "User Registered")
        }
    }
}
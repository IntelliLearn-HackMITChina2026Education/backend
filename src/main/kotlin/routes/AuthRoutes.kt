package net.sfls.lh.intellilearn.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.config.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.util.*
import kotlinx.serialization.Serializable
import net.sfls.lh.intellilearn.orm.User
import net.sfls.lh.intellilearn.orm.UsersTable
import net.sfls.lh.intellilearn.utils.Argon2idUtil
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.orWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

@Serializable
data class UserSession(
    val userId: String,
    val username: String
) {
    fun getUser() = transaction {
        return@transaction UsersTable
            .selectAll()
            .where { UsersTable.id eq userId.toUInt() }
            .first()
            .let {
                return@let User(
                    id = userId.toInt(),
                    username,
                    email = it[UsersTable.email],
                    avatar = "",
                    classes = it[UsersTable.classes]
                )
            }
    }
}

var registerEnabled = false
var cookieMaxAge = 86400.toLong()

fun verifyUser(user: String, password: String) = transaction {
    UsersTable
        .select(UsersTable.name, UsersTable.password)
        .where { UsersTable.name eq user }
        .orWhere { UsersTable.email eq user }
        .firstOrNull()
        ?.let {
            return@transaction Argon2idUtil.verify(it[UsersTable.password], password)
        } ?: false
}

fun getUserId(user: String) = transaction {
    UsersTable
        .select(UsersTable.id, UsersTable.name)
        .where { UsersTable.name eq user }
        .orWhere { UsersTable.email eq user }
        .first()[UsersTable.id].value
}

fun Application.configureSecurity() {
    registerEnabled = environment.config.property("security.registration").getAs<Boolean>()
    cookieMaxAge = environment.config.propertyOrNull("security.cookieMaxAge")?.getAs<Long>() ?: 86400.toLong()

    install(Sessions) {
        cookie<UserSession>("user_session") {
            cookie.path = "/"
            cookie.maxAgeInSeconds = cookieMaxAge
            cookie.httpOnly = true
            transform(
                SessionTransportTransformerMessageAuthentication(
                    hex("6819b57a326945c1968f45236589")
                )
            )
        }
    }

    authentication {
        session<UserSession> {
            validate { session ->
                // 验证 session 是否有效
                if (session.userId.isNotEmpty()) {
                    session
                } else {
                    null
                }
            }
            challenge {
                // 未认证时的处理
                call.respondRedirect("/login")
            }
        }
    }
    routing {
        route("api/auth") {
            post("/login") {
                val params = call.receiveParameters()
                val username =
                    params["username"] ?: return@post call.respond(HttpStatusCode.BadRequest, "用户名不能为空")
                val password = params["password"] ?: return@post call.respond(HttpStatusCode.BadRequest, "密码不能为空")

                if (verifyUser(username, password)) {
                    val userId = getUserId(username)
                    call.sessions.set(UserSession(userId = userId.toString(), username = username))
                    call.respond(mapOf("userId" to userId.toString(), "username" to username))
                } else {
                    call.respond(HttpStatusCode.Unauthorized, "用户名或密码错误")
                }
            }

            post("/logout") {
                call.sessions.clear<UserSession>()
                call.respond(HttpStatusCode.Unauthorized)
            }

            get("/me") {
                val session = call.sessions.get<UserSession>()
                if (session == null) {
                    call.respond(HttpStatusCode.Unauthorized, "未登录")
                    return@get
                }
                call.respond(session.getUser())
            }
        }
    }
}

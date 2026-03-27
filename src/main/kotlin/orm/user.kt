package net.sfls.lh.intellilearn.orm

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.dao.id.UIntIdTable

enum class UserRole {
    ADMIN,
    TEACHER,
    STUDENT
}

@Serializable
data class UserRegistration(
    val name: String,
    val password: String,
    val email: String,
    val phone: String,
    val role: UserRole,
    val classes: List<String>
)

@Serializable
data class User(
    val id: Int,
    val name: String,
    val email: String,
    val avatar: String,
    val classes: List<String>
)

object UsersTable : UIntIdTable("user") {
    val name = text("name").uniqueIndex()
    val password = text("password")
    val email = text("email").uniqueIndex()
    val phone = text("phone")
    val role = enumeration<UserRole>("role")
    val classes = array<String>("classes")
}

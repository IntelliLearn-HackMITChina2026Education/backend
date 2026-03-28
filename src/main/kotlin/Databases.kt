package net.sfls.lh.intellilearn

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import net.sfls.lh.intellilearn.orm.TaskQueueService
import org.jetbrains.exposed.v1.jdbc.Database

lateinit var queueService: TaskQueueService

fun Application.configureDatabases() {
    fun getDataSource(key: String) = environment.config.property("postgres.$key").getString()
    val hikari = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = getDataSource("url")
            username = getDataSource("user")
            password = getDataSource("password")
            driverClassName = "org.postgresql.Driver"
            transactionIsolation = "TRANSACTION_SERIALIZABLE"
            isReadOnly = false
            validate()
        }
    )
    val database = Database.connect(hikari)
    queueService = TaskQueueService(database)
    log.info("Database is successfully connected.")
}

package net.sfls.lh.intellilearn

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import org.jetbrains.exposed.v1.jdbc.Database


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
    Database.connect(hikari)
    log.info("Database is successfully connected.")
}

package net.sfls.lh.intellilearn.utils

import net.sfls.lh.intellilearn.orm.ConfigTable
import net.sfls.lh.intellilearn.orm.ExamTable
import net.sfls.lh.intellilearn.orm.TaskTable
import net.sfls.lh.intellilearn.orm.UsersTable
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.SchemaUtils.withDataBaseLock
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils

fun migrateTables() = transaction {
    withDataBaseLock {
        try {
            val missingColStatements = MigrationUtils.statementsRequiredForDatabaseMigration(
                UsersTable, ExamTable, ConfigTable, TaskTable
            )
            if (missingColStatements.isEmpty()) {
                println("No migration needed.")
                return@withDataBaseLock
            }
            println("Fixing Database schema with these command: $missingColStatements")
            missingColStatements.forEach { exec(it) }
        } catch (e: IllegalStateException) {
            println("Migration failed: ${e.message}")
            println("Attempting to create table from scratch...")
            SchemaUtils.create(UsersTable, ExamTable, ConfigTable, TaskTable)
            println("Table created successfully.")
        }
    }
}

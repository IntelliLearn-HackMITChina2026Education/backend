package net.sfls.lh.intellilearn.utils

import net.sfls.lh.intellilearn.orm.*
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.SchemaUtils.withDataBaseLock
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils

fun migrateTables() = transaction {
    withDataBaseLock {
        try {
            val missingColStatements = MigrationUtils.statementsRequiredForDatabaseMigration(
                UsersTable, ExamTable, ConfigTable, TaskTable, GroupsTable, AnalyzedTable
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
            SchemaUtils.create(UsersTable, ExamTable, ConfigTable, TaskTable, GroupsTable, AnalyzedTable)
            println("Table created successfully.")
        }
    }
}

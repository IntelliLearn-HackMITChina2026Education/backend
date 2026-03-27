package net.sfls.lh.intellilearn

import io.ktor.server.application.*
import net.sfls.lh.intellilearn.utils.migrateTables
import net.sfls.lh.intellilearn.routes.configureSecurity

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureHTTP()
    configureSecurity()
    configureMonitoring()
    configureSerialization()
    configureDatabases()
    configureRouting()

    migrateTables()
}

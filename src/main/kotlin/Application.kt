package net.sfls.lh.intellilearn

import io.ktor.server.application.*
import net.sfls.lh.intellilearn.routes.configureSecurity
import net.sfls.lh.intellilearn.utils.migrateTables
import kotlin.concurrent.atomics.ExperimentalAtomicApi

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

@OptIn(ExperimentalAtomicApi::class)
fun Application.module() {
    configureHTTP()
    configureSecurity()
    configureMonitoring()
    configureSerialization()
    configureDatabases()
    configureRouting()
    configureTaskProcessor()

    migrateTables()
}

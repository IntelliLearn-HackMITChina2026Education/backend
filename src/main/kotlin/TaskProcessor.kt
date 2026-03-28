package net.sfls.lh.intellilearn

import io.ktor.server.application.*
import io.ktor.server.config.*
import net.sfls.lh.intellilearn.docprocessing.TaskProcessor
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
fun Application.configureTaskProcessor() {
    val concurrency = environment.config.property("task.concurrency").getAs<Int>().or(2)
    val pollInterval = environment.config.property("task.pollInterval").getAs<Long>().or(1000)
    val taskProcessor = TaskProcessor(queueService, concurrency, pollInterval)
    val started = AtomicBoolean(false)
    if (started.compareAndSet(expectedValue = false, newValue = true)) {
        taskProcessor.start()
    }
}
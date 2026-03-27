package net.sfls.lh.intellilearn

import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import net.sfls.lh.intellilearn.orm.redis.RedisUploadStore
import net.sfls.lh.intellilearn.routes.examRoutes
import net.sfls.lh.intellilearn.routes.registerRoute
import net.sfls.lh.intellilearn.routes.uploadRoutes

fun Application.configureRouting() {
    val uploadService = UploadService(
        uploadDir = environment.config.propertyOrNull("upload.dir")?.getString() ?: "./uploads",
        tempDir = environment.config.propertyOrNull("upload.tempDir")?.getString() ?: "./uploads/temp",
        publicBaseUrl = environment.config.propertyOrNull("upload.publicBaseUrl")?.getString()
            ?: "http://localhost:3000/api/files",
        store = RedisUploadStore()
    )
    routing {
        // Static plugin. Try to access `/static/index.html`
        staticResources("/static", "static")

        route("/api") {
            uploadRoutes(uploadService)
            examRoutes()
            registerRoute()
        }
    }
}
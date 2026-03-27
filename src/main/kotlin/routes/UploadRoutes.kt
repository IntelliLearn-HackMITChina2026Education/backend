package net.sfls.lh.intellilearn.routes

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import net.sfls.lh.intellilearn.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("UploadRoutes")

fun Route.uploadRoutes(uploadService: UploadService) {

    route("/uploads") {

        post("/initialize") {
            val req = call.receive<InitializeUploadRequest>()
            val response = uploadService.initializeUpload(req)
            call.respond(HttpStatusCode.OK, response)
        }

        get("/{uploadId}/status") {
            val uploadId = call.parameters["uploadId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing uploadId"))

            try {
                val status = uploadService.getUploadStatus(uploadId)
                call.respond(HttpStatusCode.OK, status)
            } catch (e: NotFoundException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            }
        }

        post("/chunk") {
            var chunkBytes: ByteArray? = null
            var chunkIndex: Int? = null
            var uploadId: String? = null
            var totalChunks: Int? = null

            val multipart = call.receiveMultipart()
            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FormItem -> {
                        when (part.name) {
                            "chunkIndex" -> chunkIndex = part.value.toIntOrNull()
                            "uploadId" -> uploadId = part.value
                            "totalChunks" -> totalChunks = part.value.toIntOrNull()
                        }
                    }

                    is PartData.FileItem -> {
                        if (part.name == "chunk") {
                            chunkBytes = part.provider().readRemaining().readBytes()
                        }
                    }

                    else -> {}
                }
                part.dispose()
            }

            val missingFields = listOfNotNull(
                if (chunkBytes == null) "chunk" else null,
                if (chunkIndex == null) "chunkIndex" else null,
                if (uploadId == null) "uploadId" else null,
                if (totalChunks == null) "totalChunks" else null,
            )
            if (missingFields.isNotEmpty()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Missing fields: ${missingFields.joinToString()}")
                )
            }

            try {
                val result = uploadService.uploadChunk(
                    uploadId = uploadId!!,
                    chunkIndex = chunkIndex!!,
                    totalChunks = totalChunks!!,
                    chunkBytes = chunkBytes!!,
                )
                call.respond(HttpStatusCode.OK, result)
            } catch (e: NotFoundException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: ValidationException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            }
        }

        post("/merge") {
            val req = call.receive<MergeChunksRequest>()
            try {
                val result = uploadService.mergeChunks(req)
                call.respond(HttpStatusCode.OK, result)
            } catch (e: NotFoundException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: ValidationException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            }
        }

        post("/{uploadId}/cancel") {
            val uploadId = call.parameters["uploadId"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing uploadId"))

            val cancelled = uploadService.cancelUpload(uploadId)
            if (cancelled) {
                call.respond(HttpStatusCode.OK, mapOf("cancelled" to true))
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Session not found"))
            }
        }
    }

    get("/files") {
        call.respond(HttpStatusCode.OK, uploadService.getUploadedFiles())
    }

    // 用 uploadId 唯一定位文件，下载时以原始文件名呈现给用户
    get("/files/{id}") {
        val id = call.parameters["id"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing id"))

        val (file, originalName) = uploadService.getFile(id)
            ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "File not found"))

        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment.withParameter(
                ContentDisposition.Parameters.FileName, originalName
            ).toString()
        )
        call.respondFile(file)
    }
}
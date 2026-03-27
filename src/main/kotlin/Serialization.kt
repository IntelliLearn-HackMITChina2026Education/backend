package net.sfls.lh.intellilearn

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import net.blophy.nova.kollama.KOllamaClient
import net.blophy.nova.kollama.datamodels.ChatRole
import java.io.File
import java.util.*

/**
 * 将指定路径的图片文件转换为 Base64 编码字符串。
 *
 * @param filePath 图片文件的完整路径
 * @return Base64 编码后的字符串，如果文件不存在或读取失败则返回 null
 */
fun imageFileToBase64(filePath: String): String? {
    return try {
        val file = File(filePath)
        val bytes = file.readBytes()
        Base64.getEncoder().encodeToString(bytes)
    } catch (e: Exception) {
        // 可根据需要记录日志或处理异常
        null
    }
}

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    routing {
        get("/json/kotlinx-serialization") {
            // call.respond(mapOf("hello" to "world"))
            val ollama = KOllamaClient()
            val r = ollama.chat {
                model = "qwen3.5:latest"
                message {
                    role = ChatRole.User
                    content = "这是什么？"
                    images = listOf(imageFileToBase64("C:\\Users\\Lenovo\\Downloads\\low.jpg")!!)
                }
            }
            println(r.message.content)
            call.respond(r.message.content)
        }
    }
}

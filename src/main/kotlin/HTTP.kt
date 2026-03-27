package net.sfls.lh.intellilearn

import com.ucasoft.ktor.simpleCache.SimpleCache
import com.ucasoft.ktor.simpleRedisCache.redisCache
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.cors.routing.*
import kotlin.time.Duration.Companion.seconds

fun Application.configureHTTP() {
    install(Compression)
//    routing {
//        openAPI(path = "openapi") {
//            info = OpenApiInfo(title = "My API", version = "1.0.0")
//        }
//    }
    install(SimpleCache) {
        redisCache {
            invalidateAt = 10.seconds
            host = "localhost"
            port = 6379
        }
    }
    install(CORS) {
        anyHost()
        allowCredentials = true
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.Accept)
        exposeHeader(HttpHeaders.ContentDisposition)
    }
}

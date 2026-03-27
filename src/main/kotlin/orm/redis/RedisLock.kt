package net.sfls.lh.intellilearn.orm.redis

import io.github.crackthecodeabhi.kreds.args.SetOption
import io.github.crackthecodeabhi.kreds.connection.KredsClient
import kotlinx.coroutines.delay
import java.util.*
import kotlin.time.Duration.Companion.milliseconds

class RedisLock(
    private val client: KredsClient,
) {
    suspend fun <T> withLock(
        key: String,
        ttlMillis: ULong = 30_000.toULong(),
        retryDelayMillis: Long = 80,
        maxRetry: Int = 250, // ~20s
        block: suspend () -> T
    ): T {
        val token = UUID.randomUUID().toString()
        var acquired = false

        repeat(maxRetry) {
            val ok = trySetNxPx(key, token, ttlMillis)
            if (ok) {
                acquired = true
                return try {
                    block()
                } finally {
                    release(key, token)
                }
            }
            delay(retryDelayMillis.milliseconds)
        }

        throw IllegalStateException("Failed to acquire redis lock: $key")

        @Suppress("UNREACHABLE_CODE")
        return block()
    }

    private suspend fun trySetNxPx(key: String, token: String, ttlMillis: ULong): Boolean {
        val result =
            client.set(key, token, setOption = SetOption.Builder(nx = true, pxMilliseconds = ttlMillis).build())
        return result == "OK"
    }

    private suspend fun release(key: String, token: String) {
        val script = """
            if redis.call("GET", KEYS[1]) == ARGV[1] then
              return redis.call("DEL", KEYS[1])
            else
              return 0
            end
        """.trimIndent()

        try {
            client.eval(script, keys = arrayOf(key), args = arrayOf(token))
        } catch (_: Exception) {
            // 释放失败不影响主流程（锁会过期）
        }
    }
}

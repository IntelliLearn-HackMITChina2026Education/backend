package net.sfls.lh.intellilearn.orm.redis

import io.github.crackthecodeabhi.kreds.connection.Endpoint
import io.github.crackthecodeabhi.kreds.connection.newClient
import kotlinx.serialization.json.Json
import net.sfls.lh.intellilearn.UploadSession

/**
 * Redis key 设计：
 * - upload:session:{uploadId}          -> JSON(UploadSession)
 * - upload:chunks:{uploadId}           -> Set(chunkIndex)
 * - upload:hash:completed:{fileHash}   -> uploadId   (秒传用，只指向已完成)
 * - upload:hash:active:{fileHash}      -> uploadId   (断点续传用，只指向进行中/未取消)
 * - upload:completed:zset              -> ZSET(score=createdAtMillis, member=uploadId) (文件列表用)
 */
class RedisUploadStore(
    redisHost: String = "127.0.0.1",
    redisPort: Int = 6379,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val client = newClient(Endpoint(redisHost, redisPort))

    private fun sessionKey(id: String) = "upload:session:$id"
    private fun chunksKey(id: String) = "upload:chunks:$id"
    private fun completedHashKey(fileHash: String) = "upload:hash:completed:$fileHash"
    private fun activeHashKey(fileHash: String) = "upload:hash:active:$fileHash"
    private fun completedZsetKey() = "upload:completed:zset"

    suspend fun getSession(uploadId: String): UploadSession? {
        val s = client.get(sessionKey(uploadId)) ?: return null
        return json.decodeFromString(s)
    }

    suspend fun putSession(session: UploadSession) {
        client.set(sessionKey(session.uploadId), json.encodeToString(session))
    }

    suspend fun deleteSession(uploadId: String) {
        client.del(sessionKey(uploadId))
    }

    suspend fun addUploadedChunk(uploadId: String, chunkIndex: Int) {
        client.sadd(chunksKey(uploadId), chunkIndex.toString())
    }

    suspend fun hasUploadedChunk(uploadId: String, chunkIndex: Int): Boolean {
        return client.sismember(chunksKey(uploadId), chunkIndex.toString()) == 1L
    }

    suspend fun listUploadedChunks(uploadId: String): List<Int> {
        return client.smembers(chunksKey(uploadId))
            .mapNotNull { it.toIntOrNull() }
            .sorted()
    }

    suspend fun clearChunks(uploadId: String) {
        client.del(chunksKey(uploadId))
    }

    // 秒传：已完成 hash -> uploadId
    suspend fun getCompletedUploadIdByHash(fileHash: String): String? =
        client.get(completedHashKey(fileHash))

    suspend fun setCompletedHash(fileHash: String, uploadId: String) {
        client.set(completedHashKey(fileHash), uploadId)
    }

    // 断点续传：active hash -> uploadId
    suspend fun getActiveUploadIdByHash(fileHash: String): String? =
        client.get(activeHashKey(fileHash))

    suspend fun setActiveHash(fileHash: String, uploadId: String) {
        client.set(activeHashKey(fileHash), uploadId)
    }

    suspend fun clearActiveHash(fileHash: String) {
        client.del(activeHashKey(fileHash))
    }

    suspend fun addCompleted(uploadId: String, createdAtMillis: Long) {
        val score: Int = (createdAtMillis / 1000L).toInt()

        client.zadd(
            completedZsetKey(),
            scoreMember = (score to uploadId),
        )
    }


    suspend fun listCompletedIds(limit: Int = 200): List<String> {
        if (limit <= 0) return emptyList()

        val start = -limit.toLong()
        val stop = -1L

        val members: List<String> = client.zrange(
            key = completedZsetKey(),
            min = start,
            max = stop,
            by = null,
            rev = false,
            limit = null,
            withScores = false
        )

        return members.asReversed()
    }


}

package net.sfls.lh.intellilearn

import io.github.crackthecodeabhi.kreds.connection.Endpoint
import io.github.crackthecodeabhi.kreds.connection.newClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.sfls.lh.intellilearn.orm.redis.RedisLock
import net.sfls.lh.intellilearn.orm.redis.RedisUploadStore
import org.slf4j.LoggerFactory
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.util.*
import kotlin.math.ceil

class UploadService(
    private val store: RedisUploadStore,
    redisHost: String = "127.0.0.1",
    redisPort: Int = 6379,
    private val uploadDir: String = "./uploads",
    private val tempDir: String = "./uploads/temp",
    private val publicBaseUrl: String = "http://localhost:8080/api/files",
) {
    private val logger = LoggerFactory.getLogger(UploadService::class.java)

    // 分布式锁 client（和 store 分开是为了不暴露 store 的 client；你也可以改为在 store 里提供 client）
    private val lockClient = newClient(Endpoint(redisHost, redisPort))
    private val redisLock = RedisLock(lockClient)

    init {
        Files.createDirectories(Paths.get(uploadDir))
        Files.createDirectories(Paths.get(tempDir))
        logger.info("UploadService(Redis) initialized. uploadDir=$uploadDir")
    }

    // ─────────────────────────────────────────
    // 初始化上传
    // ─────────────────────────────────────────
    suspend fun initializeUpload(req: InitializeUploadRequest): InitializeUploadResponse {
        // 1) 秒传：同 hash 已完成
        val completedId = store.getCompletedUploadIdByHash(req.fileHash)
        if (completedId != null) {
            val existing = store.getSession(completedId)
            if (existing != null && existing.status == UploadStatus.COMPLETED) {
                logger.info("Instant upload hit for hash=${req.fileHash}, uploadId=$completedId")
                return InitializeUploadResponse(
                    uploadId = completedId,
                    alreadyCompleted = true,
                    fileUrl = existing.fileUrl,
                )
            }
        }

        // 2) 断点续传：同 hash 的 active uploadId
        val activeId = store.getActiveUploadIdByHash(req.fileHash)
        if (activeId != null) {
            val resumable = store.getSession(activeId)
            if (resumable != null && resumable.status != UploadStatus.CANCELLED) {
                val chunks = store.listUploadedChunks(activeId)
                logger.info("Resuming existing upload, uploadId=$activeId, uploadedChunks=${chunks.size}")
                return InitializeUploadResponse(
                    uploadId = activeId,
                    existingChunks = chunks,
                )
            }
        }

        // 3) 新建会话
        val uploadId = UUID.randomUUID().toString()
        val totalChunks = ceil(req.fileSize.toDouble() / req.chunkSize).toInt()
        val session = UploadSession(
            uploadId = uploadId,
            fileName = sanitizeFileName(req.fileName),
            fileSize = req.fileSize,
            fileHash = req.fileHash,
            chunkSize = req.chunkSize,
            totalChunks = totalChunks,
            createdAt = System.currentTimeMillis(),
            status = UploadStatus.PENDING,
            fileUrl = null,
            completedAt = null,
        )

        store.putSession(session)
        store.setActiveHash(req.fileHash, uploadId)

        withContext(Dispatchers.IO) {
            Files.createDirectories(chunkDir(uploadId))
        }

        logger.info("Initialized new upload: uploadId=$uploadId, file=${req.fileName}, totalChunks=$totalChunks")
        return InitializeUploadResponse(uploadId = uploadId)
    }

    // ─────────────────────────────────────────
    // 上传分片
    // ─────────────────────────────────────────
    suspend fun uploadChunk(
        uploadId: String,
        chunkIndex: Int,
        totalChunks: Int,
        chunkBytes: ByteArray,
    ): UploadChunkResponse {
        val session = store.getSession(uploadId)
            ?: throw NotFoundException("Upload session not found: $uploadId")

        if (chunkIndex < 0 || chunkIndex >= session.totalChunks) {
            throw ValidationException("Invalid chunkIndex $chunkIndex, expected 0..${session.totalChunks - 1}")
        }

        // 幂等：已上传则直接返回
        if (store.hasUploadedChunk(uploadId, chunkIndex)) {
            logger.debug("Chunk $chunkIndex already uploaded for $uploadId, skipping")
            return UploadChunkResponse(uploadId, chunkIndex, true)
        }

        // 写入临时文件
        withContext(Dispatchers.IO) {
            val f = chunkFile(uploadId, chunkIndex)
            f.writeBytes(chunkBytes)
        }

        store.addUploadedChunk(uploadId, chunkIndex)

        // 更新会话状态（只改 status，不把 uploadedChunks 塞进 session）
        if (session.status != UploadStatus.IN_PROGRESS) {
            store.putSession(session.copy(status = UploadStatus.IN_PROGRESS))
        }

        logger.info("Chunk $chunkIndex/$totalChunks saved for upload $uploadId")
        return UploadChunkResponse(uploadId, chunkIndex, true)
    }

    // ─────────────────────────────────────────
    // 查询上传状态（注意：改成 suspend，因为要读 Redis）
    // ─────────────────────────────────────────
    suspend fun getUploadStatus(uploadId: String): UploadStatusResponse {
        val session = store.getSession(uploadId)
            ?: throw NotFoundException("Upload session not found: $uploadId")
        val chunks = store.listUploadedChunks(uploadId)

        return UploadStatusResponse(
            uploadId = session.uploadId,
            fileName = session.fileName,
            fileSize = session.fileSize,
            totalChunks = session.totalChunks,
            uploadedChunks = chunks,
            status = session.status,
        )
    }

    // ─────────────────────────────────────────
    // 合并分片（用 Redis 分布式锁替代 Mutex）
    // ─────────────────────────────────────────
    suspend fun mergeChunks(req: MergeChunksRequest): MergeChunksResponse {
        val session = store.getSession(req.uploadId)
            ?: throw NotFoundException("Upload session not found: ${req.uploadId}")

        val lockKey = "upload:lock:merge:${req.uploadId}"

        return redisLock.withLock(lockKey) {
            // 幂等：已完成直接返回
            val latest = store.getSession(req.uploadId)
                ?: throw NotFoundException("Upload session not found: ${req.uploadId}")

            if (latest.status == UploadStatus.COMPLETED) {
                return@withLock MergeChunksResponse(
                    uploadId = latest.uploadId,
                    fileName = latest.fileName,
                    fileUrl = latest.fileUrl!!,
                    fileSize = latest.fileSize,
                )
            }

            // 校验所有分片都已上传（从 Redis Set 取）
            val uploaded = store.listUploadedChunks(req.uploadId).toSet()
            val missing = (0 until latest.totalChunks).filter { it !in uploaded }
            if (missing.isNotEmpty()) throw ValidationException("Missing chunks: $missing")

            withContext(Dispatchers.IO) {
                val destFile = destFileById(latest.uploadId, latest.fileName)
                logger.info("Merging ${latest.totalChunks} chunks -> ${destFile.path}")

                RandomAccessFile(destFile, "rw").use { raf ->
                    for (i in 0 until latest.totalChunks) {
                        val chunk = chunkFile(latest.uploadId, i)
                        if (!chunk.exists()) throw IllegalStateException("Chunk file missing: ${chunk.path}")
                        raf.write(chunk.readBytes())
                    }
                }

                // 清理临时分片
                chunkDir(latest.uploadId).toFile().deleteRecursively()
            }

            // 清理 Redis chunks set
            store.clearChunks(req.uploadId)

            // 标记完成 + 写索引
            val fileUrl = "$publicBaseUrl/${latest.uploadId}"
            val completedAt = System.currentTimeMillis()
            val completedSession = latest.copy(
                fileUrl = fileUrl,
                status = UploadStatus.COMPLETED,
                completedAt = completedAt,
            )
            store.putSession(completedSession)

            // completed hash（秒传命中只指向已完成）
            store.setCompletedHash(latest.fileHash, latest.uploadId)

            // completed 列表
            store.addCompleted(latest.uploadId, latest.createdAt)

            // active hash 清掉（避免后续 initializeUpload 走 resumable）
            store.clearActiveHash(latest.fileHash)

            val ext = fileExtension(latest.fileName)
            val storedName = if (ext.isNotEmpty()) "${latest.uploadId}.$ext" else latest.uploadId
            val storedFile = File(uploadDir, storedName)

            logger.info("Merge complete for ${latest.uploadId}, storedAs=${storedFile.name}")

            MergeChunksResponse(
                uploadId = latest.uploadId,
                fileName = latest.fileName,
                fileUrl = fileUrl,
                fileSize = storedFile.length(),
            )
        }
    }

    // ─────────────────────────────────────────
    // 取消上传
    // ─────────────────────────────────────────
    suspend fun cancelUpload(uploadId: String): Boolean {
        val session = store.getSession(uploadId) ?: return false

        val cancelled = session.copy(status = UploadStatus.CANCELLED)
        store.putSession(cancelled)

        // 清 active hash（断点续传索引）
        store.clearActiveHash(session.fileHash)

        // 清 chunks set
        store.clearChunks(uploadId)

        // 清本地分片目录
        withContext(Dispatchers.IO) {
            chunkDir(uploadId).toFile().deleteRecursively()
        }

        logger.info("Upload cancelled: $uploadId")
        return true
    }

    // ─────────────────────────────────────────
    // 已上传文件列表（注意：改成 suspend，因为要读 Redis）
    // ─────────────────────────────────────────
    suspend fun getUploadedFiles(limit: Int = 200): List<FileUploadMetadata> {
        val ids = store.listCompletedIds(limit)
        val sessions = ids.mapNotNull { store.getSession(it) }
            .filter { it.status == UploadStatus.COMPLETED }

        return sessions
            .map { s ->
                val chunks = emptyList<Int>() // completed 时不必返回 chunk 列表（想要也可从 Redis 拉，但我们已 clearChunks）
                FileUploadMetadata(
                    id = s.uploadId,
                    name = s.fileName,
                    size = s.fileSize,
                    totalChunks = s.totalChunks,
                    chunkSize = s.chunkSize,
                    uploadedChunks = chunks,
                    hash = s.fileHash,
                    createdAt = Instant.ofEpochMilli(s.createdAt).toString(),
                    lastModified = s.completedAt?.let { Instant.ofEpochMilli(it).toString() }
                        ?: Instant.ofEpochMilli(s.createdAt).toString(),
                    status = s.status,
                    fileUrl = s.fileUrl,
                )
            }
            .sortedByDescending { it.createdAt }
    }

    // ─────────────────────────────────────────
    // 获取文件（用于下载/访问）：按 uploadId 定位
    // ─────────────────────────────────────────
    suspend fun getFile(uploadId: String): Pair<File, String>? {
        val session = store.getSession(uploadId) ?: return null
        if (session.status != UploadStatus.COMPLETED) return null
        val ext = fileExtension(session.fileName)
        val storedName = if (ext.isNotEmpty()) "$uploadId.$ext" else uploadId
        val file = File(uploadDir, storedName)
        return if (file.exists() && file.isFile) Pair(file, session.fileName) else null
    }

    // ─────────────────────────────────────────
    // 工具函数
    // ─────────────────────────────────────────
    private fun chunkDir(uploadId: String): Path = Paths.get(tempDir, uploadId)

    private fun chunkFile(uploadId: String, index: Int): File =
        chunkDir(uploadId).resolve("chunk_${index.toString().padStart(8, '0')}").toFile()

    private fun destFileById(uploadId: String, originalFileName: String): File {
        val ext = fileExtension(originalFileName)
        val storedName = if (ext.isNotEmpty()) "$uploadId.$ext" else uploadId
        return File(uploadDir, storedName)
    }

    private fun fileExtension(fileName: String): String =
        fileName.substringAfterLast('.', "").let { if (it == fileName) "" else it }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[/\\\\:*?\"<>|]"), "_").trim()
}

class NotFoundException(message: String) : Exception(message)
class ValidationException(message: String) : Exception(message)

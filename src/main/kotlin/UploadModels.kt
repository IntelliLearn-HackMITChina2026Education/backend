package net.sfls.lh.intellilearn

import kotlinx.serialization.Serializable

@Serializable
data class InitializeUploadRequest(
    val fileName: String,
    val fileSize: Long,
    val fileHash: String,
    val chunkSize: Int,
)

@Serializable
data class InitializeUploadResponse(
    val uploadId: String,
    val existingChunks: List<Int> = emptyList(),
    /** true 表示文件已完整上传过（秒传） */
    val alreadyCompleted: Boolean = false,
    val fileUrl: String? = null,
)

@Serializable
data class UploadStatusResponse(
    val uploadId: String,
    val fileName: String,
    val fileSize: Long,
    val totalChunks: Int,
    val uploadedChunks: List<Int>,
    val status: UploadStatus,
)

@Serializable
enum class UploadStatus {
    PENDING, IN_PROGRESS, COMPLETED, CANCELLED
}

@Serializable
data class MergeChunksRequest(
    val uploadId: String,
    val fileName: String,
)

@Serializable
data class MergeChunksResponse(
    val uploadId: String,
    val fileName: String,
    val fileUrl: String,
    val fileSize: Long,
)

@Serializable
data class UploadChunkResponse(
    val uploadId: String,
    val chunkIndex: Int,
    val received: Boolean,
)

@Serializable
data class FileUploadMetadata(
    val id: String,
    val name: String,
    val size: Long,
    val totalChunks: Int,
    val chunkSize: Int,
    val uploadedChunks: List<Int>,
    val hash: String,
    val createdAt: String,
    val lastModified: String,
    val status: UploadStatus,
    val fileUrl: String? = null,
)

@Serializable
/** 内存中维护的上传会话 */
data class UploadSession(
    val uploadId: String,
    val fileName: String,
    val fileSize: Long,
    val fileHash: String,
    val chunkSize: Int,
    val totalChunks: Int,
    val uploadedChunks: MutableSet<Int> = mutableSetOf(),
    var status: UploadStatus = UploadStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    var completedAt: Long? = null,
    var fileUrl: String? = null,
)
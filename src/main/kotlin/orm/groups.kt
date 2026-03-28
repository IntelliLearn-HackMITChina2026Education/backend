package net.sfls.lh.intellilearn.orm

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.dao.id.UIntIdTable

object GroupsTable: UIntIdTable("groups") {
    val name = text("name").index()
    val members = array<UInt>("members")
    val tclass = text("tclass").index()
}

@Serializable
data class GroupResponse(
    val id: UInt,
    val name: String,
    val students: List<UInt>? = null
)

@Serializable
data class GroupAnalysisResponse(
    val summary: String,
    val suggestions: List<String>
)

@Serializable
data class GroupTrendResponse(
    val id: UInt,
    val name: String,
    val currentAvg: Double,
    val change: Double,
    val trend: List<TrendItem>
) {
    @Serializable
    data class TrendItem(
        val exam: String,
        val avgScore: Double
    )
}

@Serializable
data class StudentResponse(
    val id: UInt,
    val name: String,
    val className: String? = null
)
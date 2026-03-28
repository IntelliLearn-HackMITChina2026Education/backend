package net.sfls.lh.intellilearn.orm

import org.jetbrains.exposed.v1.core.Table

object ConfigTable : Table("config") {
    val key = text("key")
    val value = text("value")
}
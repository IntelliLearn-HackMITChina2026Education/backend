package net.sfls.lh.intellilearn.docprocessing

import kotlinx.serialization.Serializable
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import java.io.File
import java.io.FileInputStream

@Serializable
data class QuestionScore(val id: Int, val score: Double)

@Serializable
data class StudentScore(val name: String, val data: List<QuestionScore>)

fun convertXlsToJson(file: File): List<StudentScore> {
    val workbook = HSSFWorkbook(FileInputStream(file))
    val sheet = workbook.getSheet("学生小题分") ?: return emptyList()

    val headerRow = sheet.getRow(0) ?: return emptyList()

    val fixedColumns = listOf("序号", "学校", "姓名", "班级", "学号", "考号", "总分")
    val columnIndexMap = mutableMapOf<String, Int>()
    for (i in 0 until headerRow.lastCellNum) {
        val cell = headerRow.getCell(i)
        val value = cell?.toString()?.trim() ?: continue
        fixedColumns.find { it == value }?.let {
            columnIndexMap[it] = i
        }
    }

    require(columnIndexMap.containsKey("序号")) { "缺少“序号”列" }
    require(columnIndexMap.containsKey("姓名")) { "缺少“姓名”列" }

    val totalIndex = columnIndexMap["总分"] ?: (headerRow.lastCellNum - 1)
    val questionColumns = mutableListOf<Pair<Int, Int>>() // (列索引, 题号id)
    for (i in totalIndex + 1 until headerRow.lastCellNum) {
        val headerCell = headerRow.getCell(i)
        val headerText = headerCell?.toString()?.trim() ?: continue
        val id = Regex("\\d+").find(headerText)?.value?.toIntOrNull()
        if (id != null) {
            questionColumns.add(i to id)
        }
    }

    val students = mutableListOf<StudentScore>()
    for (rowIdx in 1..sheet.lastRowNum) {
        val row = sheet.getRow(rowIdx) ?: continue

        val seqCell = row.getCell(columnIndexMap["序号"]!!)
        if (seqCell == null || seqCell.toString().isBlank()) continue

        val nameCell = row.getCell(columnIndexMap["姓名"]!!)
        val name = nameCell?.toString()?.trim() ?: continue
        if (name.isBlank()) continue

        val data = mutableListOf<QuestionScore>()
        for ((colIdx, questionId) in questionColumns) {
            val score = when (val cell = row.getCell(colIdx)) {
                null -> null
                is org.apache.poi.ss.usermodel.Cell -> {
                    when (cell.cellType) {
                        org.apache.poi.ss.usermodel.CellType.NUMERIC -> cell.numericCellValue
                        org.apache.poi.ss.usermodel.CellType.STRING -> cell.stringCellValue.toDoubleOrNull()
                        else -> null
                    }
                }
            }
            if (score != null) {
                data.add(QuestionScore(questionId, score))
            }
        }

        students.add(StudentScore(name, data))
    }

    workbook.close()
    return students
}
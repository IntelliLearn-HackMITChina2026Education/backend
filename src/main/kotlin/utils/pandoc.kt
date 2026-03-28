package net.sfls.lh.intellilearn.utils

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * 调用 pandoc 将 docx 或 pdf 文件转换为 Markdown 文本。
 *
 * @param file 输入文件对象（支持 .docx 或 .pdf）
 * @return 转换后的 Markdown 字符串
 * @throws IllegalArgumentException 如果文件不存在、类型不支持或路径不是文件
 * @throws RuntimeException 如果 pandoc 执行失败或未安装
 */
fun convertToMarkdown(file: File): String {
    val extension = file.extension.lowercase()
    if (extension !in listOf("docx", "pdf")) {
        throw IllegalArgumentException("Unsupported file type: $extension. Only docx and pdf are supported.")
    }

    val command = listOf("pandoc", file.absolutePath, "-t", "markdown", "-o", "-")
    val processBuilder = ProcessBuilder(command)
    processBuilder.redirectErrorStream(true)

    val process = processBuilder.start()
    val output = StringBuilder()

    BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8)).use { reader ->
        reader.forEachLine { line ->
            output.append(line).append("\n")
        }
    }

    val exitCode = process.waitFor()
    if (exitCode != 0) {
        throw RuntimeException("Pandoc conversion failed with exit code $exitCode. Output: $output")
    }

    return output.toString().trimEnd()
}
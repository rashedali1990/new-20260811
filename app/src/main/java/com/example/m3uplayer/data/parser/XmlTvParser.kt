package com.example.m3uplayer.data.parser

import com.example.m3uplayer.data.local.entities.EpgProgramEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

object XmlTvParser {
    suspend fun parseStreaming(inputStream: InputStream, onProgress: (Int) -> Unit = {}): List<EpgProgramEntity> = withContext(Dispatchers.IO) {
        val programs = mutableListOf<EpgProgramEntity>()

        BufferedReader(InputStreamReader(inputStream)).use { reader ->
            var currentChannelId: String? = null
            var currentTitle: String? = null
            var currentDescription: String? = null
            var currentStart: String? = null
            var currentStop: String? = null
            var currentDate: String? = null
            var lineCount = 0

            reader.lineSequence().forEach { line ->
                lineCount++
                if (lineCount % 100 == 0) onProgress(lineCount)

                val trimmed = line.trim()
                when {
                    trimmed.startsWith("<channel") -> {
                        currentChannelId = trimmed.substringAfter("id=\"").substringBefore("\"")
                    }
                    trimmed.startsWith("<programme") -> {
                        currentStart = trimmed.substringAfter("start=\"").substringBefore("\"")
                        currentStop = trimmed.substringAfter("stop=\"").substringBefore("\"")

                        // Extract date from start time (YYYYMMDDHHMMSS)
                        currentDate = currentStart?.take(8)
                    }
                    trimmed.startsWith("<title") -> {
                        currentTitle = trimmed.substringAfter(">").substringBefore("</title>")
                    }
                    trimmed.startsWith("<desc") -> {
                        currentDescription = trimmed.substringAfter(">").substringBefore("</desc>")
                    }
                    trimmed.startsWith("</programme>") -> {
                        if (currentChannelId != null && currentTitle != null && currentDate != null) {
                            programs.add(EpgProgramEntity(
                                channelId = currentChannelId,
                                title = currentTitle,
                                description = currentDescription ?: "",
                                startTime = currentStart ?: "",
                                endTime = currentStop ?: "",
                                date = currentDate
                            ))
                        }
                        currentTitle = null
                        currentDescription = null
                        currentStart = null
                        currentStop = null
                        currentDate = null
                    }
                }
            }
        }
        programs
    }
}

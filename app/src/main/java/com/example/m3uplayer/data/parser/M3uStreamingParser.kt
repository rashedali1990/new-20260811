package com.example.m3uplayer.data.parser

import com.example.m3uplayer.data.local.entities.ChannelEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

object M3uStreamingParser {

    private val nameRegex = Regex(",(.*)$")
    private val logoRegex = Regex("tvg-logo=\"([^\"]*)\"")
    private val groupRegex = Regex("group-title=\"([^\"]*)\"")

    suspend fun parseStreaming(inputStream: InputStream, profileId: Int, onProgress: (Int) -> Unit = {}): List<ChannelEntity> = withContext(Dispatchers.IO) {
        val channels = mutableListOf<ChannelEntity>()

        BufferedReader(InputStreamReader(inputStream)).use { reader ->
            var pendingName: String? = null
            var pendingLogo: String? = null
            var pendingGroup: String? = null
            var lineCount = 0

            reader.lineSequence().forEach { rawLine ->
                lineCount++
                if (lineCount % 100 == 0) onProgress(lineCount)

                val line = rawLine.trim()
                if (line.isEmpty()) return@forEach

                when {
                    line.startsWith("#EXTINF") -> {
                        pendingName = nameRegex.find(line)?.groupValues?.get(1)?.trim() ?: "Untitled"
                        pendingLogo = logoRegex.find(line)?.groupValues?.get(1)
                        pendingGroup = groupRegex.find(line)?.groupValues?.get(1)
                    }
                    line.startsWith("#") -> {
                        // ignore metadata
                    }
                    else -> {
                        val name = pendingName ?: line
                        channels.add(ChannelEntity(
                            url = line,
                            name = name,
                            logoUrl = pendingLogo,
                            groupTitle = pendingGroup,
                            profileId = profileId
                        ))
                        pendingName = null
                        pendingLogo = null
                        pendingGroup = null
                    }
                }
            }
        }
        channels
    }
}

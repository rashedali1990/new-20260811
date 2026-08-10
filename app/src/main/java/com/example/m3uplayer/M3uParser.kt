package com.example.m3uplayer

object M3uParser {

    private val nameRegex = Regex(",(.*)$")
    private val logoRegex = Regex("tvg-logo=\"([^\"]*)\"")
    private val groupRegex = Regex("group-title=\"([^\"]*)\"")

    fun parse(playlistText: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        val lines = playlistText.lines()

        var pendingName: String? = null
        var pendingLogo: String? = null
        var pendingGroup: String? = null

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            when {
                line.startsWith("#EXTINF") -> {
                    pendingName = nameRegex.find(line)?.groupValues?.get(1)?.trim() ?: "Untitled"
                    pendingLogo = logoRegex.find(line)?.groupValues?.get(1)
                    pendingGroup = groupRegex.find(line)?.groupValues?.get(1)
                }
                line.startsWith("#") -> {
                    // ignore other metadata tags
                }
                else -> {
                    val name = pendingName ?: line
                    channels.add(Channel(name = name, url = line, logoUrl = pendingLogo, groupTitle = pendingGroup))
                    pendingName = null
                    pendingLogo = null
                    pendingGroup = null
                }
            }
        }

        return channels
    }
}

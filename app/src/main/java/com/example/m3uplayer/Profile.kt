package com.example.m3uplayer

import org.json.JSONObject

data class Profile(
    val id: String,
    val profileName: String,
    val serverUrl: String,
    val username: String,
    val password: String,
    val proxyHost: String = "",
    val proxyPort: Int = 0,
    val proxyType: String = "HTTP", // "HTTP" أو "SOCKS5"
    val dnsServer: String = "dns.adguard.com"
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("profileName", profileName)
        put("serverUrl", serverUrl)
        put("username", username)
        put("password", password)
        put("proxyHost", proxyHost)
        put("proxyPort", proxyPort)
        put("proxyType", proxyType)
        put("dnsServer", dnsServer)
    }

    companion object {
        fun fromJson(json: JSONObject): Profile = Profile(
            id = json.getString("id"),
            profileName = json.getString("profileName"),
            serverUrl = json.getString("serverUrl"),
            username = json.getString("username"),
            password = json.getString("password"),
            proxyHost = json.optString("proxyHost", ""),
            proxyPort = json.optInt("proxyPort", 0),
            proxyType = json.optString("proxyType", "HTTP"),
            dnsServer = json.optString("dnsServer", "")
        )
    }
}

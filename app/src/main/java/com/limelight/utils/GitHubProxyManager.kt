package com.limelight.utils

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

/** GitHub proxy fallbacks used by remote background images. */
object GitHubProxyManager {
    private const val TAG = "GitHubProxyManager"
    private const val PROXY_DISCOVERY_URL =
        "https://ghproxy.link/js/src_views_home_HomeView_vue.js"
    private const val PROXY_PREFS = "github_proxy_prefs"
    private const val PREF_LAST_PROXY_UPDATE_TIME = "last_proxy_update_time"
    private const val PROXY_CACHE_DURATION = 24 * 60 * 60 * 1000L

    @Volatile
    private var proxyPrefixes: Array<String> = emptyArray()

    fun buildProxiedUrls(url: String): List<String> = buildList {
        proxyPrefixes.forEach { add(it + url) }
        add(url)
    }

    fun ensureProxyListUpdated(context: Context) {
        if (shouldUpdateProxyList(context)) {
            updateProxyList(context)
        }
    }

    private fun shouldUpdateProxyList(context: Context): Boolean {
        val lastUpdateTime = context.getSharedPreferences(PROXY_PREFS, Context.MODE_PRIVATE)
            .getLong(PREF_LAST_PROXY_UPDATE_TIME, 0)
        return System.currentTimeMillis() - lastUpdateTime > PROXY_CACHE_DURATION ||
            proxyPrefixes.isEmpty()
    }

    private fun updateProxyList(context: Context) {
        try {
            val scriptContent = fetchScriptContent() ?: return
            val newProxies = extractProxiesFromScript(scriptContent)
            if (newProxies.isEmpty()) return

            proxyPrefixes = (proxyPrefixes.toSet() + newProxies).toTypedArray()
            context.getSharedPreferences(PROXY_PREFS, Context.MODE_PRIVATE).edit {
                putLong(PREF_LAST_PROXY_UPDATE_TIME, System.currentTimeMillis())
            }
            Log.d(TAG, "Proxy list updated with ${proxyPrefixes.size} entries")
        } catch (e: Exception) {
            Log.w(TAG, "Unable to update proxy list: ${e.message}")
        }
    }

    private fun fetchScriptContent(): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(PROXY_DISCOVERY_URL).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:40.0)")
            connection.connectTimeout = 2000
            connection.readTimeout = 2000
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null

            BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to fetch proxy discovery script: ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun extractProxiesFromScript(scriptContent: String): Array<String> {
        val proxies = mutableSetOf<String>()
        try {
            val topLevelDomains =
                "com|net|org|cn|top|cc|io|me|cf|tk|ml|ga|gg|xyz|site|online|tech|info|biz|" +
                    "work|space|shop|club|pro|dev|app|link|run|art|fun|live|store|world|today|" +
                    "design|cloud"
            val patterns = arrayOf(
                "[\"']https://[\\w.-]+\\.(?:$topLevelDomains)/[\"']",
                "baseUrl\\s*=\\s*[\"']https://[\\w.-]+\\.(?:$topLevelDomains)/[\"']",
                "url:\\s*[\"']https://[\\w.-]+\\.(?:$topLevelDomains)/[\"']",
                "[\"']https://(?:gh|mirror|proxy|cdn)[\\w.-]*\\.(?:$topLevelDomains)/[\"']",
            )

            patterns.forEach { patternString ->
                val matcher = Pattern.compile(patternString, Pattern.CASE_INSENSITIVE)
                    .matcher(scriptContent)
                while (matcher.find()) {
                    var proxyUrl = matcher.group().replace("[\"']".toRegex(), "")
                    if (!proxyUrl.startsWith("https://")) continue
                    if (!proxyUrl.endsWith('/')) proxyUrl += "/"
                    if (isValidProxyUrl(proxyUrl)) proxies.add(proxyUrl)
                }
            }

            val domainMatcher = Pattern.compile(
                "(?:proxy|mirror|gh|cdn)[\\w.-]*\\.(?:$topLevelDomains)",
                Pattern.CASE_INSENSITIVE,
            ).matcher(scriptContent)
            while (domainMatcher.find()) {
                val proxyUrl = "https://${domainMatcher.group()}/"
                if (isValidProxyUrl(proxyUrl)) proxies.add(proxyUrl)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to parse proxy addresses: ${e.message}")
        }
        return proxies.toTypedArray()
    }

    private fun isValidProxyUrl(url: String): Boolean {
        if (url.length !in 15..100) return false
        val blacklist = arrayOf(
            "github.com",
            "googleapis.com",
            "gstatic.com",
            "jquery.com",
            "bootstrap.com",
            "cdnjs.com",
            "unpkg.com",
            "jsdelivr.net",
            "ghproxy.link",
        )
        return blacklist.none(url::contains) && !redirectsToGhproxyLink(url)
    }

    private fun redirectsToGhproxyLink(proxyUrl: String): Boolean {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(proxyUrl + "https://api.github.com/zen")
                .openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:40.0)")
            connection.connectTimeout = 2000
            connection.readTimeout = 2000

            val responseCode = connection.responseCode
            val location = connection.getHeaderField("Location")
            val server = connection.getHeaderField("Server")
            (responseCode in 300..399 && location?.contains("ghproxy.link") == true) ||
                connection.url.toString().contains("ghproxy.link") ||
                server?.contains("ghproxy", ignoreCase = true) == true
        } catch (e: java.net.SocketTimeoutException) {
            true
        } catch (e: java.net.ConnectException) {
            true
        } catch (e: Exception) {
            Log.d(TAG, "Proxy validation failed without a redirect: ${e.message}")
            false
        } finally {
            connection?.disconnect()
        }
    }
}

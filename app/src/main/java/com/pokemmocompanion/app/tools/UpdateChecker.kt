package com.pokemmocompanion.app.tools

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Checks GitHub for a newer release. The automatic check runs when the app opens, at most every [AUTO_INTERVAL_MS],
 * and only reports a newer version the user hasn't skipped; nothing is shown otherwise.
 */
object UpdateChecker {
  private const val PREFS = "updates"
  private const val AUTO = "auto"
  private const val LAST = "lastCheck"
  private const val SKIPPED = "skipped"
  private const val AUTO_INTERVAL_MS = 12 * 60 * 60 * 1000L

  fun installedVersion(context: Context): String =
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"

  fun autoEnabled(context: Context) = context.getSharedPreferences(PREFS, 0).getBoolean(AUTO, true)

  fun setAuto(context: Context, on: Boolean) = context.getSharedPreferences(PREFS, 0).edit().putBoolean(AUTO, on).apply()

  fun skip(context: Context, version: String) = context.getSharedPreferences(PREFS, 0).edit().putString(SKIPPED, version).apply()

  /** The latest published release, or null when it can't be reached (offline, rate-limited, no release yet). */
  suspend fun latest(): Release? =
    withContext(Dispatchers.IO) {
      try {
        val conn = URL(Updates.LATEST_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "PokeMMO-Companion")
        try {
          if (conn.responseCode != 200) null else Updates.parse(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
          conn.disconnect()
        }
      } catch (_: Exception) {
        null
      }
    }

  /** The automatic check: a newer, not-skipped release, or null (including when it isn't time to check yet). */
  suspend fun autoCheck(context: Context): Release? {
    val prefs = context.getSharedPreferences(PREFS, 0)
    if (!prefs.getBoolean(AUTO, true)) return null
    val now = System.currentTimeMillis()
    if (now - prefs.getLong(LAST, 0) < AUTO_INTERVAL_MS) return null
    val release = latest() ?: return null
    prefs.edit().putLong(LAST, now).apply()
    if (!Updates.isNewer(release.version, installedVersion(context))) return null
    if (prefs.getString(SKIPPED, null) == release.version) return null
    return release
  }
}

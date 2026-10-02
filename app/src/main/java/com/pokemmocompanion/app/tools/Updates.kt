package com.pokemmocompanion.app.tools

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A GitHub release: its version (from the tag, "v0.75" → "0.75"), notes, page and APK download. */
data class Release(val version: String, val notes: String, val pageUrl: String, val apkUrl: String?)

/**
 * Update checks against the project's GitHub releases (the latest published release). Plain Kotlin so it can be
 * tested; the network call is [UpdateChecker]'s job.
 */
object Updates {
  const val REPO = "PokeMMOCompanion/pokemmo-companion"
  const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"

  @Serializable private data class Asset(val name: String = "", val browser_download_url: String = "")

  @Serializable
  private data class GitHubRelease(
    val tag_name: String = "",
    val body: String? = null,
    val html_url: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<Asset> = emptyList(),
  )

  private val json = Json { ignoreUnknownKeys = true }

  /** The release in GitHub's "latest release" JSON, or null for drafts, pre-releases or a tag that isn't a version. */
  fun parse(text: String): Release? {
    val r = json.decodeFromString<GitHubRelease>(text)
    if (r.draft || r.prerelease) return null
    val version = r.tag_name.trim().removePrefix("v").removePrefix("V").takeIf { parts(it) != null } ?: return null
    val apk = r.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }?.browser_download_url
    return Release(version, r.body.orEmpty().trim(), r.html_url, apk)
  }

  /** True when [remote] is a higher version than [installed] ("0.75" > "0.74", "1.0" > "0.99", "0.10" > "0.9"). */
  fun isNewer(remote: String, installed: String): Boolean {
    val a = parts(remote) ?: return false
    val b = parts(installed) ?: return true
    for (i in 0 until maxOf(a.size, b.size)) {
      val x = a.getOrElse(i) { 0 }
      val y = b.getOrElse(i) { 0 }
      if (x != y) return x > y
    }
    return false
  }

  private fun parts(v: String): List<Int>? = v.split('.').map { it.toIntOrNull() ?: return null }.takeIf { it.isNotEmpty() }
}

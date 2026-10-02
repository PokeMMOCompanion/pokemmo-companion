package com.pokemmocompanion.app.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesTest {
  @Test
  fun comparesVersions() {
    assertTrue(Updates.isNewer("0.75", "0.74"))
    assertTrue(Updates.isNewer("0.10", "0.9"))
    assertTrue(Updates.isNewer("1.0", "0.99"))
    assertTrue(Updates.isNewer("0.74.1", "0.74"))
    assertFalse(Updates.isNewer("0.74", "0.74"))
    assertFalse(Updates.isNewer("0.73", "0.74"))
    assertFalse(Updates.isNewer("beta", "0.74"))
  }

  @Test
  fun parsesGitHubRelease() {
    val r =
      Updates.parse(
        """{"tag_name":"v0.75","body":"New: things","html_url":"https://github.com/x/y/releases/tag/v0.75","draft":false,"prerelease":false,
          "assets":[{"name":"Source.zip","browser_download_url":"https://x/zip"},{"name":"PokeMMO-Companion-0.75.apk","browser_download_url":"https://x/apk"}]}"""
      )!!
    assertEquals("0.75", r.version)
    assertEquals("https://x/apk", r.apkUrl)
    assertEquals("New: things", r.notes)
    assertNull(Updates.parse("""{"tag_name":"v0.76","draft":true}"""))
    assertNull(Updates.parse("""{"tag_name":"nightly"}"""))
  }
}

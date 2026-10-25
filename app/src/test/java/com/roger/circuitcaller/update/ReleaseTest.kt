package com.roger.circuitcaller.update

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseTest {

    private val digest = "sha256:" + "ab".repeat(32)

    private fun releaseJson(
        tag: String = "1.2",
        body: String? = "\"## What's Changed\\n* Faster timer\"",
        assets: String = apkAsset()
    ) = """{"tag_name": "$tag", "body": ${body ?: "null"}, "assets": [$assets]}"""

    private fun apkAsset(name: String = "circuit-caller-1.2.apk", digest: String? = this.digest) = """
        {"name": "$name", "size": 11705896,
         "browser_download_url": "https://github.com/RMac0001/circuit-caller/releases/download/1.2/$name",
         "digest": ${if (digest == null) "null" else "\"$digest\""}}
    """.trimIndent()

    // ------------------------------------------------------------------ versions

    @Test
    fun newerVersionIsOffered() {
        assertTrue(Release.isNewer("1.2", "1.1"))
        assertTrue(Release.isNewer("2.0", "1.9.9"))
        assertTrue(Release.isNewer("1.1.1", "1.1"))
    }

    @Test
    fun equalVersionIsNotOffered() {
        assertFalse(Release.isNewer("1.1", "1.1"))
        assertFalse(Release.isNewer("1.1.0", "1.1"))
        assertFalse(Release.isNewer("1.1", "1.1.0"))
        assertEquals(0, Release.compareVersions("1.1", "1.1.0"))
    }

    @Test
    fun olderVersionIsNotOffered() {
        assertFalse(Release.isNewer("1.0", "1.1"))
        assertFalse(Release.isNewer("0.9.9", "1.0"))
    }

    @Test
    fun partsCompareNumericallyNotAlphabetically() {
        assertTrue(Release.isNewer("1.10", "1.9"))
        assertFalse(Release.isNewer("1.9", "1.10"))
        assertTrue(Release.compareVersions("10.0", "9.0") > 0)
    }

    @Test
    fun tagPrefixIsIgnored() {
        assertEquals("1.2", Release.normalizeTag("v1.2"))
        assertEquals("1.2", Release.normalizeTag("V1.2"))
        assertEquals("1.2", Release.normalizeTag(" 1.2 "))
        assertEquals(0, Release.compareVersions("v1.1", "1.1"))
        assertTrue(Release.isNewer("v1.2", "1.1"))
        assertEquals("1.2", Release.parse(releaseJson(tag = "v1.2")).getOrThrow().version)
        assertEquals("1.2", Release.parse(releaseJson(tag = "1.2")).getOrThrow().version)
    }

    @Test
    fun suffixesUseTheirLeadingDigits() {
        assertEquals(0, Release.compareVersions("1.2-beta", "1.2"))
        assertTrue(Release.isNewer("1.3-rc1", "1.2"))
    }

    // ------------------------------------------------------------------ parsing

    @Test
    fun parsesTheLatestReleaseResponse() {
        val info = Release.parse(releaseJson()).getOrThrow()
        assertEquals("1.2", info.version)
        assertEquals("## What's Changed\n* Faster timer", info.notes)
        assertEquals(
            "https://github.com/RMac0001/circuit-caller/releases/download/1.2/circuit-caller-1.2.apk",
            info.apkUrl
        )
        assertEquals(11705896L, info.apkSize)
        assertEquals("ab".repeat(32), info.sha256)
    }

    @Test
    fun picksTheFirstApkAsset() {
        val assets = """{"name": "notes.txt", "browser_download_url": "https://x/notes.txt"},""" +
            apkAsset("first.apk") + "," + apkAsset("second.apk")
        assertTrue(Release.parse(releaseJson(assets = assets)).getOrThrow().apkUrl.endsWith("/first.apk"))
    }

    @Test
    fun releaseWithoutApkFails() {
        val noApk = releaseJson(assets = """{"name": "source.zip", "browser_download_url": "https://x/source.zip"}""")
        assertEquals("Release 1.2 has no APK.", Release.parse(noApk).exceptionOrNull()?.message)
        assertTrue(Release.parse("""{"tag_name": "1.2", "assets": []}""").isFailure)
        assertTrue(Release.parse("""{"tag_name": "1.2"}""").isFailure)
    }

    @Test
    fun releaseWithoutDigestHasNoChecksum() {
        assertNull(Release.parse(releaseJson(assets = apkAsset(digest = null))).getOrThrow().sha256)
        val missing = releaseJson(
            assets = """{"name": "a.apk", "size": 1, "browser_download_url": "https://x/a.apk"}"""
        )
        assertNull(Release.parse(missing).getOrThrow().sha256)
    }

    @Test
    fun digestMustBeSha256Hex() {
        assertEquals("ab".repeat(32), Release.parseDigest("SHA256:" + "AB".repeat(32)))
        assertNull(Release.parseDigest("md5:abc"))
        assertNull(Release.parseDigest("sha256:xyz"))
        assertNull(Release.parseDigest("sha256:" + "ab".repeat(31)))
        assertNull(Release.parseDigest(""))
        assertNull(Release.parseDigest(null))
    }

    @Test
    fun missingBodyBecomesEmptyNotes() {
        assertEquals("", Release.parse(releaseJson(body = null)).getOrThrow().notes)
    }

    @Test
    fun malformedJsonFails() {
        assertTrue(Release.parse("not json").isFailure)
        assertTrue(Release.parse("").isFailure)
        assertTrue(Release.parse("""{"tag_name": "1.2", "assets": [""").isFailure)
        assertTrue(Release.parse("""{"message": "API rate limit exceeded"}""").isFailure)
    }

    // ------------------------------------------------------------------ helpers

    @Test
    fun notesPreviewTidiesMarkdown() {
        val notes = "## What's Changed\n\n* Faster timer\n- New voice\nLine four\nLine five"
        assertEquals("What's Changed\n• Faster timer\n• New voice\nLine four", Release.notesPreview(notes))
        assertEquals("", Release.notesPreview("\n\n"))
    }

    @Test
    fun hexMatchesKnownSha256() {
        val bytes = MessageDigest.getInstance("SHA-256").digest("abc".toByteArray())
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Release.hex(bytes))
    }
}

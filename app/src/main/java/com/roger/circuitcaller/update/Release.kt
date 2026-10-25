package com.roger.circuitcaller.update

import org.json.JSONObject

/** The parts of a GitHub release the updater needs. */
data class ReleaseInfo(
    /** Tag with any leading "v" removed, e.g. "1.2". */
    val version: String,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
    /** Lowercase hex SHA-256 of the APK, or null when GitHub didn't report one. */
    val sha256: String?
)

/**
 * Plain Kotlin so it can be unit-tested on the JVM: parsing the releases API response,
 * comparing versions and hashing the download.
 */
object Release {

    /**
     * Parses `GET /repos/{owner}/{repo}/releases/latest`. Fails when the JSON is malformed,
     * has no tag, or has no `.apk` asset, since there is nothing to install in any of those cases.
     */
    fun parse(json: String): Result<ReleaseInfo> = runCatching {
        val root = JSONObject(json)
        val tag = root.optString("tag_name", "")
        require(tag.isNotBlank()) { "Release has no tag." }

        val assets = root.optJSONArray("assets")
        val apk = (0 until (assets?.length() ?: 0))
            .map { assets!!.getJSONObject(it) }
            .firstOrNull { it.optString("name", "").endsWith(".apk", ignoreCase = true) }
            ?: throw IllegalArgumentException("Release $tag has no APK.")

        val url = apk.optString("browser_download_url", "")
        require(url.isNotBlank()) { "Release $tag has no download link." }

        ReleaseInfo(
            version = normalizeTag(tag),
            // JSONObject.optString turns a JSON null into "null".
            notes = if (root.isNull("body")) "" else root.optString("body", ""),
            apkUrl = url,
            apkSize = apk.optLong("size", -1L),
            sha256 = parseDigest(if (apk.isNull("digest")) null else apk.optString("digest"))
        )
    }

    fun normalizeTag(tag: String): String = tag.trim().removePrefix("v").removePrefix("V")

    /** "sha256:<64 hex>" → lowercase hex; anything else → null. */
    fun parseDigest(digest: String?): String? {
        val hex = digest?.trim()?.takeIf { it.startsWith("sha256:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.lowercase()
            ?: return null
        return hex.takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
    }

    /**
     * Compares dotted versions numerically, part by part, so 1.10 > 1.9 and 1.1 == 1.1.0.
     * A leading "v" is ignored, and a part's leading digits are its value ("2-beta" is 2).
     */
    fun compareVersions(a: String, b: String): Int {
        val pa = parts(a)
        val pb = parts(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val c = pa.getOrElse(i) { 0 }.compareTo(pb.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }

    fun isNewer(candidate: String, installed: String): Boolean = compareVersions(candidate, installed) > 0

    private fun parts(version: String): List<Long> = normalizeTag(version)
        .split('.')
        .map { part -> part.takeWhile { it.isDigit() }.take(18).toLongOrNull() ?: 0L }

    /** The first few non-blank lines of the notes, with Markdown headings and bullets tidied. */
    fun notesPreview(notes: String, maxLines: Int = 4): String = notes.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { line ->
            when {
                line.startsWith("#") -> line.trimStart('#').trim()
                line.startsWith("* ") || line.startsWith("- ") -> "• " + line.substring(2)
                else -> line
            }
        }
        .take(maxLines)
        .joinToString("\n")

    /** Lowercase hex, the form [parseDigest] returns, for comparing a computed digest. */
    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}

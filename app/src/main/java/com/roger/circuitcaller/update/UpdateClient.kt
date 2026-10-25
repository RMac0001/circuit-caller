package com.roger.circuitcaller.update

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.DigestInputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Talks to GitHub Releases with plain HttpURLConnection; no extra dependencies.
 * Error messages are short enough to show as a snackbar.
 */
class UpdateClient(private val versionName: String) {

    companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/RMac0001/circuit-caller/releases/latest"
        private const val TIMEOUT_MS = 10_000
    }

    /** A failure whose message is fit to show the user as is. */
    class UpdateException(message: String) : IOException(message)

    suspend fun fetchLatest(): Result<ReleaseInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = open(LATEST_RELEASE_URL)
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            try {
                when (val code = conn.responseCode) {
                    HttpURLConnection.HTTP_OK -> Unit

                    HttpURLConnection.HTTP_FORBIDDEN, 429 ->
                        throw UpdateException("GitHub's rate limit was reached. Try again later.")

                    HttpURLConnection.HTTP_NOT_FOUND -> throw UpdateException("No releases found.")

                    else -> throw UpdateException("GitHub returned HTTP $code.")
                }
                conn.inputStream.bufferedReader().use { it.readText() }
            } catch (e: UpdateException) {
                throw e
            } catch (e: IOException) {
                throw UpdateException("Couldn't reach GitHub. Check your connection.")
            } finally {
                conn.disconnect()
            }
        }.mapCatching { json ->
            Release.parse(json).getOrElse { e ->
                throw UpdateException(
                    (e as? IllegalArgumentException)?.message ?: "GitHub's reply couldn't be read."
                )
            }
        }
    }

    /**
     * Streams the APK to [dir]/circuit-caller-<version>.apk, reporting 0–100 to [onProgress]
     * (null when the size is unknown). GitHub's download link redirects to its asset CDN;
     * HttpURLConnection follows redirects that stay on HTTPS. When the release has a SHA-256
     * digest, a mismatch deletes the file and fails.
     */
    suspend fun download(release: ReleaseInfo, dir: File, onProgress: (Int?) -> Unit): Result<File> =
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            val file = File(dir, "circuit-caller-${release.version}.apk")
            runCatching {
                val conn = open(release.apkUrl)
                conn.instanceFollowRedirects = true
                try {
                    val code = conn.responseCode
                    if (code != HttpURLConnection.HTTP_OK) throw UpdateException("Download failed (HTTP $code).")
                    val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.apkSize.takeIf { it > 0 }
                    val digest = MessageDigest.getInstance("SHA-256")
                    DigestInputStream(conn.inputStream, digest).use { input ->
                        file.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var written = 0L
                            var lastPercent = -1
                            onProgress(if (total != null) 0 else null)
                            while (true) {
                                ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                written += read
                                if (total != null) {
                                    val percent = (written * 100 / total).toInt().coerceIn(0, 100)
                                    if (percent != lastPercent) {
                                        lastPercent = percent
                                        onProgress(percent)
                                    }
                                }
                            }
                        }
                    }
                    val actual = Release.hex(digest.digest())
                    if (release.sha256 != null && actual != release.sha256) {
                        throw UpdateException("The download was corrupted (checksum mismatch).")
                    }
                    file
                } catch (e: UpdateException) {
                    throw e
                } catch (e: IOException) {
                    throw UpdateException("Download failed. Check your connection.")
                } finally {
                    conn.disconnect()
                }
            }.onFailure { file.delete() }
        }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = TIMEOUT_MS
        readTimeout = TIMEOUT_MS
        setRequestProperty("User-Agent", "CircuitCaller/$versionName")
    }
}

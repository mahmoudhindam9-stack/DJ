package com.example

import com.example.updater.GitHubUpdater
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GitHubUpdaterTest {

    @Test
    fun testVersionComparison() {
        assertTrue(GitHubUpdater.isVersionGreater("1.7", "1.6"))
        assertTrue(GitHubUpdater.isVersionGreater("2.0.0", "1.9.9"))
        assertTrue(GitHubUpdater.isVersionGreater("v1.7.1", "1.7.0"))
        assertFalse(GitHubUpdater.isVersionGreater("1.6", "1.6"))
        assertFalse(GitHubUpdater.isVersionGreater("1.5", "1.6"))
        assertFalse(GitHubUpdater.isVersionGreater("v1.0", "v1.0"))
        assertTrue(GitHubUpdater.isVersionGreater("v2.1", "v2.0"))
    }

    @Test
    fun testSelectReleaseApkPriorityAndFiltering() {
        val json = JSONObject()
        json.put("body", "Release notes\nSHA256: e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
        
        val assets = JSONArray()
        val debugAsset = JSONObject().apply {
            put("name", "app-debug.apk")
            put("browser_download_url", "https://github.com/mahmoudhindam9-stack/DJ/releases/download/v1.7/app-debug.apk")
        }
        val unsignedAsset = JSONObject().apply {
            put("name", "app-unsigned.apk")
            put("browser_download_url", "https://github.com/mahmoudhindam9-stack/DJ/releases/download/v1.7/app-unsigned.apk")
        }
        val releaseAsset = JSONObject().apply {
            put("name", "app-release.apk")
            put("browser_download_url", "https://github.com/mahmoudhindam9-stack/DJ/releases/download/v1.7/app-release.apk")
        }
        assets.put(debugAsset)
        assets.put(unsignedAsset)
        assets.put(releaseAsset)
        json.put("assets", assets)

        val selected = GitHubUpdater.selectReleaseApk(json)
        assertNotNull(selected)
        assertEquals("app-release.apk", selected?.fileName)
        assertEquals("https://github.com/mahmoudhindam9-stack/DJ/releases/download/v1.7/app-release.apk", selected?.apkUrl)
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", selected?.expectedSha256)
    }

    @Test
    fun testSelectReleaseApkReturnsNullWhenOnlyDebugOrUnsigned() {
        val json = JSONObject()
        val assets = JSONArray()
        val debugAsset = JSONObject().apply {
            put("name", "app-debug.apk")
            put("browser_download_url", "https://github.com/mahmoudhindam9-stack/DJ/releases/download/v1.7/app-debug.apk")
        }
        val unalignedAsset = JSONObject().apply {
            put("name", "app-unaligned.apk")
            put("browser_download_url", "https://github.com/mahmoudhindam9-stack/DJ/releases/download/v1.7/app-unaligned.apk")
        }
        val idsigAsset = JSONObject().apply {
            put("name", "app-release.apk.idsig")
            put("browser_download_url", "https://github.com/mahmoudhindam9-stack/DJ/releases/download/v1.7/app-release.apk.idsig")
        }
        assets.put(debugAsset)
        assets.put(unalignedAsset)
        assets.put(idsigAsset)
        json.put("assets", assets)

        val selected = GitHubUpdater.selectReleaseApk(json)
        assertNull(selected)
    }

    @Test
    fun testExtractSha256FromBody() {
        val hash = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"
        val body = "Release v1.7\nChanges in this release:\n- Security hardening\n\nChecksums:\napp-release.apk: $hash"

        val extracted = GitHubUpdater.extractSha256FromBody(body, "app-release.apk")
        assertEquals(hash, extracted)
    }

    @Test
    fun testFileSha256AndIntegrityVerification() {
        val tempFile = File.createTempFile("test_apk", ".apk")
        try {
            tempFile.writeText("sample apk binary content for verification")
            val computedHash = GitHubUpdater.computeFileSha256(tempFile)
            assertNotNull(computedHash)
            assertEquals(64, computedHash.length)

            assertTrue(GitHubUpdater.verifyApkIntegrity(tempFile, computedHash))
            assertFalse(GitHubUpdater.verifyApkIntegrity(tempFile, "0000000000000000000000000000000000000000000000000000000000000000"))
        } finally {
            tempFile.delete()
        }
    }
}

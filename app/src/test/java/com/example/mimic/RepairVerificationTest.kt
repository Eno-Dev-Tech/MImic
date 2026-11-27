package com.example.mimic

import com.example.mimic.update.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RepairVerificationTest {
    private val cert = "a".repeat(64)
    private val version = "4.8.1-beta.2"
    private val code = AppVersion.versionCodeFor(version).toLong()
    private val name = "MImic-v$version-full-x86_64.apk"
    private fun check(archiveCode: Long = code, repair: Boolean = false, signer: String = cert, actualVersion: String = version) =
        UpdateVerification.check("com.example.mimic", "com.example.mimic", archiveCode, code,
            setOf(signer), setOf(cert), cert, "https://github.com/Eno-Dev-Tech/MImic", repair, actualVersion, version)

    @Test fun equalVersionRequiresExplicitRepairAndDowngradesAlwaysFail() {
        assertNotNull(check())
        assertNull(check(repair = true))
        assertNotNull(check(archiveCode = code - 1, repair = true))
        assertNotNull(check(repair = true, signer = "b".repeat(64)))
        assertNotNull(check(repair = true, actualVersion = "4.8.0"))
    }

    private fun release(v: String, protected: Boolean = true) = JSONObject().put("tag_name", "v$v")
        .put("assets", JSONArray(listOf("MImic-v$v-full-x86_64.apk", "SHA256SUMS.txt").plus(if (protected) listOf("protection-report.json") else emptyList()).map {
            JSONObject().put("name", it).put("browser_download_url", "https://github.com/Eno-Dev-Tech/MImic/releases/download/v$v/$it")
        }))

    @Test fun repairPrefersNewerProtectedReleaseThenSameVersionAndHonorsChannel() {
        val payload = JSONArray(listOf(release("4.8.0"), release(version), release("4.8.1-beta.3"), release("4.8.2", false))).toString()
        val candidates = ReleaseParser.repairCandidates(payload, UpdateChannel.BETA, "full", listOf("x86_64"), version, code)
        assertEquals(listOf("4.8.1-beta.3", version), candidates.map { it.version.toString() })
        assertTrue(candidates.all { it.repair })
        assertTrue(ReleaseParser.repairCandidates(payload, UpdateChannel.STABLE, "full", listOf("x86_64"), version, code).isEmpty())
    }

    private fun report(minSdk: Int = 26) = JSONObject().put("schema", "mimic-bla-release-v1").put("version", version)
        .put("artifacts", JSONObject().put(name, JSONObject().put("sha256", "b".repeat(64)).put("certificateSha256", cert)
            .put("r8", true).put("intensity", "stealth").put("blaVersion", "3.6.1").put("minSdk", minSdk).put("dexCount", 1))).toString()

    @Test fun protectedRepairRequiresCompatibleReportAndExactSignedDexReceipt() {
        val embedded = JSONObject().put("schema", "mimic-bla-apk-v1").put("r8", true).put("intensity", "stealth").put("blaVersion", "3.6.1")
            .put("dex", JSONObject().put("classes.dex", JSONObject().put("protectedSha256", "c".repeat(64)).put("originalProgramUnchanged", true).put("addedBytes", 3801)))
        fun matches(text: String = embedded.toString(), hashes: Map<String, String> = mapOf("classes.dex" to "c".repeat(64))) =
            ReleaseProtection.matchesApk(report(), text, version, name, "b".repeat(64), cert, 37, hashes)
        assertTrue(matches())
        assertFalse(matches(hashes = mapOf("classes.dex" to "d".repeat(64))))
        assertFalse(matches(hashes = mapOf("classes2.dex" to "c".repeat(64))))
        assertFalse(matches(text = "{}"))
        assertFalse(ReleaseProtection.compatibleReport(report(38), version, name, cert, 37))
        assertFalse(ReleaseProtection.compatibleReport(report(), version, name, "e".repeat(64), 37))
    }
}

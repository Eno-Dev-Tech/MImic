package com.example.mimic.update

import org.json.JSONObject

/** Signed-APK manifest + official report admission. Host SDK/BLA validation remains in CI/release. */
internal object ReleaseProtection {
    const val MANIFEST = "assets/mimic-bla-verification.json"
    const val MAX_REPORT_BYTES = 1_048_576

    fun compatibleReport(json: String, version: String, apkName: String, certificate: String, sdk: Int): Boolean =
        runCatching {
            val report = JSONObject(json)
            val entry = report.getJSONObject("artifacts").getJSONObject(apkName)
            report.getString("schema") == "mimic-bla-release-v1" && report.getString("version") == version &&
                entry.getString("certificateSha256").equals(certificate, true) && entry.getBoolean("r8") &&
                entry.getString("intensity") == "stealth" && entry.getString("blaVersion").isNotBlank() &&
                entry.getString("sha256").matches(Regex("[0-9a-fA-F]{64}")) &&
                entry.getInt("minSdk") in 1..sdk
        }.getOrDefault(false)

    fun matchesApk(reportJson: String, embeddedJson: String, version: String, apkName: String,
                   apkSha256: String, certificate: String, sdk: Int, dexHashes: Map<String, String>): Boolean =
        runCatching {
            if (!compatibleReport(reportJson, version, apkName, certificate, sdk)) return false
            val entry = JSONObject(reportJson).getJSONObject("artifacts").getJSONObject(apkName)
            val embedded = JSONObject(embeddedJson)
            val dex = embedded.getJSONObject("dex")
            val ordered = dexHashes.keys.sortedBy { if (it == "classes.dex") 1 else it.removePrefix("classes").removeSuffix(".dex").toInt() }
            val expected = (1..ordered.size).map { if (it == 1) "classes.dex" else "classes$it.dex" }
            entry.getString("sha256").equals(apkSha256, true) && embedded.getString("schema") == "mimic-bla-apk-v1" &&
                embedded.getBoolean("r8") && embedded.getString("intensity") == "stealth" &&
                embedded.getString("blaVersion") == entry.getString("blaVersion") &&
                ordered.isNotEmpty() && ordered == expected && dex.keys().asSequence().toSet() == dexHashes.keys &&
                entry.getInt("dexCount") == ordered.size && ordered.all { name ->
                    val record = dex.getJSONObject(name)
                    record.getBoolean("originalProgramUnchanged") && record.getLong("addedBytes") > 0 &&
                        record.getString("protectedSha256").equals(dexHashes.getValue(name), true)
                }
        }.getOrDefault(false)
}

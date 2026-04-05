package com.inversioncoach.app.drillpackage.mapping

import com.inversioncoach.app.drillpackage.io.DrillPackageJsonCodec
import com.inversioncoach.app.drillpackage.model.DrillManifest
import com.inversioncoach.app.drillpackage.model.DrillPackage
import com.inversioncoach.app.drillpackage.model.PortableDrill
import com.inversioncoach.app.drillpackage.model.SchemaVersion
import java.util.Base64

internal object PortableDrillLegacyPayloadCodec {
    private const val PORTABLE_PAYLOAD_KEY = "portablePayload"

    fun decodeFromCueConfig(cueConfig: String?): PortableDrill? {
        val payload = parseTokens(cueConfig)[PORTABLE_PAYLOAD_KEY] ?: return null
        return runCatching {
            val raw = String(Base64.getUrlDecoder().decode(payload))
            val decoded = DrillPackageJsonCodec.decode(raw)
            decoded.drills.firstOrNull()
        }.getOrNull()
    }

    fun mergeIntoCueConfig(existingCueConfig: String?, portable: PortableDrill): String {
        val payload = encodePortableDrill(portable)
        val existingTokens = parseTokens(existingCueConfig).toMutableMap()
        existingTokens[PORTABLE_PAYLOAD_KEY] = payload
        return existingTokens.entries.joinToString("|") { (key, value) -> "$key:$value" }
    }

    private fun encodePortableDrill(drill: PortableDrill): String {
        val serializable = drill.copy(
            extensions = drill.extensions.filterKeys { it != "cueConfig" },
        )
        val raw = DrillPackageJsonCodec.encode(
            DrillPackage(
                manifest = DrillManifest(
                    packageId = "portable_legacy_bridge",
                    schemaVersion = SchemaVersion(major = 1, minor = 0),
                    source = "android_legacy_bridge",
                    exportedAtMs = 0L,
                ),
                drills = listOf(serializable),
            ),
        )
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray())
    }

    private fun parseTokens(cueConfig: String?): Map<String, String> = cueConfig
        .orEmpty()
        .split('|')
        .asSequence()
        .mapNotNull { token ->
            val idx = token.indexOf(':')
            if (idx <= 0 || idx == token.lastIndex) null else token.substring(0, idx) to token.substring(idx + 1)
        }
        .toMap()
}

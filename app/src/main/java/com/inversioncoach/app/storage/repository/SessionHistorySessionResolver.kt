package com.inversioncoach.app.storage.repository

import com.inversioncoach.app.model.SessionRecord

internal fun SessionRecord.resolvedDrillId(): String? =
    drillId ?: metricsJson.parseInlineMetric("drillId")

internal fun String.parseInlineMetric(key: String): String? =
    split('|')
        .firstOrNull { token -> token.startsWith("$key:") }
        ?.substringAfter(':')
        ?.takeIf { it.isNotBlank() }

internal fun List<SessionRecord>.filterForDrill(drillId: String?): List<SessionRecord> =
    if (drillId.isNullOrBlank()) this else filter { session -> session.resolvedDrillId() == drillId }

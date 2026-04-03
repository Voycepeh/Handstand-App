package com.inversioncoach.app.storage.repository

import com.inversioncoach.app.model.ReferenceTemplateRecord

internal object DrillCurrentReferenceResolver {
    fun resolve(templates: List<ReferenceTemplateRecord>): ReferenceTemplateRecord? =
        templates.maxWithOrNull(
            compareBy<ReferenceTemplateRecord> { if (it.isBaseline) 1 else 0 }
                .thenBy { it.updatedAtMs }
                .thenBy { it.createdAtMs },
        )
}

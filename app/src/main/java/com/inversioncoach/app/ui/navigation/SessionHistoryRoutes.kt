package com.inversioncoach.app.ui.navigation

import android.net.Uri

internal enum class SessionHistoryMode(val routeValue: String) {
    HISTORY("history"),
    COMPARE("compare"),
    ;

    companion object {
        fun fromRoute(raw: String?): SessionHistoryMode =
            entries.firstOrNull { it.routeValue == raw } ?: HISTORY
    }
}

internal object SessionHistoryRoutes {
    const val drillIdArg = "drillId"
    const val modeArg = "mode"
    const val routePattern = "session-history?$drillIdArg={$drillIdArg}&$modeArg={$modeArg}"

    fun create(drillId: String? = null, mode: SessionHistoryMode = SessionHistoryMode.HISTORY): String =
        "session-history?$drillIdArg=${Uri.encode(drillId.orEmpty())}&$modeArg=${Uri.encode(mode.routeValue)}"
}

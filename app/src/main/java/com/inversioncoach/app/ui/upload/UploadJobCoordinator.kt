package com.inversioncoach.app.ui.upload

import android.util.Log
import java.util.UUID

private const val TAG = "UploadJobCoordinator"

object UploadJobCoordinator {
    @Volatile
    private var activeSessionId: Long? = null

    @Volatile
    private var activeOwnerToken: String? = null

    @Synchronized
    fun begin(sessionId: Long, ownerToken: String = UUID.randomUUID().toString()): String {
        val token = ownerToken
        activeSessionId = sessionId
        activeOwnerToken = token
        Log.i(TAG, "job_start sessionId=$sessionId token=$token")
        return token
    }

    @Synchronized
    fun isActive(): Boolean = activeSessionId != null && !activeOwnerToken.isNullOrBlank()

    @Synchronized
    fun isOwnedBy(token: String?): Boolean = !token.isNullOrBlank() && token == activeOwnerToken

    @Synchronized
    fun currentSessionId(): Long? = activeSessionId

    @Synchronized
    fun clear(token: String? = null) {
        if (token != null && token != activeOwnerToken) return
        Log.i(TAG, "job_clear sessionId=${activeSessionId ?: -1} token=${activeOwnerToken.orEmpty()}")
        activeSessionId = null
        activeOwnerToken = null
    }
}

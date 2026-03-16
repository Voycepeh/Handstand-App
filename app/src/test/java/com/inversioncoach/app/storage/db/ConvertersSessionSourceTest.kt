package com.inversioncoach.app.storage.db

import com.inversioncoach.app.model.SessionSource
import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersSessionSourceTest {
    private val converters = Converters()

    @Test
    fun sessionSourceRoundTrip() {
        val raw = converters.sessionSourceToString(SessionSource.UPLOADED_VIDEO)
        assertEquals(SessionSource.UPLOADED_VIDEO, converters.sessionSourceFromString(raw))
    }

    @Test
    fun unknownSessionSourceFallsBackToLive() {
        assertEquals(SessionSource.LIVE_COACHING, converters.sessionSourceFromString("UNKNOWN"))
    }
}

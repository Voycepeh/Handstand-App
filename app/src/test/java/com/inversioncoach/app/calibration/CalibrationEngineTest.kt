package com.inversioncoach.app.calibration

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CalibrationEngineTest {
    @Test
    fun buildsProfileWhenAllStepsPresent() {
        val session = CalibrationSession()
        CalibrationStep.entries.forEach {
            session.record(it, CalibrationCapture(0.4f, 0.3f, 0.5f, 0.25f, 0.2f, 0.35f, 0.33f))
        }
        val profile = CalibrationEngine().buildProfile(session)
        assertNotNull(profile)
    }

    @Test
    fun returnsNullWhenCalibrationMissing() {
        val session = CalibrationSession()
        session.record(CalibrationStep.FRONT_NEUTRAL, CalibrationCapture(0.4f, 0.3f, 0.5f, 0.25f, 0.2f, 0.35f, 0.33f))
        assertNull(CalibrationEngine().buildProfile(session))
    }
}

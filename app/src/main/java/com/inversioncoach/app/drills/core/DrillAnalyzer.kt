package com.inversioncoach.app.drills.core

import com.inversioncoach.app.model.PoseFrame

interface DrillAnalyzer {
    fun analyzeFrame(frame: PoseFrame): FrameAnalysis?
}

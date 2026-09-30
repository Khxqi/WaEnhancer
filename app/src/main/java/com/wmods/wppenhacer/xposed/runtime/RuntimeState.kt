package com.wmods.wppenhacer.xposed.runtime

import java.util.concurrent.CopyOnWriteArrayList

object RuntimeState {
    @Volatile
    var session: HostSession? = null

    @Volatile
    var config: RuntimeConfigSnapshot? = null

    @Volatile
    var capabilities: CapabilityRegistry? = null

    @Volatile
    var features: FeatureRegistry? = null

    private val stages = CopyOnWriteArrayList<RuntimeStageRecord>()

    fun stage(stage: RuntimeStage, status: RuntimeStageStatus, summary: String? = null) {
        stages.removeAll { it.stage == stage }
        stages += RuntimeStageRecord(stage, status, summary?.let(DiagnosticSanitizer::sanitize))
    }

    fun stageSnapshot(): List<RuntimeStageRecord> = stages.toList()
}

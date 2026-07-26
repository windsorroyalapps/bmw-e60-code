package com.bmwe60coderpro.protocol

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Live data polling engine.
 *
 * Polls multiple ECUs simultaneously with adaptive rates.
 * Each module gets its own polling interval based on data criticality.
 *
 * Inspired by E46Track (tomicooler) and DeepOBD (ediabaslib) polling strategies.
 */
class LiveDataPoller(
    private val session: KdcanSession,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) {

    data class PollConfig(
        val target: EcuTarget,
        val jobs: List<BmwJob>,
        val intervalMs: Long = 500,
        val enabled: Boolean = true,
    )

    data class PollResult(
        val target: EcuTarget,
        val job: BmwJob,
        val result: JobResult,
        val timestamp: Long = System.currentTimeMillis(),
    )

    private val _results = MutableStateFlow<List<PollResult>>(emptyList())
    val results: StateFlow<List<PollResult>> = _results

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private var pollJobs = mutableListOf<Job>()

    /** Start polling with the given configuration. */
    fun start(configs: List<PollConfig>) {
        stop()
        _isRunning.value = true

        for (config in configs.filter { it.enabled }) {
            val job = scope.launch {
                while (isActive) {
                    for (bmwJob in config.jobs) {
                        if (!isActive) break
                        try {
                            session.setTarget(config.target)
                            val result = session.execute(bmwJob)
                            val pollResult = PollResult(
                                target = config.target,
                                job = bmwJob,
                                result = result,
                            )
                            _results.value = (_results.value + pollResult).takeLast(100)
                        } catch (e: Exception) {
                            // Log but continue polling
                        }
                        delay(config.intervalMs / config.jobs.size.coerceAtLeast(1))
                    }
                }
            }
            pollJobs += job
        }
    }

    /** Stop all polling. */
    fun stop() {
        pollJobs.forEach { it.cancel() }
        pollJobs.clear()
        _isRunning.value = false
    }

    /** Quick-start with default E60 live data configuration. */
    fun startE60Defaults() {
        val configs = listOf(
            PollConfig(
                target = BmwTargets.DME,
                jobs = listOfNotNull(
                    BmwJobs.byId("dme_live_basic"),
                    BmwJobs.byId("dme_live_air"),
                ),
                intervalMs = 400,
            ),
            PollConfig(
                target = BmwTargets.EGS,
                jobs = listOfNotNull(
                    BmwJobs.byId("egs_live_basic"),
                ),
                intervalMs = 600,
            ),
            PollConfig(
                target = BmwTargets.DSC,
                jobs = listOfNotNull(
                    BmwJobs.byId("dsc_live_status"),
                    BmwJobs.byId("dsc_live_wheels"),
                ),
                intervalMs = 300,
            ),
            PollConfig(
                target = BmwTargets.KOMBI,
                jobs = listOfNotNull(
                    BmwJobs.byId("kombi_live_drive"),
                ),
                intervalMs = 500,
            ),
            PollConfig(
                target = BmwTargets.SZL,
                jobs = listOfNotNull(
                    BmwJobs.byId("szl_live_switches"),
                ),
                intervalMs = 200,
            ),
            PollConfig(
                target = BmwTargets.CAS,
                jobs = listOfNotNull(
                    BmwJobs.byId("cas_live_terminals"),
                ),
                intervalMs = 1000,
            ),
        )
        start(configs)
    }

    /** Get the latest result for a specific target. */
    fun latestFor(target: EcuTarget): PollResult? {
        return _results.value.lastOrNull { it.target.name == target.name }
    }

    /** Get all results for a specific target in the buffer. */
    fun historyFor(target: EcuTarget): List<PollResult> {
        return _results.value.filter { it.target.name == target.name }
    }

    fun dispose() {
        stop()
        scope.cancel()
    }
}

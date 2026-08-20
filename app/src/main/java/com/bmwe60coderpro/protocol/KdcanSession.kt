package com.bmwe60coderpro.protocol

import com.bmwe60coderpro.data.VehicleProfileKind
import com.bmwe60coderpro.util.HexUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** How long a single follow-up chunk read may block while assembling a frame. */
private const val FRAME_CHUNK_TIMEOUT_MS = 300L

class KdcanSession(
    private val transport: Transport,
    private var target: EcuTarget = BmwTargets.DME,
    private var vehicleProfile: VehicleProfileKind = VehicleProfileKind.GENERIC_E60,
    private val testerAddress: Int = 0xF1,
) {
    private val mutex = Mutex()
    fun getTransport(): Transport = transport
    private var commProfile: CommProfile = BmwCommProfiles.forTarget(target, vehicleProfile)
    fun setTarget(target: EcuTarget) {
        this.target = target
        this.commProfile = BmwCommProfiles.forTarget(target, vehicleProfile)
    }

    fun setVehicleProfile(vehicleProfile: VehicleProfileKind) {
        this.vehicleProfile = vehicleProfile
        this.commProfile = BmwCommProfiles.forTarget(target, vehicleProfile)
    }

    fun getCommProfile(): CommProfile = commProfile

    suspend fun onConnected(extraSettleDelayMs: Long = 0) {
        delay(commProfile.postConnectDelayMs + extraSettleDelayMs)
    }

    fun getTarget(): EcuTarget = target

    suspend fun sendRawHex(hex: String): String = mutex.withLock {
        val bytes = HexUtils.hexToBytes(hex)
        transport.write(bytes)
        delay(commProfile.interFrameDelayMs)
        val response = readDiscardingEcho(bytes, commProfile.requestTimeoutMs)
        return HexUtils.bytesToHex(response)
    }

    /**
     * Build and send a raw KWP frame to an arbitrary target without changing session state.
     * Used for real-time controller injection where we must not switch the session target.
     */
    suspend fun sendRawFrame(targetAddress: Int, serviceId: Int, payload: List<Int>): String = mutex.withLock {
        val data = mutableListOf(serviceId and 0xFF)
        data.addAll(payload.map { it and 0xFF })
        val format = 0x80 or (data.size and 0x3F)
        val withoutChecksum = mutableListOf(format, targetAddress and 0xFF, testerAddress and 0xFF)
        withoutChecksum.addAll(data)
        val checksum = withoutChecksum.sumOf { it and 0xFF } and 0xFF
        val frame = withoutChecksum.plus(checksum).map { (it and 0xFF).toByte() }.toByteArray()
        transport.write(frame)
        delay(commProfile.interFrameDelayMs)
        val response = readDiscardingEcho(frame, commProfile.requestTimeoutMs)
        return HexUtils.bytesToHex(response)
    }

    /**
     * Verifies physical and protocol communication using read-only KWP requests.
     * A USB port opening successfully is not enough to declare the vehicle connected.
     */
    suspend fun verifyCommunication(): ConnectionVerification {
        val startJob = BmwJobs.byId("start_session_default") ?: error("Job not found: start_session_default")
        val start = execute(startJob)
        if (!start.success) {
            return ConnectionVerification(
                connected = false,
                summary = "${target.name}: ${start.summary}",
                responseHex = start.responseHex,
            )
        }

        val idJob = BmwJobs.byId("ecu_id_9A") ?: error("Job not found: ecu_id_9A")
        val id = execute(idJob)
        return ConnectionVerification(
            connected = true,
            summary = buildString {
                append("${target.name}: ${start.summary}")
                append(" | ID: ${id.summary}")
            },
            responseHex = id.responseHex.ifBlank { start.responseHex },
        )
    }

    suspend fun execute(job: BmwJob): JobResult = mutex.withLock {
        val stepResults = mutableListOf<StepResult>()
        
        // Purge any stale data from previous jobs or failed reads
        runCatching { transport.purge() }

        if (commProfile.autoTesterPresentBeforeJob && job.category != JobCategory.SESSION) {
            runCatching {
                val keepAliveRequest = buildFrame(0x3E, listOf(0x00))
                transport.write(keepAliveRequest)
                delay(commProfile.interFrameDelayMs)
                readDiscardingEcho(keepAliveRequest, commProfile.requestTimeoutMs.coerceAtMost(900))
            }
        }
        if (commProfile.preJobDelayMs > 0) delay(commProfile.preJobDelayMs)
        for (step in job.steps) {
            val request = buildFrame(step.serviceId, step.payload)
            var lastResult: StepResult? = null
            for (attempt in 0..commProfile.retries) {
                if (attempt > 0) {
                    // Purge before retry to clear whatever caused the previous failure
                    runCatching { transport.purge() }
                }
                transport.write(request)
                delay(commProfile.interFrameDelayMs)
                val response = readDiscardingEcho(request, commProfile.requestTimeoutMs)
                val result = parseStepResponse(request, response, step, job, attempt)
                lastResult = result
                val shouldRetry = !result.success && attempt < commProfile.retries && shouldRetry(result)
                if (!shouldRetry) break
                delay((commProfile.interFrameDelayMs * (attempt + 2)).coerceAtMost(180L))
            }
            val finalResult = lastResult ?: StepResult(step.label, HexUtils.bytesToHex(request), "", false, "No response")
            stepResults += finalResult
            if (!finalResult.success) {
                break
            }
        }

        val last = stepResults.lastOrNull()
        return JobResult(
            job = job,
            target = target,
            requestHex = stepResults.joinToString(" | ") { it.requestHex },
            responseHex = stepResults.joinToString(" | ") { it.responseHex.ifBlank { "<empty>" } },
            success = stepResults.isNotEmpty() && stepResults.all { it.success },
            summary = summarizeJob(job, stepResults),
            decoded = buildMap {
                put("comm_profile", commProfile.name)
                put("comm_timeout_ms", commProfile.requestTimeoutMs.toString())
                put("comm_retries", commProfile.retries.toString())
                put("comm_inter_frame_ms", commProfile.interFrameDelayMs.toString())
                // First add all decoded fields without prefix for easy UI access
                stepResults.forEach { step -> putAll(step.decoded) }
                // Then add prefixed versions for disambiguation
                putAll(stepResults.flatMap { step ->
                    step.decoded.map { (k, v) -> "${step.label}:$k" to v }
                }.toMap())
            },
            stepResults = stepResults,
        )
    }

    private fun buildFrame(serviceId: Int, payload: List<Int>): ByteArray =
        KwpFrameCodec.buildFrame(
            targetAddress = target.targetAddress,
            sourceAddress = testerAddress,
            serviceId = serviceId,
            payload = payload,
        )

    private fun parseStepResponse(request: ByteArray, response: ByteArray, step: JobStep, job: BmwJob, attempt: Int = 0): StepResult {
        val requestHex = HexUtils.bytesToHex(request)
        val responseHex = HexUtils.bytesToHex(response)
        if (response.isEmpty()) {
            return StepResult(step.label, requestHex, responseHex, false, if (attempt > 0) "No response after retry ${attempt + 1}" else "No response")
        }

        val frame = KwpFrameCodec.parse(response)
            ?: return StepResult(
                label = step.label,
                requestHex = requestHex,
                responseHex = responseHex,
                success = false,
                summary = "Invalid KWP response frame (header, length, or checksum)",
            )
        if (frame.targetAddress != testerAddress || frame.sourceAddress != target.targetAddress) {
            return StepResult(
                label = step.label,
                requestHex = requestHex,
                responseHex = responseHex,
                success = false,
                summary = "Unexpected KWP response addresses: target 0x${frame.targetAddress.toString(16).uppercase()} source 0x${frame.sourceAddress.toString(16).uppercase()}",
            )
        }

        val payload = frame.payload
        val service = payload.firstOrNull()

        if (service == 0x7F && payload.size >= 3) {
            val negativeFor = payload[1]
            val code = payload[2]
            return StepResult(
                label = step.label,
                requestHex = requestHex,
                responseHex = responseHex,
                success = false,
                summary = "Negative response to 0x${negativeFor.toString(16).uppercase()} NRC 0x${code.toString(16).uppercase()}${if (attempt > 0) " after retry ${attempt + 1}" else ""}",
                decoded = decodePayload(job, step, payload)
            )
        }

        val positiveService = ((step.serviceId + 0x40) and 0xFF)
        val success = service == positiveService || service == step.serviceId
        return StepResult(
            label = step.label,
            requestHex = requestHex,
            responseHex = responseHex,
            success = success,
            summary = summarize(step, job, payload, attempt),
            decoded = decodePayload(job, step, payload)
        )
    }

    private fun summarize(step: JobStep, job: BmwJob, payload: List<Int>, attempt: Int = 0): String {
        if (payload.isEmpty()) return "Empty response"
        val service = payload.firstOrNull() ?: return "Unknown response"
        val retrySuffix = if (attempt > 0) " after retry ${attempt + 1}" else ""
        return when (step.serviceId) {
            0x10 -> if (payload.size >= 2) "${step.label}: session accepted 0x${payload[1].toString(16).uppercase()}${retrySuffix}" else "${step.label}: response 0x${service.toString(16).uppercase()}${retrySuffix}"
            0x1A -> "${step.label}: ${asciiFrom(payload.drop(2)).ifBlank { HexUtils.bytesToHex(payload.drop(1).map { it.toByte() }.toByteArray()) }}${retrySuffix}"
            0x18 -> "${step.label}: DTC payload ${payload.drop(1).size} byte(s)${retrySuffix}"
            0x14 -> "${step.label}: fault memory clear response${retrySuffix}"
            0x21 -> "${step.label}: ${asciiFrom(payload.drop(2)).ifBlank { HexUtils.bytesToHex(payload.drop(1).map { it.toByte() }.toByteArray()) }}${retrySuffix}"
            0x3E -> "${step.label}: tester present acknowledged${retrySuffix}"
            else -> "${step.label}: service 0x${service.toString(16).uppercase()} response${retrySuffix}"
        }
    }

    private fun shouldRetry(result: StepResult): Boolean {
        val summary = result.summary.uppercase()
        return summary.contains("NO RESPONSE") || summary.contains("NRC 0X21") || summary.contains("NRC 0X78")
    }

    private fun summarizeJob(job: BmwJob, steps: List<StepResult>): String {
        if (steps.isEmpty()) return "No steps executed"
        val ok = steps.count { it.success }
        return if (job.category == JobCategory.MODULE_PACK) {
            val last = steps.last()
            "${job.label}: ${ok}/${steps.size} step(s) OK; last=${last.summary}"
        } else {
            steps.last().summary
        }
    }

    private fun decodePayload(job: BmwJob, step: JobStep, payload: List<Int>): Map<String, String> {
        return BmwPayloadDecoders.decode(
            context = DecodeContext(target = target, step = step),
            payload = payload,
        )
    }

    private fun asciiFrom(bytes: List<Int>): String {
        return bytes.mapNotNull {
            val c = it.toChar()
            if (c.code in 32..126) c else null
        }.joinToString("")
    }

    private fun timeLeft(deadlineMs: Long): Long =
        (deadlineMs - System.currentTimeMillis()).coerceAtLeast(0)

    /**
     * Reads a checksum-valid response frame while removing an optional local echo.
     *
     * USB serial reads may split the echo and ECU response arbitrarily. The prior
     * implementation reset a partial echo and then accidentally accepted its later
     * bytes as a response. This implementation keeps the byte stream intact, removes
     * only a complete exact echo, and returns a complete frame addressed back to this
     * tester. Delayed response-pending (0x7F .. 0x78) frames are not treated as final.
     */
    private suspend fun readDiscardingEcho(request: ByteArray, timeoutMs: Int): ByteArray {
        val deadline = System.currentTimeMillis() + timeoutMs
        var buffered = ByteArray(0)
        var echoDecisionPending = true
        var firstOtherFrame: ByteArray? = null

        while (timeLeft(deadline) > 0) {
            val chunk = transport.read(
                timeLeft(deadline).coerceAtMost(FRAME_CHUNK_TIMEOUT_MS).toInt().coerceAtLeast(1),
            )
            if (chunk.isEmpty()) {
                if (!echoDecisionPending && buffered.isNotEmpty()) {
                    firstOtherFrame = firstOtherFrame ?: firstCompleteFrame(buffered)
                }
                break
            }
            buffered += chunk

            if (echoDecisionPending) {
                when {
                    buffered.size < request.size && startsWith(request, buffered) -> continue
                    buffered.size >= request.size && startsWith(buffered, request) -> {
                        buffered = buffered.copyOfRange(request.size, buffered.size)
                        echoDecisionPending = false
                    }
                    else -> echoDecisionPending = false
                }
            }

            if (echoDecisionPending) continue

            val matching = matchingResponseFrame(buffered)
            if (matching != null) {
                val parsed = KwpFrameCodec.parse(matching)
                if (parsed != null && !KwpFrameCodec.isResponsePending(parsed)) return matching
                firstOtherFrame = firstOtherFrame ?: matching
            } else {
                firstOtherFrame = firstOtherFrame ?: firstCompleteFrame(buffered)
            }
        }

        return matchingResponseFrame(buffered) ?: firstOtherFrame ?: firstCompleteFrame(buffered) ?: buffered
    }

    private fun matchingResponseFrame(buffer: ByteArray): ByteArray? {
        var offset = 0
        var pendingFrame: ByteArray? = null
        while (offset < buffer.size) {
            val located = KwpFrameCodec.findFirstCompleteFrame(buffer, offset) ?: break
            offset = located.offset + located.frame.bytes.size
            if (located.frame.targetAddress != testerAddress || located.frame.sourceAddress != target.targetAddress) continue
            if (!KwpFrameCodec.isResponsePending(located.frame)) return located.frame.bytes
            pendingFrame = located.frame.bytes
        }
        return pendingFrame
    }

    private fun firstCompleteFrame(buffer: ByteArray): ByteArray? =
        KwpFrameCodec.findFirstCompleteFrame(buffer)?.frame?.bytes

    private fun startsWith(value: ByteArray, prefix: ByteArray): Boolean =
        value.size >= prefix.size && prefix.indices.all { value[it] == prefix[it] }
}

data class ConnectionVerification(
    val connected: Boolean,
    val summary: String,
    val responseHex: String,
)

data class StepResult(
    val label: String,
    val requestHex: String,
    val responseHex: String,
    val success: Boolean,
    val summary: String,
    val decoded: Map<String, String> = emptyMap(),
)

data class JobResult(
    val job: BmwJob,
    val target: EcuTarget,
    val requestHex: String,
    val responseHex: String,
    val success: Boolean,
    val summary: String,
    val decoded: Map<String, String> = emptyMap(),
    val stepResults: List<StepResult> = emptyList(),
)

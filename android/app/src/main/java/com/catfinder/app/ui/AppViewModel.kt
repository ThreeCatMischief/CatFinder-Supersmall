package com.catfinder.app.ui

import android.app.Application
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.catfinder.app.data.AppConfig
import com.catfinder.app.data.ClockSync
import com.catfinder.app.data.DiagLog
import com.catfinder.app.data.Discovery
import com.catfinder.app.data.DriveConfigForm
import com.catfinder.app.data.EndpointRules
import com.catfinder.app.data.EspApi
import com.catfinder.app.data.MjpegReader
import com.catfinder.app.data.OfflineException
import com.catfinder.app.data.Settings
import com.catfinder.app.data.VideoGovernor
import com.catfinder.app.drive.HoldDetector
import com.catfinder.app.drive.Mixer
import com.catfinder.app.drive.StraightCal
import com.catfinder.app.drive.Wheels
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

enum class Screen { DRIVE, SETTINGS }

/** ok is null until the first request finished. */
data class LinkState(val ok: Boolean? = null, val rttMs: Long? = null, val error: String? = null)

/** lagMs: capture on the ESP to arrival here; null while unknown (no clock sync, old firmware). */
data class VideoState(val frame: ImageBitmap? = null, val fps: Float = 0f, val error: String? = null, val lagMs: Long? = null)

enum class CalPhase {
    /** Waiting for a steady forward or reverse command. */
    DRIVE,
    /** The same command is being held; progress counts up. */
    HOLD,
    SAVING,
    /** Saved; waits for the stick to be released before the next hold. */
    SAVED,
    /** Saving failed; waits for the stick to be released. */
    FAILED,
}

/**
 * Drive-straight calibration on the drive screen. [point] indexes StraightCal.POINTS
 * (the speed being driven), [done] the points saved since the mode was started.
 */
data class StraightCalState(
    val active: Boolean = false,
    val phase: CalPhase = CalPhase.DRIVE,
    val point: Int? = null,
    val progress: Float = 0f,
    val done: Set<Int> = emptySet(),
    val savedValue: Int? = null,
    val error: String? = null,
)

/** Exception class and message, for the diagnostic log. */
private fun Throwable.describe(): String {
    val root = cause ?: this
    return "${root.javaClass.simpleName}: ${root.message}"
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = Settings(app)

    init {
        DiagLog.init(app)
    }

    private val _config = MutableStateFlow(settings.load())
    val config: StateFlow<AppConfig> = _config.asStateFlow()

    private val _screen = MutableStateFlow(Screen.DRIVE)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    // Drive screen
    private val target = MutableStateFlow(Wheels.STOP)
    private val _sent = MutableStateFlow(Wheels.STOP)
    val sent: StateFlow<Wheels> = _sent.asStateFlow()
    private val _link = MutableStateFlow(LinkState())
    val link: StateFlow<LinkState> = _link.asStateFlow()
    private val _video = MutableStateFlow(VideoState())
    val video: StateFlow<VideoState> = _video.asStateFlow()
    private val _rssi = MutableStateFlow<Int?>(null)
    val rssi: StateFlow<Int?> = _rssi.asStateFlow()
    private val governor = VideoGovernor()
    /** Level the ESP last confirmed; null until known. */
    private val _videoLevel = MutableStateFlow<Int?>(null)
    val videoLevel: StateFlow<Int?> = _videoLevel.asStateFlow()
    private var videoJob: Job? = null
    // Diagnostics: phone↔ESP clock, and when each /drive seq was sent.
    private val clock = ClockSync()
    private val seq = AtomicLong(0)
    private val sentAt = object : LinkedHashMap<Long, Long>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Long>?) = size > 2_000
    }
    private var tankLeft = 0f
    private var tankRight = 0f
    private val _straightCal = MutableStateFlow(StraightCalState())
    val straightCal: StateFlow<StraightCalState> = _straightCal.asStateFlow()
    private var calJob: Job? = null

    // Settings screen
    private val _driveCfg = MutableStateFlow<Map<String, String>?>(null)
    val driveCfg: StateFlow<Map<String, String>?> = _driveCfg.asStateFlow()
    private val _status = MutableStateFlow<JSONObject?>(null)
    val status: StateFlow<JSONObject?> = _status.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    // Number of settings requests in flight.
    private val running = MutableStateFlow(0)
    val busy: StateFlow<Boolean> = running.map { it > 0 }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // True while the drive screen looks for the robot on its own.
    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private var foreground = false
    private var driveJobs: List<Job> = emptyList()
    private var discoveryJob: Job? = null

    private fun api(): EspApi {
        val address = _config.value.address
        val endpoints = EndpointRules.from(address)
            ?: throw OfflineException(if (address.isBlank()) NOT_SET_UP else "Invalid address: $address")
        return EspApi(endpoints)
    }

    // ------------------------------------------------------------ lifecycle

    fun onStart() {
        foreground = true
        updateDriving()
    }

    fun onStop() {
        foreground = false
        updateDriving()
    }

    fun show(screen: Screen) {
        if (screen != Screen.DRIVE) stopStraightCal()
        _screen.value = screen
        updateDriving()
    }

    /** Drive, stream and status poll run only while the drive screen is visible. */
    private fun updateDriving() {
        val visible = foreground && _screen.value == Screen.DRIVE
        val want = visible && _config.value.address.isNotBlank()
        if (visible) autoDiscover() else discoveryJob?.cancel()
        if (want && driveJobs.isEmpty()) {
            DiagLog.line("drive screen on, address ${_config.value.address}, video ${_config.value.videoLevel}")
            driveJobs = listOf(
                viewModelScope.launch(Dispatchers.IO) { driveLoop() },
                viewModelScope.launch(Dispatchers.IO) { streamLoop() },
                viewModelScope.launch(Dispatchers.IO) { statusLoop() },
            )
            applyVideoLevel()
        } else if (!want && driveJobs.isNotEmpty()) {
            DiagLog.line("drive screen off")
            driveJobs.forEach { it.cancel() }
            driveJobs = emptyList()
            target.value = Wheels.STOP
            tankLeft = 0f
            tankRight = 0f
            // The ESP times out on its own; this stops it at once.
            viewModelScope.launch(Dispatchers.IO + NonCancellable) {
                runCatching { api().drive(Wheels.STOP, seq.incrementAndGet()) }
                _sent.value = Wheels.STOP
            }
        }
    }

    /** Without a saved address, looks for the robot each time the drive screen opens. */
    private fun autoDiscover() {
        if (_config.value.address.isNotBlank() || discoveryJob?.isActive == true) return
        discoveryJob = viewModelScope.launch {
            _searching.value = true
            try {
                val ip = Discovery.find(getApplication())
                DiagLog.line("discovery: ${ip ?: "nothing found"}")
                if (ip != null && _config.value.address.isBlank()) {
                    updateConfig(_config.value.copy(address = ip))
                    updateDriving()
                }
            } finally {
                _searching.value = false
            }
        }
    }

    // ------------------------------------------------------------ driving

    fun setStick(x: Float, y: Float) {
        target.value = if (_straightCal.value.active) Mixer.calibration(x, y) else Mixer.arcade(x, y, _config.value.shaping)
    }

    fun setTank(left: Boolean, v: Float) {
        if (left) tankLeft = v else tankRight = v
        target.value = Mixer.tank(tankLeft, tankRight, _config.value.shaping)
    }

    /**
     * Sends the newest target at the configured rate, one request at a time.
     * While the stick rests it sends STOP until one is acknowledged, then idles.
     */
    private suspend fun driveLoop() {
        var lastSent: Wheels? = null
        while (coroutineContext.isActive) {
            val t0 = SystemClock.elapsedRealtime()
            val w = target.value
            if (w != Wheels.STOP || lastSent != Wheels.STOP) {
                val n = seq.incrementAndGet()
                synchronized(sentAt) { sentAt[n] = t0 }
                try {
                    val espMs = api().drive(w, n)
                    lastSent = w
                    _sent.value = w
                    val rtt = SystemClock.elapsedRealtime() - t0
                    _link.value = LinkState(true, rtt)
                    if (espMs != null) clock.onSample(t0, rtt, espMs)
                    DiagLog.line("drive #$n L${w.left} R${w.right} ok ${rtt} ms esp=$espMs")
                    rttSample(rtt)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastSent = null
                    _link.value = LinkState(false, error = e.message)
                    DiagLog.line("drive #$n L${w.left} R${w.right} FAIL ${SystemClock.elapsedRealtime() - t0} ms ${e.describe()}")
                    rttSample(null)
                }
            }
            val period = 1000L / AppConfig.SEND_RATE_HZ
            delay((period - (SystemClock.elapsedRealtime() - t0)).coerceAtLeast(5))
        }
    }

    // ------------------------------------------------------------ drive straight

    /**
     * Opens the drive screen in drive-straight mode: the user drives with gentle
     * steering until the tank runs straight and holds the stick there for StraightCal.HOLD_MS; the
     * held command is then turned into the balance for the nearest speed point.
     */
    fun startStraightCal() {
        target.value = Wheels.STOP
        tankLeft = 0f
        tankRight = 0f
        _straightCal.value = StraightCalState(active = true)
        show(Screen.DRIVE)
        loadDriveConfig()  // the robot's current balance and span, for the first save
        calJob?.cancel()
        calJob = viewModelScope.launch { straightCalLoop() }
        DiagLog.line("drive straight: start")
    }

    fun stopStraightCal() {
        if (!_straightCal.value.active) return
        calJob?.cancel()
        calJob = null
        target.value = Wheels.STOP
        _straightCal.value = StraightCalState()
        DiagLog.line("drive straight: stop")
    }

    private suspend fun straightCalLoop() {
        val detector = HoldDetector()
        while (coroutineContext.isActive) {
            delay(100)
            val state = _straightCal.value
            val w = target.value
            when (state.phase) {
                CalPhase.SAVING -> continue
                CalPhase.SAVED, CalPhase.FAILED -> {
                    // The held command over-steers once the new balance is applied, so the
                    // next hold starts only after the stick has been let go.
                    if (w == Wheels.STOP) _straightCal.update { it.copy(phase = CalPhase.DRIVE, savedValue = null, error = null) }
                    continue
                }
                else -> {}
            }
            // Commands that do not reach the robot do not count.
            val progress = if (_link.value.ok == true && driveJobs.isNotEmpty()) {
                detector.update(SystemClock.elapsedRealtime(), w)
            } else {
                detector.reset()
                0f
            }
            val point = StraightCal.pointFor(maxOf(kotlin.math.abs(w.left), kotlin.math.abs(w.right)))
                .takeIf { StraightCal.holdable(w) }
            _straightCal.update {
                it.copy(phase = if (progress > 0f) CalPhase.HOLD else CalPhase.DRIVE, point = point, progress = progress)
            }
            if (progress >= 1f && point != null) {
                val (left, right) = detector.average() ?: continue
                detector.reset()
                saveStraight(point, left, right)
            }
        }
    }

    private suspend fun saveStraight(point: Int, left: Double, right: Double) {
        _straightCal.update { it.copy(phase = CalPhase.SAVING) }
        try {
            val api = api()
            val cfg = _driveCfg.value ?: DriveConfigForm.fromJson(api.driveConfig())
            val bal = DriveConfigForm.BALANCE.map { (key, _) -> cfg[key]?.toIntOrNull() ?: 0 }
            val span = cfg["span"]?.toIntOrNull() ?: 500
            val value = StraightCal.balanceFor(left, right, bal, span)
            _driveCfg.value = DriveConfigForm.fromJson(api.saveDriveConfig(mapOf("bal$point" to value.toString())))
            DiagLog.line("drive straight: held L%.1f R%.1f, %d %% balance %d -> %d µs".format(
                left, right, StraightCal.POINTS[point], bal[point], value))
            _straightCal.update { it.copy(phase = CalPhase.SAVED, done = it.done + point, savedValue = value, progress = 1f) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DiagLog.line("drive straight: save FAIL ${e.describe()}")
            _straightCal.update { it.copy(phase = CalPhase.FAILED, error = e.message ?: "Saving failed", progress = 0f) }
        }
    }

    // ------------------------------------------------------------ video quality

    private fun wantedVideoLevel(): Int {
        val fixed = _config.value.videoLevel
        return if (fixed == AppConfig.VIDEO_AUTO) governor.level else fixed.coerceIn(0, VideoGovernor.LEVELS - 1)
    }

    // Drive and status loops feed samples from different threads.
    private fun rttSample(rttMs: Long?) = synchronized(governor) {
        if (_config.value.videoLevel != AppConfig.VIDEO_AUTO) return
        if (governor.onSample(SystemClock.elapsedRealtime(), rttMs)) {
            DiagLog.line("governor: level ${governor.level} (${VideoGovernor.LABELS[governor.level]}) after rtt=$rttMs")
            applyVideoLevel()
        }
    }

    /** Sends the wanted level; a newer call replaces one still in flight. */
    private fun applyVideoLevel() = synchronized(governor) {
        val level = wantedVideoLevel()
        videoJob?.cancel()
        videoJob = viewModelScope.launch(Dispatchers.IO) {
            runCatching { _videoLevel.value = api().setVideoLevel(level) }
                .onSuccess { DiagLog.line("video level $level set") }
                .onFailure { if (it !is CancellationException) DiagLog.line("video level $level FAIL ${it.describe()}") }
        }
    }

    private suspend fun statusLoop() {
        while (coroutineContext.isActive) {
            val t0 = SystemClock.elapsedRealtime()
            try {
                val s = api().status()
                val rtt = SystemClock.elapsedRealtime() - t0
                _rssi.value = s.optInt("rssi")
                logStatus(s, t0, rtt)
                // Only update the link while idle; when driving, /drive is the better measure.
                if (target.value == Wheels.STOP) {
                    _link.value = LinkState(true, rtt)
                    rttSample(rtt)
                }
                // Catches an ESP reboot (back to level 0) and a lost /video request.
                if (s.has("video_level")) {
                    _videoLevel.value = s.getInt("video_level")
                    if (s.getInt("video_level") != wantedVideoLevel()) applyVideoLevel()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DiagLog.line("status FAIL ${SystemClock.elapsedRealtime() - t0} ms ${e.describe()}")
                if (target.value == Wheels.STOP) {
                    _link.value = LinkState(false, error = e.message)
                    rttSample(null)
                }
            }
            delay(2_000)
        }
    }

    /**
     * One line per status poll. Command delay: the ESP reports the last /drive seq
     * it executed and how long ago; with the clock offset that gives when it arrived,
     * compared with when this app sent it.
     */
    private fun logStatus(s: JSONObject, t0: Long, rtt: Long) {
        if (s.has("now_ms")) clock.onSample(t0, rtt, s.getLong("now_ms"))
        val sb = StringBuilder("status ${rtt} ms")
        for (k in listOf("rssi", "wifi_reconnects", "heap_free", "page_sockets", "stream_clients", "stream_fps",
                         "frame_bytes", "video_level", "drive_l", "drive_r", "uptime_s")) {
            if (s.has(k)) sb.append(" $k=${s.opt(k)}")
        }
        if (s.has("drive_seq")) {
            val espSeq = s.getLong("drive_seq")
            val sent = synchronized(sentAt) { sentAt[espSeq] }
            val offset = clock.offset()
            sb.append(" esp_seq=$espSeq app_seq=${seq.get()}")
            if (sent != null && offset != null) {
                val arrivedPhone = s.getLong("now_ms") - s.getLong("drive_seq_age_ms") - offset
                sb.append(" cmd_delay=${arrivedPhone - sent}±${clock.uncertainty()} ms")
            }
        }
        DiagLog.line(sb.toString())
    }

    private suspend fun streamLoop() {
        var backoff = 1_000L
        while (coroutineContext.isActive) {
            try {
                val tOpen = SystemClock.elapsedRealtime()
                val c = api().openStream()
                DiagLog.line("stream open ${SystemClock.elapsedRealtime() - tOpen} ms")
                // A blocking read does not see coroutine cancellation; closing the socket ends it.
                val hook = coroutineContext.job.invokeOnCompletion { c.disconnect() }
                try {
                    val reader = MjpegReader(c.inputStream)
                    backoff = 1_000L
                    var windowStart = SystemClock.elapsedRealtime()
                    var frames = 0
                    var bytesIn = 0L
                    var lagMin = Long.MAX_VALUE
                    var lagMax = Long.MIN_VALUE
                    var fps = _video.value.fps
                    var lag: Long? = null
                    while (coroutineContext.isActive) {
                        val bytes = reader.nextFrame() ?: throw IOException("Stream ended")
                        val arrived = SystemClock.elapsedRealtime()
                        val offset = clock.offset()
                        lag = reader.timestamp?.let { ts -> offset?.let { arrived + it - ts } }
                        lag?.let { lagMin = minOf(lagMin, it); lagMax = maxOf(lagMax, it) }
                        bytesIn += bytes.size
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
                        frames++
                        val now = SystemClock.elapsedRealtime()
                        if (now - windowStart >= 1_000) {
                            fps = frames * 1_000f / (now - windowStart)
                            DiagLog.line(
                                "video %.1f fps %d kB/s decode %d ms lag %s".format(
                                    fps, bytesIn * 1_000 / (now - windowStart) / 1_024, now - arrived,
                                    if (lagMin == Long.MAX_VALUE) "?" else "$lagMin..$lagMax±${clock.uncertainty()} ms",
                                ),
                            )
                            windowStart = now
                            frames = 0
                            bytesIn = 0
                            lagMin = Long.MAX_VALUE
                            lagMax = Long.MIN_VALUE
                        }
                        _video.value = VideoState(bmp.asImageBitmap(), fps, null, lag)
                    }
                } finally {
                    hook.dispose()
                    c.disconnect()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!coroutineContext.isActive) break
                DiagLog.line("stream FAIL ${e.describe()}")
                _video.update { it.copy(fps = 0f, error = e.message ?: "Stream failed") }
            }
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(5_000L)
        }
    }

    // ------------------------------------------------------------ settings

    fun updateConfig(c: AppConfig) {
        val videoChanged = c.videoLevel != _config.value.videoLevel
        _config.value = c
        settings.save(c)
        if (videoChanged && driveJobs.isNotEmpty()) applyVideoLevel()
    }

    /** Writes the full diagnostic log to the cache dir for sharing. */
    fun exportDiagLog(): File = DiagLog.export(File(getApplication<Application>().cacheDir, "share"))

    fun clearMessage() {
        _message.value = null
    }

    private fun action(block: suspend CoroutineScope.() -> Unit) {
        running.update { it + 1 }
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = e.message ?: e.toString()
            } finally {
                running.update { it - 1 }
            }
        }
    }

    fun findOnWifi() = action {
        val ip = Discovery.find(getApplication())
        DiagLog.line("discovery: ${ip ?: "nothing found"}")
        if (ip != null) {
            updateConfig(_config.value.copy(address = ip))
            _message.value = "Found at $ip"
        } else {
            _message.value = "No CatFinder found on this WiFi. Is the robot on and the phone on the same WiFi?"
        }
    }

    fun testConnection() = action {
        val t0 = SystemClock.elapsedRealtime()
        val s = api().status()
        val rtt = SystemClock.elapsedRealtime() - t0
        _status.value = s
        _message.value = "Purring – $rtt ms, build ${s.optString("build")}, RSSI ${s.optInt("rssi")} dBm"
    }

    fun loadDriveConfig() = action {
        _driveCfg.value = DriveConfigForm.fromJson(api().driveConfig())
    }

    /** One wheel setting (swap, inv_a, inv_b), stored on the robot at once. */
    fun setDriveFlag(key: String, on: Boolean) = action {
        _driveCfg.value = DriveConfigForm.fromJson(api().saveDriveConfig(mapOf(key to if (on) "1" else "0")))
    }

    fun resetStraight() = action {
        val zero = DriveConfigForm.BALANCE.associate { (key, _) -> key to "0" }
        _driveCfg.value = DriveConfigForm.fromJson(api().saveDriveConfig(zero))
        _message.value = "Drive-straight calibration cleared"
    }

    fun resetDriveConfig() = action {
        _driveCfg.value = DriveConfigForm.fromJson(api().saveDriveConfig(mapOf("reset" to "1")))
        _message.value = "Training forgotten – drivetrain back to defaults"
    }

    fun refreshStatus() = action {
        _status.value = api().status()
    }

    private companion object {
        const val NOT_SET_UP = "No robot set up yet. Open Settings and tap Find on my WiFi."
    }
}

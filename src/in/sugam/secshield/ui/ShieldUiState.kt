package `in`.sugam.secshield.ui

import `in`.sugam.secshield.data.ScanOutcome
import `in`.sugam.secshield.data.SecurityRepository
import `in`.sugam.secshield.data.Store
import `in`.sugam.secshield.domain.AppRisk
import `in`.sugam.secshield.domain.ScanSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the user sees after a finished scan: the score and how it moved. */
data class ScanBanner(
    val band: String,
    val delta: Int?,
    val hasPrevious: Boolean
)

/** Every state the Shield / Apps / History screens can be in. Nothing else. */
sealed interface ShieldUiState {
    data object Loading : ShieldUiState
    data class Ready(
        val snapshot: ScanSnapshot,
        val history: List<Store.ScoreRecord>,
        val events: List<Store.Event>,
        val apps: List<AppRisk>,
        val banner: ScanBanner?,
        val scanning: Boolean,
        val query: String
    ) : ShieldUiState
    data class Failed(val message: String) : ShieldUiState
}

/**
 * State holder for the main screens (the ViewModel role, without AndroidX).
 * Events flow down (rescan, setQuery, clearEvents), state flows up via StateFlow.
 * The Activity owns the CoroutineScope and cancels it in onDestroy; this holder
 * only launches child jobs into it, so nothing leaks past the screen.
 */
class ShieldStateHolder(
    private val repo: SecurityRepository,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow<ShieldUiState>(ShieldUiState.Loading)
    val state: StateFlow<ShieldUiState> = _state.asStateFlow()

    private var scanJob: Job? = null

    /** First load: runs a full scan, exactly like v2.0 did on launch. */
    fun start() {
        if (_state.value is ShieldUiState.Ready) return
        runScan(showBanner = true)
    }

    fun retry() {
        runScan(showBanner = true)
    }

    /** Manual rescan from the pinned Scan button. */
    fun rescan() {
        val cur = _state.value
        if (cur is ShieldUiState.Ready && cur.scanning) return
        if (cur is ShieldUiState.Ready) {
            _state.value = cur.copy(scanning = true, banner = null)
        }
        runScan(showBanner = true)
    }

    /** Re-reads stored data without rescanning (returning from a detail screen). */
    fun refreshStored() {
        val cur = _state.value
        if (cur !is ShieldUiState.Ready) return
        _state.value = cur.copy(
            history = repo.history(),
            events = repo.events(),
            apps = repo.allApps()
        )
    }

    fun setQuery(q: String) {
        val cur = _state.value
        if (cur is ShieldUiState.Ready && cur.query != q) {
            _state.value = cur.copy(query = q)
        }
    }

    fun clearEvents() {
        repo.clearEvents()
        refreshStored()
    }

    fun setToggle(key: String, value: Boolean) = repo.setToggle(key, value)

    fun toggle(key: String, def: Boolean): Boolean = repo.toggle(key, def)

    private fun runScan(showBanner: Boolean) {
        scanJob?.cancel()
        scanJob = scope.launch {
            try {
                val outcome: ScanOutcome = repo.runScan()
                val banner = if (showBanner) {
                    ScanBanner(
                        band = outcome.snapshot.band,
                        delta = outcome.delta,
                        hasPrevious = outcome.previousScore >= 0
                    )
                } else {
                    null
                }
                _state.value = ShieldUiState.Ready(
                    snapshot = outcome.snapshot,
                    history = repo.history(),
                    events = repo.events(),
                    apps = repo.allApps(),
                    banner = banner,
                    scanning = false,
                    query = (_state.value as? ShieldUiState.Ready)?.query ?: ""
                )
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                _state.value = ShieldUiState.Failed(
                    "The scan could not finish. Your last recorded score is still safe."
                )
            }
        }
    }
}

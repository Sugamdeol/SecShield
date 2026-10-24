package `in`.sugam.secshield.data

import android.content.Context
import `in`.sugam.secshield.domain.AppRisk
import `in`.sugam.secshield.domain.ScanSnapshot
import `in`.sugam.secshield.domain.ScoreEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Result of one scan, with the delta against the previous recorded scan. */
data class ScanOutcome(
    val snapshot: ScanSnapshot,
    /** Previous recorded score, or -1 when this is the first scan ever. */
    val previousScore: Int
) {
    val delta: Int? get() = if (previousScore >= 0) snapshot.score - previousScore else null
}

/**
 * Data layer: the single place that runs scans and owns persistence.
 * The UI never touches ScoreEngine, AppProbe, or Store directly; it talks
 * to this interface, which is what makes the screens testable without Android.
 */
interface SecurityRepository {
    suspend fun runScan(): ScanOutcome
    fun history(): List<Store.ScoreRecord>
    fun events(): List<Store.Event>
    fun clearEvents()
    fun toggle(key: String, def: Boolean): Boolean
    fun setToggle(key: String, value: Boolean)
    fun allApps(): List<AppRisk>
    fun trust(pkg: String)
    fun isTrusted(pkg: String): Boolean
}

class SecurityRepositoryImpl(
    private val appCtx: Context,
    private val engine: ScoreEngine,
    private val appProbe: AppProbe,
    private val store: Store
) : SecurityRepository {

    override suspend fun runScan(): ScanOutcome {
        val previous = store.history().firstOrNull()?.score ?: -1
        val snapshot = withContext(Dispatchers.Default) {
            engine.run(appCtx)
            appProbe.scan(appCtx)
            engine.snapshot()
        }
        store.recordScore(snapshot.score, snapshot.band)
        try {
            SecShieldWidget.updateAll(appCtx)
        } catch (ignored: Throwable) {
        }
        return ScanOutcome(snapshot, previous)
    }

    override fun history(): List<Store.ScoreRecord> = store.history()

    override fun events(): List<Store.Event> = store.events()

    override fun clearEvents() = store.clearEvents()

    override fun toggle(key: String, def: Boolean): Boolean = store.toggle(key, def)

    override fun setToggle(key: String, value: Boolean) = store.setToggle(key, value)

    override fun allApps(): List<AppRisk> = ArrayList(appProbe.allApps)

    override fun trust(pkg: String) = store.trust(pkg)

    override fun isTrusted(pkg: String): Boolean = store.isTrusted(pkg)
}

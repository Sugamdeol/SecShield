package `in`.sugam.secshield

import android.app.Application
import `in`.sugam.secshield.data.AppProbe
import `in`.sugam.secshield.data.SecurityRepositoryImpl
import `in`.sugam.secshield.data.Store
import `in`.sugam.secshield.domain.ScoreEngine
import `in`.sugam.secshield.ui.ShieldStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency graph (no Hilt, no Gradle): the Application owns the
 * singletons and the app-scoped coroutine scope, so the state holder survives
 * rotation and the screens always read one shared state.
 */
class App : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val store: Store by lazy { Store(this) }
    val engine: ScoreEngine by lazy { ScoreEngine() }
    val appProbe: AppProbe by lazy { AppProbe(this) }

    val repository by lazy {
        SecurityRepositoryImpl(applicationContext, engine, appProbe, store)
    }

    val holder: ShieldStateHolder by lazy { ShieldStateHolder(repository, appScope) }
}

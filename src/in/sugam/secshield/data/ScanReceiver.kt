package `in`.sugam.secshield.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import `in`.sugam.secshield.domain.ScoreEngine
import `in`.sugam.secshield.domain.Severity
import `in`.sugam.secshield.data.Store
import java.util.concurrent.Executors

/**
 * Runs the scheduled daily scan off the main thread, records the score in the
 * history, and only notifies when something actually needs attention: a
 * critical finding, or a score drop of 10 or more since the last scan.
 */
class ScanReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        try {
            val app = ctx.applicationContext
            Executors.newSingleThreadExecutor().execute {
                try {
                    val store = Store(app)
                    val previous = store.lastScore()
                    val engine = ScoreEngine()
                    engine.run(app)
                    store.recordScore(engine.score, engine.bandLabel())
                    SecShieldWidget.updateAll(app)
                    if (engine.criticalCount > 0) {
                        store.log(Severity.CRITICAL, "Daily scan found "
                                + engine.criticalCount + " critical issue"
                                + (if (engine.criticalCount == 1) "" else "s"),
                                "Score " + engine.score + "/100")
                        if (store.toggle("alerts", true)) {
                            Notifier.alert(app, "Daily scan: " + engine.criticalCount
                                    + " critical issue" + (if (engine.criticalCount == 1) "" else "s"),
                                    "Score " + engine.score + "/100. Open SecShield to fix it.")
                        }
                    } else if (previous >= 0 && previous - engine.score >= 10) {
                        store.log(Severity.HIGH, "Daily scan: score fell to "
                                + engine.score + "/100", "Was " + previous + "/100")
                        if (store.toggle("alerts", true)) {
                            Notifier.alert(app, "Daily scan: score fell to "
                                    + engine.score + "/100",
                                    "Was " + previous + "/100. Open SecShield to see what changed.")
                        }
                    } else {
                        store.log(Severity.OK, "Daily scan complete",
                                "Score " + engine.score + "/100, no critical issues")
                    }
                } catch (ignored: Throwable) {
                }
            }
        } catch (ignored: Throwable) {
        }
    }
}

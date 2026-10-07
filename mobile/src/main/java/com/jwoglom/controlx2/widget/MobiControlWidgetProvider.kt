package com.jwoglom.controlx2.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

open class MobiControlWidgetProvider : AppWidgetProvider() {
    internal open val presentation: WidgetPresentation = WidgetPresentation.COMPACT

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        renderAsync(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        renderAsync(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, javaClass))
            renderAsync(context, manager, ids)
            return
        }
        super.onReceive(context, intent)
    }

    private fun renderAsync(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
    ) {
        if (ids.isEmpty()) return
        val providerClass = javaClass
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val snapshot = MobiControlWidgetState.load(context)
                val now = Instant.now()
                ids.forEach { id ->
                    val options = manager.getAppWidgetOptions(id)
                    manager.updateAppWidget(
                        id,
                        MobiControlWidgetRenderer.render(
                            context,
                            id,
                            options,
                            snapshot,
                            now,
                            presentation,
                        ),
                    )
                }
                scheduleFreshnessBoundary(
                    context,
                    snapshot.nextFreshnessBoundary(now),
                    providerClass,
                )
            } catch (t: Throwable) {
                Timber.e(t, "Unable to render MobiControl widget")
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        internal const val ACTION_REFRESH = "local.controlx21.action.REFRESH_WIDGET"
        private const val UPDATE_DEBOUNCE_MILLIS = 750L
        private val mainHandler = Handler(Looper.getMainLooper())
        private var pendingUpdate: Runnable? = null

        /**
         * Redraw after existing state has been persisted. This method never requests pump data.
         * Replacing the pending runnable coalesces history catch-up bursts into one widget update.
         */
        fun requestUpdate(context: Context) {
            val appContext = context.applicationContext
            synchronized(this) {
                pendingUpdate?.let(mainHandler::removeCallbacks)
                pendingUpdate = Runnable {
                    PROVIDER_CLASSES.forEach { providerClass ->
                        appContext.sendBroadcast(refreshIntent(appContext, providerClass))
                    }
                    synchronized(this) { pendingUpdate = null }
                }.also { mainHandler.postDelayed(it, UPDATE_DEBOUNCE_MILLIS) }
            }
        }

        private fun scheduleFreshnessBoundary(
            context: Context,
            boundary: Instant?,
            providerClass: Class<out AppWidgetProvider>,
        ) {
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                providerClass.name.hashCode(),
                refreshIntent(context, providerClass),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarmManager.cancel(pendingIntent)
            if (boundary != null) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC,
                    boundary.toEpochMilli(),
                    pendingIntent,
                )
            }
        }

        private fun refreshIntent(
            context: Context,
            providerClass: Class<out AppWidgetProvider>,
        ): Intent = Intent(context, providerClass).setAction(ACTION_REFRESH)

        internal val PROVIDER_CLASSES: List<Class<out AppWidgetProvider>> = listOf(
            MobiControlWidgetProvider::class.java,
            MobiControlWideWidgetProvider::class.java,
            MobiControlHistoryWidgetProvider::class.java,
            MobiControlCoverCompactWidgetProvider::class.java,
            MobiControlCoverWideWidgetProvider::class.java,
            MobiControlCarGlanceWidgetReceiver::class.java,
        )
    }
}

class MobiControlWideWidgetProvider : MobiControlWidgetProvider()

class MobiControlHistoryWidgetProvider : MobiControlWidgetProvider() {
    override val presentation: WidgetPresentation = WidgetPresentation.EXPANDED
}

class MobiControlCoverCompactWidgetProvider : MobiControlWidgetProvider() {
    override val presentation: WidgetPresentation = WidgetPresentation.COVER_COMPACT
}

class MobiControlCoverWideWidgetProvider : MobiControlWidgetProvider() {
    override val presentation: WidgetPresentation = WidgetPresentation.COVER_WIDE
}

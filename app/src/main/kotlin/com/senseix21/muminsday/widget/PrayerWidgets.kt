package com.senseix21.muminsday.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.senseix21.muminsday.MainActivity
import com.senseix21.muminsday.R
import com.senseix21.muminsday.alarm.AlarmScheduler
import com.senseix21.muminsday.alarm.LOG_TAG
import com.senseix21.muminsday.alarm.currentHealth
import com.senseix21.muminsday.clock.CalcTimesClock
import com.senseix21.muminsday.planner.PlannerSettings
import com.senseix21.muminsday.store.Stores
import com.senseix21.muminsday.ui.NO_TIME
import com.senseix21.muminsday.ui.Texts
import com.senseix21.muminsday.ui.inLanguage
import java.time.Duration
import java.time.Instant

/**
 * The two home-screen widgets (spec 03, forward path). They draw what `widget/` in core says and compute nothing. They
 * are redrawn by the alarm path's replan (every trigger ends in [refreshWidgets]) and by one alarm at the next prayer
 * or the next local midnight. There is no polling: `updatePeriodMillis` is 0 in both provider files.
 */
class NextPrayerWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refreshWidgets(context, Instant.now())
}

class FiveTimesWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refreshWidgets(context, Instant.now())
}

/** Fires at the next prayer (or local midnight) so a widget never keeps showing a prayer that has gone by. */
class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) refreshWidgets(context, Instant.now())
    }
}

private const val ACTION_REFRESH = "com.senseix21.muminsday.WIDGET_REFRESH"

/** Outside the planner's closed id space and below the Test Adhan's 1000, so a replan's cancel-all never touches it. */
private const val REFRESH_REQUEST_CODE = 1001

/** Why a wake-up after a prayer's own instant is not needed: the refresh runs at the instant, and 1 s clears it. */
private val AFTER_INSTANT: Duration = Duration.ofSeconds(1)

/**
 * Redraws every placed widget from [now], then sets the one alarm that redraws them next. With no widget placed it
 * cancels that alarm and does nothing else. A failure here is logged and never reaches the reminder path.
 */
fun refreshWidgets(context: Context, now: Instant) {
    try {
        draw(context, now)
    } catch (e: RuntimeException) {
        Log.e(LOG_TAG, "widgets: could not redraw", e)
    }
}

private fun draw(context: Context, now: Instant) {
    val manager = AppWidgetManager.getInstance(context)
    val nextIds = manager.getAppWidgetIds(ComponentName(context, NextPrayerWidget::class.java))
    val fiveIds = manager.getAppWidgetIds(ComponentName(context, FiveTimesWidget::class.java))
    if (nextIds.isEmpty() && fiveIds.isEmpty()) {
        cancelRefresh(context)
        return
    }

    val store = Stores.settings(context)
    val settings = store.read()
    val language = store.readApp().language
    val localized = context.inLanguage(language)
    val texts = Texts(localized, language)
    val health = currentHealth(context, now)
    val clock = CalcTimesClock()
    val next = nextPrayerModel(clock, now, settings.place, settings.clock, health)

    if (nextIds.isNotEmpty()) manager.updateAppWidget(nextIds, nextViews(context, localized, texts, settings, next))
    if (fiveIds.isNotEmpty()) {
        val five = fiveTimesModel(clock, now, settings.place, settings.clock, health)
        manager.updateAppWidget(fiveIds, fiveViews(context, localized, texts, settings, five))
    }
    scheduleRefresh(context, now, settings, next)
}

private fun nextViews(context: Context, localized: Context, texts: Texts, settings: PlannerSettings, model: NextPrayerModel): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_next)
    val zone = settings.place.zone
    when (model) {
        is NextPrayerModel.Found -> {
            views.setTextViewText(R.id.widget_title, localized.getString(R.string.home_next, texts.salah(model.salah)))
            views.setTextViewText(R.id.widget_time, texts.time(model.at, zone))
        }
        // INV-T7: no time can be named, so none is shown.
        is NextPrayerModel.Unknown -> {
            views.setTextViewText(R.id.widget_title, localized.getString(R.string.home_next_unknown))
            views.setTextViewText(R.id.widget_time, NO_TIME)
        }
    }
    showWarning(views, localized, model.remindersDegraded)
    views.setOnClickPendingIntent(R.id.widget_root, openApp(context))
    return views
}

private fun fiveViews(context: Context, localized: Context, texts: Texts, settings: PlannerSettings, model: FiveTimesModel): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_five)
    val zone = settings.place.zone
    model.rows.forEachIndexed { index, row ->
        views.setTextViewText(ROW_NAMES[index], texts.salah(row.salah))
        views.setTextViewText(ROW_TIMES[index], row.at?.let { texts.time(it, zone) } ?: NO_TIME)
    }
    showWarning(views, localized, model.remindersDegraded)
    views.setOnClickPendingIntent(R.id.widget_root, openApp(context))
    return views
}

private val ROW_NAMES = intArrayOf(
    R.id.widget_row_name_0, R.id.widget_row_name_1, R.id.widget_row_name_2, R.id.widget_row_name_3, R.id.widget_row_name_4,
)
private val ROW_TIMES = intArrayOf(
    R.id.widget_row_time_0, R.id.widget_row_time_1, R.id.widget_row_time_2, R.id.widget_row_time_3, R.id.widget_row_time_4,
)

/** INV-S6: when reminders cannot be promised the widget says so in words, as the home screen does. */
private fun showWarning(views: RemoteViews, localized: Context, degraded: Boolean) {
    views.setViewVisibility(R.id.widget_warning, if (degraded) View.VISIBLE else View.GONE)
    if (degraded) views.setTextViewText(R.id.widget_warning, localized.getString(R.string.widget_degraded))
}

private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
    context,
    0,
    Intent(context, MainActivity::class.java),
    PendingIntent.FLAG_IMMUTABLE,
)

private fun refreshIntent(context: Context, flags: Int): PendingIntent? = PendingIntent.getBroadcast(
    context,
    REFRESH_REQUEST_CODE,
    Intent(context, WidgetRefreshReceiver::class.java).setAction(ACTION_REFRESH),
    flags or PendingIntent.FLAG_IMMUTABLE,
)

private fun cancelRefresh(context: Context) {
    val existing = refreshIntent(context, PendingIntent.FLAG_NO_CREATE) ?: return
    checkNotNull(context.getSystemService(AlarmManager::class.java)).cancel(existing)
    existing.cancel()
}

/**
 * One alarm, at whichever comes first: the next prayer or the start of the next local day (the five-times widget shows
 * "today"). Exact when the phone allows it, otherwise a window the system may move by a little; a widget is a display,
 * so late is acceptable here where it is not for the Adhan.
 */
private fun scheduleRefresh(context: Context, now: Instant, settings: PlannerSettings, next: NextPrayerModel) {
    val zone = settings.place.zone
    val midnight = now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
    val at = when (next) {
        is NextPrayerModel.Found -> minOf(next.at, midnight)
        is NextPrayerModel.Unknown -> midnight
    }.plus(AFTER_INSTANT)
    val operation = checkNotNull(refreshIntent(context, PendingIntent.FLAG_UPDATE_CURRENT))
    val alarms = checkNotNull(context.getSystemService(AlarmManager::class.java))
    try {
        if (AlarmScheduler(context).canScheduleExact()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC, at.toEpochMilli(), operation)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC, at.toEpochMilli(), operation)
        }
    } catch (_: SecurityException) {
        alarms.setAndAllowWhileIdle(AlarmManager.RTC, at.toEpochMilli(), operation)
    }
}

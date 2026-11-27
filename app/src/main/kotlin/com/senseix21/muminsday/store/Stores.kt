package com.senseix21.muminsday.store

import android.content.Context

/**
 * One database helper per file and one settings reader per process. Receivers, the service and the activities all ask
 * here, so none of them opens its own SQLite connection (ADR-5). Not a DI container: a few functions and two fields.
 */
object Stores {
    private var alarmLogDatabase: Database? = null
    private var deedDaysDatabase: Database? = null
    private var legacyRemoved = false

    fun alarmLog(context: Context): AlarmLog = AlarmLog(
        synchronized(this) {
            alarmLogDatabase ?: open(context, ALARM_LOG_FILE, alarmLogMigrations).also { alarmLogDatabase = it }
        },
    )

    fun deedDays(context: Context): DeedDays = DeedDays(
        synchronized(this) {
            deedDaysDatabase ?: open(context, DEED_DAYS_FILE, deedMigrations).also { deedDaysDatabase = it }
        },
    )

    private fun open(context: Context, file: String, history: List<Migration>): Database {
        removeLegacyDatabase(context.applicationContext)
        return Database(context.applicationContext, file, history)
    }

    /**
     * G34: before the split one file, `mumins-day.db`, held both tables. It is deleted, not copied. The alarm log is
     * trimmed to 30 days and refills at the next replan; the log is a record of this phone's own alarms and the old
     * file was never in a backup. Its `deed_day` table had no writer: no screen existed that wrote it, and no release has
     * shipped. Caller holds the [Stores] lock.
     */
    private fun removeLegacyDatabase(context: Context) {
        if (legacyRemoved) return
        context.deleteDatabase(LEGACY_DATABASE_FILE)
        legacyRemoved = true
    }

    fun settings(context: Context): SettingsStore =
        SettingsStore(context.applicationContext.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE))

    fun device(context: Context): DeviceStore =
        DeviceStore(context.applicationContext.getSharedPreferences(DEVICE_FILE, Context.MODE_PRIVATE))
}

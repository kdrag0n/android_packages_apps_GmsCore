/*
 * SPDX-FileCopyrightText: 2020, microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.nearby.exposurenotification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.microg.gms.common.ForegroundServiceContext

class CleanupService : LifecycleService() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ForegroundServiceContext.completeForegroundService(this, intent, TAG)
        super.onStartCommand(intent, flags, startId)
        if (isNeeded(this)) {
            lifecycleScope.launchWhenStarted {
                launch(Dispatchers.IO) {
                    var workPending = true
                    while (workPending) {
                        ExposureDatabase.with(this@CleanupService) {
                            workPending = !it.dailyCleanup()
                        }
                        if (workPending) delay(5000L)
                    }
                    ExposurePreferences(this@CleanupService).lastCleanup = System.currentTimeMillis()
                }
                stop()
            }
        } else {
            stop()
        }
        return START_NOT_STICKY
    }

    fun stop() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = PendingIntent.getService(applicationContext, CleanupService::class.java.name.hashCode(), Intent(applicationContext, CleanupService::class.java), PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT)
        alarmManager.set(AlarmManager.RTC, ExposurePreferences(this).lastCleanup + CLEANUP_INTERVAL, pendingIntent)
        stopSelf()
    }

    companion object {
        fun isNeeded(context: Context): Boolean {
            return ExposurePreferences(context).let {
                it.enabled && it.lastCleanup < System.currentTimeMillis() - CLEANUP_INTERVAL
            }
        }
    }
}

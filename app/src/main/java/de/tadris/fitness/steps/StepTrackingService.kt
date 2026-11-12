/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack
 *
 * FitoTrack is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     FitoTrack is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.tadris.fitness.steps

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import de.tadris.fitness.Instance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class StepTrackingService : Service() {

    companion object {
        const val NOTIFICATION_ID = 20
        const val SENSOR_LATENCY_US = 15 * 1_000_000
        private const val WINDOW_SIZE_MS = 15 * 60 * 1000L
        private const val PERSIST_INTERVAL_MS = 3 * 60 * 1000L
        private const val ACTIVE_MINUTE_THRESHOLD = 100

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning

        private val _pendingSteps = MutableStateFlow(0)
        val pendingSteps: StateFlow<Int> = _pendingSteps
    }

    private val executor = Executors.newSingleThreadExecutor()

    private lateinit var registrar: StepRegistrar
    private lateinit var minuteConsolidator: TimeConsolidator
    private lateinit var consolidator: SampleConsolidator
    private lateinit var persister: StepPersister
    private lateinit var dailyStepCounter: DailyStepCounter
    private lateinit var notificationBuilder: StepNotificationBuilder
    private lateinit var activityRecognizer: StepActivityRecognizer

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val db = Instance.getInstance(this).db
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationBuilder = StepNotificationBuilder(this, notificationManager)
        dailyStepCounter = DailyStepCounter(
            dao = db.stepDao(),
            onTodayStepsChanged = { notificationBuilder.update(it) },
        )
        persister = StepPersister(db.stepDao())
        activityRecognizer = StepActivityRecognizer(this, db.workoutDao())
        consolidator = SampleConsolidator(
            persistIntervalMs = PERSIST_INTERVAL_MS,
            windowSizeMs = WINDOW_SIZE_MS,
            activeMinuteThreshold = ACTIVE_MINUTE_THRESHOLD,
            onPendingStepsChanged = { pending ->
                _pendingSteps.value = pending
                dailyStepCounter.onPendingStepsChanged(pending)
            },
            onPersistReady = { windowStart, steps, activeMinutes ->
                dailyStepCounter.onStepsPersisted(steps)
                executor.submit { persister.persist(windowStart, steps, activeMinutes) }
            },
        )
        minuteConsolidator = TimeConsolidator(60_000) {
            consolidator.onMinuteReceived(it)
            if (Instance.getInstance(this).userPreferences.autoStartRecordingEnabled) {
                activityRecognizer.onMinuteReceived(it)
            }
        }
        registrar = StepRegistrar(
            sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager,
            executor = executor,
            onStep = { delta -> minuteConsolidator.receiveSteps(delta) },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = notificationBuilder.buildInitial()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.let { registrar.start(it) }
        executor.submit { dailyStepCounter.initialize() }

        Instance.getInstance(this).userPreferences.stepTrackingEnabled = true
        _isRunning.value = true
        return START_STICKY
    }

    override fun onDestroy() {
        _isRunning.value = false
        registrar.stop()
        executor.submit { minuteConsolidator.flush(); consolidator.flush() }
        executor.shutdown()
        executor.awaitTermination(5, TimeUnit.SECONDS)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}

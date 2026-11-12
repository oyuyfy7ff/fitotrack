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

package de.tadris.fitness.recording.component

import de.tadris.fitness.Instance
import de.tadris.fitness.data.preferences.UserPreferences
import de.tadris.fitness.recording.GpsState
import de.tadris.fitness.recording.RecorderService
import de.tadris.fitness.recording.RecordingState
import de.tadris.fitness.recording.StopReason
import de.tadris.fitness.recording.actions.WorkoutActionDiscard
import de.tadris.fitness.recording.event.AggregatedRecordingValuesEvent
import de.tadris.fitness.recording.event.WorkoutAutoStopEvent
import de.tadris.fitness.recording.event.WorkoutGPSStateChanged
import de.tadris.fitness.util.WorkoutLogger.Companion.log
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe

class AutoStopBackgroundRecordingsComponent : LastSampleTimeComponent() {

    companion object {
        private const val TAG = "AutoStopBackground"
        private const val AUTO_PAUSE_TIMEOUT_MS = 7 * 60 * 1000L
        private const val INITIAL_ACTIVITY_TIMEOUT_MS = 1 * 60 * 1000L
        private const val GPS_LOST_TIMEOUT_MS = 1 * 60 * 1000L
        private const val NO_SAMPLE_TIMEOUT_MS = 7 * 60 * 1000L
        private const val MIN_SAMPLES_TO_KEEP = 10
    }

    private var autoPauseEnabled = false
    private var signalLostSince = 0L
    private var stopTriggered = false

    override fun register(service: RecorderService) {
        super.register(service)
        autoPauseEnabled = UserPreferences(service).useAutoPause
    }

    @Subscribe
    fun onGpsStateChange(e: WorkoutGPSStateChanged) {
        signalLostSince = if (e.newState == GpsState.SIGNAL_LOST) {
            System.currentTimeMillis()
        } else {
            0L
        }
    }

    override fun checkSlow() {
        if (stopTriggered) return
        val state = service.stateHolder.state
        val data = AggregatedRecordingValuesEvent.getCurrent()
        val now = System.currentTimeMillis()

        // Auto pause has been active too long
        if (autoPauseEnabled && state == RecordingState.AUTO_PAUSE) {
            val pausedFor = now - service.stateHolder.lastStateChange
            if (pausedFor > AUTO_PAUSE_TIMEOUT_MS) {
                log(TAG, "Stopping: auto pause active for ${pausedFor}ms")
                triggerStop()
                return
            }
        }

        // GPS signal lost too long
        if (signalLostSince > 0L && (now - signalLostSince) > GPS_LOST_TIMEOUT_MS) {
            log(TAG, "Stopping: GPS signal lost for ${now - signalLostSince}ms")
            triggerStop()
            return
        }

        // Auto pause disabled but no sample received for too long
        if (!autoPauseEnabled && lastSampleTime > 0L && state == RecordingState.RUNNING) {
            val timeSinceLastSample = now - lastSampleTime
            if (timeSinceLastSample > NO_SAMPLE_TIMEOUT_MS) {
                log(TAG, "Stopping: no sample for ${timeSinceLastSample}ms and auto pause is off")
                triggerStop()
                return
            }
        }

        if (data.sampleCount < MIN_SAMPLES_TO_KEEP && data.totalDuration > INITIAL_ACTIVITY_TIMEOUT_MS){
            log(TAG, "Stopping: not enough activity after starting the workout for ${INITIAL_ACTIVITY_TIMEOUT_MS}ms")
            triggerStop()
        }
    }

    private fun triggerStop() {
        stopTriggered = true
        val workoutId = service.stateHolder.workoutId
        val sampleCount = Instance.getInstance(service).db.workoutDao()
            .getAllSamplesOfWorkout(workoutId).size

        service.actions.stop(StopReason.AUTO_STOP)

        if (sampleCount < MIN_SAMPLES_TO_KEEP) {
            log(TAG, "Discarding workout: only $sampleCount samples recorded")
            EventBus.getDefault().post(WorkoutActionDiscard)
        }

        EventBus.getDefault().post(WorkoutAutoStopEvent()) // notify UI if attached
        service.stopSelf()
    }
}

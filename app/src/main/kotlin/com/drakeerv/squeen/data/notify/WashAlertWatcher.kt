package com.drakeerv.squeen.data.notify

import com.drakeerv.squeen.data.ApiResult
import com.drakeerv.squeen.data.SpeedQueenRepository
import com.drakeerv.squeen.data.net.MachineStatusSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Watches the live machine status stream for machines that have an active wash alert
 * and raises local notifications ("~3 minutes left", "cycle complete").
 *
 * Without an FCM project the server cannot push to this build, so this is a best-effort
 * substitute while the app process is alive.
 */
class WashAlertWatcher(
    private val repository: SpeedQueenRepository,
    private val socket: MachineStatusSocket,
    private val notifications: NotificationHelper,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var collectJob: Job? = null

    private val alertMachines = ConcurrentHashMap.newKeySet<String>() // machine ids
    private val alertRooms = ConcurrentHashMap.newKeySet<String>() // room ids
    private val lastRemaining = ConcurrentHashMap<String, Int>()
    private val notifiedThreeMinutes = ConcurrentHashMap.newKeySet<String>()
    private val notifiedComplete = ConcurrentHashMap.newKeySet<String>()

    /** Reloads the user's wash-alert subscriptions and (re)subscribes the socket. */
    fun refresh() {
        scope.launch {
            val alerts = (repository.washAlerts() as? ApiResult.Success)?.data.orEmpty()
            val machines = alerts.flatMap { it.machineIds }.toSet()
            val rooms = alerts.flatMap { it.roomIds }.toSet()

            alertMachines.clear()
            alertMachines.addAll(machines)

            val previousRooms = alertRooms.toSet()
            alertRooms.clear()
            alertRooms.addAll(rooms)
            (rooms - previousRooms).forEach { socket.watchRoom(it) }
            (previousRooms - rooms).forEach { socket.stopWatchingRoom(it) }

            lastRemaining.keys.retainAll(machines)
            notifiedThreeMinutes.retainAll(machines)
            notifiedComplete.retainAll(machines)

            if (alerts.isNotEmpty()) ensureCollecting()
        }
    }

    /** Stops tracking (e.g. after sign-out). */
    fun stop() {
        collectJob?.cancel()
        collectJob = null
        alertRooms.forEach { socket.stopWatchingRoom(it) }
        alertRooms.clear()
        alertMachines.clear()
        lastRemaining.clear()
        notifiedThreeMinutes.clear()
        notifiedComplete.clear()
    }

    private fun ensureCollecting() {
        if (collectJob?.isActive == true) return
        collectJob = scope.launch {
            socket.events.collect { event ->
                when (event) {
                    is MachineStatusSocket.Event.MachineStatus -> handleStatus(
                        machineId = event.update.machineId,
                        roomId = event.update.roomId,
                        remainingSeconds = event.update.remainingSeconds,
                    )
                    else -> Unit
                }
            }
        }
    }

    private fun handleStatus(machineId: String, roomId: String?, remainingSeconds: Int) {
        if (alertMachines.isEmpty()) return
        if (machineId !in alertMachines) return
        if (roomId != null && alertRooms.isNotEmpty() && roomId !in alertRooms) return

        val previous = lastRemaining.put(machineId, remainingSeconds)

        // New cycle started (time went up), reset the notification flags.
        if (previous != null && remainingSeconds > previous + 30) {
            notifiedThreeMinutes.remove(machineId)
            notifiedComplete.remove(machineId)
            return
        }

        if (remainingSeconds in 1..180 && notifiedThreeMinutes.add(machineId)) {
            notifications.notifyWashAlert(machineName(machineId), minutesLeft = (remainingSeconds + 59) / 60)
        }

        val finished = previous != null && previous > 0 && remainingSeconds <= 0
        if (finished && notifiedComplete.add(machineId)) {
            notifications.notifyMachineComplete(machineName(machineId))
            notifiedThreeMinutes.remove(machineId)
        }
    }

    private fun machineName(machineId: String): String = "Machine $machineId"
}

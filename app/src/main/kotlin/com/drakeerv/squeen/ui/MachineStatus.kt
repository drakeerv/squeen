package com.drakeerv.squeen.ui

import com.drakeerv.squeen.data.model.Machine

/** Human readable status for the machine grids and detail pages. */
fun machineStatusLabel(machine: Machine): String = when (machine.status) {
    "", "AVAILABLE" -> "Available"
    "IN_USE", "PARTIAL_VEND" -> "${(machine.remainingSeconds + 59) / 60} min left"
    "READY_TO_START" -> "Ready to start"
    "DOOR_OPEN" -> "Door open"
    "OUT_OF_ORDER", "ERROR" -> "Out of order"
    "OFFLINE", "NETWORK_ERROR" -> "Offline"
    "POWERED_DOWN" -> "Powered down"
    "UNKNOWN" -> "Status unknown"
    "RESERVED" -> "Reserved"
    "UNAVAILABLE" -> "Unavailable"
    else -> machine.status.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}

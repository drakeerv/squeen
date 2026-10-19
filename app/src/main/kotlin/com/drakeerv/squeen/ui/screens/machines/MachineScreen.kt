package com.drakeerv.squeen.ui.screens.machines

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dry
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.drakeerv.squeen.data.ApiResult
import com.drakeerv.squeen.data.SpeedQueenRepository
import com.drakeerv.squeen.data.model.BalanceInfo
import com.drakeerv.squeen.data.model.Cycle
import com.drakeerv.squeen.data.model.Machine
import com.drakeerv.squeen.data.model.MachineCycles
import com.drakeerv.squeen.data.model.Modifier as CycleModifier
import com.drakeerv.squeen.data.model.User
import com.drakeerv.squeen.data.net.MachineStatusSocket
import com.drakeerv.squeen.data.notify.WashAlertWatcher
import com.drakeerv.squeen.ui.Money
import com.drakeerv.squeen.ui.components.AppTopBar
import com.drakeerv.squeen.ui.components.LoadingState
import com.drakeerv.squeen.ui.components.LocalSnackbarHost
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt
import java.util.concurrent.atomic.AtomicBoolean

/** A cycle "family" (Normal, Permanent Press, Delicate…) with its temperature variants. */
data class CycleFamily(
    val key: String,
    val variants: List<Cycle>,
) {
    val label: String get() = cycleLabel(key)
    val temperatures: List<String> get() = variants.mapNotNull { temperatureOf(it) }
}

data class MachineUiState(
    val loading: Boolean = true,
    val machine: Machine? = null,
    val roomName: String = "",
    val cycles: MachineCycles? = null,
    val cyclesSupported: Boolean = false,
    val isIncludedLaundry: Boolean = false,
    val families: List<CycleFamily> = emptyList(),
    val selectedFamilyKey: String? = null,
    val selectedTemperature: String? = null,
    val selectedModifierId: String? = null,
    /** True once the user has picked options, so live frames stop overwriting the picker. */
    val userSelectedOptions: Boolean = false,
    val selectionSaving: Boolean = false,
    val user: User? = null,
    val balance: BalanceInfo? = null,
    /** user-accounts id used by vend and top-off, not the sub-account id. */
    val userAccountId: String? = null,
    val priceCents: Int = 0,
    val requestingPrice: Boolean = false,
    val priceChecked: Boolean = false,
    val startPhase: StartPhase = StartPhase.Idle,
    val vendRunning: Boolean = false,
    val topOffRunning: Boolean = false,
    val useRewards: Boolean = false,
    val emailAlertEnabled: Boolean = false,
    val smsAlertEnabled: Boolean = false,
    val smsAvailable: Boolean = false,
    val smsReason: String? = null,
    val phoneDisplay: String? = null,
    val alertWindowOpen: Boolean = true,
    val remainingSeconds: Int = 0,
    val statusId: String? = null,
    val canTopOff: Boolean = false,
    val topOffCents: Int = 0,
    val topOffSeconds: Int = 0,
    val topOffDisabled: Boolean = false,
    val dryerUnits: Int = 0,
    val dryerMaxUnits: Int = 0,
    val live: Boolean = false,
    val error: String? = null,
    val dialog: MachineDialog? = null,
) {
    val selectedCycle: Cycle?
        get() = families.firstOrNull { it.key == selectedFamilyKey }
            ?.variants?.firstOrNull { temperatureOf(it) == selectedTemperature }
            ?: families.firstOrNull { it.key == selectedFamilyKey }?.variants?.firstOrNull()

    val selectedFamily: CycleFamily?
        get() = families.firstOrNull { it.key == selectedFamilyKey }

    val selectedModifier: CycleModifier?
        get() = cycles?.modifiers?.firstOrNull { it.id == selectedModifierId }

    val isRunning: Boolean
        get() = (statusId ?: machine?.statusId)?.uppercase() in com.drakeerv.squeen.data.model.Machine.BUSY_STATUSES

    val isDryerMachine: Boolean get() = machine?.isDryer == true

    /** True while a start is being prepared or confirmed; all start controls are locked. */
    val starting: Boolean get() = startPhase != StartPhase.Idle

    /**
     * Official rule (MachineFragment): the "add more drying time" button only appears for the
     * user's own running dryer when the live frame reports canTopOff and top-off is not
     * fully disabled.
     */
    val canAddDryingTime: Boolean
        get() = machine?.isMyMachine == true &&
            machine.isDryer &&
            machine.canTopOff &&
            !topOffDisabled &&
            remainingSeconds > 0

    /** Machines without time selection: the base price from the live status. */
    private val basePriceCents: Int get() = if (priceCents > 0) priceCents else machine?.remainingVend ?: 0

    /**
     * Price in minor units. Dryers add the selected number of time units, mirroring the
     * official client: price = base + units × topOffVend, time = base + units × topOffTime.
     */
    val effectivePriceCents: Int
        get() = basePriceCents + if (isDryerMachine && !isRunning) dryerUnits * topOffCents else 0

    /** Total cycle time currently selected, in seconds. */
    val effectiveTimeSeconds: Int
        get() {
            val base = machine?.remainingSeconds ?: 0
            return base + if (isDryerMachine && !isRunning) dryerUnits * topOffSeconds else 0
        }

    val availableCents: Int
        get() = (balance?.amount?.toInt() ?: 0) + if (useRewards) (balance?.promotionalCredit?.toInt() ?: 0) else 0

    /** Extra time units the machine accepts right now (1..39, capped by the 99 minute cycle). */
    val topOffMaxUnits: Int
        get() {
            if (topOffSeconds <= 0) return 1
            return ((MachineViewModel.MAX_CYCLE_SECONDS - remainingSeconds) / topOffSeconds)
                .coerceIn(1, MachineViewModel.ABSOLUTE_MAX_UNITS)
        }
}

/** Stages of the official start sequence: selection, price, vend, command confirmation. */
enum class StartPhase { Idle, Selecting, Pricing, Vending, Confirming }

sealed interface MachineDialog {
    data class Success(val machineName: String) : MachineDialog
    data class Failed(val message: String, val httpCode: Int? = null) : MachineDialog
    data object NoConfirmation : MachineDialog
    data class NotEnoughFunds(val balanceName: String) : MachineDialog
    data object NoAmountToCharge : MachineDialog
    data class ConfirmStart(val amountCents: Int) : MachineDialog
    data class PriceChanged(val amountCents: Int) : MachineDialog
    data class ConfirmTopOff(val units: Int) : MachineDialog
}

class MachineViewModel(
    private val repository: SpeedQueenRepository,
    private val socket: MachineStatusSocket,
    private val washAlertWatcher: WashAlertWatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(MachineUiState())
    val state: StateFlow<MachineUiState> = _state.asStateFlow()

    private val pollInFlight = AtomicBoolean(false)

    /**
     * Terminal COMMAND_RESPONSE statuses keyed by command id. The WebSocket can deliver the
     * response before the REST call returns, so a small replay buffer avoids losing it.
     */
    private val recentCommandStatuses = LinkedHashMap<String, String>()
    private val commandWaiters = mutableMapOf<String, CompletableDeferred<String>>()

    private var selectionJob: Job? = null
    private var selectionSeq = 0

    companion object {
        /**
         * The service caps a machine cycle at 99 minutes (5940 s). The official dryer slider
         * offers `(5940 - baseSeconds) / topOffTime` extra units, at most 39
         * (MachineFragment: `remainingSeconds = (5940 - remainingSeconds) / topOffTime`, cap 39).
         */
        const val MAX_CYCLE_SECONDS = 5940
        const val ABSOLUTE_MAX_UNITS = 39

        fun maxDryerUnits(baseSeconds: Int, topOffSeconds: Int): Int {
            if (topOffSeconds <= 0 || baseSeconds <= 0) return 0
            return ((MAX_CYCLE_SECONDS - baseSeconds) / topOffSeconds).coerceIn(0, ABSOLUTE_MAX_UNITS)
        }

        /** "X min Y sec", the official `x_min_y_sec` format. */
        fun formatCycleTime(seconds: Int): String {
            val minutes = seconds / 60
            val rest = seconds % 60
            return "$minutes min $rest sec"
        }
    }

    fun load(roomId: String, machineId: String) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }

            val machinesResult = repository.roomMachines(roomId)
            val machineFromList = (machinesResult as? ApiResult.Success)?.data?.firstOrNull { it.id == machineId }
            val machine = machineFromList ?: (repository.roomMachineByNumber(roomId, null, machineId) as? ApiResult.Success)?.data
            val room = (repository.room(roomId) as? ApiResult.Success)?.data
            val roomName = room?.name.orEmpty()

            val user = (repository.getUser() as? ApiResult.Success)?.data
            val cycles = (repository.machineCycles(machineId) as? ApiResult.Success)?.data
            val families = cycles?.cycles?.let { cyclesToFamilies(it) } ?: emptyList()
            val defaultSelection = selectionFor(machine, cycles, families)
            val defaultFamily = families.firstOrNull { it.key == defaultSelection?.first } ?: families.firstOrNull()
            val defaultTemperature = defaultSelection?.second ?: defaultFamily?.temperatures?.firstOrNull()
            val defaultModifier = machine?.selectedModifierId
                ?.takeIf { id -> cycles?.modifiers?.any { it.id == id } == true }
                ?: cycles?.modifiers?.singleOrNull()?.id

            val account = user?.accounts?.firstOrNull()
            val balance = account?.let { acc ->
                (repository.balance(acc.id, acc.subAccounts.firstOrNull()?.geoBoundaryId) as? ApiResult.Success)?.data
            }
            val userAccountId = balance?.userAccountId ?: account?.subAccounts?.firstOrNull()?.userAccountId
            // Included (free) laundry organizations: the official app hides price/start entirely.
            val includedLaundry = room
                ?.geoBoundaryId
                ?.takeIf { it.isNotBlank() }
                ?.let { geoBoundaryId ->
                    (repository.locationContext(geoBoundaryId) as? ApiResult.Success)
                        ?.data?.organization?.isIncludedLaundry
                } ?: false

            val washAlerts = (repository.washAlerts() as? ApiResult.Success)?.data.orEmpty()
            val alertForMachine = washAlerts.filter { alert -> alert.machineIds.contains(machineId) }

            // SMS alerts are server-side subscriptions on the user's phone number.
            val phone = user?.phoneNumbers?.firstOrNull()
            val phoneCountryOk = repository.isSmsCountryEnabled(phone?.internationalCode)
            val smsAvailable = phone != null && phoneCountryOk && (room?.canSms == true)
            val smsReason = when {
                phone == null -> "Add a phone number to your profile to get texts"
                !phoneCountryOk -> "SMS alerts aren't available in your country yet"
                room?.canSms != true -> "This room doesn't support text alerts"
                else -> null
            }
            val runningNow = machine?.isRunning == true && (machine.remainingSeconds in 1..180)

            _state.update {
                it.copy(
                    loading = false,
                    machine = machine,
                    roomName = roomName,
                    cycles = cycles,
                    cyclesSupported = cycles?.isSupported == true,
                    isIncludedLaundry = includedLaundry,
                    families = families,
                    selectedFamilyKey = defaultFamily?.key,
                    selectedTemperature = defaultTemperature,
                    selectedModifierId = defaultModifier,
                    user = user,
                    balance = balance,
                    userAccountId = userAccountId,
                    remainingSeconds = if (machine?.isRunning == true) machine.remainingSeconds else 0,
                    statusId = machine?.statusId,
                    canTopOff = machine?.isDryer == true,
                    topOffCents = machine?.topOffVend ?: 0,
                    topOffSeconds = machine?.topOffTime ?: 0,
                    topOffDisabled = machine?.topOffFullyDisabled ?: false,
                    dryerMaxUnits = MachineViewModel.maxDryerUnits(
                        baseSeconds = machine?.remainingSeconds ?: 0,
                        topOffSeconds = machine?.topOffTime ?: 0,
                    ),
                    emailAlertEnabled = alertForMachine.any { alert -> !alert.email.isNullOrBlank() },
                    smsAlertEnabled = alertForMachine.any { alert -> !alert.phone.isNullOrBlank() },
                    smsAvailable = smsAvailable,
                    smsReason = smsReason,
                    phoneDisplay = phone?.formatted?.takeIf { it.isNotBlank() },
                    alertWindowOpen = !runningNow,
                )
            }
            observeMachine(roomId, machineId)
            if (!includedLaundry) requestPrice(machineId) else _state.update { it.copy(priceChecked = true) }
        }
    }

    private fun observeMachine(roomId: String, machineId: String) {
        socket.connect(roomId)
        viewModelScope.launch {
            socket.events.collect { event ->
                when (event) {
                    is MachineStatusSocket.Event.Connected -> _state.update { it.copy(live = true) }
                    is MachineStatusSocket.Event.Disconnected -> _state.update { it.copy(live = false) }
                    is MachineStatusSocket.Event.ConnectionError -> _state.update { it.copy(live = false) }
                    is MachineStatusSocket.Event.MachineStatus -> {
                        if (event.update.machineId != machineId) return@collect
                        applyLiveFrame(event.update)
                    }
                    is MachineStatusSocket.Event.CommandResponse -> {
                        val status = event.status.uppercase()
                        if (status != "COMPLETED" && status != "FAILED") return@collect
                        val id = event.commandId?.takeIf { it.isNotBlank() } ?: return@collect
                        if (recentCommandStatuses.size > 32) recentCommandStatuses.clear()
                        recentCommandStatuses[id] = status
                        commandWaiters.remove(id)?.complete(status)
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun applyLiveFrame(update: MachineStatusSocket.MachineStatusUpdate) {
        _state.update { st ->
            val synced = if (!st.userSelectedOptions) {
                selectionFor(
                    cycleId = update.selectedCycleId,
                    cycleName = update.selectedCycleName,
                    cycles = st.cycles,
                    families = st.families,
                )
            } else {
                null
            }
            val syncedModifier = if (!st.userSelectedOptions) {
                update.selectedModifierId?.takeIf { id -> st.cycles?.modifiers?.any { it.id == id } == true }
            } else {
                null
            }
            st.copy(
                statusId = update.statusId ?: st.statusId,
                remainingSeconds = update.remainingSeconds,
                canTopOff = update.canTopOff,
                topOffCents = update.topOffVend.takeIf { it > 0 } ?: st.topOffCents,
                topOffSeconds = update.topOffTime.takeIf { it > 0 } ?: st.topOffSeconds,
                topOffDisabled = update.topOffDisabled,
                priceCents = if (update.remainingVend > 0) update.remainingVend else st.priceCents,
                priceChecked = st.priceChecked || update.remainingVend > 0,
                selectedFamilyKey = synced?.first ?: st.selectedFamilyKey,
                selectedTemperature = synced?.second ?: st.selectedTemperature,
                selectedModifierId = syncedModifier ?: st.selectedModifierId,
                machine = st.machine?.copy(
                    statusId = update.statusId ?: st.machine.statusId,
                    remainingSeconds = update.remainingSeconds,
                    canTopOff = update.canTopOff,
                    topOffVend = update.topOffVend.takeIf { it > 0 } ?: st.machine.topOffVend,
                    topOffTime = update.topOffTime.takeIf { it > 0 } ?: st.machine.topOffTime,
                    topOffFullyDisabled = update.topOffDisabled,
                    selectedCycleId = update.selectedCycleId ?: st.machine.selectedCycleId,
                    selectedCycleKey = update.selectedCycleName ?: st.machine.selectedCycleKey,
                    selectedModifierId = update.selectedModifierId ?: st.machine.selectedModifierId,
                    selectedModifierName = update.selectedModifierName ?: st.machine.selectedModifierName,
                ),
            )
        }
    }

    fun requestPrice(machineId: String) {
        if (!pollInFlight.compareAndSet(false, true)) return
        viewModelScope.launch {
            _state.update { it.copy(requestingPrice = true) }
            when (val result = repository.price(machineId, "GET_PRICE", socket.currentConnectionToken)) {
                is ApiResult.Success -> {
                    // the price arrives with the next live frame; if nothing shows up in a few
                    // seconds treat the check as finished so the UI can offer a retry
                    delay(4_000)
                    _state.update { it.copy(requestingPrice = false, priceChecked = true) }
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(requestingPrice = false, priceChecked = true, error = result.message)
                }
            }
            pollInFlight.set(false)
        }
    }

    fun selectFamily(machineId: String, family: CycleFamily, temperature: String?) {
        val cycle = family.variants.firstOrNull { temperatureOf(it) == temperature } ?: family.variants.firstOrNull()
        _state.update {
            it.copy(
                selectedFamilyKey = family.key,
                selectedTemperature = cycle?.let { c -> temperatureOf(c) },
                userSelectedOptions = true,
            )
        }
        queueSelection(machineId)
    }

    fun selectTemperature(machineId: String, temperature: String) {
        val family = _state.value.families.firstOrNull { it.key == _state.value.selectedFamilyKey } ?: return
        selectFamily(machineId, family, temperature)
    }

    fun selectModifier(machineId: String, modifier: CycleModifier) {
        if (_state.value.selectedModifierId == modifier.id) return
        _state.update { it.copy(selectedModifierId = modifier.id, userSelectedOptions = true) }
        queueSelection(machineId)
    }

    private fun queueSelection(machineId: String) {
        selectionJob?.cancel()
        selectionSeq += 1
        val seq = selectionSeq
        selectionJob = viewModelScope.launch {
            _state.update { it.copy(selectionSaving = true) }
            delay(250)
            val cycle = _state.value.selectedCycle ?: run {
                _state.update { it.copy(selectionSaving = false) }
                return@launch
            }
            val modifier = _state.value.selectedModifierId
            when (
                val result = repository.setCycles(
                    machineId = machineId,
                    cycle = cycle.id,
                    modifier = modifier,
                    connectionToken = socket.currentConnectionToken,
                )
            ) {
                is ApiResult.Failure -> {
                    if (seq == selectionSeq) {
                        _state.update {
                            it.copy(
                                selectionSaving = false,
                                error = "Couldn't save those cycle options. ${result.message}",
                            )
                        }
                        resyncSelectionFromMachine()
                    }
                }
                is ApiResult.Success -> {
                    val status = awaitCommand(result.data.id, 12_000)
                    if (seq == selectionSeq) {
                        _state.update { it.copy(selectionSaving = false) }
                        if (status == "FAILED") {
                            _state.update { it.copy(error = "The machine didn't accept those cycle options.") }
                            resyncSelectionFromMachine()
                        } else {
                            // The cycle can change the vend price; keep the displayed amount honest.
                            requestPrice(machineId)
                        }
                    }
                }
            }
        }
    }

    /** Puts the picker back on the machine's last accepted selection after a rejection. */
    private fun resyncSelectionFromMachine() {
        _state.update { st ->
            val machine = st.machine ?: return@update st
            val synced = selectionFor(
                cycleId = machine.selectedCycleId,
                cycleName = machine.selectedCycleKey,
                cycles = st.cycles,
                families = st.families,
            ) ?: return@update st
            st.copy(
                selectedFamilyKey = synced.first,
                selectedTemperature = synced.second,
                selectedModifierId = machine.selectedModifierId ?: st.selectedModifierId,
                userSelectedOptions = false,
            )
        }
    }

    private suspend fun awaitCommand(commandId: String?, timeoutMs: Long): String? {
        val id = commandId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        recentCommandStatuses[id]?.let { return it }
        if (!socket.isConnected) return null
        val waiter = CompletableDeferred<String>()
        commandWaiters[id] = waiter
        return try {
            withTimeoutOrNull(timeoutMs) { waiter.await() }
        } finally {
            commandWaiters.remove(id)
        }
    }

    private suspend fun fetchMachine(roomId: String, machineId: String): Machine? =
        (repository.roomMachineByNumber(roomId, null, machineId) as? ApiResult.Success)?.data

    private fun applySnapshot(fresh: Machine) {
        _state.update {
            val merged = fresh.copy(isMyMachine = fresh.isMyMachine || it.machine?.isMyMachine == true)
            val maxUnits = MachineViewModel.maxDryerUnits(
                baseSeconds = merged.remainingSeconds,
                topOffSeconds = merged.topOffTime.takeIf { value -> value > 0 } ?: it.topOffSeconds,
            )
            it.copy(
                machine = merged,
                statusId = merged.statusId ?: it.statusId,
                remainingSeconds = if (merged.isRunning) merged.remainingSeconds else it.remainingSeconds,
                priceCents = if (merged.remainingVend > 0) merged.remainingVend else it.priceCents,
                canTopOff = merged.canTopOff,
                topOffCents = merged.topOffVend.takeIf { value -> value > 0 } ?: it.topOffCents,
                topOffSeconds = merged.topOffTime.takeIf { value -> value > 0 } ?: it.topOffSeconds,
                topOffDisabled = merged.topOffFullyDisabled,
                priceChecked = it.priceChecked || merged.remainingVend > 0,
                dryerUnits = it.dryerUnits.coerceIn(0, maxUnits),
                dryerMaxUnits = maxUnits,
            )
        }
    }

    /**
     * Official start sequence (MachineFragment): setCycleSelections, then getPrice, then
     * remoteVend. Each step waits for its COMMAND_RESPONSE before the next one starts, and the
     * price is compared with the one the user saw so a stale quote can never be charged.
     */
    fun startVend(machineId: String, roomId: String) {
        val initial = _state.value
        if (initial.starting) return
        val subAccount = initial.user?.accounts?.firstOrNull()?.subAccounts?.firstOrNull()
        if (subAccount == null) {
            _state.update { it.copy(dialog = MachineDialog.Failed("No account balance found for this location.")) }
            return
        }
        if (initial.isIncludedLaundry) return
        if (initial.machine?.isActive == false) {
            _state.update { it.copy(dialog = MachineDialog.Failed("This machine isn't available right now.")) }
            return
        }
        if (initial.isRunning) {
            _state.update { it.copy(dialog = MachineDialog.Failed("This machine is already running.")) }
            return
        }
        if (initial.effectivePriceCents <= 0) {
            _state.update { it.copy(dialog = MachineDialog.NoAmountToCharge) }
            return
        }
        if (initial.availableCents < initial.effectivePriceCents) {
            _state.update { it.copy(dialog = MachineDialog.NotEnoughFunds("balance")) }
            return
        }

        val displayedAmount = initial.effectivePriceCents
        // Claim the flow synchronously so a double tap cannot start two vends.
        _state.update { it.copy(startPhase = StartPhase.Selecting, vendRunning = true, dialog = null) }
        viewModelScope.launch {
            try {
                // The vend relationship is the user-accounts id, which is not the sub-account id.
                var userAccountId = _state.value.userAccountId ?: subAccount.userAccountId
                if (userAccountId.isNullOrBlank()) {
                    val acc = _state.value.user?.accounts?.firstOrNull()
                    userAccountId = acc?.let {
                        (repository.balance(it.id, it.subAccounts.firstOrNull()?.geoBoundaryId) as? ApiResult.Success)
                            ?.data?.userAccountId
                    }
                }
                if (userAccountId.isNullOrBlank()) {
                    _state.update {
                        it.copy(
                            startPhase = StartPhase.Idle,
                            vendRunning = false,
                            dialog = MachineDialog.Failed(
                                "We couldn't find the laundry account linked to this balance. Refresh your account and try again.",
                            ),
                        )
                    }
                    return@launch
                }

                // 1. Apply the visible cycle and soil level so the vendor uses the options on screen.
                if (_state.value.cyclesSupported && _state.value.selectedCycle != null) {
                    _state.update { it.copy(startPhase = StartPhase.Selecting, vendRunning = true) }
                    if (!applySelectionNow(machineId)) {
                        _state.update { it.copy(startPhase = StartPhase.Idle, vendRunning = false) }
                        return@launch
                    }
                }

                // 2. Fresh quote. The command response matters, the status snapshot carries the amount.
                _state.update { it.copy(startPhase = StartPhase.Pricing) }
                val freshBase = refreshPrice(machineId, roomId)
                if (freshBase == null) {
                    _state.update {
                        it.copy(
                            startPhase = StartPhase.Idle,
                            vendRunning = false,
                            dialog = MachineDialog.Failed("We couldn't confirm the current price. Please try again."),
                        )
                    }
                    return@launch
                }
                if (freshBase <= 0) {
                    _state.update {
                        it.copy(startPhase = StartPhase.Idle, vendRunning = false, dialog = MachineDialog.NoAmountToCharge)
                    }
                    return@launch
                }

                val fresh = _state.value
                if (fresh.machine?.isAvailable == false) {
                    _state.update {
                        it.copy(
                            startPhase = StartPhase.Idle,
                            vendRunning = false,
                            dialog = MachineDialog.Failed("This machine isn't available anymore."),
                        )
                    }
                    return@launch
                }
                val freshAmount = fresh.effectivePriceCents
                if (freshAmount != displayedAmount) {
                    // Never charge an amount the user has not confirmed.
                    _state.update {
                        it.copy(
                            startPhase = StartPhase.Idle,
                            vendRunning = false,
                            dialog = MachineDialog.PriceChanged(freshAmount),
                        )
                    }
                    return@launch
                }
                if (fresh.availableCents < freshAmount) {
                    _state.update {
                        it.copy(startPhase = StartPhase.Idle, vendRunning = false, dialog = MachineDialog.NotEnoughFunds("balance"))
                    }
                    return@launch
                }

                // 3. Vend and wait for the payment command to resolve.
                _state.update { it.copy(startPhase = StartPhase.Vending) }
                when (
                    val result = repository.remoteVend(
                        machineId = machineId,
                        userAccountId = userAccountId,
                        vendAmount = freshAmount,
                        connectionToken = socket.currentConnectionToken,
                        useRewardsCredit = fresh.useRewards,
                    )
                ) {
                    is ApiResult.Failure -> _state.update {
                        it.copy(
                            startPhase = StartPhase.Idle,
                            vendRunning = false,
                            dialog = MachineDialog.Failed(result.message, result.httpCode),
                        )
                    }
                    is ApiResult.Success -> {
                        _state.update { it.copy(startPhase = StartPhase.Confirming) }
                        when (awaitVendOutcome(result.data.id, roomId, machineId)) {
                            true -> {
                                _state.update {
                                    it.copy(
                                        startPhase = StartPhase.Idle,
                                        vendRunning = false,
                                        dialog = MachineDialog.Success(it.machine?.displayName.orEmpty()),
                                    )
                                }
                                claimMachine(machineId)
                            }
                            false -> _state.update {
                                it.copy(
                                    startPhase = StartPhase.Idle,
                                    vendRunning = false,
                                    dialog = MachineDialog.Failed(
                                        "The service rejected the start. Your balance was not charged. " +
                                            "Check the machine and try again.",
                                    ),
                                )
                            }
                            null -> _state.update {
                                it.copy(startPhase = StartPhase.Idle, vendRunning = false, dialog = MachineDialog.NoConfirmation)
                            }
                        }
                    }
                }
            } finally {
                _state.update { if (it.starting) it.copy(startPhase = StartPhase.Idle, vendRunning = false) else it }
            }
        }
    }

    /** Sends the visible selection and waits for SET_SELECTION when the socket is live. */
    private suspend fun applySelectionNow(machineId: String): Boolean {
        val state = _state.value
        val cycle = state.selectedCycle ?: return true
        selectionJob?.cancel()
        selectionSeq += 1
        _state.update { it.copy(selectionSaving = true) }
        val result = repository.setCycles(
            machineId = machineId,
            cycle = cycle.id,
            modifier = state.selectedModifierId,
            connectionToken = socket.currentConnectionToken,
        )
        val ok = when (result) {
            is ApiResult.Failure -> {
                _state.update { it.copy(error = "Couldn't apply the cycle options. ${result.message}") }
                false
            }
            is ApiResult.Success -> awaitCommand(result.data.id, 15_000) != "FAILED"
        }
        _state.update { it.copy(selectionSaving = false) }
        if (!ok) {
            _state.update { it.copy(error = "The machine didn't accept those cycle options.") }
            resyncSelectionFromMachine()
        }
        return ok
    }

    /**
     * Requests a new price and returns the machine's post-request base amount. Polls the
     * machine resource because the amount is written to its currentStatus, not the REST reply.
     */
    private suspend fun refreshPrice(machineId: String, roomId: String): Int? {
        when (val result = repository.price(machineId, "GET_PRICE", socket.currentConnectionToken)) {
            is ApiResult.Failure -> {
                _state.update { it.copy(error = result.message, priceChecked = true) }
                return null
            }
            is ApiResult.Success -> awaitCommand(result.data.id, 15_000)
        }
        var last: Machine? = null
        for (attempt in 0 until 6) {
            val fresh = fetchMachine(roomId, machineId)
            if (fresh != null) {
                last = fresh
                applySnapshot(fresh)
                if (fresh.remainingVend > 0) return fresh.remainingVend
            }
            delay(900)
        }
        return last?.remainingVend
    }

    /**
     * A completed payment command means the vend was accepted (the official client treats it as
     * such). If the socket is down, poll for the machine turning busy instead.
     */
    private suspend fun awaitVendOutcome(commandId: String?, roomId: String, machineId: String): Boolean? {
        when (awaitCommand(commandId, 30_000)?.uppercase()) {
            "COMPLETED" -> return true
            "FAILED" -> return false
            else -> Unit
        }
        for (attempt in 0 until 10) {
            delay(2_000)
            val fresh = fetchMachine(roomId, machineId) ?: continue
            applySnapshot(fresh)
            if (fresh.isRunning) return true
        }
        return null
    }

    fun setDryerUnits(units: Int) {
        _state.update { it.copy(dryerUnits = units.coerceIn(0, it.dryerMaxUnits)) }
    }

    fun toggleRewards(enabled: Boolean) {
        _state.update { it.copy(useRewards = enabled) }
    }

    fun setEmailAlert(enabled: Boolean, machineId: String, roomId: String) {
        _state.update { it.copy(emailAlertEnabled = enabled) }
        if (!enabled) return
        viewModelScope.launch {
            val email = _state.value.user?.email
            if (email.isNullOrBlank()) {
                _state.update { it.copy(emailAlertEnabled = false, error = "Add an email address to your profile first") }
                return@launch
            }
            when (val result = repository.addWashAlert(machineId, roomId, email = email, phone = null)) {
                is ApiResult.Success -> washAlertWatcher.refresh()
                is ApiResult.Failure -> _state.update { it.copy(emailAlertEnabled = false, error = result.message) }
            }
        }
    }

    fun setSmsAlert(enabled: Boolean, machineId: String, roomId: String) {
        _state.update { it.copy(smsAlertEnabled = enabled) }
        if (!enabled) return
        viewModelScope.launch {
            val phone = _state.value.user?.phoneNumbers?.firstOrNull()
            if (phone == null) {
                _state.update { it.copy(smsAlertEnabled = false, error = "Add a phone number to your profile first") }
                return@launch
            }
            when (val result = repository.addWashAlert(machineId, roomId, email = null, phone = phone.compact)) {
                is ApiResult.Success -> washAlertWatcher.refresh()
                is ApiResult.Failure -> _state.update { it.copy(smsAlertEnabled = false, error = result.message) }
            }
        }
    }

    /** Validates, then shows the confirmation dialog before any money moves. */
    fun requestStart(machineId: String) {
        val state = _state.value
        val subAccount = state.user?.accounts?.firstOrNull()?.subAccounts?.firstOrNull()
        val machine = state.machine ?: return
        val amount = state.effectivePriceCents

        when {
            state.starting -> Unit
            subAccount == null -> {
                _state.update { it.copy(dialog = MachineDialog.Failed("No account balance found for this location.")) }
            }
            machine.isActive.not() -> {
                _state.update { it.copy(dialog = MachineDialog.Failed("This machine isn't available right now.")) }
            }
            state.isRunning -> {
                _state.update { it.copy(dialog = MachineDialog.Failed("This machine is already running.")) }
            }
            amount <= 0 -> {
                _state.update { it.copy(dialog = MachineDialog.NoAmountToCharge) }
            }
            state.availableCents < amount -> {
                _state.update { it.copy(dialog = MachineDialog.NotEnoughFunds("balance")) }
            }
            else -> _state.update { it.copy(dialog = MachineDialog.ConfirmStart(amount)) }
        }
    }

    /** Opens the add-time picker (official opens a dryer payment screen here). */
    fun requestTopOff() {
        if (!_state.value.canAddDryingTime) {
            _state.update { it.copy(dialog = MachineDialog.Failed("This machine can't take more drying time right now.")) }
            return
        }
        _state.update { it.copy(dialog = MachineDialog.ConfirmTopOff(units = 1)) }
    }

    fun setTopOffUnits(units: Int) {
        _state.update { state ->
            state.copy(dialog = MachineDialog.ConfirmTopOff(units = units.coerceIn(1, state.topOffMaxUnits)))
        }
    }

    fun topOff(machineId: String) {
        val state = _state.value
        val subAccount = state.user?.accounts?.firstOrNull()?.subAccounts?.firstOrNull() ?: return
        val units = (state.dialog as? MachineDialog.ConfirmTopOff)?.units ?: 1
        val amount = state.topOffCents * units
        if (amount <= 0) {
            _state.update { it.copy(dialog = MachineDialog.NoAmountToCharge) }
            return
        }
        val userAccountId = state.userAccountId ?: subAccount.userAccountId
        if (userAccountId.isNullOrBlank()) {
            _state.update {
                it.copy(dialog = MachineDialog.Failed("We couldn't find the laundry account linked to this balance."))
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(topOffRunning = true) }
            when (
                val result = repository.remoteTopoff(
                    machineId = machineId,
                    userAccountId = userAccountId,
                    vendAmount = amount,
                    connectionToken = socket.currentConnectionToken,
                )
            ) {
                is ApiResult.Failure -> _state.update {
                    it.copy(topOffRunning = false, dialog = MachineDialog.Failed(result.message, result.httpCode))
                }
                is ApiResult.Success -> {
                    when (awaitCommand(result.data.id, 30_000)?.uppercase()) {
                        "COMPLETED" -> _state.update {
                            it.copy(
                                topOffRunning = false,
                                dialog = MachineDialog.Success(it.machine?.displayName.orEmpty()),
                            )
                        }
                        "FAILED" -> _state.update {
                            it.copy(
                                topOffRunning = false,
                                dialog = MachineDialog.Failed("The service rejected the extra time. Your balance was not charged."),
                            )
                        }
                        else -> _state.update {
                            it.copy(topOffRunning = false, dialog = MachineDialog.NoConfirmation)
                        }
                    }
                }
            }
        }
    }

    private fun claimMachine(machineId: String) {
        val userId = _state.value.user?.id ?: return
        viewModelScope.launch { repository.assignMachine(userId, machineId) }
    }

    fun dismissDialog() = _state.update { it.copy(dialog = null) }
    fun consumeError() = _state.update { it.copy(error = null) }

    fun stopLive(roomId: String) {
        socket.unsubscribeRoom(roomId)
    }
}

// ------------------------------------------------------------------ helpers

/** "NORMAL_HOT" → "Normal Hot", "DELICATE_30C" → "Delicate 30C". */
fun prettyCycleKey(key: String): String =
    key.split('_')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            when {
                word.length <= 3 && word.any(Char::isDigit) -> word
                else -> word.lowercase().replaceFirstChar { it.uppercase() }
            }
        }

private fun temperatureOf(cycle: Cycle): String? {
    val key = cycle.description?.takeIf { it.isNotBlank() } ?: cycle.name
    val suffix = key.substringAfterLast('_', "")
    // Only real temperature suffixes split a cycle into a family. DELICATES_BULKY and
    // NO_HEAT are complete cycle names, not a family plus a temperature.
    return suffix.uppercase().takeIf { it in KNOWN_TEMPERATURES }
}

private val KNOWN_TEMPERATURES = setOf("HOT", "WARM", "COLD", "30C", "40C", "50C", "60C", "90C", "120C")

fun cyclesToFamilies(cycles: List<Cycle>): List<CycleFamily> =
    cycles
        .filter { it.enabled }
        .groupBy { cycle ->
            val key = cycle.description?.takeIf { it.isNotBlank() } ?: cycle.name
            if (temperatureOf(cycle) != null) key.substringBeforeLast('_') else key
        }
        .map { (family, variants) -> CycleFamily(key = family, variants = variants) }

/** Official display names for the cycle keys shipped in the resources (strings.xml). */
fun cycleLabel(key: String): String = when (key.uppercase()) {
    "DELICATES_BULKY" -> "Delicates/Bulky"
    "PERMANENT_PRESS_50C" -> "Perm Press 50C"
    "PERMANENT_PRESS_60C" -> "Perm Press 60C"
    "PERMANENT_PRESS_90C" -> "Perm Press 90C"
    "NO_HEAT" -> "No Heat"
    else -> prettyCycleKey(key)
}

fun cycleLabel(cycle: Cycle): String =
    cycleLabel(cycle.description?.takeIf { it.isNotBlank() } ?: cycle.name)

/** Soil levels are modifiers whose desc is a resource key such as LIGHT or HEAVY_SOIL. */
fun modifierLabel(description: String?): String =
    when (description?.uppercase()?.removeSuffix("_SOIL")) {
        "LIGHT" -> "Light"
        "MEDIUM" -> "Medium"
        "HEAVY" -> "Heavy"
        else -> description?.let { prettyCycleKey(it) } ?: "Default"
    }

fun modifierDescription(description: String?): String? =
    when (description?.uppercase()?.removeSuffix("_SOIL")) {
        "LIGHT" -> "A gentle cycle for lightly-soiled items"
        "MEDIUM" -> "Cycle enhanced to better wash medium-soiled items"
        "HEAVY" -> "Heavy-duty wash penetrates tough stains on extra-dirty garments"
        else -> null
    }

/**
 * Resolves a cycle id or name from the machine status to a family and temperature. The live
 * frame only carries `{id, name}`, while the catalog carries ids and desc keys, so both are
 * matched and the name is also used as a NORMAL_HOT style fallback.
 */
private fun selectionFor(
    cycleId: String?,
    cycleName: String?,
    cycles: MachineCycles?,
    families: List<CycleFamily>,
): Pair<String, String?>? {
    if (cycles == null || families.isEmpty()) return null
    val cycle = cycles.cycles.firstOrNull { candidate ->
        (!cycleId.isNullOrBlank() && candidate.id == cycleId) ||
            (!cycleName.isNullOrBlank() && (candidate.name == cycleName || candidate.description == cycleName))
    }
    if (cycle != null) {
        val family = families.firstOrNull { family -> family.variants.any { it.id == cycle.id } }
        if (family != null) return family.key to temperatureOf(cycle)
    }
    if (cycleName.isNullOrBlank()) return null
    val family = families.firstOrNull { family -> cycleName.startsWith(family.key) } ?: return null
    val temperature = cycleName.substringAfterLast('_', "")
        .takeIf { it.isNotBlank() && it in family.temperatures }
    return family.key to (temperature ?: family.temperatures.firstOrNull())
}

private fun selectionFor(
    machine: Machine?,
    cycles: MachineCycles?,
    families: List<CycleFamily>,
): Pair<String, String?>? =
    selectionFor(machine?.selectedCycleId, machine?.selectedCycleKey, cycles, families)

// ----------------------------------------------------------------------- UI

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MachineScreen(
    roomId: String,
    machineId: String,
    onBack: () -> Unit,
    onAddCredit: (accountId: String, geoBoundaryId: String) -> Unit,
    onPaymentMethods: (accountId: String) -> Unit,
    viewModel: MachineViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHost.current

    LaunchedEffect(roomId, machineId) { viewModel.load(roomId, machineId) }
    DisposableEffect(roomId) {
        onDispose { viewModel.stopLive(roomId) }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = {
                    val machine = state.machine
                    Text(
                        machine?.number?.takeIf { it.isNotBlank() }?.let { "#$it" }
                            ?: machine?.displayName?.takeIf { it.isNotBlank() }
                            ?: "Machine",
                    )
                },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        androidx.compose.material3.Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) { LoadingState() }
            return@Scaffold
        }

        val machine = state.machine
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MachineHeroCard(state, machine)

            if (state.isIncludedLaundry) {
                IncludedLaundryCard()
            } else {
                PriceAndStartCard(
                    state = state,
                    onStart = { viewModel.requestStart(machineId) },
                    onTopOff = { viewModel.requestTopOff() },
                    onRetryPrice = { viewModel.requestPrice(machineId) },
                )
            }

            if (state.isDryerMachine && !state.isRunning && !state.isIncludedLaundry && state.topOffSeconds > 0) {
                DryerTimeCard(state = state, onUnits = { viewModel.setDryerUnits(it) })
            }

            if (state.families.isNotEmpty() && state.cyclesSupported) {
                CyclePickerCard(
                    state = state,
                    onFamily = { family ->
                        viewModel.selectFamily(machineId, family, state.selectedTemperature ?: family.temperatures.firstOrNull())
                    },
                    onTemperature = { temperature ->
                        viewModel.selectTemperature(machineId, temperature)
                    },
                    onModifier = { modifier ->
                        viewModel.selectModifier(machineId, modifier)
                    },
                )
            }

            if ((state.balance?.promotionalCredit ?: 0.0) > 0.0) {
                RewardsCard(state = state, onToggle = { viewModel.toggleRewards(it) })
            }

            WashAlertCard(
                state = state,
                onEmailToggle = { viewModel.setEmailAlert(it, machineId, roomId) },
                onSmsToggle = { viewModel.setSmsAlert(it, machineId, roomId) },
            )

            state.user?.accounts?.firstOrNull()?.let { account ->
                AccountCard(
                    balanceCents = state.balance?.amount?.toInt() ?: 0,
                    onAddCredit = { onAddCredit(account.id, account.subAccounts.firstOrNull()?.geoBoundaryId.orEmpty()) },
                    onPaymentMethods = { onPaymentMethods(account.id) },
                )
            }
        }
    }

    MachineDialogs(
        state = state,
        onDismiss = { viewModel.dismissDialog() },
        onAddCredit = {
            state.user?.accounts?.firstOrNull()?.let { account ->
                onAddCredit(account.id, account.subAccounts.firstOrNull()?.geoBoundaryId.orEmpty())
            }
        },
        onConfirmStart = {
            viewModel.dismissDialog()
            viewModel.startVend(machineId, roomId)
        },
        onPriceChanged = {
            viewModel.dismissDialog()
            viewModel.requestStart(machineId)
        },
        onTopOffUnits = { viewModel.setTopOffUnits(it) },
        onConfirmTopOff = {
            viewModel.dismissDialog()
            viewModel.topOff(machineId)
        },
        onBack = onBack,
    )
}

@Composable
private fun IncludedLaundryCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Included laundry",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Laundry is included at this location, so no payment is needed. Press start on the machine to begin your cycle.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun MachineHeroCard(state: MachineUiState, machine: Machine?) {
    val running = state.isRunning
    val container by animateColorAsState(
        targetValue = when {
            machine?.isActive == false -> MaterialTheme.colorScheme.surfaceVariant
            running -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.secondaryContainer
        },
        label = "heroContainer",
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp)) {
                val ringColor = MaterialTheme.colorScheme.primary
                val trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
                val progress = if (running) progressFor(state.remainingSeconds) else 0f
                Canvas(modifier = Modifier.size(96.dp)) {
                    val stroke = 7.dp.toPx()
                    val inset = stroke / 2
                    drawArc(
                        color = trackColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    if (progress > 0f) {
                        drawArc(
                            color = ringColor,
                            startAngle = -90f,
                            sweepAngle = 360f * progress.coerceIn(0f, 1f),
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = Size(size.width - stroke, size.height - stroke),
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                    }
                }
                com.drakeerv.squeen.ui.components.MachineAnimation(
                    isDryer = machine?.isDryer == true,
                    running = running,
                    modifier = Modifier.size(68.dp),
                )
            }
            Spacer(Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    when {
                        machine == null -> "Machine"
                        !machine.isActive -> "Unavailable"
                        running -> "${(state.remainingSeconds + 59) / 60} min left"
                        else -> com.drakeerv.squeen.ui.machineStatusLabel(machine)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        if (running) append(if (machine?.isDryer == true) "Tumbling dry" else "Washing")
                        else append(if (machine?.isDryer == true) "Dryer" else "Washer")
                        machine?.number?.takeIf { it.isNotBlank() }?.let { append(" · #$it") }
                        state.roomName.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.live) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Live status",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

private fun progressFor(remainingSeconds: Int): Float {
    // Washer/dryer cycles are ~35 minutes; the ring drains as the cycle completes.
    val total = 35 * 60f
    return (remainingSeconds / total).coerceIn(0f, 1f)
}

@Composable
private fun PriceAndStartCard(
    state: MachineUiState,
    onStart: () -> Unit,
    onTopOff: () -> Unit,
    onRetryPrice: () -> Unit = {},
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (state.isRunning) "Cycle price" else "Price",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    when {
                        state.effectivePriceCents > 0 -> Text(
                            Money.fromMinorUnits(state.effectivePriceCents),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        !state.priceChecked -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Checking price…", style = MaterialTheme.typography.bodyMedium)
                        }
                        else -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Price not available",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = onRetryPrice) { Text("Retry") }
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Your balance", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        Money.fromMinorUnits(state.balance?.amount?.toInt() ?: 0),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onStart,
                enabled = !state.starting &&
                    state.machine?.isActive == true &&
                    !state.isRunning &&
                    state.effectivePriceCents > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (state.starting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                val selectedCycle = state.selectedCycle
                Text(
                    when (state.startPhase) {
                        StartPhase.Selecting -> "Saving cycle…"
                        StartPhase.Pricing -> "Checking price…"
                        StartPhase.Vending, StartPhase.Confirming -> "Starting…"
                        StartPhase.Idle ->
                            if (selectedCycle != null) "Start ${cycleLabel(selectedCycle)}"
                            else "Start machine"
                    },
                )
            }
            if (state.selectionSaving && !state.starting) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Saving cycle options…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.canAddDryingTime) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onTopOff,
                    enabled = !state.topOffRunning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text(if (state.topOffRunning) "Adding time…" else "Add more drying time")
                }
            }
        }
    }
}

/**
 * Dryer time selector, the official client's slider: the base cycle is extended by whole
 * units of `topOffTime`, each unit adding `topOffVend` to the price.
 */
@Composable
private fun DryerTimeCard(state: MachineUiState, onUnits: (Int) -> Unit) {
    val baseSeconds = state.machine?.remainingSeconds ?: 0
    val maxUnits = state.dryerMaxUnits

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Drying time", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Base ${MachineViewModel.formatCycleTime(baseSeconds)} · " +
                    "+${MachineViewModel.formatCycleTime(state.topOffSeconds)} per step " +
                    "(${Money.fromMinorUnits(state.topOffCents)})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.FilledTonalIconButton(
                    onClick = { onUnits(state.dryerUnits - 1) },
                    enabled = state.dryerUnits > 0,
                ) {
                    androidx.compose.material3.Icon(Icons.Filled.Remove, contentDescription = "Less time")
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        MachineViewModel.formatCycleTime(state.effectiveTimeSeconds),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        Money.fromMinorUnits(state.effectivePriceCents),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                androidx.compose.material3.FilledTonalIconButton(
                    onClick = { onUnits(state.dryerUnits + 1) },
                    enabled = state.dryerUnits < maxUnits,
                ) {
                    androidx.compose.material3.Icon(Icons.Filled.Add, contentDescription = "More time")
                }
            }

            if (maxUnits > 0) {
                androidx.compose.material3.Slider(
                    value = state.dryerUnits.toFloat(),
                    onValueChange = { onUnits(it.roundToInt()) },
                    valueRange = 0f..maxUnits.toFloat(),
                    steps = (maxUnits - 1).coerceAtLeast(0),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        MachineViewModel.formatCycleTime(baseSeconds),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        MachineViewModel.formatCycleTime(baseSeconds + state.topOffSeconds * maxUnits),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CyclePickerCard(
    state: MachineUiState,
    onFamily: (CycleFamily) -> Unit,
    onTemperature: (String) -> Unit,
    onModifier: (CycleModifier) -> Unit,
) {
    val isDryer = state.isDryerMachine
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                if (isDryer) "Heat level" else "Choose your cycle",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (isDryer) "Pick how warm the dryer runs." else "Tap a fabric type, then pick a temperature and soil level.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))

            if (isDryer) {
                // Dryer cycles are heat levels (HIGH / MEDIUM / LOW / NO_HEAT), render them
                // as a single ordered row of chips.
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val ordered = state.families.sortedBy { heatRank(it.key) }
                    ordered.forEach { family ->
                        androidx.compose.material3.FilterChip(
                            selected = family.key == state.selectedFamilyKey,
                            onClick = { onFamily(family) },
                            label = { Text(family.label) },
                        )
                    }
                }
                return@Column
            }

            state.families.chunked(2).forEach { rowFamilies ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowFamilies.forEach { family ->
                        val selected = family.key == state.selectedFamilyKey
                        val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .height(88.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                )
                                .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
                                .clickable { onFamily(family) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                family.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                            )
                            if (family.temperatures.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    family.temperatures.joinToString(" · ") { temperature -> cycleLabel(temperature) },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                    if (rowFamilies.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
            }

            val selectedFamily = state.selectedFamily
            if (selectedFamily != null && selectedFamily.temperatures.size > 1) {
                Text("Temperature", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    selectedFamily.temperatures.forEach { temperature ->
                        FilterChip(
                            selected = temperature == state.selectedTemperature,
                            onClick = { onTemperature(temperature) },
                            label = { Text(cycleLabel(temperature)) },
                        )
                    }
                }
            }

            val modifiers = state.cycles?.modifiers.orEmpty()
            if (modifiers.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Soil level", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(2.dp))
                Text(
                    "How dirty is the load?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    modifiers.forEach { modifier ->
                        FilterChip(
                            selected = modifier.id == state.selectedModifierId,
                            onClick = { onModifier(modifier) },
                            label = { Text(modifierLabel(modifier.description)) },
                        )
                    }
                }
                modifierDescription(state.selectedModifier?.description)?.let { description ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RewardsCard(state: MachineUiState, onToggle: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Use rewards credit", style = MaterialTheme.typography.titleSmall)
                Text(
                    "You have ${Money.fromMinorUnits(state.balance?.promotionalCredit?.toInt() ?: 0)} in promotional credit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = state.useRewards, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun WashAlertCard(
    state: MachineUiState,
    onEmailToggle: (Boolean) -> Unit,
    onSmsToggle: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(
                    Icons.Filled.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Wash alerts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Get a reminder when this machine has about 3 minutes left. Delivered by the laundry service, no app needed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!state.alertWindowOpen) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "This cycle is nearly finished, so a new alert can't be set for it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(8.dp))
            // ---- email
            AlertRow(
                icon = Icons.Filled.Email,
                title = "Email",
                subtitle = state.user?.email?.takeIf { it.isNotBlank() }?.let { "Send to $it" }
                    ?: "Add an email address in My Profile",
                checked = state.emailAlertEnabled,
                enabled = state.alertWindowOpen && !state.user?.email.isNullOrBlank(),
                onToggle = onEmailToggle,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            // ---- sms
            AlertRow(
                icon = Icons.Filled.Sms,
                title = "Text message",
                subtitle = when {
                    !state.smsAvailable -> state.smsReason ?: "Text alerts unavailable"
                    else -> "Send to ${state.phoneDisplay.orEmpty()}"
                },
                checked = state.smsAlertEnabled,
                enabled = state.alertWindowOpen && state.smsAvailable,
                onToggle = onSmsToggle,
            )
        }
    }
}

@Composable
private fun AlertRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun AccountCard(balanceCents: Int, onAddCredit: () -> Unit, onPaymentMethods: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(
                    Icons.Filled.Payments,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Account", style = MaterialTheme.typography.titleSmall)
                    Text(
                        Money.fromMinorUnits(balanceCents),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onAddCredit) { Text("Add funds") }
                    TextButton(onClick = onPaymentMethods) { Text("Cards") }
                }
            }
        }
    }
}

@Composable
private fun MachineDialogs(
    state: MachineUiState,
    onDismiss: () -> Unit,
    onAddCredit: () -> Unit,
    onConfirmStart: () -> Unit,
    onPriceChanged: () -> Unit,
    onTopOffUnits: (Int) -> Unit,
    onConfirmTopOff: () -> Unit,
    onBack: () -> Unit,
) {
    when (val dialog = state.dialog) {
        null -> Unit
        is MachineDialog.Success -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Your machine is set!") },
            text = { Text("Press \u201cstart\u201d on the machine to begin your cycle.") },
            confirmButton = {
                TextButton(onClick = {
                    onDismiss()
                    onBack()
                }) { Text("Done") }
            },
        )
        is MachineDialog.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Something went wrong") },
            text = {
                Column {
                    Text(dialog.message)
                    dialog.httpCode?.let { code ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Service response: HTTP $code",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
        is MachineDialog.NoConfirmation -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("We didn't get confirmation") },
            text = { Text("We didn't get confirmation in time. Your payment may have already gone through, check the machine before trying again.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
        is MachineDialog.ConfirmStart -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Start this machine?") },
            text = {
                Column {
                    val machine = state.machine
                    Text("${machine?.displayName ?: "Machine"} \u00b7 ${state.roomName}".trim())
                    state.selectedCycle?.let { cycle ->
                        Text("Cycle: ${cycleLabel(cycle)}")
                    }
                    state.selectedModifier?.let { modifier ->
                        Text("Soil level: ${modifierLabel(modifier.description)}")
                    }
                    if (state.isDryerMachine && state.effectiveTimeSeconds > 0) {
                        Text("Time: ${MachineViewModel.formatCycleTime(state.effectiveTimeSeconds)}")
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Price: ${Money.fromMinorUnits(dialog.amountCents)}")
                    Text(
                        "From your balance: ${Money.fromMinorUnits(state.availableCents)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "The price is checked again before the machine starts.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = onConfirmStart) { Text("Continue") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
        is MachineDialog.PriceChanged -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("The price changed") },
            text = {
                Column {
                    Text("This machine now costs ${Money.fromMinorUnits(dialog.amountCents)}.")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "You have not been charged. Review the new price before starting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = onPriceChanged) { Text("Review") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
        is MachineDialog.ConfirmTopOff -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Add more drying time") },
            text = {
                Column {
                    val addedSeconds = state.topOffSeconds * dialog.units
                    val price = state.topOffCents * dialog.units
                    Text("Add ${MachineViewModel.formatCycleTime(addedSeconds)} for ${Money.fromMinorUnits(price)}")
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.Slider(
                        value = dialog.units.toFloat(),
                        onValueChange = { onTopOffUnits(it.roundToInt()) },
                        valueRange = 1f..state.topOffMaxUnits.toFloat(),
                        steps = (state.topOffMaxUnits - 2).coerceAtLeast(0),
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            MachineViewModel.formatCycleTime(state.topOffSeconds),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            MachineViewModel.formatCycleTime(state.topOffSeconds * state.topOffMaxUnits),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = onConfirmTopOff) { Text("Add time") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
        MachineDialog.NoAmountToCharge -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("No amount to charge") },
            text = {
                Text(
                    "This machine has no amount to charge right now. You haven't been charged. " +
                        "If you just paid, check the machine before paying again.",
                )
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
        is MachineDialog.NotEnoughFunds -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Not enough balance") },
            text = { Text("Not enough balance for this machine. Add funds and try again.") },
            confirmButton = {
                TextButton(onClick = {
                    onDismiss()
                    onAddCredit()
                }) { Text("Add funds") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
    }
}

/** Canonical heat ordering for dryer cycles. */
private fun heatRank(key: String): Int = when (key.uppercase()) {
    "HIGH" -> 0
    "MEDIUM" -> 1
    "LOW" -> 2
    "NO_HEAT", "NOHEAT" -> 3
    else -> 4
}

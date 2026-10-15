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
import com.drakeerv.squeen.data.model.User
import com.drakeerv.squeen.data.net.MachineStatusSocket
import com.drakeerv.squeen.data.notify.WashAlertWatcher
import com.drakeerv.squeen.ui.Money
import com.drakeerv.squeen.ui.components.AppTopBar
import com.drakeerv.squeen.ui.components.LoadingState
import com.drakeerv.squeen.ui.components.LocalSnackbarHost
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt
import java.util.concurrent.atomic.AtomicBoolean

/** A cycle "family" (Normal, Permanent Press, Delicate…) with its temperature variants. */
data class CycleFamily(
    val key: String,
    val variants: List<Cycle>,
) {
    val label: String get() = prettyCycleKey(key)
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
    val user: User? = null,
    val balance: BalanceInfo? = null,
    val priceCents: Int = 0,
    val requestingPrice: Boolean = false,
    val priceChecked: Boolean = false,
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

    val isRunning: Boolean
        get() = (statusId ?: machine?.statusId)?.uppercase() in com.drakeerv.squeen.data.model.Machine.BUSY_STATUSES

    val isDryerMachine: Boolean get() = machine?.isDryer == true

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
}

sealed interface MachineDialog {
    data class Success(val machineName: String) : MachineDialog
    data class Failed(val message: String) : MachineDialog
    data object NoConfirmation : MachineDialog
    data class NotEnoughFunds(val balanceName: String) : MachineDialog
    data object NoAmountToCharge : MachineDialog
}

class MachineViewModel(
    private val repository: SpeedQueenRepository,
    private val socket: MachineStatusSocket,
    private val washAlertWatcher: WashAlertWatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(MachineUiState())
    val state: StateFlow<MachineUiState> = _state.asStateFlow()

    private val pollInFlight = AtomicBoolean(false)

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
            val roomName = (repository.room(roomId) as? ApiResult.Success)?.data?.name.orEmpty()

            val user = (repository.getUser() as? ApiResult.Success)?.data
            val cycles = (repository.machineCycles(machineId) as? ApiResult.Success)?.data
            val families = cycles?.cycles?.let { cyclesToFamilies(it) } ?: emptyList()
            val currentKey = machine?.selectedCycleKey
            val defaultFamily = families.firstOrNull { family -> currentKey?.startsWith(family.key) == true }
                ?: families.firstOrNull()
            val defaultTemperature = currentKey
                ?.takeIf { key -> defaultFamily?.variants?.any { temperatureOf(it) == key.substringAfterLast('_') } == true }
                ?.substringAfterLast('_')
                ?: defaultFamily?.temperatures?.firstOrNull()

            val account = user?.accounts?.firstOrNull()
            val balance = account?.let { acc ->
                (repository.balance(acc.id, acc.subAccounts.firstOrNull()?.geoBoundaryId) as? ApiResult.Success)?.data
            }
            // Included (free) laundry organizations: the official app hides price/start entirely.
            val includedLaundry = (repository.room(roomId) as? ApiResult.Success)?.data
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
            val room = (repository.room(roomId) as? ApiResult.Success)?.data
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
                    user = user,
                    balance = balance,
                    remainingSeconds = if (machine?.isRunning == true) machine.remainingSeconds else 0,
                    statusId = machine?.statusId,
                    canTopOff = machine?.isDryer == true,
                    topOffCents = machine?.topOffVend ?: 0,
                    topOffSeconds = machine?.topOffTime ?: 0,
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
                        val update = event.update
                        _state.update {
                            it.copy(
                                statusId = update.statusId ?: it.statusId,
                                remainingSeconds = update.remainingSeconds,
                                canTopOff = update.canTopOff,
                                topOffCents = update.topOffVend,
                                topOffSeconds = update.topOffTime,
                                priceCents = if (update.remainingVend > 0) update.remainingVend else it.priceCents,
                                priceChecked = it.priceChecked || update.remainingVend > 0,
                            )
                        }
                    }
                    is MachineStatusSocket.Event.CommandResponse -> {
                        if (event.machineId != machineId) return@collect
                        val completed = event.status.equals("COMPLETED", ignoreCase = true)
                        val failed = event.status.equals("FAILED", ignoreCase = true)
                        when (event.type?.uppercase()) {
                            "REMOTE_VEND", "PAY_MACHINE", null -> {
                                if (completed) {
                                    _state.update {
                                        it.copy(
                                            vendRunning = false,
                                            dialog = MachineDialog.Success(it.machine?.displayName.orEmpty()),
                                        )
                                    }
                                    claimMachine(machineId)
                                } else if (failed) {
                                    _state.update {
                                        it.copy(
                                            vendRunning = false,
                                            dialog = MachineDialog.Failed("The machine didn't start. Please try again."),
                                        )
                                    }
                                }
                            }
                            "TOP_OFF" -> {
                                if (completed) {
                                    _state.update { it.copy(topOffRunning = false, dialog = MachineDialog.Success(it.machine?.displayName.orEmpty())) }
                                } else if (failed) {
                                    _state.update { it.copy(topOffRunning = false, dialog = MachineDialog.Failed("The top off didn't start. Please try again.")) }
                                }
                            }
                        }
                    }
                    else -> Unit
                }
            }
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
        _state.update { it.copy(selectedFamilyKey = family.key, selectedTemperature = cycle?.let { c -> temperatureOf(c) }) }
        cycle?.let { sendCycleSelection(machineId, it) }
    }

    fun selectTemperature(machineId: String, temperature: String) {
        val family = _state.value.families.firstOrNull { it.key == _state.value.selectedFamilyKey } ?: return
        selectFamily(machineId, family, temperature)
    }

    private fun sendCycleSelection(machineId: String, cycle: Cycle) {
        viewModelScope.launch {
            repository.setCycles(machineId, cycle.id, null, socket.currentConnectionToken)
        }
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

    fun startVend(machineId: String) {
        val state = _state.value
        val subAccount = state.user?.accounts?.firstOrNull()?.subAccounts?.firstOrNull()
        val machine = state.machine ?: return
        val amount = state.effectivePriceCents

        when {
            subAccount == null -> {
                _state.update { it.copy(dialog = MachineDialog.Failed("No account balance found for this location.")) }
                return
            }
            // The service refuses vends without an amount (official: "No amount to charge"),
            // so a machine that never reported a price cannot be started.
            amount <= 0 -> {
                _state.update { it.copy(dialog = MachineDialog.NoAmountToCharge) }
                return
            }
            state.availableCents < amount -> {
                _state.update { it.copy(dialog = MachineDialog.NotEnoughFunds("balance")) }
                return
            }
        }

        viewModelScope.launch {
            _state.update { it.copy(vendRunning = true) }
            when (
                val result = repository.remoteVend(
                    machineId = machineId,
                    userAccountId = subAccount.userAccountId ?: subAccount.id,
                    vendAmount = amount,
                    connectionToken = socket.currentConnectionToken,
                    useRewardsCredit = state.useRewards,
                )
            ) {
                is ApiResult.Success -> {
                    delay(30_000)
                    if (_state.value.vendRunning) {
                        _state.update { it.copy(vendRunning = false, dialog = MachineDialog.NoConfirmation) }
                    }
                }
                is ApiResult.Failure -> _state.update { it.copy(vendRunning = false, dialog = MachineDialog.Failed(result.message)) }
            }
        }
    }

    fun topOff(machineId: String) {
        val state = _state.value
        val subAccount = state.user?.accounts?.firstOrNull()?.subAccounts?.firstOrNull() ?: return
        val amount = if (state.topOffCents > 0) state.topOffCents else state.effectivePriceCents
        viewModelScope.launch {
            _state.update { it.copy(topOffRunning = true) }
            when (
                val result = repository.remoteTopoff(
                    machineId = machineId,
                    userAccountId = subAccount.userAccountId ?: subAccount.id,
                    vendAmount = amount,
                    connectionToken = socket.currentConnectionToken,
                )
            ) {
                is ApiResult.Success -> {
                    delay(30_000)
                    if (_state.value.topOffRunning) {
                        _state.update { it.copy(topOffRunning = false, dialog = MachineDialog.NoConfirmation) }
                    }
                }
                is ApiResult.Failure -> _state.update { it.copy(topOffRunning = false, dialog = MachineDialog.Failed(result.message)) }
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
    val parts = key.split('_')
    return if (parts.size > 1) parts.last() else null
}

fun cyclesToFamilies(cycles: List<Cycle>): List<CycleFamily> =
    cycles
        .filter { it.enabled }
        .groupBy { cycle ->
            val key = cycle.description?.takeIf { it.isNotBlank() } ?: cycle.name
            key.substringBeforeLast('_', key)
        }
        .map { (family, variants) -> CycleFamily(key = family, variants = variants) }

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
                onStart = { viewModel.startVend(machineId) },
                onTopOff = { viewModel.topOff(machineId) },
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
                        viewModel.selectFamily(machineId, family, family.temperatures.firstOrNull())
                    },
                    onTemperature = { temperature ->
                        viewModel.selectTemperature(machineId, temperature)
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

    MachineDialogs(state = state, onDismiss = { viewModel.dismissDialog() }, onAddCredit = {
        state.user?.accounts?.firstOrNull()?.let { account ->
            onAddCredit(account.id, account.subAccounts.firstOrNull()?.geoBoundaryId.orEmpty())
        }
    }, onBack = onBack)
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
                "Laundry is included at this location , no payment is needed. Press start on the machine to begin your cycle.",
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
                enabled = !state.vendRunning &&
                    state.machine?.isActive == true &&
                    !state.isRunning &&
                    state.effectivePriceCents > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (state.vendRunning) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    val selectedCycle = state.selectedCycle
                    Text(
                        if (selectedCycle != null) {
                            "Start ${prettyCycleKey(selectedCycle.description ?: selectedCycle.name)}"
                        } else {
                            "Start machine"
                        },
                    )
                }
            }
            if (state.canTopOff && state.isRunning) {
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
                if (isDryer) "Pick how warm the dryer runs." else "Tap a fabric type, then pick a temperature.",
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
                            label = { Text(prettyCycleKey(family.key)) },
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
                                    family.temperatures.joinToString(" · ") { temperature -> prettyCycleKey(temperature) },
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

            val selectedFamily = state.families.firstOrNull { it.key == state.selectedFamilyKey }
            if (selectedFamily != null && selectedFamily.temperatures.size > 1) {
                Text("Temperature", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    selectedFamily.temperatures.forEach { temperature ->
                        FilterChip(
                            selected = temperature == state.selectedTemperature,
                            onClick = { onTemperature(temperature) },
                            label = { Text(prettyCycleKey(temperature)) },
                        )
                    }
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
            text = { Text(dialog.message) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
        is MachineDialog.NoConfirmation -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("We didn't get confirmation") },
            text = { Text("We didn't get confirmation in time. Your payment may have already gone through, check the machine before trying again.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
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

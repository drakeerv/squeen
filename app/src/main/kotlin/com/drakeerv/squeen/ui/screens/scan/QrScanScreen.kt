package com.drakeerv.squeen.ui.screens.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.drakeerv.squeen.ui.components.AppTopBar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.drakeerv.squeen.ui.navigation.QrMode
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.json.JSONObject

/** Parsed QR payloads, mirroring the original `pl3` parser. */
sealed interface ScannedValue {
    data class ValueCenterCode(val code: String) : ScannedValue
    data class LocationNumber(val number: String) : ScannedValue
    data class Machine(val roomId: String?, val machineNumber: String?, val machineId: String?) : ScannedValue
    data class Kiosk(val vtmId: String, val roomId: String?) : ScannedValue
    data class ShortCode(val token: String) : ScannedValue
    data class Marketing(val message: String) : ScannedValue
    data object Unusable : ScannedValue
}

fun parseScannedValue(raw: String): ScannedValue {
    val value = raw.trim()
    if (value.isEmpty() || value.length > 64) return ScannedValue.Unusable

    // qr_<token>, ScanPay&Wash short code
    val qrToken = Regex("qr_([A-Za-z0-9]+)").find(value)?.groupValues?.getOrNull(1)
    if (qrToken != null && qrToken.isNotEmpty()) return ScannedValue.ShortCode(qrToken)

    if (!value.startsWith("{")) {
        return if (value.all { it.isLetterOrDigit() }) {
            ScannedValue.ValueCenterCode(value)
        } else ScannedValue.Unusable
    }

    return try {
        val json = JSONObject(value)
        when {
            json.has("vtmId") -> ScannedValue.Kiosk(
                json.getString("vtmId"),
                json.optString("roomId").takeIf { it.isNotEmpty() },
            )
            json.has("machineNode") || json.has("machineId") -> ScannedValue.Machine(
                roomId = json.optString("roomId").takeIf { it.isNotEmpty() },
                machineNumber = json.optString("machineNode").takeIf { it.isNotEmpty() }
                    ?: json.optString("machineNumber").takeIf { it.isNotEmpty() },
                machineId = json.optString("machineId").takeIf { it.isNotEmpty() },
            )
            json.has("roomId") -> ScannedValue.Machine(
                roomId = json.optString("roomId").takeIf { it.isNotEmpty() },
                machineNumber = null,
                machineId = null,
            )
            json.has("locationNumber") -> ScannedValue.LocationNumber(json.getString("locationNumber"))
            json.has("displayMessage") -> ScannedValue.Marketing(json.getString("displayMessage"))
            else -> ScannedValue.Unusable
        }
    } catch (_: Exception) {
        ScannedValue.Unusable
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScanScreen(
    mode: QrMode,
    onBack: () -> Unit,
    onValueCenterId: (String) -> Unit,
    onLocationNumber: (String) -> Unit,
    onMachineScanned: (roomId: String?, machineNumber: String?, machineId: String?) -> Unit,
    onKioskScanned: (vtmId: String, roomId: String?) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    var marketingMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var launched by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents == null) {
            launched = false
            return@rememberLauncherForActivityResult
        }
        when (val parsed = parseScannedValue(result.contents)) {
            is ScannedValue.ValueCenterCode -> onValueCenterId(parsed.code)
            is ScannedValue.LocationNumber -> onLocationNumber(parsed.number)
            is ScannedValue.Machine -> {
                if (mode == QrMode.MACHINE) {
                    onMachineScanned(parsed.roomId, parsed.machineNumber, parsed.machineId)
                } else {
                    onMachineScanned(parsed.roomId, parsed.machineNumber, parsed.machineId)
                }
            }
            is ScannedValue.Kiosk -> onKioskScanned(parsed.vtmId, parsed.roomId)
            is ScannedValue.Marketing -> marketingMessage = parsed.message
            is ScannedValue.ShortCode -> errorMessage = "This QR code can't be used here."
            ScannedValue.Unusable -> errorMessage = when (mode) {
                QrMode.LOCATION -> "Error scanning Location QR."
                QrMode.MACHINE -> "Error scanning Machine QR."
                QrMode.KIOSK -> "Error scanning Value Station QR."
            }
        }
        launched = false
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            launcher.launch(scannerOptions())
        } else {
            errorMessage = "Camera permission is required to scan QR codes."
        }
    }

    fun startScan() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (granted) launcher.launch(scannerOptions()) else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(Unit) {
        if (!launched) {
            launched = true
            startScan()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = {
                    Text(
                        when (mode) {
                            QrMode.LOCATION -> "Scan Location QR"
                            QrMode.MACHINE -> "Scan Machine QR"
                            QrMode.KIOSK -> "Scan Value Station QR"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Filled.QrCodeScanner,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                when (mode) {
                    QrMode.LOCATION -> "Scan the QR code to add location"
                    QrMode.MACHINE -> "Scan the QR code to add machine"
                    QrMode.KIOSK -> "Scan the QR code on the value station"
                },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = { startScan() }) { Text("Open camera") }
        }
    }

    if (marketingMessage != null) {
        AlertDialog(
            onDismissRequest = { marketingMessage = null },
            title = { Text("Speed Queen") },
            text = { Text(marketingMessage.orEmpty()) },
            confirmButton = { TextButton(onClick = { marketingMessage = null }) { Text("OK") } },
        )
    }
    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { errorMessage = null; startScan() },
            title = { Text("Scan failed") },
            text = { Text(errorMessage.orEmpty()) },
            confirmButton = {
                TextButton(onClick = {
                    errorMessage = null
                    startScan()
                }) { Text("Try again") }
            },
            dismissButton = { TextButton(onClick = { errorMessage = null; onBack() }) { Text("Cancel") } },
        )
    }
}

private fun scannerOptions(): ScanOptions = ScanOptions()
    .setDesiredBarcodeFormats(ScanOptions.QR_CODE, ScanOptions.CODE_128, ScanOptions.CODE_39)
    .setPrompt("Point the camera at a Speed Queen QR code")
    .setBeepEnabled(false)
    .setOrientationLocked(true)

package com.drakeerv.squeen.ui.screens.cardlink

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.view.WindowManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.drakeerv.squeen.data.ApiResult
import com.drakeerv.squeen.data.SpeedQueenRepository
import com.drakeerv.squeen.data.session.SessionManager
import com.drakeerv.squeen.ui.components.AppTopBar
import com.drakeerv.squeen.ui.components.ErrorState
import com.drakeerv.squeen.ui.components.LoadingState
import com.drakeerv.squeen.ui.components.LocalSnackbarHost
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel

data class CardLinkUiState(
    val loading: Boolean = true,
    val shortcode: String? = null,
    val token: String? = null,
    val error: String? = null,
)

class CardLinkViewModel(
    private val repository: SpeedQueenRepository,
    private val session: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(CardLinkUiState())
    val state: StateFlow<CardLinkUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val result = repository.userShortcode()) {
                is ApiResult.Success -> _state.update {
                    it.copy(loading = false, shortcode = result.data, token = session.token)
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, token = session.token, error = result.message)
                }
            }
        }
    }
}

/**
 * Card link / QR pay, shows the personal ScanPay&Wash code (and the sign-in token) as
 * scannable QR codes, mirroring the official app's "Card link" screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardLinkScreen(
    onBack: () -> Unit,
    viewModel: CardLinkViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHost.current
    val clipboard = LocalClipboardManager.current
    var tab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { viewModel.load() }

    // Keep the screen awake while a code is displayed, like the official app.
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text("Scan & pay") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LoadingState()
                state.shortcode.isNullOrBlank() -> ErrorState(
                    message = state.error ?: "We couldn't load your payment code right now.",
                    onRetry = { viewModel.load() },
                )
                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = tab == 0,
                            onClick = { tab = 0 },
                            label = {
                                Text(
                                    "Payment code",
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                        FilterChip(
                            selected = tab == 1,
                            onClick = { tab = 1 },
                            label = {
                                Text(
                                    "Sign-in code",
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(16.dp))

                    val content = if (tab == 0) state.shortcode.orEmpty() else state.token.orEmpty()
                    val qr by androidx.compose.runtime.produceState<ImageBitmap?>(initialValue = null, content) {
                        value = generateQrCode(content)
                    }

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                if (tab == 0) "Scan at the machine" else "Scan to sign in",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                if (tab == 0) {
                                    "Show this code at any Speed Queen machine or kiosk that supports QR payment."
                                } else {
                                    "Your account sign-in code. Keep it private, anyone with this code can act as you."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(16.dp))
                            Box(
                                modifier = Modifier
                                    .size(248.dp)
                                    .background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(16.dp))
                                    .padding(14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                val qrBitmap = qr
                                if (qrBitmap != null) {
                                    Image(
                                        bitmap = qrBitmap,
                                        contentDescription = "QR code",
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    CircularProgressIndicator()
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(
                                content,
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(4.dp))
                            val scope = rememberCoroutineScope()
                            TextButton(onClick = {
                                clipboard.setText(AnnotatedString(content))
                                scope.launch {
                                    snackbar.showSnackbar(
                                        if (tab == 0) "Payment code copied" else "Sign-in code copied",
                                    )
                                }
                            }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(8.dp))
                                Text("Copy code")
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Codes are personal to your account and change when you sign out.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

/** Renders [content] as a crisp black-on-white QR bitmap. */
private suspend fun generateQrCode(content: String, sizePx: Int = 640): ImageBitmap? =
    withContext(Dispatchers.Default) {
        if (content.isBlank()) return@withContext null
        runCatching {
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
            val pixels = IntArray(sizePx * sizePx)
            for (y in 0 until sizePx) {
                val offset = y * sizePx
                for (x in 0 until sizePx) {
                    pixels[offset + x] = if (matrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE
                }
            }
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
            bitmap.asImageBitmap()
        }.getOrNull()
    }

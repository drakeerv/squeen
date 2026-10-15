package com.drakeerv.squeen.data.net

import com.drakeerv.squeen.data.jsonapi.primitiveContent
import com.drakeerv.squeen.data.jsonapi.safeObject
import com.drakeerv.squeen.data.session.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.random.Random

/**
 * Live machine status channel.
 *
 * Reverse engineered protocol:
 *  - `wss://websocket.alliancelaundrydigital.com/?app=ca-android&ver=4.2.2&connectionToken=<uuid>`
 *  - `Authorization: Bearer <alliancels-auth-token>`
 *  - subscribe: `{"eventType":"SUBSCRIBE","roomId":"...","topics":["room:<id>"],"app":"ca-android","ver":"4.2.2"}`
 *  - unsubscribe / ping described by the same envelope
 *  - inbound events: MACHINE_STATUS, COMMAND_STATUSES, COMMAND_RESPONSE, PAYMENT_STATUS,
 *    KIOSK_SESSION_STATUS and `error`
 */
class MachineStatusSocket(
    private val session: SessionManager,
    json: Json,
    private val okHttpClient: OkHttpClient,
) {

    private val json: Json = json

    sealed interface Event {
        data class MachineStatus(val update: MachineStatusUpdate) : Event
        data class CommandResponse(val machineId: String, val status: String, val type: String?) : Event
        data class PaymentStatus(val sessionId: String, val status: String, val raw: JsonObject) : Event
        data class KioskSessionStatus(val sessionId: String, val status: String, val raw: JsonObject) : Event
        data class ServerError(val message: String) : Event
        data class ConnectionError(val message: String) : Event
        data object Connected : Event
        data object Disconnected : Event
    }

    data class MachineStatusUpdate(
        val machineId: String,
        val roomId: String?,
        val statusId: String?,
        val lastStatusId: String?,
        val remainingSeconds: Int,
        val remainingVend: Int,
        val canTopOff: Boolean,
        val topOffTime: Int,
        val topOffVend: Int,
        val topOffDisabled: Boolean,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 32)
    val events: SharedFlow<Event> = _events

    private var webSocket: WebSocket? = null
    private var connectionToken: String = UUID.randomUUID().toString()
    private var subscribedRoomId: String? = null
    private val extraRooms = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private var reconnectJob: Job? = null
    private var heartbeatJob: Job? = null
    private var reconnectAttempts = 0
    private var intentionalClose = false
    private var connected = false

    val isConnected: Boolean get() = connected

    /** Correlation id shared with the REST command endpoints (`getPrice`, `remoteVend`, …). */
    val currentConnectionToken: String get() = connectionToken

    fun connect(roomId: String?) {
        roomId?.let { subscribedRoomId = it }
        if (webSocket != null) {
            roomId?.let { subscribeRoom(it) }
            return
        }
        intentionalClose = false
        reconnectAttempts = 0
        openSocket()
    }

    /** Subscribes permanently to a room (e.g. rooms with active wash alerts). */
    fun watchRoom(roomId: String) {
        extraRooms.add(roomId)
        if (connected) sendSubscribe(roomId)
    }

    fun stopWatchingRoom(roomId: String) {
        extraRooms.remove(roomId)
        unsubscribeRoom(roomId)
    }

    fun disconnect() {
        intentionalClose = true
        reconnectJob?.cancel()
        heartbeatJob?.cancel()
        subscribedRoomId = null
        webSocket?.close(1000, "client closing")
        webSocket = null
        connected = false
    }

    fun subscribeRoom(roomId: String) {
        subscribedRoomId = roomId
        if (connected) sendSubscribe(roomId)
    }

    fun unsubscribeRoom(roomId: String? = subscribedRoomId) {
        if (roomId == null) return
        if (subscribedRoomId == roomId) subscribedRoomId = null
        extraRooms.remove(roomId)
        if (connected) {
            send(
                buildJson {
                    put("eventType", "UNSUBSCRIBE")
                    put("roomId", roomId)
                    putArray("topics", listOf("room:$roomId"))
                    put("app", "ca-android")
                    put("ver", APP_VERSION)
                }
            )
        }
    }

    private fun openSocket() {
        val token = session.token
        if (token.isEmpty()) return
        val url = "wss://websocket.alliancelaundrydigital.com/" +
            "?app=ca-android&ver=$APP_VERSION&connectionToken=$connectionToken"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .build()

        val client = okHttpClient.newBuilder()
            .pingInterval(20, TimeUnit.SECONDS)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connected = true
                reconnectAttempts = 0
                emit(Event.Connected)
                subscribedRoomId?.let { sendSubscribe(it) }
                extraRooms.forEach { sendSubscribe(it) }
                startHeartbeat()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                connected = false
                this@MachineStatusSocket.webSocket = null
                emit(Event.Disconnected)
                if (!intentionalClose) scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                connected = false
                this@MachineStatusSocket.webSocket = null
                val message = when (response?.code) {
                    401, 403 -> "Live updates are temporarily unavailable."
                    409 -> "Live connection was reset. Reconnecting…"
                    else -> t.message ?: "Live connection failed."
                }
                emit(Event.ConnectionError(message))
                if (!intentionalClose) scheduleReconnect(response?.code == 409)
            }
        })
    }

    private fun scheduleReconnect(freshToken: Boolean = false) {
        if (intentionalClose || reconnectJob?.isActive == true) return
        if (freshToken) connectionToken = UUID.randomUUID().toString()
        reconnectAttempts += 1
        val base = min(30_000L, 1_000L shl min(reconnectAttempts, 5))
        val jitter = (base * 0.2 * Random.nextDouble()).toLong()
        reconnectJob = scope.launch {
            delay(base + jitter)
            if (!intentionalClose && !connected) {
                webSocket = null
                openSocket()
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (connected) {
                delay(45_000)
                if (connected) {
                    send(buildJson { put("eventType", "PING") })
                }
            }
        }
    }

    private fun sendSubscribe(roomId: String) {
        send(
            buildJson {
                put("eventType", "SUBSCRIBE")
                put("roomId", roomId)
                putArray("topics", listOf("room:$roomId"))
                put("app", "ca-android")
                put("ver", APP_VERSION)
            }
        )
    }

    private fun send(json: JsonObject): Boolean = webSocket?.send(json.toString()) ?: false

    private fun buildJson(block: MutableJsonBuilder.() -> Unit): JsonObject =
        MutableJsonBuilder().apply(block).build()

    private class MutableJsonBuilder {
        private val map = LinkedHashMap<String, kotlinx.serialization.json.JsonElement>()

        fun put(key: String, value: String) {
            map[key] = kotlinx.serialization.json.JsonPrimitive(value)
        }

        fun putArray(key: String, values: List<String>) {
            map[key] = kotlinx.serialization.json.JsonArray(values.map { kotlinx.serialization.json.JsonPrimitive(it) })
        }

        fun build(): JsonObject = JsonObject(map)
    }

    private fun handleMessage(text: String) {
        val root = runCatching { json.parseToJsonElement(text).safeObject() }.getOrNull() ?: return
        val eventType = root["eventType"].primitiveContent() ?: return
        when (eventType) {
            "MACHINE_STATUS" -> {
                val data = root["data"].safeObject() ?: return
                emit(
                    Event.MachineStatus(
                        MachineStatusUpdate(
                            machineId = data["id"].primitiveContent().orEmpty(),
                            roomId = data["roomId"].primitiveContent(),
                            statusId = data["statusId"].primitiveContent(),
                            lastStatusId = data["lastStatusId"].primitiveContent(),
                            remainingSeconds = data["remainingSeconds"].primitiveContent()?.toIntOrNull() ?: 0,
                            remainingVend = data["remainingVend"].primitiveContent()?.toIntOrNull() ?: 0,
                            canTopOff = data["canTopOff"].primitiveContent()?.toBooleanStrictOrNull() ?: false,
                            topOffTime = data["topOffTime"].primitiveContent()?.toIntOrNull() ?: 0,
                            topOffVend = data["topOffVend"].primitiveContent()?.toIntOrNull() ?: 0,
                            topOffDisabled = data["topoffFullyDisabled"].primitiveContent()?.toBooleanStrictOrNull() ?: false,
                        )
                    )
                )
            }
            "COMMAND_RESPONSE" -> {
                val data = root["data"].safeObject() ?: return
                emit(
                    Event.CommandResponse(
                        machineId = data["machineId"].primitiveContent().orEmpty(),
                        status = data["status"].primitiveContent().orEmpty(),
                        type = data["type"].primitiveContent(),
                    )
                )
            }
            "PAYMENT_STATUS" -> {
                val data = root["data"].safeObject() ?: return
                emit(
                    Event.PaymentStatus(
                        sessionId = data["sessionId"].primitiveContent().orEmpty(),
                        status = data["status"].primitiveContent().orEmpty(),
                        raw = data,
                    )
                )
            }
            "KIOSK_SESSION_STATUS" -> {
                val data = root["data"].safeObject() ?: return
                emit(
                    Event.KioskSessionStatus(
                        sessionId = data["sessionId"].primitiveContent().orEmpty(),
                        status = data["status"].primitiveContent().orEmpty(),
                        raw = data,
                    )
                )
            }
            "error" -> emit(Event.ServerError(root["message"].primitiveContent().orEmpty()))
        }
    }

    private fun emit(event: Event) {
        _events.tryEmit(event)
    }

    companion object {
        const val APP_VERSION = "4.2.2"
    }
}

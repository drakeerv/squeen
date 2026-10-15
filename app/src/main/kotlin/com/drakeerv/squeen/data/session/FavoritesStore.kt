package com.drakeerv.squeen.data.session

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class FavoriteLocation(
    val id: String,
    val name: String,
    val organizationName: String? = null,
)

@Serializable
data class FavoriteRoom(
    val roomId: String,
    val roomName: String,
    val locationId: String? = null,
    val locationName: String? = null,
)

/**
 * Client-side favourites (stored only on the device): the home screen surfaces the
 * locations and rooms the user starred for quick access.
 */
class FavoritesStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _locations = MutableStateFlow(readLocations())
    val locations: StateFlow<List<FavoriteLocation>> = _locations.asStateFlow()

    private val _rooms = MutableStateFlow(readRooms())
    val rooms: StateFlow<List<FavoriteRoom>> = _rooms.asStateFlow()

    fun isLocationFavorite(id: String): Boolean = _locations.value.any { it.id == id }

    fun isRoomFavorite(roomId: String): Boolean = _rooms.value.any { it.roomId == roomId }

    /** @return true when the location is now a favourite. */
    fun toggleLocation(location: FavoriteLocation): Boolean {
        val current = _locations.value.toMutableList()
        val existing = current.firstOrNull { it.id == location.id }
        val added = existing == null
        if (added) current.add(location) else current.remove(existing)
        _locations.value = current
        writeLocations(current)
        return added
    }

    /** @return true when the room is now a favourite. */
    fun toggleRoom(room: FavoriteRoom): Boolean {
        val current = _rooms.value.toMutableList()
        val existing = current.firstOrNull { it.roomId == room.roomId }
        val added = existing == null
        if (added) current.add(room) else current.remove(existing)
        _rooms.value = current
        writeRooms(current)
        return added
    }

    fun clear() {
        _locations.value = emptyList()
        _rooms.value = emptyList()
        prefs.edit().remove(KEY_LOCATIONS).remove(KEY_ROOMS).apply()
    }

    // ------------------------------------------------------------- internals

    private fun readLocations(): List<FavoriteLocation> =
        (prefs.getStringSet(KEY_LOCATIONS, null) ?: emptySet()).mapNotNull { entry ->
            runCatching { json.decodeFromString(FavoriteLocation.serializer(), entry) }.getOrNull()
        }

    private fun readRooms(): List<FavoriteRoom> =
        (prefs.getStringSet(KEY_ROOMS, null) ?: emptySet()).mapNotNull { entry ->
            runCatching { json.decodeFromString(FavoriteRoom.serializer(), entry) }.getOrNull()
        }

    private fun writeLocations(values: List<FavoriteLocation>) {
        prefs.edit()
            .putStringSet(KEY_LOCATIONS, values.map { json.encodeToString(FavoriteLocation.serializer(), it) }.toSet())
            .apply()
    }

    private fun writeRooms(values: List<FavoriteRoom>) {
        prefs.edit()
            .putStringSet(KEY_ROOMS, values.map { json.encodeToString(FavoriteRoom.serializer(), it) }.toSet())
            .apply()
    }

    companion object {
        private const val PREFS_FILE = "favorites"
        private const val KEY_LOCATIONS = "favorite_locations"
        private const val KEY_ROOMS = "favorite_rooms"
    }
}

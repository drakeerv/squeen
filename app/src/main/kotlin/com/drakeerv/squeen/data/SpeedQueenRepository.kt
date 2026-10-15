package com.drakeerv.squeen.data

import com.drakeerv.squeen.data.api.AddCreditRequest
import com.drakeerv.squeen.data.api.AddPaymentMethodRequest
import com.drakeerv.squeen.data.api.AddPhoneNumberRequest
import com.drakeerv.squeen.data.api.AssignMachineRequest
import com.drakeerv.squeen.data.api.ChangePasswordRequest
import com.drakeerv.squeen.data.api.CustomerGeoBoundaryRequest
import com.drakeerv.squeen.data.api.CycleParams
import com.drakeerv.squeen.data.api.DefaultFlagRequest
import com.drakeerv.squeen.data.api.EmailTransactionRequest
import com.drakeerv.squeen.data.api.ForceLogoutRequest
import com.drakeerv.squeen.data.api.IdRef
import com.drakeerv.squeen.data.api.JoinGroupRequest
import com.drakeerv.squeen.data.api.LoginRequest
import com.drakeerv.squeen.data.api.MailboxStatusRequest
import com.drakeerv.squeen.data.api.PhoneRef
import com.drakeerv.squeen.data.api.PhoneVerifyRequest
import com.drakeerv.squeen.data.api.PriceRequest
import com.drakeerv.squeen.data.api.PushDeviceRequest
import com.drakeerv.squeen.data.api.RedeemPromoRequest
import com.drakeerv.squeen.data.api.RegisterRequest
import com.drakeerv.squeen.data.api.RemoveCustomerGeoBoundaryRequest
import com.drakeerv.squeen.data.api.ReplenishmentDefaultsRequest
import com.drakeerv.squeen.data.api.ResetPasswordRequest
import com.drakeerv.squeen.data.api.SetCyclesRequest
import com.drakeerv.squeen.data.api.SpeedQueenApi
import com.drakeerv.squeen.data.api.StripePaymentIntentRequest
import com.drakeerv.squeen.data.api.StripeSetupRequest
import com.drakeerv.squeen.data.api.SubscriptionCondition
import com.drakeerv.squeen.data.api.TransactLinkRequest
import com.drakeerv.squeen.data.api.UpdateAccountRequest
import com.drakeerv.squeen.data.api.UpdateUserRequest
import com.drakeerv.squeen.data.api.VendRequest
import com.drakeerv.squeen.data.api.VtmSessionRequest
import com.drakeerv.squeen.data.api.WashAlertRequest
import com.drakeerv.squeen.data.db.PayloadCacheDao
import com.drakeerv.squeen.data.db.PayloadCacheEntity
import com.drakeerv.squeen.data.jsonapi.JsonApiDocument
import com.drakeerv.squeen.data.jsonapi.JsonApiResource
import com.drakeerv.squeen.data.jsonapi.primitiveContent
import com.drakeerv.squeen.data.jsonapi.safeArray
import com.drakeerv.squeen.data.jsonapi.safeObject
import com.drakeerv.squeen.data.model.Account
import com.drakeerv.squeen.data.model.ApplicationInfo
import com.drakeerv.squeen.data.model.BalanceInfo
import com.drakeerv.squeen.data.model.CampaignPromotion
import com.drakeerv.squeen.data.model.CustomerGeoBoundary
import com.drakeerv.squeen.data.model.GeoBoundary
import com.drakeerv.squeen.data.model.Machine
import com.drakeerv.squeen.data.model.MachineCommand
import com.drakeerv.squeen.data.model.MachineCycles
import com.drakeerv.squeen.data.model.MailboxNotification
import com.drakeerv.squeen.data.model.OrganizationInfo
import com.drakeerv.squeen.data.model.PaymentMethod
import com.drakeerv.squeen.data.model.ReplenishmentDefaults
import com.drakeerv.squeen.data.model.Room
import com.drakeerv.squeen.data.model.SmsCountry
import com.drakeerv.squeen.data.model.StripeKeys
import com.drakeerv.squeen.data.model.Transaction
import com.drakeerv.squeen.data.model.User
import com.drakeerv.squeen.data.model.WashAlert
import com.drakeerv.squeen.data.model.apiErrorCode
import com.drakeerv.squeen.data.model.apiErrorMessage
import com.drakeerv.squeen.data.model.toAccount
import com.drakeerv.squeen.data.model.toApplicationInfo
import com.drakeerv.squeen.data.model.toBalanceInfo
import com.drakeerv.squeen.data.model.toCampaignPromotion
import com.drakeerv.squeen.data.model.toGeoBoundary
import com.drakeerv.squeen.data.model.toMachine
import com.drakeerv.squeen.data.model.toMachineCommand
import com.drakeerv.squeen.data.model.toMachineCycles
import com.drakeerv.squeen.data.model.toNotifications
import com.drakeerv.squeen.data.model.toOrganizationInfo
import com.drakeerv.squeen.data.model.toPaymentMethod
import com.drakeerv.squeen.data.model.toPhoneNumber
import com.drakeerv.squeen.data.model.toReplenishmentDefaults
import com.drakeerv.squeen.data.model.toRoom
import com.drakeerv.squeen.data.model.toSmsCountries
import com.drakeerv.squeen.data.model.toStripeKeys
import com.drakeerv.squeen.data.model.toSubAccount
import com.drakeerv.squeen.data.model.toTransaction
import com.drakeerv.squeen.data.model.toUser
import com.drakeerv.squeen.data.model.toWashAlert
import com.drakeerv.squeen.data.net.NetworkFactory
import com.drakeerv.squeen.data.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * High level data access. Every method returns [ApiResult] so callers never see
 * raw exceptions. Network payloads are cached (raw JSON:API documents) and used
 * as fallback when the device is offline.
 */
class SpeedQueenRepository(
    private val api: SpeedQueenApi,
    private val session: SessionManager,
    private val cache: PayloadCacheDao,
    private val json: Json = NetworkFactory.json,
) {

    // ------------------------------------------------------------ plumbing

    private suspend fun call(block: suspend () -> Response<JsonElement>): ApiResult<JsonElement> =
        withContext(Dispatchers.IO) {
            try {
                val response = block()
                if (response.isSuccessful) {
                    ApiResult.Success(response.body() ?: JsonNull)
                } else {
                    failureFrom(response.errorBody()?.string(), response.code(), unauthorized = response.code() == 401)
                }
            } catch (io: IOException) {
                ApiResult.Failure(
                    message = "No internet connection. Please check your network and try again.",
                    offline = true,
                    cause = io,
                )
            } catch (t: Throwable) {
                ApiResult.Failure(message = t.message ?: "Something went wrong. Please try again.", cause = t)
            }
        }

    private fun failureFrom(errorBody: String?, code: Int, unauthorized: Boolean = false): ApiResult.Failure {
        val parsed = errorBody?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() }
        val message = parsed.apiErrorMessage()?.takeIf { it.isUserFacing() }
            ?: when (code) {
                401, 403 -> "Your session expired. Please sign in again."
                404 -> "We couldn't find what you were looking for."
                429 -> "Too many requests. Please try again in a moment."
                in 500..599 -> "The server is having trouble right now. Please try again."
                else -> "Something went wrong. Please try again."
            }
        return ApiResult.Failure(message, code, unauthorized = unauthorized || code == 401 || code == 403)
    }

    /**
     * Some backend failures leak Node exception text ("Cannot read properties of undefined…").
     * Those are never shown to the user; generic copy is used instead.
     */
    private fun String.isUserFacing(): Boolean {
        if (isBlank() || length > 200) return false
        val lower = lowercase()
        return listOf("cannot read propert", "undefined", "typeerror", "syntaxerror", "is not a function", "\n    at ")
            .none { lower.contains(it) }
    }

    private suspend fun cachePut(key: String, payload: String) {
        runCatching { cache.put(PayloadCacheEntity(key, payload, System.currentTimeMillis())) }
    }

    private suspend fun cacheGet(key: String): JsonElement? {
        val raw = runCatching { cache.get(key) }.getOrNull() ?: return null
        return runCatching { json.parseToJsonElement(raw) }.getOrNull()
    }

    /** Cached payload younger than [maxAgeMs], if available. */
    private suspend fun cacheGetFresh(key: String, maxAgeMs: Long): JsonElement? {
        val entity = runCatching { cache.getEntity(key) }.getOrNull() ?: return null
        if (System.currentTimeMillis() - entity.updatedAt > maxAgeMs) return null
        return runCatching { json.parseToJsonElement(entity.payload) }.getOrNull()
    }

    private fun parseOrNull(raw: String?): JsonElement? =
        raw?.takeIf { it.isNotBlank() }?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() }

    // ---------------------------------------------------------------- auth

    /** Performs login and stores the session token/user id from the response headers. */
    suspend fun login(
        email: String? = null,
        internationalCode: String? = null,
        areaCode: String? = null,
        phoneNumber: String? = null,
        password: String,
    ): ApiResult<User> = withContext(Dispatchers.IO) {
        try {
            val response = api.login(
                LoginRequest(
                    email = email?.takeIf { it.isNotBlank() },
                    telecomInternationalCode = internationalCode?.takeIf { it.isNotBlank() },
                    telecomAreaCode = areaCode?.takeIf { it.isNotBlank() },
                    telecomContactNumber = phoneNumber?.takeIf { it.isNotBlank() },
                    password = password,
                    littleDuck = session.deviceId,
                )
            )
            authFromResponse(response)
        } catch (io: IOException) {
            ApiResult.Failure("No internet connection. Please check your network and try again.", offline = true, cause = io)
        } catch (t: Throwable) {
            ApiResult.Failure(t.message ?: "Sign in failed. Please try again.", cause = t)
        }
    }

    suspend fun register(
        firstName: String,
        lastName: String,
        email: String,
        password: String,
        internationalCode: String? = null,
        areaCode: String? = null,
        phoneNumber: String? = null,
        optOutSms: Boolean = false,
        optOutEmail: Boolean = false,
    ): ApiResult<User> = withContext(Dispatchers.IO) {
        try {
            val response = api.registerCustomer(
                RegisterRequest(
                    firstName = firstName,
                    lastName = lastName,
                    email = email,
                    password = password,
                    telecomInternationalCode = internationalCode?.takeIf { it.isNotBlank() },
                    telecomAreaCode = areaCode?.takeIf { it.isNotBlank() },
                    telecomContactNumber = phoneNumber?.takeIf { it.isNotBlank() },
                    universalOptOutSms = optOutSms,
                    universalOptOutEmail = optOutEmail,
                    littleDuck = session.deviceId,
                )
            )
            authFromResponse(response)
        } catch (io: IOException) {
            ApiResult.Failure("No internet connection. Please check your network and try again.", offline = true, cause = io)
        } catch (t: Throwable) {
            ApiResult.Failure(t.message ?: "Sign up failed. Please try again.", cause = t)
        }
    }

    private suspend fun authFromResponse(response: Response<JsonElement>): ApiResult<User> {
        if (!response.isSuccessful) {
            return failureFrom(response.errorBody()?.string(), response.code(), unauthorized = response.code() == 401)
        }
        val doc = JsonApiDocument.of(response.body())
        val user = doc.toUser()
            ?: return ApiResult.Failure("We couldn't sign you in. Please try again.")
        response.headers()["alliancels-auth-token"]?.let { session.setAuth(it, user.id) }
            ?: session.setUserId(user.id)
        session.cachedUserJson = json.encodeToString(JsonElement.serializer(), response.body() ?: JsonNull)
        applyOrganizationFromUser(user)
        return ApiResult.Success(user)
    }

    suspend fun resetPassword(
        email: String? = null,
        internationalCode: String? = null,
        areaCode: String? = null,
        phoneNumber: String? = null,
    ): ApiResult<Unit> = call {
        api.resetPassword(
            "android",
            ResetPasswordRequest(email, internationalCode, areaCode, phoneNumber),
        )
    }.toUnit()

    suspend fun changePassword(current: String, new: String): ApiResult<Unit> = call {
        api.changePassword(ChangePasswordRequest(session.userId, current, new))
    }.toUnit()

    suspend fun resendVerifyEmail(): ApiResult<Unit> = call { api.resendVerifyEmail(session.userId) }.toUnit()

    suspend fun verifyEmail(code: String): ApiResult<Unit> = call {
        api.verifyEmail(session.userId, com.drakeerv.squeen.data.api.VerifyEmailRequest(code, session.userId, IdRef(session.userId)))
    }.toUnit()

    suspend fun requestAccountDeletion(): ApiResult<Unit> = call { api.requestAccountDeletion(session.userId) }.toUnit()

    /** Server side force-logout of the current device; also clears local session. */
    suspend fun logoutRemote(): ApiResult<Unit> {
        val result = call { api.setForceLogout(ForceLogoutRequest(session.deviceId)) }
        session.clear()
        runCatching { cache.delete("user:${session.userId}") }
        return result.toUnit()
    }

    // ---------------------------------------------------------------- user

    suspend fun cachedUser(): User? = withContext(Dispatchers.Default) {
        session.cachedUserJson?.let { raw ->
            runCatching { JsonApiDocument.of(json.parseToJsonElement(raw)).toUser() }.getOrNull()
        }
    }

    @Volatile
    private var userFetchedAt: Long = 0

    suspend fun getUser(useCacheOnFailure: Boolean = true, force: Boolean = false): ApiResult<User> {
        if (session.userId.isEmpty()) return ApiResult.Failure("Not signed in", httpCode = 401, unauthorized = true)

        if (!force && useCacheOnFailure && System.currentTimeMillis() - userFetchedAt < USER_FRESH_MS) {
            val cached = cachedUserInternal()
            if (cached != null) return ApiResult.Success(cached)
        }

        val result = call { api.getUser(session.userId) }
        return when (result) {
            is ApiResult.Success -> {
                val doc = JsonApiDocument.of(result.data)
                val user = doc.toUser()
                if (user == null) {
                    ApiResult.Failure("We couldn't load your account.")
                } else {
                    session.cachedUserJson = json.encodeToString(JsonElement.serializer(), result.data)
                    userFetchedAt = System.currentTimeMillis()
                    applyOrganizationFromUser(user)
                    ApiResult.Success(user)
                }
            }
            is ApiResult.Failure -> {
                val cached = if (useCacheOnFailure) {
                    session.cachedUserJson?.let { JsonApiDocument.of(json.parseToJsonElement(it)).toUser() }
                } else null
                if (cached != null) ApiResult.Success(cached) else result
            }
        }
    }

    private fun applyOrganizationFromUser(user: User) {
        user.customerGeoBoundaries.firstOrNull()?.organizationId?.takeIf { it.isNotBlank() }?.let {
            if (session.organizationId.isEmpty()) session.setOrganizationId(it)
        }
    }

    suspend fun updateUserProfile(
        firstName: String,
        lastName: String,
        email: String,
        optOutSms: Boolean,
        optOutEmail: Boolean,
        phone: PhoneRef?,
        locale: String?,
    ): ApiResult<User> = call {
        api.updateUserProfile(
            session.userId,
            UpdateUserRequest(
                id = session.userId,
                firstName = firstName,
                lastName = lastName,
                email = email,
                universalOptOutSms = optOutSms,
                universalOptOutEmail = optOutEmail,
                phone = phone,
                locale = locale,
            ),
        )
    }.mapToUser()

    private fun ApiResult<JsonElement>.mapToUser(): ApiResult<User> = when (this) {
        is ApiResult.Success -> {
            val user = JsonApiDocument.of(data).toUser()
            if (user != null) {
                session.cachedUserJson = json.encodeToString(JsonElement.serializer(), data)
                ApiResult.Success(user)
            } else ApiResult.Failure("We couldn't update your profile.")
        }
        is ApiResult.Failure -> this
    }

    // ------------------------------------------------------------- phones

    suspend fun addPhoneNumber(internationalCode: String, areaCode: String, contactNumber: String): ApiResult<Unit> =
        call { api.addPhoneNumber(AddPhoneNumberRequest(internationalCode, areaCode, contactNumber, session.userId)) }.toUnit()

    suspend fun removePhoneNumber(phoneNumberId: String): ApiResult<Unit> = call { api.removePhoneNumber(phoneNumberId) }.toUnit()

    suspend fun requestPhoneVerification(countryCode: String, phoneNumber: String): ApiResult<Unit> = call {
        api.addPhoneNumberAndVerify(session.userId, PhoneVerifyRequest(countryCode, phoneNumber))
    }.toUnit()

    suspend fun verifyPhoneNumber(code: String): ApiResult<Unit> = call {
        api.verifyPhoneNumber(session.userId, com.drakeerv.squeen.data.api.VerifyPhoneRequest(code))
    }.toUnit()

    // ---------------------------------------------------------- locations

    suspend fun locations(force: Boolean = false): ApiResult<List<CustomerGeoBoundary>> =
        getUser(force = force).map { user -> user.customerGeoBoundaries.sortedByDescending { it.isPreferred } }

    private fun cachedUserInternal(): User? =
        session.cachedUserJson?.let { raw ->
            runCatching { JsonApiDocument.of(json.parseToJsonElement(raw)).toUser() }.getOrNull()
        }

    /** Organization + geo boundary context for a location. */
    data class LocationContext(
        val organization: OrganizationInfo?,
        val boundary: GeoBoundary?,
    )

    suspend fun locationContext(geoBoundaryId: String, force: Boolean = false): ApiResult<LocationContext> {
        if (!force) {
            cacheGetFresh("location:$geoBoundaryId", LOCATION_FRESH_MS)?.let { cached ->
                return parseLocationContext(cached, geoBoundaryId)
            }
        }
        val result = call { api.getGeoBoundary(geoBoundaryId) }
        if (result is ApiResult.Success) cachePut("location:$geoBoundaryId", result.data.toString())
        return when (result) {
            is ApiResult.Success -> parseLocationContext(result.data, geoBoundaryId)
            is ApiResult.Failure -> result
        }
    }

    private fun parseLocationContext(data: JsonElement, geoBoundaryId: String): ApiResult<SpeedQueenRepository.LocationContext> {
        val doc = JsonApiDocument.of(data)
        val org = doc.resource()?.toOrganizationInfo()
        val boundary = doc.resource()?.relList("geoBoundaries")?.firstOrNull()
            ?: doc.included.firstOrNull { it.type == "geo-boundaries" }
            ?: doc.lookup("geo-boundaries", geoBoundaryId)

        val mapped = boundary?.toGeoBoundary(doc)?.let { geo -> geo.copy(rooms = collectRooms(doc, boundary)) }
        org?.id?.takeIf { it.isNotBlank() }?.let { session.setOrganizationId(it) }
        session.lastGeoBoundaryId = geoBoundaryId
        return ApiResult.Success(LocationContext(org, mapped))
    }

    /**
     * Rooms hang off the *child* geo boundaries of a location
     * (root boundary → child boundaries → `rooms`), with a flat fallback to every
     * room resource included in the payload.
     */
    private fun collectRooms(doc: JsonApiDocument, boundary: JsonApiResource): List<Room> {
        val direct = boundary.relList("rooms")
        if (direct.isNotEmpty()) return direct.map { it.toRoom(doc) }

        val fromChildren = boundary.relList("geoBoundaries")
            .flatMap { child -> child.relList("rooms") }
            .distinctBy { it.id }
        if (fromChildren.isNotEmpty()) return fromChildren.map { it.toRoom(doc) }

        return doc.included
            .filter { it.type == "rooms" }
            .distinctBy { it.id }
            .map { it.toRoom(doc) }
    }

    suspend fun locationContextByLocationNumber(locationNumber: String): ApiResult<LocationContext> =
        call { api.getGeoBoundaryByLocationNumber(locationNumber) }.map { data ->
            val doc = JsonApiDocument.of(data)
            val org = doc.resource()?.toOrganizationInfo()
            val boundary = doc.resource()?.relList("geoBoundaries")?.firstOrNull()?.toGeoBoundary(doc)
            org?.id?.takeIf { it.isNotBlank() }?.let { session.setOrganizationId(it) }
            LocationContext(org, boundary)
        }

    suspend fun locationContextByValueCenterId(valueCenterId: String): ApiResult<LocationContext> =
        call { api.getGeoBoundaryByValueCenterId(valueCenterId) }.map { data ->
            val doc = JsonApiDocument.of(data)
            val org = doc.resource()?.toOrganizationInfo()
            val boundary = doc.resource()?.relList("geoBoundaries")?.firstOrNull()?.toGeoBoundary(doc)
            org?.id?.takeIf { it.isNotBlank() }?.let { session.setOrganizationId(it) }
            LocationContext(org, boundary)
        }

    /** Registers the current user as customer of the given geo boundary. */
    suspend fun addCustomerGeoBoundary(geoBoundaryId: String, qrCode: String? = null): ApiResult<Unit> = call {
        api.addCustomerGeoBoundary(
            geoBoundaryId,
            CustomerGeoBoundaryRequest(
                id = geoBoundaryId,
                qrCode = qrCode,
                customerId = session.userId,
                createdById = session.userId,
            ),
        )
    }.toUnit()

    suspend fun removeCustomerGeoBoundary(geoBoundaryId: String): ApiResult<Unit> = call {
        api.removeCustomerGeoBoundary(
            geoBoundaryId,
            RemoveCustomerGeoBoundaryRequest(
                id = geoBoundaryId,
                userId = session.userId,
                updatedById = session.userId,
            ),
        )
    }.toUnit()

    suspend fun cachedBoundary(geoBoundaryId: String): GeoBoundary? {
        val doc = JsonApiDocument.of(cacheGet("location:$geoBoundaryId")) ?: return null
        return doc.resource()?.relList("geoBoundaries")?.firstOrNull()?.toGeoBoundary(doc)
            ?: doc.lookup("geo-boundaries", geoBoundaryId)?.toGeoBoundary(doc)
    }

    // --------------------------------------------------------------- rooms

    suspend fun room(roomId: String): ApiResult<Room> {
        return call { api.getRoomByRoomId(roomId) }.map { data ->
            JsonApiDocument.of(data).resource()?.toRoom(JsonApiDocument.of(data))
                ?: Room(
                    id = roomId, name = "", organizationId = null, geoBoundaryId = null, timeZoneName = null,
                    timeZoneAbbreviation = null, hasVtm = false, hasWashAlert = false, canSms = false, hasCoin = false,
                    hidePrimaryAccountBalance = false, isActive = true, subscriptionExpired = false,
                    serviceEmail = null, servicePhone = null, serviceUrl = null, latitude = null,
                    longitude = null, ordinalValue = 0, connectionType = null,
                )
        }
    }

    suspend fun roomMachines(roomId: String, force: Boolean = false): ApiResult<List<Machine>> {
        if (!force) {
            cacheGetFresh("machines:$roomId", MACHINES_FRESH_MS)?.let { cached ->
                return ApiResult.Success(mapMachines(cached))
            }
        }
        val result = call { api.getRoomMachines(roomId) }
        if (result is ApiResult.Success) cachePut("machines:$roomId", result.data.toString())
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(mapMachines(result.data))
            is ApiResult.Failure -> {
                val cached = cacheGet("machines:$roomId")
                if (cached != null) ApiResult.Success(mapMachines(cached)) else result
            }
        }
    }

    private fun mapMachines(data: JsonElement): List<Machine> {
        val doc = JsonApiDocument.of(data)
        return doc.resources().map { it.toMachine() }.sortedBy { it.ordinalValue }
    }

    suspend fun roomMachineByNumber(roomId: String, machineNumber: String?, machineId: String?): ApiResult<Machine> =
        call { api.getRoomMachine(roomId, machineNumber, machineId) }.require("Invalid machine number") { data ->
            JsonApiDocument.of(data).resource()?.toMachine()?.takeIf { it.id.isNotEmpty() }
        }

    // ------------------------------------------------------------- machines

    suspend fun machineCycles(machineId: String): ApiResult<MachineCycles> =
        call { api.getCycles(machineId) }
            .require("This machine is not available right now.") { JsonApiDocument.of(it).resource()?.toMachineCycles() }

    suspend fun price(machineId: String, type: String, connectionToken: String?): ApiResult<MachineCommand> = call {
        api.getPrice(machineId, PriceRequest(createdById = session.userId, type = type, connectionToken = connectionToken))
    }.map { JsonApiDocument.of(it).resource()?.toMachineCommand() ?: MachineCommand(null, null, machineId, null, type) }

    suspend fun setCycles(machineId: String, cycle: String, modifier: String?, connectionToken: String?): ApiResult<MachineCommand> = call {
        api.setCycles(machineId, SetCyclesRequest(CycleParams(cycle = cycle, modifier = modifier), connectionToken))
    }.map { JsonApiDocument.of(it).resource()?.toMachineCommand() ?: MachineCommand(null, null, machineId, null, "SET_CYCLES") }

    suspend fun remoteVend(
        machineId: String,
        userAccountId: String,
        vendAmount: Int,
        connectionToken: String?,
        useRewardsCredit: Boolean = false,
        isPromotionalCredit: Boolean = false,
    ): ApiResult<MachineCommand> = call {
        api.remoteVend(
            machineId,
            VendRequest(
                vendAmount = vendAmount,
                userAccountId = userAccountId,
                isPromotionalCredit = isPromotionalCredit,
                useRewardsCredit = useRewardsCredit,
                connectionToken = connectionToken,
            ),
        )
    }.map { JsonApiDocument.of(it).resource()?.toMachineCommand() ?: MachineCommand(null, null, machineId, null, "REMOTE_VEND") }

    suspend fun remoteTopoff(
        machineId: String,
        userAccountId: String,
        vendAmount: Int,
        connectionToken: String?,
    ): ApiResult<MachineCommand> = call {
        api.remoteTopoff(
            machineId,
            VendRequest(vendAmount = vendAmount, userAccountId = userAccountId, connectionToken = connectionToken),
        )
    }.map { JsonApiDocument.of(it).resource()?.toMachineCommand() ?: MachineCommand(null, null, machineId, null, "REMOTE_TOPOFF") }

    suspend fun machineRewards(machineId: String) = call { api.getRewardsProgramsByMachineId(machineId) }

    // -------------------------------------------------------------- balance

    suspend fun balance(accountId: String, geoBoundaryId: String?): ApiResult<BalanceInfo> =
        call { api.getSubAccount(accountId, geoBoundaryId) }.map { data ->
            JsonApiDocument.of(data).resource()?.let {
                BalanceInfo(
                    amount = it.double("balance") ?: 0.0,
                    promotionalCredit = it.double("promotionalCredit") ?: 0.0,
                    rewardsPoints = it.double("rewardsPoints") ?: 0.0,
                    isActive = it.bool("isActive") ?: true,
                    geoBoundaryId = it.relId("geoBoundary"),
                    userAccountId = it.relId("userAccount"),
                )
            } ?: BalanceInfo(0.0, 0.0, 0.0, false, geoBoundaryId, accountId)
        }

    suspend fun accounts(): ApiResult<List<Account>> =
        call { api.getUserAccounts(session.userId) }.map { data ->
            JsonApiDocument.of(data).resources().map { it.toAccount(JsonApiDocument.of(data)) }
        }

    suspend fun account(accountId: String): ApiResult<Account> =
        call { api.getAccount(accountId) }
            .require("Account not found") { JsonApiDocument.of(it).resource()?.toAccount(JsonApiDocument.of(it)) }

    suspend fun transactions(geoBoundaryId: String?): ApiResult<List<Transaction>> {
        val orgId = session.organizationId
        val result = call {
            api.getAccountTransactions(session.userId, orgId.takeIf { it.isNotEmpty() }, geoBoundaryId, "account-transactions")
        }
        if (result is ApiResult.Success) cachePut("transactions:${session.userId}:$geoBoundaryId", result.data.toString())
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(mapTransactions(result.data))
            is ApiResult.Failure -> {
                val cached = cacheGet("transactions:${session.userId}:$geoBoundaryId")
                if (cached != null) ApiResult.Success(mapTransactions(cached)) else result
            }
        }
    }

    private fun mapTransactions(data: JsonElement): List<Transaction> {
        val doc = JsonApiDocument.of(data)
        return doc.resources()
            .flatMap { resource ->
                if (resource.type == "account-transactions") {
                    listOf(resource)
                } else {
                    resource.relList("accountTransactions")
                }
            }
            .distinctBy { it.id }
            .map { it.toTransaction() }
    }

    suspend fun emailTransaction(transactionId: String, timezone: String): ApiResult<Unit> =
        call { api.emailAccountTransaction(transactionId, EmailTransactionRequest(timezone)) }.toUnit()

    // ------------------------------------------------------------ payments

    suspend fun paymentMethods(accountId: String): ApiResult<List<PaymentMethod>> =
        parseOrNull(callString { api.getAccountPaymentMethods(accountId) }.dataOrNull())
            ?.let { data -> ApiResult.Success(JsonApiDocument.of(data).resources().map { it.toPaymentMethod() }) }
            ?: ApiResult.Success(emptyList())

    private suspend fun callString(block: suspend () -> Response<String>): ApiResult<String> =
        withContext(Dispatchers.IO) {
            try {
                val response = block()
                if (response.isSuccessful) ApiResult.Success(response.body().orEmpty())
                else failureFrom(response.errorBody()?.string(), response.code())
            } catch (io: IOException) {
                ApiResult.Failure("No internet connection. Please check your network and try again.", offline = true, cause = io)
            } catch (t: Throwable) {
                ApiResult.Failure(t.message ?: "Something went wrong.", cause = t)
            }
        }

    suspend fun addPaymentMethod(
        accountId: String,
        paymentNonce: String?,
        paymentType: String?,
        isDefault: Boolean,
        zipCode: String? = null,
        cryptogram: String? = null,
    ): ApiResult<PaymentMethod> = call {
        api.addPaymentMethod(
            accountId,
            AddPaymentMethodRequest(
                userId = session.userId,
                accountId = accountId,
                paymentNonce = paymentNonce,
                isDefault = isDefault,
                zipCode = zipCode,
                creditCardCryptogram = cryptogram,
                financing = paymentType,
            ),
        )
    }.require("The card could not be saved.") { JsonApiDocument.of(it).resource()?.toPaymentMethod() }

    suspend fun removePaymentMethod(paymentMethodId: String): ApiResult<Unit> =
        call { api.removePaymentMethod(paymentMethodId) }.toUnit()

    suspend fun setDefaultPaymentMethod(paymentMethodId: String, isDefault: Boolean): ApiResult<Unit> =
        call { api.setPaymentMethodDefault(paymentMethodId, DefaultFlagRequest(isDefault)) }.toUnit()

    suspend fun addCredit(
        accountId: String,
        amountInCents: Int,
        paymentMethodId: String?,
        stripeToken: String? = null,
        paymentIntent: String? = null,
        isStripe3DSCompatible: Boolean? = null,
        returnUrl: String? = null,
        omiseToken: String? = null,
        cvv: String? = null,
        description: String? = null,
        geoBoundaryId: String? = null,
        connectionToken: String? = null,
    ): ApiResult<Transaction?> = call {
        api.addCredit(
            accountId,
            AddCreditRequest(
                userAccountId = accountId,
                amount = amountInCents,
                paymentMethodId = paymentMethodId,
                stripeToken = stripeToken,
                paymentIntent = paymentIntent,
                isStripe3DSCompatible = isStripe3DSCompatible,
                returnUrl = returnUrl,
                omiseToken = omiseToken,
                cvv = cvv,
                description = description,
                geoBoundaryId = geoBoundaryId,
                userId = session.userId,
                connectionToken = connectionToken,
            ),
        )
    }.map { data ->
        val doc = JsonApiDocument.of(data)
        (doc.resource()?.toTransaction()) ?: doc.resource()?.relList("transactions")?.firstOrNull()?.toTransaction()
    }

    suspend fun replenishmentDefaults(accountId: String, geoBoundaryId: String?): ApiResult<ReplenishmentDefaults?> =
        call { api.getReplenishmentDefaults(accountId, geoBoundaryId) }.map { data ->
            val resource = JsonApiDocument.of(data).resource() ?: return@map null
            resource.toReplenishmentDefaults()
        }

    suspend fun setReplenishmentDefaults(
        accountId: String,
        amount: Int,
        enabled: Boolean,
        geoBoundaryId: String?,
    ): ApiResult<Unit> = call {
        api.setReplenishmentDefaults(
            accountId,
            ReplenishmentDefaultsRequest(amount, enabled, geoBoundaryId),
        )
    }.toUnit()

    suspend fun updateAccount(accountId: String, replenishmentValueAmount: Int, replenishmentMinimumValueAmount: Int): ApiResult<Unit> =
        call { api.updateAccount(accountId, UpdateAccountRequest(replenishmentValueAmount, replenishmentMinimumValueAmount)) }.toUnit()

    suspend fun stripeSetup(userNonce: String?): ApiResult<StripeKeys> =
        call { api.getStripeKeys(StripeSetupRequest(userNonce)) }.map { it.toStripeKeys() }

    suspend fun stripePaymentIntent(amountInCents: Int, paymentMethodTypes: List<String>): ApiResult<StripeKeys> =
        call { api.getStripePaymentIntent(StripePaymentIntentRequest(amountInCents, paymentMethodTypes)) }
            .map { it.toStripeKeys() }

    // ------------------------------------------------------- notifications

    suspend fun notifications(geoBoundaryId: String? = null, roomId: String? = null, unreadOnly: Boolean = false): ApiResult<List<MailboxNotification>> {
        val result = when {
            geoBoundaryId != null -> call { api.getMailboxNotificationsForGeoBoundary(session.userId, geoBoundaryId, unreadOnly) }
            roomId != null -> call { api.getMailboxNotifications(session.userId, roomId) }
            else -> call { api.getMailboxNotificationsPaged(session.userId, null, null, null) }
        }
        return result.map { data -> JsonApiDocument.of(data).resources().map { it.toNotifications() } }
    }

    suspend fun setNotificationRead(id: String, read: Boolean): ApiResult<Unit> =
        call { api.setMailboxNotificationStatus(id, MailboxStatusRequest(read)) }.toUnit()

    suspend fun deleteNotification(id: String): ApiResult<Unit> = call { api.deleteMailboxNotification(id) }.toUnit()

    // -------------------------------------------------------------- rewards

    suspend fun rewardsPrograms(): ApiResult<Unit> = call { api.getRewardsProgram(session.organizationId) }.toUnit()

    suspend fun redeemPromoCode(code: String, roomId: String?): ApiResult<CampaignPromotion?> = call {
        api.redeemPromoCode(
            RedeemPromoRequest(
                userGuid = session.userId,
                promotionCode = code,
                roomId = roomId,
            ),
        )
    }.map { JsonApiDocument.of(it).resource()?.toCampaignPromotion() }

    // ---------------------------------------------------------- wash alerts

    suspend fun washAlerts(): ApiResult<List<WashAlert>> =
        call { api.getWashAlertList(session.userId) }.map { data ->
            JsonApiDocument.of(data).resources().map { it.toWashAlert() }
        }

    suspend fun addWashAlert(machineId: String, roomId: String, email: String?, phone: String?): ApiResult<Unit> = call {
        api.addWashAlert(
            WashAlertRequest(
                userId = session.userId,
                phone = phone,
                email = email,
                roomId = roomId,
                machineId = machineId,
                user = IdRef(session.userId),
                subscriptionConditions = listOf(
                    SubscriptionCondition(intValue = roomId.toIntOrNull(), subscriptionConditionTypeId = 2),
                    SubscriptionCondition(intValue = machineId.toIntOrNull(), subscriptionConditionTypeId = 3),
                    SubscriptionCondition(dateValue = "P0Y0M0DT0H3M0S", subscriptionConditionTypeId = 11),
                ),
            ),
        )
    }.toUnit()

    suspend fun assignMachine(userId: String, machineId: String): ApiResult<Unit> =
        call { api.assignUserMachine(userId, AssignMachineRequest(userId = userId, machineId = machineId)) }.toUnit()

    // ---------------------------------------------------------------- misc

    suspend fun minAppVersion(): ApiResult<ApplicationInfo> =
        call { api.getMinAppVersion() }
            .require("Could not check app version") { JsonApiDocument.of(it).resource()?.toApplicationInfo() }

    suspend fun smsCountries(): ApiResult<List<SmsCountry>> =
        call { api.getSmsCountryCodes() }.map { it.toSmsCountries() }

    /** True when the given international dial code can receive Speed Queen SMS. */
    suspend fun isSmsCountryEnabled(internationalCode: String?): Boolean {
        if (internationalCode.isNullOrBlank()) return false
        val normalized = internationalCode.trimStart('0')
        val countries = (smsCountries() as? ApiResult.Success)?.data.orEmpty()
        return countries.any { it.countryCode.trimStart('0') == normalized }
    }

    /** Personal ScanPay&Wash code (Card link) used to pay at supported machines/kiosks. */
    suspend fun userShortcode(): ApiResult<String?> =
        call { api.getUserShortcodeForQrPayment() }.map { data ->
            data.safeObject()
                ?.get("payload")?.safeObject()
                ?.get("shortcode")?.primitiveContent()
                ?: data.safeObject()?.get("shortcode")?.primitiveContent()
        }

    suspend fun vtmSession(vtmId: String, roomId: String, connectionToken: String?): ApiResult<JsonObject?> =
        call { api.createVtmSession(vtmId, connectionToken, VtmSessionRequest(roomId)) }.map { data ->
            data.safeObject()?.get("data")?.safeObject()?.let { it } ?: data.safeObject()
        }

    /**
     * Hosted payment form HTML for gateways without native support
     * (Payeezy/FirstPay/…). Rendered in a WebView.
     */
    suspend fun paymentsForm(
        accountId: String,
        geoBoundaryId: String?,
        amount: Int? = null,
        oneTime: Boolean = true,
        connectionToken: String? = null,
    ): ApiResult<String> = callString {
        api.getAccountPaymentsForm(accountId, "android", oneTime, amount, geoBoundaryId, connectionToken)
    }

    suspend fun linkTransactAccount(accountId: String, xml: String): ApiResult<Unit> =
        call { api.linkTransactAccount(accountId, TransactLinkRequest(xml)) }.toUnit()

    suspend fun joinCommunityGroup(organizationId: String, name: String): ApiResult<Unit> =
        call { api.joinUserMembershipGroup(organizationId, JoinGroupRequest(name, session.userId)) }.toUnit()

    suspend fun registerPushDevice(): ApiResult<Unit> = call {
        api.registerPushNotification(
            PushDeviceRequest(
                token = session.pushToken,
                userId = session.userId,
                appId = "CUSTOMER_APP_SPEED_QUEEN",
                bindingType = "ANDROID",
            ),
        )
    }.toUnit()

    suspend fun unregisterPushDevice(): ApiResult<Unit> = call {
        api.removePushNotification(
            PushDeviceRequest(
                token = session.pushToken,
                userId = session.userId,
                appId = "CUSTOMER_APP_SPEED_QUEEN",
                bindingType = "ANDROID",
            ),
        )
    }.toUnit()

    /** Amount helper: the API works in minor currency units (cents). */
    fun toMinorUnits(majorUnits: Double, decimalPlaces: Int = 2): Int =
        (majorUnits * Math.pow(10.0, decimalPlaces.toDouble())).toInt()

    fun formatMoney(amount: Double, symbol: String? = "$"): String =
        "%s%.2f".format(symbol ?: "", amount)

    // -------------------------------------------------------- session chips

    fun isLoggedIn(): Boolean = session.isLoggedIn

    fun loginMode(): String = session.loginMode

    fun setLoginMode(mode: String) {
        session.loginMode = mode
    }

    fun sessionOrganizationId(): String = session.organizationId

    fun clearSession() = session.clear()

    suspend fun clearCache() {
        runCatching { cache.deleteOlderThan(Long.MAX_VALUE) }
    }

    companion object {
        val DEFAULT_TIMEOUT = TimeUnit.SECONDS.toMillis(90)

        /** Response cache freshness windows. */
        const val USER_FRESH_MS = 2 * 60 * 1000L
        const val LOCATION_FRESH_MS = 10 * 60 * 1000L
        const val MACHINES_FRESH_MS = 45 * 1000L

        fun resourceId(resource: JsonApiResource): String = resource.id

        fun dataId(element: JsonElement?): String? = element.safeObject()?.get("data")?.safeObject()?.get("id").primitiveContent()
    }
}

private fun <T, R : Any> ApiResult<T>.require(failure: String, transform: (T) -> R?): ApiResult<R> =
    when (this) {
        is ApiResult.Success -> transform(data)?.let { ApiResult.Success(it) } ?: ApiResult.Failure(failure)
        is ApiResult.Failure -> this
    }

private fun ApiResult<*>.toUnit(): ApiResult<Unit> = when (this) {
    is ApiResult.Success -> ApiResult.Success(Unit)
    is ApiResult.Failure -> this
}

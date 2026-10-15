package com.drakeerv.squeen.data.model

import com.drakeerv.squeen.data.jsonapi.JsonApiDocument
import com.drakeerv.squeen.data.jsonapi.JsonApiResource
import com.drakeerv.squeen.data.jsonapi.primitiveBool
import com.drakeerv.squeen.data.jsonapi.primitiveContent
import com.drakeerv.squeen.data.jsonapi.primitiveInt
import com.drakeerv.squeen.data.jsonapi.safeArray
import com.drakeerv.squeen.data.jsonapi.safeObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// ------------------------------------------------------------------- users

fun JsonApiDocument.toUser(): User? = resource()?.toUser(this)

fun JsonApiResource.toUser(document: JsonApiDocument): User {
    val phones = rel("phoneNumbers")?.let { listOf(it.toPhoneNumber()) }
        ?: relList("phoneNumbers").map { it.toPhoneNumber() }
    val emails = relList("emailAddresses").map { EmailAddress(it.id, it.str("emailAddress").orEmpty(), it.bool("isActive") ?: true) }
    val unreadByBoundary: Map<String, Int> = relList("geoBoundaryUnreadMailboxNotifications")
        .associate { notif -> (notif.int("count") ?: 0) to notif.relIds("geoBoundaries") }
        .flatMap { (count, ids) -> ids.map { it to count } }
        .toMap()
    val boundaries = relList("customerGeoBoundaries").map { cb ->
        val geo = cb.rel("geoBoundary")
        val boundaryId = geo?.id ?: cb.relId("geoBoundary").orEmpty()
        CustomerGeoBoundary(
            id = boundaryId,
            name = cb.str("geoBoundaryName") ?: geo?.str("name").orEmpty(),
            organizationName = cb.str("organizationName") ?: geo?.let { document.lookup("organizations", it.str("organizationId").orEmpty()) }?.str("organizationName"),
            organizationId = geo?.str("organizationId"),
            isPreferred = cb.bool("isCustomerPreferredGeoBoundary") ?: false,
            pin = cb.str("pin"),
            hasUnreadNotifications = (unreadByBoundary[boundaryId] ?: 0) > 0
                || (geo?.bool("hasUnreadMailboxNotification") ?: false),
            rooms = geo?.let { boundary ->
                val direct = boundary.relList("rooms")
                if (direct.isNotEmpty()) direct.map { it.toRoom(document) }
                else document.included
                    .filter { it.type == "rooms" && it.relId("geoBoundary") == boundary.id }
                    .map { it.toRoom(document) }
            }.orEmpty(),
        )
    }
    val accounts = relList("accounts").map { it.toAccount(document) }

    return User(
        id = id,
        firstName = str("firstName").orEmpty(),
        lastName = str("lastName").orEmpty(),
        email = emails.firstOrNull()?.email ?: str("email").orEmpty(),
        locale = str("locale"),
        isEmailVerified = bool("isEmailVerified") ?: false,
        hasVerifiedPhoneNumber = bool("hasVerifiedPhoneNumber") ?: false,
        universalOptOutEmail = bool("universalOptOutEmail") ?: false,
        universalOptOutSms = bool("universalOptOutSms") ?: false,
        machineNumber = str("machineNumber"),
        organizationId = relId("organization"),
        organizationName = str("organizationName"),
        phoneNumbers = phones,
        emailAddresses = emails,
        customerGeoBoundaries = boundaries,
        accounts = accounts,
        machines = relList("machines").map { it.toMachine() },
        roles = relList("roles").map {
            UserRole(
                id = it.id,
                firstName = it.str("firstName"),
                lastName = it.str("lastName"),
                organizationName = it.str("organizationName"),
                relationshipType = it.str("relationshipType"),
                isActive = it.bool("userRoleIsActive") ?: (it.bool("isActive") ?: false),
            )
        },
    )
}

fun JsonApiResource.toPhoneNumber(): PhoneNumber = PhoneNumber(
    id = id,
    internationalCode = str("telecomInternationalCode"),
    areaCode = str("telecomAreaCode"),
    contactNumber = str("telecomContactNumber"),
    isVerified = bool("isVerified") ?: false,
    isActive = bool("isActive") ?: true,
)

fun JsonApiResource.toAccount(document: JsonApiDocument): Account = Account(
    id = id,
    name = str("name"),
    userNonce = str("userNonce"),
    organizationId = relId("organization"),
    subAccounts = run {
        val related = relList("subAccounts").map { it.toSubAccount() }
        related
    },
)

fun JsonApiResource.toSubAccount(): SubAccount = SubAccount(
    id = id,
    balance = double("balance") ?: 0.0,
    promotionalCredit = double("promotionalCredit") ?: 0.0,
    rewardsPoints = double("rewardsPoints") ?: 0.0,
    isActive = bool("isActive") ?: true,
    userAccountId = relId("userAccount"),
    geoBoundaryId = relId("geoBoundary"),
    rewardsGroup = str("rewardsGroup"),
    signupBonusTrigger = str("signupBonusTrigger"),
    atriumId = str("atriumId"),
    auxFundsId = str("auxFundsId"),
    appliedAmount = str("appliedAmount"),
    isCredit = bool("isCredit"),
    isDebit = bool("isDebit"),
)

fun JsonApiResource.toBalanceInfo(): BalanceInfo = BalanceInfo(
    amount = double("balance") ?: 0.0,
    promotionalCredit = double("promotionalCredit") ?: 0.0,
    rewardsPoints = double("rewardsPoints") ?: 0.0,
    isActive = bool("isActive") ?: true,
    geoBoundaryId = relId("geoBoundary"),
    userAccountId = relId("userAccount"),
)

// ----------------------------------------------------------------- places

fun JsonApiResource.toGeoBoundary(document: JsonApiDocument): GeoBoundary = GeoBoundary(
    id = id,
    name = str("name").orEmpty(),
    description = str("description"),
    latitude = str("latitude"),
    longitude = str("longitude"),
    organizationId = str("organizationId"),
    hasUnreadMailboxNotification = bool("hasUnreadMailboxNotification") ?: false,
    ord = int("ordinalValue") ?: 0,
    depth = int("depth") ?: 0,
    rewardsProgramId = relId("rewardsProgram"),
    rooms = relList("rooms").map { it.toRoom(document) },
)

fun JsonApiResource.toRoom(document: JsonApiDocument): Room = Room(
    id = id,
    name = str("roomName").orEmpty(),
    organizationId = relId("organization"),
    geoBoundaryId = relId("geoBoundary"),
    timeZoneName = str("timeZoneName"),
    timeZoneAbbreviation = str("internationalTimeZoneAbbreviation"),
    hasVtm = bool("hasVtm") ?: false,
    hasWashAlert = bool("hasWashAlert") ?: false,
    canSms = bool("canSms") ?: false,
    hasCoin = bool("hasCoin") ?: false,
    hidePrimaryAccountBalance = bool("hidePrimaryAccountBalance") ?: false,
    isActive = bool("isActive") ?: true,
    subscriptionExpired = bool("subscriptionExpired") ?: false,
    serviceEmail = str("serviceEmail"),
    servicePhone = str("servicePhoneNumber"),
    serviceUrl = str("serviceUrl"),
    latitude = str("latitude"),
    longitude = str("longitude"),
    ordinalValue = int("ordinalValue") ?: 0,
    connectionType = str("connectionType"),
    banners = relList("roomBanner").map {
        RoomBanner(it.id, it.str("imageUrl"), it.str("bgColorHex"))
    },
    machines = relList("machines").map { it.toMachine() },
)

fun JsonApiResource.toMachine(): Machine = Machine(
    id = id,
    name = str("machineName").orEmpty(),
    number = str("machineNumber").orEmpty(),
    serialNumber = str("serialNumber"),
    isActive = bool("isActive") ?: true,
    isMyMachine = bool("isMyMachine") ?: false,
    ordinalValue = int("ordinalValue") ?: 0,
    machineTypeId = relId("machineType"),
    machineTypeName = rel("machineType")?.str("typeName"),
    isWasher = bool("isWasher") ?: rel("machineType")?.bool("isWasher") ?: false,
    isDryer = bool("isDryer") ?: rel("machineType")?.bool("isDryer") ?: false,
    statusId = rel("currentStatus")?.str("statusId"),
    remainingSeconds = rel("currentStatus")?.int("remainingSeconds") ?: 0,
    remainingVend = rel("currentStatus")?.int("remainingVend") ?: 0,
    topOffTime = rel("currentStatus")?.int("topOffTime") ?: 0,
    topOffVend = rel("currentStatus")?.int("topOffVend") ?: 0,
    canTopOff = rel("currentStatus")?.bool("canTopOff") ?: false,
    roomId = relId("room"),
    organizationId = relId("organization"),
    selectedCycleKey = rel("currentStatus")?.rel("selectedCycle")?.str("name"),
)

fun JsonApiResource.toOrganizationInfo(): OrganizationInfo {
    val currency = rel("currency")
    val gateway = rel("paymentGateway")
    return OrganizationInfo(
        id = id,
        name = str("organizationName"),
        isAtrium = bool("isAtrium") ?: false,
        hasAuxFunds = bool("hasAuxFunds") ?: false,
        hasMailbox = bool("hasMailbox") ?: false,
        isIncludedLaundry = bool("isIncludedLaundry") ?: false,
        isMeteredLaundry = bool("isMeteredLaundry") ?: false,
        meteredBalanceName = str("meteredBalanceName"),
        atriumBalanceName = str("atriumBalanceName"),
        currencySymbol = currency?.str("symbol"),
        currencyDivisor = currency?.double("divisor") ?: 100.0,
        paymentGatewayKey = gateway?.str("lookupKey"),
        paymentGatewayPublicKey = gateway?.str("publicKey"),
    )
}

// --------------------------------------------------------------- machines

fun JsonApiResource.toMachineCycles(): MachineCycles = MachineCycles(
    id = id,
    isMidas = bool("isMidas") ?: false,
    isSupported = bool("isSupported") ?: true,
    cycles = relList("cycles").map { cycle ->
        Cycle(
            id = cycle.id,
            name = cycle.str("name").orEmpty(),
            description = cycle.str("desc"),
            enabled = cycle.bool("enabled") ?: true,
            defaultTemperature = cycle.int("defaultTemperature") ?: 0,
        )
    },
    modifiers = relList("modifiers").map { Modifier(it.id, it.str("desc").orEmpty()) },
)

fun JsonApiResource.toMachineCommand(): MachineCommand = MachineCommand(
    id = id,
    controlId = str("controlId"),
    machineId = str("machineId"),
    roomId = str("roomId"),
    type = str("type"),
)

fun JsonApiResource.toRewardsProgram(): RewardsProgram = RewardsProgram(
    id = id,
    name = str("name"),
    hasRewardsPoints = bool("hasRewardsPoints") ?: false,
    pointsPerWash = int("pointsPerWash"),
    pointsPerDry = int("pointsPerDry"),
    pointsThreshold = int("pointsThreshold"),
    pointsExpirationInterval = str("pointsExpirationInterval"),
)

// --------------------------------------------------------------- payments

fun JsonApiResource.toPaymentMethod(): PaymentMethod = PaymentMethod(
    id = id,
    cardType = str("cardType"),
    lastFour = str("lastFour"),
    expMonth = str("expMonth"),
    expYear = str("expYear"),
    isDefault = bool("isDefault") ?: false,
    paymentNonce = str("paymentNonce"),
)

fun JsonApiResource.toTransaction(): Transaction = Transaction(
    id = id,
    amountText = str("appliedAmount"),
    isCredit = bool("isCredit") ?: false,
    isDebit = bool("isDebit") ?: false,
    isWasher = bool("isWasher") ?: false,
    isDryer = bool("isDryer") ?: false,
    machineName = str("machineName"),
    transactionDate = str("transactionDate"),
    description = rel("transactionDescription")?.str("text"),
    balanceText = str("userAccountBalanceAmount"),
    depositRewardsEarned = int("depositRewardsEarned"),
    pointsRewardsCreditEarned = int("pointsRewardsCreditEarned"),
    promotionalCredit = int("promotionalCredit"),
    promoCode = str("promoCode"),
    typeKey = str("userAccountTransactionTypeLookupKey"),
    appliedAmountIsRewardsCredit = bool("appliedAmountIsRewardsCredit"),
)

fun JsonApiResource.toNotifications(): MailboxNotification = MailboxNotification(
    id = id,
    subject = str("subject"),
    body = str("body"),
    createdAt = str("createdAt"),
    sentAt = str("sentAt"),
    isRead = bool("isRead") ?: false,
    isActive = bool("isActive") ?: true,
    roomId = str("roomId") ?: relId("room"),
    additionalData = str("additionalData"),
    count = int("count") ?: 0,
)

fun JsonApiResource.toWashAlert(): WashAlert = WashAlert(
    id = id,
    email = str("email"),
    phone = str("phone"),
    machineIds = relList("subscriptionMachineConditions").mapNotNull { it.relId("machine") },
    roomIds = relList("subscriptionRoomConditions").mapNotNull { it.relId("room") },
)

fun JsonApiResource.toReplenishmentDefaults(): ReplenishmentDefaults = ReplenishmentDefaults(
    id = id,
    autoReplenishmentEnabled = bool("autoReplenishmentEnabled") ?: false,
    replenishmentValueAmount = int("replenishmentValueAmount") ?: 0,
    replenishmentMinimumValueAmount = int("replenishmentDefaultMinimumValueAmount")
        ?: int("replenishmentMinimumValueAmount") ?: 0,
)

// ------------------------------------------------------------------ misc

fun JsonApiResource.toApplicationInfo(): ApplicationInfo = ApplicationInfo(
    id = id,
    minVersion = str("minVersion"),
    forceUpgradeMessage = str("forceUpgradeMessage"),
)

fun JsonElement?.toStripeKeys(): StripeKeys {
    val obj = this.safeObject() ?: JsonObject(emptyMap())
    return StripeKeys(
        clientSecret = obj["clientSecret"].primitiveContent(),
        ephemeralKey = obj["ephemeralKey"].primitiveContent(),
    )
}

fun JsonElement?.toSmsCountries(): List<SmsCountry> =
    this.safeObject()?.get("countries")?.safeArray()?.mapNotNull { el ->
        val obj = el.safeObject() ?: return@mapNotNull null
        SmsCountry(
            countryCode = obj["countryCode"].primitiveContent().orEmpty(),
            countryName = obj["countryName"].primitiveContent().orEmpty(),
            isoCountryCode = obj["isoCountryCode"].primitiveContent().orEmpty(),
            isoCountryName = obj["isoCountryName"].primitiveContent().orEmpty(),
        )
    } ?: emptyList()

fun JsonApiResource.toCampaignPromotion(): CampaignPromotion = CampaignPromotion(
    id = id,
    promotionTypeLookupKey = str("promotionTypeLookupKey"),
    promotionValue = int("promotionValue"),
)

/** Plain JSON error envelope `{ message, error, statusCode }` used by the API. */
fun JsonElement?.apiErrorMessage(): String? =
    this.safeObject()?.get("message").primitiveContent()

fun JsonElement?.apiErrorCode(): Int? =
    this.safeObject()?.get("statusCode").primitiveInt() ?: this.safeObject()?.get("status").primitiveInt()

@Suppress("unused")
internal fun JsonElement?.boolOrNull(): Boolean? = this.primitiveBool()

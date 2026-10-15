package com.drakeerv.squeen.data.model

/**
 * Domain models, mapped from the JSON:API payloads of the Speed Queen cloud.
 */

data class CustomerGeoBoundary(
    val id: String,
    val name: String,
    val organizationName: String?,
    val organizationId: String?,
    val isPreferred: Boolean,
    val pin: String?,
    val hasUnreadNotifications: Boolean = false,
    val rooms: List<Room> = emptyList(),
) {
    val displayName: String get() = name.ifBlank { organizationName.orEmpty() }
}

data class User(
    val id: String,
    val firstName: String,
    val lastName: String,
    val email: String,
    val locale: String?,
    val isEmailVerified: Boolean,
    val hasVerifiedPhoneNumber: Boolean,
    val universalOptOutEmail: Boolean,
    val universalOptOutSms: Boolean,
    val machineNumber: String?,
    val organizationId: String?,
    val organizationName: String?,
    val phoneNumbers: List<PhoneNumber> = emptyList(),
    val emailAddresses: List<EmailAddress> = emptyList(),
    val customerGeoBoundaries: List<CustomerGeoBoundary> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val machines: List<Machine> = emptyList(),
    val roles: List<UserRole> = emptyList(),
) {
    val fullName: String get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
}

data class EmailAddress(val id: String, val email: String, val isActive: Boolean)

data class PhoneNumber(
    val id: String,
    val internationalCode: String?,
    val areaCode: String?,
    val contactNumber: String?,
    val isVerified: Boolean,
    val isActive: Boolean,
) {
    val formatted: String
        get() = listOfNotNull(internationalCode?.takeIf { it.isNotBlank() }, areaCode?.takeIf { it.isNotBlank() }, contactNumber)
            .joinToString(" ")

    val compact: String
        get() = "+${internationalCode.orEmpty()}${areaCode.orEmpty()}${contactNumber.orEmpty()}"
}

data class UserRole(
    val id: String,
    val firstName: String?,
    val lastName: String?,
    val organizationName: String?,
    val relationshipType: String?,
    val isActive: Boolean,
)

data class Account(
    val id: String,
    val name: String?,
    val userNonce: String?,
    val organizationId: String?,
    val subAccounts: List<SubAccount> = emptyList(),
) {
    fun subAccountFor(geoBoundaryId: String?): SubAccount? =
        subAccounts.firstOrNull { it.geoBoundaryId == geoBoundaryId } ?: subAccounts.firstOrNull()
}

data class SubAccount(
    val id: String,
    val balance: Double,
    val promotionalCredit: Double,
    val rewardsPoints: Double,
    val isActive: Boolean,
    val userAccountId: String?,
    val geoBoundaryId: String?,
    val rewardsGroup: String?,
    val signupBonusTrigger: String?,
    val atriumId: String?,
    val auxFundsId: String?,
    val appliedAmount: String?,
    val isCredit: Boolean?,
    val isDebit: Boolean?,
)

data class GeoBoundary(
    val id: String,
    val name: String,
    val description: String?,
    val latitude: String?,
    val longitude: String?,
    val organizationId: String?,
    val hasUnreadMailboxNotification: Boolean,
    val ord: Int,
    val depth: Int,
    val rewardsProgramId: String?,
    val rooms: List<Room> = emptyList(),
) {
    val displayName: String get() = name.ifBlank { description.orEmpty() }
}

data class Room(
    val id: String,
    val name: String,
    val organizationId: String?,
    val geoBoundaryId: String?,
    val timeZoneName: String?,
    val timeZoneAbbreviation: String?,
    val hasVtm: Boolean,
    val hasWashAlert: Boolean,
    val canSms: Boolean,
    val hasCoin: Boolean,
    val hidePrimaryAccountBalance: Boolean,
    val isActive: Boolean,
    val subscriptionExpired: Boolean,
    val serviceEmail: String?,
    val servicePhone: String?,
    val serviceUrl: String?,
    val latitude: String?,
    val longitude: String?,
    val ordinalValue: Int,
    val connectionType: String?,
    val banners: List<RoomBanner> = emptyList(),
    val machines: List<Machine> = emptyList(),
)

data class RoomBanner(val id: String, val imageUrl: String?, val bgColorHex: String?)

data class Machine(
    val id: String,
    val name: String,
    val number: String,
    val serialNumber: String?,
    val isActive: Boolean,
    val isMyMachine: Boolean,
    val ordinalValue: Int,
    val machineTypeId: String?,
    val machineTypeName: String?,
    val isWasher: Boolean,
    val isDryer: Boolean,
    val statusId: String?,
    val remainingSeconds: Int,
    val remainingVend: Int,
    val topOffTime: Int = 0,
    val topOffVend: Int = 0,
    val canTopOff: Boolean = false,
    val roomId: String?,
    val organizationId: String?,
    val selectedCycleKey: String? = null,
) {
    val displayName: String get() = name.ifBlank { number }

    /** Normalised status key, e.g. AVAILABLE / IN_USE / READY_TO_START. */
    val status: String get() = statusId?.uppercase().orEmpty()

    /**
     * A machine is busy while a cycle is in progress (IN_USE / PARTIAL_VEND) or while it is
     * vended but waiting for the physical start button (READY_TO_START). Note that the API
     * reports the *cycle length* in `remainingSeconds` even when a machine is idle, so the
     * status id, not the countdown, decides availability.
     */
    val isRunning: Boolean get() = status in BUSY_STATUSES

    val isAvailable: Boolean get() = isActive && (status.isEmpty() || status == "AVAILABLE")

    companion object {
        val BUSY_STATUSES = setOf("IN_USE", "PARTIAL_VEND", "READY_TO_START")
    }
}

data class MachineCycles(
    val id: String,
    val isMidas: Boolean,
    val isSupported: Boolean,
    val cycles: List<Cycle>,
    val modifiers: List<Modifier>,
)

data class Cycle(
    val id: String,
    val name: String,
    val description: String?,
    val enabled: Boolean,
    val defaultTemperature: Int,
)

data class Modifier(val id: String, val description: String)

data class MachineCommand(
    val id: String?,
    val controlId: String?,
    val machineId: String?,
    val roomId: String?,
    val type: String?,
)

data class PaymentMethod(
    val id: String,
    val cardType: String?,
    val lastFour: String?,
    val expMonth: String?,
    val expYear: String?,
    val isDefault: Boolean,
    val paymentNonce: String?,
) {
    val display: String
        get() = buildString {
            append(cardType?.replaceFirstChar { it.uppercase() } ?: "Card")
            lastFour?.let { append(" •••• $it") }
        }
}

data class Transaction(
    val id: String,
    val amountText: String?,
    val isCredit: Boolean,
    val isDebit: Boolean,
    val isWasher: Boolean,
    val isDryer: Boolean,
    val machineName: String?,
    val transactionDate: String?,
    val description: String?,
    val balanceText: String?,
    val depositRewardsEarned: Int?,
    val pointsRewardsCreditEarned: Int?,
    val promotionalCredit: Int?,
    val promoCode: String?,
    val typeKey: String?,
    val appliedAmountIsRewardsCredit: Boolean?,
)

data class MailboxNotification(
    val id: String,
    val subject: String?,
    val body: String?,
    val createdAt: String?,
    val sentAt: String?,
    val isRead: Boolean,
    val isActive: Boolean,
    val roomId: String?,
    val additionalData: String?,
    val count: Int,
)

data class WashAlert(
    val id: String,
    val email: String?,
    val phone: String?,
    val machineIds: List<String>,
    val roomIds: List<String>,
)

data class ApplicationInfo(
    val id: String,
    val minVersion: String?,
    val forceUpgradeMessage: String?,
)

data class StripeKeys(val clientSecret: String?, val ephemeralKey: String?)

data class SmsCountry(
    val countryCode: String,
    val countryName: String,
    val isoCountryCode: String,
    val isoCountryName: String,
)

data class CampaignPromotion(
    val id: String,
    val promotionTypeLookupKey: String?,
    val promotionValue: Int?,
)

data class ReplenishmentDefaults(
    val id: String?,
    val autoReplenishmentEnabled: Boolean,
    val replenishmentValueAmount: Int,
    val replenishmentMinimumValueAmount: Int,
)

data class RewardsProgram(
    val id: String,
    val name: String?,
    val hasRewardsPoints: Boolean,
    val pointsPerWash: Int?,
    val pointsPerDry: Int?,
    val pointsThreshold: Int?,
    val pointsExpirationInterval: String?,
)

data class OrganizationInfo(
    val id: String,
    val name: String?,
    val isAtrium: Boolean,
    val hasAuxFunds: Boolean,
    val hasMailbox: Boolean,
    val isIncludedLaundry: Boolean,
    val isMeteredLaundry: Boolean,
    val meteredBalanceName: String?,
    val atriumBalanceName: String?,
    val currencySymbol: String?,
    val currencyDivisor: Double,
    val paymentGatewayKey: String?,
    val paymentGatewayPublicKey: String?,
)

data class BalanceInfo(
    val amount: Double,
    val promotionalCredit: Double,
    val rewardsPoints: Double,
    val isActive: Boolean,
    val geoBoundaryId: String?,
    val userAccountId: String?,
)

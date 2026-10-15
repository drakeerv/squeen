package com.drakeerv.squeen.data.api

import kotlinx.serialization.Serializable

/**
 * Request / auxiliary bodies mirroring the DTOs used by the original app.
 * All fields are nullable and serialized with `explicitNulls = false`, matching
 * the Gson behaviour of the original client (nulls are omitted).
 */

@Serializable
data class IdRef(val id: String? = null)

@Serializable
data class PhoneRef(
    val telecomInternationalCode: String? = null,
    val telecomAreaCode: String? = null,
    val telecomContactNumber: String? = null,
)

@Serializable
data class LoginRequest(
    val email: String? = null,
    val telecomInternationalCode: String? = null,
    val telecomAreaCode: String? = null,
    val telecomContactNumber: String? = null,
    val password: String? = null,
    val littleDuck: String? = null,
)

@Serializable
data class RegisterRequest(
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    val password: String? = null,
    val telecomInternationalCode: String? = null,
    val telecomAreaCode: String? = null,
    val telecomContactNumber: String? = null,
    val universalOptOutSms: Boolean = false,
    val universalOptOutEmail: Boolean = false,
    val littleDuck: String? = null,
)

@Serializable
data class ResetPasswordRequest(
    val email: String? = null,
    val telecomInternationalCode: String? = null,
    val telecomAreaCode: String? = null,
    val telecomContactNumber: String? = null,
)

@Serializable
data class ChangePasswordRequest(
    val userId: String,
    val currentPassword: String,
    val newPassword: String,
)

@Serializable
data class VerifyEmailRequest(
    val code: String,
    val updatedById: String? = null,
    val updatedBy: IdRef? = null,
)

@Serializable
data class VerifyPhoneRequest(val code: String)

@Serializable
data class AddPhoneNumberRequest(
    val internationalCode: String? = null,
    val areaCode: String? = null,
    val contactNumber: String? = null,
    val userId: String? = null,
)

@Serializable
data class PhoneVerifyRequest(
    val countryCode: String? = null,
    val phoneNumber: String? = null,
)

@Serializable
data class AddCreditRequest(
    val userAccountId: String? = null,
    val amount: Int? = null,
    val paymentMethodId: String? = null,
    val stripeToken: String? = null,
    val paymentIntent: String? = null,
    val isStripe3DSCompatible: Boolean? = null,
    val returnUrl: String? = null,
    val omiseToken: String? = null,
    val geoBoundaryId: String? = null,
    val userId: String? = null,
    val cvv: String? = null,
    val description: String? = null,
    val deviceInfo: String? = null,
    val connectionToken: String? = null,
)

@Serializable
data class AddPaymentMethodRequest(
    val userId: String? = null,
    val accountId: String? = null,
    val creditCardCryptogram: String? = null,
    val isDefault: Boolean? = null,
    val financing: String? = null,
    val paymentNonce: String? = null,
    val zipCode: String? = null,
)

@Serializable
data class StripeSetupRequest(val userNonce: String? = null)

@Serializable
data class StripePaymentIntentRequest(
    val amount: Int? = null,
    val paymentMethodTypes: List<String>? = null,
)

@Serializable
data class VendRequest(
    val vendAmount: Int? = null,
    val userAccountId: String? = null,
    val isPromotionalCredit: Boolean? = null,
    val useRewardsCredit: Boolean? = null,
    val connectionToken: String? = null,
)

@Serializable
data class PriceRequest(
    val createdById: String? = null,
    val type: String? = null,
    val connectionToken: String? = null,
)

@Serializable
data class CycleParams(
    val cycle: String? = null,
    val modifier: String? = null,
)

@Serializable
data class SetCyclesRequest(
    val params: CycleParams,
    val connectionToken: String? = null,
)

@Serializable
data class AssignMachineRequest(
    val userId: String? = null,
    val machineId: String? = null,
)

@Serializable
data class SubscriptionCondition(
    val dateValue: String? = null,
    val intValue: Int? = null,
    val subscriptionConditionTypeId: Int? = null,
)

@Serializable
data class WashAlertRequest(
    val userId: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val roomId: String? = null,
    val machineId: String? = null,
    val user: IdRef? = null,
    val subscriptionConditions: List<SubscriptionCondition>? = null,
)

@Serializable
data class CampaignStatsRequest(
    val messageId: String? = null,
    val userId: String? = null,
    val runId: String? = null,
    val organizationId: String? = null,
    val type: String? = null,
    val deliveryMethodLookupKey: String? = null,
)

@Serializable
data class JoinGroupRequest(
    val name: String? = null,
    val userId: String? = null,
)

@Serializable
data class TouchNetLinkRequest(
    val username: String? = null,
    val password: String? = null,
)

@Serializable
data class CbordLinkRequest(val paymentToken: String? = null)

@Serializable
data class TransactLinkRequest(val xml: String? = null)

@Serializable
data class PushDeviceRequest(
    val token: String? = null,
    val userId: String? = null,
    val appId: String? = null,
    val bindingType: String? = null,
)

@Serializable
data class ForceLogoutRequest(val littleDuck: String? = null)

@Serializable
data class MailboxStatusRequest(val isRead: Boolean? = null)

@Serializable
data class DefaultFlagRequest(val isDefault: Boolean? = null)

@Serializable
data class ReplenishmentDefaultsRequest(
    val replenishmentValueAmount: Int? = null,
    val autoReplenishmentEnabled: Boolean? = null,
    val geoBoundaryId: String? = null,
)

@Serializable
data class UpdateAccountRequest(
    val replenishmentValueAmount: Int? = null,
    val replenishmentMinimumValueAmount: Int? = null,
)

@Serializable
data class UpdateUserRequest(
    val id: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    val universalOptOutSms: Boolean = false,
    val universalOptOutEmail: Boolean = false,
    val phone: PhoneRef? = null,
    val locale: String? = null,
)

@Serializable
data class RedeemPromoRequest(
    val userGuid: String? = null,
    val promotionCode: String? = null,
    val roomId: String? = null,
)

@Serializable
data class EmailTransactionRequest(val timezone: String? = null)

@Serializable
data class VtmSessionRequest(val roomId: String? = null)

@Serializable
data class CustomerGeoBoundaryRequest(
    val id: String? = null,
    val qrCode: String? = null,
    val customerId: String? = null,
    val createdById: String? = null,
)

@Serializable
data class RemoveCustomerGeoBoundaryRequest(
    val id: String? = null,
    val userId: String? = null,
    val updatedById: String? = null,
    val customers: List<IdRef>? = null,
)

package com.drakeerv.squeen.data.api

import kotlinx.serialization.json.JsonElement
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Speed Queen / Alliance Laundry cloud API, as reverse engineered from the original client.
 *
 *  - base URL: https://api.alliancelslabs.com/
 *  - auth: `alliancels-auth-token` header, captured from the login/register/verify responses
 *  - status errors are plain JSON `{ "message": "...", "error": "...", "statusCode": n }`
 *  - resources are JSON:API 1.0 documents (see [com.drakeerv.squeen.data.jsonapi])
 */
interface SpeedQueenApi {

    // ---------------------------------------------------------------- auth

    @Headers(JSON_CONTENT)
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @POST("auth/register")
    suspend fun registerCustomer(@Body body: RegisterRequest): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @POST("auth/resetPassword")
    suspend fun resetPassword(
        @Query("appType") appType: String?,
        @Body body: ResetPasswordRequest,
    ): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @POST("auth/changePassword")
    suspend fun changePassword(@Body body: ChangePasswordRequest): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @POST("users/{userId}/verifyEmail/resend")
    suspend fun resendVerifyEmail(@Path("userId") userId: String): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @POST("users/{userId}/verifyEmail")
    suspend fun verifyEmail(
        @Path("userId") userId: String,
        @Body body: VerifyEmailRequest,
    ): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @POST("users/{userId}/requestDeletion")
    suspend fun requestAccountDeletion(@Path("userId") userId: String): Response<JsonElement>

    // --------------------------------------------------------------- users

    @Headers(JSON_CONTENT)
    @GET("users/{userId}?include=all")
    suspend fun getUser(@Path("userId") userId: String): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @PATCH("users/{userId}")
    suspend fun updateUserProfile(
        @Path("userId") userId: String,
        @Body body: UpdateUserRequest,
    ): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @GET("users/{userId}/phoneNumbers")
    suspend fun getUserPhoneNumbers(@Path("userId") userId: String): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @POST("phoneNumbers")
    suspend fun addPhoneNumber(@Body body: AddPhoneNumberRequest): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @PATCH("phoneNumbers/{phoneNumberId}")
    suspend fun updatePhoneNumber(
        @Path("phoneNumberId") phoneNumberId: String,
        @Body body: AddPhoneNumberRequest,
    ): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @DELETE("phoneNumbers/{phoneNumberId}")
    suspend fun removePhoneNumber(@Path("phoneNumberId") phoneNumberId: String): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @POST("users/{userId}/phone/verify")
    suspend fun addPhoneNumberAndVerify(
        @Path("userId") userId: String,
        @Body body: PhoneVerifyRequest,
    ): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @PATCH("users/{userId}/phone/verify")
    suspend fun verifyPhoneNumber(
        @Path("userId") userId: String,
        @Body body: VerifyPhoneRequest,
    ): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @GET("users/shortcode")
    suspend fun getUserShortcodeForQrPayment(): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @GET("users/{userId}/machines")
    suspend fun getUserMachines(@Path("userId") userId: String): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @GET("users/{userId}/machines")
    suspend fun getUserRoomMachines(
        @Path("userId") userId: String,
        @Query("roomId") roomId: String?,
    ): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @POST("users/{userId}/machines")
    suspend fun assignUserMachine(
        @Path("userId") userId: String,
        @Body body: AssignMachineRequest,
    ): Response<JsonElement>

    // ------------------------------------------------------------ accounts

    @Headers(JSON_CONTENT)
    @GET("users/{userId}/accounts?include=sub-accounts")
    suspend fun getUserAccounts(@Path("userId") userId: String): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @GET("users/{userId}/accounts")
    suspend fun getAccountTransactions(
        @Path("userId") userId: String,
        @Query("organizationId") organizationId: String?,
        @Query("geoBoundaryId") geoBoundaryId: String?,
        @Query("include") include: String?,
    ): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @GET("accounts/{accountId}?include=sub-accounts")
    suspend fun getAccount(@Path("accountId") accountId: String): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @PATCH("accounts/{accountId}")
    suspend fun updateAccount(
        @Path("accountId") accountId: String,
        @Body body: UpdateAccountRequest,
    ): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @GET("accounts/{accountId}/balance")
    suspend fun getSubAccount(
        @Path("accountId") accountId: String,
        @Query("geoBoundaryId") geoBoundaryId: String?,
    ): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @GET("accountCreditValues")
    suspend fun getAccountCreditValues(@Query("organizationId") organizationId: String?): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @GET("accounts/{accountId}/atrium")
    suspend fun getAtrium(@Path("accountId") accountId: String): Response<String>

    @Headers(JSON_CONTENT)
    @DELETE("accounts/{accountId}/auxFunds")
    suspend fun unlinkAuxFundsAccount(@Path("accountId") accountId: String): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @POST("accounts/{accountId}/addCredit")
    suspend fun addCredit(
        @Path("accountId") accountId: String,
        @Body body: AddCreditRequest,
    ): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @POST("accounts/{accountId}/addCredit")
    suspend fun addCreditPlacetoPay(
        @Path("accountId") accountId: String,
        @Body body: AddCreditRequest,
    ): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @GET("accounts/{accountId}/paymentsForm")
    suspend fun getAccountPaymentsForm(
        @Path("accountId") accountId: String,
        @Query("deviceType") deviceType: String?,
        @Query("isOneTime") isOneTime: Boolean?,
        @Query("amount") amount: Int?,
        @Query("geoBoundaryId") geoBoundaryId: String?,
        @Query("connectionToken") connectionToken: String?,
    ): Response<String>

    @Headers(JSON_CONTENT)
    @GET("payeezy/form")
    suspend fun getPayeezyPaymentsForm(@Query("userAccountId") userAccountId: String?): Response<String>

    @Headers(JSON_CONTENT)
    @POST("accounts/{accountId}/touchnet/link")
    suspend fun connectTouchNetAccount(
        @Path("accountId") accountId: String,
        @Body body: TouchNetLinkRequest,
    ): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @POST("accounts/{accountId}/cbord/link")
    suspend fun linkCbordAccount(
        @Path("accountId") accountId: String,
        @Body body: CbordLinkRequest,
    ): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @POST("accounts/{accountId}/transact/link")
    suspend fun linkTransactAccount(
        @Path("accountId") accountId: String,
        @Body body: TransactLinkRequest,
    ): Response<JsonElement>

    // ------------------------------------------------------ payment methods

    @Headers(JSON_CONTENT)
    @GET("accounts/{accountId}/paymentMethods")
    suspend fun getAccountPaymentMethods(@Path("accountId") accountId: String): Response<String>

    @Headers(JSON_CONTENT)
    @POST("accounts/{accountId}/paymentMethods")
    suspend fun addPaymentMethod(
        @Path("accountId") accountId: String,
        @Body body: AddPaymentMethodRequest,
    ): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @DELETE("paymentMethods/{paymentMethodId}")
    suspend fun removePaymentMethod(@Path("paymentMethodId") paymentMethodId: String): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @PATCH("paymentMethods/{paymentMethodId}")
    suspend fun setPaymentMethodDefault(
        @Path("paymentMethodId") paymentMethodId: String,
        @Body body: DefaultFlagRequest,
    ): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @GET("accounts/{accountId}/replenishmentDefaults")
    suspend fun getReplenishmentDefaults(
        @Path("accountId") accountId: String,
        @Query("geoBoundaryId") geoBoundaryId: String?,
    ): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @PATCH("accounts/{accountId}/replenishmentDefaults")
    suspend fun setReplenishmentDefaults(
        @Path("accountId") accountId: String,
        @Body body: ReplenishmentDefaultsRequest,
    ): Response<JsonElement>

    // --------------------------------------------------------------- stripe

    @Headers(JSON_CONTENT)
    @POST("stripe/intents/setup")
    suspend fun getStripeKeys(@Body body: StripeSetupRequest): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @POST("stripe/intents/payment")
    suspend fun getStripePaymentIntent(@Body body: StripePaymentIntentRequest): Response<JsonElement>

    // ------------------------------------------------------------ machines

    @Headers(JSON_CONTENT)
    @GET("rooms/{roomId}/machines")
    suspend fun getRoomMachines(@Path("roomId") roomId: String): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @GET("rooms/{roomId}/machinesMobile")
    suspend fun getRoomMachine(
        @Path("roomId") roomId: String,
        @Query("machineNumber") machineNumber: String?,
        @Query("machineId") machineId: String?,
    ): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @GET("machines/{machineId}/cycles")
    suspend fun getCycles(@Path("machineId") machineId: String): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @POST("machines/{machineId}/getPrice")
    suspend fun getPrice(
        @Path("machineId") machineId: String,
        @Body body: PriceRequest,
    ): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @POST("machines/{machineId}/remoteVend")
    suspend fun remoteVend(
        @Path("machineId") machineId: String,
        @Body body: VendRequest,
    ): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @POST("machines/{machineId}/remoteTopoff")
    suspend fun remoteTopoff(
        @Path("machineId") machineId: String,
        @Body body: VendRequest,
    ): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @POST("machines/{machineId}/setCycleSelections")
    suspend fun setCycles(
        @Path("machineId") machineId: String,
        @Body body: SetCyclesRequest,
    ): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @GET("machines/{machineId}/rewards")
    suspend fun getRewardsProgramsByMachineId(@Path("machineId") machineId: String): Response<JsonElement>

    // --------------------------------------------------------------- rooms

    @Headers(JSON_CONTENT)
    @GET("rooms/{roomId}")
    suspend fun getRoomByRoomId(@Path("roomId") roomId: String): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @POST("vtm/{vtmId}/session")
    suspend fun createVtmSession(
        @Path("vtmId") vtmId: String,
        @Query("connectionToken") connectionToken: String?,
        @Body body: VtmSessionRequest,
    ): Response<JsonElement>

    // --------------------------------------------------------- geoBoundary

    @Headers(JSON_CONTENT)
    @GET("geoBoundaries/{geoBoundaryId}")
    suspend fun getGeoBoundary(@Path("geoBoundaryId") geoBoundaryId: String): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @GET("geoBoundaries")
    suspend fun getGeoBoundaryByLocationNumber(
        @Query("locationNumber") locationNumber: String?,
    ): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @GET("geoBoundaries")
    suspend fun getGeoBoundaryByValueCenterId(
        @Query("valueCenterId") valueCenterId: String?,
    ): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @PATCH("geoBoundaries/{geoBoundaryId}/customers")
    suspend fun addCustomerGeoBoundary(
        @Path("geoBoundaryId") geoBoundaryId: String,
        @Body body: CustomerGeoBoundaryRequest,
    ): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @HTTP(method = "DELETE", path = "geoBoundaries/{geoBoundaryId}/customers", hasBody = true)
    suspend fun removeCustomerGeoBoundary(
        @Path("geoBoundaryId") geoBoundaryId: String,
        @Body body: RemoveCustomerGeoBoundaryRequest,
    ): Response<JsonElement>

    // ----------------------------------------------------------- rewards

    @Headers(JSON_CONTENT)
    @GET("rewardsPrograms")
    suspend fun getRewardsProgram(@Query("organizationId") organizationId: String?): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @GET("rewardsPrograms")
    suspend fun getRewardsProgramsByGeoBoundaryId(
        @Query("geoBoundaryId") geoBoundaryId: String?,
    ): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @GET("pointsExpirationIntervals")
    suspend fun getPointsExpirationIntervals(): Response<JsonElement>

    // ------------------------------------------------------- notifications

    @Headers(JSON_CONTENT)
    @GET("users/{userId}/mailboxNotifications")
    suspend fun getMailboxNotifications(
        @Path("userId") userId: String,
        @Query("roomId") roomId: String?,
    ): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @GET("users/{userId}/mailboxNotifications")
    suspend fun getMailboxNotificationsPaged(
        @Path("userId") userId: String,
        @Query("roomId") roomId: String?,
        @Query("isPreview") isPreview: Boolean?,
        @Query("isRead") isRead: Boolean?,
    ): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @GET("users/{userId}/mailboxNotifications")
    suspend fun getMailboxNotificationsForGeoBoundary(
        @Path("userId") userId: String,
        @Query("geoBoundaryId") geoBoundaryId: String?,
        @Query("isRead") isRead: Boolean?,
    ): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @GET("mailboxNotifications/{mailboxNotificationId}")
    suspend fun getMailboxNotification(
        @Path("mailboxNotificationId") mailboxNotificationId: String,
    ): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @PATCH("mailboxNotifications/{mailboxNotificationId}")
    suspend fun setMailboxNotificationStatus(
        @Path("mailboxNotificationId") mailboxNotificationId: String,
        @Body body: MailboxStatusRequest,
    ): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @DELETE("mailboxNotifications/{mailboxNotificationId}")
    suspend fun deleteMailboxNotification(
        @Path("mailboxNotificationId") mailboxNotificationId: String,
    ): Response<JsonElement>

    // -------------------------------------------------------------- devices

    @Headers(JSON_CONTENT)
    @POST("devices")
    suspend fun registerPushNotification(@Body body: PushDeviceRequest): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @HTTP(method = "DELETE", path = "devices", hasBody = true)
    suspend fun removePushNotification(@Body body: PushDeviceRequest): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @HTTP(method = "DELETE", path = "devices", hasBody = true)
    suspend fun removeAllPushNotifications(@Body body: PushDeviceRequest): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @POST("devices/logout")
    suspend fun setForceLogout(@Body body: ForceLogoutRequest): Response<JsonElement>

    // ------------------------------------------------------------ wash alerts

    @Headers(JSON_CONTENT)
    @POST("subscriptionTemplates/96/subscriptions")
    suspend fun addWashAlert(@Body body: WashAlertRequest): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @GET("users/{userId}/subscriptions?eventTypeLookupKey=WASH_ALERT")
    suspend fun getWashAlertList(@Path("userId") userId: String): Response<JsonElement>

    // ------------------------------------------------------------- misc

    @Headers(JSON_CONTENT)
    @GET("applications/CUSTOMER_APP_ANDROID")
    suspend fun getMinAppVersion(): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @GET("organizations/{organizationId}/groups?raw=true")
    suspend fun getCommunityGroups(@Path("organizationId") organizationId: String): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @GET("organizations/{organizationId}/users/{userId}/groups?raw=true")
    suspend fun getUserMembershipGroups(
        @Path("organizationId") organizationId: String,
        @Path("userId") userId: String,
    ): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @POST("organizations/{organizationId}/groups/join?raw=true")
    suspend fun joinUserMembershipGroup(
        @Path("organizationId") organizationId: String,
        @Body body: JoinGroupRequest,
    ): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @DELETE("organizations/{organizationId}/users/{userId}/groups/{groupId}?raw=true")
    suspend fun removeUserMembershipGroup(
        @Path("organizationId") organizationId: String,
        @Path("userId") userId: String,
        @Path("groupId") groupId: Int,
    ): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @POST("campaigns/{campaignId}/stats")
    suspend fun campaignsStats(
        @Path("campaignId") campaignId: String,
        @Body body: CampaignStatsRequest,
    ): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @PATCH("promotionCodes/redeem")
    suspend fun redeemPromoCode(@Body body: RedeemPromoRequest): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @POST("accountTransactions/{accountTransactionId}/email")
    suspend fun emailAccountTransaction(
        @Path("accountTransactionId") accountTransactionId: String,
        @Body body: EmailTransactionRequest,
    ): Response<JsonElement>

    @Headers(JSON_CONTENT)
    @GET("sms/enabled-countries?raw=true")
    suspend fun getSmsCountryCodes(): Response<JsonElement>

    companion object {
        const val JSON_CONTENT = "Content-Type: application/json"
    }
}

package com.drakeerv.squeen.ui.navigation

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val SIGN_UP = "sign_up"
    const val FORGOT_PASSWORD = "forgot_password"
    const val VERIFY_EMAIL = "verify_email"

    const val MAIN = "main"
    const val ADD_LOCATION = "add_location"
    const val FIND_KIOSK = "find_kiosk"
    const val KIOSK_DEPOSIT = "kiosk_deposit/{vtmId}?roomId={roomId}"

    const val ROOM_LIST = "rooms/{geoBoundaryId}"
    const val ROOM = "room/{roomId}"
    const val ADD_MACHINE = "add_machine/{roomId}"
    const val MACHINE = "machine/{roomId}/{machineId}"

    const val PAYMENT_METHODS = "payment_methods/{accountId}"
    const val PAYMENT_FORM = "payment_form/{accountId}/{geoBoundaryId}"
    const val ADD_CREDIT = "add_credit/{accountId}/{geoBoundaryId}"
    const val AUTO_RECHARGE = "auto_recharge/{accountId}/{geoBoundaryId}"
    const val TRANSACTIONS = "transactions"
    const val CARD_LINK = "card_link"

    const val PROFILE = "profile"
    const val CHANGE_PASSWORD = "change_password"
    const val NOTIFICATIONS = "notifications"
    const val PROMOTIONS = "promotions"
    const val SETTINGS = "settings"
    const val WASH_ALERTS = "wash_alerts"

    const val QR_SCAN = "qr_scan/{mode}"
    const val WEBVIEW = "webview?title={title}&url={url}&accountId={accountId}&kind={kind}"

    fun roomList(geoBoundaryId: String) = "rooms/$geoBoundaryId"
    fun room(roomId: String) = "room/$roomId"
    fun addMachine(roomId: String) = "add_machine/$roomId"
    fun machine(roomId: String, machineId: String) = "machine/$roomId/$machineId"
    fun paymentMethods(accountId: String) = "payment_methods/$accountId"
    fun paymentForm(accountId: String, geoBoundaryId: String) = "payment_form/$accountId/$geoBoundaryId"
    fun addCredit(accountId: String, geoBoundaryId: String) = "add_credit/$accountId/$geoBoundaryId"
    fun autoRecharge(accountId: String, geoBoundaryId: String) = "auto_recharge/$accountId/$geoBoundaryId"
    fun qrScan(mode: QrMode) = "qr_scan/${mode.name}"
    fun kioskDeposit(vtmId: String, roomId: String?) =
        "kiosk_deposit/${android.net.Uri.encode(vtmId)}?roomId=${android.net.Uri.encode(roomId.orEmpty())}"
    fun webView(title: String, url: String, accountId: String = "", kind: String = "web") =
        "webview?title=${android.net.Uri.encode(title)}&url=${android.net.Uri.encode(url)}&accountId=$accountId&kind=$kind"
}

enum class QrMode { LOCATION, MACHINE, KIOSK }

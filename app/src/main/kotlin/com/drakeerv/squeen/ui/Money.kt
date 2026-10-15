package com.drakeerv.squeen.ui

/**
 * Currency formatting helpers.
 *
 * The API mixes representations: `appliedAmount` / `userAccountBalanceAmount` arrive as
 * strings that are usually minor units ("300" = $3.00) but can already be decimal
 * ("3.00"); machine vend amounts and balances are plain minor units; balances come as
 * major units in doubles.
 */
object Money {

    /** Formats an amount string coming from the API (minor units unless it has a decimal point). */
    fun format(raw: String?, symbol: String = "$", fallback: String = ""): String {
        if (raw.isNullOrBlank()) return fallback
        val trimmed = raw.trim()
        val negative = trimmed.startsWith("-")
        val body = trimmed.removePrefix("-").removePrefix("+")
        val sign = if (negative) "-" else ""
        if (body.isEmpty()) return fallback

        return if (body.contains('.') || body.contains(',')) {
            val normalized = body.replace(',', '.')
            val decimals = normalized.substringAfter('.', "")
            val value = if (decimals.length == 1) "$normalized" + "0" else normalized
            "$sign$symbol$value"
        } else {
            val minor = body.toLongOrNull() ?: return "$sign$symbol$body"
            "$sign$symbol%d.%02d".format(minor / 100, minor % 100)
        }
    }

    /** Formats minor units (ints) as currency. */
    fun fromMinorUnits(minor: Int, symbol: String = "$"): String {
        val negative = minor < 0
        val value = kotlin.math.abs(minor.toLong())
        return (if (negative) "-" else "") + "$symbol%d.%02d".format(value / 100, value % 100)
    }

    /** Formats major units (doubles) as currency. */
    fun fromMajorUnits(amount: Double, symbol: String = "$"): String {
        val negative = amount < 0
        return (if (negative) "-" else "") + "$symbol%.2f".format(kotlin.math.abs(amount))
    }
}

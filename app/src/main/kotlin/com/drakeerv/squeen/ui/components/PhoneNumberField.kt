package com.drakeerv.squeen.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Phone number input used by sign-in, sign-up and profile.
 *
 * Two well-proportioned fields: a compact country-code field and the national
 * number; the area code required by the API is split off automatically
 * (first three digits for 10-digit North-American numbers, otherwise the whole
 * number is sent and the server uses the international format).
 */
data class PhoneInput(
    val internationalCode: String,
    val areaCode: String,
    val contactNumber: String,
)

fun splitPhone(countryCode: String, nationalNumber: String): PhoneInput {
    val digits = nationalNumber.filter(Char::isDigit)
    return when {
        digits.length == 10 -> PhoneInput(countryCode.ifBlank { "1" }, digits.take(3), digits.drop(3))
        digits.length > 10 -> PhoneInput(countryCode.ifBlank { "1" }, digits.take(3), digits.drop(3))
        else -> PhoneInput(countryCode.ifBlank { "1" }, "", digits)
    }
}

@Composable
fun PhoneNumberField(
    countryCode: String,
    onCountryCodeChange: (String) -> Unit,
    number: String,
    onNumberChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    label: String = "Phone number",
    optional: Boolean = false,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = countryCode,
                onValueChange = { onCountryCodeChange(it.filter(Char::isDigit).take(3)) },
                singleLine = true,
                enabled = enabled,
                isError = isError,
                label = { Text("+") },
                textStyle = MaterialTheme.typography.bodyLarge,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                modifier = Modifier.width(96.dp),
            )
            Spacer(Modifier.width(10.dp))
            OutlinedTextField(
                value = number,
                onValueChange = { onNumberChange(it.filter { ch -> ch.isDigit() || ch in " ()-+" }.take(15)) },
                label = { Text(label + if (optional) " (optional)" else "") },
                singleLine = true,
                enabled = enabled,
                isError = isError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                modifier = Modifier.weight(1f),
            )
        }
        val digits = number.filter(Char::isDigit)
        if (digits.length >= 7) {
            val split = splitPhone(countryCode, digits)
            val pretty = buildString {
                if (split.areaCode.isNotEmpty()) {
                    append("+").append(split.internationalCode).append(" ")
                    append("(").append(split.areaCode).append(") ").append(split.contactNumber)
                }
            }
            if (pretty.isNotBlank()) {
                Text(
                    pretty,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )
            }
        }
    }
}

package com.moneromerchant.pos.ui.payment.login

import java.net.URI
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class PosLoginQr(
    @SerialName("instance_url") val instanceUrl: String,
    @SerialName("vendor_id") val vendorId: Int,
    val username: String,
    val password: String,
) {
    companion object {
        fun parse(raw: String): PosLoginQr {
            val payload = Json.decodeFromString<PosLoginQr>(raw)
            val url = URI(payload.instanceUrl)
            require(url.scheme?.lowercase() in setOf("http", "https") && !url.host.isNullOrBlank())
            require(url.rawUserInfo == null && url.port in -1..65535 && url.port != 0)
            require(payload.vendorId > 0 && payload.username.isNotBlank() && payload.password.isNotBlank())
            return payload
        }
    }
}

package com.trade.exchanges.bitmex.dto.privatedata

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

@JsonIgnoreProperties(ignoreUnknown = true)
data class BitmexWallet(
        @JsonProperty("currency") val currency: String?,
        @JsonProperty("walletBalance") val walletBalance: BigDecimal?,
        @JsonProperty("availableMargin") val availableMargin: BigDecimal?
)
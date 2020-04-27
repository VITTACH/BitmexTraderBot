package com.trade.exchanges.bitmex.dto.privatedata

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

@JsonIgnoreProperties(ignoreUnknown = true)
data class BitmexPosition(
        @JsonProperty("unrealisedRoePcnt") val unrealisedRoePcnt: BigDecimal?,
        @JsonProperty("avgEntryPrice") val avgEntryPrice: BigDecimal?,
        @JsonProperty("currentQty") val currentQty: BigDecimal?
)
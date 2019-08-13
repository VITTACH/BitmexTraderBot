package com.trade.exchanges.bitmex.dto.privatedata

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

@JsonIgnoreProperties(ignoreUnknown = true)
data class BitmexPrivateOrder(
        @JsonProperty("price") val price: BigDecimal?,
        @JsonProperty("orderID") val id: String?,
        @JsonProperty("orderQty") val volume: BigDecimal?,
        @JsonProperty("side") val side: String?,
        @JsonProperty("stopPx") val stopPx: BigDecimal?,
        @JsonProperty("ordType") val ordType: String?,
        @JsonProperty("timeInForce") val expire: String?,
        @JsonProperty("execInst") val execInst: String?)
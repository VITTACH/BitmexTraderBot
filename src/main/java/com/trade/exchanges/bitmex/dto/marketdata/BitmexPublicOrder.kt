package com.trade.exchanges.bitmex.dto.marketdata

import com.fasterxml.jackson.annotation.JsonProperty
import com.trade.exchanges.core.orders.OrderType
import java.math.BigDecimal

data class BitmexPublicOrder(
        @JsonProperty("price") val price: BigDecimal?,
        @JsonProperty("id") val id: BigDecimal?,
        @JsonProperty("size") val volume: BigDecimal?,
        @JsonProperty("side") val side: String?,
        @JsonProperty("symbol") val symbol: String?
)

fun BitmexPublicOrder.getSide(): OrderType {
    return if (side.equals("Sell", true)) OrderType.ASK else OrderType.BID
}
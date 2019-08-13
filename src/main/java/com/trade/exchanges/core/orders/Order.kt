package com.trade.exchanges.core.orders

import com.trade.exchanges.core.CurrencyPair
import java.math.BigDecimal

enum class OrderType {
    BID,
    ASK
}

open class LimitOrder(val type: OrderType,
                      val amount: BigDecimal?,
                      val pair: CurrencyPair,
                      val price: BigDecimal?,
                      val stopPrice: BigDecimal?) : Comparable<LimitOrder> {

    override fun compareTo(other: LimitOrder) = if (type !== other.type) {
        if (type === OrderType.BID) -1 else 1
    } else if (price != null) {
        price.compareTo(other.price) * if (type === OrderType.BID) -1 else 1
    } else 0

    override fun toString(): String {
        return "(type=$type, amount=$amount, price=$price, stop=$stopPrice)"
    }
}
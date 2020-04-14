package com.trade.exchanges.bitmex.services

import com.trade.exchanges.bitmex.dto.marketdata.BitmexTicker
import com.trade.exchanges.bitmex.dto.privatedata.BitmexOrderParams
import com.trade.exchanges.bitmex.dto.privatedata.BitmexOrderType
import com.trade.exchanges.bitmex.dto.privatedata.BitmexPosition
import com.trade.exchanges.bitmex.dto.privatedata.BitmexPrivateOrder
import com.trade.exchanges.core.CurrencyPair
import com.trade.exchanges.core.orders.LimitOrder
import com.trade.exchanges.core.orders.OrderType
import java.io.IOException

interface BitmexTradeService {

    @Throws(IOException::class)
    fun getTicker(symbol: String): List<BitmexTicker>?

    @Throws(IOException::class)
    fun getOrderBook(pair: CurrencyPair, oSide: OrderType): List<LimitOrder>?

    @Throws(IOException::class)
    fun getBitmexOrders(): List<BitmexPrivateOrder>

    @Throws(IOException::class)
    fun placeBitmexOrder(order: LimitOrder, type: BitmexOrderType, params: BitmexOrderParams): String

    @Throws(IOException::class)
    fun cancelMyBitmexOrder(code: String?): List<BitmexPrivateOrder>?

    @Throws(IOException::class)
    fun cancelOrderBySymbol(code: String?): List<BitmexPrivateOrder>?

    @Throws(IOException::class)
    fun getOpenPositions(): List<BitmexPosition>?

    @Throws(IOException::class)
    fun closePosition(symbol: String): BitmexPosition
}
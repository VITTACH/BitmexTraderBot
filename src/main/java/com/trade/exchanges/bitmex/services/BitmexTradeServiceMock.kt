package com.trade.exchanges.bitmex.services

import com.trade.exchanges.bitmex.dto.marketdata.BitmexTicker
import com.trade.exchanges.bitmex.dto.privatedata.BitmexOrderParams
import com.trade.exchanges.bitmex.dto.privatedata.BitmexOrderType
import com.trade.exchanges.bitmex.dto.privatedata.BitmexPosition
import com.trade.exchanges.bitmex.dto.privatedata.BitmexPrivateOrder
import com.trade.exchanges.core.CurrencyPair
import com.trade.exchanges.core.orders.LimitOrder
import com.trade.exchanges.core.orders.OrderType

class BitmexTradeServiceMock: BitmexTradeService {

    override fun getTicker(symbol: String): List<BitmexTicker>? {
        TODO("not implemented")
    }

    override fun getOrderBook(pair: CurrencyPair, oSide: OrderType): List<LimitOrder>? {
        TODO("not implemented")
    }

    override fun getBitmexOrders(): List<BitmexPrivateOrder> {
        TODO("not implemented")
    }

    override fun placeBitmexOrder(order: LimitOrder, type: BitmexOrderType, params: BitmexOrderParams): String {
        TODO("not implemented")
    }

    override fun cancelMyBitmexOrder(code: String?): List<BitmexPrivateOrder>? {
        TODO("not implemented")
    }

    override fun cancelOrderBySymbol(code: String?): List<BitmexPrivateOrder>? {
        TODO("not implemented")
    }

    override fun closePosition(symbol: String): BitmexPosition {
        TODO("not implemented")
    }

    override fun getOpenPositions(): List<BitmexPosition>? {
        TODO("not implemented")
    }
}
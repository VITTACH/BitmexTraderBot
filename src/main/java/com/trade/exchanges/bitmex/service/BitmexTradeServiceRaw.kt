package com.trade.exchanges.bitmex.service

import com.trade.exchanges.bitmex.BitmexExchange
import com.trade.exchanges.bitmex.dto.marketdata.BitmexPublicOrder
import com.trade.exchanges.bitmex.dto.marketdata.BitmexTicker
import com.trade.exchanges.bitmex.dto.privatedata.*
import com.trade.exchanges.core.CurrencyPair
import com.trade.exchanges.core.orders.LimitOrder
import com.trade.exchanges.core.orders.OrderType
import java.io.IOException
import java.math.BigDecimal
import java.util.*


class BitmexTradeServiceRaw(val exchange: BitmexExchange) : BitmexBaseService(exchange) {

    @Throws(IOException::class)
    fun getTicker(symbol: String): List<BitmexTicker>? {
        return bitmex.getTicker(symbol)
    }

    @Throws(IOException::class)
    fun getOrderBook(pair: CurrencyPair, oSide: OrderType): List<LimitOrder>? {
        val orders = ArrayList<LimitOrder>()
        for (order in bitmex.getDepth(pair.toString(), null)) {
            if (order.getSide() == oSide) {
                val limitOrder = LimitOrder(oSide, order.volume, pair, order.price, null)
                if (oSide == OrderType.BID) {
                    orders.add(limitOrder)
                } else {
                    orders.add(0, limitOrder)
                }
            }
        }
        return orders
    }

    private fun BitmexPublicOrder.getSide(): OrderType {
        return if (side.equals("Sell", true)) OrderType.ASK else OrderType.BID
    }

    @Throws(IOException::class)
    fun getBitmexOrders(): List<BitmexPrivateOrder> {
        val orders = ArrayList<BitmexPrivateOrder>()
        var i = 0
        while (orders.size % 500 == 0) {
            val order = bitmex.getOrders(
                    apiKey = apiKey,
                    nonce = exchange.timeNonceFactory,
                    paramsDigest = signature,
                    filter = "{\"open\": true}",
                    count = 500,
                    start = i * 500,
                    reverse = true)

            if (order?.isEmpty() == false) {
                orders.addAll(order)
                i++
            } else break
        }
        return orders
    }

    @Throws(IOException::class)
    fun placeBitmexOrder(order: LimitOrder, type: BitmexOrderType, params: BitmexOrderParams): String {

        val symbol = order.pair.toString()
        val quantity = order.amount?.toInt()
        val icebergQty = params.icebergQty
        var price: BigDecimal? = order.price
        val stop = order.stopPrice
        val leverage = params.leverage
        val side = getSide(order.type)
        val hidden = params.hidden
        val post = params.postOnly
        val reduce = params.reduceOnly
        val trigger = params.trigger
        val closeOnTrigger = params.closeOnTrigger
        var pegOffsetValue = params.pegOffsetValue
        val text = params.text

        var orderType = type.name
        val timeInForce = params.expire?.name

        if (type === BitmexOrderType.Market
                || type === BitmexOrderType.Stop
                || type === BitmexOrderType.MarketIfTouched) {
            price = null
        }

        val execInst = StringBuilder()
        if (post != null && post) {
            execInst.append(PARTICIPATE_DO_NOT_INITIATE)
        }

        if (closeOnTrigger != null && closeOnTrigger) {
            if (execInst.isNotEmpty()) execInst.append(",")
            execInst.append(CLOSE)
        }

        if (trigger != null && trigger !== BitmexOrderTrigger.MarkPrice) {
            if (execInst.isNotEmpty()) execInst.append(",")
            execInst.append(trigger.name)
        }

        if (reduce != null && reduce) {
            if (execInst.isNotEmpty()) execInst.append(",")
            execInst.append(REDUCE_ONLY)
        }

        if (type === BitmexOrderType.Pegged) {
            orderType = BitmexOrderType.Stop.name
            price = null
            if (side == "Sell" && pegOffsetValue != null) {
                pegOffsetValue = pegOffsetValue.negate()
            }
        }

        val displayQty = if (hidden != null) {
            if (hidden && icebergQty == null) {
                BigDecimal("0")
            } else icebergQty
        } else null

        return bitmex.placeOrder(
                apiKey,
                exchange.timeNonceFactory,
                signature,
                symbol,
                side,
                quantity,
                price,
                stop,
                pegOffsetValue,
                orderType,
                execInst.toString(),
                displayQty,
                timeInForce,
                text).id ?: ""
    }

    @Throws(IOException::class)
    fun cancelMyBitmexOrder(code: String?): List<BitmexPrivateOrder>? {
        return bitmex.cancelOpenOrder(apiKey, exchange.timeNonceFactory, signature, code)
    }

    @Throws(IOException::class)
    fun cancelOrderBySymbol(code: String?): List<BitmexPrivateOrder>? {
        return bitmex.cancelAllOrders(apiKey, exchange.timeNonceFactory, signature, code)
    }

    @Throws(IOException::class)
    fun getOpenPositions(): List<BitmexPosition>? {
        val filter = "{\"isOpen\":true}"
        return bitmex.getPositions(apiKey, exchange.timeNonceFactory, signature, filter);
    }

    @Throws(IOException::class)
    fun closePosition(symbol: String): BitmexPosition {
        return bitmex.closePosition(apiKey, exchange.timeNonceFactory, signature, symbol)
    }

    companion object {
        private const val PARTICIPATE_DO_NOT_INITIATE = "ParticipateDoNotInitiate"
        private const val REDUCE_ONLY = "ReduceOnly"
        private const val CLOSE = "Close"

        private fun getSide(type: OrderType?): String {
            return if (type === OrderType.ASK) "Sell" else "Buy"
        }
    }

}
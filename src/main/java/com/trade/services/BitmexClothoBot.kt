package com.trade.services

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.trade.bots.telegram.TelegramCore
import com.trade.bots.telegram.service.TelegramMsgService
import com.trade.exchanges.bitmex.BitmexDigest
import com.trade.exchanges.bitmex.BitmexExchange
import com.trade.exchanges.bitmex.dto.privatedata.*
import com.trade.exchanges.bitmex.service.BitmexTradeServiceRaw
import com.trade.exchanges.core.CurrencyPair
import com.trade.exchanges.core.orders.LimitOrder
import com.trade.exchanges.core.orders.OrderType
import com.trade.models.PrefModel
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.math.BigDecimal
import java.net.URI
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.absoluteValue


// {"table":"trade","action":"partial","keys":[],"types":{"timestamp":"timestamp","symbol":"symbol","side":"symbol","size":"long","price":"float","tickDirection":"symbol","trdMatchID":"guid","grossValue":"long","homeNotional":"float","foreignNotional":"float"},"foreignKeys":{"symbol":"instrument","side":"side"},"attributes":{"timestamp":"sorted","symbol":"grouped"},"filter":{"symbol":currency},"data":[{"timestamp":"2019-04-28T20:13:08.186Z","symbol":currency,"side":"Buy","size":3929,"price":5150,"tickDirection":"ZeroPlusTick","trdMatchID":"9c4d3dc8-40fb-80ac-5019-7431020ee3e9","grossValue":76289393,"homeNotional":0.76289393,"foreignNotional":3929}]}

data class Subscribe(val op: String, val args: List<Any>)

interface BaseWsResponse

@JsonIgnoreProperties(ignoreUnknown = true)
data class TradeWSResponse(@JsonProperty("data") val data: List<BitmexTrade>?) : BaseWsResponse

@JsonIgnoreProperties(ignoreUnknown = true)
data class PositionWSResponse(@JsonProperty("data") val data: List<BitmexPosition>?) : BaseWsResponse

@JsonIgnoreProperties(ignoreUnknown = true)
data class BitmexTrade(
        @JsonProperty("side") val side: String?,
        @JsonProperty("price") val price: BigDecimal?,
        @JsonProperty("timestamp") val timestamp: String?)

enum class BitmexDirection {
    Up,
    Down,
    None
}

enum class WebSocketStatus {
    Opened,
    Closed
}

object BitmexClothoBot {
    private const val HUNDRED_MILLIONS = 100_000_000L
    private const val POSITION_PROFIT_SCALE = 10
    private const val POSITION_LOSS_SCALE = 100
    private const val POSITION_MAX_MINUTES = 10f

    private lateinit var apiKey: String
    private lateinit var secretKey: String
    private lateinit var url: String
    private lateinit var wsUri: URI

    private lateinit var telegramChatId: String
    private lateinit var telegramToken: String
    private lateinit var telegramService: TelegramMsgService

    private lateinit var tradeService: BitmexTradeServiceRaw

    private var watcherThread: Thread = Thread()
    private var positionsThread: Thread = Thread()

    private val openedPosOrders = ConcurrentHashMap<BigDecimal, String>()
    private val openedMainOrders = ConcurrentHashMap<BigDecimal, String>()
    private val openedStopOrders = ConcurrentHashMap<BigDecimal, String>()

    private val objMapper = ObjectMapper()
    private var socketState = WebSocketStatus.Closed
    private var webSocket: WebSocketClient? = null;

    private var oldPrice = BigDecimal.ZERO
    private var oldDirection = BitmexDirection.None

    private lateinit var priceSensitive: BigDecimal
    private lateinit var priceOffset: BigDecimal
    private lateinit var priceStep: BigDecimal
    private lateinit var pair: CurrencyPair

    private lateinit var stopPxOffset: BigDecimal
    private lateinit var stopPriceStep: BigDecimal
    private lateinit var stopPriceBias: BigDecimal

    private lateinit var orderVol: BigDecimal
    private var countOfOrders = 0

    private var posLastUpdateTime = 0L
    private var positionTime = 0L

    private val firstOrder = AtomicBoolean(true)

    fun close() {
        watcherThread.stop()
        positionsThread.stop()

        webSocket?.closeBlocking()
    }

    private fun init(prefModel: PrefModel) {
        apiKey = prefModel.apiKey
        secretKey = prefModel.secretKey;
        url = "https://${prefModel.url}"
        wsUri = URI("wss://${prefModel.url}/realtime")

        tradeService = BitmexExchange(apiKey, secretKey, url).pollingTradeService

        telegramChatId = prefModel.telegramChatId
        telegramToken = prefModel.telegramToken
        telegramService = TelegramCore(telegramToken).messageService

        priceSensitive = prefModel.priceSensitive
        priceOffset = prefModel.priceOffset
        priceStep = prefModel.priceStep
        pair = prefModel.pair

        stopPxOffset = prefModel.stopPxOffset
        stopPriceStep = prefModel.stopPriceStep
        stopPriceBias = prefModel.stopPriceBias

        orderVol = prefModel.orderVol
        countOfOrders = prefModel.countOfOrders
    }

    fun start(prefModel: PrefModel) {
        init(prefModel)
        while (!cancelAllOpenedOrders(pair.toString()));

        webSocket = object : WebSocketClient(wsUri) {
            override fun onOpen(hand: ServerHandshake) {
                telegramService.sendMessage(telegramChatId, "WebSocket connected!")

                synchronized(socketState) { socketState = WebSocketStatus.Opened; }
                authentication()
                subscribePositions(pair.toString())
                subscribeTrades(pair.toString())
            }

            override fun onClose(errorCode: Int, reason: String, remote: Boolean) {
                telegramService.sendMessage(telegramChatId, "WebSocket was closed")

                synchronized(socketState) { socketState = WebSocketStatus.Closed; }
            }

            override fun onMessage(message: String) = onHandleMessages(message)

            override fun onError(e: Exception) {}
        }

        socketState = WebSocketStatus.Opened
        webSocket?.connect()
        startWatchDog()
    }

    fun subscribeTrades(symbol: String) {
        webSocket?.send(Subscribe("subscribe", listOf("trade:$symbol")).toJson());
    }

    fun authentication() {
        val expireTime = System.currentTimeMillis()
        val sign = BitmexDigest.createInstance(secretKey).createWsSign(expireTime)
        webSocket?.send(Subscribe("authKeyExpires", listOf(apiKey, expireTime, sign)).toJson())
    }

    fun subscribePositions(symbol: String) {
        webSocket?.send(Subscribe("subscribe", listOf("position:$symbol")).toJson())
    }

    private fun Double.format(digits: Int) = String.format("%.${digits}f", this)

    private fun Subscribe.toJson() = objMapper.writeValueAsString(this)

    private fun startWatchDog() {
        watcherThread = Thread {
            while (true) {
                if (socketState == WebSocketStatus.Opened) continue;
                telegramService.sendMessage(telegramChatId, "WebSocket reconnect!")
                webSocket?.reconnect()
                Thread.sleep(8000)
                if (socketState == WebSocketStatus.Closed) {
                    break
                }
            }
            telegramService.sendMessage(telegramChatId, "Can not reconnect socket")
            while (!cancelAllOpenedOrders(pair.toString()));
        }
        watcherThread.start()
    }

    // -- START POSITIONS
    private fun closePositions() {
        val positions = tradeService.getOpenPositions() ?: return;

        if (positions.isEmpty()) {
            if (openedPosOrders.isNotEmpty()) {
                positionTime = 0;
                cancelOrders(openedPosOrders, force = true);
            }
        } else if (positionTime == 0L) {
            positionTime = Date().time
        }

        for (position in positions) {
            val entryPrice = position.avgEntryPrice?.toDouble() ?: continue
            val quantity = position.currentQty?.toDouble() ?: continue
            closePosition(pair, entryPrice, quantity)
        }
    }

    private fun closePosition(pair: CurrencyPair, entry: Double, amount: Double) {
        val price = tradeService.getTicker(pair.toString())?.get(0)?.lastPrice ?: return;

        val diffTime = Date().time - positionTime
        val scale = 1f - Math.min(POSITION_MAX_MINUTES, diffTime / 60000f) / POSITION_MAX_MINUTES;
        val amountValue = amount.absoluteValue / HUNDRED_MILLIONS

        val side = if (amount >= 0) OrderType.BID else OrderType.ASK
        val realise = amount.absoluteValue * (1 / entry - 1 / price.toDouble())
        val profitVol = POSITION_PROFIT_SCALE * amountValue
        val lossVol = POSITION_LOSS_SCALE * amountValue * scale

        val isLongProfit = side == OrderType.BID && realise > profitVol
        val isShortProfit = side == OrderType.ASK && realise < -profitVol
        val isLongLoss = side == OrderType.BID && realise < -lossVol
        val isShortLoss = side == OrderType.ASK && realise > lossVol

        val message = "Position: side = <b>$side</b>," +
                "realise = <b>${realise.format(5)}</b>\n, entry = <b>$entry</b>," +
                "price = <b>$price</b>, scale = <b>$scale</b>\n" +
                "Position with profit = <b>${isLongProfit || isShortProfit}</b>"
        println("${Date()} WS -> $message")

        if (isLongProfit || isLongLoss || isShortProfit || isShortLoss) {
            telegramService.sendMessage(telegramChatId, "Close position. $message")
            try {
                placeOrder(pair, side, amount.absoluteValue)
                println("${Date()} WS <- Close position. $message")
            } catch (exception: Exception) {
                exception.printStackTrace()
            }
        }
    }

    private fun placeOrder(pair: CurrencyPair, side: OrderType, quantity: Double) {
        val order1 = tradeService.getOrderBook(pair, side)?.get(0);
        val volume = order1?.amount?.min(BigDecimal(quantity))
        val otherSide = if (side == OrderType.BID) OrderType.ASK else OrderType.BID
        val order = LimitOrder(otherSide, volume, pair, order1?.price, null)
        val param = BitmexOrderParams(
                expire = BitmexOrderExpire.GoodTillCancel,
                postOnly = false,
                reduceOnly = false,
                trigger = null,
                closeOnTrigger = false
        )
        val orderId = tradeService.placeBitmexOrder(order, BitmexOrderType.Limit, param)
        order.price?.let { price ->
            openedPosOrders[price] = orderId
        }
    }
    // -- END POSITIONS

    private fun placeOrders(side: OrderType, price: BigDecimal, type: BitmexOrderType, orderVolume: BigDecimal?) {

        var price = price
        val stop = type == BitmexOrderType.StopLimit
        val param = BitmexOrderParams(
                expire = BitmexOrderExpire.GoodTillCancel,
                postOnly = !stop,
                reduceOnly = false,
                trigger = if (stop) BitmexOrderTrigger.LastPrice else null,
                closeOnTrigger = stop
        )

        var stopPrice = price + if (side == OrderType.ASK) {
            stopPxOffset
        } else -stopPxOffset

        var orderCount = 0
        val size = if (stop) 10 else countOfOrders
        for (i in 0 until size) {
            val priceSteps = if (stop) {
                price + if (side == OrderType.ASK) -stopPriceBias else stopPriceBias
            } else null
            val orderPrice = if (stop) stopPrice else price

            var nextPxStep = if (side == OrderType.ASK) priceStep else -priceStep
            if (stop) {
                nextPxStep = nextPxStep.negate()
            }
            price += nextPxStep

            stopPrice += if (side == OrderType.ASK) {
                -stopPriceStep
            } else stopPriceStep

            val orders = if (stop) openedStopOrders else openedMainOrders

            if (hasNearOrder(orderPrice, orders)) {
                println("Has near order: $orderPrice")
                continue
            } else {
                println("No near orders in: ${orders.keys}, at price: $orderPrice")
            }

            val order = LimitOrder(side, orderVolume, pair, orderPrice, priceSteps)
            Thread.sleep(200)
            try {
                val id = tradeService.placeBitmexOrder(order, type, param)
                println("Order placed with price: $orderPrice")
                if (stop) {
                    openedStopOrders[orderPrice] = id
                } else {
                    openedMainOrders[orderPrice] = id
                }
                orderCount++
            } catch (exception: Exception) {
                exception.printStackTrace()
                Thread.sleep(6000)
                break
            }
        }
        if (orderCount > 0) {
            println("${Date()} WS <- End place $orderCount/$countOfOrders orders")
        }
    }

    private fun cancelAllOpenedOrders(currency: String? = null): Boolean {
        val orderIds = openedMainOrders.values + openedStopOrders.values
        println("${Date()} WS -> Start cancel All ${orderIds.size} orders")
        var success = true
        try {
            if (currency == null) {
                if (orderIds.isEmpty()) return success
                orderIds.forEach { tradeService.cancelMyBitmexOrder(it); }
            } else tradeService.cancelOrderBySymbol(currency)
            openedPosOrders.clear()
            openedMainOrders.clear()
            openedStopOrders.clear()
            println("${Date()} WS <- End cancel All opened orders")
        } catch (exception: Exception) {
            success = false
            exception.printStackTrace()
            Thread.sleep(6000)
        }
        return success
    }

    private fun cancelOrders(
            orders: ConcurrentHashMap<BigDecimal, String>,
            price: BigDecimal = BigDecimal.ZERO,
            force: Boolean = false
    ): Boolean {
        println("${Date()} WS -> Start cancel order size ${orders.size}")

        var success = true
        var orderCount = 0
        val corridor = priceStep * BigDecimal(countOfOrders)
        val iterator = orders.entries.iterator()

        while (iterator.hasNext()) {
            val next = iterator.next()
            val isHigher = next.key > price + corridor
            val isLowest = next.key < price - corridor
            val isNear = (next.key - price).abs() <= priceStep.divide(BigDecimal("2"))

            if (isHigher || isLowest || isNear || force) {
                try {
                    tradeService.cancelMyBitmexOrder(next.value)
                    iterator.remove()
                    orderCount++
                    Thread.sleep(100)
                } catch (exception: Exception) {
                    success = false
                    break
                }
            }
        }
        if (!force) {
            println("${Date()} WS <- End cancel with result = $success, $orderCount orders");
        }
        return success
    }

    private fun hasNearOrder(lwPrice: BigDecimal, orders: Map<BigDecimal, String>): Boolean {
        for (order in orders) {
            if ((order.key - lwPrice).abs() <= priceStep.divide(BigDecimal("2"))) return true
        }
        return false
    }

    private fun onHandleMessages(message: String) {
        val response = when {
            message.contains("table\":\"trade") -> objMapper.readValue(message, TradeWSResponse::class.java)
            message.contains("table\":\"position") -> objMapper.readValue(message, PositionWSResponse::class.java);
            else -> if (message.contains("error")) throw Exception(message) else null
        }

        when (response) {
            is TradeWSResponse -> response.data?.last()?.let { trade -> updLastPrice(trade.price, trade.timestamp) }
            is PositionWSResponse -> updPositions()
        }
    }

    private fun updPositions() {
        if (System.currentTimeMillis() - posLastUpdateTime < 1000 || positionsThread.isAlive) {
            return
        }
        positionsThread = Thread { closePositions() }.apply { start() }
        posLastUpdateTime = System.currentTimeMillis()
    }

    private fun orderPriceOffset(curPrice: BigDecimal): BigDecimal? {
        return if (oldDirection == BitmexDirection.Up) {
            openedMainOrders.minBy { it.key }
        } else {
            openedMainOrders.maxBy { it.key }
        }?.let { curPrice - it.key }
    }

    private fun updLastPrice(curPrice: BigDecimal?, tradeTime: String?) {
        if (curPrice == null || tradeTime == null) {
            return
        }

        if (oldPrice == BigDecimal.ZERO) {
            oldPrice = curPrice
        } else if (oldPrice != curPrice) {
            val maxOffset = priceOffset + priceSensitive
            val offset = orderPriceOffset(curPrice)
            val init = firstOrder.getAndSet(false)

            if (init || (offset != null && (offset >= maxOffset || offset <= -maxOffset))) {
                updateOrders(curPrice)
            }
        }
    }

    private fun updateOrders(curPrice: BigDecimal) {
        val direction = when {
            curPrice > oldPrice -> BitmexDirection.Up
            curPrice < oldPrice -> BitmexDirection.Down
            else -> return
        }

        val orderSide = if (direction == BitmexDirection.Up) OrderType.ASK else OrderType.BID
        val stopOrderSide = if (orderSide == OrderType.BID) OrderType.ASK else OrderType.BID;
        val price = curPrice + if (orderSide == OrderType.ASK) priceOffset else -priceOffset;

        oldDirection = direction
        oldPrice = curPrice

        if (!cancelOrders(openedMainOrders, price)/*|| !cancelOrders(openedStopOrders, orderSide, price)*/) {
            return
        }

        placeOrders(orderSide, price, BitmexOrderType.Limit, orderVol)
        // placeOrders(stopOrderSide, price, BitmexOrderType.StopLimit, orderVol)
    }
}

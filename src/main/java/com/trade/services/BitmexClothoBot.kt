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
import com.trade.utils.ConsoleColors
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
    private const val WS_TIMEOUT_TIME = 120 * 1000
    private const val POSITION_PROFIT_SCALE = 5
    private const val POSITION_LOSS_SCALE = 3

    private lateinit var apiKey: String
    private lateinit var secretKey: String
    private lateinit var url: String
    private lateinit var wsUri: URI

    private lateinit var telegramChatId: String
    private lateinit var telegramToken: String
    private lateinit var telegramService: TelegramMsgService

    private lateinit var tradeService: BitmexTradeServiceRaw

    private var watcherThread: Thread = Thread()
    private var ordersThread: Thread = Thread()
    private var positionsThread: Thread = Thread()

    private val openedPosOrders = ConcurrentHashMap<BigDecimal, String>()
    private val openedMainOrders = ConcurrentHashMap<BigDecimal, String>()
    private val openedStopOrders = ConcurrentHashMap<BigDecimal, String>()

    private val objMapper = ObjectMapper()
    private var socketState = WebSocketStatus.Closed
    private var webSocket: WebSocketClient? = null;

    private var oldPrice = BigDecimal.ZERO
    private var oldDirection = BitmexDirection.None

    private lateinit var minPriceSensitive: BigDecimal
    private lateinit var maxPriceSensitive: BigDecimal
    private lateinit var priceOffset: BigDecimal
    private lateinit var priceStep: BigDecimal
    private lateinit var orderVol: BigDecimal
    private lateinit var pair: CurrencyPair

    private lateinit var stopLossOffset: BigDecimal
    private lateinit var stopPriceStep: BigDecimal
    private lateinit var stopPriceBias: BigDecimal

    private var countOfOrders = 0

    private var posLastUpdTime = 0L
    private var wsLastUpdTime = 0L

    private val isInit = AtomicBoolean(true)

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

        minPriceSensitive = prefModel.minPriceSensitive
        maxPriceSensitive = prefModel.maxPriceSensitive
        priceOffset = prefModel.priceOffset
        priceStep = prefModel.priceStep
        pair = prefModel.pair

        stopLossOffset = prefModel.stopLossOffset
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

    fun subscribePositions(symbol: String) {
        webSocket?.send(Subscribe("subscribe", listOf("position:$symbol")).toJson())
    }

    fun authentication() {
        val expireTime = System.currentTimeMillis()
        val sign = BitmexDigest.createInstance(secretKey).createWsSign(expireTime)
        webSocket?.send(Subscribe("authKeyExpires", listOf(apiKey, expireTime, sign)).toJson())
    }

    private fun startWatchDog() {
        watcherThread = Thread {
            while (true) {
                if (System.currentTimeMillis() - wsLastUpdTime > WS_TIMEOUT_TIME) {
                    socketState = WebSocketStatus.Closed
                }
                if (socketState == WebSocketStatus.Opened) {
                    continue
                }
                val message = "WatchDOG: WebSocket reconnect!"
                println("${ConsoleColors.GREEN_BOLD_BRIGHT}$message${ConsoleColors.RESET}")
                telegramService.sendMessage(telegramChatId, message)
                webSocket?.reconnect()
                Thread.sleep(60000)
                if (socketState == WebSocketStatus.Closed) {
                    break
                }
            }
            val message = "WatchDOG: Can not reconnect socket"
            println("${ConsoleColors.GREEN_BOLD_BRIGHT}$message${ConsoleColors.RESET}")
            telegramService.sendMessage(telegramChatId, message)
            while (!cancelAllOpenedOrders(pair.toString()));
        }
        watcherThread.start()
    }

    // -- START POSITIONS
    private fun closePositions() {
        val positions = tradeService.getOpenPositions() ?: return;

        if (positions.isEmpty()) {
            if (openedPosOrders.isNotEmpty()) {
                cancelOrders(openedPosOrders, isPosition = true);
            }
        }

        val curPrice = tradeService.getTicker(pair.toString())?.get(0)?.lastPrice ?: return

        for (position in positions) {
            val entryPrice = position.avgEntryPrice ?: continue
            val quantity = position.currentQty?.toDouble() ?: continue
            closePosition(pair, curPrice, entryPrice, quantity)
        }
    }

    private fun closePosition(pair: CurrencyPair, curPrice: BigDecimal, entryPrice: BigDecimal, amount: Double) {
        val side = if (amount >= 0) OrderType.BID else OrderType.ASK
        val entryPrice = entryPrice.toDouble()
        val curPrice = curPrice.toDouble()

        val realise = amount.absoluteValue * (1 / entryPrice - 1 / curPrice)
        val profitPercent = ((entryPrice - curPrice) / ((entryPrice + curPrice) / 2f)) * 100 * amount.absoluteValue

        val isLongProfit = side == OrderType.BID && profitPercent <= -POSITION_PROFIT_SCALE
        val isShortProfit = side == OrderType.ASK && profitPercent >= POSITION_PROFIT_SCALE
        val isLongLoss = side == OrderType.BID && profitPercent >= POSITION_LOSS_SCALE
        val isShortLoss = side == OrderType.ASK && profitPercent <= -POSITION_LOSS_SCALE

        val message = "Position: side = <b>$side</b>, percent = <b>$profitPercent</b>, " +
                "realise = <b>${realise.format(5)}</b>, entry = <b>$entryPrice</b>, price = <b>$curPrice</b>\n" +
                "Position with profit = <b>${isLongProfit || isShortProfit}</b>"

        // println("${ConsoleColors.GREEN} ${Date()} WS -> $message ${ConsoleColors.RESET}")

        if (isLongProfit || isLongLoss || isShortProfit || isShortLoss) {
            telegramService.sendMessage(telegramChatId, "Close position. $message")
            try {
                placeOrder(pair, side, amount.absoluteValue)
                println("${ConsoleColors.PURPLE} ${Date()} WS <- Close position. $message ${ConsoleColors.RESET}")
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

        var stopLossPrice = price + if (side == OrderType.ASK) {
            stopLossOffset
        } else -stopLossOffset

        var orderCount = 0
        for (i in 0 until countOfOrders) {
            val triggerPrice = if (stop) {
                price + if (side == OrderType.ASK) -stopPriceBias else stopPriceBias
            } else null
            val orderPrice = if (stop) stopLossPrice else price

            var nextPxStep = if (side == OrderType.ASK) priceStep else -priceStep
            if (stop) {
                nextPxStep = nextPxStep.negate()
            }
            price += nextPxStep

            stopLossPrice += if (side == OrderType.ASK) {
                -stopPriceStep
            } else stopPriceStep

            val orders = if (stop) openedStopOrders else openedMainOrders

            if (hasNearOrder(orderPrice, orders)) {
                println("${ConsoleColors.BLUE}Has near orders in: ${orders.keys}, at price: $orderPrice${ConsoleColors.RESET}")
                continue
            } else {
                println("${ConsoleColors.BLACK}No near orders in: ${orders.keys}, at price: $orderPrice${ConsoleColors.RESET}")
            }

            val order = LimitOrder(side, orderVolume, pair, orderPrice, triggerPrice)
            Thread.sleep(200)
            try {
                val id = tradeService.placeBitmexOrder(order, type, param)
                println("Order type = $type side = $side with price: $orderPrice")
                if (stop) {
                    openedStopOrders[triggerPrice!!] = id
                } else {
                    openedMainOrders[orderPrice] = id
                }
                orderCount++
            } catch (exception: Exception) {
                exception.printStackTrace()
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
        }
        return success
    }

    private fun cancelOrders(
            orders: ConcurrentHashMap<BigDecimal, String>,
            price: BigDecimal = BigDecimal.ZERO,
            isPosition: Boolean = false
    ) {
        println("${Date()} WS -> Start cancel order. Total size = ${orders.size}")

        var success = true
        var orderCount = 0
        val maxPriceOffset = priceStep * BigDecimal(countOfOrders)
        val iterator = orders.entries.iterator()

        try {
            while (iterator.hasNext()) {
                val orderEntry = iterator.next()
                val isHigher = orderEntry.key > price + maxPriceOffset
                val isLowest = orderEntry.key < price - maxPriceOffset
                val isNear = isNear(orderEntry.key, price)

                println("${ConsoleColors.YELLOW}" +
                        "TRY cancel order with price = ${orderEntry.key} at price = $price, maxPriceOffset = $maxPriceOffset, isHigher = $isHigher, isLowest = $isLowest, isNear = $isNear" +
                        "${ConsoleColors.RESET}"
                )

                if (isHigher || isLowest || isNear || isPosition) {
                    if (!isPosition) {
                        cancelStopOrders(orderEntry.key)
                    }
                    tradeService.cancelMyBitmexOrder(orderEntry.value)
                    iterator.remove()
                    orderCount++
                    Thread.sleep(200)
                }
            }
        } catch (exception: Exception) {
            while(!cancelAllOpenedOrders(pair.toString()));
            success = false
        }

        println("${Date()} WS <- End cancel result = $success, $orderCount orders")
    }

    private fun cancelStopOrders(price: BigDecimal) {
        val orderOffsets = HashMap<BigDecimal, Pair<BigDecimal, String>>()
        var minimalOffset = BigDecimal(Int.MAX_VALUE)
        for (order in openedStopOrders) {
            val offset = (price - order.key).abs()
            orderOffsets[offset] = order.key to order.value
            if (offset < minimalOffset) {
                minimalOffset = offset
            }
        }
        orderOffsets[minimalOffset]?.let {
            tradeService.cancelMyBitmexOrder(it.second)
            openedStopOrders.remove(it.first)
        }
    }

    private fun hasNearOrder(lwPrice: BigDecimal, orders: Map<BigDecimal, String>): Boolean {
        for (order in orders) {
            if (isNear(order.key, lwPrice)) return true
        }
        return false
    }

    private fun isNear(firstPrice: BigDecimal, scondPrice: BigDecimal): Boolean {
        return (firstPrice - scondPrice).abs() <= priceStep.divide(BigDecimal("2"))
    }

    private fun onHandleMessages(message: String) {
        wsLastUpdTime = System.currentTimeMillis()

        val response = when {
            message.contains("table\":\"trade") -> objMapper.readValue(message, TradeWSResponse::class.java)
            message.contains("table\":\"position") -> objMapper.readValue(message, PositionWSResponse::class.java);
            else -> {
                println("${ConsoleColors.GREEN_BACKGROUND_BRIGHT}Websocket message: $message${ConsoleColors.RESET}")
                if (message.contains("error")) {
                    socketState = WebSocketStatus.Closed
                    throw Exception(message)
                } else null
            }
        }

        when (response) {
            is TradeWSResponse -> response.data?.last()?.let { trade -> updLastPrice(trade.price, trade.timestamp) }
            is PositionWSResponse -> updPositions()
        }
    }

    private fun updPositions() {
        if (System.currentTimeMillis() - posLastUpdTime < 1000 || positionsThread.isAlive) {
            return
        }
        positionsThread = Thread {
            try { closePositions() }
            catch (exception: Exception) {
                exception.printStackTrace()
                positionsThread.stop()
            }
        }.apply { start() }
        posLastUpdTime = System.currentTimeMillis()
    }

    private fun updLastPrice(currentPrice: BigDecimal?, tradeTime: String?) {
        if (currentPrice == null || tradeTime == null) return

        if (oldPrice == BigDecimal.ZERO) {
            oldPrice = currentPrice
        } else if (oldPrice != currentPrice) {
            val offset = orderPriceOffset(currentPrice) ?: return
            val minOffset = priceOffset + minPriceSensitive
            val maxOffset = priceOffset + maxPriceSensitive
            val init = isInit.getAndSet(false)

            val isBoost = offset >= maxOffset || maxOffset <= -maxOffset
            val isInUpChannel = offset >= minOffset
            val isInDownChannel = offset <= -minOffset

            println("${ConsoleColors.BLACK_BACKGROUND_BRIGHT}" +
                    "Price isInUpChannel = $isInUpChannel, isInDownChannel = $isInDownChannel, isBoost = $isBoost" +
                    "${ConsoleColors.RESET}"
            )

            if (!ordersThread.isAlive && (init || isInUpChannel || isInDownChannel)) {
                ordersThread = Thread {
                    try { updateOrders(currentPrice, isBoost) }
                    catch (exception: Exception) {
                        exception.printStackTrace()
                        ordersThread.stop()
                    }
                }.apply { start() }
            }
        }
    }

    private fun orderPriceOffset(currentPrice: BigDecimal): BigDecimal? {
        return if (oldDirection == BitmexDirection.Up) {
            openedMainOrders.minBy { it.key }
        } else {
            openedMainOrders.maxBy { it.key }
        }?.let { currentPrice - it.key }
    }

    private fun updateOrders(curPrice: BigDecimal, isBoost: Boolean) {
        val direction = when {
            curPrice > oldPrice -> BitmexDirection.Up
            curPrice < oldPrice -> BitmexDirection.Down
            else -> return
        }

        val orderSide = if (direction == BitmexDirection.Up && !isBoost) {
            OrderType.ASK
        } else OrderType.BID
        val stopOrderSide = if (orderSide == OrderType.BID) OrderType.ASK else OrderType.BID;
        val price = curPrice + if (orderSide == OrderType.ASK) priceOffset else -priceOffset;

        cancelOrders(openedMainOrders, price)

        oldDirection = direction
        oldPrice = curPrice

        placeOrders(orderSide, price, BitmexOrderType.Limit, orderVol)
        placeOrders(stopOrderSide, price, BitmexOrderType.StopLimit, orderVol)
    }

    private fun Double.format(digits: Int) = String.format("%.${digits}f", this)

    private fun Subscribe.toJson() = objMapper.writeValueAsString(this)
}

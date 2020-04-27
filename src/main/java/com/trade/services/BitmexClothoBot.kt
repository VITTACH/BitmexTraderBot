package com.trade.services

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.trade.bots.telegram.TelegramCore
import com.trade.bots.telegram.service.TelegramMsgService
import com.trade.exchanges.bitmex.BitmexDigest
import com.trade.exchanges.bitmex.BitmexExchange
import com.trade.exchanges.bitmex.dto.privatedata.*
import com.trade.exchanges.bitmex.services.BitmexTradeService
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

class BitmexClothoBot(private val prefModel: PrefModel) {
    companion object {
        private val BIGDECIMAL_TWO = BigDecimal("2")
        private val PROFIT_BALANCE = BigDecimal("0.0001")

        private const val SYNC_PERIOD = 60 * 1000
        private const val BALANCE_PERIOD = 8 * 3600 * 1000
        private const val HUNDRED_MILLIONS = 100_000_000
        private const val WS_TIMEOUT_TIME = 120 * 1000
        private const val POSITION_PROFIT_SCALE = 5
        private const val POSITION_LOSS_SCALE = 100
    }

    private val apiKey: String
    private val secretKey: String
    private val url: String
    private val wsUri: URI

    private val openedPosOrders = ConcurrentHashMap<BigDecimal, String>()
    private val openedMainOrders = ConcurrentHashMap<BigDecimal, String>()
    private val openedStopOrders = ConcurrentHashMap<BigDecimal, String>()

    private val telegramChatId: String
    private val telegramToken: String
    private val telegramService: TelegramMsgService

    private val tradeService: BitmexTradeService

    private var socketLastUpdTime = 0L
    private var balanceLastUpdTime = 0L
    private var positionLastUpdTime = 0L
    private var orderLastRequestTime = 0L
    private var countOfOrders = 0

    private var watcherThread: Thread = Thread()
    private var ordersThread: Thread = Thread()
    private var positionsThread: Thread = Thread()

    private val objMapper = ObjectMapper()
    private var socketState = WebSocketStatus.Closed
    private var webSocket: WebSocketClient? = null

    private var oldPrice = BigDecimal.ZERO
    private var oldDirection = BitmexDirection.None

    private val minPriceSensitive: BigDecimal
    private val maxPriceSensitive: BigDecimal
    private var orderVolume: BigDecimal
    private val priceOffset: BigDecimal
    private val priceStep: BigDecimal
    private val pair: CurrencyPair

    private val stopLossOffset: BigDecimal
    private val stopPriceStep: BigDecimal
    private val stopPriceBias: BigDecimal

    private var initTotalBalance = BigDecimal.ZERO
    private var profitBalance = PROFIT_BALANCE

    init {
        url = "https://${prefModel.url}"
        wsUri = URI("wss://${prefModel.url}/realtime");
        apiKey = prefModel.apiKey
        secretKey = prefModel.secretKey
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
        orderVolume = prefModel.orderVol
        countOfOrders = prefModel.countOfOrders
    }

    fun close() {
        watcherThread.stop()
        positionsThread.stop()

        webSocket?.closeBlocking()
    }

    fun start() {
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
            var olPrintTime = 0L
            while (true) {
                val wsTimeDiff = System.currentTimeMillis() - socketLastUpdTime
                if (socketLastUpdTime > 0 && wsTimeDiff >= WS_TIMEOUT_TIME) {
                    socketState = WebSocketStatus.Closed
                }

                if (socketState == WebSocketStatus.Opened) {
                    System.currentTimeMillis().also { time ->
                        if (time - olPrintTime > 60000) {
                            println("${ConsoleColors.PURPLE_BACKGROUND_BRIGHT}" +
                                    "${Date()} WatchDOG: continue, timeDiff = $wsTimeDiff" +
                                    "${ConsoleColors.RESET}"
                            )
                            olPrintTime = time
                        }
                    }
                    continue
                }

                val message = "${Date()} WatchDOG: WebSocket reconnect!"
                println("${ConsoleColors.GREEN_BOLD_BRIGHT}$message${ConsoleColors.RESET}")
                telegramService.sendMessage(telegramChatId, message)

                webSocket?.reconnect()
                Thread.sleep(30000)
            }
        }.apply { start() }
    }

    // -- START POSITIONS
    private fun closePositions() {
        val positions = tradeService.getOpenPositions() ?: return;

        if (positions.isEmpty() && openedPosOrders.isNotEmpty()) {
            cancelOrders(openedPosOrders, force = true)
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
        val myPrice = curPrice.toDouble()

        val realise = amount.absoluteValue * (1 / entryPrice - 1 / myPrice)
        val profitPercent = ((entryPrice - myPrice) / ((entryPrice + myPrice) / 2f)) * 100 * amount.absoluteValue

        val isLongProfit = side == OrderType.BID && profitPercent <= -POSITION_PROFIT_SCALE
        val isShortProfit = side == OrderType.ASK && profitPercent >= POSITION_PROFIT_SCALE
        val isLongLoss = side == OrderType.BID && profitPercent >= POSITION_LOSS_SCALE
        val isShortLoss = side == OrderType.ASK && profitPercent <= -POSITION_LOSS_SCALE

        val message = "Position: profit = <b>${isLongProfit || isShortProfit}</b>\n" +
                "side = <b>$side</b>, " +
                "percent = <b>$profitPercent</b>, " +
                "realise = <b>${realise.format(5)}</b>, " +
                "amount = <b>$amount</b>, " +
                "entry = <b>$entryPrice</b>, " +
                "price = <b>$myPrice</b>"

        //println("${ConsoleColors.BLACK_UNDERLINED}${Date()} WS -> $message${ConsoleColors.RESET}")

        if (isLongProfit || isLongLoss || isShortProfit || isShortLoss) {
            telegramService.sendMessage(telegramChatId, "Close position. $message")
            try {
                placeOrder(pair, curPrice, side, amount.absoluteValue)
                println("${ConsoleColors.PURPLE}${Date()} WS <- Close position. $message${ConsoleColors.RESET}")
            } catch (exception: Exception) {
                exception.printStackTrace()
            }
        }
    }

    private fun placeOrder(pair: CurrencyPair, price: BigDecimal, side: OrderType, amount: Double) {
        val orderFromBook = tradeService.getOrderBook(pair, side)?.get(0)
        val volume = orderFromBook?.amount?.min(BigDecimal(amount));
        val otherSide = if (side == OrderType.BID) OrderType.ASK else OrderType.BID
        val order = LimitOrder(otherSide, volume, pair, price, null)
        val param = BitmexOrderParams(
                expire = BitmexOrderExpire.GoodTillCancel,
                postOnly = false,
                reduceOnly = false,
                trigger = null,
                closeOnTrigger = false
        )
        val orderId = tradeService.placeBitmexOrder(order, BitmexOrderType.Limit, param)

        cancelOrders(openedPosOrders, force = true) // CANCEL OPEN POSITIONS

        order.price?.let { openedPosOrders[it] = orderId }
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
                val message = "Has near in: ${orders.keys}, at price: $orderPrice"
                println("${ConsoleColors.BLUE}$message${ConsoleColors.RESET}")
                continue
            } else {
                val message = "No near in: ${orders.keys}, at price: $orderPrice"
                println("${ConsoleColors.BLACK}$message${ConsoleColors.RESET}")
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
            force: Boolean = false
    ) {
        println("${Date()} WS -> Start cancel order. Total size = ${orders.size}")

        var success = true
        var count = 0
        val maxPriceOffset = priceStep * BigDecimal(countOfOrders)
        val iterator = orders.entries.iterator()

        try {
            while (iterator.hasNext()) {
                val orderEntry = iterator.next()
                val isHigher = orderEntry.key > price + maxPriceOffset
                val isLowest = orderEntry.key < price - maxPriceOffset
                val isNear = isNear(orderEntry.key, price)

                val message = "Cancel my order = ${orderEntry.key}" +
                        "at price = $price, " +
                        "maxPriceOffset = $maxPriceOffset, " +
                        "isHigher = $isHigher, " +
                        "isLowest = $isLowest, " +
                        "isNear = $isNear"

                println("${ConsoleColors.YELLOW}$message${ConsoleColors.RESET}")

                if (isHigher || isLowest || isNear || force) {
                    if (!force) {
                        cancelStopOrders(orderEntry.key)
                    }
                    tradeService.cancelMyBitmexOrder(orderEntry.value)
                    iterator.remove()
                    count++
                    Thread.sleep(200)
                }
            }
        } catch (exception: Exception) {
            while (!cancelAllOpenedOrders(pair.toString()));
            success = false
        }

        println("${Date()} WS <- End cancel result = $success, $count orders");
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

    private fun isNear(first: BigDecimal, second: BigDecimal): Boolean {
        return (first - second).abs() <= priceStep.divide(BIGDECIMAL_TWO)
    }

    private fun onHandleMessages(message: String) {
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
        if (System.currentTimeMillis() - positionLastUpdTime < 1000 || positionsThread.isAlive) {
            return
        }

        positionsThread = Thread {
            try {
                positionLastUpdTime = System.currentTimeMillis()
                closePositions()
            } catch (exception: Exception) {
                exception.printStackTrace()
                positionsThread.stop()
            }
        }.apply { start() }
    }

    private fun updLastPrice(currentPrice: BigDecimal?, tradeTime: String?) {
        if (currentPrice == null || tradeTime == null) {
            return
        }

        if (oldPrice == BigDecimal.ZERO) {
            oldPrice = currentPrice
        } else if (oldPrice != currentPrice && !ordersThread.isAlive) {
            ordersThread = Thread {
                try {
                    socketLastUpdTime = System.currentTimeMillis()
                    updateOrders(currentPrice)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }.apply { start() }
        }
    }

    private fun updateOrders(currentPrice: BigDecimal) {
        val offset = orderPriceOffset(currentPrice)
        val maxOffset = priceOffset + maxPriceSensitive;

        val isInUpChannel = offset?.let { it >= priceOffset + minPriceSensitive } ?: false
        val isInDownChannel = offset?.let { it <= -(priceOffset + minPriceSensitive) } ?: false
        val isBoost = offset?.let { it !in -maxOffset..maxOffset } ?: false

        if (socketLastUpdTime - orderLastRequestTime > SYNC_PERIOD) {
            synchronisedOrders()
            syncAccountBalance()
        }

        val message = "${Date()} Handle incoming trade " +
                (if (isInUpChannel) "isInUpChannel = $isInUpChannel, " else "") +
                (if (isInDownChannel) "isInDownChannel = $isInDownChannel, " else "") +
                (if (isBoost) "isBoost = $isBoost, " else "") +
                "currentPrice = $currentPrice, " +
                "oldPrice = $oldPrice, " +
                "diff = ${(currentPrice - oldPrice).abs()}, " +
                "offset = $offset, " +
                "posOrders = ${openedPosOrders.keys}, " +
                "mainOrders = ${openedMainOrders.keys}, " +
                "stopOrders = ${openedStopOrders.keys}\n"

        if (offset == null || isInUpChannel || isInDownChannel) {
            print("${ConsoleColors.WHITE_BACKGROUND_BRIGHT}$message${ConsoleColors.RESET}")
            updateOpenOrders(currentPrice, isBoost)
        } else {
            print("${ConsoleColors.BLACK_BACKGROUND_BRIGHT}$message${ConsoleColors.RESET}")
        }
    }

    private fun synchronisedOrders() {
        val bitmexOrderIds = tradeService.getBitmexOrders().map { it.id }
        val message = "${Date()} BEFORE SYNCHRONISED " +
                "bitmexOrderIds = $bitmexOrderIds, " +
                "openedPosOrders = ${openedPosOrders.keys}, " +
                "openedMainOrders = ${openedMainOrders.keys}, " +
                "openedStopOrders = ${openedStopOrders.keys}"

        println(message)

        synchronisedOrders(openedMainOrders, bitmexOrderIds)
        synchronisedOrders(openedStopOrders, bitmexOrderIds)
        synchronisedOrders(openedPosOrders, bitmexOrderIds)

        orderLastRequestTime = socketLastUpdTime
    }

    private fun updateOpenOrders(curPrice: BigDecimal, isBoost: Boolean) {
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

        placeOrders(orderSide, price, BitmexOrderType.Limit, orderVolume)
        placeOrders(stopOrderSide, price, BitmexOrderType.StopLimit, orderVolume)
    }

    private fun synchronisedOrders(
            orders: ConcurrentHashMap<BigDecimal, String>,
            bitmexOrderIds: List<String?>
    ) {
        val iterator = orders.iterator()
        while (iterator.hasNext()) {
            val orderEntry = iterator.next()
            if (bitmexOrderIds.find { it == orderEntry.value } == null) {
                iterator.remove()
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

    private fun syncAccountBalance() {
        val totalBalance = getAccountBalance() ?: return

        if (System.currentTimeMillis() - balanceLastUpdTime >= BALANCE_PERIOD) {
            initTotalBalance = totalBalance
            profitBalance = PROFIT_BALANCE
            balanceLastUpdTime = System.currentTimeMillis();
        }

        val message = "${Date()} BalanceLastUpdTime = ${Date(balanceLastUpdTime)}, " +
                "orderVolume = $orderVolume, " +
                "totalBalance = $totalBalance, " +
                "profitBalance = $profitBalance, " +
                "initTotalBalance = $initTotalBalance\n"

        print("${ConsoleColors.BLUE_BACKGROUND_BRIGHT}$message${ConsoleColors.RESET}")

        if (totalBalance > initTotalBalance + profitBalance) {
            profitBalance += PROFIT_BALANCE
            orderVolume = orderVolume.divide(BIGDECIMAL_TWO)
        } else if (totalBalance < initTotalBalance) {
            orderVolume = prefModel.orderVol
        }
    }

    private fun getAccountBalance(): BigDecimal? {
        return tradeService.getBitmexWallet()?.let { wallet ->
            wallet.walletBalance?.divide(BigDecimal(HUNDRED_MILLIONS));
        }
    }

    private fun Double.format(digits: Int) = String.format("%.${digits}f", this)

    private fun Subscribe.toJson() = objMapper.writeValueAsString(this)
}

package com.trade.services

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.trade.bots.telegram.TelegramCore
import com.trade.exchanges.bitmex.BitmexDigest
import com.trade.exchanges.bitmex.BitmexExchange
import com.trade.exchanges.bitmex.dto.privatedata.*
import com.trade.exchanges.core.CurrencyPair
import com.trade.exchanges.core.orders.LimitOrder
import com.trade.exchanges.core.orders.OrderType
import com.trade.models.PrefModel
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.math.BigDecimal
import java.net.URI
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.HashMap
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

data class Channel(val highPrice: BigDecimal, val lowPrice: BigDecimal, val open: Long, val close: Long) {
    override fun toString(): String {
        return "Channel(highPrice = $highPrice, lowPrice = $lowPrice, open = ${Date(open)}, close = ${Date(close)})"
    }
}

data class BarModel(val openPrice: BigDecimal, val closePrice: BigDecimal, val currentTime: Long) {
    fun getMinMax(): Pair<BigDecimal, BigDecimal> = Pair(openPrice.min(closePrice), openPrice.max(closePrice));
    fun getHeight(): BigDecimal {
        val (minPrice, maxPrice) = getMinMax()
        return maxPrice - minPrice
    }
    override fun toString(): String {
        return "BarModel(openPrice = $openPrice, closePrice = $closePrice, currentTime = ${Date(currentTime)})"
    }
}

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
    private val apiKey = prefModel.apiKey
    private val secretKey = prefModel.secretKey;
    private val url = "https://${prefModel.url}"
    private val wsUri = URI("wss://${prefModel.url}/realtime")

    private val timeDatePattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"

    private val telegramChatId = prefModel.telegramChatId
    private val telegramToken = prefModel.telegramToken
    private val telegramService = TelegramCore(telegramToken).messageService

    private val myPollingTradeService = BitmexExchange(apiKey, secretKey, url).pollingTradeService

    private var orderThread: Thread = Thread()
    private var watcherThread: Thread = Thread()
    private var positionsThread: Thread = Thread()
    private var socketState = WebSocketStatus.Closed

    private val openedMainOrders = HashMap<BigDecimal, String>()
    private val openedStopOrders = HashMap<BigDecimal, String>()

    private val objMapper = ObjectMapper()
    private var webSocket: WebSocketClient? = null;

    private var oldPrice = BigDecimal.ZERO
    private var oldDirection = BitmexDirection.None

    private val heightInterval = prefModel.heightInterval
    private val channelMinTime = prefModel.channelMinTime

    private val priceSensitive = prefModel.priceSensitive
    private var priceOffset = prefModel.priceOffset
    private val priceStep = prefModel.priceStep
    private val pair = prefModel.pair

    private val stopPxOffset = prefModel.stopPxOffset
    private val stopPriceStep = prefModel.stopPriceStep
    private val stopPriceBias = prefModel.stopPriceBias

    private val orderVol = prefModel.orderVol
    private val countOfOrders = prefModel.countOfOrders

    private val hundredMillions = 100_000_000L
    private var posLastTime = 0L

    private var barOpenPrice = BigDecimal.ZERO
    private var barClosePrice = BigDecimal.ZERO
    private val historyBars = ArrayList<BarModel>()
    private val channels = HashMap<Long, Channel>()
    private var recentBarTime = 0L
    private var popularHeight = BigDecimal.ZERO
    private val barModelsMaxSize = 1440
    private val heightPeriod = 30

    fun close() {
        orderThread.stop()
        watcherThread.stop()
        positionsThread.stop()

        webSocket?.close()
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

            override fun onError(e: Exception) { }
        }

        socketState = WebSocketStatus.Opened
        webSocket?.connect()
        startWatchDog()
    }

    private fun startWatchDog() {
        watcherThread = Thread {
            while (true) {
                if (socketState == WebSocketStatus.Opened) continue;
                telegramService.sendMessage(telegramChatId, "WebSocket reconnect!")
                webSocket?.reconnect()
                Thread.sleep(8000)
                if (socketState == WebSocketStatus.Closed) { break }
            }
            telegramService.sendMessage(telegramChatId, "Can not reconnected!")
            while (!cancelAllOpenedOrders(pair.toString()));
        }
        watcherThread.start()
    }

    private fun closePosition(pair: CurrencyPair, entry: Double, amount: Double) {
        val price = myPollingTradeService.getTicker(pair.toString())?.get(0)?.lastPrice ?: return;
        val side = if (amount >= 0) OrderType.BID else OrderType.ASK;
        val currentQty = amount.absoluteValue
        val realise = currentQty * (1 / entry - 1 / price.toDouble())
        val profitVol = 4 * currentQty / hundredMillions

        val isLongProfit = side == OrderType.BID && realise > profitVol
        val isShortProfit = side == OrderType.ASK && realise < -profitVol
        val isLongLoss = side == OrderType.BID && realise < -20 * profitVol
        val isShortLoss = side == OrderType.ASK && realise > 20 * profitVol

        val message = "Position: side = <b>$side</b>, realise = <b>${realise.format(4)}</b>\n," +
            "entry = <b>$entry</b>, price = <b>$price</b>\n" +
            "Position with profit = <b>${isLongProfit || isShortProfit}</b>"
        println("${Date()} WS -> $message")

        if (isLongProfit || isLongLoss || isShortProfit || isShortLoss) {
            telegramService.sendMessage(telegramChatId, "Close position. $message")
            try {
                placeOrder(pair, side, currentQty)
                println("${Date()} WS <- End closing open positions")
            } catch (exception: Exception) {
                exception.printStackTrace()
            }
        }
    }

    private fun placeOrder(pair: CurrencyPair, side: OrderType, quantity: Double) {
        val order1 = myPollingTradeService.getOrderBook(pair, side)?.get(0);
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
        myPollingTradeService.placeBitmexOrder(order, BitmexOrderType.Limit, param)
    }

    private fun closePositions() {
        val positions = myPollingTradeService.getOpenPositions() ?: return;

        for (position in positions) {
            val entryPrice = position.avgEntryPrice?.toDouble() ?: continue
            val quantity = position.currentQty?.toDouble() ?: continue
            closePosition(pair, entryPrice, quantity)
        }
    }

    private fun placeOrders(side: OrderType, price: BigDecimal, type: BitmexOrderType, orderVolume: BigDecimal?) {

        println("${Date()} WS -> Start placing $countOfOrders orders")
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
        for (i in 0..(countOfOrders - 1)) {
            val priceSteps = if (stop) {
                price + if (side == OrderType.ASK) -stopPriceBias else stopPriceBias
            } else null
            val orderPrice = if (stop) stopPrice else price

            var nextPxStep = if (side == OrderType.ASK) priceStep else -priceStep
            if (stop) {
                nextPxStep = nextPxStep.negate()
            }
            price += nextPxStep

            if (stop && i > 10) continue

            stopPrice += if (side == OrderType.ASK) {
                -stopPriceStep
            } else stopPriceStep

            val orders = if (stop) openedStopOrders else openedMainOrders
            if (hasNearOrder(orderPrice, orders)) continue

            val order = LimitOrder(side, orderVolume, pair, orderPrice, priceSteps)
            Thread.sleep(200)
            try {
                val id = myPollingTradeService.placeBitmexOrder(order, type, param)
                println("Order placed with price: $orderPrice")
                if (type == BitmexOrderType.Limit) {
                    openedMainOrders[orderPrice] = id
                } else {
                    openedStopOrders[orderPrice] = id
                }
                orderCount++
            } catch (exception: Exception) {
                exception.printStackTrace()
                Thread.sleep(6000)
                break
            }
        }
        println("${Date()} WS <- Placed $orderCount/$countOfOrders orders")
    }

    private fun cancelAllOpenedOrders(currency: String? = null): Boolean {
        val orderIds = openedMainOrders.values + openedStopOrders.values
        println("${Date()} WS -> Start cancel All ${orderIds.size} orders")
        var success = true
        try {
            if (currency == null) {
                if (orderIds.isEmpty()) return success
                orderIds.forEach { myPollingTradeService.cancelMyBitmexOrder(it); }
            } else myPollingTradeService.cancelOrderBySymbol(currency)
            openedMainOrders.clear()
            openedStopOrders.clear()
            println("${Date()} WS <- End canceling All opened orders")
        } catch (exception: Exception) {
            success = false
            exception.printStackTrace()
            Thread.sleep(6000)
        }
        return success
    }

    private fun cancelOrders(side: OrderType, lowPrice: BigDecimal, orders: HashMap<BigDecimal, String>): Boolean {
        val iterator = orders.entries.iterator()
        var success = true
        var orderCount = 0
        println("${Date()} WS -> Start cancel orders out of corridor")
        while (iterator.hasNext()) {
            val next = iterator.next()
            if ((next.key < lowPrice && side == OrderType.ASK) || (next.key > lowPrice && side == OrderType.BID)) {
                Thread.sleep(100)
                try {
                    myPollingTradeService.cancelMyBitmexOrder(next.value)
                    iterator.remove()
                    orderCount++
                } catch (exception: Exception) {
                    success = false
                    break
                }
            }
        }
        println("${Date()} WS <- End canceling my $orderCount orders")
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
            is TradeWSResponse -> response.data?.forEach { trade -> updLastPrice(trade.price, trade.timestamp) }
            is PositionWSResponse -> updPositions()
        }
    }

    private fun findChannels() {
        val index = historyBars.indexOfFirst { it.getHeight() < popularHeight }
        val lastBar = historyBars[index]
        var (oldMinPrice, oldMaxPrice) = lastBar.getMinMax()
        var openTime = lastBar.currentTime
        var i = 0L
        for (bar in historyBars) {
            if (i < index) continue
            val (curMinPrice, curMaxPrice) = bar.getMinMax()
            val lowPrice = oldMinPrice - popularHeight
            val highPrice = oldMaxPrice + popularHeight

            val oldHeight = highPrice - lowPrice
            val curHeight = curMaxPrice - curMinPrice

            if (curMinPrice > highPrice || curMaxPrice < lowPrice || curHeight > oldHeight) {
                val isLongTime = bar.currentTime - openTime >= channelMinTime * 60 * 1000
                if (isLongTime && channels[i] == null) {
                    channels[i] = Channel(highPrice, lowPrice, openTime, bar.currentTime)
                    telegramService.sendMessage(telegramChatId, "Channel[$i] ${channels[i]}")
                }
                if (curHeight < oldHeight) {
                    oldMaxPrice = curMaxPrice
                    oldMinPrice = curMinPrice
                }
                openTime = bar.currentTime
            }
            i++
        }
    }

    private fun toPopularHeight(): Int? {
        val heights = ArrayList<BigDecimal>()
        historyBars.forEach { model -> heights.add(model.getHeight()); }

        val heightCounter = HashMap<Int, Long>()
        for (i in 0..100 step heightInterval) {
            heights.forEach { barHeight ->
                if (barHeight.toInt() > i && barHeight.toInt() < i + heightInterval) {
                    heightCounter[i] = heightCounter[i]?.let { it + 1 } ?: 1
                }
            }
        }

        return heightCounter.maxBy{ it.value }?.key?.let { (it + 1) * heightInterval }
    }

    private fun composeModel(curPrice: BigDecimal, date: Date?) {
        val actualBarTime = date?.time ?: return
        if (TimeUnit.MILLISECONDS.toMinutes(actualBarTime) - TimeUnit.MILLISECONDS.toMinutes(recentBarTime) >= 1) {
            if (recentBarTime != 0L) {
                historyBars.add(BarModel(barOpenPrice, barClosePrice, recentBarTime));
                if (historyBars.size >= barModelsMaxSize) {
                    channels.remove(channels.minBy { it.key; }?.key)
                    historyBars.removeAt(0)
                }
                if (historyBars.size % heightPeriod == 0) toPopularHeight()?.let { popularHeight = BigDecimal(it) }
                if (popularHeight != BigDecimal.ZERO) findChannels()
                barOpenPrice = BigDecimal.ZERO
            }
            recentBarTime = actualBarTime
        }

        if (barOpenPrice == BigDecimal.ZERO) barOpenPrice = curPrice
        barClosePrice = curPrice
    }

    private fun foundOffset(curPrice: BigDecimal): BigDecimal? {
        return if (oldDirection == BitmexDirection.Up) {
            openedMainOrders.minBy { it.key }
        } else {
            openedMainOrders.maxBy { it.key }
        }?.let { (curPrice - it.key).abs() }
    }

    private fun updateOrders(curPrice: BigDecimal, offset: BigDecimal?, maxDiff: BigDecimal, minDiff: BigDecimal) {
        if (!(openedMainOrders.isEmpty() || (offset != null && (offset >= maxDiff || offset <= minDiff)))) return
        orderThread = Thread { updateOrders(curPrice) }
        orderThread.start()
    }

    private fun updLastPrice(price: BigDecimal?, tradeTime: String?) {
        if (price == null || tradeTime == null) return;
        if (oldPrice != BigDecimal.ZERO && !orderThread.isAlive) {
            val date = try {
                SimpleDateFormat(timeDatePattern).apply { timeZone = TimeZone.getTimeZone("GMT") }.parse(tradeTime)
            } catch (e: ParseException) { return }
            composeModel(price, date)
            if (true) {
                updateOrders(price, foundOffset(price), priceOffset + priceSensitive, priceOffset - priceSensitive)
            }
        }
        oldPrice = price
    }

    private fun updPositions() {
        if (System.currentTimeMillis() + -posLastTime < 1000 || positionsThread.isAlive) return
        positionsThread = Thread { closePositions(); }
        positionsThread.start()
        posLastTime = System.currentTimeMillis()
    }

    private fun updateOrders(orderPrice: BigDecimal) {
        val direction = if (orderPrice > oldPrice) BitmexDirection.Up else BitmexDirection.Down

        val side = if (direction == BitmexDirection.Up) OrderType.ASK else OrderType.BID
        val bottomPrice = orderPrice + if (side == OrderType.ASK) priceOffset else -priceOffset

        val success = if (oldDirection == direction) {
            cancelOrders(side, bottomPrice, openedMainOrders) && cancelOrders(side, bottomPrice, openedStopOrders)
        } else cancelAllOpenedOrders(pair.toString())
        if (!success) return

        placeOrders(side, bottomPrice, BitmexOrderType.Limit, orderVol)
        val otherSide = if (side == OrderType.BID) OrderType.ASK else OrderType.BID;
        placeOrders(otherSide, bottomPrice, BitmexOrderType.StopLimit, orderVol)

        oldDirection = direction
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

}

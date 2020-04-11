package com.trade.models

import com.trade.exchanges.core.CurrencyPair
import java.math.BigDecimal


data class UserModel(var userName: String? = null, var password: String? = null)

data class PrefModel(
    var telegramChatId: String = "@coinsroad",
    var telegramToken: String = "708056608:AAHJwrByB0EpVdVRt4poqopXZz3Wr0V8ESo",
    var apiKey: String = "9pjboPqEfXm0ckyz55WmSVzo",
    var secretKey: String = "jSshrCKFCQWEAbLkogj7QivqGTSos1KfKzo_TICF8XGn96Hg",
    var url: String = "testnet.bitmex.com",
    var pair: CurrencyPair = CurrencyPair("XBT/USD"),
    var priceSensitive: BigDecimal = BigDecimal("5"),
    var countOfOrders: Int = 1,
    var orderVol: BigDecimal = BigDecimal("100"),
    var priceOffset: BigDecimal = BigDecimal("15"),
    var priceStep: BigDecimal = BigDecimal("10"),
    var stopLossOffset: BigDecimal = BigDecimal("14"),
    var stopPriceBias: BigDecimal = BigDecimal("0.5"),
    var stopPriceStep: BigDecimal = BigDecimal("10")
)
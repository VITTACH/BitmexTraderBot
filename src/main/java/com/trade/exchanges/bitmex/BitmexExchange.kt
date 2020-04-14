package com.trade.exchanges.bitmex

import com.trade.exchanges.bitmex.services.BitmexTradeServiceRaw
import com.trade.exchanges.core.nonce.CurTimeNonceFactory

class BitmexExchange(val apiKey: String, val secretKey: String, val sslUri: String = "https://www.bitmex.com") {

    val timeNonceFactory = CurTimeNonceFactory()

    val pollingTradeService = BitmexTradeServiceRaw(this)

}
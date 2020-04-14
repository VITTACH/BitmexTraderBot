package com.trade.exchanges.bitmex.services.base

import si.mazi.rescu.ParamsDigest
import si.mazi.rescu.RestProxyFactory
import com.trade.exchanges.bitmex.BitmexPublic
import com.trade.exchanges.bitmex.BitmexDigest
import com.trade.exchanges.bitmex.BitmexExchange


open class BitmexBaseService(exchange: BitmexExchange) {

    protected val bitmex: BitmexPublic = RestProxyFactory.createProxy(BitmexPublic::class.java, exchange.sslUri)
    protected val signature: ParamsDigest
    protected var apiKey = exchange.apiKey

    init {
        signature = BitmexDigest.createInstance(exchange.secretKey)
    }
}

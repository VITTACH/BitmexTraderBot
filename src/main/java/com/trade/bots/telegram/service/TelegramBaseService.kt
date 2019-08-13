package com.trade.bots.telegram.service

import com.trade.bots.telegram.TelegramCore
import com.trade.bots.telegram.TelegramPublic;
import si.mazi.rescu.RestProxyFactory

open class TelegramBaseService(telegram: TelegramCore) {
    protected val telegram: TelegramPublic = RestProxyFactory.createProxy(TelegramPublic::class.java, telegram.sslUri)
    protected var apiToken = telegram.apiToken
}
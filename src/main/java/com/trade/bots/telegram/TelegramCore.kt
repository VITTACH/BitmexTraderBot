package com.trade.bots.telegram

import com.trade.bots.telegram.service.TelegramMsgService

class TelegramCore(val apiToken: String, val sslUri: String = "https://api.telegram.org") {
    val messageService = TelegramMsgService(this)
}
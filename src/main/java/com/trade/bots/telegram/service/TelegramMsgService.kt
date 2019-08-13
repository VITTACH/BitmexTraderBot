package com.trade.bots.telegram.service

import com.trade.bots.telegram.TelegramCore

class TelegramMsgService(telegramCore: TelegramCore) : TelegramBaseService(telegramCore) {
    fun sendMessage(chatId: String, message: String) {
        Thread {
            try { telegram.sendMessage(chatId, message, apiToken) } catch (e: Exception) {
                println(e.message)
            }
        }.start()
    }
}
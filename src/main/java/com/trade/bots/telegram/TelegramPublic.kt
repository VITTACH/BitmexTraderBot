package com.trade.bots.telegram

import java.io.IOException
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.PathParam

@Path("/")
interface TelegramPublic {
    @GET
    @Path("/bot{apiToken}/sendMessage?chat_id={chatId}&text={message}&parse_mode=html")
    @Throws(IOException::class)
    fun sendMessage(
        @PathParam("chatId") chatId: String,
        @PathParam("message") message: String,
        @PathParam("apiToken") apiToken: String
    ): String
}
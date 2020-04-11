package com.trade.exchanges.bitmex

import com.trade.exchanges.bitmex.dto.marketdata.BitmexPublicOrder
import com.trade.exchanges.bitmex.dto.marketdata.BitmexTicker
import java.io.IOException
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.QueryParam

@Path("/api/v1")
interface BitmexPublic : BitmexAuthorization {
    @GET
    @Path("/instrument")
    @Throws(IOException::class)
    fun getTicker(@QueryParam("symbol") symbol: String): List<BitmexTicker>

    @GET
    @Path("orderBook/L2")
    @Throws(IOException::class)
    fun getDepth(
            @QueryParam("symbol") pair: String,
            @QueryParam("depth") depth: Int?
    ): List<BitmexPublicOrder>
}

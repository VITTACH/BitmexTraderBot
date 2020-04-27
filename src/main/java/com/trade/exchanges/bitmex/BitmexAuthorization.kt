package com.trade.exchanges.bitmex

import com.trade.exchanges.bitmex.dto.privatedata.BitmexPosition
import com.trade.exchanges.bitmex.dto.privatedata.BitmexPrivateOrder
import com.trade.exchanges.bitmex.dto.privatedata.BitmexWallet
import si.mazi.rescu.ParamsDigest
import si.mazi.rescu.SynchronizedValueFactory
import java.io.IOException
import java.math.BigDecimal
import java.util.*
import javax.ws.rs.*
import javax.ws.rs.core.MediaType


@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
interface BitmexAuthorization {

    @GET
    @Path("user/margin")
    @Throws(BitmexException::class)
    fun getWallet(
            @HeaderParam("api-key") apiKey: String,
            @HeaderParam("api-expires") nonce: SynchronizedValueFactory<Long>,
            @HeaderParam("api-signature") paramsDigest: ParamsDigest
    ): BitmexWallet?

    @DELETE
    @Path("order")
    @Throws(BitmexException::class)
    fun cancelOpenOrder(
            @HeaderParam("api-key") apiKey: String,
            @HeaderParam("api-expires") nonce: SynchronizedValueFactory<Long>,
            @HeaderParam("api-signature") paramsDigest: ParamsDigest,
            @FormParam("orderID") orderID: String?,
            @FormParam("clOrdID") clOrdID: String? = null): List<BitmexPrivateOrder>?

    @DELETE
    @Path("order/all")
    @Throws(BitmexException::class)
    fun cancelAllOrders(
            @HeaderParam("api-key") apiKey: String,
            @HeaderParam("api-expires") nonce: SynchronizedValueFactory<Long>,
            @HeaderParam("api-signature") paramsDigest: ParamsDigest,
            @FormParam("symbol") symbol: String?): List<BitmexPrivateOrder>?

    @GET
    @Path("order")
    @Throws(IOException::class)
    fun getOrders(
            @HeaderParam("api-key") apiKey: String,
            @HeaderParam("api-expires") nonce: SynchronizedValueFactory<Long>,
            @HeaderParam("api-signature") paramsDigest: ParamsDigest,
            @QueryParam("symbol") symbol: String? = null,
            @QueryParam("filter") filter: String,
            @QueryParam("count") count: Int? = null,
            @QueryParam("start") start: Int? = null,
            @QueryParam("reverse") reverse: Boolean?,
            @QueryParam("startTime") startTime: Date? = null,
            @QueryParam("endTime") endTime: Date? = null): List<BitmexPrivateOrder>?

    @POST
    @Path("order")
    @Throws(BitmexException::class)
    fun placeOrder(
            @HeaderParam("api-key") apiKey: String,
            @HeaderParam("api-expires") nonce: SynchronizedValueFactory<Long>,
            @HeaderParam("api-signature") paramsDigest: ParamsDigest,
            @FormParam("symbol") symbol: String,
            @FormParam("side") side: String,
            @FormParam("orderQty") orderQuantity: Int?,
            @FormParam("price") price: BigDecimal?,
            @FormParam("stopPx") stopPrice: BigDecimal?,
            @FormParam("pegOffsetValue") pegOffsetValue: BigDecimal?,
            @FormParam("ordType") orderType: String,
            @FormParam("execInst") execInst: String,
            @FormParam("displayQty") displayQty: BigDecimal?,
            @FormParam("timeInForce") expire: String?,
            @FormParam("text") text: String?): BitmexPrivateOrder

    @GET
    @Path("position")
    @Throws(BitmexException::class)
    fun getPositions(
            @HeaderParam("api-key") apiKey: String,
            @HeaderParam("api-expires") nonce: SynchronizedValueFactory<Long>,
            @HeaderParam("api-signature") paramsDigest: ParamsDigest,
            @QueryParam("filter") filter: String): List<BitmexPosition>

    @POST
    @Path("order/closePosition")
    @Throws(BitmexException::class)
    fun closePosition(
            @HeaderParam("api-key") apiKey: String,
            @HeaderParam("api-expires") nonce: SynchronizedValueFactory<Long>,
            @HeaderParam("api-signature") paramsDigest: ParamsDigest,
            @FormParam("symbol") symbol: String): BitmexPosition
}
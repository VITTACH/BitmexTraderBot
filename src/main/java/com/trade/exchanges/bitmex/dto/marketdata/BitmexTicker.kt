package com.trade.exchanges.bitmex.dto.marketdata

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.math.BigInteger

data class BitmexTicker(
        @JsonProperty("symbol") val symbol: String,
        @JsonProperty("rootSymbol") val rootSymbol: String,
        @JsonProperty("state") val state: String?,
        @JsonProperty("typ") val typ: String?,
        @JsonProperty("listing") val listing: String?,
        @JsonProperty("front") val front: String?,
        @JsonProperty("expiry") val expiry: String?,
        @JsonProperty("settle") val settle: String?,
        @JsonProperty("relistInterval") val relistInterval: String?,
        @JsonProperty("inverseLeg") val inverseLeg: String?,
        @JsonProperty("sellLeg") val sellLeg: String?,
        @JsonProperty("buyLeg") val buyLeg: String?,
        @JsonProperty("positionCurrency") val positionCurrency: String?,
        @JsonProperty("underlying") val underlying: String?,
        @JsonProperty("quoteCurrency") val quoteCurrency: String?,
        @JsonProperty("underlyingSymbol") val underlyingSymbol: String?,
        @JsonProperty("reference") val reference: String?,
        @JsonProperty("referenceSymbol") val referenceSymbol: String?,
        @JsonProperty("calcInterval") val calcInterval: String?,
        @JsonProperty("publishInterval") val publishInterval: String?,
        @JsonProperty("publishTime") val publishTime: String?,
        @JsonProperty("maxOrderQty") val maxOrderQty: BigDecimal?,
        @JsonProperty("maxPrice") val maxPrice: BigDecimal?,
        @JsonProperty("lotSize") val lotSize: BigDecimal?,
        @JsonProperty("tickSize") val tickSize: BigDecimal,
        @JsonProperty("multiplier") val multiplier: BigDecimal?,
        @JsonProperty("settlCurrency") val settlCurrency: String?,
        @JsonProperty("underlyingToPositionMultiplier") val underlyingToPositionMultiplier: BigDecimal?,
        @JsonProperty("underlyingToSettleMultiplier") val underlyingToSettleMultiplier: BigDecimal?,
        @JsonProperty("quoteToSettleMultiplier") val quoteToSettleMultiplier: BigDecimal?,
        @JsonProperty("isQuanto") val isQuanto: Boolean?,
        @JsonProperty("isInverse") val isInverse: Boolean?,
        @JsonProperty("initMargin") val initMargin: BigDecimal?,
        @JsonProperty("maintMargin") val maintMargin: BigDecimal?,
        @JsonProperty("riskLimit") val riskLimit: BigInteger?,
        @JsonProperty("riskStep") val riskStep: BigInteger?,
        @JsonProperty("limit") val limit: BigDecimal?,
        @JsonProperty("capped") val capped: Boolean?,
        @JsonProperty("taxed") val taxed: Boolean?,
        @JsonProperty("deleverage") val deleverage: Boolean?,
        @JsonProperty("makerFee") val makerFee: BigDecimal?,
        @JsonProperty("takerFee") val takerFee: BigDecimal?,
        @JsonProperty("settlementFee") val settlementFee: BigDecimal?,
        @JsonProperty("insuranceFee") val insuranceFee: BigDecimal?,
        @JsonProperty("fundingBaseSymbol") val fundingBaseSymbol: String?,
        @JsonProperty("fundingQuoteSymbol") val fundingQuoteSymbol: String?,
        @JsonProperty("fundingPremiumSymbol") val fundingPremiumSymbol: String?,
        @JsonProperty("fundingTimestamp") val fundingTimestamp: String?,
        @JsonProperty("fundingInterval") val fundingInterval: String?,
        @JsonProperty("fundingRate") val fundingRate: BigDecimal?,
        @JsonProperty("indicativeFundingRate") val indicativeFundingRate: BigDecimal?,
        @JsonProperty("rebalanceTimestamp") val rebalanceTimestamp: String?,
        @JsonProperty("rebalanceInterval") val rebalanceInterval: String?,
        @JsonProperty("openingTimestamp") val openingTimestamp: String?,
        @JsonProperty("closingTimestamp") val closingTimestamp: String?,
        @JsonProperty("sessionInterval") val sessionInterval: String?,
        @JsonProperty("prevClosePrice") val prevClosePrice: BigDecimal?,
        @JsonProperty("limitDownPrice") val limitDownPrice: BigDecimal?,
        @JsonProperty("limitUpPrice") val limitUpPrice: BigDecimal?,
        @JsonProperty("bankruptLimitDownPrice") val bankruptLimitDownPrice: BigDecimal?,
        @JsonProperty("bankruptLimitUpPrice") val bankruptLimitUpPrice: BigDecimal?,
        @JsonProperty("prevTotalVolume") val prevTotalVolume: BigDecimal?,
        @JsonProperty("totalVolume") val totalVolume: BigDecimal?,
        @JsonProperty("volume") val volume: BigDecimal?,
        @JsonProperty("volume24h") val volume24h: BigDecimal?,
        @JsonProperty("prevTotalTurnover") val prevTotalTurnover: BigInteger?,
        @JsonProperty("totalTurnover") val totalTurnover: BigInteger?,
        @JsonProperty("turnover") val turnover: BigInteger?,
        @JsonProperty("turnover24h") val turnover24h: BigInteger?,
        @JsonProperty("prevPrice24h") val prevPrice24h: BigInteger?,
        @JsonProperty("vwap") val vwap: BigInteger?,
        @JsonProperty("highPrice") val highPrice: BigDecimal?,
        @JsonProperty("lowPrice") val lowPrice: BigDecimal?,
        @JsonProperty("lastPrice") val lastPrice: BigDecimal?,
        @JsonProperty("lastPriceProtected") val lastPriceProtected: BigDecimal?,
        @JsonProperty("lastTickDirection") val lastTickDirection: String?,
        @JsonProperty("lastChangePcnt") val lastChangePcnt: BigDecimal?,
        @JsonProperty("bidPrice") val bidPrice: BigDecimal?,
        @JsonProperty("midPrice") val midPrice: BigDecimal?,
        @JsonProperty("askPrice") val askPrice: BigDecimal?,
        @JsonProperty("impactBidPrice") val impactBidPrice: BigDecimal?,
        @JsonProperty("impactMidPrice") val impactMidPrice: BigDecimal?,
        @JsonProperty("impactAskPrice") val impactAskPrice: BigDecimal?,
        @JsonProperty("hasLiquidity") val hasLiquidity: Boolean?,
        @JsonProperty("openInterest") val openInterest: BigDecimal?,
        @JsonProperty("openValue") val openValue: BigDecimal?,
        @JsonProperty("fairMethod") val fairMethod: String?,
        @JsonProperty("fairBasisRate") val fairBasisRate: BigDecimal?,
        @JsonProperty("fairBasis") val fairBasis: BigDecimal?,
        @JsonProperty("fairPrice") val fairPrice: BigDecimal?,
        @JsonProperty("markMethod") val markMethod: String?,
        @JsonProperty("markPrice") val markPrice: BigDecimal?,
        @JsonProperty("indicativeTaxRate") val indicativeTaxRate: BigDecimal?,
        @JsonProperty("indicativeSettlePrice") val indicativeSettlePrice: BigDecimal?,
        @JsonProperty("settledPrice") val settledPrice: BigDecimal?,
        @JsonProperty("timestamp") val timestamp: String?)
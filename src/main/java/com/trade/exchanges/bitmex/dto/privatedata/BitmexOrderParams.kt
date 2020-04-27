package com.trade.exchanges.bitmex.dto.privatedata

import java.math.BigDecimal

data class BitmexOrderParams(
        val leverage: BigDecimal? = null,
        val icebergQty: BigDecimal? = null,
        val expire: BitmexOrderExpire?,
        val hidden: Boolean? = null,
        val postOnly: Boolean?,
        val reduceOnly: Boolean?,
        val trigger: BitmexOrderTrigger? = null,
        val closeOnTrigger: Boolean?,
        val pegOffsetValue: BigDecimal? = null,
        val text: String? = null
)
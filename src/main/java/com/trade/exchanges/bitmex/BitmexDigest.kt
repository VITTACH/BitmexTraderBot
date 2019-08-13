package com.trade.exchanges.bitmex

import com.trade.exchanges.core.digist.BaseParamsDigist
import com.trade.exchanges.core.digist.DigistUtils.bytesToHex
import si.mazi.rescu.RestInvocation
import javax.ws.rs.HeaderParam

class BitmexDigest(secretKeyBase64: String) : BaseParamsDigist(secretKeyBase64, HMAC_SHA_256) {

    override fun digestParams(invocation: RestInvocation): String {
        val nonce = invocation.getParamValue(HeaderParam::class.java, "api-expires").toString()
        val urlPath = invocation.invocationUrl.split(invocation.baseUrl.toRegex())
                .dropLastWhile { it.isEmpty() }.toTypedArray()[1]
        val payload = invocation.httpMethod + urlPath + nonce + invocation.requestBody

        return bytesToHex(mac.doFinal(payload.toByteArray())).toLowerCase()
    }

    fun createWsSign(nonce: Long): String {
        val payload = "GET/realtime$nonce"
        return bytesToHex(mac.doFinal(payload.toByteArray())).toLowerCase()
    }

    companion object {
        fun createInstance(secretKeyBase64: String): BitmexDigest {
            return BitmexDigest(secretKeyBase64)
        }
    }

}

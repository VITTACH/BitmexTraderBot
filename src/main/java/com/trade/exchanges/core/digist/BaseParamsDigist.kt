package com.trade.exchanges.core.digist

import net.iharder.Base64
import si.mazi.rescu.ParamsDigest
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

abstract class BaseParamsDigist : ParamsDigest {

    private val threadLocalMac: ThreadLocal<Mac>
    protected val mac: Mac get() = threadLocalMac.get()

    @Throws(IllegalArgumentException::class)
    protected constructor(secretKeyBase64: String, hmacString: String) {
        try {
            val secretKey = SecretKeySpec(secretKeyBase64.toByteArray(charset("UTF-8")), hmacString)
            threadLocalMac = object : ThreadLocal<Mac>() {
                override fun initialValue(): Mac {
                    try {
                        return Mac.getInstance(hmacString).apply { init(secretKey) }
                    } catch (excp: InvalidKeyException) {
                        throw IllegalArgumentException("Invalid key for hmac initialization.", excp)
                    } catch (e: NoSuchAlgorithmException) {
                        throw RuntimeException("Illegal algorithm for post body digist implements.")
                    }
                }
            }
        } catch (ex: UnsupportedEncodingException) {
            throw RuntimeException("Illegal encoding, check the code.", ex)
        }
    }

    companion object {

        val HMAC_SHA_512 = "HmacSHA512"
        val HMAC_SHA_384 = "HmacSHA384"
        val HMAC_SHA_256 = "HmacSHA256"
        val HMAC_SHA_1 = "HmacSHA1"

        protected fun decodeBase64(secretKey: String): ByteArray {
            try {
                return Base64.decode(secretKey)
            } catch (e: IOException) {
                throw RuntimeException("Can't decode secret as Base 64", e)
            }
        }
    }
}
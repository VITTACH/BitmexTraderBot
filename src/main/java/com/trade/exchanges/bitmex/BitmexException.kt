package com.trade.exchanges.bitmex

import com.fasterxml.jackson.annotation.JsonProperty
import java.lang.RuntimeException

data class BitmexExceptionInfo(@JsonProperty("message") val message : String?, @JsonProperty("name") val name : String?)

data class BitmexException(@JsonProperty("error") val info: BitmexExceptionInfo): RuntimeException()
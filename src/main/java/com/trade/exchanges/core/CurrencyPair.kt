package com.trade.exchanges.core

class CurrencyPair : Comparable<CurrencyPair> {
    val base: String
    val counter: String

    constructor(base: String, counter: String) {
        this.base = base
        this.counter = counter
    }

    constructor(id: String) {
        val split = id.indexOf("/")
        if (split < 1) {
            throw IllegalArgumentException("Could not parse the currency with: '$id'")
        }
        counter = id.substring(split + 1)
        base = id.substring(0, split)
    }

    fun toString(separator: String): String {
        return base + separator + counter
    }

    override fun toString(): String {
        return base + counter
    }

    override fun compareTo(other: CurrencyPair): Int {
        return (base.compareTo(other.base) shl 16) + counter.compareTo(other.counter)
    }
}

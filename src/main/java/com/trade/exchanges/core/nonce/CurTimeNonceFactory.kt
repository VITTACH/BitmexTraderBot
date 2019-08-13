package com.trade.exchanges.core.nonce

import si.mazi.rescu.SynchronizedValueFactory

class CurTimeNonceFactory : SynchronizedValueFactory<Long> {
    override fun createValue() = System.currentTimeMillis();
}

package com.trade.exchanges.bitmex.dto.privatedata

enum class BitmexOrderExpire {
    GoodTillCancel,
    ImmediateOrCancel,
    FillOrKill;
}

enum class BitmexOrderTrigger {
    MarkPrice,
    LastPrice,
    IndexPrice
}

enum class BitmexOrderType {
    Limit,
    Market,
    StopLimit,
    Stop,
    LimitIfTouched,
    MarketIfTouched,
    Pegged
}
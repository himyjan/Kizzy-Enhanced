package com.my.kizzy.data.rpc

/** Start and end epoch timestamps (in milliseconds) for a Rich Presence timeline. */
data class Timestamps(
    val start: Long? = null,
    val end: Long? = null,
)
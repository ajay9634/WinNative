package com.winlator.cmod.feature.stores.common

/** Identifies which store emitted a session event. */
enum class Store {
    EPIC,
    GOG,
    STEAM,
}

/** Session-lifecycle events that can surface from any store integration to the UI. */
sealed class StoreSessionEvent {
    abstract val store: Store

    /** Stored refresh credentials are dead — user must sign in again. */
    data class SessionExpired(
        override val store: Store,
        val reason: String,
    ) : StoreSessionEvent()

    /** A silent token refresh just succeeded — purely informational. */
    data class SessionRefreshed(
        override val store: Store,
    ) : StoreSessionEvent()
}

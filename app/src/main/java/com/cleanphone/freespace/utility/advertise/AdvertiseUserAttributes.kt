package com.cleanphone.freespace.utility.advertise

import android.util.Log
import com.mar2sdk.core.Core

object AdvertiseUserAttributes {
    private const val TAG = "AdvertiseUserAttributes"

    fun setUser(params: Map<String, Any>, tag: String) {
        params.forEach { (key, value) ->
            runCatching {
                when (tag) {
                    "user_setOnce" -> Core.setUserOnceAttr(key, value.toString())
                    "user_set" -> Core.setUserAttr(key, value)
                    else -> Log.w(TAG, "Unknown user attribute tag: $tag")
                }
            }.onFailure { throwable ->
                Log.w(TAG, "setUser failed for $key with tag $tag", throwable)
            }
        }
    }
}

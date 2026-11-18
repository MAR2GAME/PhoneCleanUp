package com.cleanphone.freespace.utility.base.config

import com.cleanphone.freespace.utility.BuildConfig

object AppConfig {
    var isDebug= BuildConfig.DEBUG
    const val TERMS_URL = "https://sites.google.com/view/phone-clean-up-free-space/home"
    const val PRIVACY_URL = "https://sites.google.com/view/phonecleanupfreespace/home"
    const val  IS_SCAN_PHONE="is_scan_phone"

    const val IS_SP_FIRST_SET="sp_first_set"

    const val PARAM_SCENE = "scene"

    const val Singular_Api_Key = "mar2game_f7b9272a"
    const val Singular_Secret = "72b3df2ee5d0a64a6c404ce01937c3d6"

    const val  THINKING_APPID="140a67622f9641948b96dfa33df0f0ba"
    const val  THINKING_URL="https://mar2.top"

    const val NEXT_TAG="next_tag"
    const val TASK_TITLE="task_title"
    const val TASK_CONTENT="task_content"
    const val ADVHost = "https://api.cigars-of-cuba.top"

    val ParseTokenUrl: String
        get() = "$ADVHost/parseToken"

    val IPInfoV2Url: String
        get() = "$ADVHost/getIpInfoV2"

    const val PROCESS_TIME = "config_process_time_setting"
}
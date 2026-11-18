package com.cleanphone.freespace.utility.base

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.cleanphone.freespace.utility.utils.MyShortcutManager
import com.cleanphone.freespace.utility.utils.SafeChecker
import com.cleanphone.freespace.utility.utils.SdkInitUtils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class MyApp : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
//        applicationScope.launch {
//            val isChek = SafeChecker.performEssentialCheck(this@MyApp)
//            if (!isChek) {
//                withContext(Dispatchers.Main) {
//                    SafeChecker.forceExit()
//                }
//            }
//        }
    }

    private var isInitialized = false
    fun isInitialized(): Boolean = isInitialized
    private var activityCount = 0

    companion object {
        @SuppressLint("StaticFieldLeak")
        private var _instance: MyApp? = null
        val instance: MyApp
            get() = _instance ?: throw IllegalStateException("Application not initialized")
    }


    override fun onCreate() {
        super.onCreate()
        _instance = this
        SdkInitUtils.initSDKAsync(this)
        initShortcutsSafe(this)
        launchSecurityChecks()
    }
    private fun initShortcutsSafe(context: Application) {
        try {
            MyShortcutManager.initDynamicShortcuts(context)
        } catch (e: Exception) {
        }
    }


    private fun launchSecurityChecks() {
        applicationScope.launch {
            delay(1000) // 延迟1秒，让应用先启动
            SafeChecker.performAuxiliaryChecks()
        }
    }

    override fun onTerminate() {
        // 清理资源
        cleanup()
        super.onTerminate()
    }

    private fun cleanup() {
        _instance = null
        SdkInitUtils.cleanup()
        applicationScope.cancel()
    }

}

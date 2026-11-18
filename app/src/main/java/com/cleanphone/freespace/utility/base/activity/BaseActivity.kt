package com.cleanphone.freespace.utility.base.activity

import android.content.Context
import android.os.Bundle
import com.cleanphone.freespace.utility.advertise.AdvertisePageMediator
import com.cleanphone.freespace.utility.advertise.AdvertiseUserAttributes
import com.cleanphone.freespace.utility.base.command.checkNotificationPermission
import com.cleanphone.freespace.utility.base.command.hasStoragePermission
import com.cleanphone.freespace.utility.utils.ActivityManagerUtils
import com.cleanphone.freespace.utility.view.ProgressDialog


open class BaseActivity : com.mar2sdk.impl.BaseActivity() {


    var progressDialog: ProgressDialog? = null

    fun handleLoadingState(showLoading: Boolean) {
        if (progressDialog == null) {
            progressDialog = ProgressDialog(this)
        }
        if (showLoading) {
            progressDialog?.showProgress()
        } else {
            progressDialog?.hideProgress()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityManagerUtils.addActivity(this)
    }

    override fun onResume() {
        super.onResume()
        AdvertisePageMediator.updateForegroundHostState(this, isResumed = true)
    }

    override fun onPause() {
        AdvertisePageMediator.updateForegroundHostState(this, isResumed = false)
        super.onPause()
    }

    override fun onDestroy() {
        progressDialog?.hideProgress()
        progressDialog = null
        AdvertiseUserAttributes.setUser(
            mapOf("has_notification_permission" to checkNotificationPermission(this)),
            "user_set",
        )
        AdvertiseUserAttributes.setUser(
            mapOf("has_allfile_permission" to hasStoragePermission()),
            "user_set",
        )
        ActivityManagerUtils.removeActivity(this)
        super.onDestroy()
    }

}

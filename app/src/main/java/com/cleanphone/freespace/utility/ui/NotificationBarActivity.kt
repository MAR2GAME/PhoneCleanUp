package com.cleanphone.freespace.utility.ui

import android.os.Bundle
import android.text.TextUtils
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.recyclerview.widget.LinearLayoutManager
import com.cleanphone.freespace.utility.R
import com.cleanphone.freespace.utility.adapter.NotificationRecordAdapter
import com.cleanphone.freespace.utility.advertise.AdvertisePageMediator
import com.cleanphone.freespace.utility.base.activity.BaseViewActivity
import com.cleanphone.freespace.utility.base.command.log
import com.cleanphone.freespace.utility.base.command.readyGo
import com.cleanphone.freespace.utility.base.command.readyGoThenKill
import com.cleanphone.freespace.utility.base.command.safeClick
import com.cleanphone.freespace.utility.base.config.AppConfig
import com.cleanphone.freespace.utility.bean.NotificationBarEvent
import com.cleanphone.freespace.utility.bean.NotificationDashboardState
import com.cleanphone.freespace.utility.bean.ToScanEvent
import com.cleanphone.freespace.utility.databinding.ActivityNotificationBarBinding
import com.cleanphone.freespace.utility.utils.NotificationBarStore
import com.cleanphone.freespace.utility.utils.SizeUtil
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import kotlin.jvm.java

class NotificationBarActivity : BaseViewActivity<ActivityNotificationBarBinding>(){
    private lateinit var notificationRecordAdapter: NotificationRecordAdapter


    override fun initializeActivity() {
        super.initializeActivity()
        EventBus.getDefault().register(this)
        setupRecyclerView()
        setupClickListeners()
        setupBackPressHandler()
        val details=intent.extras?.getString("details")
        val referrer_name=intent.extras?.getString("referrer_name")
        if(!TextUtils.isEmpty( details)){
            log("enter_corefeatures_page",mapOf("details" to details!! ,"referrer_name" to referrer_name!!))
        }
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this,object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onBack()
            }
        })
    }

    fun onBack(){
        readyGoThenKill(MainActivity::class.java)
    }
    private fun setupClickListeners() {
        binding.llBack.safeClick {   onBack() }
        binding.ivSettings.safeClick { readyGo(NotificationBarConfigActivity::class.java) }
        binding.btnTidyUp.safeClick {
            val clearedCount = NotificationBarStore.clearBlockedNotifications(this)
            AdvertisePageMediator.showNotificationCleanFinishInterstitial(this){
                val bundle = Bundle()
                bundle.putString(AppConfig.NEXT_TAG, getString(R.string.notification_bar))
                bundle.putString(AppConfig.TASK_TITLE,getString(R.string.notification_cleared_count, clearedCount) )
                bundle.putString(AppConfig.TASK_CONTENT, getString(R.string.have_been_cleared))
                readyGoThenKill(TaskListActivity::class.java, bundle)
            }
        }
    }


    override fun onResume() {
        super.onResume()
        NotificationBarStore.refresh(this)
        NotificationBarStore.observeState().observe(this, ::renderState)
    }


    private fun renderState(state: NotificationDashboardState) {
//        if (!state.listenerEnabled || !state.blockingEnabled || !state.hasConfiguredTargets) {
//            readyGo(NotificationBarConfigActivity::class.java)
//            finish()
//            return
//        }
        binding.tvSummaryCount.text ="${state.totalBlockedCount}"
        binding.tvSummaryHint.text =
            getString(R.string.notification_showing_up_to, NotificationBarStore.MAX_BLOCKED_RECORDS)
        val records = state.blockedRecords.sortedByDescending { it.postedAt }
        notificationRecordAdapter.submitList(records)
        binding.tvEmpty.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
        binding.btnTidyUp.isEnabled = records.isNotEmpty()
        binding.btnTidyUp.alpha = if (records.isEmpty()) 0.5f else 1f
    }

    private fun setupRecyclerView() {
        notificationRecordAdapter = NotificationRecordAdapter(this)
        binding.rvNotifications.apply {
            adapter = notificationRecordAdapter
            layoutManager = LinearLayoutManager(this@NotificationBarActivity)
            setHasFixedSize(true)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: NotificationBarEvent) {
        finish()
    }

    override fun onDestroy() {
        EventBus.getDefault().unregister(this)
        super.onDestroy()
    }

}
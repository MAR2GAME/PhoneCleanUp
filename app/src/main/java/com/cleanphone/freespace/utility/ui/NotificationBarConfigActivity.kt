package com.cleanphone.freespace.utility.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil.setContentView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.cleanphone.freespace.utility.R
import com.cleanphone.freespace.utility.adapter.NotificationBlockedAppAdapter
import com.cleanphone.freespace.utility.base.activity.BaseViewActivity
import com.cleanphone.freespace.utility.base.command.log
import com.cleanphone.freespace.utility.base.command.readyGoThenKill
import com.cleanphone.freespace.utility.base.command.safeClick
import com.cleanphone.freespace.utility.bean.NotificationAppEntry
import com.cleanphone.freespace.utility.bean.NotificationBarEvent
import com.cleanphone.freespace.utility.databinding.ActivityNotificationBarConfigBinding
import com.cleanphone.freespace.utility.utils.ActivityManagerUtils
import com.cleanphone.freespace.utility.utils.NotificationAccessUtils
import com.cleanphone.freespace.utility.utils.NotificationBarStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import kotlin.toString

class NotificationBarConfigActivity : BaseViewActivity<ActivityNotificationBarConfigBinding>() {

    private lateinit var blockedAppAdapter: NotificationBlockedAppAdapter
    private var appEntries: List<NotificationAppEntry> = emptyList()

    private var isLoad=false

    private var isChecked=false

    override fun initializeActivity() {
        super.initializeActivity()
        setupClickListeners()
        setupRecyclerView()
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
        if(ActivityManagerUtils.hasActivity(MainActivity::class.java)){
            finish()
        }else{
            readyGoThenKill(MainActivity::class.java)
        }

    }

    override fun onResume() {
        super.onResume()
        if(!isLoad){
            isLoad=true
            loadInstalledApps()
        }else{
            renderState()
        }
    }

    private fun loadInstalledApps() {
        handleLoadingState(true)
        lifecycleScope.launch(Dispatchers.IO) {
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolvedApps = packageManager.queryIntentActivities(
                launcherIntent,
                PackageManager.MATCH_ALL
            )
            val entries = resolvedApps
                .asSequence()
                .map { it.activityInfo.packageName }
                .distinct()
                .filter { it != packageName }
                .map { pkg ->
                    NotificationAppEntry(
                        packageName = pkg,
                        appName = runCatching {
                            val appInfo = packageManager.getApplicationInfo(pkg, 0)
                            packageManager.getApplicationLabel(appInfo).toString()
                        }.getOrDefault(pkg),
                        icon = runCatching {
                            val appInfo = packageManager.getApplicationInfo(pkg, 0)
                            packageManager.getApplicationIcon(appInfo)
                        }.getOrNull()
                    )
                }
                .sortedBy { it.appName.lowercase() }
                .toList()

            withContext(Dispatchers.Main) {
                handleLoadingState(false)
                appEntries = entries
                renderState()
            }
        }
    }


    private fun setupClickListeners() {
        binding.llBack.safeClick {   onBack()}
        binding.ivBlockedNotifications.setOnClickListener {
            if(!NotificationAccessUtils.isNotificationListenerEnabled(this)){
                NotificationAccessUtils.openNotificationListenerSettings(this)
            }else{
                isChecked=!isChecked
                NotificationBarStore.setBlockingEnabled(this, isChecked)
                renderState()

            }
        }

    }



    private fun renderState() {
        val state = NotificationBarStore.loadState(this)
        if(state.listenerEnabled && state.blockingEnabled){
            binding.ivBlockedNotifications.setImageResource(R.mipmap.ic_bar_check)
        }else{
            binding.ivBlockedNotifications.setImageResource(R.mipmap.ic_bar_nor)
            EventBus.getDefault().post(NotificationBarEvent(false))
        }
        blockedAppAdapter.submitList(appEntries, state.blockedPackages)
    }

    private fun setupRecyclerView() {
        blockedAppAdapter = NotificationBlockedAppAdapter { appEntry ->
            if (!NotificationAccessUtils.isNotificationListenerEnabled(this)) {
                NotificationAccessUtils.openNotificationListenerSettings(this)
                return@NotificationBlockedAppAdapter
            }
            if (!NotificationBarStore.isBlockingEnabled()) {
                NotificationBarStore.setBlockingEnabled(this, true)
            }
            NotificationBarStore.toggleBlockedPackage(this, appEntry.packageName)
            renderState()
        }

        binding.rvAppList.apply {
            adapter = blockedAppAdapter
            layoutManager = LinearLayoutManager(this@NotificationBarConfigActivity)
            setHasFixedSize(true)
        }
    }


}
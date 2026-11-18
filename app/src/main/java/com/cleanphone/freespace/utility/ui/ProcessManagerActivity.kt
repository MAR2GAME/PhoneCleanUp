package com.cleanphone.freespace.utility.ui

import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil.setContentView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.chad.library.adapter.base.BaseMultiItemQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.cleanphone.freespace.utility.R
import com.cleanphone.freespace.utility.base.activity.BaseMvvmActivity
import com.cleanphone.freespace.utility.base.activity.BaseViewActivity
import com.cleanphone.freespace.utility.base.command.log
import com.cleanphone.freespace.utility.base.command.openAppSettings
import com.cleanphone.freespace.utility.base.command.readyGo
import com.cleanphone.freespace.utility.base.command.readyGoThenKill
import com.cleanphone.freespace.utility.base.command.safeClick
import com.cleanphone.freespace.utility.base.config.AppConfig
import com.cleanphone.freespace.utility.bean.ProcessEvent
import com.cleanphone.freespace.utility.bean.RunningAppInfo
import com.cleanphone.freespace.utility.databinding.ActivityProcessManagerBinding
import com.cleanphone.freespace.utility.viewmodel.ProcessManagerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import kotlin.jvm.java

class ProcessManagerActivity : BaseMvvmActivity<ActivityProcessManagerBinding, ProcessManagerViewModel> (){
    private lateinit var appAdapter: AppProcessAdapter
    override fun init() {
        initializeEventBus()
        setupObservers()
        setupClickListeners()
        setupBackPressHandler()
        initializeAdapter()
        val details=intent.extras?.getString("details")
        val referrer_name=intent.extras?.getString("referrer_name")
        if(!TextUtils.isEmpty( details)){
            log("enter_corefeatures_page",mapOf("details" to details!! ,"referrer_name" to referrer_name!!))
        }



    }
    private fun initializeEventBus() {
        EventBus.getDefault().register(this)
    }

    private fun initializeAdapter() {
        appAdapter = AppProcessAdapter { appInfo ->
            openAppSettings(this@ProcessManagerActivity, appInfo.packageName)
            navigateToSettingHintAfterDelay()
        }
        binding.rcProcess.apply {
            adapter = appAdapter
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this@ProcessManagerActivity, RecyclerView.VERTICAL, false)
        }
    }

    private fun navigateToSettingHintAfterDelay() {
        lifecycleScope.launch {
            delay(DELAY_BEFORE_NAVIGATION)
            val bundle = Bundle().apply {
                putString(AppConfig.NEXT_TAG, getString(R.string.process_manager))
            }
            readyGo(ToSettingHintActivity::class.java, bundle)
        }
    }

    private fun setupClickListeners() {
        binding.llBack.safeClick {   onBack()}
        binding.btStart.safeClick { viewModel.onStartClicked() }
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

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    private fun setupObservers() {
        viewModel.memoryUsageText.observe(this) { memoryUsage ->
            binding.tvMemoryUsage.text = memoryUsage
        }
        viewModel.appsCountText.observe(this) { appsCount ->
            binding.tvSize.text = appsCount
        }
        viewModel.isEmptyDataVisible.observe(this) { isEmpty ->
            binding.rlNodata.visibility = if (isEmpty) View.VISIBLE else View.GONE
        }
        viewModel.isLoading.observe(this) { showLoading ->
            handleLoadingState(showLoading)
        }
        viewModel.appsList.observe(this) { apps ->
            appAdapter.setList(apps)
        }
        viewModel.navigationEvent.observe(this) { navigationData ->
            when (navigationData.destination) {
                ProcessManagerViewModel.NavigationDestination.MAIN -> {
                    readyGoThenKill(navigationData.targetClass)
                }
                ProcessManagerViewModel.NavigationDestination.NEXT_STEP -> {
                    readyGoThenKill(navigationData.targetClass, navigationData.bundle)
                }
                ProcessManagerViewModel.NavigationDestination.SETTING_HINT -> {
                    readyGo(navigationData.targetClass, navigationData.bundle)
                }
            }
        }
    }


    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: ProcessEvent) {
        viewModel.onProcessEventReceived(event)
        EventBus.getDefault().removeStickyEvent(event)
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
    }


    private inner class AppProcessAdapter(
        private val onStopClick: (RunningAppInfo) -> Unit
    ) : BaseMultiItemQuickAdapter<RunningAppInfo, BaseViewHolder>() {
        init {
            addItemType(0, R.layout.item_process)
        }

        override fun convert(holder: BaseViewHolder, item: RunningAppInfo) {
            with(holder) {
                setText(R.id.tv_name, item.appName)
                Glide.with(context)
                    .load(item.icon)
                    .into(getView(R.id.iv_app))

                getView<View>(R.id.bt_stop).safeClick {
                    onStopClick(item)
                }
            }
        }
    }

    companion object {
        private const val DELAY_BEFORE_NAVIGATION = 200L
    }

}
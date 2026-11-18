package com.cleanphone.freespace.utility.ui

import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.Button
import android.widget.ImageView
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
import com.cleanphone.freespace.utility.base.command.log
import com.cleanphone.freespace.utility.base.command.openAppSettings
import com.cleanphone.freespace.utility.base.command.readyGo
import com.cleanphone.freespace.utility.base.command.readyGoThenKill
import com.cleanphone.freespace.utility.base.command.safeClick
import com.cleanphone.freespace.utility.base.config.AppConfig
import com.cleanphone.freespace.utility.bean.AppInfo
import com.cleanphone.freespace.utility.bean.InstallAppEvent
import com.cleanphone.freespace.utility.databinding.ActivityAppManagerBinding
import com.cleanphone.freespace.utility.receiver.CustomAppUninstallReceiver
import com.cleanphone.freespace.utility.utils.AppPrefsUtils
import com.cleanphone.freespace.utility.utils.SizeUtil
import com.cleanphone.freespace.utility.viewmodel.AppManagerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale
import kotlin.collections.get

class AppManagerActivity : BaseMvvmActivity<ActivityAppManagerBinding, AppManagerViewModel>() {


    companion object {
        private const val DELAY_BEFORE_SETTING = 300L
    }

    private lateinit var appAdapter: AppListAdapter
    private val uninstallReceiver = CustomAppUninstallReceiver()
    private val dateFormatter by lazy(LazyThreadSafetyMode.NONE) {
        DateFormat.getDateInstance(DateFormat.MEDIUM, resources.configuration.locales[0])
    }

    override fun init() {
        initializeEventBus()
        initializeViews()
        initializeUninstallReceiver()
        initializeAdapter()
        setupObservers()
        val details=intent.extras?.getString("details")
        val referrer_name=intent.extras?.getString("referrer_name")
        if(!TextUtils.isEmpty( details)){
            log("enter_corefeatures_page",mapOf("details" to details!! ,"referrer_name" to referrer_name!!))
        }
    }


    fun initializeEventBus() {
        EventBus.getDefault().register(this)
    }

    fun initializeViews() {
        binding.llBack.safeClick { finish() }
        binding.btStart.safeClick { navigateToNextActivity() }
        binding.rlSize.setOnClickListener { viewModel.handleSizeSortClick() }
        binding.rlTime.setOnClickListener { viewModel.handleTimeSortClick() }
    }



    private fun initializeUninstallReceiver() {
        uninstallReceiver.addOnUninstallListener(object :
            CustomAppUninstallReceiver.OnUninstallListener {
            override fun onAppUninstalled(packageName: String) {
                viewModel.handleAppUninstalled(packageName)
            }
        })
        registerReceiver(
            uninstallReceiver,
            IntentFilter(Intent.ACTION_PACKAGE_REMOVED).apply {
                addDataScheme("package")
            }
        )
    }

    private fun initializeAdapter() {
        appAdapter = AppListAdapter(dateFormatter).apply {
            setOnItemUninstallClickListener { appInfo ->
                handleAppUninstall(appInfo)
            }
        }

        binding.rcApp.apply {
            adapter = appAdapter
            setHasFixedSize(true)
            layoutManager =
                LinearLayoutManager(this@AppManagerActivity, RecyclerView.VERTICAL, false)
        }
    }

    private fun setupObservers() {
        viewModel.appList.observe(this) { appList ->
            appAdapter.setList(appList)
            updateEmptyState(appList.isEmpty())
        }
        viewModel.sortUIState.observe(this) { state ->
            updateSortUI(state)
        }
    }



    private fun navigateToNextActivity() {
        val bundle = Bundle().apply {
            putString(AppConfig.NEXT_TAG, getString(R.string.app_manager))
        }
        readyGoThenKill(NextStepActivity::class.java, bundle)
    }

    private fun updateSortUI(state: AppManagerViewModel.SortUIState) {
        binding.ivSizeSort.setImageResource(state.sizeIconRes)
        binding.ivTimeSort.setImageResource(state.timeIconRes)
    }

    private fun handleAppUninstall(appInfo: AppInfo) {
        openAppSettings(this, appInfo.packageName)
        lifecycleScope.launch {
            delay(DELAY_BEFORE_SETTING)
            val bundle = Bundle().apply {
                putString(AppConfig.NEXT_TAG, getString(R.string.app_manager))
            }
            readyGo(ToSettingHintActivity::class.java, bundle)
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: InstallAppEvent) {
        viewModel.handleInstallAppEvent(event.data)
        binding.tvSize.text="${event.data.size}"
        EventBus.getDefault().removeStickyEvent(event)
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.rlNodata.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
        unregisterReceiver(uninstallReceiver)
        uninstallReceiver.clearAllListeners()
        appAdapter.clearData()
    }
}

private class AppListAdapter(
    private val dateFormatter: DateFormat
) : BaseMultiItemQuickAdapter<AppInfo, BaseViewHolder>() {
    init {
        addItemType(0, R.layout.item_installapp)
    }

    private var uninstallClickListener: ((AppInfo) -> Unit)? = null

    fun setOnItemUninstallClickListener(listener: (AppInfo) -> Unit) {
        uninstallClickListener = listener
    }

    override fun convert(holder: BaseViewHolder, item: AppInfo) {
        with(holder) {
            setText(R.id.tv_name, item.name)
            setText(
                R.id.tv_information,
                "${formatSize(item.size, locale = context.resources.configuration.locales[0])} " +
                        dateFormatter.format(Date(item.firstInstallTime))
            )
            Glide.with(context)
                .load(item.icon)
                .placeholder(R.mipmap.ic_launcher)
                .error(R.mipmap.ic_launcher)
                .into(getView<ImageView>(R.id.iv_app))

            getView<Button>(R.id.bt_uninstall).safeClick {
                uninstallClickListener?.invoke(item)
            }
        }
    }


    fun formatSize(
        bytes: Long,
        decimalPlaces: Int = 2,
        locale: Locale = Locale.getDefault(),
        trimTrailingZeros: Boolean = false
    ): String {
        if (bytes < 0L) return bytes.toString()

        val (divisor, unit) = when {
            bytes >= BYTES_PER_GB -> BYTES_PER_GB to "GB"
            bytes >= BYTES_PER_MB -> BYTES_PER_MB to "MB"
            bytes >= BYTES_PER_KB -> BYTES_PER_KB to "KB"
            else -> return "0 KB"
        }

        val value = BigDecimal.valueOf(bytes)
            .divide(BigDecimal.valueOf(divisor), INTERMEDIATE_SCALE, RoundingMode.DOWN)
            .setScale(decimalPlaces, RoundingMode.HALF_UP)
        val numberFormat = NumberFormat.getNumberInstance(locale).apply {
            isGroupingUsed = false
            minimumFractionDigits = if (trimTrailingZeros) 0 else decimalPlaces
            maximumFractionDigits = decimalPlaces
            roundingMode = RoundingMode.HALF_UP
        }
        return "${numberFormat.format(value)}$unit"
    }

   val BYTES_PER_KB = 1024L
   val BYTES_PER_MB = BYTES_PER_KB * 1024L
    val BYTES_PER_GB = BYTES_PER_MB * 1024L
   val INTERMEDIATE_SCALE = 3

    fun clearData() {
        setList(emptyList())
    }

}
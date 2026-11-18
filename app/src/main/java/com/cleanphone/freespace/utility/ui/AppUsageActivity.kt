package com.cleanphone.freespace.utility.ui

import android.annotation.SuppressLint
import android.app.ProgressDialog.show
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil.setContentView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.cleanphone.freespace.utility.R
import com.cleanphone.freespace.utility.adapter.AppUsageAdapter
import com.cleanphone.freespace.utility.base.activity.BaseMvvmActivity
import com.cleanphone.freespace.utility.base.activity.BaseViewActivity
import com.cleanphone.freespace.utility.base.command.log
import com.cleanphone.freespace.utility.base.command.openAppInfoSettings
import com.cleanphone.freespace.utility.base.command.readyGoThenKill
import com.cleanphone.freespace.utility.base.command.safeClick
import com.cleanphone.freespace.utility.bean.AppUsageDonutSlice
import com.cleanphone.freespace.utility.bean.AppUsageEntry
import com.cleanphone.freespace.utility.bean.AppUsageListItem
import com.cleanphone.freespace.utility.bean.AppUsageMetric
import com.cleanphone.freespace.utility.bean.AppUsageRange
import com.cleanphone.freespace.utility.bean.AppUsageSnapshot
import com.cleanphone.freespace.utility.databinding.ActivityAppUsageBinding
import com.cleanphone.freespace.utility.utils.AppUsageAnalyzer
import com.cleanphone.freespace.utility.utils.AppUsageFormatter
import com.cleanphone.freespace.utility.utils.UsageStatsHelper
import com.cleanphone.freespace.utility.view.UsageDataPermissionDialog
import com.cleanphone.freespace.utility.viewmodel.AppUsageViewModel
import com.google.android.material.tabs.TabItem
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.compareTo
import kotlin.text.toFloat


class AppUsageActivity : BaseMvvmActivity<ActivityAppUsageBinding, AppUsageViewModel>(){

    private var tabData = mutableListOf<AppUsageActivity.TabItem>()
    private var usageDataPermissionDialog: UsageDataPermissionDialog? = null
    private lateinit var appUsageAdapter: AppUsageAdapter
    private var currentRange = AppUsageRange.TODAY
    private var currentMetric = AppUsageMetric.HOURS_SPENT
    private var currentSnapshot: AppUsageSnapshot? = null

    override fun init() {
        initTab()
        checkPermissionAndRedirect()
        setupClickListeners()
        setupRecyclerView()
        val details=intent.extras?.getString("details")
        val referrer_name=intent.extras?.getString("referrer_name")
        if(!TextUtils.isEmpty( details)){
            log("enter_corefeatures_page",mapOf("details" to details!! ,"referrer_name" to referrer_name!!))
        }
        setupBackPressHandler()
    }

    private fun setupRecyclerView() {
        appUsageAdapter = AppUsageAdapter { item ->
            openAppInfoSettings(item.packageName)
        }

        binding.rvUsage.apply {
            adapter = appUsageAdapter
            layoutManager = LinearLayoutManager(this@AppUsageActivity)
            setHasFixedSize(true)
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
        binding.llBack.safeClick {    onBack() }
        binding.btnHoursSpent.safeClick {
            currentMetric = AppUsageMetric.HOURS_SPENT
            renderMetricToggle()
            currentSnapshot?.let(::renderUsageSnapshot)
        }
        binding.btnTimesOpened.safeClick {
            currentMetric = AppUsageMetric.TIMES_OPENED
            renderMetricToggle()
            currentSnapshot?.let(::renderUsageSnapshot)
        }
    }



    private fun renderUsageSnapshot(snapshot: AppUsageSnapshot) {
        binding.tvSummaryRange.text = getString(snapshot.range.labelResId)
        binding.tvSummaryValue.text = AppUsageFormatter.formatSummaryValue(this, currentMetric, snapshot)
        val sortedEntries = when (currentMetric) {
            AppUsageMetric.HOURS_SPENT -> snapshot.entries.sortedByDescending { it.totalTimeMs }
            AppUsageMetric.TIMES_OPENED -> snapshot.entries.sortedByDescending { it.openCount }
        }

        val maxValue = when (currentMetric) {
            AppUsageMetric.HOURS_SPENT -> sortedEntries.maxOfOrNull { it.totalTimeMs }?.toFloat() ?: 0f
            AppUsageMetric.TIMES_OPENED -> sortedEntries.maxOfOrNull { it.openCount }?.toFloat() ?: 0f
        }

        val items = sortedEntries.map { entry ->
            AppUsageListItem(
                packageName = entry.packageName,
                appName = entry.appName,
                icon = entry.icon,
                valueText = when (currentMetric) {
                    AppUsageMetric.HOURS_SPENT -> AppUsageFormatter.formatDurationCompact(
                        this,
                        entry.totalTimeMs
                    )

                    AppUsageMetric.TIMES_OPENED -> AppUsageFormatter.formatOpenCount(
                        this,
                        entry.openCount
                    )
                },
                progress = calculateProgress(entry, maxValue),
                canManage = AppUsageFormatter.canManageApp(
                    currentMetric,
                    packageName = entry.packageName,
                    currentPackageName = packageName,
                    totalTimeMs = entry.totalTimeMs,
                    openCount = entry.openCount
                )
            )
        }
        appUsageAdapter.submitList(items)
        binding.tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        renderDonutAndLegend(sortedEntries)
    }


    private fun renderDonutAndLegend(entries: List<AppUsageEntry>) {
        val palette = listOf(
            ContextCompat.getColor(this, R.color.feature_donut_1),
            ContextCompat.getColor(this, R.color.feature_donut_2),
            ContextCompat.getColor(this, R.color.feature_donut_3),
            ContextCompat.getColor(this, R.color.feature_donut_4)
        )

        val sourceValues = when (currentMetric) {
            AppUsageMetric.HOURS_SPENT -> entries.filter { it.totalTimeMs > 0L }.sortedByDescending { it.totalTimeMs }
            AppUsageMetric.TIMES_OPENED -> entries.filter { it.openCount > 0 }.sortedByDescending { it.openCount }
        }

        val topEntries = sourceValues.take(3)
        val otherValue = when (currentMetric) {
            AppUsageMetric.HOURS_SPENT -> (sourceValues.drop(3).sumOf { it.totalTimeMs }).toFloat()
            AppUsageMetric.TIMES_OPENED -> sourceValues.drop(3).sumOf { it.openCount }.toFloat()
        }

        val slices = buildList {
            topEntries.forEachIndexed { index, entry ->
                add(
                    AppUsageDonutSlice(
                        label = entry.appName,
                        color = palette[index],
                        value = when (currentMetric) {
                            AppUsageMetric.HOURS_SPENT -> entry.totalTimeMs.toFloat()
                            AppUsageMetric.TIMES_OPENED -> entry.openCount.toFloat()
                        }
                    )
                )
            }
            if (otherValue > 0f) {
                add(AppUsageDonutSlice(getString(R.string.other), palette.last(), otherValue))
            }
        }

        binding.donutUsage.setSlices(slices)
        binding.legendContainer.removeAllViews()
        slices.forEach { slice ->
            binding.legendContainer.addView(createLegendView(slice))
        }
    }

    private fun createLegendView(slice: AppUsageDonutSlice): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 10
            }
        }

        val dotView = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(14, 14).apply {
                marginEnd = 12
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(slice.color)
            }
        }

        val labelView = TextView(this).apply {
            text = slice.label
            setTextColor(ContextCompat.getColor(this@AppUsageActivity, R.color.main_caption))
            textSize = 14f
        }

        container.addView(dotView)
        container.addView(labelView)
        return container
    }


    private fun calculateProgress(entry: AppUsageEntry, maxValue: Float): Int {
        if (maxValue <= 0f) return 0
        val rawProgress = when (currentMetric) {
            AppUsageMetric.HOURS_SPENT -> (entry.totalTimeMs / maxValue) * 100f
            AppUsageMetric.TIMES_OPENED -> (entry.openCount / maxValue) * 100f
        }
        return rawProgress.toInt().coerceIn(if (rawProgress > 0f) 6 else 0, 100)
    }

    private fun renderMetricToggle() {
        val selectedColor = ContextCompat.getColor(this, R.color.theme_color)
        val unselectedColor = ContextCompat.getColor(this, R.color.hours_hint_color)

       if(currentMetric == AppUsageMetric.HOURS_SPENT){
           binding.tvHoursSpentHint.visibility= View.VISIBLE
           binding.tvTimesOpenedHint.visibility= View.GONE

       }else{
           binding.tvHoursSpentHint.visibility= View.GONE
           binding.tvTimesOpenedHint.visibility= View.VISIBLE
       }

        binding.btnHoursSpent.setTextColor(
            if (currentMetric == AppUsageMetric.HOURS_SPENT) selectedColor else unselectedColor
        )
        binding.btnTimesOpened.setTextColor(
            if (currentMetric == AppUsageMetric.TIMES_OPENED) selectedColor else unselectedColor
        )
    }





    private fun checkPermissionAndRedirect() {
        if (!UsageStatsHelper.hasPermission(this)) {
            if (usageDataPermissionDialog == null) {
                usageDataPermissionDialog = UsageDataPermissionDialog(this)
                usageDataPermissionDialog?.setOnClickListener(object :
                    UsageDataPermissionDialog.OnClickListener {
                    override fun onAllow() {
                        val success =
                            UsageStatsHelper.navigateToUsageAccessSettings(this@AppUsageActivity)
                        if (success) {
                            UsageStatsHelper.markNavigated()
                        } else {
                            Toast.makeText(
                                this@AppUsageActivity,
                                getString(R.string.unable_to_open_permission),
                                Toast.LENGTH_SHORT
                            ).show()
                            finish()
                        }
                    }
                })
            }
            usageDataPermissionDialog?.showDialog()
        }
    }

    @SuppressLint("MissingInflatedId")
    fun initTab(){


        tabData.add(AppUsageActivity.TabItem(getString(R.string.today)))
        tabData.add(AppUsageActivity.TabItem(getString(R.string.yesterday)))
        tabData.add(AppUsageActivity.TabItem(getString(R.string.last_7_days)))
        tabData.add(AppUsageActivity.TabItem(getString(R.string.last_30_days)))
        tabData.forEachIndexed { index, item ->
            val tab = binding.tabLayout.newTab()
            // 自定义视图
            val customView = layoutInflater.inflate(R.layout.tab_item, null)
            val titleTv =customView.findViewById<TextView>(R.id.tv_title)
            titleTv.text = item.title
            tab.customView = customView
            binding.tabLayout.addTab(tab, index) // 添加并指定位置
        }

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.let {
                    val position = it.position
                    when(position){
                        0->{
                            if(currentRange!=AppUsageRange.TODAY ){
                                currentRange = AppUsageRange.TODAY
                                loadUsageData()
                            }
                        }
                        1->{
                            if(currentRange!=AppUsageRange.YESTERDAY ){
                                currentRange = AppUsageRange.YESTERDAY
                                loadUsageData()
                            }
                        }
                        2->{ if(currentRange!=AppUsageRange.LAST_7_DAYS ){
                            currentRange = AppUsageRange.LAST_7_DAYS
                            loadUsageData()
                        }

                        }
                        3->{
                            if(currentRange!=AppUsageRange.LAST_30_DAYS ){
                                currentRange = AppUsageRange.LAST_30_DAYS
                                loadUsageData()
                            }
                        }
                    }
                    // 更新内容区域
                    // 可选：更新自定义视图的样式（比如改变文字颜色）
                    updateTabStyle(it, true)

                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {
                tab?.let {
                    // 取消选中样式
                    updateTabStyle(it, false)
                }
            }
            override fun onTabReselected(tab: TabLayout.Tab?) {
                // 可忽略，或做刷新操作
            }
        })
        binding.tabLayout.getTabAt(0)?.let {
            it.select()                     // 触发监听器
            // 保险起见，再手动更新一次
            updateTabStyle(it, true)
        }

    }

    private fun loadUsageData(isShowLoading: Boolean=false) {
        if(isShowLoading){
            handleLoadingState(true)
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val snapshot = AppUsageAnalyzer.loadSnapshot(this@AppUsageActivity, currentRange)
            withContext(Dispatchers.Main) {
                if(isShowLoading){
                    handleLoadingState(false)
                }
                currentSnapshot = snapshot
                renderUsageSnapshot(snapshot)
            }
        }
    }




    override fun onResume() {
        super.onResume()
        if (!UsageStatsHelper.hasPermission(this) ) {
            if (UsageStatsHelper.hasNavigatedBefore()){
                finish()
            }
        }else{
            loadUsageData(true)
        }
    }

    override fun onDestroy() {
        UsageStatsHelper.resetNavigatedFlag()
        usageDataPermissionDialog?.release()
        usageDataPermissionDialog = null
        super.onDestroy()
    }




    private fun updateTabStyle(tab: TabLayout.Tab, isSelected: Boolean) {
        val customView = tab.customView ?: return
        val titleTv = customView.findViewById<TextView>(R.id.tv_title)
        val color = if (isSelected) {
            getColor(R.color.theme_color)
        } else {
            getColor(R.color.hint_color)
        }
        titleTv.setTextColor(color)
    }
    data class TabItem( val title: String)
}
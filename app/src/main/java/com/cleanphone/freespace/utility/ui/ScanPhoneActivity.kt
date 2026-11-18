package com.cleanphone.freespace.utility.ui

import android.Manifest
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Point
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil.setContentView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.cleanphone.freespace.utility.R
import com.cleanphone.freespace.utility.advertise.AdAreaKeys
import com.cleanphone.freespace.utility.advertise.AdvertisePageMediator
import com.cleanphone.freespace.utility.advertise.AdvertisePageMediator.showInterstitial
import com.cleanphone.freespace.utility.base.activity.BaseActivity
import com.cleanphone.freespace.utility.base.activity.BaseViewActivity
import com.cleanphone.freespace.utility.base.command.REQUEST_CODE_MANAGE_STORAGE
import com.cleanphone.freespace.utility.base.command.REQUEST_CODE_STORAGE_PERMISSION
import com.cleanphone.freespace.utility.base.command.checkAndRequestPermissions
import com.cleanphone.freespace.utility.base.command.hasStoragePermission
import com.cleanphone.freespace.utility.base.command.log
import com.cleanphone.freespace.utility.base.command.readyGo
import com.cleanphone.freespace.utility.base.command.readyGoThenKill
import com.cleanphone.freespace.utility.base.command.safeClick
import com.cleanphone.freespace.utility.base.config.AppConfig
import com.cleanphone.freespace.utility.bean.PhoneInfo
import com.cleanphone.freespace.utility.bean.StorageInfo
import com.cleanphone.freespace.utility.databinding.ActivityScanPhoneBinding
import com.cleanphone.freespace.utility.utils.AppPrefsUtils
import com.cleanphone.freespace.utility.utils.SizeUtil
import com.google.android.gms.ads.appopen.AppOpenAd
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import java.io.File
import kotlin.compareTo
import kotlin.jvm.java
import kotlin.text.toInt

class ScanPhoneActivity : BaseViewActivity<ActivityScanPhoneBinding>(){
    private lateinit var appAdapter: BaseQuickAdapter<PhoneInfo,BaseViewHolder>
    private var infoList: MutableList<PhoneInfo> = mutableListOf()
    companion object {
        private const val ANIMATION_DURATION = 800L
        private const val ANIMATION_STEPS = 50

    }

    override fun initializeActivity() {
        super.initializeActivity()
        log("enter_guide_page_scanning")
        initScanAnimation()
        getScreenResolution()
        getStorageInfo()
        getBatteryHealth()
        initAdapter()
        lifecycleScope.launch {
            delay(1200)
            binding.tvSkip.visibility = View.VISIBLE
        }
        binding.tvSkip.safeClick {
            log("click_skip_guidepage")
            showInterstitial(this,AdAreaKeys.Interstitial.ENTER_HOME_FROM_SCAN_DEVICE_RESULT){
                AppPrefsUtils.commitBoolean(AppConfig.IS_SCAN_PHONE, true)
                readyGoThenKill(MainActivity::class.java)
            }
        }
        onBackPressedDispatcher.addCallback(this,object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
            }
        })


    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_STORAGE_PERMISSION && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            onGetPermission()
        } else {
            onNotGetPermission()
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_MANAGE_STORAGE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                onGetPermission()
            }else{
                onNotGetPermission()
            }
        }
    }

    fun onNotGetPermission(){
        AdvertisePageMediator.showFileAccessCancelInterstitial(this){
            log("disagree_filemanage_permission")
            AppPrefsUtils.commitBoolean(AppConfig.IS_SCAN_PHONE, true)
            readyGoThenKill(MainActivity::class.java)
        }
    }


    fun onGetPermission() {
        log("agree_filemanage_permission")
        AppPrefsUtils.commitBoolean(AppConfig.IS_SCAN_PHONE, true)
        readyGoThenKill(ScanFilesActivity::class.java)
    }

    private var scanAnimator: ObjectAnimator? = null

    private fun initScanAnimation() {
        binding.ivScan.post {
            val parentHeight = binding.rlPhone.height
            val viewHeight = binding.ivScan.height
            val marginTop = (binding.ivScan.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin ?: 0
            val maxTranslation = (parentHeight - viewHeight - marginTop).toFloat()
            // 确保初始位置在顶部
            binding.ivScan.translationY = 0f
            scanAnimator = ObjectAnimator.ofFloat(binding.ivScan, "translationY", 0f, maxTranslation).apply {
                duration = 1200L
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.RESTART   // 关键：从上到下重复，不反向
                interpolator = LinearInterpolator()
            }
            startScanAnimation()   // 放在 post 内部，确保动画已创建
        }
    }


    fun startScanAnimation() {
        scanAnimator?.start()
    }

    fun cancelScanAnimation() {
        scanAnimator?.cancel()
    }



    fun initAdapter(){
        infoList.add(PhoneInfo(getString(R.string.device_model),"--",false))
        infoList.add(PhoneInfo(getString(R.string.system_version),"--",false))
        infoList.add(PhoneInfo(getString(R.string.screen_resolution),"--",false))
        infoList.add(PhoneInfo(getString(R.string.battery_health_status),"--",false))
        infoList.add(PhoneInfo(getString(R.string.storage_usage),"--",false))
        infoList.add(PhoneInfo(getString(R.string.generating_cleanup_plan),"--",false))
        appAdapter =object : BaseQuickAdapter<PhoneInfo,BaseViewHolder>(R.layout.item_phone_info){
            override fun convert(
                holder: BaseViewHolder,
                item: PhoneInfo
            ) {
                item.let {
                    holder.setText(R.id.tv_tag, it.tagName)
                    holder.setText(R.id.tv_info, it.info)
                    holder.setGone(R.id.iv_check,!it.isCheck)
                    holder.setGone(R.id.rotatingRingView,it.isCheck)
                }
                if(appAdapter.getItemPosition(item)==4&&!TextUtils.isEmpty(item.info)&&item.info!="--"){
                    val spannableString = SpannableString(item.info)
                    spannableString.setSpan(
                        ForegroundColorSpan(Color.parseColor("#FF5352")),
                        0, "${SizeUtil.formatSize3(storageInfo!!.usedSpace)}".length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    holder.setText(R.id.tv_info, spannableString)
                }
            }
        }
        binding.rcInfo.adapter = appAdapter
        binding.rcInfo.layoutManager =
            LinearLayoutManager(this@ScanPhoneActivity, RecyclerView.VERTICAL, false)
        binding.rcInfo.setHasFixedSize(true)

        appAdapter.setList(infoList)
        lifecycleScope.launch {
            for (i in 0 until infoList.size){
                delay( 600L)
                when(i){
                    0->{
                        appAdapter.data[0].info="${Build.MODEL}"
                        appAdapter.data[0].isCheck=true
                    }
                    1->{
                        appAdapter.data[1].info="Android ${Build.VERSION.RELEASE}"
                        appAdapter.data[1].isCheck=true
                    }
                    2->{
                        appAdapter.data[2].info= "${point!!.x}X${point!!.y}"
                        appAdapter.data[2].isCheck=true
                    }
                    3->{
                        appAdapter.data[3].info=batteryHealth
                        appAdapter.data[3].isCheck=true
                    }
                    4->{
                        appAdapter.data[4].info="${SizeUtil.formatSize3(storageInfo!!.usedSpace)}/${SizeUtil.formatSize3(storageInfo!!.totalSpace)}"
                        appAdapter.data[4].isCheck=true
                    }
                    5->{
                        appAdapter.data[5].info=getString(R.string.to_done)
                        appAdapter.data[5].isCheck=true
                    }
                }
                appAdapter.notifyItemChanged(i)
            }
            cancelScanAnimation()
            binding.ivScan.visibility=View.GONE
            binding.ivPhone.visibility=View.GONE
            binding.tvChecking.visibility=View.GONE
            binding.btClean.visibility= View.VISIBLE
            binding.rlEnd.visibility=View.VISIBLE
            if (storageInfo!!.usagePercentage > 0) {
                binding.progressBar.setProgressColor(getColor(R.color.white))
                lifecycleScope.launch {
                    animateProgressBar(storageInfo!!.usagePercentage)
                }
                binding.tvMemory.text =
                    "${SizeUtil.formatSize3(storageInfo!!.usedSpace)}/${SizeUtil.formatSize3(storageInfo!!.totalSpace)}"
            }
            binding.btClean.setOnClickListener {
                log("click_continue_guidepage")
                if (hasStoragePermission()) {
                    onGetPermission()
                }else{
                    AdvertisePageMediator.suppressNextForegroundOpenAd()
                    checkAndRequestPermissions(Manifest.permission.READ_EXTERNAL_STORAGE, {
                        lifecycleScope.launch {
                            delay(200)
                            readyGo(TransparentActivity::class.java)
                        }
                    })
                }
            }


        }

    }

    private suspend fun animateProgressBar(targetPercentage: Float) {
        val delayPerStep = ANIMATION_DURATION / ANIMATION_STEPS
        val increment = targetPercentage / ANIMATION_STEPS
        for (i in 1..ANIMATION_STEPS) {
            val progress = i * increment
            binding.progressBar.setProgress(progress)
            binding.tvProgress.text = "${progress.toInt()}"
            delay(delayPerStep)
        }
        // 确保最终显示正确
        binding.progressBar.setProgress(targetPercentage)
        binding.tvProgress.text = "${targetPercentage.toInt()}"
    }

    var storageInfo: StorageInfo?=null
    @SuppressLint("DefaultLocale", "SetTextI18n")
    fun getStorageInfo() {
        try {
            val app =application
            val externalDir = File(
                app.getExternalFilesDir(null)?.absolutePath?.split("/Android")?.get(0) ?: ""
            )
            if (externalDir.exists()) {
                val statFs = StatFs(externalDir.absolutePath)

                // 获取存储块大小和数量
                val blockSize = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    statFs.blockSizeLong
                } else {
                    statFs.blockSize.toLong()
                }

                val totalBlocks = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    statFs.blockCountLong
                } else {
                    statFs.blockCount.toLong()
                }

                val availableBlocks = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    statFs.availableBlocksLong
                } else {
                    statFs.availableBlocks.toLong()
                }
                // 计算存储空间
                val totalSize = totalBlocks * blockSize
                val availableSize = availableBlocks * blockSize
                val usedSize = totalSize - availableSize
                // 计算使用百分比
                val usedPercentage = if (totalSize > 0) {
                    (usedSize * 100 / totalSize)
                } else {
                    0
                }
                storageInfo = StorageInfo(totalSize, usedSize, usedPercentage.toFloat())
            } else {
                storageInfo = StorageInfo(0, 0, 0f)
            }
        }catch (e: Exception){
            storageInfo = StorageInfo(0, 0, 0f)
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        cancelScanAnimation()
    }
    var point:Point?=null
    fun getScreenResolution() {
        point = Point()
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            point!!.x = metrics.bounds.width()
            point!!.y = metrics.bounds.height()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealSize(point)
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getSize(point)
        }
    }
    var batteryHealth: String=""
    fun getBatteryHealth() {
       var batteryStatus= registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: Intent().also {
            }
        batteryHealth=when (batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_COLD -> getString(R.string.overcooling)
            BatteryManager.BATTERY_HEALTH_DEAD -> getString(R.string.damage)
            BatteryManager.BATTERY_HEALTH_GOOD -> getString(R.string.good)
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> getString(R.string.overheat)
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> getString(R.string.overvoltage)
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> getString(R.string.fault)
            else ->getString(R.string.unknown)
        }
    }
}
package com.cleanphone.freespace.utility.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil.setContentView
import androidx.lifecycle.lifecycleScope
import com.cleanphone.freespace.utility.R
import com.cleanphone.freespace.utility.adapter.ViewPagerAdapter
import com.cleanphone.freespace.utility.advertise.AdvertisePageMediator
import com.cleanphone.freespace.utility.base.activity.BaseViewActivity
import com.cleanphone.freespace.utility.base.command.REQUEST_CODE_MANAGE_STORAGE
import com.cleanphone.freespace.utility.base.command.REQUEST_CODE_NOTIFICATION
import com.cleanphone.freespace.utility.base.command.REQUEST_CODE_STORAGE_PERMISSION
import com.cleanphone.freespace.utility.base.command.checkAndRequestPermissions
import com.cleanphone.freespace.utility.base.command.checkNotificationPermission
import com.cleanphone.freespace.utility.base.command.getFunctionBundle
import com.cleanphone.freespace.utility.base.command.hasStoragePermission
import com.cleanphone.freespace.utility.base.command.log
import com.cleanphone.freespace.utility.base.command.navigateToAppSettings
import com.cleanphone.freespace.utility.base.command.rateUS
import com.cleanphone.freespace.utility.base.command.readyGo
import com.cleanphone.freespace.utility.base.config.AppConfig
import com.cleanphone.freespace.utility.bean.ToScanEvent
import com.cleanphone.freespace.utility.databinding.ActivityMainBinding
import com.cleanphone.freespace.utility.utils.AppPrefsUtils
import com.cleanphone.freespace.utility.utils.AppUpdateHelper
import com.cleanphone.freespace.utility.view.ExitDialog
import com.cleanphone.freespace.utility.view.NotificationDialog
import com.cleanphone.freespace.utility.view.RateUsDialog
import com.cleanphone.freespace.utility.view.RequestPermissionDialog
import com.google.android.play.core.install.model.AppUpdateType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import kotlin.jvm.java

class MainActivity : BaseViewActivity<ActivityMainBinding> (){
    companion object {
        const val TAB_HOME = 0
        const val TAB_TOOL = 1
        const val TAB_SETTING = 2
        private const val ONE_DAY_MS = 86400000L
        private const val UPDATE_CHECK_INTERVAL = 24 * 60 * 60 * 1000L // 24小时
    }
    private var currentTabPosition = 0
    private lateinit var viewPagerAdapter: ViewPagerAdapter
    private var requestPermissionDialog: RequestPermissionDialog? = null
    private var notificationDialog: NotificationDialog? = null
    private var exitDialog: ExitDialog? = null
    private lateinit var appUpdateHelper: AppUpdateHelper
    private var rateUsDialog: RateUsDialog?=null
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            log("agree_push_permission")
           // com.mar2sdk.core.notify.NotificationUtil.startFGS()
        } else {
            log("disagree_push_permission")
            Toast.makeText(this, getString(R.string.notification_dialog_hint), Toast.LENGTH_SHORT)
                .show()
            val isPermanentlyDenied = !ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (isPermanentlyDenied) {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
                startActivity(intent)
            }
        }
    }
    private data class TabConfig(
        val selectedIcon: Int,
        val selectedColor: Int,
        val normalIcon: Int,
        val normalColor: Int
    )
    override fun initializeActivity() {
        super.initializeActivity()
        EventBus.getDefault().register(this)
        appUpdateHelper = AppUpdateHelper(
            context = this.applicationContext,
            coroutineScope = lifecycleScope,
            updateRequestCode = AppUpdateHelper.UPDATE_REQUEST_CODE
        )
        initViewPager()
        initTabLayout()
        setupBackPressHandler()
        if (!checkNotificationPermission(this)) {
            showNotificationPermissionDialog()
        }

    }






    fun toScanActivity() {
        if (!hasStoragePermission()) {
            showStoragePermissionDialog(getString(R.string.remove_junk)) {
                AdvertisePageMediator.suppressNextForegroundOpenAd()
                lifecycleScope.launch {
                    delay(200)
                    readyGo(TransparentActivity::class.java)
                }
            }
        } else {
            AdvertisePageMediator.showFeatureEntryInterstitial(this, getString(R.string.remove_junk)) {
                readyGo(ScanFilesActivity::class.java,getFunctionBundle("junkClean","homepage"))
            }
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?){
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CODE_MANAGE_STORAGE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                    onGetStoragePermission()
                }else{
                    log("disagree_filemanage_permission")
                    AdvertisePageMediator.showFileAccessCancelInterstitial(this)
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CODE_STORAGE_PERMISSION -> handleStoragePermissionResult(grantResults)
        }
    }

    private fun handleStoragePermissionResult(grantResults: IntArray) {
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            onGetStoragePermission()
        }else{
            log("disagree_filemanage_permission")
            AdvertisePageMediator.showFileAccessCancelInterstitial(this)
        }
    }


    fun onGetStoragePermission() {
        log("agree_filemanage_permission")
        lifecycleScope.launch {
            delay(100)
            when (toTag) {
                getString(R.string.remove_junk) -> {
                    AdvertisePageMediator.showFeatureEntryInterstitial(this@MainActivity, getString(R.string.remove_junk)) {
                        readyGo(ScanFilesActivity::class.java,getFunctionBundle("junkClean","homepage") )
                    }
                }
                else -> {
                    val bundle = Bundle().apply {
                        putString(AppConfig.NEXT_TAG, toTag)
                        putString("details", "fileManage")
                        putString("referrer_name", "homepage")
                    }
                    readyGo(LoadingActivity::class.java, bundle)
                }
            }

        }
    }

    private var toTag = ""
    private fun showStoragePermissionDialog(tag: String, onPermissionGranted: () -> Unit) {
        if (requestPermissionDialog == null) {
            requestPermissionDialog = RequestPermissionDialog(this).apply {
                setmOnClickListener(object : RequestPermissionDialog.onClickListener {
                    override fun onAllow() {
                        toTag = tag
                        checkAndRequestPermissions(Manifest.permission.READ_EXTERNAL_STORAGE) {
                            onPermissionGranted()
                        }
                    }
                    override fun cancel() {
                        AdvertisePageMediator.showFileAccessCancelInterstitial(this@MainActivity)
                    }
                })
            }
        }
        requestPermissionDialog!!.toShow()
    }


    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if(AppPrefsUtils.getBoolean("isFirstExit",true)){
                    if(rateUsDialog==null){
                        rateUsDialog= RateUsDialog(this@MainActivity)
                        rateUsDialog!!.setOnRateClickListener(object : RateUsDialog.OnRateClickListener{
                            override fun onRateClicked(rating: Int) {
                                Toast.makeText(this@MainActivity, getString(R.string.feedback), Toast.LENGTH_SHORT)
                                    .show()
                                if(rating==5){
                                    rateUS()
                                }
                                rateUsDialog!!.dismiss()

                            }
                        })
                    }
                    AppPrefsUtils.commitBoolean("isFirstExit",false)
                    rateUsDialog!!.showDialog()
                }else{
                    if (exitDialog == null) {
                        exitDialog = ExitDialog(this@MainActivity)
                        exitDialog!!.setmOnClickListener(object : ExitDialog.onClickListener {
                            override fun onNext() {
                                finish()
                            }
                        })
                    }
                    exitDialog!!.toShow()
                }
            }
        })
    }
    private fun showNotificationPermissionDialog() {
        if (notificationDialog == null) {
            notificationDialog = NotificationDialog(this)
            notificationDialog!!.setmOnClickListener(object : NotificationDialog.onClickListener {
                override fun onAllow() {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            })
        }
        notificationDialog!!.toShow()
    }

    private fun initViewPager() {
        viewPagerAdapter = ViewPagerAdapter(this)
        binding.viewPager.apply {
            adapter = viewPagerAdapter
            isUserInputEnabled = false
        }
    }
    private fun initTabLayout() {
        binding.rlHome.setOnClickListener { selectTab(TAB_HOME) }
        binding.rlBox.setOnClickListener { selectTab(TAB_TOOL) }
        binding.rlSetting.setOnClickListener { selectTab(TAB_SETTING) }
        selectTab(TAB_HOME)
    }

    private fun selectTab(position: Int) {
        if (currentTabPosition == position) return
        currentTabPosition = position
        binding.viewPager.setCurrentItem(position, false)
        updateTabUI()
    }

    private fun updateTabUI() {
        val tabList = listOf(
            TabConfig(
                R.mipmap.ic_home_check, R.color.theme_color,
                R.mipmap.ic_home_nor, R.color.hint_color
            ),
            TabConfig(
                R.mipmap.ic_box_check, R.color.theme_color,
                R.mipmap.ic_box_nor, R.color.hint_color
            ),
            TabConfig(
                R.mipmap.ic_setting_check, R.color.theme_color,
                R.mipmap.ic_setting_nor, R.color.hint_color
            )
        )
        setTabState(binding.icHome, binding.tvHome, currentTabPosition == TAB_HOME, tabList[0])
        setTabState(
            binding.icBox,
            binding.tvBox,
            currentTabPosition == TAB_TOOL,
            tabList[1]
        )
        setTabState(
            binding.icSetting,
            binding.tvSetting,
            currentTabPosition == TAB_SETTING,
            tabList[2]
        )
    }

    private fun setTabState(
        icon: ImageView,
        text: TextView,
        isSelected: Boolean,
        config: TabConfig
    ) {
        if (isSelected) {
            icon.setImageResource(config.selectedIcon)
            text.setTextColor(getColorCompat(config.selectedColor))
        } else {
            icon.setImageResource(config.normalIcon)
            text.setTextColor(getColorCompat(config.normalColor))
        }
    }
    private fun getColorCompat(colorRes: Int): Int {
        return ContextCompat.getColor(this, colorRes)
    }
    override fun onResume() {
        super.onResume()
        log("enter_homepage")
        ensurePersistentNotificationRunning()
        appUpdateHelper.setCurrentActivity(this)
        // 优先检查即时更新（强制更新）
        appUpdateHelper.onResume(this)
        appUpdateHelper.checkAndStartUpdate(this, AppUpdateType.IMMEDIATE)
    }

    private fun ensurePersistentNotificationRunning() {
        if (checkNotificationPermission(this)){
            com.mar2sdk.core.notify.NotificationUtil.startFGS()
        }

    }
    override fun onPause() {
        super.onPause()
        appUpdateHelper.clearCurrentActivity()
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: ToScanEvent) {
        when (event.tag) {
            getString(R.string.remove_junk)-> toScanActivity()
            else->{
                toLoadActivity(event.tag)
            }
        }
    }


    fun toLoadActivity(tag:String){
        if (!hasStoragePermission()) {
            showStoragePermissionDialog(tag) {
                AdvertisePageMediator.suppressNextForegroundOpenAd()
                lifecycleScope.launch {
                    delay(200)
                    readyGo(TransparentActivity::class.java)
                }
            }
        } else {

            val bundle = Bundle().apply {
                putString(AppConfig.NEXT_TAG, tag)
                putString("details", "fileManage")
                putString("referrer_name", "homepage")
            }
            readyGo(LoadingActivity::class.java, bundle)
        }
    }



    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
        appUpdateHelper.cleanup()
        notificationDialog?.release()
        requestPermissionDialog?.release()
        requestPermissionDialog=null
        notificationDialog=null
        rateUsDialog?.release()
        rateUsDialog=null
        exitDialog?.release()
        exitDialog=null


    }























}
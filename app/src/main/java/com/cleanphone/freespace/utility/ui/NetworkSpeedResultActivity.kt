package com.cleanphone.freespace.utility.ui

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil.setContentView
import com.cleanphone.freespace.utility.R
import com.cleanphone.freespace.utility.advertise.AdAreaKeys
import com.cleanphone.freespace.utility.advertise.AdvertisePageMediator
import com.cleanphone.freespace.utility.base.activity.BaseViewActivity
import com.cleanphone.freespace.utility.base.command.getFunctionBundle
import com.cleanphone.freespace.utility.base.command.hasStoragePermission
import com.cleanphone.freespace.utility.base.command.log
import com.cleanphone.freespace.utility.base.command.readyGoThenKill
import com.cleanphone.freespace.utility.base.config.AppConfig
import com.cleanphone.freespace.utility.bean.TaskListBean
import com.cleanphone.freespace.utility.bean.ToScanEvent
import com.cleanphone.freespace.utility.databinding.ActivityNetworkSpeedResultBinding
import com.cleanphone.freespace.utility.utils.NotificationBarStore
import org.greenrobot.eventbus.EventBus

class NetworkSpeedResultActivity : BaseViewActivity<ActivityNetworkSpeedResultBinding>() {
    var taskListBeans: MutableList<TaskListBean> = mutableListOf()


    override fun initializeActivity() {
        super.initializeActivity()
        initTask()
        val randomSelected = taskListBeans.shuffled().take(2)

        binding.tvTask1.text=randomSelected[0].title
        binding.tvHint1.text=randomSelected[0].hint
        binding.ivTask1.setImageResource(randomSelected[0].res)
        binding.tvNext1.text=randomSelected[0].next
        binding.tvNext1.setOnClickListener {
            toNext(randomSelected[0].clazz)
        }
        binding.tvTask2.text=randomSelected[1].title
        binding.tvHint2.text=randomSelected[1].hint
        binding.ivTask2.setImageResource(randomSelected[1].res)
        binding.tvNext2.text=randomSelected[1].next
        binding.tvNext2.setOnClickListener {
            toNext(randomSelected[1].clazz)
        }
        binding.tvDownloadValue.text=intent.getStringExtra("downloadMbps")
        binding.tvUploadValue.text=intent.getStringExtra("uploadMbps")
        binding.llBack.setOnClickListener { finish() }

    }



    fun toNext(clazz: Class<*>) {
        when (clazz) {
            ScanFilesActivity::class.java -> {
                log("click_corefeatures_btn",mapOf("details" to "junkClean","referrer_name" to "taskcompleted"))
                if (!hasStoragePermission()) {
                    EventBus.getDefault().post(ToScanEvent(getString(R.string.remove_junk)))
                    finish()
                } else {
                    AdvertisePageMediator.showFeatureEntryInterstitial(
                        this,
                        getString(R.string.remove_junk)
                    ) {
                        readyGoThenKill(ScanFilesActivity::class.java,getFunctionBundle("junkClean","taskcompleted"))
                    }
                }
            }

            NotificationBarConfigActivity::class.java -> {
                log("click_corefeatures_btn",mapOf("details" to "notificationClean","referrer_name" to "taskcompleted"))
                AdvertisePageMediator.showFeatureEntryInterstitial(
                    this,
                    getString(R.string.notification_bar)
                ) {
                    val dashboardState = NotificationBarStore.loadState(this)
                    if (dashboardState.listenerEnabled &&
                        dashboardState.blockingEnabled &&
                        dashboardState.hasConfiguredTargets
                    ) {
                        readyGoThenKill(NotificationBarActivity::class.java,getFunctionBundle("notificationClean","taskcompleted"))
                    } else {
                        readyGoThenKill(NotificationBarConfigActivity::class.java,getFunctionBundle("notificationClean","taskcompleted"))
                    }
                }
            }
            AppUsageActivity::class.java -> {
                log("click_corefeatures_btn",mapOf("details" to "appUsage","referrer_name" to "taskcompleted"))
                AdvertisePageMediator.showFeatureEntryInterstitial(this, getString(R.string.app_usage)){
                    readyGoThenKill(AppUsageActivity::class.java,getFunctionBundle("appUsage","taskcompleted"))
                }
            }

            NetworkScanActivity::class.java -> {
                log("click_corefeatures_btn",mapOf("details" to "networkScan","referrer_name" to "taskcompleted"))
                AdvertisePageMediator.showFeatureEntryInterstitial(this, getString(R.string.network_speed)){
                    readyGoThenKill(NetworkScanActivity::class.java,getFunctionBundle("networkScan","taskcompleted"))
                }

            }

            NetworkSpeedActivity::class.java -> {
                log("click_corefeatures_btn",mapOf("details" to "networkSpeed","referrer_name" to "taskcompleted"))
                AdvertisePageMediator.showFeatureEntryInterstitial(this, getString(R.string.network_speed)){
                    readyGoThenKill(NetworkSpeedActivity::class.java,getFunctionBundle("networkSpeed","taskcompleted"))
                }
            }

            NetworkUsageActivity::class.java -> {
                log("click_corefeatures_btn",mapOf("details" to "networkUsage","referrer_name" to "taskcompleted"))
                AdvertisePageMediator.showFeatureEntryInterstitial(this, getString(R.string.network_usage)){
                    readyGoThenKill(NetworkUsageActivity::class.java,getFunctionBundle("networkUsage","taskcompleted"))
                }
            }
            BatteryInfoActivity::class.java -> {
                log("click_corefeatures_btn",mapOf("details" to "batteryInfo","referrer_name" to "taskcompleted"))
                AdvertisePageMediator.showFeatureEntryInterstitialByAreaKey(this,AdAreaKeys.Interstitial.ENTER_BATTERY_INFO){
                    val bundle = Bundle().apply {
                        putString(AppConfig.NEXT_TAG, getString(R.string.battery_info))
                        putString("details", "batteryInfo")
                        putString("referrer_name", "taskcompleted")
                    }
                    readyGoThenKill(LoadingActivity::class.java, bundle)
                }
            }
        }


    }








    fun  initTask(){
        taskListBeans.add(TaskListBean(getString(R.string.remove_junk),R.mipmap.ic_cleaner,
            getString(R.string.remove_junk),getString(R.string.remove_junk_hint), ScanFilesActivity::class.java,getString(R.string.scan_now)))
        taskListBeans.add(TaskListBean(getString(R.string.notification_bar),R.mipmap.ic_nt,
            getString(R.string.notification_bar),getString(R.string.notification_bar_hint), NotificationBarConfigActivity::class.java,getString(R.string.check_now)))
        taskListBeans.add(TaskListBean(getString(R.string.app_usage),R.mipmap.ic_app,
            getString(R.string.app_usage),getString(R.string.app_usage_hint), AppUsageActivity::class.java,getString(R.string.view_now)))

        taskListBeans.add(TaskListBean(getString(R.string.network_scan),R.mipmap.ic_network_scan,
            getString(R.string.network_scan),getString(R.string.network_scan_hint), NetworkScanActivity::class.java,getString(R.string.scan_now)))

        taskListBeans.add(TaskListBean(getString(R.string.network_speed),R.mipmap.ic_speed,
            getString(R.string.network_speed),getString(R.string.network_speed_hint),
            NetworkSpeedActivity::class.java,getString(R.string.check_now)))
        taskListBeans.add(TaskListBean(getString(R.string.network_usage),R.mipmap.ic_neteork_usage,
            getString(R.string.network_usage),getString(R.string.app_usage_hint),
            NetworkUsageActivity::class.java,getString(R.string.view_now)))
        taskListBeans.add(TaskListBean(getString(R.string.battery_info),R.mipmap.ic_battery,
            getString(R.string.battery_info),getString(R.string.battery_info_hint),
            BatteryInfoActivity::class.java,getString(R.string.view_now)))
    }

}
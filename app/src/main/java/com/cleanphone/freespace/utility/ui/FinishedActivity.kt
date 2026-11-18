package com.cleanphone.freespace.utility.ui

import android.R.attr.tag
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil.setContentView
import com.cleanphone.freespace.utility.R
import com.cleanphone.freespace.utility.advertise.AdvertisePageMediator
import com.cleanphone.freespace.utility.base.activity.BaseViewActivity
import com.cleanphone.freespace.utility.base.command.readyGoThenKill
import com.cleanphone.freespace.utility.base.config.AppConfig
import com.cleanphone.freespace.utility.databinding.ActivityFinishedBinding
import kotlin.jvm.java

class FinishedActivity : BaseViewActivity<ActivityFinishedBinding>(){
    var tag: String =""
    override fun initializeActivity() {
        super.initializeActivity()
        val bundle = intent.extras
        binding.ivSuccess.setImageAssetsFolder("images/")
        binding.ivSuccess.setAnimation(R.raw.finish)
        tag = bundle?.getString(AppConfig.NEXT_TAG).toString()
        when (tag) {
            getString(R.string.remove_junk) -> {
                binding.content.setBackgroundColor(getColor(R.color.theme_color))
            }
            getString(R.string.process_manager) -> {
                binding.content.setBackgroundColor(getColor(R.color.process_scan_bg))
            }
            getString(R.string.app_manager) -> {
                binding.content.setBackgroundColor(getColor(R.color.app_scan_bg))
            }
            getString(R.string.speaker_cleaner) -> {
                binding.content.setBackgroundColor(getColor(R.color.speaker_scan_bg))
            }
            getString(R.string.battery_info) -> {
                binding.content.setBackgroundColor(getColor(R.color.battery_scan_bg))
            }
            getString(R.string.screenshot_manager)->{
                binding.content.setBackgroundColor(getColor(R.color.large_scan_bg))
            }
            else -> {
                binding.content.setBackgroundColor(getColor(R.color.large_scan_bg))
            }

        }
        binding.ivSuccess.addAnimatorListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if(!isToTask){
                    isToTask=true
                    AdvertisePageMediator.showCompletionInterstitial(this@FinishedActivity, tag) {
                        readyGoThenKill(TaskListActivity::class.java, bundle)
                    }
                }
            }
        })
        onBackPressedDispatcher.addCallback(this,object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {

            }
        })


    }
    var isToTask=false

    override fun onDestroy() {
        super.onDestroy()
        binding.ivSuccess.cancelAnimation()
        binding.ivSuccess.clearAnimation()
    }
}
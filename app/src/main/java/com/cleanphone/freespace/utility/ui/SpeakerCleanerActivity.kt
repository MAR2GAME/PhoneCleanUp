package com.cleanphone.freespace.utility.ui

import android.os.Bundle
import android.text.TextUtils
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.cleanphone.freespace.utility.R
import com.cleanphone.freespace.utility.base.activity.BaseViewActivity
import com.cleanphone.freespace.utility.base.command.log
import com.cleanphone.freespace.utility.base.command.readyGoThenKill
import com.cleanphone.freespace.utility.base.command.safeClick
import com.cleanphone.freespace.utility.databinding.ActivitySpeakerCleanerBinding

class SpeakerCleanerActivity : BaseViewActivity<ActivitySpeakerCleanerBinding>() {

    override fun initializeActivity() {
        super.initializeActivity()
        setupClickListeners()
        setupBackPressHandler()
        val details=intent.extras?.getString("details")
        val referrer_name=intent.extras?.getString("referrer_name")
        if(!TextUtils.isEmpty( details)){
            log("enter_corefeatures_page",mapOf("details" to details!! ,"referrer_name" to referrer_name!!))
        }

    }
    private fun setupClickListeners() {
        binding.btCleanDust.safeClick {
            readyGoThenKill(PlayAudioActivity::class.java)
        }
        binding.llBack.safeClick {
            onBack()
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



}
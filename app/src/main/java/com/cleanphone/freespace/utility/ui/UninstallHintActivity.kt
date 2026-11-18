package com.cleanphone.freespace.utility.ui

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
import com.cleanphone.freespace.utility.base.command.getFunctionBundle
import com.cleanphone.freespace.utility.base.command.hasStoragePermission
import com.cleanphone.freespace.utility.base.command.readyGoThenKill
import com.cleanphone.freespace.utility.base.command.safeClick
import com.cleanphone.freespace.utility.databinding.ActivityUninstallHintBinding
import kotlin.jvm.java

class UninstallHintActivity : BaseViewActivity<ActivityUninstallHintBinding>() {




    override fun initializeActivity() {
        super.initializeActivity()
        setupBackPressHandler()
        setupClickListeners()

    }

    private fun setupClickListeners() {
        with(binding) {
            llBack.setOnClickListener { handleBackAction() }
            rlClean.safeClick { handleCleanClick() }
            rlDiscover.safeClick { handleBackAction() }
            btnCancle.safeClick { handleBackAction() }
            tvUninstall.safeClick { handleUninstallClick() }
        }
    }


    private fun handleUninstallClick() {
        AdvertisePageMediator.showUninstallFirstInterstitial(this) {
            readyGoThenKill(SubmitMessageActivity::class.java)
        }
    }



    private fun handleCleanClick() {
        if (hasStoragePermission()) {
            AdvertisePageMediator.showFeatureEntryInterstitial(this, getString(R.string.junk_files)) {
                readyGoThenKill(ScanFilesActivity::class.java,getFunctionBundle("junkClean","homepage"))
            }
        } else {
            handleBackAction()
        }
    }




    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackAction()
            }
        })
    }

    private fun handleBackAction() {
        AdvertisePageMediator.showReturnHomeInterstitial(this) {
            readyGoThenKill(MainActivity::class.java)
        }
    }



}

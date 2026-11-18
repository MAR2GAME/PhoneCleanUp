package com.cleanphone.freespace.utility.ui

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil.setContentView
import com.cleanphone.freespace.utility.R
import com.cleanphone.freespace.utility.base.activity.BaseViewActivity
import com.cleanphone.freespace.utility.base.command.safeClick
import com.cleanphone.freespace.utility.base.config.AppConfig
import com.cleanphone.freespace.utility.databinding.ActivityProcessManagerBinding
import com.cleanphone.freespace.utility.databinding.ActivityToSettingHintBinding

class ToSettingHintActivity : BaseViewActivity<ActivityToSettingHintBinding>(){
    override fun initializeActivity() {
        super.initializeActivity()
        val bundle = intent.extras
        val tag = bundle?.getString(AppConfig.NEXT_TAG)
        if (tag == getString(R.string.process_manager)) {
            binding.tvHint.text = getString(R.string.force_stop)
        } else {
            binding.tvHint.text = getString(R.string.uninstall_app)
        }
        binding.btGotIt.safeClick {
            finish()
        }
    }

}
package com.cleanphone.freespace.utility.ui

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.cleanphone.freespace.utility.R
import com.cleanphone.freespace.utility.base.activity.BaseActivity
import com.cleanphone.freespace.utility.base.activity.BaseViewActivity
import com.cleanphone.freespace.utility.base.command.safeClick
import com.cleanphone.freespace.utility.databinding.ActivityTransparentBinding

class TransparentActivity : BaseViewActivity<ActivityTransparentBinding>() {

    override fun initializeActivity() {
        super.initializeActivity()
        binding.btOk.safeClick {
            finish()
        }

    }


}
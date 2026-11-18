package com.cleanphone.freespace.utility.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil.setContentView
import com.cleanphone.freespace.utility.R
import com.cleanphone.freespace.utility.base.activity.BaseActivity
import com.cleanphone.freespace.utility.base.activity.BaseViewActivity
import com.cleanphone.freespace.utility.base.command.readyGo
import com.cleanphone.freespace.utility.base.command.safeClick
import com.cleanphone.freespace.utility.base.config.AppConfig
import com.cleanphone.freespace.utility.databinding.ActivityAboutBinding
import com.google.android.gms.common.wrappers.Wrappers.packageManager

class AboutActivity : BaseViewActivity<ActivityAboutBinding>() {
    override fun initializeActivity() {
        super.initializeActivity()
        binding.ivAboutBack.safeClick {
            finish()
        }
        binding.tvTerms.safeClick {
            openTermsOrPrivacyWebView(AppConfig.TERMS_URL)
        }
        binding.tvPrivacy.safeClick {
            openTermsOrPrivacyWebView(AppConfig.PRIVACY_URL)
        }

        binding.tvCode.text=getString(R.string.version)+getAppVersionName()

    }

    private fun openTermsOrPrivacyWebView(url: String) {
        val bundle = Bundle().apply {
            putString(WebViewActivity.EXTRA_URL, url)
            putString("tag", "about")
        }
        readyGo(WebViewActivity::class.java, bundle)
    }

    fun getAppVersionName(): String {
        return try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            packageInfo.versionName ?: ""
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            ""
        }
    }



}
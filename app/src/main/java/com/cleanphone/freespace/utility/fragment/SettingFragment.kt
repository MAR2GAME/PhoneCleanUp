package com.cleanphone.freespace.utility.fragment

import android.content.Intent
import androidx.core.content.ContextCompat
import com.cleanphone.freespace.utility.R
import com.cleanphone.freespace.utility.base.command.rateUS
import com.cleanphone.freespace.utility.base.command.readyGo
import com.cleanphone.freespace.utility.base.command.safeClick
import com.cleanphone.freespace.utility.base.fragment.BaseMvvmFragment
import com.cleanphone.freespace.utility.databinding.FragmentSettingsBinding
import com.cleanphone.freespace.utility.ui.AboutActivity
import com.cleanphone.freespace.utility.viewmodel.SettingFragmentViewModel
import kotlin.jvm.java

class SettingFragment : BaseMvvmFragment<FragmentSettingsBinding, SettingFragmentViewModel>(){
    override fun init() {
        binding.tvShare.safeClick {
            shareApp()
        }
        binding.tvRate.safeClick {
            requireActivity().rateUS()
        }
        binding.tvAbout.safeClick {
            requireActivity().readyGo(AboutActivity::class.java)
        }
    }
    fun shareApp() {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "text/plain"

            // 应用名称
            val appName = getString(R.string.app_name)

            // 应用商店链接（替换为你的应用实际链接）
            val appLink = "https://play.google.com/store/apps/details?id=${requireActivity().packageName}"
            // 分享文本
            val shareText = "Check out $appName at: $appLink"
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, appName)
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareText)
           startActivity(Intent.createChooser(shareIntent, "Share $appName"))
        } catch (e: Exception) {
            e.printStackTrace()
            // 处理异常，例如显示错误消息
        }
    }
}
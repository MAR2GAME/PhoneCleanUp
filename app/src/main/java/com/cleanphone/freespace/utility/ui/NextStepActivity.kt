package com.cleanphone.freespace.utility.ui

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
import com.cleanphone.freespace.utility.base.activity.BaseMvvmActivity
import com.cleanphone.freespace.utility.base.activity.BaseViewActivity
import com.cleanphone.freespace.utility.base.command.readyGoThenKill
import com.cleanphone.freespace.utility.base.config.AppConfig
import com.cleanphone.freespace.utility.bean.NextConfig
import com.cleanphone.freespace.utility.databinding.ActivityNextStepBinding
import com.cleanphone.freespace.utility.viewmodel.NextStepViewModel

class NextStepActivity : BaseMvvmActivity<ActivityNextStepBinding, NextStepViewModel>() {

    override fun init() {
        binding.ivNext.setImageAssetsFolder("images/")
        setupBackPressHandler()
        observeViewModel()
        setupAnimationListener()
        val tag = intent.extras?.getString(AppConfig.NEXT_TAG)
        viewModel.setupConfiguration(tag)
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this,object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 禁用返回键
            }
        })
    }

    private fun observeViewModel() {
        // 观察配置变化
        viewModel.configuration.observe(this) { config ->
            config?.let { applyConfiguration(it) }
        }

        // 观察计数器文本变化
        viewModel.counterText.observe(this) { text ->
            binding.tvHint.text = text
        }

        // 观察导航状态
        viewModel.shouldNavigateToFinished.observe(this) { shouldNavigate ->
            if (shouldNavigate && !viewModel.isToFinished) {
                viewModel.isToFinished = true
                readyGoThenKill(FinishedActivity::class.java, intent.extras)
            }
        }
    }
    private fun applyConfiguration(config: NextConfig) {
        binding.content.setBackgroundColor(getColor(config.backgroundColor))

        binding.ivNext.setAnimation(config.animationRes)
        // 如果需要计数器，启动计数器动画
        if (config.needCounter) {
            viewModel.startCounterAnimation()
        }
    }

    private fun setupAnimationListener() {
        binding.ivNext.addAnimatorListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                viewModel.handleAnimationEnd()
            }
        })
    }


}
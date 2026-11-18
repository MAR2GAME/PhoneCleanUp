package com.cleanphone.freespace.utility.ui

import android.annotation.SuppressLint
import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import com.cleanphone.freespace.utility.R
import com.cleanphone.freespace.utility.base.activity.BaseViewActivity
import com.cleanphone.freespace.utility.base.command.readyGoThenKill
import com.cleanphone.freespace.utility.base.config.AppConfig
import com.cleanphone.freespace.utility.bean.CleanFilesBean
import com.cleanphone.freespace.utility.bean.CleanLargeFileBean
import com.cleanphone.freespace.utility.bean.JunkMotherBean
import com.cleanphone.freespace.utility.bean.LargeFile
import com.cleanphone.freespace.utility.bean.Screenshot
import com.cleanphone.freespace.utility.databinding.ActivityCleanFilesBinding
import com.cleanphone.freespace.utility.utils.SizeUtil
import com.cleanphone.freespace.utility.bean.ScreenshotEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.File
import kotlin.jvm.java

class CleanFilesActivity : BaseViewActivity<ActivityCleanFilesBinding>() {

    var size: Long = 0
    var junkMotherBeans = mutableListOf<JunkMotherBean>()
    var largeFileBeans = mutableListOf<LargeFile>()
    var screenshots = mutableListOf<Screenshot>()
    var tag: String? = null
    var key = ""
    var toFinished: Boolean = false

    override fun initializeActivity() {
        super.initializeActivity()
        EventBus.getDefault().register(this)
        binding.ivClean.setImageAssetsFolder("images/")
        val bundle = intent.extras
        tag = bundle?.getString(AppConfig.NEXT_TAG)
        when (tag) {
            getString(R.string.remove_junk) -> {
                binding.content.setBackgroundColor(getColor(R.color.theme_color))
                binding.ivClean.setAnimation(R.raw.junk)
            }
            else->{
                binding.content.setBackgroundColor(getColor(R.color.large_scan_bg))
                binding.ivClean.setAnimation(R.raw.docclean)
            }
        }
        onBackPressedDispatcher.addCallback(this,object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }





    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onMessageEvent(cleanFilesBean: CleanFilesBean) {
        // 处理事件（在主线程执行）
        size = cleanFilesBean.size
        junkMotherBeans.addAll(cleanFilesBean.groups)
        binding.tvCleanSize.text = "" + SizeUtil.formatSize3(size)
        startCleanup()
        EventBus.getDefault().removeStickyEvent(cleanFilesBean)
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onMessageEvent(cleanLargeFileBean: CleanLargeFileBean) {
        // 处理事件（在主线程执行）
        size = cleanLargeFileBean.size
        largeFileBeans.addAll(cleanLargeFileBean.groups)
        binding.tvCleanSize.text = "" + SizeUtil.formatSize3(size)
        startCleanup()
        EventBus.getDefault().removeStickyEvent(cleanLargeFileBean)
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: ScreenshotEvent) {
        // 处理事件（在主线程执行）
        size = event.totalSize
        screenshots.addAll(event.data)
        binding.tvCleanSize.text = "" + SizeUtil.formatSize3(size)

        startCleanup()
        EventBus.getDefault().removeStickyEvent(event)

    }


    // 开始清理
    @SuppressLint("UseCompatLoadingForDrawables")
    private fun startCleanup() {
        lifecycleScope.launch {
            delay(1500)
            try {
                // 执行清理操作
                withContext(Dispatchers.IO) {
                    deleteJunkFiles { remainingSize ->
                        // 在主线程更新UI
                        launch(Dispatchers.Main) {
                            if (remainingSize > 0) {
                                binding.tvCleanSize.text = "" + SizeUtil.formatSize3(remainingSize)
                            } else {
                                binding.tvCleanSize.text = "0KB"
                                if (!toFinished) {
                                    toFinished = true
                                    readyGoThenKill(FinishedActivity::class.java, intent.extras)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {

            }
        }
    }

    // 删除垃圾文件
    private suspend fun deleteJunkFiles(onProgress: (Long) -> Unit) {
        val totalSize = size
        var remainingSize = totalSize
        // 模拟逐步清理过程
        val chunkSize = totalSize / 20 // 分为20个步骤
        repeat(20) {
            // 检查是否取消
            // 模拟删除一部分文件
            remainingSize -= chunkSize
            onProgress(remainingSize.coerceAtLeast(0))
            // 模拟删除操作的延迟
            delay(100)
        }
        // 实际删除文件
        try {
            when (tag) {
                getString(R.string.remove_junk) -> {
                    // 清理缓存
                    for (junkMotherBean in junkMotherBeans) {
                        if (junkMotherBean.isChecked && junkMotherBean.size > 0) {
                            for (junkChildBean in junkMotherBean.junkChildrenItems) {
                                var file: File = File(junkChildBean.path!!)
                                if (file.exists()) {
                                    try {
                                        file.delete()
                                    } catch (e: Exception) {
                                        continue
                                    }
                                }
                            }
                        }
                    }
                }

                getString(R.string.screenshot_manager) -> {
                    screenshots.forEach { file ->
                        try {
                            val fileObj = File(file.path)
                            if (fileObj.exists()) {
                                fileObj.delete()
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                // 使用MediaStore删除
                                val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                                    .buildUpon()
                                    .appendPath(file.id.toString())
                                    .build()
                                contentResolver.delete(uri, null, null)
                            }
                        } catch (e: Exception) {
                        }
                    }
                }

                else->{
                    largeFileBeans.forEach { file ->
                        try {
                            val fileObj = File(file.path)
                            if (fileObj.exists()) {
                                fileObj.delete()
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                // 使用MediaStore删除
                                val uri = ContentUris.withAppendedId(
                                    MediaStore.Files.getContentUri("external"),
                                    file.id
                                )
                                contentResolver.delete(uri, null, null)
                            }
                        } catch (e: Exception) {

                        }
                    }
                }

            }
            // 确保进度显示为100%
            onProgress(0)
        } catch (e: Exception) {
            // throw e
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        binding.ivClean.cancelAnimation()
        binding.ivClean.clearAnimation()
        EventBus.getDefault().unregister(this)

    }






}
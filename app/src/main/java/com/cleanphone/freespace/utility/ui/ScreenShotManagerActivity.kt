package com.cleanphone.freespace.utility.ui

import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import androidx.activity.OnBackPressedCallback
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.chad.library.adapter.base.BaseMultiItemQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.cleanphone.freespace.utility.R
import com.cleanphone.freespace.utility.advertise.AdvertisePageMediator
import com.cleanphone.freespace.utility.base.activity.BaseMvvmActivity
import com.cleanphone.freespace.utility.base.command.log
import com.cleanphone.freespace.utility.base.command.readyGoThenKill
import com.cleanphone.freespace.utility.base.command.safeClick
import com.cleanphone.freespace.utility.base.config.AppConfig
import com.cleanphone.freespace.utility.bean.Screenshot
import com.cleanphone.freespace.utility.bean.ScreenshotsData
import com.cleanphone.freespace.utility.databinding.ActivityScreenShotManagerBinding
import com.cleanphone.freespace.utility.utils.SizeUtil
import com.cleanphone.freespace.utility.view.CleanFileDialog
import com.cleanphone.freespace.utility.viewmodel.ScreenShotManagerViewModel
import com.cleanphone.freespace.utility.viewmodel.ScreenShotUIState
import com.cleanphone.freespace.utility.bean.ScreenshotEvent
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.text.DateFormat
import kotlin.jvm.java

class ScreenShotManagerActivity :  BaseMvvmActivity<ActivityScreenShotManagerBinding, ScreenShotManagerViewModel>(){

    private lateinit var appAdapter: ScreenShotAdapter
    private var cleanFileDialog: CleanFileDialog? = null
    private val dateFormat by lazy(LazyThreadSafetyMode.NONE) {
        DateFormat.getDateTimeInstance(
            DateFormat.MEDIUM,
            DateFormat.MEDIUM,
            resources.configuration.locales[0]
        )
    }

    private fun setupEventBus() {
        EventBus.getDefault().register(this)
    }

    private fun setupUI() {
        setupBackButton()
        setupSelectAllButton()
        setupDeleteButton()
        setupObservers()
        setupBackPressHandler()
        val details=intent.extras?.getString("details")
        val referrer_name=intent.extras?.getString("referrer_name")
        if(!TextUtils.isEmpty( details)){
            log("enter_corefeatures_page",mapOf("details" to details!! ,"referrer_name" to referrer_name!!))
        }
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onBack()
            }
        })
    }
    fun onBack(){
        AdvertisePageMediator.showReturnHomeInterstitial(this) {
            readyGoThenKill(MainActivity::class.java)
        }
    }


    private fun setupBackButton() {
        binding.llBack.safeClick { onBack()}
    }

    private fun setupSelectAllButton() {
        binding.rlSelectAll.setOnClickListener {
            viewModel.toggleSelectAll()
        }
    }


    private fun setupDeleteButton() {
        binding.btDelete.setOnClickListener {
            handleDeleteAction()
        }
    }



    private fun setupObservers() {
        viewModel.uiState.observe(this) { uiState ->
            updateUI(uiState)
        }

        viewModel.isSelectAll.observe(this) { isSelectAll ->
            updateSelectAllCheckbox(isSelectAll)
        }

        // 观察Adapter数据变化
        viewModel.adapterDataChanged.observe(this) { changed ->
            if (changed) {
                // 刷新Adapter数据
                appAdapter.setList(viewModel.getScreenshotList())
                // 重置变化标志
                viewModel.resetAdapterDataChanged()
            }
        }
    }

    private fun updateUI(uiState: ScreenShotUIState) {
        if (uiState.hasData) {
            binding.rlNodata.visibility = View.GONE
            binding.rlSelectAll.visibility = View.VISIBLE
            binding.tvSize.text = uiState.totalSizeText
            with(binding.btDelete) {
                text = uiState.deleteButtonText
                if (uiState.deleteButtonText.equals(getString(R.string.delete))) {
                    isClickable = false
                    isEnabled = false
                    background = getDrawable(R.drawable.btn_unclick)
                } else {
                    isClickable = true
                    isEnabled = true
                    background = getDrawable(R.drawable.btn_nor)
                }
            }

        } else {
            binding.rlNodata.visibility = View.VISIBLE
            binding.rlSelectAll.visibility = View.GONE
            binding.btDelete.text = getString(R.string.back_to_main_page)
        }
    }
    private fun updateSelectAllCheckbox(isSelectAll: Boolean) {
        val checkIcon = if (isSelectAll) R.mipmap.ic_check else R.mipmap.ic_check_nor
        binding.ivCheck.setImageResource(checkIcon)
    }



    private fun handleDeleteAction() {
        if(viewModel.hasScreenshotData()){
            showCleanFileDialog()
        }else{
            AdvertisePageMediator.showReturnHomeInterstitial(this) {
                readyGoThenKill(MainActivity::class.java)
            }
        }
    }
//    private fun navigateToNextActivity() {
//        val bundle = Bundle().apply {
//            putString(AppConfig.NEXT_TAG, getString(R.string.screenshot_manager))
//        }
//        readyGoThenKill(NextStepActivity::class.java, bundle)
//    }

    private fun showCleanFileDialog() {
        if (cleanFileDialog == null) {
            cleanFileDialog = CleanFileDialog(this).apply {
                setHint(getString(R.string.delete_screenshots_hint))
                setmOnClickListener(object : CleanFileDialog.onClickListener {
                    override fun onNext() {
                        processScreenshotDeletion()
                    }
                })
            }
        }
        cleanFileDialog!!.showDialog()
    }

    private fun processScreenshotDeletion() {
        val selectedData = viewModel.selectedScreenshots.value ?: mutableListOf()
        val deleteSize = viewModel.deleteSize.value ?: 0L

        EventBus.getDefault().postSticky(ScreenshotEvent(selectedData, deleteSize))
        val bundle = Bundle().apply {
            putString(AppConfig.NEXT_TAG, getString(R.string.screenshot_manager))
        }
        readyGoThenKill(CleanFilesActivity::class.java, bundle)
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: ScreenshotsData) {
        viewModel.setScreenshotsData(event)
        initAdapter()
        appAdapter.setList(event.data)
        setupRecyclerView()
        EventBus.getDefault().removeStickyEvent(event)
    }

    private fun initAdapter() {
        appAdapter = ScreenShotAdapter()
    }

    private inner class ScreenShotAdapter(
    ) : BaseMultiItemQuickAdapter<Screenshot, BaseViewHolder>() {
        init {
            // 注册两种布局
            addItemType(0, R.layout.item_largefile)
        }
        override fun convert(holder: BaseViewHolder, item: Screenshot) {
            bindScreenshotItem(holder, item)
        }
    }

    private fun bindScreenshotItem(holder: BaseViewHolder, item: Screenshot) {
        with(item) {
            holder.setText(R.id.tv_name, name)
            holder.setText(
                R.id.tv_info,
                "${SizeUtil.formatSize(size)}  " +
                        dateFormat.format(dateTaken)
            )
            loadScreenshotImage(holder, contentUri!!)
            updateCheckboxState(holder, isSelected)
        }

        setupItemClickListeners(holder, item)
    }

    private fun loadScreenshotImage(holder: BaseViewHolder, contentUri: Uri) {
        Glide.with(this@ScreenShotManagerActivity)
            .load(contentUri)
            .placeholder(R.mipmap.ic_shotes)
            .error(R.mipmap.ic_shotes)
            .into(holder.getView<ImageView>(R.id.iv_largefile))
    }

    private fun updateCheckboxState(holder: BaseViewHolder, isSelected: Boolean) {
        val checkIcon = if (isSelected) R.mipmap.ic_check else R.mipmap.ic_check_nor
        holder.setImageResource(R.id.iv_check, checkIcon)
    }

    private fun setupItemClickListeners(holder: BaseViewHolder, item: Screenshot) {
        holder.getView<RelativeLayout>(R.id.rl_function).setOnClickListener {
            viewModel.toggleScreenshotSelection(item)
            // 直接更新当前项，而不是刷新整个列表
            appAdapter.notifyItemChanged(holder.adapterPosition)
        }

        holder.getView<ImageView>(R.id.iv_largefile).setOnClickListener {
           // navigateToPhotoViewer(item)
        }
    }

//    private fun navigateToPhotoViewer(item: Screenshot) {
//        val bundle = Bundle().apply {
//            putString("Uri", item.contentUri.toString())
//        }
//        readyGo(ShowPhotoActivity::class.java, bundle)
//    }

    private fun setupRecyclerView() {
        binding.rcScreenshot.apply {
            adapter = appAdapter
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(
                this@ScreenShotManagerActivity,
                RecyclerView.VERTICAL,
                false
            )
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        cleanupResources()
    }
    override fun init() {
        setupEventBus()
        setupUI()
    }
    private fun cleanupResources() {
        EventBus.getDefault().unregister(this)
        cleanFileDialog?.release()
        cleanFileDialog = null
        viewModel.clearSelection()
    }




}
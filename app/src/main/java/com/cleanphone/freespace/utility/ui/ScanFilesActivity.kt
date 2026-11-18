package com.cleanphone.freespace.utility.ui

import android.R.attr.text
import android.annotation.SuppressLint
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil.setContentView
import androidx.lifecycle.lifecycleScope
import com.cleanphone.freespace.utility.R
import com.cleanphone.freespace.utility.adapter.ExpandableListAdapter
import com.cleanphone.freespace.utility.advertise.AdvertisePageMediator
import com.cleanphone.freespace.utility.base.activity.BaseMvvmActivity
import com.cleanphone.freespace.utility.base.activity.BaseViewActivity
import com.cleanphone.freespace.utility.base.command.log
import com.cleanphone.freespace.utility.base.command.rateUS
import com.cleanphone.freespace.utility.base.command.readyGoThenKill
import com.cleanphone.freespace.utility.base.command.safeClick
import com.cleanphone.freespace.utility.base.config.AppConfig
import com.cleanphone.freespace.utility.bean.CleanFilesBean
import com.cleanphone.freespace.utility.bean.JunkMotherBean
import com.cleanphone.freespace.utility.databinding.ActivityScanFilesBinding
import com.cleanphone.freespace.utility.utils.AppPrefsUtils
import com.cleanphone.freespace.utility.utils.JunkFileScanner
import com.cleanphone.freespace.utility.utils.SizeUtil
import com.cleanphone.freespace.utility.view.CleanHintDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus

class ScanFilesActivity : BaseViewActivity<ActivityScanFilesBinding>() {
    companion object {
        private const val MIN_SCAN_DISPLAY_TIME = 4000L
        private const val SCAN_UPDATE_INTERVAL = 200L
        private const val JUNK_CATEGORY_COUNT = 4
    }

    // UI State
    private var isScanning: Boolean = false
    private var isScanFinished: Boolean = false
    private var totalJunkSize: Long = 0
    private var scanStartTime: Long = 0

    // Adapter & Data
    private lateinit var expandableListAdapter: ExpandableListAdapter
    private val junkMotherBeans = mutableListOf<JunkMotherBean>()

    // Components
    private var cleanHintDialog: CleanHintDialog? = null
    private lateinit var scanner: JunkFileScanner


    // Coroutines
    private var scanJob: Job? = null
    private var timeJob: Job? = null


    override fun initializeActivity() {
        super.initializeActivity()
        initJunkData()
        initAdapter()
        setupBackPressHandler()
        startScanning()
        initializeViews()
    }

    private fun initializeViews() {
        binding.ivScanBack.safeClick { onBack() }
        binding.tvRate.setOnClickListener { rateUS() }
        binding.btClean.safeClick {
            navigateToCleanFiles()
        }
    }

    private fun initJunkData() {
        val details=intent.extras?.getString("details")
        val referrer_name=intent.extras?.getString("referrer_name")
        if(!TextUtils.isEmpty( details)){
            log("enter_corefeatures_page",mapOf("details" to details!! ,"referrer_name" to referrer_name!!))
        }
        val junkCategories = listOf(
            JunkMotherBean(getString(R.string.obsolete_files),  0, mutableListOf()),
            JunkMotherBean(getString(R.string.residuals),  0, mutableListOf()),
            JunkMotherBean(getString(R.string.ad_junk), 0, mutableListOf()),
            JunkMotherBean(getString(R.string.trash_items), 0, mutableListOf())
        )
        junkMotherBeans.addAll(junkCategories)
        scanner = JunkFileScanner(this, junkMotherBeans)
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this,object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onBack()
            }
        })
    }

    private fun onBack() {
        if (isScanning) {
            showScanningDialog()
        } else {
            handleBackAfterScan()
        }
    }

    private fun handleBackAfterScan() {
        if (totalJunkSize > 0) {
            showCleanHintDialog()
        } else {
            AdvertisePageMediator.showReturnHomeInterstitial(this) {
                readyGoThenKill(MainActivity::class.java)
            }
        }
    }
    private fun showCleanHintDialog() {
        val hint = createFormattedHintText()
        getOrCreateCleanHintDialog().toShow(hint, true)
    }

    private fun createFormattedHintText(): SpannableString {
        val formattedSize = SizeUtil.formatSize3(totalJunkSize)
        val hintText = "$formattedSize ${getString(R.string.cleaning_in_progress)}"
        val hint = SpannableString(hintText)
        hint.setSpan(
            ForegroundColorSpan(getColor(R.color.theme_color)),
            0,
            formattedSize.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return hint
    }

    private fun showScanningDialog() {
        getOrCreateCleanHintDialog().toShow(getString(R.string.scanning_in_progress), false)
    }

    private fun getOrCreateCleanHintDialog(): CleanHintDialog {
        if (cleanHintDialog == null) {
            cleanHintDialog = CleanHintDialog(this).apply {
                setmOnClickListener(object : CleanHintDialog.onClickListener {
                    override fun onNext(isTodelete: Boolean) {
                        if (isTodelete) {
                            navigateToCleanFiles()
                        }
                    }
                    override fun onStop() {
                        AdvertisePageMediator.showReturnHomeInterstitial(this@ScanFilesActivity) {
                            readyGoThenKill(MainActivity::class.java)
                        }
                    }
                })
            }
        }
        return cleanHintDialog!!
    }

    private fun navigateToCleanFiles() {
        val bundle = Bundle()
        bundle.putString(AppConfig.NEXT_TAG, getString(R.string.remove_junk))
        if(totalJunkSize>0){
            EventBus.getDefault().postSticky(CleanFilesBean(totalJunkSize, junkMotherBeans))
            bundle.putString(AppConfig.TASK_TITLE, "" + SizeUtil.formatSize3(totalJunkSize)+getString(R.string.junk_files))
            bundle.putString(AppConfig.TASK_CONTENT, getString(R.string.have_been_cleared))
            readyGoThenKill(CleanFilesActivity::class.java, bundle)
        }else{
            readyGoThenKill(FinishedActivity::class.java, bundle)
        }
    }
    private fun initAdapter() {
        expandableListAdapter = ExpandableListAdapter(this, junkMotherBeans)
        expandableListAdapter.setmonCheckChangeLister(createCheckChangeListener())
        binding.exJunk.setAdapter(expandableListAdapter)
    }

    private fun createCheckChangeListener(): ExpandableListAdapter.OnCheckChangeLister {
        return object : ExpandableListAdapter.OnCheckChangeLister {
            @SuppressLint("UseCompatLoadingForDrawables")
            override fun onCheckChange() {
                totalJunkSize = junkMotherBeans
                    .filter { it.isChecked }
                    .sumOf { it.size }

                updateCleanButtonState()
            }
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun updateCleanButtonState() {
        if (totalJunkSize > 0) {
            binding.tvSize.text = SizeUtil.formatSize(totalJunkSize)
        } else {
            binding.tvSize.text = "0KB"
        }
    }


    private fun startScanning() {
        isScanning = true
        isScanFinished = false
        scanStartTime = System.currentTimeMillis()
        startScanJob()
        startTimeMonitoringJob()
    }

    private fun startTimeMonitoringJob() {
        timeJob = lifecycleScope.launch {
            while (isActive) {
                if (shouldFinishScan()) {
                    onScanFinished()
                    return@launch
                }
                delay(SCAN_UPDATE_INTERVAL)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun onScanFinished() {
        isScanning = false
        updateJunkListVisibility()
        updateUIAfterScan()
    }



    @SuppressLint("UseCompatLoadingForDrawables")
    private fun updateUIAfterScan() {
        binding.rotatingRingView.visibility=View.GONE
        binding.tvState.visibility=View.GONE
        binding.tvSize.visibility=View.VISIBLE
        binding.tvHint.visibility=View.VISIBLE
        binding.llHint.visibility=View.VISIBLE
        binding.btClean.background = getDrawable(R.drawable.btn_selector)
        binding.btClean.isClickable = true
        binding.btClean.isEnabled = true
        totalJunkSize = junkMotherBeans.sumOf { it.size }
        if (totalJunkSize > 0) {
            setupUIForJunkFound()
        } else {
            setupUIForNoJunk()
        }
    }
    @SuppressLint("UseCompatLoadingForDrawables")
    private fun setupUIForJunkFound() {
        binding.tvSize.text = SizeUtil.formatSize3(totalJunkSize)
        binding.tvCleanHint.text = getString(R.string.junk_delete_hint)
        binding.tvCleanHint.setTextColor(getColor(R.color.junk_hint_color))
        binding.ivCleanHint.setImageResource(R.mipmap.ic_clean_hint)
        binding.btClean.text=getString(R.string.clean_now)
        binding.tvRate.visibility = View.GONE
    }


    @SuppressLint("UseCompatLoadingForDrawables")
    private fun setupUIForNoJunk() {
        binding.tvSize.text = "0KB"
        binding.ivCleanHint.setImageResource(R.mipmap.ic_check)
        binding.tvCleanHint.text = getString(R.string.very_clean)
        binding.tvCleanHint.setTextColor(getColor(R.color.theme_color))
        binding.tvRate.visibility = View.VISIBLE
        binding.btClean.text=getString(R.string.ok)
    }

    private fun updateJunkListVisibility() {
        repeat(JUNK_CATEGORY_COUNT) { index ->
            junkMotherBeans[index].isShowCheck = true
        }
        expandableListAdapter.notifyDataSetChanged()
    }

    private fun shouldFinishScan(): Boolean {
        return isScanFinished &&
                (System.currentTimeMillis() - scanStartTime) > MIN_SCAN_DISPLAY_TIME
    }

    private fun startScanJob() {
        scanJob = lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    scanner.scanJunkFiles()
                }
            } catch (e: Exception) {
                // Handle scanning error - could show user feedback
                e.printStackTrace()
            } finally {
                isScanFinished = true
            }
        }
    }




    override fun onDestroy() {
        cleanHintDialog?.release()
        cleanHintDialog=null
        timeJob?.cancel()
        scanJob?.cancel()
        timeJob = null
        scanJob = null
        scanner.destroy()
        super.onDestroy()

    }






}
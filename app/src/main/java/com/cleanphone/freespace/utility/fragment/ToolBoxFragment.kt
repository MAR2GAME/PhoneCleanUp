package com.cleanphone.freespace.utility.fragment

import android.os.Bundle
import android.widget.RelativeLayout
import androidx.recyclerview.widget.GridLayoutManager
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.cleanphone.freespace.utility.R
import com.cleanphone.freespace.utility.advertise.AdAreaKeys
import com.cleanphone.freespace.utility.advertise.AdvertisePageMediator
import com.cleanphone.freespace.utility.base.command.log
import com.cleanphone.freespace.utility.base.command.readyGo
import com.cleanphone.freespace.utility.base.config.AppConfig
import com.cleanphone.freespace.utility.base.fragment.BaseMvvmFragment
import com.cleanphone.freespace.utility.bean.ToScanEvent
import com.cleanphone.freespace.utility.bean.ToolBeans
import com.cleanphone.freespace.utility.databinding.FragmentToolBinding
import com.cleanphone.freespace.utility.ui.BatteryInfoActivity
import com.cleanphone.freespace.utility.ui.LoadingActivity
import com.cleanphone.freespace.utility.viewmodel.ToolFragmentViewModel
import org.greenrobot.eventbus.EventBus
import kotlin.jvm.java

class ToolBoxFragment : BaseMvvmFragment<FragmentToolBinding, ToolFragmentViewModel>(){
    private lateinit  var toolAdapter: BaseQuickAdapter<ToolBeans, BaseViewHolder>
    private var toolBeans=mutableListOf<ToolBeans>()
    override fun init() {
        initAdapter()
        binding.rlProcessManager.setOnClickListener {
            log("click_corefeatures_btn",mapOf("details" to "processManager","referrer_name" to "homepage"))
            AdvertisePageMediator.showFeatureEntryInterstitialByAreaKey(requireActivity(),AdAreaKeys.Interstitial.ENTER_PROCESS_MANAGE){
                readyGoLoadingActivity(getString(R.string.process_manager),"processManager")
            }
        }
        binding.rlBatteryInfo.setOnClickListener {
            log("click_corefeatures_btn",mapOf("details" to "batteryInfo","referrer_name" to "homepage"))
            AdvertisePageMediator.showFeatureEntryInterstitialByAreaKey(requireActivity(),AdAreaKeys.Interstitial.ENTER_BATTERY_INFO){
                readyGoLoadingActivity(getString(R.string.battery_info),"batteryInfo")
            }

        }
        binding.rlSpeaker.setOnClickListener {
            log("click_corefeatures_btn",mapOf("details" to "speakerCleaner","referrer_name" to "homepage"))
            AdvertisePageMediator.showFeatureEntryInterstitialByAreaKey(requireActivity(),AdAreaKeys.Interstitial.ENTER_SPEAKER_CLEAN){
                readyGoLoadingActivity(getString(R.string.speaker_cleaner),"speakerCleaner")
            }
        }
        binding.rlApp.setOnClickListener {
            log("click_corefeatures_btn",mapOf("details" to "appManager","referrer_name" to "homepage"))
            AdvertisePageMediator.showFeatureEntryInterstitialByAreaKey(requireActivity(),AdAreaKeys.Interstitial.ENTER_APP_MANAGE){
                readyGoLoadingActivity(getString(R.string.app_manager),"appManager")
            }
        }

    }

    fun readyGoLoadingActivity(tag:String,details: String){
        val bundle = Bundle().apply {
            putString(AppConfig.NEXT_TAG, tag)
            putString("details", details)
            putString("referrer_name", "homepage")
        }
        requireActivity().readyGo(LoadingActivity::class.java, bundle)
    }
   fun  initAdapter(){
       toolAdapter= object : BaseQuickAdapter<ToolBeans, BaseViewHolder>(R.layout.item_tool){
           override fun convert(
               holder: BaseViewHolder,
               item: ToolBeans
           ) {
               holder.setText(R.id.tv_name,  item.title)
               holder.setImageResource(R.id.iv_res,item.res)
               holder.getView<RelativeLayout>(R.id.rl_content).setOnClickListener{
                   AdvertisePageMediator.showFeatureEntryInterstitialByAreaKey(requireActivity(),AdAreaKeys.Interstitial.ENTER_FILE_MANAGE){
                       log("click_corefeatures_btn",mapOf("details" to "fileManage","referrer_name" to "homepage"))
                       var tag=item.title
                       if(tag==getString(R.string.screenshots)){
                           tag=getString(R.string.screenshot_manager)
                       }
                       if(tag==getString(R.string.large_files)){
                           tag=getString(R.string.large_file_cleaner)
                       }
                       EventBus.getDefault().post(ToScanEvent(tag))
                   }
               }
           }
       }
       binding.rcTool.adapter=toolAdapter
       binding.rcTool.layoutManager= GridLayoutManager(requireActivity(),2)
       toolBeans.add(ToolBeans(R.mipmap.ic_image,getString(R.string.images)))
       toolBeans.add(ToolBeans(R.mipmap.ic_video,getString(R.string.video)))
       toolBeans.add(ToolBeans(R.mipmap.ic_audio,getString(R.string.audio)))
       toolBeans.add(ToolBeans(R.mipmap.ic_shotes,getString(R.string.screenshots)))
       toolBeans.add(ToolBeans(R.mipmap.ic_apk,getString(R.string.apk)))
       toolBeans.add(ToolBeans(R.mipmap.ic_file,getString(R.string.large_files)))
       toolAdapter.setList(toolBeans)

    }
}

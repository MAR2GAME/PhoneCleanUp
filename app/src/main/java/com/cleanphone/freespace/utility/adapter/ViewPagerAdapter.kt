package com.cleanphone.freespace.utility.adapter


import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.cleanphone.freespace.utility.fragment.HomeFragment
import com.cleanphone.freespace.utility.fragment.SettingFragment
import com.cleanphone.freespace.utility.fragment.ToolBoxFragment

class ViewPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {
    override fun getItemCount(): Int = 3
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> HomeFragment()
            1 -> ToolBoxFragment()
            else -> SettingFragment()
        }
    }
}
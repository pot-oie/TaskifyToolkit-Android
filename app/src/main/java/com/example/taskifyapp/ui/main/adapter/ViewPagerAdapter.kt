package com.example.taskifyapp.ui.main.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.taskifyapp.ui.main.fragment.InfoFragment
import com.example.taskifyapp.ui.main.fragment.LogFragment
import com.example.taskifyapp.ui.main.fragment.SettingsFragment

class ViewPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> LogFragment()
            1 -> SettingsFragment()
            else -> InfoFragment()
        }
    }
}
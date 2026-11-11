@file:Suppress("PackageName")

package com.cabgon.mercadeocomitan.Fragmentos

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.cabgon.mercadeocomitan.databinding.FragmentMisAnunciosBinding
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener


class FragmentMisAnuncios : Fragment() {

    private lateinit var binding : FragmentMisAnunciosBinding
    private lateinit var mContext : Context
    private lateinit var mTabsViewPagerAdapter : MyTabsViewPagerAdapter
    private val pageChangeCallback = object : OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            binding.tabLayout.selectTab(binding.tabLayout.getTabAt(position))
        }
    }

    override fun onAttach(context: Context) {
        this.mContext = context
        super.onAttach(context)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        // Inflate the layout for this fragment
        binding = FragmentMisAnunciosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Publicados"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Favoritos"))

        val fragmentManager = childFragmentManager

        mTabsViewPagerAdapter = MyTabsViewPagerAdapter(fragmentManager, lifecycle)
        binding.viewPager.adapter = mTabsViewPagerAdapter

        binding.tabLayout.addOnTabSelectedListener(object : OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                binding.viewPager.currentItem = tab.position
            }

            override fun onTabUnselected(p0: TabLayout.Tab?) {

            }

            override fun onTabReselected(p0: TabLayout.Tab?) {

            }
        })

        binding.viewPager.offscreenPageLimit = 1
        binding.viewPager.registerOnPageChangeCallback(pageChangeCallback)
    }

    override fun onDestroyView() {
        binding.viewPager.unregisterOnPageChangeCallback(pageChangeCallback)
        binding.viewPager.adapter = null
        super.onDestroyView()
    }

    class MyTabsViewPagerAdapter(fragmentManager: FragmentManager, lifecycle: Lifecycle) :
        FragmentStateAdapter(fragmentManager, lifecycle) {
        override fun createFragment(position: Int): Fragment {
            return if (position == 0) {
                Mis_Anuncios_Publicados_Fragment()
            } else {
                Fav_Anuncios_Fragment()
            }
        }

        override fun getItemCount(): Int {
            return 2
        }
    }

}

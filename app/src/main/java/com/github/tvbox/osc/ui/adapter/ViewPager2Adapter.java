package com.github.tvbox.osc.ui.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.github.tvbox.osc.ui.fragment.HomeFragment;
import com.github.tvbox.osc.ui.fragment.MyFragment;

public class ViewPager2Adapter extends FragmentStateAdapter {
    HomeFragment blankFragment = new HomeFragment();
    MyFragment blankFragment2 = new MyFragment();
 
    public ViewPager2Adapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }
 
    public int getItemCount() {
        return 2;
    }
 
    @NonNull
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return blankFragment;
            case 1:
                return blankFragment2;
        }
        return blankFragment;
    }
}
package com.cwgoover.applications;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import com.cwgoover.applications.ManageApplications.PagerInfo;

import java.util.ArrayList;

/**
 * This class used to create ViewPagerTabButton for each tab.
 */
public class FixedTabsAdapter implements TabsAdapter {

    private final Activity mActivity;

    private final ArrayList<PagerInfo> mPagers;

    public FixedTabsAdapter(Activity activity, ArrayList<PagerInfo> pagers) {
        mActivity = activity;
        mPagers = pagers;
    }

    @Override
    public Activity getActivity() {
        return mActivity;
    }

    @Override
    public View getTabView(int position) {
        ViewPagerTabButton tab;

        LayoutInflater inflater = (LayoutInflater) mActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        tab = (ViewPagerTabButton) inflater.inflate(R.layout.tab_fixed, null);

        if (position < mPagers.size()) tab.setText(mPagers.get(position).mTabLabel);
        return tab;
    }
}

package com.cwgoover.applications;

import android.app.Activity;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.cwgoover.applications.ManageApplications.PagerInfo;

import java.util.ArrayList;

public class ApplicationsPagerAdapter extends PagerAdapter {

    public static final String TAG = ManageApplications.TAG + ".pager";

    protected transient Activity mContext;

    private final LayoutInflater mInflater;
    private final ArrayList<PagerInfo> mPagerInfos;

    public ApplicationsPagerAdapter(Activity activity, ArrayList<PagerInfo> infos) {
        mInflater = activity.getLayoutInflater();
        mPagerInfos = infos;
    }

    @Override
    public int getCount() {
        return mPagerInfos.size();
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        PagerInfo pager = mPagerInfos.get(position);
        View root = pager.buildPager(mInflater);
        ((ViewPager) container).addView(root);
        return root;
    }

    @Override
    public boolean isViewFromObject(View view, Object object) {
        return view == ((View) object);
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object view) {
        ((ViewPager) container).removeView((View) view);
    }
}

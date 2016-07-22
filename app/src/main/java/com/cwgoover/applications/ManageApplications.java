package com.cwgoover.applications;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.SearchView;

import com.cwgoover.applications.R;
import com.cwgoover.applications.ApplicationsAdapter.ToggleClickListener;

import java.util.ArrayList;
import java.util.List;

public class ManageApplications extends Activity implements ToggleClickListener,
                SearchView.OnQueryTextListener {

    public static final String TAG = "Mgapplication";
    public static final boolean DEBUG = true;

    public static final int LIST_TYPE_SYSTEM = 0;
    public static final int LIST_TYPE_DOWNLOAD = 1;
    public static final int LIST_TYPE_DISABLED = 2;

    private ViewPager mViewPager;
    private LayoutInflater mInflater;
    private PackageManager mPM;

    private final ArrayList<PagerInfo> mPagerInfos = new ArrayList<PagerInfo>();
    private int mNumPagers;
    private PagerInfo mCurPager = null;
    private Menu mOptionsMenu;
    private boolean mSorted;
    private boolean mEnableGMS = true;

    // These are for keeping track of activity and spinner switch state.
    private boolean mActivityResumed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.manage_applications_content);

        ApplicationsState applicationsState = ApplicationsState.getInstance(getApplication());
        mInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mPM = getPackageManager();

        // build all the PagerInfo for mPagerInfos.
        PagerInfo pager = new PagerInfo(this, applicationsState,
                    getResources().getString(R.string.filter_apps_system), LIST_TYPE_SYSTEM);
        mPagerInfos.add(pager);

        pager = new PagerInfo(this, applicationsState,
                getResources().getString(R.string.filter_apps_third_party), LIST_TYPE_DOWNLOAD);
        mPagerInfos.add(pager);

        pager = new PagerInfo(this, applicationsState,
                getResources().getString(R.string.filter_apps_disabled), LIST_TYPE_DISABLED);
        mPagerInfos.add(pager);

        mNumPagers = mPagerInfos.size();

        initViewPager(mNumPagers);
        FixedTabsView fixedTabs = (FixedTabsView) findViewById(R.id.fixed_tabs);
        TabsAdapter fixedTabsAdatper = new FixedTabsAdapter(this, mPagerInfos);
        fixedTabs.setAdapter(fixedTabsAdatper);
        fixedTabs.setViewPager(mViewPager);

        // change action bar's background
//        ActionBar bar = getActionBar();
//        bar.setTitle(Html.fromHtml("<font color='#ffffff'>App Management </font>"));
//        bar.setBackgroundDrawable(new ColorDrawable(getResources()
//                                        .getColor(R.color.app_manage_tab_background)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        mActivityResumed = true;
        updateCurrentTab(mViewPager.getCurrentItem());
    }

    @Override
    protected void onPause() {
        super.onPause();
        mActivityResumed = false;
        for (int i = 0; i < mNumPagers; i++) {
            mPagerInfos.get(i).pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        for (int i = 0; i < mNumPagers; i++) {
            mPagerInfos.get(i).release();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mOptionsMenu = menu;
        getMenuInflater().inflate(R.menu.menu_app_management, menu);

        SearchManager manager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        SearchView searchView = (SearchView) menu.findItem(R.id.action_search).getActionView();
        searchView.setSearchableInfo(manager.getSearchableInfo(getComponentName()));
        searchView.setOnQueryTextListener(this);

        updateOptionsMenu();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_sort:
                if (mSorted) {
                    mSorted = false;
                    if (mCurPager != null && mCurPager.mListAdapter != null) {
                        mCurPager.mListAdapter.rebuild(false, true);
                    }
                } else {
                    mSorted = true;
                    if (mCurPager != null && mCurPager.mListAdapter != null) {
                        mCurPager.mListAdapter.rebuild(true, true);
                    }
                }
                updateOptionsMenu();
                break;
            case R.id.menu_disable_gms:
                mEnableGMS = false;
                new GMSTask(false).execute();
                break;
            case R.id.menu_enable_gms:
                mEnableGMS = true;
                new GMSTask(true).execute();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onQueryTextChange(String searchString) {
        for (int i = 0; i < mPagerInfos.size(); i++) {
            mPagerInfos.get(i).mListAdapter.getFilter().filter(searchString);
        }
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    private void updateOptionsMenu() {
        if (mOptionsMenu == null) return;

        MenuItem sortItem = mOptionsMenu.findItem(R.id.menu_sort);
        if (mSorted) {
            sortItem.setTitle(getResources().getString(R.string.menu_app_sort_default));
        } else {
            sortItem.setTitle(getResources().getString(R.string.menu_app_sort_name));
        }

        if (mEnableGMS) {
            mOptionsMenu.findItem(R.id.menu_disable_gms).setVisible(true);
            mOptionsMenu.findItem(R.id.menu_enable_gms).setVisible(false);
        } else {
            mOptionsMenu.findItem(R.id.menu_disable_gms).setVisible(false);
            mOptionsMenu.findItem(R.id.menu_enable_gms).setVisible(true);
        }
    }

    private void initViewPager(int pageCount) {
        mViewPager = (ViewPager) findViewById(R.id.pager);
        PagerAdapter mPagerAdapter = new ApplicationsPagerAdapter(this, mPagerInfos);
        mViewPager.setAdapter(mPagerAdapter);
        mViewPager.setCurrentItem(LIST_TYPE_DOWNLOAD);
        mViewPager.setPageMargin(1);
    }

    public void updateCurrentTab(int position) {
        mCurPager = mPagerInfos.get(position);

        // Put things in the correct paused/resumed state.
        if (mActivityResumed) {
            mCurPager.buildPager(mInflater);
            mCurPager.resume(mSorted);
        } else {
            mCurPager.pause();
        }
        for (int i = 0; i < mNumPagers; i++) {
            PagerInfo p = mPagerInfos.get(i);
            if (p != mCurPager) {
                p.pause();
            }
        }
    }

    @Override
    public void onToggleClick() {
        for (int i = 0; i < mPagerInfos.size(); i++) {
            PagerInfo pager = mPagerInfos.get(i);
            if (pager.mListAdapter != null) {
                Log.d(TAG, "onToggleClick: i="+i);
                pager.mListAdapter.pause();
            }
        }
        if (mCurPager != null) {
            mCurPager.resume(mSorted);
        }
    }

    class GMSTask extends AsyncTask<Object, Object, Boolean> {
        private final boolean mEnable;

        public GMSTask(boolean enable) {
            mEnable = enable;
        }

        @Override
        protected Boolean doInBackground(Object... params) {
            // get all apps.
            List<ApplicationInfo> applications = mPM.getInstalledApplications(
                      PackageManager.GET_DISABLED_COMPONENTS
                    | PackageManager.GET_DISABLED_UNTIL_USED_COMPONENTS);
            if (applications == null) {
                applications = new ArrayList<ApplicationInfo>();
            }
            for (int i = 0; i < applications.size(); i++) {
                ApplicationInfo info = applications.get(i);
                String curPkg = info.packageName;
                if (curPkg.contains(ApplicationsState.GOOGLE_APP_PACKAGE)) {
                    if (info.enabled != mEnable) {
                        int newState = PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER;
                        if (mEnable) {
                            newState = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
                        }
                        mPM.setApplicationEnabledSetting(curPkg, newState, PackageManager.DONT_KILL_APP);
                    }
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (mCurPager != null && mCurPager.mListAdapter != null) {
                mCurPager.mListAdapter.updateEntries(mSorted);
            }
            updateOptionsMenu();
        }
    }

    /**
     *   Using this class to build the bridge between ManageApplications and
     * ApplicationsAdapter class which deals with ApplicationsState class for
     * data processing, so we can directly communicate with ListView's Adapter
     * without ApplicationsPagerAdapter.
     *   In addition, with keeping PagerView's views, we can update PagerView's
     * UI here independently. And the benefits go further, we can create three
     * totally different view for three tabs with three instances of this class.
     *
     */
    public static class PagerInfo {
        public final ManageApplications mOwner;
        public final ApplicationsState mApplicationsState;
        public final CharSequence mTabLabel;
        public final int mListType;
        public final int mFilter;

        public LayoutInflater mInflater;
        public ApplicationsAdapter mListAdapter;
        public View mRootView;
        public View mLoadingContainer;
        public View mListContainer;

        public PagerInfo(ManageApplications owner, ApplicationsState apps,
                        CharSequence tabLabel, int listType) {
            mOwner = owner;
            mApplicationsState = apps;
            mTabLabel = tabLabel;
            mListType = listType;
            switch (mListType) {
                case LIST_TYPE_SYSTEM:
                    mFilter = ApplicationsAdapter.FILTER_APPS_SYSTEM;
                    break;
                case LIST_TYPE_DOWNLOAD:
                    mFilter = ApplicationsAdapter.FILTER_APPS_THIRD_PARTY;
                    break;
                case LIST_TYPE_DISABLED:
                    mFilter = ApplicationsAdapter.FILTER_APPS_DISABLED;
                    break;
                default:
                    mFilter = ApplicationsAdapter.FILTER_APPS_THIRD_PARTY;
                    break;
            }
        }

        public View buildPager(LayoutInflater inflater) {
            if (mRootView != null) {
                return mRootView;
            }
            mInflater = inflater;
            mRootView = inflater.inflate(R.layout.manage_applications_apps, null);
            mLoadingContainer = mRootView.findViewById(R.id.loading_container);
            mLoadingContainer.setVisibility(View.VISIBLE);
            mListContainer = mRootView.findViewById(R.id.list_container);
            if (mListContainer != null) {
                // Create adapter and list view here
                View emptyView = mListContainer.findViewById(R.id.empty);
                ListView lv = (ListView) mListContainer.findViewById(R.id.list);
                if (emptyView != null) {
                    lv.setEmptyView(emptyView);
                }
                lv.setItemsCanFocus(true);
                lv.setTextFilterEnabled(true);

                mListAdapter = new ApplicationsAdapter(mApplicationsState, this, mFilter);
                lv.setAdapter(mListAdapter);
                // This listener can be used to free resources associated to the View.
                lv.setRecyclerListener(mListAdapter);
            }

        return mRootView;
        }

        public void resume(boolean sort) {
            if (mListAdapter != null) {
                mListAdapter.resume(sort);
            }
        }

        public void pause() {
            if (mListAdapter != null) {
                mListAdapter.pause();
            }
        }

        public void release() {
            if (mListAdapter != null) {
                mListAdapter.release();
            }
        }
    }
}

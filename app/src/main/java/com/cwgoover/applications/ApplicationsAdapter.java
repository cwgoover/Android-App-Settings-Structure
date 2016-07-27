package com.cwgoover.applications;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.cwgoover.applications.ApplicationsState.AppEntry;
import com.cwgoover.applications.ManageApplications.PagerInfo;

import java.util.ArrayList;
import java.util.Comparator;

public class ApplicationsAdapter extends BaseAdapter implements Filterable,
            ApplicationsState.Callbacks, AbsListView.RecyclerListener, View.OnClickListener {

    public static final String TAG = ManageApplications.TAG + ".adapter";
    public static final boolean DEBUG = ManageApplications.DEBUG;

    // sort order that can be changed through the menu can be sorted alphabetically
    // or size(descending)
    public static final int MENU_OPTIONS_BASE = 0;
    public static final int FILTER_APPS_SYSTEM = MENU_OPTIONS_BASE;
    public static final int FILTER_APPS_THIRD_PARTY = MENU_OPTIONS_BASE + 1;
    public static final int FILTER_APPS_DISABLED = MENU_OPTIONS_BASE + 2;

    private final int mFilterMode;
    private final ApplicationsState mState;
    private final ApplicationsState.Session mSession;
    private final PagerInfo mPagerInfo;
    private final Context mContext;
    private final LayoutInflater mInflater;
    private final ToggleClickListener mListener;
    private final PackageManager mPm;
    private final ArrayList<View> mActive = new ArrayList<View>();
    private ArrayList<ApplicationsState.AppEntry> mBaseEntries;
    private ArrayList<ApplicationsState.AppEntry> mEntries;
    private boolean mResumed;

    CharSequence mCurFilterPrefix;

    interface ToggleClickListener {
        void onToggleClick();
    }

    private final Filter mFilter = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            ArrayList<ApplicationsState.AppEntry> entries
                    = applyPrefixFilter(constraint, mBaseEntries);
            FilterResults fr = new FilterResults();
            fr.values = entries;
            fr.count = entries.size();
            return fr;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            mCurFilterPrefix = constraint;
            mEntries = (ArrayList<ApplicationsState.AppEntry>)results.values;
            notifyDataSetChanged();
        }
    };

    public ApplicationsAdapter(ApplicationsState state, PagerInfo pager, int filterMode) {
        mPagerInfo = pager;
        mInflater = pager.mInflater;
        mContext = pager.mOwner;
        mListener = pager.mOwner;
        mPm = mContext.getPackageManager();
        mState = state;
        mSession = state.newSession(this);
        mFilterMode = filterMode;
    }

    @Override
    public int getCount() {
        return mEntries != null ? mEntries.size() : 0;
    }

    @Override
    public Object getItem(int position) {
        return mEntries.get(position);
    }

    @Override
    public long getItemId(int position) {
        return mEntries.get(position).id;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // A ViewHolder keeps references to children views to avoid unnecessary calls
        // to findViewById() on each row.
        AppViewHolder holder = AppViewHolder.createOrRecycle(this, mInflater, convertView);
        convertView = holder.rootView;

        // Bind the data efficiently with the holder
        ApplicationsState.AppEntry entry = mEntries.get(position);

        synchronized (entry) {
//            holder.entry = entry;
            if (entry.label != null) {
                holder.appName.setText(entry.label);
            }
            if (entry.packageName != null) {
                holder.appPkg.setText(entry.packageName);
            }
             mState.ensureIcon(entry);
            if (entry.icon != null) {
                holder.appIcon.setImageDrawable(entry.icon);
            }
            holder.toggle.setChecked(entry.info.enabled);
            holder.toggle.setTag(R.id.checkbox, position);
        }
        // TODO: to check?
        mActive.remove(convertView);
        mActive.add(convertView);
        return convertView;
    }

    @Override
    public void onClick(View v) {
        if (v instanceof ToggleButton) {
            final String curPkgName = mEntries.get((Integer)v.getTag(R.id.checkbox)).packageName;
            final Handler handler = new Handler(mContext.getMainLooper());
            (new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    int newState = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
                    if (mPm.getApplicationEnabledSetting(curPkgName)
                                != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER) {
                        newState = PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER;
                    }
                    try {
                        mPm.setApplicationEnabledSetting(curPkgName, newState, PackageManager.DONT_KILL_APP);
                    } catch (SecurityException e) {
                        Log.e(TAG, "Error: set state to system fail! [" + e.toString() + "]");
                    }
                    handler.post(new Runnable() {
                        @Override public void run() {
                            mListener.onToggleClick();
                        }});
                    return null;
                }
            }).execute();
        }
    }

    @Override
    public Filter getFilter() {
        return mFilter;
    }

    /**
     * RecyclerListener's method:
     * Indicates that the specified View was moved into the recycler's scrap heap.
     */
    @Override
    public void onMovedToScrapHeap(View view) {
        mActive.remove(view);
    }

    @Override
    public void onRebuildComplete(ArrayList<AppEntry> apps) {
        if (mPagerInfo.mLoadingContainer.getVisibility() == View.VISIBLE) {
            mPagerInfo.mLoadingContainer.startAnimation(AnimationUtils.loadAnimation(
                    mContext, android.R.anim.fade_out));
            mPagerInfo.mListContainer.startAnimation(AnimationUtils.loadAnimation(
                    mContext, android.R.anim.fade_in));
        }
        mPagerInfo.mListContainer.setVisibility(View.VISIBLE);
        mPagerInfo.mLoadingContainer.setVisibility(View.GONE);
        mBaseEntries = apps;
        mEntries = applyPrefixFilter(mCurFilterPrefix, mBaseEntries);
        notifyDataSetChanged();
    }

    @Override
    public void onRunningStateChanged(boolean running) {
        ((ManageApplications)mContext).setProgressBarIndeterminateVisibility(running);
    }

    public void updateEntries(boolean sort) {
        if (DEBUG) Log.i(TAG, "updateEntries: sort=" + sort);
        mSession.updateEntries();
        rebuild(sort, true);
    }

    public void resume(boolean sort) {
        if (DEBUG) Log.i(TAG, "Resume!  mResumed=" + mResumed);
        if (!mResumed) {
            mResumed = true;
            mSession.resume();
            rebuild(sort, true);
        } else {
            rebuild(sort, true);
        }
    }

    public void pause() {
        if (mResumed) {
            mResumed = false;
            mSession.pause();
        }
    }

    public void release() {
        mSession.release();
    }

    public void rebuild(boolean sort, boolean eraseold) {
        if (DEBUG) Log.i(TAG, "Rebuilding app list...");
        ApplicationsState.AppFilter filterObj;
        Comparator<AppEntry> comparatorObj = null;
        switch (mFilterMode) {
            case FILTER_APPS_SYSTEM:
                filterObj = ApplicationsState.SYSTEM_FILTER;
                break;
            case FILTER_APPS_THIRD_PARTY:
                filterObj = ApplicationsState.THIRD_PARTY_FILTER;
                break;
            case FILTER_APPS_DISABLED:
                filterObj = ApplicationsState.DISABLED_FILTER;
                break;
            default:
                filterObj = ApplicationsState.THIRD_PARTY_FILTER;
                break;
        }
        if (sort) {
            comparatorObj = ApplicationsState.ALPHA_COMPARATOR;
        }
        // rebuild list in background thread
        ArrayList<ApplicationsState.AppEntry> entries
                = mSession.rebuild(filterObj, comparatorObj);
        if (entries == null && !eraseold) {
            // Don't have new list yet, but can continue using the old one.
            return;
        }
        mBaseEntries = entries;
        if (mBaseEntries != null) {
            mEntries = applyPrefixFilter(mCurFilterPrefix, mBaseEntries);
        } else {
            mEntries = null;
        }
        notifyDataSetChanged();

        if (entries == null) {
            mPagerInfo.mListContainer.setVisibility(View.INVISIBLE);
            mPagerInfo.mLoadingContainer.setVisibility(View.VISIBLE);
        } else {
            mPagerInfo.mListContainer.setVisibility(View.VISIBLE);
            mPagerInfo.mLoadingContainer.setVisibility(View.GONE);
        }
    }

    ArrayList<ApplicationsState.AppEntry> applyPrefixFilter(CharSequence prefix,
                ArrayList<ApplicationsState.AppEntry> origEntries) {
        if (prefix == null || prefix.length() == 0) {
            return origEntries;
        } else {
            String prefixStr = ApplicationsState.normalize(prefix.toString());
            ArrayList<ApplicationsState.AppEntry> newEntries
                    = new ArrayList<ApplicationsState.AppEntry>();
            for (int i = 0; i < origEntries.size(); i++) {
                ApplicationsState.AppEntry entry = origEntries.get(i);
                String nlabel = entry.getNormalizedLabel();
                if (nlabel.startsWith(prefixStr) /*|| nlabel.indexOf(prefixStr) != -1*/
                        || entry.packageName.startsWith(prefixStr)
                        || entry.packageName.contains(prefixStr)) {
                    newEntries.add(entry);
                }
            }
            return newEntries;
        }
    }

    static class AppViewHolder {
//        public ApplicationsState.AppEntry entry;
        public View rootView;
        public TextView appName;
        public TextView appPkg;
        public ImageView appIcon;
        public ToggleButton toggle;

        static public AppViewHolder createOrRecycle(ApplicationsAdapter adapter,
                            LayoutInflater inflater, View convertView) {
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.manage_applications_item, null);
                AppViewHolder holder = new AppViewHolder();
                holder.rootView = convertView;
                holder.appName = (TextView) convertView.findViewById(R.id.app_name);
                holder.appPkg = (TextView) convertView.findViewById(R.id.app_pkg);
                holder.appIcon = (ImageView) convertView.findViewById(R.id.app_icon);
                holder.toggle = (ToggleButton) convertView.findViewById(R.id.app_toggle);
                holder.toggle.setOnClickListener(adapter);
                convertView.setTag(holder);
                return holder;
            } else {
                return (AppViewHolder) convertView.getTag();
            }
        }
    }
}

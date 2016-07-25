package com.cwgoover.applications;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import java.io.File;
import java.text.Collator;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

public class ApplicationsState {

    public static final String TAG = ManageApplications.TAG + ".state";
    public static final boolean DEBUG = ManageApplications.DEBUG;
    public static final boolean DEBUG_LOCKING = true;
    public static final String GOOGLE_APP_PACKAGE = "com.google.android";

    public interface Callbacks {
        void onRebuildComplete(ArrayList<AppEntry> apps);
        void onRunningStateChanged(boolean running);
    }

    public interface AppFilter {
        void init();
        boolean filterApp(ApplicationInfo info);
    }

    static final Pattern REMOVE_DIACRITICALS_PATTERN
                = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    public static String normalize(String str) {
        String tmp = Normalizer.normalize(str, Form.NFD);
        Log.d(TAG, "tcao: normalize--> tmp=" + tmp);
        return REMOVE_DIACRITICALS_PATTERN.matcher(tmp)
                    .replaceAll("").toLowerCase();
    }

    public static class AppEntry {
        final File apkFile;
        final long id;
        String label;
        String packageName;
        // TODO: check whether necessary?
        String normalizedLabel;
        Drawable icon;
        // Need to synchronize on 'this' for the following.
        ApplicationInfo info;
        boolean mounted;

        String getNormalizedLabel() {
            if (normalizedLabel != null) {
                return normalizedLabel;
            }
            normalizedLabel = normalize(label);
            return normalizedLabel;
        }

        public AppEntry(Context context, ApplicationInfo info, long id) {
            apkFile = new File(info.sourceDir);
            this.id = id;
            this.info = info;
            this.packageName = info.packageName;

            ensureLabel(context);
        }

        void ensureLabel(Context context) {
            if (this.label == null || !this.mounted) {
                if (!this.apkFile.exists()) {
                    this.mounted = false;
                    this.label = info.packageName;
                } else {
                    this.mounted = true;
                    CharSequence label = info.loadLabel(context.getPackageManager());
                    this.label = label != null ? label.toString() : info.packageName;
                }
            }
        }

        boolean ensureIconLocked(Context context, PackageManager pm) {
            if (this.icon == null) {
                if (this.apkFile.exists()) {
                    this.icon = info.loadIcon(pm);
                    this.mounted = true;    // add by mine.
                    return true;
                } else {
                    this.mounted = false;
                    this.icon = context.getResources().getDrawable(
                                R.mipmap.sym_app_on_sd_unavailable_icon);
                }
            } else if (!this.mounted) {
                // If the app wasn't mounted but is now mounted, reload
                // its icon.
                if (this.apkFile.exists()) {
                    this.mounted = true;
                    this.icon = info.loadIcon(pm);
                    return true;
                }
            }
            return false;
        }
    }

    public static final Comparator<AppEntry> ALPHA_COMPARATOR = new Comparator<AppEntry>() {
        private final Collator sCollator = Collator.getInstance();

        @Override
        public int compare(AppEntry object1, AppEntry object2) {
            final boolean normal1 = object1.info.enabled
                    && (object1.info.flags & ApplicationInfo.FLAG_INSTALLED) != 0;
            final boolean normal2 = object2.info.enabled
                    && (object2.info.flags & ApplicationInfo.FLAG_INSTALLED) != 0;
            if (normal1 != normal2) {
                return normal1 ? -1 : 1;
            }
            return sCollator.compare(object1.label, object2.label);
        }
    };

    public static final AppFilter SYSTEM_FILTER = new AppFilter() {
        @Override
        public void init() {}

        @Override
        public boolean filterApp(ApplicationInfo info) {
            if ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                return true;
            }
            return false;
        }
    };

    public static final AppFilter THIRD_PARTY_FILTER = new AppFilter() {
        @Override
        public void init() {}

        @Override
        public boolean filterApp(ApplicationInfo info) {
            if ((info.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
                return true;
            }
            else if ((info.flags & ApplicationInfo.FLAG_SYSTEM) == 0
                    && (info.flags & ApplicationInfo.FLAG_EXTERNAL_STORAGE) == 0) {
                return true;
            }
            return false;
        }
    };

    public static final AppFilter DISABLED_FILTER = new AppFilter() {
        @Override
        public void init() {}

        @Override
        public boolean filterApp(ApplicationInfo info) {
            if (!info.enabled && !info.packageName.contains(GOOGLE_APP_PACKAGE)) {
                return true;
            }
            return false;
        }
    };

    /**
     * Here is the where actually do the work.
     */
    public class Session {
        final Callbacks mCallbacks;
        boolean mResumed;

        // Rebuilding of app list. Synchronized on mRebuildSync.
        final Object mRebuildSync = new Object();
        boolean mRebuildRequested;      // TODO: check what's mean?
        boolean mRebuildAsync;
        AppFilter mRebuildFilter;
        Comparator<AppEntry> mRebuildComparator;
        ArrayList<AppEntry> mRebuildResult;
        ArrayList<AppEntry> mLastAppList;

        Session(Callbacks callbacks) {
            mCallbacks =callbacks;
        }

        public void updateEntries() {
            synchronized (mEntriesMap) {
                // Note: update mApplications' all the ApplicationInfo, includes
                // app's label, packageName, enable etc messages, to the newest.
                mApplications = mPm.getInstalledApplications(mRetrieveFlags);
                if (mApplications == null) {
                    mApplications = new ArrayList<ApplicationInfo>();
                }
                if (!mBackgroundHandler.hasMessages(BackgroundHandler.MSG_LOAD_ENTRIES)) {
                    mBackgroundHandler.sendEmptyMessage(BackgroundHandler.MSG_LOAD_ENTRIES);
                }
            }
        }

        public void resume() {
            if (DEBUG_LOCKING) Log.v(TAG, "resume about to acquire locking...");
            synchronized (mEntriesMap) {
                if (!mResumed) {
                    mResumed = true;
                    mSessionsChanged = true;
                    Log.d(TAG, "tcao: resume: mResumed=" +mResumed);
                    doResumeIfNeededLocked();
                }
            }
            if (DEBUG_LOCKING) Log.v(TAG, "...resume releasing lock");
        }

        public void pause() {
            if (DEBUG_LOCKING) Log.v(TAG, "pause about to acquire lock...");
            synchronized (mEntriesMap) {
                if (mResumed) {
                    mResumed = false;
                    mSessionsChanged = true;
                    mBackgroundHandler.removeMessages(BackgroundHandler.MSG_REBUILD_LIST, this);
                    Log.d(TAG, "tcao: pause  mResumed="+mResumed);
                    doPauseIfNeededLocked();
                }
            }
            if (DEBUG_LOCKING) Log.v(TAG, "...pause releasing lock");
        }

        public void release() {
            pause();
            synchronized (mEntriesMap) {
                mSessions.remove(this);
            }
        }

        // Creates a new list of app entries with the given filter and comparator.
        ArrayList<AppEntry> rebuild(AppFilter filter, Comparator<AppEntry> comparator) {
            synchronized (mRebuildSync) {
                synchronized (mEntriesMap) {
                    mRebuildingSessions.add(this);
                    mRebuildRequested = true;
                    mRebuildAsync = false;
                    mRebuildFilter = filter;
                    mRebuildComparator = comparator;
                    mRebuildResult = null;
                    if (!mBackgroundHandler.hasMessages(BackgroundHandler.MSG_REBUILD_LIST)) {
                        Message msg = mBackgroundHandler.obtainMessage(
                                BackgroundHandler.MSG_REBUILD_LIST);
                        mBackgroundHandler.sendMessage(msg);
                    }
                }

                // we will wait for .25s for the list to be built.
                long waitend = SystemClock.uptimeMillis() + 250;

                while (mRebuildResult == null) {
                    long now = SystemClock.uptimeMillis();
                    if (now >= waitend) {
                        break;
                    }
                    try {
                        mRebuildSync.wait(waitend - now);
                    } catch (InterruptedException e){
                    }
                }
                mRebuildAsync = true;
                return mRebuildResult;
            }
        }

        // Run in background thread to rebuild list
        void handleRebuildList() {
            AppFilter filter;
            Comparator<AppEntry> comparator;
            synchronized (mRebuildSync) {
                if (!mRebuildRequested) {
                    return;
                }
                filter = mRebuildFilter;
                comparator = mRebuildComparator;
                mRebuildRequested = false;
                mRebuildFilter = null;
                mRebuildComparator = null;
            }

            Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND);

            if (filter != null) {
                filter.init();
            }

            List<ApplicationInfo> apps;
            synchronized (mEntriesMap) {
                apps = new ArrayList<ApplicationInfo>(mApplications);
            }

            ArrayList<AppEntry> filteredApps = new ArrayList<AppEntry>();
            if (DEBUG) Log.i(TAG, "Rebuilding...");
            for (int i = 0; i < apps.size(); i++) {
                ApplicationInfo info = apps.get(i);

                // get all the filtered list for different tab
                if ((filter == null || filter.filterApp(info))
                        && !"com.cwgoover.applications".equals(info.packageName)/*filter myself out*/) {
                    synchronized (mEntriesMap) {
                        if (DEBUG_LOCKING) Log.v(TAG, "rebuild acquired lock");
                        AppEntry entry = getEntryLocked(info);
                        entry.ensureLabel(mContext);
                        if (DEBUG) Log.v(TAG, "Using " + info.packageName + ": " + entry);
                        filteredApps.add(entry);
                        if (DEBUG_LOCKING) Log.v(TAG, "rebuild releasing lock");
                    }
                }
            }

            // sort list
            if (comparator != null) {
                try {
                    Collections.sort(filteredApps, comparator);
                } catch (IllegalArgumentException ex) {
                    Log.e(TAG, "handleRebuildList sort error: " + ex.getMessage());
                }
            }

            synchronized (mRebuildSync) {
                if (!mRebuildRequested) {
                    mLastAppList = filteredApps;
                    if (!mRebuildAsync) {
                        mRebuildResult = filteredApps;
                        mRebuildSync.notifyAll();
                    } else {
                        if (!mMainHandler.hasMessages(MainHandler.MSG_REBUILD_COMPLETE, this)) {
                            Message msg = mMainHandler.obtainMessage(
                                    MainHandler.MSG_REBUILD_COMPLETE, this);
                            mMainHandler.sendMessage(msg);
                        }
                    }
                }
            }

            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
        }
    }

    void rebuildActiveSessions() {
        synchronized (mEntriesMap) {
            if (!mSessionsChanged) {
                return;
            }
            mActiveSessions.clear();
            for (int i = 0; i < mSessions.size(); i++) {
                Session s = mSessions.get(i);
                if (s.mResumed) {
                    mActiveSessions.add(s);
                }
            }
        }
    }

    final MainHandler mMainHandler = new MainHandler();
    class MainHandler extends Handler {
        static final int MSG_REBUILD_COMPLETE = 1;
        static final int MSG_RUNNING_STATE_CHANGED = 2;

        @Override
        public void handleMessage(Message msg) {
            rebuildActiveSessions();
            switch (msg.what) {
                case MSG_REBUILD_COMPLETE:
                    Session s = (Session)msg.obj;
                    if (mSessions.contains(s)) {
                        s.mCallbacks.onRebuildComplete(s.mLastAppList);
                    }
                    break;
                case MSG_RUNNING_STATE_CHANGED:
                    for (int i = 0; i < mActiveSessions.size(); i++) {
                        mActiveSessions.get(i).mCallbacks.onRunningStateChanged(msg.arg1 != 0);
                    }
                    break;
            }
        }
    }

    final HandlerThread mThread;
    final BackgroundHandler mBackgroundHandler;
    class BackgroundHandler extends Handler {
        static final int MSG_REBUILD_LIST = 1;
        static final int MSG_LOAD_ENTRIES = 2;
        static final int MSG_LOAD_ICONS = 3;

        boolean mRunning;

        BackgroundHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_REBUILD_LIST: {
                    ArrayList<Session> rebuildingSessions = null;
                    synchronized (mEntriesMap) {
                        if (mRebuildingSessions.size() > 0) {
                            rebuildingSessions = new ArrayList<Session>(mRebuildingSessions);
                            mRebuildingSessions.clear();
                        }
                    }
                    if (rebuildingSessions != null) {
                        for (int i = 0; i < rebuildingSessions.size(); i++) {
                            rebuildingSessions.get(i).handleRebuildList();
                        }
                    }
                } break;
                case MSG_LOAD_ENTRIES: {
                    int numDone = 0;
                    synchronized (mEntriesMap) {
                        if (DEBUG_LOCKING) Log.v(TAG, "MSG_LOAD_ENTRIES acquired lock");
                        for (int i = 0; i < mApplications.size() && numDone < 6; i++) {
                            if (!mRunning) {
                                mRunning = true;
                                // update the process bar's state
                                Message m = mMainHandler.obtainMessage(
                                        MainHandler.MSG_RUNNING_STATE_CHANGED, 1);
                                mMainHandler.sendMessage(m);
                            }
                            ApplicationInfo info = mApplications.get(i);
                            if (mEntriesMap.get(info.packageName) == null) {
                                // The end of the loop decided by {numDone}, and just new ApplicationInfo
                                // in mEntriesMap can cause numDone's value growing.
                                numDone++;
                                getEntryLocked(info);
                            }
                        }
                        if (DEBUG_LOCKING) Log.v(TAG, "MSG_LOAD_ENTRIES releasing lock");
                    }

                    if (numDone >= 6) {
                        sendEmptyMessage(MSG_LOAD_ENTRIES);
                    } else {
                        sendEmptyMessage(MSG_LOAD_ICONS);
                    }
                } break;
                case MSG_LOAD_ICONS: {
                    int numDone = 0;
                    synchronized (mEntriesMap) {
                        if (DEBUG_LOCKING) Log.v(TAG, "MSG_LOAD_ICONS acquired lock");
                        for (int i = 0; i < mAppEntries.size() && numDone < 2; i++) {
                            AppEntry entry = mAppEntries.get(i);
                            if (entry.icon == null || !entry.mounted) {
                                synchronized (entry) {
                                    if (entry.ensureIconLocked(mContext, mPm)) {
                                        if (!mRunning) {
                                            mRunning = true;
                                            Message m = mMainHandler.obtainMessage(
                                                    MainHandler.MSG_RUNNING_STATE_CHANGED, 1);
                                            mMainHandler.sendMessage(m);
                                        }
                                        numDone++;
                                    }
                                }
                            }
                        }
                        if (numDone >= 2) {
                            sendEmptyMessage(MSG_LOAD_ICONS);
                        } else {
                            mRunning = false;
                            Message m = mMainHandler.obtainMessage(
                                    MainHandler.MSG_RUNNING_STATE_CHANGED, 0);
                            mMainHandler.sendMessage(m);
                        }
                        if (DEBUG_LOCKING) Log.v(TAG, "MSG_LOAD_ICONS releasing lock");
                    }
                } break;
            }
        }
    }

    // --------------------------------------------------------------

    final Context mContext;
    final PackageManager mPm;
    final int mRetrieveFlags;

    boolean mResumed;
    boolean mSessionsChanged;
    // The number of the AppEntries in mEntriesMap &  mAppEntries
    long mCurId = 1;

    // Information about all the applications. Synchronize on mEntriesMap
    // to protect access to these.
    final ArrayList<Session> mSessions = new ArrayList<Session>();
    final ArrayList<Session> mRebuildingSessions = new ArrayList<Session>();
    // The same as below
    final HashMap<String, AppEntry> mEntriesMap = new HashMap<String, AppEntry>();
    // Only one object in ManageApplications(means all over the project)
    // cause we only need one list which saves all the AppEntry of the handset.
    final ArrayList<AppEntry> mAppEntries = new ArrayList<AppEntry>();
    // The same as above
    List<ApplicationInfo> mApplications = new ArrayList<ApplicationInfo>();

    // Update all the active session's progress bar. Only touched by main thread.
    final ArrayList<Session> mActiveSessions = new ArrayList<Session>();

    static final Object sLock = new Object();
    static ApplicationsState sInstance;

    static ApplicationsState getInstance(Application app) {
        synchronized (sLock) {
            if (sInstance == null) {
                sInstance = new ApplicationsState(app);
            }
            return sInstance;
        }
    }

    private ApplicationsState(Application app) {
        mContext = app;
        mPm = mContext.getPackageManager();
        mThread = new HandlerThread("perfTrack.loadApps",
                Process.THREAD_PRIORITY_BACKGROUND);
        mThread.start();
        mBackgroundHandler = new BackgroundHandler(mThread.getLooper());

        // can see all apps.
        mRetrieveFlags = PackageManager.GET_DISABLED_COMPONENTS |
                PackageManager.GET_DISABLED_UNTIL_USED_COMPONENTS;

        /**
         * This is a trick to prevent the foreground thread from being delayed.
         * The problem is that Dalvik monitors are initially spin locks, to keep
         * them lightweight.  This leads to unfair contention -- Even though the
         * background thread only holds the lock for a short amount of time, if
         * it keeps running and locking again it can prevent the main thread from
         * acquiring its lock for a long time...  sometimes even > 5 seconds
         * (leading to an ANR).
         *
         * Dalvik will promote a monitor to a "real" lock if it detects enough
         * contention on it.  It doesn't figure this out fast enough for us
         * here, though, so this little trick will force it to turn into a real
         * lock immediately.
         */
        synchronized (mEntriesMap) {
            try {
                mEntriesMap.wait(1);
            } catch (InterruptedException e) {
            }
        }
    }

    public Session newSession(Callbacks callbacks) {
        Session s = new Session(callbacks);
        synchronized (mEntriesMap) {
            mSessions.add(s);
        }
        return s;
    }

    void doResumeIfNeededLocked() {
        if (mResumed) {
            return;
        }
        mResumed = true;
        Log.d(TAG, "doResumeIfNeededLocked: mResumed="+mResumed);
        mApplications = mPm.getInstalledApplications(mRetrieveFlags);
        if (mApplications == null) {
            mApplications = new ArrayList<ApplicationInfo>();
        }
        if (!mBackgroundHandler.hasMessages(BackgroundHandler.MSG_LOAD_ENTRIES)) {
            mBackgroundHandler.sendEmptyMessage(BackgroundHandler.MSG_LOAD_ENTRIES);
        }
    }

    void doPauseIfNeededLocked() {
        Log.d(TAG, "tcao: doPauseIfNeededLocked: mResumed=" +mResumed);
        if (!mResumed) {
            return;
        }
        Log.d(TAG, "tcao: doPauseIfNeededLocked: never do:"+ this.toString());
        for (int i = 0; i < mSessions.size(); i++) {
            if (mSessions.get(i).mResumed) {
                return;
            }
        }
        Log.d(TAG, "doPauseIfNeededLocked: set mResumed false");
        mResumed = false;
    }

    AppEntry getEntryLocked(ApplicationInfo info) {
        AppEntry entry = mEntriesMap.get(info.packageName);
        if (DEBUG) Log.v(TAG, "Looking up entry of pkg " + info.packageName + ": " + entry);
        if (entry == null) {
            if (DEBUG) Log.v(TAG, "Creating AppEntry for " + info.packageName);
            entry = new AppEntry(mContext, info, mCurId++);
            mEntriesMap.put(info.packageName, entry);
            mAppEntries.add(entry);
        }
        else if (entry.info != info) {
            entry.info = info;
        }
        return entry;
    }

    void ensureIcon(AppEntry entry) {
        if (entry.icon != null) {
            return;
        }
        synchronized (entry) {
            entry.ensureIconLocked(mContext, mPm);
        }
    }
}

package com.cwgoover.applications;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import java.util.ArrayList;

/**
 * All the user interaction is managed by this View:
 *   * user touches ViewPagerTabButton (setOnClickListener)
 *   * user swipes ViewPager (OnPageChangeListener)
 */
public class FixedTabsView extends LinearLayout implements ViewPager.OnPageChangeListener {

    private final Context mContext;
    private ViewPager mViewPager;
    private TabsAdapter mAdapter;

    private final ArrayList<View> mTabs = new ArrayList<View>();
    private final Drawable mDividerDrawable;

    private int mDividerColor;
    private int mDividerMarginTop = 12;
    private int mDividerMarginBottom = 21;

    int mCurPos = 0;

    public FixedTabsView(Context context) {
        this(context, null);
    }

    public FixedTabsView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FixedTabsView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs);
        mContext = context;
        mDividerColor = context.getResources().getColor(R.color.app_manage_tab_div_color);

        final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ViewPagerExtensions);
        mDividerColor = a.getColor(R.styleable.ViewPagerExtensions_dividerColor, mDividerColor);
        mDividerMarginTop = a.getDimensionPixelSize(R.styleable.ViewPagerExtensions_dividerMarginTop,
                                    mDividerMarginTop);
        mDividerMarginBottom = a.getDimensionPixelSize(R.styleable.ViewPagerExtensions_dividerMarginBottom,
                                    mDividerMarginBottom);
        mDividerDrawable = a.getDrawable(R.styleable.ViewPagerExtensions_dividerDrawable);
        a.recycle();

        this.setOrientation(LinearLayout.HORIZONTAL);
    }

    /**
     * Sets the data behind this FixedTabsView.
     *
     * @param adapter
     *          The {@link TabsAdapter} which is responsible for maintaining the
     *          data backing this FixedTabsView and for producing a view to
     *          represent an item in that data set.
     */
    public void setAdapter(TabsAdapter adapter) {
        mAdapter = adapter;

        if (mViewPager != null && mAdapter != null) initTabs();
    }

    /**
     * Binds the {@link ViewPager} to this View
     *
     */
    public void setViewPager(ViewPager pager) {
        mViewPager = pager;
        mViewPager.setOnPageChangeListener(this);

        if (mViewPager != null && mAdapter != null) initTabs();
    }

    /* it will be invoked if the ViewPager is scrolling */
    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}

    /* it will be invoked after onPageSelected method when the ViewPager is fully stopped/idle */
    @Override
    public void onPageScrollStateChanged(int state) {
        /**
         * SCROLL_STATE_IDLE(0): The ViewPager is fully stopped in all the situation.
         * SCROLL_STATE_DRAGGING(1): The pager is currently being dragged by the user.
         * SCROLL_STATE_SETTLING(2): Seem like SCROLL_STATE_IDLE, except that pager doesn't change
         *                           after scrolling, such as swiping left at the left-most position of the pager.
         */
        if (state == ViewPager.SCROLL_STATE_IDLE) {
            ((ManageApplications) mAdapter.getActivity()).updateCurrentTab(mCurPos);
        }
    }

    /* it will be invoked when the ViewPager is fully stopped/idle */
    @Override
    public void onPageSelected(int position) {
        mCurPos = position;
        selectTab(position);
    }

    /**
     * Initialize and add all tabs to the layout
     */
    private void initTabs() {
        removeAllViews();
        mTabs.clear();

        if (mAdapter == null) return;

        for (int i = 0; i < mViewPager.getAdapter().getCount(); i++) {
            final int index = i;
            View tab = mAdapter.getTabView(i);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f);
            tab.setLayoutParams(params);
            addView(tab);
            mTabs.add(tab);

            if (i != mViewPager.getAdapter().getCount() - 1) {
                addView(getSeparator());
            }

            tab.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    // invoked ViewPager's onPageSelected method and then onPageScrollStateChanged method
                    mViewPager.setCurrentItem(index);
                }
            });
        }

        selectTab(mViewPager.getCurrentItem());
    }

    /**
     * Creates and returns a new Separator View
     *
     * @return
     */
    private View getSeparator() {
        View v = new View(mContext);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(1, LayoutParams.MATCH_PARENT);
        params.setMargins(0, mDividerMarginTop, 0, mDividerMarginBottom);
        v.setLayoutParams(params);

        if (mDividerDrawable != null) {
            v.setBackground(mDividerDrawable);
        } else {
            v.setBackgroundColor(mDividerColor);
        }
        return v;
    }

    /**
     * Runs through all tabs and sets if they are currently selected.
     *
     * @param position
     *          The position of the currently selected tab.
     */
    private void selectTab(int position) {
        for (int i = 0, pos = 0; i < getChildCount(); i++) {
            if (getChildAt(i) instanceof ViewPagerTabButton) {
                getChildAt(i).setSelected(pos == position);
                pos++;
            }
        }
    }
}

package com.cwgoover.applications;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.Button;

public class ViewPagerTabButton extends Button {

    private final int mTopLineColor;
    private int mLineColor;
    private int mLineColorSelected;

    private int mTopLineHeight = 1;
    private int mLineHeight = 2;
    private int mLineHeightSelected = 6;

    private final Paint mLinePaint = new Paint();

    public ViewPagerTabButton(Context  context) {
        this(context, null);
    }

    public ViewPagerTabButton(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ViewPagerTabButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mTopLineColor = context.getResources().getColor(R.color.app_manage_tab_div_color);
        mLineColor = context.getResources().getColor(R.color.app_manage_tab_line_color);
        mLineColorSelected = context.getResources().getColor(R.color.app_manage_tab_line_color);

        // calculate default pixels from dp. "dp -> px"
        mTopLineHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                                mTopLineHeight, context.getResources().getDisplayMetrics());
        mLineHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                                mLineHeight, context.getResources().getDisplayMetrics());
        mLineHeightSelected = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                            mLineHeightSelected, context.getResources().getDisplayMetrics());

        // retrieve resources from xml
        final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ViewPagerExtensions, defStyle, 0);
        mLineColor = a.getColor(R.styleable.ViewPagerExtensions_lineColor, mLineColor);
        mLineColorSelected = a.getColor(R.styleable.ViewPagerExtensions_lineColorSelected, mLineColorSelected);

        mLineHeight = a.getDimensionPixelSize(R.styleable.ViewPagerExtensions_lineHeight, mLineHeight);
        mLineHeightSelected = a.getDimensionPixelSize(R.styleable.ViewPagerExtensions_lineHeightSelected,
                                    mLineHeightSelected);

        a.recycle();
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        final Paint linePaint = mLinePaint;
        linePaint.setColor(isSelected()? mLineColorSelected: mLineColor);

        final int height = isSelected()? mLineHeightSelected : mLineHeight;
        // draw the line
        canvas.drawRect(0, getMeasuredHeight() - height, getMeasuredWidth(), getMeasuredHeight(), linePaint);

        // draw top line
        linePaint.setColor(mTopLineColor);
        canvas.drawRect(0, 0, getMeasuredWidth(), mTopLineHeight, linePaint);
    }

    /*
     *  retrieve resources from interface
     */
    public void setLineColor(int color) {
        this.mLineColor = color;
        invalidate();
    }

    public int getLineColor() {
        return this.mLineColor;
    }

    public void setLineColorSelected(int color) {
        this.mLineColorSelected = color;
        invalidate();
    }

    public int getLineColorSelected() {
        return this.mLineColorSelected;
    }

    public void setLineHeight(int height) {
        this.mLineHeight = height;
        invalidate();
    }

    @Override
    public int getLineHeight() {
        return this.mLineHeight;
    }

    public void setLineHeightSelected(int height) {
        this.mLineHeightSelected = height;
        invalidate();
    }

    public int getLineHeightSelected() {
        return this.mLineHeightSelected;
    }
}

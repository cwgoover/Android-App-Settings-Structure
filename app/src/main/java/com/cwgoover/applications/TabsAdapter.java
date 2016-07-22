package com.cwgoover.applications;

import android.app.Activity;
import android.view.View;

public interface TabsAdapter {
    View getTabView(int position);
    Activity getActivity();
}

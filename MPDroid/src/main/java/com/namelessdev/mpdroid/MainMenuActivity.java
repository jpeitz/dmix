/*
 * Copyright (C) 2010-2014 The MPDroid Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.namelessdev.mpdroid;

import com.namelessdev.mpdroid.MPDroidActivities.MPDroidFragmentActivity;
import com.namelessdev.mpdroid.fragments.BrowseFragment;
import com.namelessdev.mpdroid.fragments.LibraryFragment;
import com.namelessdev.mpdroid.fragments.OutputsFragment;
import com.namelessdev.mpdroid.fragments.QueueFragment;
import com.namelessdev.mpdroid.helpers.MPDConnectionHandler;
import com.namelessdev.mpdroid.helpers.MPDControl;
import com.namelessdev.mpdroid.library.ILibraryFragmentActivity;
import com.namelessdev.mpdroid.library.ILibraryTabActivity;
import com.namelessdev.mpdroid.tools.LibraryTabsUtil;
import com.namelessdev.mpdroid.tools.Tools;

import android.app.ActionBar;
import android.app.ActionBar.OnNavigationListener;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentManager.OnBackStackChangedListener;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;

import java.util.Collections;
import java.util.List;

public class MainMenuActivity extends MPDroidFragmentActivity implements OnNavigationListener,
        ILibraryFragmentActivity, ILibraryTabActivity, OnBackStackChangedListener,
        PopupMenu.OnMenuItemClickListener {

    private static final boolean DEBUG = false;

    private static final String EXTRA_DISPLAY_MODE = "displaymode";

    private static final String FRAGMENT_TAG_LIBRARY = "library";

    private static final String FRAGMENT_TAG_OUTPUTS = "outputs";

    private static final int SETTINGS = 5;

    private static final List<String> TAB_LIST;

    private static final String TAG = "MainMenuActivity";

    private int mBackPressExitCount;

    private DisplayMode mCurrentDisplayMode;

    private DrawerLayout mDrawerLayout;

    private ListView mDrawerList;

    private ActionBarDrawerToggle mDrawerToggle;

    private Handler mExitCounterReset = new Handler();

    private FragmentManager mFragmentManager;

    private LibraryFragment mLibraryFragment;

    private View mLibraryRootFrame;

    private int mOldDrawerPosition = 0;

    private OutputsFragment mOutputsFragment;

    private View mOutputsRootFrame;

    private QueueFragment mQueueFragment;

    private TextView mTextView;

    static {
        // Get the list of the currently visible tabs
        TAB_LIST = LibraryTabsUtil.getCurrentLibraryTabs();

        final StrictMode.ThreadPolicy policy =
                new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
    }

    @Override
    public List<String> getTabList() {
        return Collections.unmodifiableList(TAB_LIST);
    }

    private ListView initializeDrawerList() {
        final ListView drawerList = (ListView) findViewById(R.id.left_drawer);
        final DrawerItem[] drawerItems = {
                new DrawerItem(getString(R.string.libraryTabActivity),
                        DrawerItem.Action.ACTION_LIBRARY),

                new DrawerItem(getString(R.string.outputs), DrawerItem.Action.ACTION_OUTPUTS),

                new DrawerItem(getString(R.string.settings), DrawerItem.Action.ACTION_SETTINGS)
        };

        // Set the adapter for the list view
        drawerList.setAdapter(new ArrayAdapter<>(this,
                R.layout.drawer_list_item, drawerItems));
        drawerList.setItemChecked(mOldDrawerPosition, true);

        // Set the list's click listener
        drawerList.setOnItemClickListener(new DrawerItemClickListener());

        return drawerList;
    }

    private ActionBarDrawerToggle initializeDrawerToggle() {

        // Set up the action bar.
        final ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeButtonEnabled(true);
        actionBar.setCustomView(mTextView);
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setDisplayShowHomeEnabled(false);
        actionBar.setDisplayShowCustomEnabled(true);

        final ArrayAdapter<CharSequence> actionBarAdapter = new ArrayAdapter<>(
                actionBar.getThemedContext(),
                android.R.layout.simple_spinner_item);
        for (final String tab : TAB_LIST) {
            actionBarAdapter.add(getText(LibraryTabsUtil.getTabTitleResId(tab)));
        }

        actionBarAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        actionBar.setListNavigationCallbacks(actionBarAdapter, this);

        /**
         * @param Activity activity
         * @param DrawerLayout
         * @param drawerImageRes nav drawer icon to replace 'Up' caret
         * @param openDrawerContentDescRes "open drawer" description
         * @param closeDrawerContentDescRes "close drawer" description
         */
        return new ActionBarDrawerToggle(this, mDrawerLayout, R.string.drawer_open,
                R.string.drawer_close) {

            /**
             * Called when a drawer has settled in a completely closed
             * state.
             */
            @Override
            public void onDrawerClosed(final View drawerView) {
                refreshActionBarTitle();
            }

            /**
             * Called when a drawer has settled in a completely open
             * state.
             */
            @Override
            public void onDrawerOpened(final View drawerView) {
                actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
                actionBar.setDisplayShowCustomEnabled(true);
                mTextView.setText(R.string.app_name);
            }
        };
    }

    private LibraryFragment initializeLibraryFragment() {
        LibraryFragment fragment =
                (LibraryFragment) mFragmentManager.findFragmentByTag(FRAGMENT_TAG_LIBRARY);

        if (fragment == null) {
            fragment = new LibraryFragment();
            final FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
            ft.replace(R.id.library_root_frame, fragment, FRAGMENT_TAG_LIBRARY);
            ft.commit();
        }

        return fragment;
    }

    private OutputsFragment initializeOutputsFragment() {
        OutputsFragment fragment =
                (OutputsFragment) mFragmentManager.findFragmentByTag(FRAGMENT_TAG_OUTPUTS);

        if (fragment == null) {
            fragment = new OutputsFragment();
            final FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
            ft.replace(R.id.outputs_root_frame, fragment, FRAGMENT_TAG_OUTPUTS);
            ft.commit();
        }

        return fragment;
    }

    private TextView initializeTextView() {
        final LayoutInflater inflater = (LayoutInflater) getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        final TextView textView = (TextView) inflater.inflate(R.layout.actionbar_title, null);

        textView.setFocusable(true);
        textView.setFocusableInTouchMode(true);
        textView.setSelected(true);
        textView.requestFocus();

        return textView;
    }

    @Override
    public void onBackPressed() {
        if (mCurrentDisplayMode != DisplayMode.MODE_LIBRARY) {
            switchMode(DisplayMode.MODE_LIBRARY);
        } else if (mFragmentManager.getBackStackEntryCount() > 0) {
            super.onBackPressed();
        } else {
            final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);

            if (settings.getBoolean("enableExitConfirmation", false) && mBackPressExitCount < 1) {
                Tools.notifyUser(R.string.backpressToQuit);
                mBackPressExitCount += 1;
                mExitCounterReset.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mBackPressExitCount = 0;
                    }
                }, 5000L);
            } else {
                finish();
            }
        }
    }

    @Override
    public void onBackStackChanged() {
        refreshActionBarTitle();
    }

    @Override
    public void onConfigurationChanged(final Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mApp.setupServiceBinder();

        setContentView(R.layout.main_activity_nagvigation);

        mTextView = initializeTextView();

        mLibraryRootFrame = findViewById(R.id.library_root_frame);
        mOutputsRootFrame = findViewById(R.id.outputs_root_frame);

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);

        mDrawerToggle = initializeDrawerToggle();

        // Set the drawer toggle as the DrawerListener
        mDrawerLayout.setDrawerListener(mDrawerToggle);
        mDrawerList = initializeDrawerList();

        mFragmentManager = getSupportFragmentManager();
        mFragmentManager.addOnBackStackChangedListener(this);

        mLibraryFragment = initializeLibraryFragment();
        mOutputsFragment = initializeOutputsFragment();
        mQueueFragment = (QueueFragment) mFragmentManager.findFragmentById(R.id.queue_fragment);

        if (savedInstanceState == null) {
            switchMode(DisplayMode.MODE_LIBRARY);
        } else {
            switchMode((DisplayMode) savedInstanceState.getSerializable(EXTRA_DISPLAY_MODE));
        }

        /** Reset the persistent override when the application is reset. */
        mApp.setPersistentOverride(false);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.mpd_searchmenu, menu);
        return true;
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
        final boolean result;

        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            // For onKeyLongPress to work
            event.startTracking();
            result = !mApp.isLocalAudible();
        } else {
            result = super.onKeyDown(keyCode, event);
        }

        return result;
    }

    @Override
    public boolean onKeyLongPress(final int keyCode, final KeyEvent event) {
        boolean result = true;

        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                MPDControl.run(MPDControl.ACTION_NEXT);
                break;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                MPDControl.run(MPDControl.ACTION_PREVIOUS);
                break;
            default:
                result = super.onKeyLongPress(keyCode, event);
                break;
        }
        return result;
    }

    @Override
    public final boolean onKeyUp(final int keyCode, @NonNull final KeyEvent event) {
        boolean result = true;

        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (event.isTracking() && !event.isCanceled() && !mApp.isLocalAudible()) {
                    if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                        MPDControl.run(MPDControl.ACTION_VOLUME_STEP_UP);
                    } else {
                        MPDControl.run(MPDControl.ACTION_VOLUME_STEP_DOWN);
                    }
                }
                break;
            default:
                result = super.onKeyUp(keyCode, event);
                break;
        }

        return result;
    }

    @Override
    public boolean onMenuItemClick(final MenuItem item) {
        return onOptionsItemSelected(item);
    }

    @Override
    public boolean onNavigationItemSelected(final int itemPosition, final long itemId) {
        mLibraryFragment.setCurrentItem(itemPosition, true);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        boolean result = true;
        final boolean itemHandled = mDrawerToggle.onOptionsItemSelected(item) ||
                mQueueFragment != null && mQueueFragment.onOptionsItemSelected(item);
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        if (DEBUG) {
            unregisterReceiver(MPDConnectionHandler.getInstance());
        }
        super.onPause();
    }

    @Override
    protected void onPostCreate(final Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        // Reminder: Never disable buttons that are shown as actionbar actions here.
        super.onPrepareOptionsMenu(menu);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        mBackPressExitCount = 0;
        if (DEBUG) {
            registerReceiver(MPDConnectionHandler.getInstance(),
                    new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION));
        }
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        outState.putSerializable(EXTRA_DISPLAY_MODE, mCurrentDisplayMode);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onStart() {
        super.onStart();
        mApp.setActivity(this);

        if (mApp.isNotificationPersistent()) {
            mApp.startNotification();
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        mApp.unsetActivity(this);
    }

    @Override
    public void pageChanged(final int position) {
        final ActionBar actionBar = getActionBar();
        if (mCurrentDisplayMode == DisplayMode.MODE_LIBRARY
                && actionBar.getNavigationMode() == ActionBar.NAVIGATION_MODE_LIST) {
            actionBar.setSelectedNavigationItem(position);
        }
    }

    @Override
    public void pushLibraryFragment(final Fragment fragment, final String label) {
        final String title;
        if (fragment instanceof BrowseFragment) {
            title = ((BrowseFragment) fragment).getTitle();
        } else {
            title = fragment.toString();
        }
        final FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
        ft.replace(R.id.library_root_frame, fragment);
        ft.addToBackStack(label);
        ft.setBreadCrumbTitle(title);
        ft.commit();
    }

    /**
     * Navigation Drawer helpers
     */

    private void refreshActionBarTitle() {
        final ActionBar actionBar = getActionBar();
        actionBar.setDisplayShowCustomEnabled(true);

        if (mCurrentDisplayMode == DisplayMode.MODE_OUTPUTS) {
            actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
            if (mCurrentDisplayMode == DisplayMode.MODE_OUTPUTS) {
                mTextView.setText(R.string.outputs);
            }
        } else if (mCurrentDisplayMode == DisplayMode.MODE_LIBRARY) {
            int fmStackCount = 0;

            if (mFragmentManager != null) {
                fmStackCount = mFragmentManager.getBackStackEntryCount();
            }

            if (fmStackCount > 0) {
                actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
                mTextView.setText(mFragmentManager.getBackStackEntryAt(fmStackCount - 1)
                        .getBreadCrumbTitle());
            } else {
                actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
                actionBar.setDisplayShowCustomEnabled(false);
            }
        }
    }

    /** Swaps fragments in the main content view */
    void switchMode(final DisplayMode newMode) {
        mCurrentDisplayMode = newMode;
        switch (mCurrentDisplayMode) {
            case MODE_LIBRARY:
                mLibraryRootFrame.setVisibility(View.VISIBLE);
                mOutputsRootFrame.setVisibility(View.GONE);
                break;
            case MODE_OUTPUTS:
                mLibraryRootFrame.setVisibility(View.GONE);
                mOutputsRootFrame.setVisibility(View.VISIBLE);
                mOutputsFragment.refreshOutputs();
                break;
        }
        refreshActionBarTitle();
    }

    public enum DisplayMode {
        MODE_LIBRARY,
        MODE_OUTPUTS
    }

    private static class DrawerItem {

        private final Action mAction;

        private final String mLabel;

        DrawerItem(final String label, final Action action) {
            super();
            mLabel = label;
            mAction = action;
        }

        @Override
        public String toString() {
            return mLabel;
        }

        private enum Action {
            ACTION_LIBRARY,
            ACTION_OUTPUTS,
            ACTION_SETTINGS
        }
    }

    private class DrawerItemClickListener implements AdapterView.OnItemClickListener {

        @Override
        public void onItemClick(final AdapterView<?> parent, final View view, final int position,
                final long id) {
            mDrawerLayout.closeDrawer(mDrawerList);

            switch (((DrawerItem) parent.getItemAtPosition(position)).mAction) {
                case ACTION_LIBRARY:
                    // If we are already on the library, pop the whole stack.
                    // Acts like an "up" button
                    if (mCurrentDisplayMode == DisplayMode.MODE_LIBRARY) {
                        final int fmStackCount = mFragmentManager.getBackStackEntryCount();
                        if (fmStackCount > 0) {
                            mFragmentManager.popBackStack(null,
                                    FragmentManager.POP_BACK_STACK_INCLUSIVE);
                        }
                    }
                    switchMode(DisplayMode.MODE_LIBRARY);
                    break;
                case ACTION_OUTPUTS:
                    switchMode(DisplayMode.MODE_OUTPUTS);
                    break;
                case ACTION_SETTINGS:
                    mDrawerList.setItemChecked(mOldDrawerPosition, true);
                    final Intent intent = new Intent(MainMenuActivity.this, SettingsActivity.class);
                    startActivityForResult(intent, SETTINGS);
                    break;
            }
            mOldDrawerPosition = position;
        }
    }

}

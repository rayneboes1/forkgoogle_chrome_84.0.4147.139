// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.reengagement;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.service.notification.StatusBarNotification;
import android.support.test.InstrumentationRegistry;
import android.text.TextUtils;

import androidx.annotation.StringRes;
import androidx.test.filters.MediumTest;
import androidx.test.filters.SmallTest;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import org.chromium.base.FeatureList;
import org.chromium.base.test.util.CallbackHelper;
import org.chromium.base.test.util.CommandLineFlags;
import org.chromium.base.test.util.DisabledTest;
import org.chromium.base.test.util.UrlUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.DefaultBrowserInfo2;
import org.chromium.chrome.browser.app.reengagement.ReengagementActivity;
import org.chromium.chrome.browser.customtabs.CustomTabActivityTestRule;
import org.chromium.chrome.browser.customtabs.CustomTabsTestUtils;
import org.chromium.chrome.browser.feature_engagement.TrackerFactory;
import org.chromium.chrome.browser.flags.ChromeFeatureList;
import org.chromium.chrome.browser.flags.ChromeSwitches;
import org.chromium.chrome.browser.ntp.NewTabPage;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabCreationState;
import org.chromium.chrome.browser.tabmodel.EmptyTabModelSelectorObserver;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorObserver;
import org.chromium.chrome.test.ChromeJUnit4ClassRunner;
import org.chromium.chrome.test.ChromeTabbedActivityTestRule;
import org.chromium.components.feature_engagement.EventConstants;
import org.chromium.components.feature_engagement.FeatureConstants;
import org.chromium.components.feature_engagement.Tracker;
import org.chromium.content_public.browser.test.util.CriteriaHelper;
import org.chromium.content_public.browser.test.util.TestThreadUtils;
import org.chromium.content_public.common.ContentUrlConstants;

import java.util.HashMap;
import java.util.Map;

/** Integration tests for {@link ReengagementNotificationController}. */
@RunWith(ChromeJUnit4ClassRunner.class)
@CommandLineFlags.Add({ChromeSwitches.DISABLE_FIRST_RUN_EXPERIENCE})
public class ReengagementNotificationControllerIntegrationTest {
    @Rule
    public ChromeTabbedActivityTestRule mTabbedActivityTestRule =
            new ChromeTabbedActivityTestRule();

    @Rule
    public CustomTabActivityTestRule mCustomTabActivityTestRule = new CustomTabActivityTestRule();

    @Rule
    public MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Mock
    public Tracker mTracker;

    @Before
    public void setUp() throws Exception {
        reset(mTracker);
        FeatureList.setTestCanUseDefaultsForTesting();
        setReengagementNotificationEnabled(true);
        TrackerFactory.setTrackerForTests(mTracker);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) closeReengagementNotifications();
    }

    @After
    public void tearDown() {
        TrackerFactory.setTrackerForTests(null);
        DefaultBrowserInfo2.clearDefaultInfoForTests();
        FeatureList.resetTestCanUseDefaultsForTesting();
        FeatureList.setTestFeatures(null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) closeReengagementNotifications();
    }

    @Test
    @MediumTest
    public void testReengagementNotificationSent() {
        DefaultBrowserInfo2.setDefaultInfoForTests(
                createDefaultInfo(/* passesPrecondition = */ true));
        doReturn(true).when(mTracker).shouldTriggerHelpUI(
                FeatureConstants.CHROME_REENGAGEMENT_NOTIFICATION_1_FEATURE);
        mCustomTabActivityTestRule.startCustomTabActivityWithIntent(
                CustomTabsTestUtils.createMinimalCustomTabIntent(
                        InstrumentationRegistry.getTargetContext(),
                        ContentUrlConstants.ABOUT_BLANK_DISPLAY_URL));
        verify(mTracker, times(1))
                .shouldTriggerHelpUI(FeatureConstants.CHROME_REENGAGEMENT_NOTIFICATION_1_FEATURE);
        verify(mTracker, times(1))
                .dismissed(FeatureConstants.CHROME_REENGAGEMENT_NOTIFICATION_1_FEATURE);

        verifyNotification(R.string.chrome_reengagement_notification_1_title,
                R.string.chrome_reengagement_notification_1_description);
    }

    @Test
    @MediumTest
    public void testReengagementDifferentNotificationSent() {
        DefaultBrowserInfo2.setDefaultInfoForTests(
                createDefaultInfo(/* passesPrecondition = */ true));
        doReturn(true).when(mTracker).shouldTriggerHelpUI(
                FeatureConstants.CHROME_REENGAGEMENT_NOTIFICATION_2_FEATURE);
        mCustomTabActivityTestRule.startCustomTabActivityWithIntent(
                CustomTabsTestUtils.createMinimalCustomTabIntent(
                        InstrumentationRegistry.getTargetContext(),
                        ContentUrlConstants.ABOUT_BLANK_DISPLAY_URL));
        verify(mTracker, times(1))
                .shouldTriggerHelpUI(FeatureConstants.CHROME_REENGAGEMENT_NOTIFICATION_2_FEATURE);
        verify(mTracker, times(1))
                .dismissed(FeatureConstants.CHROME_REENGAGEMENT_NOTIFICATION_2_FEATURE);

        verifyNotification(R.string.chrome_reengagement_notification_2_title,
                R.string.chrome_reengagement_notification_2_description);
    }

    @Test
    @MediumTest
    public void testReengagementNotificationNotSentDueToIPH() {
        DefaultBrowserInfo2.setDefaultInfoForTests(
                createDefaultInfo(/* passesPrecondition = */ true));
        mCustomTabActivityTestRule.startCustomTabActivityWithIntent(
                CustomTabsTestUtils.createMinimalCustomTabIntent(
                        InstrumentationRegistry.getTargetContext(),
                        ContentUrlConstants.ABOUT_BLANK_DISPLAY_URL));
        verifyHasNoNotifications();
        verify(mTracker, times(1))
                .shouldTriggerHelpUI(FeatureConstants.CHROME_REENGAGEMENT_NOTIFICATION_1_FEATURE);
        verify(mTracker, times(1))
                .shouldTriggerHelpUI(FeatureConstants.CHROME_REENGAGEMENT_NOTIFICATION_2_FEATURE);
        verify(mTracker, times(1))
                .shouldTriggerHelpUI(FeatureConstants.CHROME_REENGAGEMENT_NOTIFICATION_3_FEATURE);
        verify(mTracker, never())
                .dismissed(FeatureConstants.CHROME_REENGAGEMENT_NOTIFICATION_1_FEATURE);
        verify(mTracker, never())
                .dismissed(FeatureConstants.CHROME_REENGAGEMENT_NOTIFICATION_2_FEATURE);
        verify(mTracker, never())
                .dismissed(FeatureConstants.CHROME_REENGAGEMENT_NOTIFICATION_3_FEATURE);
    }

    @Test
    @MediumTest
    public void testReengagementNotificationNotSentDueToPreconditions() {
        DefaultBrowserInfo2.setDefaultInfoForTests(
                createDefaultInfo(/* passesPrecondition = */ false));
        mCustomTabActivityTestRule.startCustomTabActivityWithIntent(
                CustomTabsTestUtils.createMinimalCustomTabIntent(
                        InstrumentationRegistry.getTargetContext(),
                        ContentUrlConstants.ABOUT_BLANK_DISPLAY_URL));
        verifyHasNoNotifications();
        verify(mTracker, never())
                .shouldTriggerHelpUI(FeatureConstants.CHROME_REENGAGEMENT_NOTIFICATION_1_FEATURE);
        verify(mTracker, never())
                .shouldTriggerHelpUI(FeatureConstants.CHROME_REENGAGEMENT_NOTIFICATION_2_FEATURE);
        verify(mTracker, never())
                .shouldTriggerHelpUI(FeatureConstants.CHROME_REENGAGEMENT_NOTIFICATION_3_FEATURE);
        verify(mTracker, never())
                .dismissed(FeatureConstants.CHROME_REENGAGEMENT_NOTIFICATION_1_FEATURE);
        verify(mTracker, never())
                .dismissed(FeatureConstants.CHROME_REENGAGEMENT_NOTIFICATION_2_FEATURE);
        verify(mTracker, never())
                .dismissed(FeatureConstants.CHROME_REENGAGEMENT_NOTIFICATION_3_FEATURE);
    }

    @Test
    @MediumTest
    public void testReengagementNotificationNotSentDueToUnavailablePreconditions() {
        DefaultBrowserInfo2.setDefaultInfoForTests(null);
        mCustomTabActivityTestRule.startCustomTabActivityWithIntent(
                CustomTabsTestUtils.createMinimalCustomTabIntent(
                        InstrumentationRegistry.getTargetContext(),
                        ContentUrlConstants.ABOUT_BLANK_DISPLAY_URL));
        verifyHasNoNotifications();
        verify(mTracker, never())
                .shouldTriggerHelpUI(FeatureConstants.CHROME_REENGAGEMENT_NOTIFICATION_1_FEATURE);
        verify(mTracker, never())
                .shouldTriggerHelpUI(FeatureConstants.CHROME_REENGAGEMENT_NOTIFICATION_2_FEATURE);
        verify(mTracker, never())
                .shouldTriggerHelpUI(FeatureConstants.CHROME_REENGAGEMENT_NOTIFICATION_3_FEATURE);
        verify(mTracker, never())
                .dismissed(FeatureConstants.CHROME_REENGAGEMENT_NOTIFICATION_1_FEATURE);
        verify(mTracker, never())
                .dismissed(FeatureConstants.CHROME_REENGAGEMENT_NOTIFICATION_2_FEATURE);
        verify(mTracker, never())
                .dismissed(FeatureConstants.CHROME_REENGAGEMENT_NOTIFICATION_3_FEATURE);
    }

    @Test
    @SmallTest
    public void testEngagementTracked() {
        mTabbedActivityTestRule.startMainActivityFromLauncher();
        verify(mTracker, times(1)).notifyEvent(EventConstants.STARTED_FROM_MAIN_INTENT);
    }

    @Test
    @SmallTest
    public void testEngagementNotTracked() {
        mCustomTabActivityTestRule.startCustomTabActivityWithIntent(
                CustomTabsTestUtils.createMinimalCustomTabIntent(
                        InstrumentationRegistry.getTargetContext(),
                        ContentUrlConstants.ABOUT_BLANK_DISPLAY_URL));
        verify(mTracker, never()).notifyEvent(EventConstants.STARTED_FROM_MAIN_INTENT);
    }

    @Test
    @SmallTest
    @DisabledTest(message = "crbug.com/1112519 - Disabled while safety guard is in place.")
    public void testEngagementTrackedWhenDisabled() {
        setReengagementNotificationEnabled(false);
        mTabbedActivityTestRule.startMainActivityFromLauncher();
        verify(mTracker, times(1)).notifyEvent(EventConstants.STARTED_FROM_MAIN_INTENT);
    }

    @Test
    @SmallTest
    public void testEngagementNotTrackedDueToIntentOpeningTab() {
        mTabbedActivityTestRule.startMainActivityWithURL(
                UrlUtils.encodeHtmlDataUri("<html><head></head><body>foo</body></html>"));
        verify(mTracker, never()).notifyEvent(EventConstants.STARTED_FROM_MAIN_INTENT);
    }

    @Test
    @MediumTest
    public void testEngagementNotificationNotSentDueToDisabled() {
        setReengagementNotificationEnabled(false);
        DefaultBrowserInfo2.setDefaultInfoForTests(
                createDefaultInfo(/* passesPrecondition = */ true));
        mCustomTabActivityTestRule.startCustomTabActivityWithIntent(
                CustomTabsTestUtils.createMinimalCustomTabIntent(
                        InstrumentationRegistry.getTargetContext(),
                        ContentUrlConstants.ABOUT_BLANK_DISPLAY_URL));
        verifyHasNoNotifications();
        verify(mTracker, never())
                .shouldTriggerHelpUI(FeatureConstants.CHROME_REENGAGEMENT_NOTIFICATION_1_FEATURE);
        verify(mTracker, never())
                .shouldTriggerHelpUI(FeatureConstants.CHROME_REENGAGEMENT_NOTIFICATION_2_FEATURE);
        verify(mTracker, never())
                .shouldTriggerHelpUI(FeatureConstants.CHROME_REENGAGEMENT_NOTIFICATION_3_FEATURE);
        verify(mTracker, never())
                .dismissed(FeatureConstants.CHROME_REENGAGEMENT_NOTIFICATION_1_FEATURE);
        verify(mTracker, never())
                .dismissed(FeatureConstants.CHROME_REENGAGEMENT_NOTIFICATION_2_FEATURE);
        verify(mTracker, never())
                .dismissed(FeatureConstants.CHROME_REENGAGEMENT_NOTIFICATION_3_FEATURE);
    }

    @Test
    @MediumTest
    public void testReengagementActivity() throws Exception {
        mTabbedActivityTestRule.startMainActivityOnBlankPage();
        int initialTabCount =
                mTabbedActivityTestRule.getActivity().getTabModelSelector().getTotalTabCount();

        final CallbackHelper tabAddedCallback = new CallbackHelper();
        TabModelSelectorObserver selectorObserver = new EmptyTabModelSelectorObserver() {
            @Override
            public void onNewTabCreated(Tab tab, @TabCreationState int creationState) {
                tabAddedCallback.notifyCalled();
            }
        };
        mTabbedActivityTestRule.getActivity().getTabModelSelector().addObserver(selectorObserver);

        Intent intent =
                new Intent(InstrumentationRegistry.getTargetContext(), ReengagementActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setAction(ReengagementNotificationController.LAUNCH_NTP_ACTION);
        InstrumentationRegistry.getInstrumentation().startActivitySync(intent);

        tabAddedCallback.waitForCallback(0);
        Tab tab = TestThreadUtils.runOnUiThreadBlocking(
                () -> mTabbedActivityTestRule.getActivity().getActivityTab());
        Assert.assertTrue(NewTabPage.isNTPUrl(tab.getUrl()));
        Assert.assertFalse(tab.isIncognito());
        Assert.assertEquals(initialTabCount + 1,
                mTabbedActivityTestRule.getActivity().getTabModelSelector().getTotalTabCount());
    }

    private void verifyNotification(@StringRes int title, @StringRes int description) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;

        CriteriaHelper.pollUiThread(() -> { return findNotification(title, description); });
    }

    private void verifyHasNoNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;

        Assert.assertFalse(hasNotifications());
    }

    @TargetApi(Build.VERSION_CODES.M)
    private static boolean findNotification(@StringRes int title, @StringRes int description) {
        Context context = InstrumentationRegistry.getTargetContext();
        StatusBarNotification[] notifications =
                ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE))
                        .getActiveNotifications();

        String titleStr = context.getString(title);
        String descriptionStr = context.getString(description);

        for (StatusBarNotification notification : notifications) {
            CharSequence notifTitle =
                    notification.getNotification().extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence notifDescription =
                    notification.getNotification().extras.getCharSequence(Notification.EXTRA_TEXT);
            if (TextUtils.equals(titleStr, notifTitle)
                    && TextUtils.equals(descriptionStr, notifDescription)) {
                return true;
            }
        }

        return false;
    }

    @TargetApi(Build.VERSION_CODES.M)
    private static boolean hasNotifications() {
        Context context = InstrumentationRegistry.getTargetContext();
        StatusBarNotification[] notifications =
                ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE))
                        .getActiveNotifications();

        for (StatusBarNotification notification : notifications) {
            String tag = notification.getTag();
            if (TextUtils.equals(ReengagementNotificationController.NOTIFICATION_TAG, tag)) {
                return true;
            }
        }

        return false;
    }

    @TargetApi(Build.VERSION_CODES.M)
    private static void closeReengagementNotifications() {
        if (!hasNotifications()) return;

        Context context = InstrumentationRegistry.getTargetContext();
        ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE))
                .cancel(ReengagementNotificationController.NOTIFICATION_TAG,
                        ReengagementNotificationController.NOTIFICATION_ID);
    }

    private DefaultBrowserInfo2.DefaultInfo createDefaultInfo(boolean passesPrecondition) {
        int browserCount = passesPrecondition ? 2 : 1;
        return new DefaultBrowserInfo2.DefaultInfo(/* isChromeSystem = */ true,
                /* isChromeDefault = */ true,
                /* isDefaultSystem = */ true, /* hasDefault = */ true, browserCount,
                /* systemCount = */ 0);
    }

    private static void setReengagementNotificationEnabled(boolean enabled) {
        Map<String, Boolean> features = new HashMap<>();
        features.put(ChromeFeatureList.REENGAGEMENT_NOTIFICATION, enabled);
        // TODO(crbug.com/1111584): Remove these overrides when FeatureList#isInitialized() works
        // as expected with test values.
        features.put(ChromeFeatureList.HORIZONTAL_TAB_SWITCHER_ANDROID, false);
        features.put(ChromeFeatureList.SEARCH_ENGINE_PROMO_EXISTING_DEVICE, false);
        features.put(ChromeFeatureList.OMNIBOX_SEARCH_ENGINE_LOGO, false);
        FeatureList.setTestFeatures(features);
    }
}
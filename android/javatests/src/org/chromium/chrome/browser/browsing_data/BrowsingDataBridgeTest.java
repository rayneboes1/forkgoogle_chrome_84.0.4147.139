// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.browsing_data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import android.support.test.filters.MediumTest;
import android.support.test.filters.SmallTest;

import junit.framework.Assert;

import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.chromium.base.test.util.CallbackHelper;
import org.chromium.base.test.util.CommandLineFlags;
import org.chromium.base.test.util.RetryOnFailure;
import org.chromium.base.test.util.UserActionTester;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.browserservices.BrowserServicesIntentDataProvider;
import org.chromium.chrome.browser.flags.ChromeFeatureList;
import org.chromium.chrome.browser.flags.ChromeSwitches;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabState;
import org.chromium.chrome.browser.webapps.TestFetchStorageCallback;
import org.chromium.chrome.browser.webapps.WebappDataStorage;
import org.chromium.chrome.browser.webapps.WebappRegistry;
import org.chromium.chrome.test.ChromeActivityTestRule;
import org.chromium.chrome.test.ChromeJUnit4ClassRunner;
import org.chromium.chrome.test.util.browser.Features;
import org.chromium.chrome.test.util.browser.webapps.WebappTestHelper;
import org.chromium.content_public.browser.NavigationController;
import org.chromium.content_public.browser.NavigationEntry;
import org.chromium.content_public.browser.WebContents;
import org.chromium.content_public.browser.test.util.TestThreadUtils;
import org.chromium.net.test.EmbeddedTestServer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

/**
 * Integration tests for ClearBrowsingDataPreferences.
 */
@RunWith(ChromeJUnit4ClassRunner.class)
@CommandLineFlags.Add({ChromeSwitches.DISABLE_FIRST_RUN_EXPERIENCE})
public class BrowsingDataBridgeTest {
    @Rule
    public ChromeActivityTestRule<ChromeActivity> mActivityTestRule =
            new ChromeActivityTestRule<>(ChromeActivity.class);

    private CallbackHelper mCallbackHelper;
    private BrowsingDataBridge.OnClearBrowsingDataListener mListener;
    private UserActionTester mActionTester;
    private EmbeddedTestServer mTestServer;

    @Before
    public void setUp() throws Exception {
        mCallbackHelper = new CallbackHelper();
        mListener = new BrowsingDataBridge.OnClearBrowsingDataListener() {
            @Override
            public void onBrowsingDataCleared() {
                mCallbackHelper.notifyCalled();
            }
        };
        mActivityTestRule.startMainActivityOnBlankPage();
        mTestServer = mActivityTestRule.getTestServer();
        mActionTester = new UserActionTester();
    }

    @After
    public void tearDown() {
        mActionTester.tearDown();
    }

    /**
     * Test no clear browsing data calls.
     */
    @Test
    @SmallTest
    @RetryOnFailure
    public void testNoCalls() throws Exception {
        TestThreadUtils.runOnUiThreadBlocking(() -> {
            BrowsingDataBridge.getInstance().clearBrowsingData(
                    mListener, new int[] {}, TimePeriod.ALL_TIME);
        });
        mCallbackHelper.waitForCallback(0);
        assertThat(mActionTester.toString(), getActions(),
                Matchers.contains("ClearBrowsingData_Everything"));
    }

    /**
     * Test cookies deletion.
     */
    @Test
    @SmallTest
    public void testCookiesDeleted() throws Exception {
        TestThreadUtils.runOnUiThreadBlocking(() -> {
            BrowsingDataBridge.getInstance().clearBrowsingData(
                    mListener, new int[] {BrowsingDataType.COOKIES}, TimePeriod.LAST_HOUR);
        });
        mCallbackHelper.waitForCallback(0);
        assertThat(mActionTester.toString(), getActions(),
                Matchers.containsInAnyOrder("ClearBrowsingData_LastHour",
                        "ClearBrowsingData_MaskContainsUnprotectedWeb", "ClearBrowsingData_Cookies",
                        "ClearBrowsingData_SiteUsageData", "ClearBrowsingData_ContentLicenses"));
    }

    /**
     * Get ClearBrowsingData related actions, filter all other actions to avoid flakes.
     */
    private List<String> getActions() {
        List<String> actions = new ArrayList<>(mActionTester.getActions());
        Iterator<String> it = actions.iterator();
        while (it.hasNext()) {
            if (!it.next().startsWith("ClearBrowsingData_")) {
                it.remove();
            }
        }
        return actions;
    }

    /**
     * Test history deletion.
     */
    @Test
    @SmallTest
    public void testHistoryDeleted() throws Exception {
        TestThreadUtils.runOnUiThreadBlocking(() -> {
            BrowsingDataBridge.getInstance().clearBrowsingData(
                    mListener, new int[] {BrowsingDataType.HISTORY}, TimePeriod.LAST_DAY);
        });
        mCallbackHelper.waitForCallback(0);
        assertThat(mActionTester.toString(), getActions(),
                Matchers.containsInAnyOrder("ClearBrowsingData_LastDay",
                        "ClearBrowsingData_MaskContainsUnprotectedWeb",
                        "ClearBrowsingData_History"));
    }

    /**
     * Test deleting cache and content settings.
     */
    @Test
    @SmallTest
    public void testClearingSiteSettingsAndCache() throws Exception {
        TestThreadUtils.runOnUiThreadBlocking(() -> {
            BrowsingDataBridge.getInstance().clearBrowsingData(mListener,
                    new int[] {
                            BrowsingDataType.CACHE,
                            BrowsingDataType.SITE_SETTINGS,
                    },
                    TimePeriod.FOUR_WEEKS);
        });
        mCallbackHelper.waitForCallback(0);
        assertThat(mActionTester.toString(), getActions(),
                Matchers.containsInAnyOrder("ClearBrowsingData_LastMonth",
                        "ClearBrowsingData_MaskContainsUnprotectedWeb", "ClearBrowsingData_Cache",
                        "ClearBrowsingData_ShaderCache", "ClearBrowsingData_ContentSettings"));
    }

    /**
     * Test deleting cache and content settings with important sites.
     */
    @Test
    @SmallTest
    public void testClearingSiteSettingsAndCacheWithImportantSites() throws Exception {
        TestThreadUtils.runOnUiThreadBlocking(() -> {
            BrowsingDataBridge.getInstance().clearBrowsingDataExcludingDomains(mListener,
                    new int[] {
                            BrowsingDataType.CACHE,
                            BrowsingDataType.SITE_SETTINGS,
                    },
                    TimePeriod.FOUR_WEEKS, new String[] {"google.com"}, new int[] {1},
                    new String[0], new int[0]);
        });
        mCallbackHelper.waitForCallback(0);
        assertThat(mActionTester.toString(), getActions(),
                Matchers.containsInAnyOrder("ClearBrowsingData_LastMonth",
                        // ClearBrowsingData_MaskContainsUnprotectedWeb is logged
                        // twice because important storage is deleted separately.
                        "ClearBrowsingData_MaskContainsUnprotectedWeb",
                        "ClearBrowsingData_MaskContainsUnprotectedWeb", "ClearBrowsingData_Cache",
                        "ClearBrowsingData_ShaderCache", "ClearBrowsingData_ContentSettings"));
    }

    /**
     * Test deleting all browsing data. (Except bookmarks, they are deleted in Java code)
     */
    @Test
    @SmallTest
    public void testClearingAll() throws Exception {
        TestThreadUtils.runOnUiThreadBlocking(() -> {
            BrowsingDataBridge.getInstance().clearBrowsingData(mListener,
                    new int[] {
                            BrowsingDataType.CACHE,
                            BrowsingDataType.COOKIES,
                            BrowsingDataType.FORM_DATA,
                            BrowsingDataType.HISTORY,
                            BrowsingDataType.PASSWORDS,
                            BrowsingDataType.SITE_SETTINGS,
                    },
                    TimePeriod.LAST_WEEK);
        });
        mCallbackHelper.waitForCallback(0);
        assertThat(mActionTester.toString(), getActions(),
                Matchers.containsInAnyOrder("ClearBrowsingData_LastWeek",
                        "ClearBrowsingData_MaskContainsUnprotectedWeb", "ClearBrowsingData_Cache",
                        "ClearBrowsingData_ShaderCache", "ClearBrowsingData_Cookies",
                        "ClearBrowsingData_Autofill", "ClearBrowsingData_History",
                        "ClearBrowsingData_Passwords", "ClearBrowsingData_ContentSettings",
                        "ClearBrowsingData_SiteUsageData", "ClearBrowsingData_ContentLicenses"));
    }

    /**
     * Tests navigation entries from frozen state are removed by history deletions.
     */
    @Test
    @MediumTest
    @Features.EnableFeatures(ChromeFeatureList.REMOVE_NAVIGATION_HISTORY)
    public void testFrozenNavigationDeletion() throws Exception {
        final String url1 = mTestServer.getURL("/chrome/test/data/browsing_data/a.html");
        final String url2 = mTestServer.getURL("/chrome/test/data/browsing_data/b.html");

        // Navigate to url1 and url2, close and recreate as frozen tab.
        Tab tab = mActivityTestRule.loadUrlInNewTab(url1);
        mActivityTestRule.loadUrl(url2);
        Tab[] frozen = new Tab[1];
        WebContents[] restored = new WebContents[1];
        TestThreadUtils.runOnUiThreadBlocking(() -> {
            TabState state = TabState.from(tab);
            mActivityTestRule.getActivity().getCurrentTabModel().closeTab(tab);
            frozen[0] = mActivityTestRule.getActivity().getCurrentTabCreator().createFrozenTab(
                    state, tab.getId(), 1);
            restored[0] =
                    TabState.from(frozen[0]).contentsState.restoreContentsFromByteBuffer(false);
        });

        // Check content of frozen state.
        NavigationController controller = restored[0].getNavigationController();
        assertEquals(1, controller.getLastCommittedEntryIndex());
        assertThat(getUrls(controller), Matchers.contains(url1, url2));
        assertNull(frozen[0].getWebContents());

        TestThreadUtils.runOnUiThreadBlocking(() -> {
            BrowsingDataBridge.getInstance().clearBrowsingData(mListener,
                    new int[] {
                            BrowsingDataType.HISTORY,
                    },
                    TimePeriod.LAST_WEEK);
        });

        mCallbackHelper.waitForCallback(0);

        // Check that frozen state was cleaned up.
        TestThreadUtils.runOnUiThreadBlocking(() -> {
            restored[0] =
                    TabState.from(frozen[0]).contentsState.restoreContentsFromByteBuffer(false);
        });

        controller = restored[0].getNavigationController();
        assertEquals(0, controller.getLastCommittedEntryIndex());
        assertThat(getUrls(controller), Matchers.contains(url2));
        assertNull(frozen[0].getWebContents());
    }

    /**
     * Tests navigation entries are removed by history deletions.
     */
    @Test
    @Features.EnableFeatures(ChromeFeatureList.REMOVE_NAVIGATION_HISTORY)
    @MediumTest
    public void testNavigationDeletion() throws Exception {
        final String url1 = mTestServer.getURL("/chrome/test/data/browsing_data/a.html");
        final String url2 = mTestServer.getURL("/chrome/test/data/browsing_data/b.html");

        // Navigate to url1 and url2.
        Tab tab = mActivityTestRule.loadUrlInNewTab(url1);
        mActivityTestRule.loadUrl(url2);
        NavigationController controller = tab.getWebContents().getNavigationController();
        assertTrue(tab.canGoBack());
        assertEquals(1, controller.getLastCommittedEntryIndex());
        assertThat(getUrls(controller), Matchers.contains(url1, url2));

        TestThreadUtils.runOnUiThreadBlocking(() -> {
            BrowsingDataBridge.getInstance().clearBrowsingData(mListener,
                    new int[] {
                            BrowsingDataType.HISTORY,
                    },
                    TimePeriod.LAST_WEEK);
        });
        mCallbackHelper.waitForCallback(0);

        // Check navigation entries.
        assertFalse(tab.canGoBack());
        assertEquals(0, controller.getLastCommittedEntryIndex());
        assertThat(getUrls(controller), Matchers.contains(url2));
    }

    /**
     * Tests that web apps are cleared when the "cookies and site data" option is selected.
     */
    @Test
    @MediumTest
    public void testClearingSiteDataClearsWebapps() throws Exception {
        TestFetchStorageCallback callback = new TestFetchStorageCallback();
        WebappRegistry.getInstance().register("first", callback);
        callback.waitForCallback(0);
        Assert.assertEquals(new HashSet<>(Arrays.asList("first")),
                WebappRegistry.getRegisteredWebappIdsForTesting());

        TestThreadUtils.runOnUiThreadBlocking(() -> {
            BrowsingDataBridge.getInstance().clearBrowsingData(mListener,
                    new int[] {
                            org.chromium.chrome.browser.browsing_data.BrowsingDataType.COOKIES,
                    },
                    TimePeriod.LAST_WEEK);
        });

        Assert.assertTrue(WebappRegistry.getRegisteredWebappIdsForTesting().isEmpty());
    }

    /**
     * Tests that web app scopes and last launch times are cleared when the "history" option is
     * selected. However, the web app is not removed from the registry.
     */
    @Test
    @MediumTest
    public void testClearingHistoryClearsWebappScopesAndLaunchTimes() throws Exception {
        BrowserServicesIntentDataProvider intentDataProvider =
                WebappTestHelper.createIntentDataProvider("id", "url");
        TestFetchStorageCallback callback = new TestFetchStorageCallback();
        WebappRegistry.getInstance().register("first", callback);
        callback.waitForCallback(0);
        callback.getStorage().updateFromWebappIntentDataProvider(intentDataProvider);

        Assert.assertEquals(new HashSet<>(Arrays.asList("first")),
                WebappRegistry.getRegisteredWebappIdsForTesting());

        TestThreadUtils.runOnUiThreadBlocking(() -> {
            BrowsingDataBridge.getInstance().clearBrowsingData(mListener,
                    new int[] {
                            org.chromium.chrome.browser.browsing_data.BrowsingDataType.HISTORY,
                    },
                    TimePeriod.LAST_WEEK);
        });
        Assert.assertEquals(new HashSet<>(Arrays.asList("first")),
                WebappRegistry.getRegisteredWebappIdsForTesting());

        // URL and scope should be empty, and last used time should be 0.
        WebappDataStorage storage = WebappRegistry.getInstance().getWebappDataStorage("first");
        Assert.assertEquals("", storage.getScope());
        Assert.assertEquals("", storage.getUrl());
        Assert.assertEquals(0, storage.getLastUsedTimeMs());
    }

    private List<String> getUrls(NavigationController controller) {
        List<String> urls = new ArrayList<>();
        int i = 0;
        while (true) {
            NavigationEntry entry = controller.getEntryAtIndex(i++);
            if (entry == null) return urls;
            urls.add(entry.getUrl());
        }
    }
}

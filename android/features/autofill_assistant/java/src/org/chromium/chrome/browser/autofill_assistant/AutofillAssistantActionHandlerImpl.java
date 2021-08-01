// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill_assistant;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Nullable;

import org.chromium.base.Callback;
import org.chromium.base.ThreadUtils;
import org.chromium.chrome.browser.ActivityTabProvider;
import org.chromium.chrome.browser.compositor.CompositorViewHolder;
import org.chromium.chrome.browser.fullscreen.ChromeFullscreenManager;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.widget.ScrimView;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheetController;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A handler that provides Autofill Assistant actions for a specific activity.
 */
class AutofillAssistantActionHandlerImpl implements AutofillAssistantActionHandler {
    private final Context mContext;
    private final BottomSheetController mBottomSheetController;
    private final ChromeFullscreenManager mFullscreenManager;
    private final CompositorViewHolder mCompositorViewHolder;
    private final ActivityTabProvider mActivityTabProvider;
    private final ScrimView mScrimView;

    AutofillAssistantActionHandlerImpl(Context context, BottomSheetController bottomSheetController,
            ChromeFullscreenManager fullscreenManager, CompositorViewHolder compositorViewHolder,
            ActivityTabProvider activityTabProvider, ScrimView scrimView) {
        mContext = context;
        mBottomSheetController = bottomSheetController;
        mFullscreenManager = fullscreenManager;
        mCompositorViewHolder = compositorViewHolder;
        mActivityTabProvider = activityTabProvider;
        mScrimView = scrimView;
    }

    @Override
    public void fetchWebsiteActions(
            String userName, String experimentIds, Bundle arguments, Callback<Boolean> callback) {
        if (!AutofillAssistantPreferencesUtil.isAutofillOnboardingAccepted()) {
            callback.onResult(false);
            return;
        }
        AutofillAssistantClient client = getOrCreateClient();
        if (client == null) {
            callback.onResult(false);
            return;
        }

        client.fetchWebsiteActions(userName, experimentIds, toArgumentMap(arguments), callback);
    }

    @Override
    public boolean hasRunFirstCheck() {
        if (!AutofillAssistantPreferencesUtil.isAutofillOnboardingAccepted()) return false;
        AutofillAssistantClient client = getOrCreateClient();
        if (client == null) return false;
        return client.hasRunFirstCheck();
    }

    @Override
    public List<AutofillAssistantDirectAction> getActions() {
        AutofillAssistantClient client = getOrCreateClient();
        if (client == null) {
            return Collections.emptyList();
        }
        return client.getDirectActions();
    }

    @Override
    public void performOnboarding(
            String experimentIds, Bundle arguments, Callback<Boolean> callback) {
        Map<String, String> parameters = toArgumentMap(arguments);
        AssistantOnboardingCoordinator coordinator = new AssistantOnboardingCoordinator(
                experimentIds, parameters, mContext, mBottomSheetController, mFullscreenManager,
                mCompositorViewHolder, mScrimView);
        coordinator.show(accepted -> {
            coordinator.hide();
            callback.onResult(accepted);
        });
    }

    @Override
    public void performAction(
            String name, String experimentIds, Bundle arguments, Callback<Boolean> callback) {
        AutofillAssistantClient client = getOrCreateClient();
        if (client == null) {
            callback.onResult(false);
            return;
        }

        Map<String, String> argumentMap = toArgumentMap(arguments);
        Callback<AssistantOnboardingCoordinator> afterOnboarding = (onboardingCoordinator) -> {
            callback.onResult(client.performDirectAction(
                    name, experimentIds, argumentMap, onboardingCoordinator));
        };

        if (!AutofillAssistantPreferencesUtil.isAutofillOnboardingAccepted()) {
            AssistantOnboardingCoordinator coordinator = new AssistantOnboardingCoordinator(
                    experimentIds, argumentMap, mContext, mBottomSheetController,
                    mFullscreenManager, mCompositorViewHolder, mScrimView);
            coordinator.show(accepted -> {
                if (!accepted) {
                    coordinator.hide();
                    callback.onResult(false);
                    return;
                }
                afterOnboarding.onResult(coordinator);
            });
            return;
        }
        afterOnboarding.onResult(null);
    }

    /**
     * Returns a client for the current tab or {@code null} if there's no current tab or the current
     * tab doesn't have an associated browser content.
     */
    @Nullable
    private AutofillAssistantClient getOrCreateClient() {
        ThreadUtils.assertOnUiThread();
        Tab tab = mActivityTabProvider.get();

        if (tab == null || tab.getWebContents() == null) return null;

        return AutofillAssistantClient.fromWebContents(tab.getWebContents());
    }

    /** Extracts string arguments from a bundle. */
    private Map<String, String> toArgumentMap(Bundle bundle) {
        Map<String, String> map = new HashMap<>();
        for (String key : bundle.keySet()) {
            String value = bundle.getString(key);
            if (value != null) {
                map.put(key, value);
            }
        }
        return map;
    }
}

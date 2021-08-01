// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.vr.rules;

import android.content.Intent;

import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import org.chromium.chrome.browser.vr.TestVrShellDelegate;
import org.chromium.chrome.browser.vr.rules.XrActivityRestriction.SupportedActivity;
import org.chromium.chrome.browser.vr.util.VrTestRuleUtils;
import org.chromium.chrome.browser.webapps.WebappActivityTestRule;

/**
 * VR extension of WebappActivityTestRule. Applies WebappActivityTestRule then opens
 * up a WebappActivity to a blank page while performing some additional VR-only setup.
 */
public class WebappActivityVrTestRule extends WebappActivityTestRule implements VrTestRule {
    private boolean mDonEnabled;

    @Override
    public Statement apply(final Statement base, final Description desc) {
        return super.apply(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                VrTestRuleUtils.evaluateVrTestRuleImpl(
                        base, desc, WebappActivityVrTestRule.this, () -> {
                            startWebappActivity();
                            TestVrShellDelegate.createTestVrShellDelegate(getActivity());
                        });
            }
        }, desc);
    }

    @Override
    public @SupportedActivity int getRestriction() {
        return SupportedActivity.WAA;
    }

    @Override
    public boolean isDonEnabled() {
        return mDonEnabled;
    }

    @Override
    public void setDonEnabled(boolean isEnabled) {
        mDonEnabled = isEnabled;
    }

    @Override
    public Intent createIntent() {
        return VrTestRuleUtils.maybeAddStandaloneIntentData(super.createIntent());
    }
}

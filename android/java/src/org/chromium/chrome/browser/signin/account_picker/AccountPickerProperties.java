// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.signin.account_picker;

import androidx.annotation.IntDef;

import org.chromium.base.Callback;
import org.chromium.chrome.browser.signin.DisplayableProfileData;
import org.chromium.ui.modelutil.PropertyKey;
import org.chromium.ui.modelutil.PropertyModel;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Properties for account picker.
 */
class AccountPickerProperties {
    private AccountPickerProperties() {}

    /**
     * Properties for "add account" row in account picker.
     */
    static class AddAccountRowProperties {
        static final PropertyModel.ReadableObjectPropertyKey<Runnable> ON_CLICK_LISTENER =
                new PropertyModel.ReadableObjectPropertyKey<>("on_click_listener");

        static final PropertyKey[] ALL_KEYS = new PropertyKey[] {ON_CLICK_LISTENER};

        private AddAccountRowProperties() {}

        static PropertyModel createModel(Runnable runnableAddAccount) {
            return new PropertyModel.Builder(ALL_KEYS)
                    .with(ON_CLICK_LISTENER, runnableAddAccount)
                    .build();
        }
    }

    /**
     * Properties for account row in account picker.
     */
    static class ExistingAccountRowProperties {
        static final PropertyModel.ReadableObjectPropertyKey<DisplayableProfileData> PROFILE_DATA =
                new PropertyModel.ReadableObjectPropertyKey<>("profile_data");
        static final PropertyModel.WritableBooleanPropertyKey IS_SELECTED_ACCOUNT =
                new PropertyModel.WritableBooleanPropertyKey("is_selected_account");
        static final PropertyModel
                .ReadableObjectPropertyKey<Callback<DisplayableProfileData>> ON_CLICK_LISTENER =
                new PropertyModel.ReadableObjectPropertyKey<>("on_click_listener");

        static final PropertyKey[] ALL_KEYS =
                new PropertyKey[] {PROFILE_DATA, IS_SELECTED_ACCOUNT, ON_CLICK_LISTENER};

        private ExistingAccountRowProperties() {}

        static PropertyModel createModel(DisplayableProfileData profileData,
                Callback<DisplayableProfileData> listener, boolean isSelectedAccount) {
            return new PropertyModel.Builder(ALL_KEYS)
                    .with(PROFILE_DATA, profileData)
                    .with(IS_SELECTED_ACCOUNT, isSelectedAccount)
                    .with(ON_CLICK_LISTENER, listener)
                    .build();
        }
    }

    /**
     * Item types of account picker.
     */
    @IntDef({ItemType.EXISTING_ACCOUNT_ROW, ItemType.ADD_ACCOUNT_ROW})
    @Retention(RetentionPolicy.SOURCE)
    @interface ItemType {
        /**
         * Item type for models created with {@link ExistingAccountRowProperties#createModel} and
         * use {@link ExistingAccountRowViewBinder} for view setup.
         */
        int EXISTING_ACCOUNT_ROW = 1;

        /**
         * Item type for models created with {@link AddAccountRowProperties#createModel} and
         * use {@link AddAccountRowViewBinder} for view setup.
         */
        int ADD_ACCOUNT_ROW = 2;
    }
}

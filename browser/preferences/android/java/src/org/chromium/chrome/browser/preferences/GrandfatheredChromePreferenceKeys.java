// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import org.chromium.base.annotations.CheckDiscard;

import java.util.Arrays;
import java.util.List;

/**
 * Do not add new constants to this list unless you are migrating old SharedPreferences keys.
 * Instead, declare new keys in the format "Chrome.[Feature].[Key]", for example
 * "Chrome.FooBar.FooEnabled", and add them to {@link ChromePreferenceKeys#getKeysInUse()}.
 */
@CheckDiscard("Validation is performed in tests and in debug builds.")
public class GrandfatheredChromePreferenceKeys {

    /**
     * @return The list of [keys in use] that do not conform to the "Chrome.[Feature].[Key]"
     *     format.
     */
    static List<String> getKeysInUse() {
        // clang-format off
        return Arrays.asList(
                ChromePreferenceKeys.ACCESSIBILITY_TAB_SWITCHER,
                ChromePreferenceKeys.APP_LOCALE,
                ChromePreferenceKeys.AUTOFILL_ASSISTANT_ENABLED,
                ChromePreferenceKeys.AUTOFILL_ASSISTANT_ONBOARDING_ACCEPTED,
                ChromePreferenceKeys.AUTOFILL_ASSISTANT_SKIP_INIT_SCREEN,
                ChromePreferenceKeys.BACKUP_FIRST_BACKUP_DONE,
                ChromePreferenceKeys.BOOKMARKS_LAST_MODIFIED_FOLDER_ID,
                ChromePreferenceKeys.BOOKMARKS_LAST_USED_URL,
                ChromePreferenceKeys.BOOKMARKS_LAST_USED_PARENT,
                ChromePreferenceKeys.CHROME_DEFAULT_BROWSER,
                ChromePreferenceKeys.CONTENT_SUGGESTIONS_SHOWN,
                ChromePreferenceKeys.CONTEXTUAL_SEARCH_ALL_TIME_OPEN_COUNT,
                ChromePreferenceKeys.CONTEXTUAL_SEARCH_ALL_TIME_TAP_COUNT,
                ChromePreferenceKeys.CONTEXTUAL_SEARCH_ALL_TIME_TAP_QUICK_ANSWER_COUNT,
                ChromePreferenceKeys.CONTEXTUAL_SEARCH_CURRENT_WEEK_NUMBER,
                ChromePreferenceKeys.CONTEXTUAL_SEARCH_ENTITY_IMPRESSIONS_COUNT,
                ChromePreferenceKeys.CONTEXTUAL_SEARCH_ENTITY_OPENS_COUNT,
                ChromePreferenceKeys.CONTEXTUAL_SEARCH_LAST_ANIMATION_TIME,
                ChromePreferenceKeys.CONTEXTUAL_SEARCH_NEWEST_WEEK,
                ChromePreferenceKeys.CONTEXTUAL_SEARCH_OLDEST_WEEK,
                ChromePreferenceKeys.CONTEXTUAL_SEARCH_PREVIOUS_INTERACTION_ENCODED_OUTCOMES,
                ChromePreferenceKeys.CONTEXTUAL_SEARCH_PREVIOUS_INTERACTION_EVENT_ID,
                ChromePreferenceKeys.CONTEXTUAL_SEARCH_PREVIOUS_INTERACTION_TIMESTAMP,
                ChromePreferenceKeys.CONTEXTUAL_SEARCH_PROMO_OPEN_COUNT,
                ChromePreferenceKeys.CONTEXTUAL_SEARCH_QUICK_ACTIONS_IGNORED_COUNT,
                ChromePreferenceKeys.CONTEXTUAL_SEARCH_QUICK_ACTIONS_TAKEN_COUNT,
                ChromePreferenceKeys.CONTEXTUAL_SEARCH_QUICK_ACTION_IMPRESSIONS_COUNT,
                ChromePreferenceKeys.CONTEXTUAL_SEARCH_TAP_SINCE_OPEN_COUNT,
                ChromePreferenceKeys.CONTEXTUAL_SEARCH_TAP_SINCE_OPEN_QUICK_ANSWER_COUNT,
                ChromePreferenceKeys.CONTEXTUAL_SEARCH_TAP_TRIGGERED_PROMO_COUNT,
                ChromePreferenceKeys.CRASH_UPLOAD_FAILURE_BROWSER,
                ChromePreferenceKeys.CRASH_UPLOAD_FAILURE_GPU,
                ChromePreferenceKeys.CRASH_UPLOAD_FAILURE_OTHER,
                ChromePreferenceKeys.CRASH_UPLOAD_FAILURE_RENDERER,
                ChromePreferenceKeys.CRASH_UPLOAD_SUCCESS_BROWSER,
                ChromePreferenceKeys.CRASH_UPLOAD_SUCCESS_GPU,
                ChromePreferenceKeys.CRASH_UPLOAD_SUCCESS_OTHER,
                ChromePreferenceKeys.CRASH_UPLOAD_SUCCESS_RENDERER,
                ChromePreferenceKeys.CUSTOM_TABS_LAST_URL,
                ChromePreferenceKeys.DATA_REDUCTION_DISPLAYED_FRE_OR_SECOND_PROMO_TIME_MS,
                ChromePreferenceKeys.DATA_REDUCTION_DISPLAYED_FRE_OR_SECOND_PROMO_VERSION,
                ChromePreferenceKeys.DATA_REDUCTION_DISPLAYED_FRE_OR_SECOND_RUN_PROMO,
                ChromePreferenceKeys.DATA_REDUCTION_DISPLAYED_INFOBAR_PROMO,
                ChromePreferenceKeys.DATA_REDUCTION_DISPLAYED_INFOBAR_PROMO_VERSION,
                ChromePreferenceKeys.DATA_REDUCTION_DISPLAYED_MILESTONE_PROMO_SAVED_BYTES,
                ChromePreferenceKeys.DATA_REDUCTION_ENABLED,
                ChromePreferenceKeys.DATA_REDUCTION_FIRST_ENABLED_TIME,
                ChromePreferenceKeys.DATA_REDUCTION_FRE_PROMO_OPT_OUT,
                ChromePreferenceKeys.DATA_REDUCTION_SITE_BREAKDOWN_ALLOWED_DATE,
                ChromePreferenceKeys.DOWNLOAD_AUTO_RESUMPTION_ATTEMPT_LEFT,
                ChromePreferenceKeys.DOWNLOAD_FOREGROUND_SERVICE_OBSERVERS,
                ChromePreferenceKeys.DOWNLOAD_IS_DOWNLOAD_HOME_ENABLED,
                ChromePreferenceKeys.DOWNLOAD_NEXT_DOWNLOAD_NOTIFICATION_ID,
                ChromePreferenceKeys.DOWNLOAD_PENDING_DOWNLOAD_NOTIFICATIONS,
                ChromePreferenceKeys.DOWNLOAD_PENDING_OMA_DOWNLOADS,
                ChromePreferenceKeys.DOWNLOAD_UMA_ENTRY,
                ChromePreferenceKeys.FIRST_RUN_CACHED_TOS_ACCEPTED,
                ChromePreferenceKeys.FIRST_RUN_FLOW_COMPLETE,
                ChromePreferenceKeys.FIRST_RUN_FLOW_SIGNIN_ACCOUNT_NAME,
                ChromePreferenceKeys.FIRST_RUN_FLOW_SIGNIN_COMPLETE,
                ChromePreferenceKeys.FIRST_RUN_FLOW_SIGNIN_SETUP,
                ChromePreferenceKeys.FIRST_RUN_LIGHTWEIGHT_FLOW_COMPLETE,
                ChromePreferenceKeys.FIRST_RUN_SKIP_WELCOME_PAGE,
                ChromePreferenceKeys.FLAGS_CACHED_ADAPTIVE_TOOLBAR_ENABLED,
                ChromePreferenceKeys.FLAGS_CACHED_BOTTOM_TOOLBAR_ENABLED,
                ChromePreferenceKeys.FLAGS_CACHED_COMMAND_LINE_ON_NON_ROOTED_ENABLED,
                ChromePreferenceKeys.FLAGS_CACHED_DOWNLOAD_AUTO_RESUMPTION_IN_NATIVE,
                ChromePreferenceKeys.FLAGS_CACHED_GRID_TAB_SWITCHER_ENABLED,
                ChromePreferenceKeys.FLAGS_CACHED_IMMERSIVE_UI_MODE_ENABLED,
                ChromePreferenceKeys.FLAGS_CACHED_INTEREST_FEED_CONTENT_SUGGESTIONS,
                ChromePreferenceKeys.FLAGS_CACHED_LABELED_BOTTOM_TOOLBAR_ENABLED,
                ChromePreferenceKeys.FLAGS_CACHED_NETWORK_SERVICE_WARM_UP_ENABLED,
                ChromePreferenceKeys.FLAGS_CACHED_PRIORITIZE_BOOTSTRAP_TASKS,
                ChromePreferenceKeys.FLAGS_CACHED_SERVICE_MANAGER_FOR_BACKGROUND_PREFETCH,
                ChromePreferenceKeys.FLAGS_CACHED_SERVICE_MANAGER_FOR_DOWNLOAD_RESUMPTION,
                ChromePreferenceKeys.FLAGS_CACHED_START_SURFACE_ENABLED,
                ChromePreferenceKeys.FLAGS_CACHED_SWAP_PIXEL_FORMAT_TO_FIX_CONVERT_FROM_TRANSLUCENT,
                ChromePreferenceKeys.FLAGS_CACHED_TAB_GROUPS_ANDROID_ENABLED,
                ChromePreferenceKeys.FONT_USER_FONT_SCALE_FACTOR,
                ChromePreferenceKeys.FONT_USER_SET_FORCE_ENABLE_ZOOM,
                ChromePreferenceKeys.HISTORY_SHOW_HISTORY_INFO,
                ChromePreferenceKeys.HOMEPAGE_CUSTOM_URI,
                ChromePreferenceKeys.HOMEPAGE_ENABLED,
                ChromePreferenceKeys.HOMEPAGE_USE_DEFAULT_URI,
                ChromePreferenceKeys.INCOGNITO_SHORTCUT_ADDED,
                ChromePreferenceKeys.INVALIDATIONS_UUID_PREF_KEY,
                ChromePreferenceKeys.LATEST_UNSUPPORTED_VERSION,
                ChromePreferenceKeys.LOCALE_MANAGER_AUTO_SWITCH,
                ChromePreferenceKeys.LOCALE_MANAGER_PROMO_SHOWN,
                ChromePreferenceKeys.LOCALE_MANAGER_SEARCH_ENGINE_PROMO_SHOW_STATE,
                ChromePreferenceKeys.LOCALE_MANAGER_WAS_IN_SPECIAL_LOCALE,
                ChromePreferenceKeys.MEDIA_WEBRTC_NOTIFICATION_IDS,
                ChromePreferenceKeys.METRICS_MAIN_INTENT_LAUNCH_COUNT,
                ChromePreferenceKeys.METRICS_MAIN_INTENT_LAUNCH_TIMESTAMP,
                ChromePreferenceKeys.NOTIFICATIONS_CHANNELS_VERSION,
                ChromePreferenceKeys.NOTIFICATIONS_LAST_SHOWN_NOTIFICATION_TYPE,
                ChromePreferenceKeys.NOTIFICATIONS_NEXT_TRIGGER,
                ChromePreferenceKeys.NTP_SNIPPETS_IS_SCHEDULED,
                ChromePreferenceKeys.OFFLINE_AUTO_FETCH_SHOWING_IN_PROGRESS,
                ChromePreferenceKeys.OFFLINE_AUTO_FETCH_USER_CANCEL_ACTION_IN_PROGRESS,
                ChromePreferenceKeys.OFFLINE_INDICATOR_V2_ENABLED,
                ChromePreferenceKeys.PAYMENTS_CHECK_SAVE_CARD_TO_DEVICE,
                ChromePreferenceKeys.PAYMENTS_PAYMENT_COMPLETE_ONCE,
                ChromePreferenceKeys.PREFETCH_HAS_NEW_PAGES,
                ChromePreferenceKeys.PREFETCH_IGNORED_NOTIFICATION_COUNTER,
                ChromePreferenceKeys.PREFETCH_NOTIFICATION_ENABLED,
                ChromePreferenceKeys.PREFETCH_NOTIFICATION_TIME,
                ChromePreferenceKeys.PREFETCH_OFFLINE_COUNTER,
                ChromePreferenceKeys.PRIVACY_ALLOW_PRERENDER_OLD,
                ChromePreferenceKeys.PRIVACY_BANDWIDTH_NO_CELLULAR_OLD,
                ChromePreferenceKeys.PRIVACY_BANDWIDTH_OLD,
                ChromePreferenceKeys.PRIVACY_METRICS_IN_SAMPLE,
                ChromePreferenceKeys.PRIVACY_METRICS_REPORTING,
                ChromePreferenceKeys.PRIVACY_NETWORK_PREDICTIONS,
                ChromePreferenceKeys.PROFILES_BOOT_TIMESTAMP,
                ChromePreferenceKeys.PROMOS_SKIPPED_ON_FIRST_START,
                ChromePreferenceKeys.REACHED_CODE_PROFILER_GROUP,
                ChromePreferenceKeys.RLZ_NOTIFIED,
                ChromePreferenceKeys.SEARCH_ENGINE_CHOICE_DEFAULT_TYPE_BEFORE,
                ChromePreferenceKeys.SEARCH_ENGINE_CHOICE_PRESENTED_VERSION,
                ChromePreferenceKeys.SEARCH_ENGINE_CHOICE_REQUESTED_TIMESTAMP,
                ChromePreferenceKeys.SEND_TAB_TO_SELF_ACTIVE_NOTIFICATIONS,
                ChromePreferenceKeys.SEND_TAB_TO_SELF_NEXT_NOTIFICATION_ID,
                ChromePreferenceKeys.SETTINGS_DEVELOPER_ENABLED,
                ChromePreferenceKeys.SETTINGS_DEVELOPER_TRACING_CATEGORIES,
                ChromePreferenceKeys.SETTINGS_DEVELOPER_TRACING_MODE,
                ChromePreferenceKeys.SETTINGS_PRIVACY_OTHER_FORMS_OF_HISTORY_DIALOG_SHOWN,
                ChromePreferenceKeys.SETTINGS_SYNC_SIGN_OUT_ALLOWED,
                ChromePreferenceKeys.SETTINGS_WEBSITE_FAILED_BUILD_VERSION,
                ChromePreferenceKeys.SHARING_LAST_SHARED_CLASS_NAME,
                ChromePreferenceKeys.SHARING_LAST_SHARED_PACKAGE_NAME,
                ChromePreferenceKeys.SIGNIN_ACCOUNTS_CHANGED,
                ChromePreferenceKeys.SIGNIN_ACCOUNT_RENAMED,
                ChromePreferenceKeys.SIGNIN_ACCOUNT_RENAME_EVENT_INDEX,
                ChromePreferenceKeys.SIGNIN_AND_SYNC_PROMO_SHOW_COUNT,
                ChromePreferenceKeys.SIGNIN_PROMO_IMPRESSIONS_COUNT_BOOKMARKS,
                ChromePreferenceKeys.SIGNIN_PROMO_IMPRESSIONS_COUNT_SETTINGS,
                ChromePreferenceKeys.SIGNIN_PROMO_LAST_SHOWN_ACCOUNT_NAMES,
                ChromePreferenceKeys.SIGNIN_PROMO_LAST_SHOWN_MAJOR_VERSION,
                ChromePreferenceKeys.SIGNIN_PROMO_NTP_PROMO_DISMISSED,
                ChromePreferenceKeys.SIGNIN_PROMO_NTP_PROMO_SUPPRESSION_PERIOD_START,
                ChromePreferenceKeys.SIGNIN_PROMO_PERSONALIZED_DECLINED,
                ChromePreferenceKeys.SIGNIN_PROMO_SETTINGS_PERSONALIZED_DISMISSED,
                ChromePreferenceKeys.SNAPSHOT_DATABASE_REMOVED,
                ChromePreferenceKeys.START_SURFACE_SINGLE_PANE_ENABLED_KEY,
                ChromePreferenceKeys.SURVEY_DATE_LAST_ROLLED,
                ChromePreferenceKeys.SURVEY_INFO_BAR_DISPLAYED,
                ChromePreferenceKeys.SYNC_SESSIONS_UUID,
                ChromePreferenceKeys.TABBED_ACTIVITY_LAST_BACKGROUNDED_TIME_MS_PREF,
                ChromePreferenceKeys.TABMODEL_ACTIVE_TAB_ID,
                ChromePreferenceKeys.TABMODEL_HAS_COMPUTED_MAX_ID,
                ChromePreferenceKeys.TABMODEL_HAS_RUN_FILE_MIGRATION,
                ChromePreferenceKeys.TABMODEL_HAS_RUN_MULTI_INSTANCE_FILE_MIGRATION,
                ChromePreferenceKeys.TAB_ID_MANAGER_NEXT_ID,
                ChromePreferenceKeys.TOS_ACKED_ACCOUNTS,
                ChromePreferenceKeys.TWA_DIALOG_NUMBER_OF_DISMISSALS_ON_CLEAR_DATA,
                ChromePreferenceKeys.TWA_DIALOG_NUMBER_OF_DISMISSALS_ON_UNINSTALL,
                ChromePreferenceKeys.TWA_DISCLOSURE_ACCEPTED_PACKAGES,
                ChromePreferenceKeys.UI_THEME_DARKEN_WEBSITES_ENABLED,
                ChromePreferenceKeys.UI_THEME_SETTING,
                ChromePreferenceKeys.VARIATION_CACHED_BOTTOM_TOOLBAR,
                ChromePreferenceKeys.VERIFIED_DIGITAL_ASSET_LINKS,
                ChromePreferenceKeys.VR_EXIT_TO_2D_COUNT,
                ChromePreferenceKeys.VR_FEEDBACK_OPT_OUT,
                ChromePreferenceKeys.VR_SHOULD_REGISTER_ASSETS_COMPONENT_ON_STARTUP,
                ChromePreferenceKeys.WEBAPK_EXTRACTED_DEX_VERSION,
                ChromePreferenceKeys.WEBAPK_LAST_SDK_VERSION,
                ChromePreferenceKeys.WEBAPK_UNINSTALLED_PACKAGES,
                ChromePreferenceKeys.KEY_ZERO_SUGGEST_LIST_SIZE,
                ChromePreferenceKeys.KEY_ZERO_SUGGEST_HEADER_LIST_SIZE
        );
        // clang-format on
    }

    static List<KeyPrefix> getPrefixesInUse() {
        // clang-format off
        return Arrays.asList(
                ChromePreferenceKeys.CONTEXTUAL_SEARCH_CLICKS_WEEK_PREFIX,
                ChromePreferenceKeys.CONTEXTUAL_SEARCH_IMPRESSIONS_WEEK_PREFIX,
                ChromePreferenceKeys.CUSTOM_TABS_DEX_LAST_UPDATE_TIME_PREF_PREFIX,
                ChromePreferenceKeys.PAYMENTS_PAYMENT_INSTRUMENT_USE_COUNT,
                ChromePreferenceKeys.PAYMENTS_PAYMENT_INSTRUMENT_USE_DATE,
                ChromePreferenceKeys.KEY_ZERO_SUGGEST_URL_PREFIX,
                ChromePreferenceKeys.KEY_ZERO_SUGGEST_DISPLAY_TEXT_PREFIX,
                ChromePreferenceKeys.KEY_ZERO_SUGGEST_DESCRIPTION_PREFIX,
                ChromePreferenceKeys.KEY_ZERO_SUGGEST_NATIVE_TYPE_PREFIX,
                ChromePreferenceKeys.KEY_ZERO_SUGGEST_IS_SEARCH_TYPE_PREFIX,
                ChromePreferenceKeys.KEY_ZERO_SUGGEST_ANSWER_TEXT_PREFIX,
                ChromePreferenceKeys.KEY_ZERO_SUGGEST_GROUP_ID_PREFIX,
                ChromePreferenceKeys.KEY_ZERO_SUGGEST_IS_DELETABLE_PREFIX,
                ChromePreferenceKeys.KEY_ZERO_SUGGEST_IS_STARRED_PREFIX,
                ChromePreferenceKeys.KEY_ZERO_SUGGEST_POST_CONTENT_TYPE_PREFIX,
                ChromePreferenceKeys.KEY_ZERO_SUGGEST_POST_CONTENT_DATA_PREFIX,
                ChromePreferenceKeys.KEY_ZERO_SUGGEST_HEADER_GROUP_ID_PREFIX,
                ChromePreferenceKeys.KEY_ZERO_SUGGEST_HEADER_GROUP_TITLE_PREFIX
        );
        // clang-format on
    }
}

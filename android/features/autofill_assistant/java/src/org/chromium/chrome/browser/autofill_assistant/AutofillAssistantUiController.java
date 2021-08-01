// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill_assistant;

import android.content.Context;

import androidx.annotation.Nullable;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.base.annotations.NativeMethods;
import org.chromium.base.task.PostTask;
import org.chromium.chrome.autofill_assistant.R;
import org.chromium.chrome.browser.ActivityTabProvider;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.autofill_assistant.carousel.AssistantCarouselModel;
import org.chromium.chrome.browser.autofill_assistant.carousel.AssistantChip;
import org.chromium.chrome.browser.autofill_assistant.carousel.AssistantChip.Type;
import org.chromium.chrome.browser.autofill_assistant.header.AssistantHeaderModel;
import org.chromium.chrome.browser.autofill_assistant.metrics.DropOutReason;
import org.chromium.chrome.browser.customtabs.CustomTabActivity;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.ui.TabObscuringHandler;
import org.chromium.chrome.browser.ui.messages.snackbar.SnackbarManager.SnackbarController;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheetController;
import org.chromium.content_public.browser.UiThreadTaskTraits;
import org.chromium.content_public.browser.WebContents;
import org.chromium.ui.base.WindowAndroid;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Bridge to native side autofill_assistant::UiControllerAndroid. It allows native side to control
 * Autofill Assistant related UIs and forward UI events to native side.
 * This controller is purely a translation and forwarding layer between Native side and the
 * different Java coordinators.
 */
@JNINamespace("autofill_assistant")
// TODO(crbug.com/806868): This class should be removed once all logic is in native side and the
// model is directly modified by the native AssistantMediator.
class AutofillAssistantUiController {
    private static Set<ChromeActivity> sActiveChromeActivities;
    private long mNativeUiController;

    private final ChromeActivity mActivity;
    private final AssistantCoordinator mCoordinator;
    private final ActivityTabProvider.ActivityTabTabObserver mActivityTabObserver;
    private WebContents mWebContents;
    private SnackbarController mSnackbarController;

    /**
     * Finds an activity to which a AA UI can be added.
     *
     * <p>The activity must not already have an AA UI installed.
     */
    @CalledByNative
    @Nullable
    private static ChromeActivity findAppropriateActivity(WebContents webContents) {
        ChromeActivity activity = ChromeActivity.fromWebContents(webContents);
        if (activity != null && isActive(activity)) {
            return null;
        }

        return activity;
    }

    /**
     * Returns {@code true} if an AA UI is active on the given activity.
     *
     * <p>Used to avoid creating duplicate coordinators views.
     *
     * <p>TODO(crbug.com/806868): Refactor to have AssistantCoordinator owned by the activity, so
     * it's easy to guarantee that there will be at most one per activity.
     */
    private static boolean isActive(ChromeActivity activity) {
        if (sActiveChromeActivities == null) {
            return false;
        }

        return sActiveChromeActivities.contains(activity);
    }

    @CalledByNative
    private static AutofillAssistantUiController create(ChromeActivity activity,
            boolean allowTabSwitching, long nativeUiController,
            @Nullable AssistantOnboardingCoordinator onboardingCoordinator) {
        assert activity != null;
        assert activity.getBottomSheetController() != null;

        if (sActiveChromeActivities == null) {
            sActiveChromeActivities = new HashSet<>();
        }
        sActiveChromeActivities.add(activity);

        // TODO(crbug.com/1048983): Have the params be passed in to the constructor directly rather
        //         than obtaining them from ChromeActivity getters.
        return new AutofillAssistantUiController(activity, activity.getBottomSheetController(),
                activity.getTabObscuringHandler(), allowTabSwitching, nativeUiController,
                onboardingCoordinator);
    }

    private AutofillAssistantUiController(ChromeActivity activity, BottomSheetController controller,
            TabObscuringHandler tabObscuringHandler, boolean allowTabSwitching,
            long nativeUiController,
            @Nullable AssistantOnboardingCoordinator onboardingCoordinator) {
        mNativeUiController = nativeUiController;
        mActivity = activity;
        mCoordinator = new AssistantCoordinator(activity, controller, tabObscuringHandler,
                onboardingCoordinator == null ? null : onboardingCoordinator.transferControls(),
                this::safeNativeOnKeyboardVisibilityChanged, this::safeNativeOnBackButtonClicked);
        mActivityTabObserver =
                new ActivityTabProvider.ActivityTabTabObserver(activity.getActivityTabProvider()) {
                    @Override
                    protected void onObservingDifferentTab(Tab tab) {
                        if (mWebContents == null) return;

                        if (!allowTabSwitching) {
                            if (tab == null || tab.getWebContents() != mWebContents) {
                                safeNativeOnFatalError(
                                        activity.getString(R.string.autofill_assistant_give_up),
                                        DropOutReason.TAB_CHANGED);
                            }
                            return;
                        }

                        // Get rid of any undo snackbars right away before switching tabs, to avoid
                        // confusion.
                        dismissSnackbar();

                        if (tab == null) {
                            // A null tab indicates that there's no selected tab; Most likely, we're
                            // in the process of selecting a new tab. Hide the UI for possible reuse
                            // later.
                            safeNativeSetVisible(false);
                        } else if (tab.getWebContents() == mWebContents) {
                            // The original tab was re-selected. Show it again and force an
                            // expansion on the bottom sheet.
                            safeNativeSetVisible(true);
                            if (mCoordinator.getBottomBarCoordinator() != null) {
                                showContentAndExpandBottomSheet();
                            }
                        } else {
                            // A new tab was selected. If Autofill Assistant is running on it,
                            // attach the UI to that other instance, otherwise destroy the UI.
                            AutofillAssistantClient.fromWebContents(mWebContents)
                                    .transferUiTo(tab.getWebContents());
                        }
                    }

                    @Override
                    public void onActivityAttachmentChanged(
                            Tab tab, @Nullable WindowAndroid window) {
                        if (mWebContents == null) return;

                        if (window == null && tab.getWebContents() == mWebContents) {
                            if (!allowTabSwitching) {
                                safeNativeStop(DropOutReason.TAB_DETACHED);
                                return;
                            }
                            AutofillAssistantClient.fromWebContents(mWebContents).destroyUi();
                        }
                    }
                };
    }

    // Native => Java methods.

    // TODO(crbug.com/806868): Some of these functions still have a little bit of logic (e.g. make
    // the progress bar pulse when hiding overlay). Maybe it would be better to forward all calls to
    // AssistantCoordinator (that way this bridge would only have a reference to that one) which in
    // turn will forward calls to the other sub coordinators. The main reason this is not done yet
    // is to avoid boilerplate.

    @CalledByNative
    private void setWebContents(@Nullable WebContents webContents) {
        mWebContents = webContents;
    }

    @CalledByNative
    private AssistantModel getModel() {
        return mCoordinator.getModel();
    }

    @CalledByNative
    private void clearNativePtr() {
        mNativeUiController = 0;
        mActivityTabObserver.destroy();
        mCoordinator.destroy();
        sActiveChromeActivities.remove(mActivity);
    }

    /**
     * Close CCT after the current task has finished running - usually after Autofill Assistant has
     * finished shutting itself down.
     */
    @CalledByNative
    private void scheduleCloseCustomTab() {
        if (mActivity instanceof CustomTabActivity) {
            PostTask.postTask(UiThreadTaskTraits.DEFAULT, mActivity::finish);
        }
    }

    @CalledByNative
    private void showContentAndExpandBottomSheet() {
        mCoordinator.getBottomBarCoordinator().showContentAndExpand();
    }

    @CalledByNative
    private void expandBottomSheet() {
        mCoordinator.getBottomBarCoordinator().expand();
    }

    @CalledByNative
    private void collapseBottomSheet() {
        mCoordinator.getBottomBarCoordinator().collapse();
    }

    @CalledByNative
    private void showFeedback(String debugContext) {
        mCoordinator.showFeedback(debugContext);
    }

    @CalledByNative
    private boolean isKeyboardShown() {
        return mCoordinator.getKeyboardCoordinator().isKeyboardShown();
    }

    @CalledByNative
    private void hideKeyboard() {
        mCoordinator.getKeyboardCoordinator().hideKeyboard();
    }

    @CalledByNative
    private void hideKeyboardIfFocusNotOnText() {
        mCoordinator.getKeyboardCoordinator().hideKeyboardIfFocusNotOnText();
    }

    @CalledByNative
    private void showSnackbar(int delayMs, String message) {
        mSnackbarController = AssistantSnackbar.show(mActivity, mActivity.getSnackbarManager(),
                delayMs, message, this::safeSnackbarResult);
    }

    private void dismissSnackbar() {
        if (mSnackbarController != null) {
            mActivity.getSnackbarManager().dismissSnackbars(mSnackbarController);
            mSnackbarController = null;
        }
    }

    /** Creates an empty list of chips. */
    @CalledByNative
    private static List<AssistantChip> createChipList() {
        return new ArrayList<>();
    }

    /**
     * Adds an action button to the chip list, which executes the action {@code actionIndex}.
     */
    @CalledByNative
    private void addActionButton(List<AssistantChip> chips, int icon, String text, int actionIndex,
            boolean disabled, boolean sticky, String identifier) {
        chips.add(new AssistantChip(AssistantChip.Type.BUTTON_HAIRLINE, icon, text, disabled,
                sticky, identifier, () -> safeNativeOnUserActionSelected(actionIndex)));
    }

    /**
     * Adds a highlighted action button to the chip list, which executes the action {@code
     * actionIndex}.
     */
    @CalledByNative
    private void addHighlightedActionButton(List<AssistantChip> chips, int icon, String text,
            int actionIndex, boolean disabled, boolean sticky, String identifier) {
        chips.add(new AssistantChip(Type.BUTTON_FILLED_BLUE, icon, text, disabled, sticky,
                identifier, () -> safeNativeOnUserActionSelected(actionIndex)));
    }

    /**
     * Adds a cancel action button to the chip list. If the keyboard is currently shown, it
     * dismisses the keyboard. Otherwise, it shows the snackbar and then executes
     * {@code actionIndex}, or shuts down Autofill Assistant if {@code actionIndex} is {@code -1}.
     */
    @CalledByNative
    private void addCancelButton(List<AssistantChip> chips, int icon, String text, int actionIndex,
            boolean disabled, boolean sticky, String identifier) {
        chips.add(new AssistantChip(AssistantChip.Type.BUTTON_HAIRLINE, icon, text, disabled,
                sticky, identifier, () -> safeNativeOnCancelButtonClicked(actionIndex)));
    }

    /**
     * Adds a close action button to the chip list, which shuts down Autofill Assistant.
     */
    @CalledByNative
    private void addCloseButton(List<AssistantChip> chips, int icon, String text, boolean disabled,
            boolean sticky, String identifier) {
        chips.add(new AssistantChip(AssistantChip.Type.BUTTON_HAIRLINE, icon, text, disabled,
                sticky, identifier, this::safeNativeOnCloseButtonClicked));
    }

    @CalledByNative
    private void setActions(List<AssistantChip> chips) {
        // TODO(b/144075373): Move this to AssistantCarouselModel and AssistantHeaderModel. Move
        // header chip logic to native.
        AssistantCarouselModel model = getModel().getActionsModel();
        model.setChips(chips);
        setHeaderChip(chips);
    }

    @CalledByNative
    private void setDisableChipChangeAnimations(boolean disable) {
        // TODO(b/144075373): Move this to AssistantCarouselModel.
        getModel().getActionsModel().setDisableChangeAnimations(disable);
    }

    @CalledByNative
    private void setAllChipsVisibleExcept(String identifier, boolean visible) {
        AssistantCarouselModel model = getModel().getActionsModel();
        List<AssistantChip> chips = model.get(AssistantCarouselModel.CHIPS);
        // Copy the list and modify the copy. Modifying the actual list in-place will not fire the
        // relevant change notifications. TODO(b/144075373): Refactor to avoid this deep copy,
        // preferably by moving this to native.
        List<AssistantChip> newChips = new ArrayList<>();
        for (int i = 0; i < chips.size(); ++i) {
            AssistantChip newChip = new AssistantChip(chips.get(i));
            newChips.add(newChip);
            if (!chips.get(i).getIdentifier().equals(identifier)) {
                newChip.setVisible(visible);
            }
        }
        model.setChips(newChips);
    }

    private void setHeaderChip(List<AssistantChip> chips) {
        // The header chip is the first sticky chip found in the actions.
        AssistantChip headerChip = null;
        for (AssistantChip chip : chips) {
            if (chip.isSticky()) {
                headerChip = chip;
                break;
            }
        }

        getModel().getHeaderModel().set(AssistantHeaderModel.CHIP, headerChip);
    }

    @CalledByNative
    private void setViewportMode(@AssistantViewportMode int mode) {
        mCoordinator.getBottomBarCoordinator().setViewportMode(mode);
    }

    @CalledByNative
    private void setPeekMode(@AssistantPeekHeightCoordinator.PeekMode int peekMode) {
        mCoordinator.getBottomBarCoordinator().setPeekMode(peekMode);
    }

    @CalledByNative
    private Context getContext() {
        return mActivity;
    }

    // Native methods.
    private void safeSnackbarResult(boolean undo) {
        if (mNativeUiController != 0) {
            AutofillAssistantUiControllerJni.get().snackbarResult(
                    mNativeUiController, AutofillAssistantUiController.this, undo);
        }
    }

    private void safeNativeStop(@DropOutReason int reason) {
        if (mNativeUiController != 0) {
            AutofillAssistantUiControllerJni.get().stop(
                    mNativeUiController, AutofillAssistantUiController.this, reason);
        }
    }

    private void safeNativeOnFatalError(String message, @DropOutReason int reason) {
        if (mNativeUiController != 0) {
            AutofillAssistantUiControllerJni.get().onFatalError(
                    mNativeUiController, AutofillAssistantUiController.this, message, reason);
        }
    }

    private void safeNativeOnUserActionSelected(int index) {
        if (mNativeUiController != 0) {
            AutofillAssistantUiControllerJni.get().onUserActionSelected(
                    mNativeUiController, AutofillAssistantUiController.this, index);
        }
    }

    private void safeNativeOnCancelButtonClicked(int index) {
        if (mNativeUiController != 0) {
            AutofillAssistantUiControllerJni.get().onCancelButtonClicked(
                    mNativeUiController, AutofillAssistantUiController.this, index);
        }
    }

    private void safeNativeOnCloseButtonClicked() {
        if (mNativeUiController != 0) {
            AutofillAssistantUiControllerJni.get().onCloseButtonClicked(
                    mNativeUiController, AutofillAssistantUiController.this);
        }
    }

    private void safeNativeOnKeyboardVisibilityChanged(boolean visible) {
        if (mNativeUiController != 0) {
            AutofillAssistantUiControllerJni.get().onKeyboardVisibilityChanged(
                    mNativeUiController, AutofillAssistantUiController.this, visible);
        }
    }

    private boolean safeNativeOnBackButtonClicked() {
        if (mNativeUiController != 0) {
            return AutofillAssistantUiControllerJni.get().onBackButtonClicked(
                    mNativeUiController, AutofillAssistantUiController.this);
        }
        return false;
    }

    private void safeNativeSetVisible(boolean visible) {
        if (mNativeUiController != 0) {
            AutofillAssistantUiControllerJni.get().setVisible(
                    mNativeUiController, AutofillAssistantUiController.this, visible);
        }
    }

    @NativeMethods
    interface Natives {
        void snackbarResult(
                long nativeUiControllerAndroid, AutofillAssistantUiController caller, boolean undo);
        void stop(long nativeUiControllerAndroid, AutofillAssistantUiController caller,
                @DropOutReason int reason);
        void onFatalError(long nativeUiControllerAndroid, AutofillAssistantUiController caller,
                String message, @DropOutReason int reason);
        void onUserActionSelected(
                long nativeUiControllerAndroid, AutofillAssistantUiController caller, int index);
        void onCancelButtonClicked(
                long nativeUiControllerAndroid, AutofillAssistantUiController caller, int index);
        void onCloseButtonClicked(
                long nativeUiControllerAndroid, AutofillAssistantUiController caller);
        void onKeyboardVisibilityChanged(long nativeUiControllerAndroid,
                AutofillAssistantUiController caller, boolean visible);
        boolean onBackButtonClicked(
                long nativeUiControllerAndroid, AutofillAssistantUiController caller);
        void setVisible(long nativeUiControllerAndroid, AutofillAssistantUiController caller,
                boolean visible);
    }
}

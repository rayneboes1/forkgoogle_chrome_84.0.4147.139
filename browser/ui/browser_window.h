// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef CHROME_BROWSER_UI_BROWSER_WINDOW_H_
#define CHROME_BROWSER_UI_BROWSER_WINDOW_H_

#include <memory>
#include <string>
#include <vector>

#include "base/callback_forward.h"
#include "base/optional.h"
#include "build/build_config.h"
#include "chrome/browser/apps/intent_helper/apps_navigation_types.h"
#include "chrome/browser/lifetime/browser_close_manager.h"
#include "chrome/browser/sharing/sharing_dialog_data.h"
#include "chrome/browser/signin/chrome_signin_helper.h"
#include "chrome/browser/translate/chrome_translate_client.h"
#include "chrome/browser/ui/bookmarks/bookmark_bar.h"
#include "chrome/browser/ui/browser.h"
#include "chrome/browser/ui/browser_dialogs.h"
#include "chrome/browser/ui/exclusive_access/exclusive_access_bubble_type.h"
#include "chrome/browser/ui/in_product_help/in_product_help.h"
#include "chrome/browser/ui/page_action/page_action_icon_type.h"
#include "chrome/common/buildflags.h"
#include "components/content_settings/core/common/content_settings_types.h"
#include "components/translate/core/common/translate_errors.h"
#include "ui/base/base_window.h"
#include "ui/base/window_open_disposition.h"
#include "ui/gfx/native_widget_types.h"
#include "url/origin.h"

#if defined(OS_ANDROID)
#error This file should only be included on desktop.
#endif

class Browser;
class SharingDialog;
class DownloadShelf;
class ExclusiveAccessContext;
class ExtensionsContainer;
class FindBar;
class GURL;
class LocationBar;
class StatusBubble;

namespace autofill {
class AutofillBubbleHandler;
}  // namespace autofill

namespace content {
class WebContents;
struct NativeWebKeyboardEvent;
enum class KeyboardEventProcessingResult;
}  // namespace content

namespace extensions {
class Command;
class Extension;
}  // namespace extensions

namespace gfx {
class Size;
}

namespace qrcode_generator {
class QRCodeGeneratorBubbleController;
class QRCodeGeneratorBubbleView;
}  // namespace qrcode_generator

namespace signin_metrics {
enum class AccessPoint;
}

namespace send_tab_to_self {
class SendTabToSelfBubbleController;
class SendTabToSelfBubbleView;
}  // namespace send_tab_to_self

namespace web_modal {
class WebContentsModalDialogHost;
}

enum class ImeWarningBubblePermissionStatus;

enum class ShowTranslateBubbleResult {
  // The translate bubble was successfully shown.
  SUCCESS,

  // The various reasons for which the translate bubble could fail to be shown.
  BROWSER_WINDOW_NOT_VALID,
  BROWSER_WINDOW_MINIMIZED,
  BROWSER_WINDOW_NOT_ACTIVE,
  WEB_CONTENTS_NOT_ACTIVE,
  EDITABLE_FIELD_IS_ACTIVE,
};

enum class BrowserThemeChangeType { kBrowserTheme, kNativeTheme };

////////////////////////////////////////////////////////////////////////////////
// BrowserWindow interface
//  An interface implemented by the "view" of the Browser window.
//  This interface includes ui::BaseWindow methods as well as Browser window
//  specific methods.
//
// NOTE: All getters may return NULL.
//
class BrowserWindow : public ui::BaseWindow {
 public:
  virtual ~BrowserWindow() {}

  //////////////////////////////////////////////////////////////////////////////
  // ui::BaseWindow interface notes:

  // Closes the window as soon as possible. If the window is not in a drag
  // session, it will close immediately; otherwise, it will move offscreen (so
  // events are still fired) until the drag ends, then close. This assumes
  // that the Browser is not immediately destroyed, but will be eventually
  // destroyed by other means (eg, the tab strip going to zero elements).
  // Bad things happen if the Browser dtor is called directly as a result of
  // invoking this method.
  // virtual void Close() = 0;

  // Browser::OnWindowDidShow should be called after showing the window.
  // virtual void Show() = 0;

  //////////////////////////////////////////////////////////////////////////////
  // Browser specific methods:

  // Returns true if the browser window is on the current workspace (a.k.a.
  // virtual desktop) or if we can't tell. False otherwise.
  //
  // On Windows, it must not be called while application is dispatching an input
  // synchronous call like SendMessage, because IsWindowOnCurrentVirtualDesktop
  // will return an error.
  virtual bool IsOnCurrentWorkspace() const = 0;

  // Sets the shown |ratio| of the browser's top controls (a.k.a. top-chrome) as
  // a result of gesture scrolling in |web_contents|.
  virtual void SetTopControlsShownRatio(content::WebContents* web_contents,
                                        float ratio) = 0;

  // Whether or not the renderer's viewport size should be shrunk by the height
  // of the browser's top controls.
  // As top-chrome is slided up or down, we don't actually resize the web
  // contents (for perf reasons) but we have to do a bunch of adjustments on the
  // renderer side to make it appear to the user like we're resizing things
  // smoothly:
  //
  // 1) Expose content beyond the web contents rect by expanding the clip.
  // 2) Push bottom-fixed elements around until we get a resize. As top-chrome
  //    hides, we push the fixed elements down by an equivalent amount so that
  //    they appear to stay fixed to the viewport bottom.
  //
  // Only when the user releases their finger to finish the scroll do we
  // actually resize the web contents and clear these adjustments. So web
  // contents has two possible sizes, viewport filling and shrunk by the top
  // controls.
  //
  // The GetTopControlsHeight is a static number that never changes (as long as
  // the top-chrome slide with gesture scrolls feature is enabled). To get the
  // actual "showing" height as the user sees, you multiply this by the shown
  // ratio. However, it's not enough to know this value, the renderer also needs
  // to know which direction it should be doing the above-mentioned adjustments.
  // That's what the DoBrowserControlsShrinkRendererSize bit is for. It tells
  // the renderer whether it's currently in the "viewport filling" or the
  // "shrunk by top controls" state.
  // The returned value should never change while sliding top-chrome is in
  // progress (either due to an in-progress gesture scroll, or due to a
  // renderer-initiated animation of the top controls shown ratio).
  virtual bool DoBrowserControlsShrinkRendererSize(
      const content::WebContents* contents) const = 0;

  // Returns the height of the browser's top controls. This height doesn't
  // change with the current shown ratio above. Renderers will call this to
  // calculate the top-chrome shown ratio from the gesture scroll offset.
  //
  // Note: This should always return 0 if hiding top-chrome with page gesture
  // scrolls is disabled. This is needed so the renderer scrolls the page
  // immediately rather than changing the shown ratio, thinking that top-chrome
  // and the page's top edge are moving.
  virtual int GetTopControlsHeight() const = 0;

  // Propagates to the browser that gesture scrolling has changed state.
  virtual void SetTopControlsGestureScrollInProgress(bool in_progress) = 0;

  // Return the status bubble associated with the frame
  virtual StatusBubble* GetStatusBubble() = 0;

  // Inform the frame that the selected tab favicon or title has changed. Some
  // frames may need to refresh their title bar.
  virtual void UpdateTitleBar() = 0;

  // Inform the frame that its color has changed.
  virtual void UpdateFrameColor() = 0;

  // Invoked when the state of the bookmark bar changes. This is only invoked if
  // the state changes for the current tab, it is not sent when switching tabs.
  virtual void BookmarkBarStateChanged(
      BookmarkBar::AnimateChangeType change_type) = 0;

  // Inform the frame that the dev tools window for the selected tab has
  // changed.
  virtual void UpdateDevTools() = 0;

  // Update any loading animations running in the window. |should_animate| is
  // true if there are tabs loading and the animations should continue, false
  // if there are no active loads and the animations should end.
  virtual void UpdateLoadingAnimations(bool should_animate) = 0;

  // Sets the starred state for the current tab.
  virtual void SetStarredState(bool is_starred) = 0;

  // Sets whether the translate icon is lit for the current tab.
  virtual void SetTranslateIconToggled(bool is_lit) = 0;

  // Called when the active tab changes.  Subclasses which implement
  // TabStripModelObserver should implement this instead of ActiveTabChanged();
  // the Browser will call this method while processing that one.
  virtual void OnActiveTabChanged(content::WebContents* old_contents,
                                  content::WebContents* new_contents,
                                  int index,
                                  int reason) = 0;

  // Called when a tab is detached. Subclasses which implement
  // TabStripModelObserver should implement this instead of processing this
  // in OnTabStripModelChanged(); the Browser will call this method.
  virtual void OnTabDetached(content::WebContents* contents,
                             bool was_active) = 0;

  // Called when the user restores a tab. |command_id| may be IDC_RESTORE_TAB or
  // the menu command, depending on whether the tab was restored via keyboard or
  // main menu.
  virtual void OnTabRestored(int command_id) = 0;

  // Called to force the zoom state to for the active tab to be recalculated.
  // |can_show_bubble| is true when a user presses the zoom up or down keyboard
  // shortcuts and will be false in other cases (e.g. switching tabs, "clicking"
  // + or - in the app menu to change zoom).
  virtual void ZoomChangedForActiveTab(bool can_show_bubble) = 0;

  // Windows and GTK remove the browser controls in fullscreen, but Mac and Ash
  // keep the controls in a slide-down panel.
  virtual bool ShouldHideUIForFullscreen() const = 0;

  // Returns true if the fullscreen bubble is visible.
  virtual bool IsFullscreenBubbleVisible() const = 0;

  // Returns the size of WebContents in the browser. This may be called before
  // the TabStripModel has an active tab.
  virtual gfx::Size GetContentsSize() const = 0;

  // Resizes the window to fit a WebContents of a certain size. This should only
  // be called after the TabStripModel has an active tab.
  virtual void SetContentsSize(const gfx::Size& size) = 0;

  // Updates the visual state of the specified page action icon if present on
  // the window.
  virtual void UpdatePageActionIcon(PageActionIconType type) = 0;

  // Returns the AutofillBubbleHandler responsible for handling all
  // Autofill-related bubbles.
  virtual autofill::AutofillBubbleHandler* GetAutofillBubbleHandler() = 0;

  // Executes the action for the specified page action icon.
  virtual void ExecutePageActionIconForTesting(PageActionIconType type) = 0;

  // Returns the location bar.
  virtual LocationBar* GetLocationBar() const = 0;

  // Tries to focus the location bar.  Clears the window focus (to avoid
  // inconsistent state) if this fails.
  virtual void SetFocusToLocationBar(bool select_all) = 0;

  // Informs the view whether or not a load is in progress for the current tab.
  // The view can use this notification to update the reload/stop button.
  virtual void UpdateReloadStopState(bool is_loading, bool force) = 0;

  // Updates the toolbar with the state for the specified |contents|.
  virtual void UpdateToolbar(content::WebContents* contents) = 0;

  // Updates whether or not the custom tab bar is visible. Animates the
  // transition if |animate| is true.
  virtual void UpdateCustomTabBarVisibility(bool visible, bool animate) = 0;

  // Resets the toolbar's tab state for |contents|.
  virtual void ResetToolbarTabState(content::WebContents* contents) = 0;

  // Focuses the toolbar (for accessibility).
  virtual void FocusToolbar() = 0;

  // Returns the ExtensionsContainer associated with the window, if any.
  virtual ExtensionsContainer* GetExtensionsContainer() = 0;

  // Called from toolbar subviews during their show/hide animations.
  virtual void ToolbarSizeChanged(bool is_animating) = 0;

  // Called when the accociated window's tab dragging status changed.
  virtual void TabDraggingStatusChanged(bool is_dragging) = 0;

  // Focuses the app menu like it was a menu bar.
  //
  // Not used on the Mac, which has a "normal" menu bar.
  virtual void FocusAppMenu() = 0;

  // Focuses the bookmarks toolbar (for accessibility).
  virtual void FocusBookmarksToolbar() = 0;

  // Focuses a visible but inactive popup for accessibility.
  virtual void FocusInactivePopupForAccessibility() = 0;

  // Moves keyboard focus to the next pane.
  virtual void RotatePaneFocus(bool forwards) = 0;

  // Returns whether the bookmark bar is visible or not.
  virtual bool IsBookmarkBarVisible() const = 0;

  // Returns whether the bookmark bar is animating or not.
  virtual bool IsBookmarkBarAnimating() const = 0;

  // Returns whether the tab strip is editable (for extensions).
  virtual bool IsTabStripEditable() const = 0;

  // Returns whether the toolbar is available or not. It's called "Visible()"
  // to follow the name convention. But it does not indicate the visibility of
  // the toolbar, i.e. toolbar may be hidden, and only visible when the mouse
  // cursor is at a certain place.
  // TODO(zijiehe): Rename Visible() functions into Available() to match their
  // original meaning.
  virtual bool IsToolbarVisible() const = 0;

  // Returns whether the toolbar is showing up on the screen.
  // TODO(zijiehe): Rename this function into IsToolbarVisible() once other
  // Visible() functions are renamed to Available().
  virtual bool IsToolbarShowing() const = 0;

  // Shows the dialog for a sharing feature.
  virtual SharingDialog* ShowSharingDialog(content::WebContents* contents,
                                           SharingDialogData data) = 0;

  // Shows the Update Recommended dialog box.
  virtual void ShowUpdateChromeDialog() = 0;

  // Shows the intent picker bubble. |app_info| contains the app candidates to
  // display, if |show_stay_in_chrome| is false, the 'Stay in
  // Chrome' (used for non-http(s) queries) button is hidden, if
  // |show_remember_selection| is false, the "remember my choice" checkbox is
  // hidden and |callback| helps to continue the flow back to either
  // AppsNavigationThrottle or ArcExternalProtocolDialog capturing the user's
  // decision and storing UMA metrics.
  virtual void ShowIntentPickerBubble(
      std::vector<apps::IntentPickerAppInfo> app_info,
      bool show_stay_in_chrome,
      bool show_remember_selection,
      PageActionIconType icon_type,
      const base::Optional<url::Origin>& initiating_origin,
      IntentPickerResponse callback) = 0;

  // Shows the Bookmark bubble. |url| is the URL being bookmarked,
  // |already_bookmarked| is true if the url is already bookmarked.
  virtual void ShowBookmarkBubble(const GURL& url, bool already_bookmarked) = 0;

  // Shows the QR Code generator bubble. |url| is the URL for the initial code.
  virtual qrcode_generator::QRCodeGeneratorBubbleView*
  ShowQRCodeGeneratorBubble(
      content::WebContents* contents,
      qrcode_generator::QRCodeGeneratorBubbleController* controller,
      const GURL& url) = 0;

  // Shows the "send tab to self" bubble.
  virtual send_tab_to_self::SendTabToSelfBubbleView* ShowSendTabToSelfBubble(
      content::WebContents* contents,
      send_tab_to_self::SendTabToSelfBubbleController* controller,
      bool is_user_gesture) = 0;

  // Shows the translate bubble.
  //
  // |is_user_gesture| is true when the bubble is shown on the user's deliberate
  // action.
  virtual ShowTranslateBubbleResult ShowTranslateBubble(
      content::WebContents* contents,
      translate::TranslateStep step,
      const std::string& source_language,
      const std::string& target_language,
      translate::TranslateErrors::Type error_type,
      bool is_user_gesture) = 0;

#if BUILDFLAG(ENABLE_ONE_CLICK_SIGNIN)
  // Shows the one-click sign in confirmation UI. |email| holds the full email
  // address of the account that has signed in.
  virtual void ShowOneClickSigninConfirmation(
      const base::string16& email,
      base::OnceCallback<void(bool)> confirmed_callback) = 0;
#endif

  // Whether or not the shelf view is visible.
  virtual bool IsDownloadShelfVisible() const = 0;

  // Returns the DownloadShelf.
  virtual DownloadShelf* GetDownloadShelf() = 0;

  // Shows the confirmation dialog box warning that the browser is closing with
  // in-progress downloads.
  // This method should call |callback| with the user's response.
  virtual void ConfirmBrowserCloseWithPendingDownloads(
      int download_count,
      Browser::DownloadCloseType dialog_type,
      bool app_modal,
      const base::Callback<void(bool)>& callback) = 0;

  // ThemeService calls this when a user has changed their theme, indicating
  // that it's time to redraw everything.
  virtual void UserChangedTheme(BrowserThemeChangeType theme_change_type) = 0;

  // Shows the app menu (for accessibility).
  virtual void ShowAppMenu() = 0;

  // Allows the BrowserWindow object to handle the specified keyboard event
  // before sending it to the renderer.
  virtual content::KeyboardEventProcessingResult PreHandleKeyboardEvent(
      const content::NativeWebKeyboardEvent& event) = 0;

  // Allows the BrowserWindow object to handle the specified keyboard event,
  // if the renderer did not process it.
  virtual bool HandleKeyboardEvent(
      const content::NativeWebKeyboardEvent& event) = 0;

  // Clipboard commands applied to the whole browser window.
  virtual void CutCopyPaste(int command_id) = 0;

  // Construct a FindBar implementation for the |browser|.
  virtual std::unique_ptr<FindBar> CreateFindBar() = 0;

  // Return the WebContentsModalDialogHost for use in positioning web contents
  // modal dialogs within the browser window. This can sometimes be NULL (for
  // instance during tab drag on Views/Win32).
  virtual web_modal::WebContentsModalDialogHost*
  GetWebContentsModalDialogHost() = 0;

  // Construct a BrowserWindow implementation for the specified |browser|.
  static BrowserWindow* CreateBrowserWindow(std::unique_ptr<Browser> browser,
                                            bool user_gesture,
                                            bool in_tab_dragging);

  // Shows the avatar bubble on the window frame off of the avatar button with
  // the given mode. The Service Type specified by GAIA is provided as well.
  // |access_point| indicates the access point used to open the Gaia sign in
  // page.
  enum AvatarBubbleMode {
    AVATAR_BUBBLE_MODE_DEFAULT,
    AVATAR_BUBBLE_MODE_SIGNIN,
    AVATAR_BUBBLE_MODE_ADD_ACCOUNT,
    AVATAR_BUBBLE_MODE_REAUTH,
    AVATAR_BUBBLE_MODE_CONFIRM_SIGNIN
  };
  virtual void ShowAvatarBubbleFromAvatarButton(
      AvatarBubbleMode mode,
      signin_metrics::AccessPoint access_point,
      bool is_source_keyboard) = 0;

  // Shows User Happiness Tracking Survey's invitation bubble when possible
  // (such as having the proper anchor view).
  // |site_id| is the site identification of the survey the bubble leads to.
  virtual void ShowHatsBubble(const std::string& site_id) = 0;

  // Executes |command| registered by |extension|.
  virtual void ExecuteExtensionCommand(const extensions::Extension* extension,
                                       const extensions::Command& command) = 0;

  // Returns object implementing ExclusiveAccessContext interface.
  virtual ExclusiveAccessContext* GetExclusiveAccessContext() = 0;

  // Shows the IME warning bubble.
  virtual void ShowImeWarningBubble(
      const extensions::Extension* extension,
      const base::Callback<void(ImeWarningBubblePermissionStatus status)>&
          callback) = 0;

  // Shows in-product help for the given feature.
  virtual void ShowInProductHelpPromo(InProductHelpFeature iph_feature) = 0;

  // Returns the platform-specific ID of the workspace the browser window
  // currently resides in.
  virtual std::string GetWorkspace() const = 0;
  virtual bool IsVisibleOnAllWorkspaces() const = 0;

  // Shows the platform specific emoji picker.
  virtual void ShowEmojiPanel() = 0;

  // Opens the eye dropper.
  virtual std::unique_ptr<content::EyeDropper> OpenEyeDropper(
      content::RenderFrameHost* frame,
      content::EyeDropperListener* listener) = 0;

 protected:
  friend class BrowserCloseManager;
  friend class BrowserView;
  virtual void DestroyBrowser() = 0;
};

#endif  // CHROME_BROWSER_UI_BROWSER_WINDOW_H_

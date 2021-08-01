// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

import 'chrome://resources/cr_elements/cr_button/cr_button.m.js';
import 'chrome://resources/cr_elements/cr_icon_button/cr_icon_button.m.js';
import './grid.js';
import './theme_icon.js';

import {html, PolymerElement} from 'chrome://resources/polymer/v3_0/polymer/polymer_bundled.min.js';

import {BrowserProxy} from './browser_proxy.js';
import {hexColorToSkColor, skColorToRgba} from './utils.js';

/** Element that lets the user configure the theme. */
class CustomizeThemesElement extends PolymerElement {
  static get is() {
    return 'ntp-customize-themes';
  }

  static get template() {
    return html`{__html_template__}`;
  }

  static get properties() {
    return {
      /** @type {!newTabPage.mojom.Theme} */
      theme: {
        type: Object,
        observer: 'onThemeChange_',
      },

      /** @private {!Array<!newTabPage.mojom.ChromeTheme>} */
      chromeThemes_: Array,
    };
  }

  constructor() {
    super();
    /** @private {newTabPage.mojom.PageHandlerRemote} */
    this.pageHandler_ = BrowserProxy.getInstance().handler;
    this.pageHandler_.getChromeThemes().then(({chromeThemes}) => {
      this.chromeThemes_ = chromeThemes;
    });
  }

  /**
   * @param {!Event} e
   * @private
   */
  onCustomFrameColorChange_(e) {
    this.pageHandler_.applyAutogeneratedTheme(
        hexColorToSkColor(e.target.value));
  }

  /** @private */
  onAutogeneratedThemeClick_() {
    this.$.colorPicker.click();
  }

  /** @private */
  onDefaultThemeClick_() {
    this.pageHandler_.applyDefaultTheme();
  }

  /**
   * @param {!Event} e
   * @private
   */
  onChromeThemeClick_(e) {
    this.pageHandler_.applyChromeTheme(
        this.$.themes.itemForElement(e.target).id);
  }

  /** private */
  onThemeChange_() {
    if (this.theme.type !== newTabPage.mojom.ThemeType.AUTOGENERATED) {
      return;
    }
    const rgbaFrameColor =
        skColorToRgba(this.theme.info.autogeneratedThemeColors.frame);
    const rgbaActiveTabColor =
        skColorToRgba(this.theme.info.autogeneratedThemeColors.activeTab);
    this.$.autogeneratedTheme.style.setProperty(
        '--ntp-theme-icon-frame-color', rgbaFrameColor);
    this.$.autogeneratedTheme.style.setProperty(
        '--ntp-theme-icon-stroke-color', rgbaFrameColor);
    this.$.autogeneratedTheme.style.setProperty(
        '--ntp-theme-icon-active-tab-color', rgbaActiveTabColor);
    this.$.colorPickerIcon.style.setProperty(
        'background-color', skColorToRgba(this.theme.shortcutTextColor));
  }

  /**
   * @param {!Event} e
   * @private
   */
  onUninstallThirdPartyThemeClick_(e) {
    this.pageHandler_.applyDefaultTheme();
    this.pageHandler_.confirmThemeChanges();
  }

  /**
   * @param {string|number} id
   * @return {boolean}
   * @private
   */
  isThemeIconSelected_(id) {
    if (!this.theme) {
      return false;
    }
    if (id === 'autogenerated') {
      return this.theme.type === newTabPage.mojom.ThemeType.AUTOGENERATED;
    } else if (id === 'default') {
      return this.theme.type === newTabPage.mojom.ThemeType.DEFAULT;
    } else {
      return this.theme.type === newTabPage.mojom.ThemeType.CHROME &&
          id === this.theme.info.chromeThemeId;
    }
  }

  /**
   * @return {boolean}
   * @private
   */
  isThirdPartyTheme_() {
    return this.theme.type === newTabPage.mojom.ThemeType.THIRD_PARTY;
  }

  /** @private */
  onThirdPartyLinkButtonClick_() {
    BrowserProxy.getInstance().open(
        `https://chrome.google.com/webstore/detail/${
            this.theme.info.thirdPartyThemeInfo.id}`);
  }

  /**
   * @param {skia.mojom.SkColor} skColor
   * @return {string}
   * @private
   */
  skColorToRgba_(skColor) {
    return skColorToRgba(skColor);
  }
}

customElements.define(CustomizeThemesElement.is, CustomizeThemesElement);

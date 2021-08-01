// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// SettingsAccessibilityV3Test fixture.
GEN_INCLUDE([
  'settings_accessibility_v3_test.js',
]);

GEN('#include "build/branding_buildflags.h"');
GEN('#include "chrome/common/chrome_features.h"');
GEN('#include "content/public/test/browser_test.h"');

// TODO(crbug.com/1002627): This block prevents generation of a
// link-in-text-block browser-test. This can be removed once the bug is
// addressed, and usage should be replaced with
// SettingsAccessibilityV3Test.axeOptions
const axeOptionsExcludeLinkInTextBlock =
    Object.assign({}, SettingsAccessibilityV3Test.axeOptions, {
      'rules': Object.assign({}, SettingsAccessibilityV3Test.axeOptions.rules, {
        'link-in-text-block': {enabled: false},
      })
    });

const violationFilterExcludeCustomInputAndTabindex =
    Object.assign({}, SettingsAccessibilityV3Test.violationFilter, {
      // Excuse custom input elements.
      'aria-valid-attr-value': function(nodeResult) {
        const describerId = nodeResult.element.getAttribute('aria-describedby');
        return describerId === '' && nodeResult.element.tagName === 'INPUT';
      },
      'tabindex': function(nodeResult) {
        // TODO(crbug.com/808276): remove this exception when bug is fixed.
        return nodeResult.element.getAttribute('tabindex') === '0';
      },
    });

[[
  'About', 'about_a11y_v3_test.js', {options: axeOptionsExcludeLinkInTextBlock}
],
 ['Accessibility', 'accessibility_a11y_v3_test.js'],
 ['Basic', 'basic_a11y_v3_test.js'],
 ['Passwords', 'passwords_a11y_v3_test.js'],
].forEach(test => defineTest(...test));

GEN('#if !defined(OS_CHROMEOS)');
[[
  'ManageProfile', 'manage_profile_a11y_v3_test.js',
  {filter: violationFilterExcludeCustomInputAndTabindex}
],
 ['Signout', 'sign_out_a11y_v3_test.js'],
].forEach(test => defineTest(...test));
GEN('#endif  // !defined(OS_CHROMEOS)');

// Disable since the EDIT_DICTIONARY route does not exist on Mac.
// TODO(crbug.com/1012370) flaky on Linux b/c assertTrue(!!languagesPage);
// TODO(crbug.com/1012370) flaky on Win the same way
GEN('#if !defined(OS_MACOSX) && !defined(OS_LINUX) && !defined(OS_WIN)');
defineTest(
    'EditDictionary', 'edit_dictionary_a11y_v3_test.js',
    {filter: violationFilterExcludeCustomInputAndTabindex});
GEN('#endif');

function defineTest(testName, module, config) {
  const className = `SettingsA11y${testName}V3`;
  this[className] = class extends SettingsAccessibilityV3Test {
    /** @override */
    get browsePreload() {
      return `chrome://settings/test_loader.html?module=settings/a11y/${
          module}`;
    }

    /** @override */
    get featureListInternal() {
      return {disabled: ['features::kPrivacySettingsRedesign']};
    }
  };

  const filter = config && config.filter ?
      config.filter :
      SettingsAccessibilityV3Test.violationFilter;
  const options = config && config.options ?
      config.options :
      SettingsAccessibilityV3Test.axeOptions;
  AccessibilityTest.define(className, {
    /** @override */
    name: testName,
    /** @override */
    axeOptions: options,
    /** @override */
    tests: {'All': function() {}},
    /** @override */
    violationFilter: filter,
  });
}

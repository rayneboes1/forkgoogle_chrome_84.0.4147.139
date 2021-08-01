// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/search/chrome_colors/chrome_colors_service.h"
#include "base/strings/utf_string_conversions.h"
#include "chrome/browser/search/chrome_colors/chrome_colors_factory.h"
#include "chrome/browser/search_engines/template_url_service_factory.h"
#include "chrome/browser/themes/theme_service.h"
#include "chrome/browser/themes/theme_service_factory.h"
#include "chrome/browser/ui/search/local_ntp_test_utils.h"
#include "chrome/test/base/browser_with_test_window_test.h"
#include "chrome/test/base/search_test_utils.h"
#include "components/search_engines/template_url.h"
#include "components/search_engines/template_url_data.h"
#include "components/search_engines/template_url_service.h"
#include "content/public/test/web_contents_tester.h"
#include "testing/gtest/include/gtest/gtest.h"

class TestChromeColorsService : public BrowserWithTestWindowTest {
 protected:
  TestChromeColorsService() {}

  void SetUp() override {
    BrowserWithTestWindowTest::SetUp();

    template_url_service_ = TemplateURLServiceFactory::GetForProfile(profile());
    search_test_utils::WaitForTemplateURLServiceToLoad(template_url_service_);

    chrome_colors_service_ =
        chrome_colors::ChromeColorsFactory::GetForProfile(profile());

    AddTab(browser(), GURL("chrome://newtab"));
    tab_ = browser()->tab_strip_model()->GetActiveWebContents();
  }

  bool HasThemeReinstaller() {
    return !!chrome_colors_service_->prev_theme_reinstaller_;
  }

  void SetUserSelectedDefaultSearchProvider(const std::string& base_url) {
    TemplateURLData data;
    data.SetShortName(base::UTF8ToUTF16(base_url));
    data.SetKeyword(base::UTF8ToUTF16(base_url));
    data.SetURL(base_url + "url?bar={searchTerms}");
    data.new_tab_url = base_url + "newtab";
    data.alternate_urls.push_back(base_url + "alt#quux={searchTerms}");

    TemplateURL* template_url =
        template_url_service_->Add(std::make_unique<TemplateURL>(data));
    template_url_service_->SetUserSelectedDefaultSearchProvider(template_url);
  }

  chrome_colors::ChromeColorsService* chrome_colors_service_;
  content::WebContents* tab_;

 private:
  // BrowserWithTestWindowTest override:
  TestingProfile* CreateProfile() override {
    TestingProfile* profile = BrowserWithTestWindowTest::CreateProfile();
    TemplateURLServiceFactory::GetInstance()->SetTestingFactoryAndUse(
        profile,
        base::BindRepeating(&TemplateURLServiceFactory::BuildInstanceFor));
    return profile;
  }

  TemplateURLService* template_url_service_;
};

TEST_F(TestChromeColorsService, ApplyAndConfirmAutogeneratedTheme) {
  ThemeService* theme_service = ThemeServiceFactory::GetForProfile(profile());
  ASSERT_TRUE(theme_service->UsingDefaultTheme());

  SkColor theme_color1 = SkColorSetRGB(100, 0, 200);
  chrome_colors_service_->ApplyAutogeneratedTheme(theme_color1, tab_);
  EXPECT_TRUE(theme_service->UsingAutogeneratedTheme());
  EXPECT_TRUE(HasThemeReinstaller());

  SkColor theme_color2 = SkColorSetRGB(100, 0, 200);
  chrome_colors_service_->ApplyAutogeneratedTheme(theme_color2, tab_);
  EXPECT_TRUE(theme_service->UsingAutogeneratedTheme());
  EXPECT_TRUE(HasThemeReinstaller());

  // Last color is saved.
  chrome_colors_service_->ConfirmThemeChanges();
  EXPECT_TRUE(theme_service->UsingAutogeneratedTheme());
  EXPECT_EQ(theme_color2, theme_service->GetAutogeneratedThemeColor());
  EXPECT_FALSE(HasThemeReinstaller());
}

TEST_F(TestChromeColorsService, ApplyAndRevertAutogeneratedTheme) {
  ThemeService* theme_service = ThemeServiceFactory::GetForProfile(profile());
  ASSERT_TRUE(theme_service->UsingDefaultTheme());

  SkColor theme_color1 = SkColorSetRGB(100, 0, 200);
  chrome_colors_service_->ApplyAutogeneratedTheme(theme_color1, tab_);
  EXPECT_TRUE(theme_service->UsingAutogeneratedTheme());
  EXPECT_TRUE(HasThemeReinstaller());

  SkColor theme_color2 = SkColorSetRGB(100, 0, 200);
  chrome_colors_service_->ApplyAutogeneratedTheme(theme_color2, tab_);
  EXPECT_TRUE(theme_service->UsingAutogeneratedTheme());
  EXPECT_TRUE(HasThemeReinstaller());

  // State before first apply is restored.
  chrome_colors_service_->RevertThemeChanges();
  EXPECT_FALSE(theme_service->UsingAutogeneratedTheme());
  EXPECT_FALSE(HasThemeReinstaller());
}

TEST_F(TestChromeColorsService,
       ApplyAndConfirmAutogeneratedTheme_withPreviousTheme) {
  ThemeService* theme_service = ThemeServiceFactory::GetForProfile(profile());
  SkColor prev_theme_color = SkColorSetRGB(200, 0, 200);
  theme_service->BuildAutogeneratedThemeFromColor(prev_theme_color);
  ASSERT_EQ(prev_theme_color, theme_service->GetAutogeneratedThemeColor());

  SkColor new_theme_color = SkColorSetRGB(100, 0, 200);
  chrome_colors_service_->ApplyAutogeneratedTheme(new_theme_color, tab_);
  EXPECT_EQ(new_theme_color, theme_service->GetAutogeneratedThemeColor());
  EXPECT_TRUE(HasThemeReinstaller());

  chrome_colors_service_->ConfirmThemeChanges();
  EXPECT_TRUE(theme_service->UsingAutogeneratedTheme());
  EXPECT_EQ(new_theme_color, theme_service->GetAutogeneratedThemeColor());
  EXPECT_FALSE(HasThemeReinstaller());
}

TEST_F(TestChromeColorsService,
       ApplyAndRevertAutogeneratedTheme_withPreviousTheme) {
  ThemeService* theme_service = ThemeServiceFactory::GetForProfile(profile());
  SkColor prev_theme_color = SkColorSetRGB(200, 0, 200);
  theme_service->BuildAutogeneratedThemeFromColor(prev_theme_color);
  ASSERT_EQ(prev_theme_color, theme_service->GetAutogeneratedThemeColor());

  SkColor new_theme_color = SkColorSetRGB(100, 0, 200);
  chrome_colors_service_->ApplyAutogeneratedTheme(new_theme_color, tab_);
  EXPECT_EQ(new_theme_color, theme_service->GetAutogeneratedThemeColor());
  EXPECT_TRUE(HasThemeReinstaller());

  chrome_colors_service_->RevertThemeChanges();
  EXPECT_TRUE(theme_service->UsingAutogeneratedTheme());
  EXPECT_EQ(prev_theme_color, theme_service->GetAutogeneratedThemeColor());
  EXPECT_FALSE(HasThemeReinstaller());
}

TEST_F(TestChromeColorsService, ApplyAndConfirmDefaultTheme_withPreviousTheme) {
  ThemeService* theme_service = ThemeServiceFactory::GetForProfile(profile());
  SkColor prev_theme_color = SkColorSetRGB(200, 0, 200);
  theme_service->BuildAutogeneratedThemeFromColor(prev_theme_color);
  ASSERT_EQ(prev_theme_color, theme_service->GetAutogeneratedThemeColor());
  ASSERT_FALSE(theme_service->UsingDefaultTheme());

  chrome_colors_service_->ApplyDefaultTheme(tab_);
  EXPECT_TRUE(theme_service->UsingDefaultTheme());
  EXPECT_TRUE(HasThemeReinstaller());

  chrome_colors_service_->ConfirmThemeChanges();
  EXPECT_TRUE(theme_service->UsingDefaultTheme());
  EXPECT_NE(prev_theme_color, theme_service->GetAutogeneratedThemeColor());
  EXPECT_FALSE(HasThemeReinstaller());
}

TEST_F(TestChromeColorsService, ApplyAndRevertDefaultTheme_withPreviousTheme) {
  ThemeService* theme_service = ThemeServiceFactory::GetForProfile(profile());
  SkColor prev_theme_color = SkColorSetRGB(200, 0, 200);
  theme_service->BuildAutogeneratedThemeFromColor(prev_theme_color);
  ASSERT_EQ(prev_theme_color, theme_service->GetAutogeneratedThemeColor());
  ASSERT_FALSE(theme_service->UsingDefaultTheme());

  chrome_colors_service_->ApplyDefaultTheme(tab_);
  EXPECT_TRUE(theme_service->UsingDefaultTheme());
  EXPECT_TRUE(HasThemeReinstaller());

  chrome_colors_service_->RevertThemeChanges();
  EXPECT_FALSE(theme_service->UsingDefaultTheme());
  EXPECT_EQ(prev_theme_color, theme_service->GetAutogeneratedThemeColor());
  EXPECT_FALSE(HasThemeReinstaller());
}

TEST_F(TestChromeColorsService, RevertThemeChangesForTab) {
  ThemeService* theme_service = ThemeServiceFactory::GetForProfile(profile());
  ASSERT_TRUE(theme_service->UsingDefaultTheme());

  SkColor theme_color = SkColorSetRGB(100, 0, 200);
  chrome_colors_service_->ApplyAutogeneratedTheme(theme_color, tab_);
  EXPECT_TRUE(theme_service->UsingAutogeneratedTheme());
  EXPECT_TRUE(HasThemeReinstaller());

  chrome_colors_service_->RevertThemeChangesForTab(
      nullptr, chrome_colors::RevertReason::TAB_CLOSED);
  EXPECT_TRUE(theme_service->UsingAutogeneratedTheme());
  EXPECT_TRUE(HasThemeReinstaller());

  AddTab(browser(), GURL("chrome://newtab"));
  content::WebContents* second_tab =
      browser()->tab_strip_model()->GetActiveWebContents();
  ASSERT_NE(tab_, second_tab);
  chrome_colors_service_->RevertThemeChangesForTab(
      second_tab, chrome_colors::RevertReason::TAB_CLOSED);
  EXPECT_TRUE(theme_service->UsingAutogeneratedTheme());
  EXPECT_TRUE(HasThemeReinstaller());

  chrome_colors_service_->RevertThemeChangesForTab(
      tab_, chrome_colors::RevertReason::TAB_CLOSED);
  EXPECT_FALSE(theme_service->UsingAutogeneratedTheme());
  EXPECT_FALSE(HasThemeReinstaller());
}

TEST_F(TestChromeColorsService, RevertThemeChangesWhenSwitchToThirdPartyNTP) {
  ThemeService* theme_service = ThemeServiceFactory::GetForProfile(profile());
  ASSERT_TRUE(theme_service->UsingDefaultTheme());

  SkColor theme_color = SkColorSetRGB(100, 0, 200);
  chrome_colors_service_->ApplyAutogeneratedTheme(theme_color, tab_);
  EXPECT_TRUE(theme_service->UsingAutogeneratedTheme());
  EXPECT_TRUE(HasThemeReinstaller());

  // Switching to third-party NTP should revert current changes.
  SetUserSelectedDefaultSearchProvider("www.third-party-ntp.com");
  EXPECT_FALSE(theme_service->UsingAutogeneratedTheme());
  EXPECT_FALSE(HasThemeReinstaller());

  // When third-party NTP is present autogenerated theme shouldn't apply.
  chrome_colors_service_->ApplyAutogeneratedTheme(theme_color, tab_);
  EXPECT_FALSE(theme_service->UsingAutogeneratedTheme());
  EXPECT_FALSE(HasThemeReinstaller());
}

// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/supervised_user/logged_in_user_mixin.h"

#include <vector>

#include "chromeos/constants/chromeos_switches.h"
#include "chromeos/login/auth/stub_authenticator_builder.h"
#include "chromeos/login/auth/user_context.h"
#include "components/account_id/account_id.h"
#include "net/dns/mock_host_resolver.h"

namespace chromeos {

namespace {

user_manager::UserType ConvertUserType(LoggedInUserMixin::LogInType type) {
  switch (type) {
    case LoggedInUserMixin::LogInType::kChild:
      return user_manager::USER_TYPE_CHILD;
    case LoggedInUserMixin::LogInType::kRegular:
      return user_manager::USER_TYPE_REGULAR;
  }
}

std::vector<LoginManagerMixin::TestUserInfo> GetInitialUsers(
    const LoginManagerMixin::TestUserInfo& user,
    bool include_initial_user) {
  if (include_initial_user)
    return {user};
  return {};
}

}  // namespace

LoggedInUserMixin::LoggedInUserMixin(
    InProcessBrowserTestMixinHost* mixin_host,
    LogInType type,
    net::EmbeddedTestServer* embedded_test_server,
    InProcessBrowserTest* test_base,
    bool should_launch_browser,
    base::Optional<AccountId> account_id,
    bool include_initial_user)
    : InProcessBrowserTestMixin(mixin_host),
      user_(account_id.value_or(
                AccountId::FromUserEmailGaiaId(FakeGaiaMixin::kFakeUserEmail,
                                               FakeGaiaMixin::kFakeUserGaiaId)),
            ConvertUserType(type)),
      login_manager_(mixin_host, GetInitialUsers(user_, include_initial_user)),
      local_policy_server_(mixin_host),
      user_policy_(mixin_host, user_.account_id, &local_policy_server_),
      user_policy_helper_(user_.account_id.GetUserEmail(),
                          &local_policy_server_),
      embedded_test_server_setup_(mixin_host, embedded_test_server),
      fake_gaia_(mixin_host, embedded_test_server),
      test_base_(test_base) {
  // By default, LoginManagerMixin will set up user session manager not to
  // launch browser as part of user session setup - use this to override that
  // behavior.
  login_manager_.set_should_launch_browser(should_launch_browser);
}

LoggedInUserMixin::~LoggedInUserMixin() = default;

void LoggedInUserMixin::SetUpOnMainThread() {
  // By default, browser tests block anything that doesn't go to localhost, so
  // account.google.com requests would never reach fake GAIA server without
  // this.
  test_base_->host_resolver()->AddRule("*", "127.0.0.1");
}

void LoggedInUserMixin::LogInUser(bool issue_any_scope_token,
                                  bool wait_for_active_session,
                                  bool request_policy_update) {
  UserContext user_context = LoginManagerMixin::CreateDefaultUserContext(user_);
  user_context.SetRefreshToken(FakeGaiaMixin::kFakeRefreshToken);
  if (user_.user_type == user_manager::USER_TYPE_CHILD) {
    fake_gaia_.SetupFakeGaiaForChildUser(
        user_.account_id.GetUserEmail(), user_.account_id.GetGaiaId(),
        FakeGaiaMixin::kFakeRefreshToken, issue_any_scope_token);
  } else {
    fake_gaia_.SetupFakeGaiaForLogin(user_.account_id.GetUserEmail(),
                                     user_.account_id.GetGaiaId(),
                                     FakeGaiaMixin::kFakeRefreshToken);
  }
  if (request_policy_update) {
    // Set up policy, which prevents the call to LoginAndWaitForActiveSession()
    // below from hanging indefinitely in some test scenarios.
    GetUserPolicyMixin()->RequestPolicyUpdate();
  }
  if (wait_for_active_session) {
    login_manager_.LoginAndWaitForActiveSession(user_context);
    // Set the private |browser_| member in InProcessBrowserTest.
    // Otherwise calls to InProcessBrowserTest::browser() returns null and leads
    // to segmentation faults.
    // Note: |browser_| is only non-null if should_launch_browser was set to
    // true in the constructor.
    test_base_->SelectFirstBrowser();
  } else {
    login_manager_.AttemptLoginUsingAuthenticator(
        user_context, std::make_unique<StubAuthenticatorBuilder>(user_context));
  }
}

}  // namespace chromeos

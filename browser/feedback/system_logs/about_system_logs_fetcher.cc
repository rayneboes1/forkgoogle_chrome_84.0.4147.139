// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/feedback/system_logs/about_system_logs_fetcher.h"

#include <memory>

#include "build/build_config.h"
#include "chrome/browser/feedback/system_logs/log_sources/chrome_internal_log_source.h"
#include "chrome/browser/feedback/system_logs/log_sources/memory_details_log_source.h"
#include "components/feedback/system_logs/system_logs_fetcher.h"

#if defined(OS_CHROMEOS)
#include "chrome/browser/chromeos/system_logs/command_line_log_source.h"
#include "chrome/browser/chromeos/system_logs/dbus_log_source.h"
#include "chrome/browser/chromeos/system_logs/debug_daemon_log_source.h"
#include "chrome/browser/chromeos/system_logs/device_event_log_source.h"
#include "chrome/browser/chromeos/system_logs/touch_log_source.h"
#include "chrome/browser/chromeos/system_logs/ui_hierarchy_log_source.h"
#endif

namespace system_logs {

SystemLogsFetcher* BuildAboutSystemLogsFetcher() {
  const bool scrub_data = false;
  // We aren't anonymizing, so we can pass null for the 1st party IDs.
  SystemLogsFetcher* fetcher = new SystemLogsFetcher(scrub_data, nullptr);

  fetcher->AddSource(std::make_unique<ChromeInternalLogSource>());
  fetcher->AddSource(std::make_unique<MemoryDetailsLogSource>());

#if defined(OS_CHROMEOS)
  fetcher->AddSource(std::make_unique<CommandLineLogSource>());
  fetcher->AddSource(std::make_unique<DBusLogSource>());
  fetcher->AddSource(std::make_unique<DeviceEventLogSource>());
  fetcher->AddSource(std::make_unique<TouchLogSource>());

  // Debug Daemon data source - currently only this data source supports
  // the scrub_data parameter.
  fetcher->AddSource(std::make_unique<DebugDaemonLogSource>(scrub_data));
  fetcher->AddSource(std::make_unique<UiHierarchyLogSource>(scrub_data));
#endif

  return fetcher;
}

}  // namespace system_logs

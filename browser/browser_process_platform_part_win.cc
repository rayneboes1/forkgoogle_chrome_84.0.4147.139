// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/browser_process_platform_part_win.h"

#include "chrome/browser/active_use_util.h"
#include "chrome/browser/google/did_run_updater_win.h"

BrowserProcessPlatformPart::BrowserProcessPlatformPart() = default;
BrowserProcessPlatformPart::~BrowserProcessPlatformPart() = default;

void BrowserProcessPlatformPart::PlatformSpecificCommandLineProcessing(
    const base::CommandLine& command_line) {
  if (!did_run_updater_ && ShouldRecordActiveUse(command_line))
    did_run_updater_ = std::make_unique<DidRunUpdater>();
}

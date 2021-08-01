// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/shell_integration_linux.h"

#include <fcntl.h>
#include <stddef.h>
#include <stdlib.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#include <memory>
#include <sstream>
#include <string>
#include <utility>
#include <vector>

#include "base/base_paths.h"
#include "base/command_line.h"
#include "base/environment.h"
#include "base/files/file_enumerator.h"
#include "base/files/file_path.h"
#include "base/files/file_util.h"
#include "base/i18n/file_util_icu.h"
#include "base/memory/ref_counted_memory.h"
#include "base/nix/xdg_util.h"
#include "base/path_service.h"
#include "base/posix/eintr_wrapper.h"
#include "base/process/kill.h"
#include "base/process/launch.h"
#include "base/strings/string_number_conversions.h"
#include "base/strings/string_tokenizer.h"
#include "base/strings/string_util.h"
#include "base/strings/stringprintf.h"
#include "base/strings/utf_string_conversions.h"
#include "base/threading/scoped_blocking_call.h"
#include "base/threading/thread.h"
#include "base/threading/thread_restrictions.h"
#include "build/branding_buildflags.h"
#include "build/build_config.h"
#include "chrome/browser/shell_integration.h"
#include "chrome/common/buildflags.h"
#include "chrome/common/channel_info.h"
#include "chrome/common/chrome_constants.h"
#include "chrome/common/chrome_switches.h"
#include "chrome/grit/chrome_unscaled_resources.h"
#include "components/version_info/version_info.h"
#include "ui/base/resource/resource_bundle.h"
#include "ui/gfx/image/image_family.h"
#include "url/gurl.h"

#if defined(USE_GLIB)
#include <glib.h>
#endif

namespace shell_integration_linux {

const char kXdgSettings[] = "xdg-settings";
const char kXdgSettingsDefaultBrowser[] = "default-web-browser";
const char kXdgSettingsDefaultSchemeHandler[] = "default-url-scheme-handler";

// Utility function to get the path to the version of a script shipped with
// Chrome. |script| gives the name of the script. |chrome_version| returns the
// path to the Chrome version of the script, and the return value of the
// function is true if the function is successful and the Chrome version is
// not the script found on the PATH.
bool GetChromeVersionOfScript(const std::string& script,
                              std::string* chrome_version) {
  // Get the path to the Chrome version.
  base::FilePath chrome_dir;
  if (!base::PathService::Get(base::DIR_EXE, &chrome_dir))
    return false;

  base::FilePath chrome_version_path = chrome_dir.Append(script);
  *chrome_version = chrome_version_path.value();

  // Check if this is different to the one on path.
  std::vector<std::string> argv;
  argv.push_back("which");
  argv.push_back(script);
  std::string path_version;
  if (base::GetAppOutput(base::CommandLine(argv), &path_version)) {
    // Remove trailing newline
    path_version.erase(path_version.length() - 1, 1);
    base::FilePath path_version_path(path_version);
    return (chrome_version_path != path_version_path);
  }
  return false;
}

// Value returned by xdg-settings if it can't understand our request.
const int EXIT_XDG_SETTINGS_SYNTAX_ERROR = 1;

// We delegate the difficulty of setting the default browser and default url
// scheme handler in Linux desktop environments to an xdg utility, xdg-settings.

// When calling this script we first try to use the script on PATH. If that
// fails we then try to use the script that we have included. This gives
// scripts on the system priority over ours, as distribution vendors may have
// tweaked the script, but still allows our copy to be used if the script on the
// system fails, as the system copy may be missing capabilities of the Chrome
// copy.

// If |protocol| is empty this function sets Chrome as the default browser,
// otherwise it sets Chrome as the default handler application for |protocol|.
bool SetDefaultWebClient(const std::string& protocol) {
#if defined(OS_CHROMEOS)
  return true;
#else
  std::unique_ptr<base::Environment> env(base::Environment::Create());

  std::vector<std::string> argv;
  argv.push_back(kXdgSettings);
  argv.push_back("set");
  if (protocol.empty()) {
    argv.push_back(kXdgSettingsDefaultBrowser);
  } else {
    argv.push_back(kXdgSettingsDefaultSchemeHandler);
    argv.push_back(protocol);
  }
  argv.push_back(chrome::GetDesktopName(env.get()));

  int exit_code;
  bool ran_ok = LaunchXdgUtility(argv, &exit_code);
  if (ran_ok && exit_code == EXIT_XDG_SETTINGS_SYNTAX_ERROR) {
    if (GetChromeVersionOfScript(kXdgSettings, &argv[0])) {
      ran_ok = LaunchXdgUtility(argv, &exit_code);
    }
  }

  return ran_ok && exit_code == EXIT_SUCCESS;
#endif
}

// If |protocol| is empty this function checks if Chrome is the default browser,
// otherwise it checks if Chrome is the default handler application for
// |protocol|.
shell_integration::DefaultWebClientState GetIsDefaultWebClient(
    const std::string& protocol) {
#if defined(OS_CHROMEOS)
  return shell_integration::UNKNOWN_DEFAULT;
#else
  base::ScopedBlockingCall scoped_blocking_call(FROM_HERE,
                                                base::BlockingType::MAY_BLOCK);

  std::unique_ptr<base::Environment> env(base::Environment::Create());

  std::vector<std::string> argv;
  argv.push_back(kXdgSettings);
  argv.push_back("check");
  if (protocol.empty()) {
    argv.push_back(kXdgSettingsDefaultBrowser);
  } else {
    argv.push_back(kXdgSettingsDefaultSchemeHandler);
    argv.push_back(protocol);
  }
  argv.push_back(chrome::GetDesktopName(env.get()));

  std::string reply;
  int success_code;
  bool ran_ok = base::GetAppOutputWithExitCode(base::CommandLine(argv), &reply,
                                               &success_code);
  if (ran_ok && success_code == EXIT_XDG_SETTINGS_SYNTAX_ERROR) {
    if (GetChromeVersionOfScript(kXdgSettings, &argv[0])) {
      ran_ok = base::GetAppOutputWithExitCode(base::CommandLine(argv), &reply,
                                              &success_code);
    }
  }

  if (!ran_ok || success_code != EXIT_SUCCESS) {
    // xdg-settings failed: we can't determine or set the default browser.
    return shell_integration::UNKNOWN_DEFAULT;
  }

  // Allow any reply that starts with "yes".
  return base::StartsWith(reply, "yes", base::CompareCase::SENSITIVE)
             ? shell_integration::IS_DEFAULT
             : shell_integration::NOT_DEFAULT;
#endif
}

// https://wiki.gnome.org/Projects/GnomeShell/ApplicationBased
// The WM_CLASS property should be set to the same as the *.desktop file without
// the .desktop extension.  We cannot simply use argv[0] in this case, because
// on the stable channel, the executable name is google-chrome-stable, but the
// desktop file is google-chrome.desktop.
std::string GetDesktopBaseName(const std::string& desktop_file_name) {
  static const char kDesktopExtension[] = ".desktop";
  if (base::EndsWith(desktop_file_name, kDesktopExtension,
                     base::CompareCase::SENSITIVE)) {
    return desktop_file_name.substr(
        0, desktop_file_name.length() - strlen(kDesktopExtension));
  }
  return desktop_file_name;
}

namespace {

#if defined(USE_GLIB)
// Quote a string such that it appears as one verbatim argument for the Exec
// key in a desktop file.
std::string QuoteArgForDesktopFileExec(const std::string& arg) {
  // http://standards.freedesktop.org/desktop-entry-spec/latest/ar01s06.html

  // Quoting is only necessary if the argument has a reserved character.
  if (arg.find_first_of(" \t\n\"'\\><~|&;$*?#()`") == std::string::npos)
    return arg;  // No quoting necessary.

  std::string quoted = "\"";
  for (size_t i = 0; i < arg.size(); ++i) {
    // Note that the set of backslashed characters is smaller than the
    // set of reserved characters.
    switch (arg[i]) {
      case '"':
      case '`':
      case '$':
      case '\\':
        quoted += '\\';
        break;
    }
    quoted += arg[i];
  }
  quoted += '"';

  return quoted;
}

// Quote a command line so it is suitable for use as the Exec key in a desktop
// file. Note: This should be used instead of GetCommandLineString, which does
// not properly quote the string; this function is designed for the Exec key.
std::string QuoteCommandLineForDesktopFileExec(
    const base::CommandLine& command_line) {
  // http://standards.freedesktop.org/desktop-entry-spec/latest/ar01s06.html

  std::string quoted_path;
  const base::CommandLine::StringVector& argv = command_line.argv();
  for (auto i = argv.begin(); i != argv.end(); ++i) {
    if (i != argv.begin())
      quoted_path += " ";
    quoted_path += QuoteArgForDesktopFileExec(*i);
  }

  return quoted_path;
}
#endif

#if defined(USE_GLIB)
const char kDesktopEntry[] = "Desktop Entry";
const char kXdgOpenShebang[] = "#!/usr/bin/env xdg-open";
#endif

}  // namespace

// Allows LaunchXdgUtility to join a process.
// thread_restrictions.h assumes it to be in shell_integration_linux namespace.
class LaunchXdgUtilityScopedAllowBaseSyncPrimitives
    : public base::ScopedAllowBaseSyncPrimitives {};

bool LaunchXdgUtility(const std::vector<std::string>& argv, int* exit_code) {
  // xdg-settings internally runs xdg-mime, which uses mv to move newly-created
  // files on top of originals after making changes to them. In the event that
  // the original files are owned by another user (e.g. root, which can happen
  // if they are updated within sudo), mv will prompt the user to confirm if
  // standard input is a terminal (otherwise it just does it). So make sure it's
  // not, to avoid locking everything up waiting for mv.
  *exit_code = EXIT_FAILURE;
  int devnull = open("/dev/null", O_RDONLY);
  if (devnull < 0)
    return false;

  base::LaunchOptions options;
  options.fds_to_remap.push_back(std::make_pair(devnull, STDIN_FILENO));
  base::Process process = base::LaunchProcess(argv, options);
  close(devnull);
  if (!process.IsValid())
    return false;
  LaunchXdgUtilityScopedAllowBaseSyncPrimitives allow_base_sync_primitives;
  return process.WaitForExit(exit_code);
}

std::string GetWMClassFromAppName(std::string app_name) {
  base::i18n::ReplaceIllegalCharactersInPath(&app_name, '_');
  base::TrimString(app_name, "_", &app_name);
  return app_name;
}

base::FilePath GetDataWriteLocation(base::Environment* env) {
  return base::nix::GetXDGDirectory(env, "XDG_DATA_HOME", ".local/share");
}

std::vector<base::FilePath> GetDataSearchLocations(base::Environment* env) {
  base::ScopedBlockingCall scoped_blocking_call(FROM_HERE,
                                                base::BlockingType::MAY_BLOCK);

  std::vector<base::FilePath> search_paths;
  base::FilePath write_location = GetDataWriteLocation(env);
  search_paths.push_back(write_location);

  std::string xdg_data_dirs;
  if (env->GetVar("XDG_DATA_DIRS", &xdg_data_dirs) && !xdg_data_dirs.empty()) {
    base::StringTokenizer tokenizer(xdg_data_dirs, ":");
    while (tokenizer.GetNext()) {
      base::FilePath data_dir(tokenizer.token());
      search_paths.push_back(data_dir);
    }
  } else {
    search_paths.push_back(base::FilePath("/usr/local/share"));
    search_paths.push_back(base::FilePath("/usr/share"));
  }

  return search_paths;
}

namespace internal {

// Get the value of NoDisplay from the [Desktop Entry] section of a .desktop
// file, given in |shortcut_contents|. If the key is not found, returns false.
bool GetNoDisplayFromDesktopFile(const std::string& shortcut_contents) {
#if defined(USE_GLIB)
  // An empty file causes a crash with glib <= 2.32, so special case here.
  if (shortcut_contents.empty())
    return false;

  GKeyFile* key_file = g_key_file_new();
  GError* err = NULL;
  if (!g_key_file_load_from_data(key_file, shortcut_contents.c_str(),
                                 shortcut_contents.size(), G_KEY_FILE_NONE,
                                 &err)) {
    LOG(WARNING) << "Unable to read desktop file template: " << err->message;
    g_error_free(err);
    g_key_file_free(key_file);
    return false;
  }

  bool nodisplay = false;
  char* nodisplay_c_string = g_key_file_get_string(key_file, kDesktopEntry,
                                                   "NoDisplay", &err);
  if (nodisplay_c_string) {
    if (!g_strcmp0(nodisplay_c_string, "true"))
      nodisplay = true;
    g_free(nodisplay_c_string);
  } else {
    g_error_free(err);
  }

  g_key_file_free(key_file);
  return nodisplay;
#else
  NOTIMPLEMENTED();
  return false;
#endif
}

// Gets the path to the Chrome executable or wrapper script.
// Returns an empty path if the executable path could not be found, which should
// never happen.
base::FilePath GetChromeExePath() {
  // Try to get the name of the wrapper script that launched Chrome.
  std::unique_ptr<base::Environment> environment(base::Environment::Create());
  std::string wrapper_script;
  if (environment->GetVar("CHROME_WRAPPER", &wrapper_script))
    return base::FilePath(wrapper_script);

  // Just return the name of the executable path for Chrome.
  base::FilePath chrome_exe_path;
  base::PathService::Get(base::FILE_EXE, &chrome_exe_path);
  return chrome_exe_path;
}

std::string GetProgramClassName(const base::CommandLine& command_line,
                                const std::string& desktop_file_name) {
  std::string class_name = GetDesktopBaseName(desktop_file_name);
  std::string user_data_dir =
      command_line.GetSwitchValueNative(switches::kUserDataDir);
  // If the user launches with e.g. --user-data-dir=/tmp/my-user-data, set the
  // class name to "Chrome (/tmp/my-user-data)".  The class name will show up in
  // the alt-tab list in gnome-shell if you're running a binary that doesn't
  // have a matching .desktop file.
  return user_data_dir.empty()
             ? class_name
             : class_name + " (" + user_data_dir + ")";
}

std::string GetProgramClassClass(const base::CommandLine& command_line,
                                 const std::string& desktop_file_name) {
  if (command_line.HasSwitch(switches::kWmClass))
    return command_line.GetSwitchValueASCII(switches::kWmClass);
  std::string class_class = GetDesktopBaseName(desktop_file_name);
  if (!class_class.empty()) {
    // Capitalize the first character like gtk does.
    class_class[0] = base::ToUpperASCII(class_class[0]);
  }
  return class_class;
}

}  // namespace internal

std::string GetProgramClassName() {
  std::unique_ptr<base::Environment> env(base::Environment::Create());
  return internal::GetProgramClassName(*base::CommandLine::ForCurrentProcess(),
                                       chrome::GetDesktopName(env.get()));
}

std::string GetProgramClassClass() {
  std::unique_ptr<base::Environment> env(base::Environment::Create());
  return internal::GetProgramClassClass(*base::CommandLine::ForCurrentProcess(),
                                        chrome::GetDesktopName(env.get()));
}

std::string GetIconName() {
#if BUILDFLAG(GOOGLE_CHROME_BRANDING)
  return "google-chrome";
#else  // BUILDFLAG(CHROMIUM_BRANDING)
  return "chromium-browser";
#endif
}

bool GetExistingShortcutContents(base::Environment* env,
                                 const base::FilePath& desktop_filename,
                                 std::string* output) {
  base::ScopedBlockingCall scoped_blocking_call(FROM_HERE,
                                                base::BlockingType::MAY_BLOCK);

  std::vector<base::FilePath> search_paths = GetDataSearchLocations(env);

  for (std::vector<base::FilePath>::const_iterator i = search_paths.begin();
       i != search_paths.end(); ++i) {
    base::FilePath path = i->Append("applications").Append(desktop_filename);
    VLOG(1) << "Looking for desktop file in " << path.value();
    if (base::PathExists(path)) {
      VLOG(1) << "Found desktop file at " << path.value();
      return base::ReadFileToString(path, output);
    }
  }

  return false;
}

base::FilePath GetWebShortcutFilename(const GURL& url) {
  // Use a prefix, because xdg-desktop-menu requires it.
  std::string filename =
      std::string(chrome::kBrowserProcessExecutableName) + "-" + url.spec();
  base::i18n::ReplaceIllegalCharactersInPath(&filename, '_');

  base::FilePath desktop_path;
  if (!base::PathService::Get(base::DIR_USER_DESKTOP, &desktop_path))
    return base::FilePath();

  base::FilePath filepath = desktop_path.Append(filename);
  base::FilePath alternative_filepath(filepath.value() + ".desktop");
  for (size_t i = 1; i < 100; ++i) {
    if (base::PathExists(base::FilePath(alternative_filepath))) {
      alternative_filepath = base::FilePath(
          filepath.value() + "_" + base::NumberToString(i) + ".desktop");
    } else {
      return base::FilePath(alternative_filepath).BaseName();
    }
  }

  return base::FilePath();
}

std::vector<base::FilePath> GetExistingProfileShortcutFilenames(
    const base::FilePath& profile_path,
    const base::FilePath& directory) {
  base::ScopedBlockingCall scoped_blocking_call(FROM_HERE,
                                                base::BlockingType::MAY_BLOCK);

  // Use a prefix, because xdg-desktop-menu requires it.
  std::string prefix(chrome::kBrowserProcessExecutableName);
  prefix.append("-");
  std::string suffix("-");
  suffix.append(profile_path.BaseName().value());
  base::i18n::ReplaceIllegalCharactersInPath(&suffix, '_');
  // Spaces in filenames break xdg-desktop-menu
  // (see https://bugs.freedesktop.org/show_bug.cgi?id=66605).
  base::ReplaceChars(suffix, " ", "_", &suffix);
  std::string glob = prefix + "*" + suffix + ".desktop";

  base::FileEnumerator files(directory, false, base::FileEnumerator::FILES,
                             glob);
  base::FilePath shortcut_file = files.Next();
  std::vector<base::FilePath> shortcut_paths;
  while (!shortcut_file.empty()) {
    shortcut_paths.push_back(shortcut_file.BaseName());
    shortcut_file = files.Next();
  }
  return shortcut_paths;
}

std::string GetDesktopFileContents(const base::FilePath& chrome_exe_path,
                                   const std::string& app_name,
                                   const GURL& url,
                                   const std::string& extension_id,
                                   const base::string16& title,
                                   const std::string& icon_name,
                                   const base::FilePath& profile_path,
                                   const std::string& categories,
                                   const std::string& mime_type,
                                   bool no_display) {
  base::CommandLine cmd_line = shell_integration::CommandLineArgsForLauncher(
      url, extension_id, profile_path);
  cmd_line.SetProgram(chrome_exe_path);
  return GetDesktopFileContentsForCommand(cmd_line, app_name, url, title,
                                          icon_name, categories, mime_type,
                                          no_display);
}

std::string GetDesktopFileContentsForCommand(
    const base::CommandLine& command_line,
    const std::string& app_name,
    const GURL& url,
    const base::string16& title,
    const std::string& icon_name,
    const std::string& categories,
    const std::string& mime_type,
    bool no_display) {
#if defined(USE_GLIB)
  // Although not required by the spec, Nautilus on Ubuntu Karmic creates its
  // launchers with an xdg-open shebang. Follow that convention.
  std::string output_buffer = std::string(kXdgOpenShebang) + "\n";

  // See http://standards.freedesktop.org/desktop-entry-spec/latest/
  GKeyFile* key_file = g_key_file_new();

  // Set keys with fixed values.
  g_key_file_set_string(key_file, kDesktopEntry, "Version", "1.0");
  g_key_file_set_string(key_file, kDesktopEntry, "Terminal", "false");
  g_key_file_set_string(key_file, kDesktopEntry, "Type", "Application");

  // Set the "Name" key.
  std::string final_title = base::UTF16ToUTF8(title);
  // Make sure no endline characters can slip in and possibly introduce
  // additional lines (like Exec, which makes it a security risk). Also
  // use the URL as a default when the title is empty.
  if (final_title.empty() ||
      final_title.find("\n") != std::string::npos ||
      final_title.find("\r") != std::string::npos) {
    final_title = url.spec();
  }
  g_key_file_set_string(key_file, kDesktopEntry, "Name", final_title.c_str());

  base::CommandLine modified_command_line(command_line);

  // Set the "MimeType" key.
  if (!mime_type.empty() && mime_type.find("\n") == std::string::npos &&
      mime_type.find("\r") == std::string::npos) {
    g_key_file_set_string(key_file, kDesktopEntry, "MimeType",
                          mime_type.c_str());

    // Some Linux Desktop Environments don't show file handlers unless they
    // specify where to place file arguments.
    // Note: We only include this parameter if the application is actually able
    // to handle files, to prevent it showing up in the list of all applications
    // which can handle files.
    modified_command_line.AppendArg("%F");
  }

  // Set the "Exec" key.
  std::string final_path =
      QuoteCommandLineForDesktopFileExec(modified_command_line);
  g_key_file_set_string(key_file, kDesktopEntry, "Exec", final_path.c_str());

  // Set the "Icon" key.
  if (!icon_name.empty()) {
    g_key_file_set_string(key_file, kDesktopEntry, "Icon", icon_name.c_str());
  } else {
    g_key_file_set_string(key_file, kDesktopEntry, "Icon",
                          GetIconName().c_str());
  }

  // Set the "Categories" key.
  if (!categories.empty()) {
    g_key_file_set_string(
        key_file, kDesktopEntry, "Categories", categories.c_str());
  }

  // Set the "NoDisplay" key.
  if (no_display)
    g_key_file_set_string(key_file, kDesktopEntry, "NoDisplay", "true");

  std::string wmclass = GetWMClassFromAppName(app_name);
  g_key_file_set_string(key_file, kDesktopEntry, "StartupWMClass",
                        wmclass.c_str());

  gsize length = 0;
  gchar* data_dump = g_key_file_to_data(key_file, &length, NULL);
  if (data_dump) {
    // If strlen(data_dump[0]) == 0, this check will fail.
    if (data_dump[0] == '\n') {
      // Older versions of glib produce a leading newline. If this is the case,
      // remove it to avoid double-newline after the shebang.
      output_buffer += (data_dump + 1);
    } else {
      output_buffer += data_dump;
    }
    g_free(data_dump);
  }

  g_key_file_free(key_file);
  return output_buffer;
#else
  NOTIMPLEMENTED();
  return std::string();
#endif
}

std::string GetDirectoryFileContents(const base::string16& title,
                                     const std::string& icon_name) {
#if defined(USE_GLIB)
  // See http://standards.freedesktop.org/desktop-entry-spec/latest/
  GKeyFile* key_file = g_key_file_new();

  g_key_file_set_string(key_file, kDesktopEntry, "Version", "1.0");
  g_key_file_set_string(key_file, kDesktopEntry, "Type", "Directory");
  std::string final_title = base::UTF16ToUTF8(title);
  g_key_file_set_string(key_file, kDesktopEntry, "Name", final_title.c_str());
  if (!icon_name.empty()) {
    g_key_file_set_string(key_file, kDesktopEntry, "Icon", icon_name.c_str());
  } else {
    g_key_file_set_string(key_file, kDesktopEntry, "Icon",
                          GetIconName().c_str());
  }

  gsize length = 0;
  gchar* data_dump = g_key_file_to_data(key_file, &length, NULL);
  std::string output_buffer;
  if (data_dump) {
    // If strlen(data_dump[0]) == 0, this check will fail.
    if (data_dump[0] == '\n') {
      // Older versions of glib produce a leading newline. If this is the case,
      // remove it to avoid double-newline after the shebang.
      output_buffer += (data_dump + 1);
    } else {
      output_buffer += data_dump;
    }
    g_free(data_dump);
  }

  g_key_file_free(key_file);
  return output_buffer;
#else
  NOTIMPLEMENTED();
  return std::string();
#endif
}

base::FilePath GetMimeTypesRegistrationFilename(
    const base::FilePath& profile_path,
    const web_app::AppId& app_id) {
  DCHECK(!profile_path.empty() && !app_id.empty());

  // Use a prefix to clearly group files created by Chrome.
  std::string filename = base::StringPrintf(
      "%s-%s-%s%s", chrome::kBrowserProcessExecutableName, app_id.c_str(),
      profile_path.BaseName().value().c_str(), ".xml");

  // Replace illegal characters and spaces in |filename|.
  base::i18n::ReplaceIllegalCharactersInPath(&filename, '_');
  base::ReplaceChars(filename, " ", "_", &filename);

  return base::FilePath(filename);
}

std::string GetMimeTypesRegistrationFileContents(
    const apps::FileHandlers& file_handlers) {
  std::stringstream ss;
  ss << "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        "<mime-info "
        "xmlns=\"http://www.freedesktop.org/standards/shared-mime-info\">\n";

  for (const auto& file_handler : file_handlers) {
    for (const auto& accept_entry : file_handler.accept) {
      ss << "  <mime-type type=\"" << accept_entry.mime_type + "\">\n";
      for (const auto& file_extension : accept_entry.file_extensions)
        ss << "    <glob pattern=\"*" << file_extension << "\"/>\n";
      ss << "  </mime-type>\n";
    }
  }

  ss << "</mime-info>\n";
  return ss.str();
}

}  // namespace shell_integration_linux

namespace shell_integration {

bool SetAsDefaultBrowser() {
  return shell_integration_linux::SetDefaultWebClient(std::string());
}

bool SetAsDefaultProtocolClient(const std::string& protocol) {
  return shell_integration_linux::SetDefaultWebClient(protocol);
}

DefaultWebClientSetPermission GetDefaultWebClientSetPermission() {
  return SET_DEFAULT_UNATTENDED;
}

base::string16 GetApplicationNameForProtocol(const GURL& url) {
  return base::ASCIIToUTF16("xdg-open");
}

DefaultWebClientState GetDefaultBrowser() {
  return shell_integration_linux::GetIsDefaultWebClient(std::string());
}

bool IsFirefoxDefaultBrowser() {
  std::vector<std::string> argv;
  argv.push_back(shell_integration_linux::kXdgSettings);
  argv.push_back("get");
  argv.push_back(shell_integration_linux::kXdgSettingsDefaultBrowser);

  std::string browser;
  // We don't care about the return value here.
  base::GetAppOutput(base::CommandLine(argv), &browser);
  return browser.find("irefox") != std::string::npos;
}

DefaultWebClientState IsDefaultProtocolClient(const std::string& protocol) {
  return shell_integration_linux::GetIsDefaultWebClient(protocol);
}

}  // namespace shell_integration
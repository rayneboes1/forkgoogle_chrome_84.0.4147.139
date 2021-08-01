// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome/browser/chromeos/policy/cloud_external_data_store.h"

#include <set>

#include "base/check.h"
#include "base/sequenced_task_runner.h"
#include "base/strings/string_number_conversions.h"
#include "components/policy/core/common/cloud/resource_cache.h"
#include "crypto/sha2.h"

namespace policy {

namespace {

// Encodes (policy, hash) into a single string.
std::string GetSubkey(const std::string& policy, const std::string& hash) {
  DCHECK(!policy.empty());
  DCHECK(!hash.empty());
  return base::NumberToString(policy.size()) + ":" +
         base::NumberToString(hash.size()) + ":" + policy + hash;
}

}  // namespace

CloudExternalDataStore::CloudExternalDataStore(
    const std::string& cache_key,
    scoped_refptr<base::SequencedTaskRunner> task_runner,
    ResourceCache* cache)
    : cache_key_(cache_key),
      task_runner_(task_runner),
      cache_(cache) {
}

CloudExternalDataStore::~CloudExternalDataStore() {
  // No RunsTasksInCurrentSequence() check to avoid unit tests failures.
  // In unit tests the browser process instance is deleted only after test ends
  // and test task scheduler is shutted down. Therefore we need to delete some
  // components of BrowserPolicyConnector (ResourceCache and
  // CloudExternalDataManagerBase::Backend) manually when task runner doesn't
  // accept new tasks (DeleteSoon in this case). This leads to the situation
  // when this destructor is called not on |task_runner|.
}

void CloudExternalDataStore::Prune(
    const CloudExternalDataManager::Metadata& metadata) {
  DCHECK(task_runner_->RunsTasksInCurrentSequence());
  std::set<std::string> subkeys_to_keep;
  for (CloudExternalDataManager::Metadata::const_iterator it = metadata.begin();
       it != metadata.end(); ++it) {
    subkeys_to_keep.insert(GetSubkey(it->first, it->second.hash));
  }
  cache_->PurgeOtherSubkeys(cache_key_, subkeys_to_keep);
}

base::FilePath CloudExternalDataStore::Store(const std::string& policy,
                                             const std::string& hash,
                                             const std::string& data) {
  DCHECK(task_runner_->RunsTasksInCurrentSequence());
  return cache_->Store(cache_key_, GetSubkey(policy, hash), data);
}

base::FilePath CloudExternalDataStore::Load(const std::string& policy,
                                            const std::string& hash,
                                            size_t max_size,
                                            std::string* data) {
  DCHECK(task_runner_->RunsTasksInCurrentSequence());
  const std::string subkey = GetSubkey(policy, hash);
  base::FilePath file_path = cache_->Load(cache_key_, subkey, data);
  if (!file_path.empty()) {
    if (data->size() <= max_size && crypto::SHA256HashString(*data) == hash)
      return file_path;
    // If the data is larger than allowed or does not match the expected hash,
    // delete the entry.
    cache_->Delete(cache_key_, subkey);
    data->clear();
  }
  return base::FilePath();
}

}  // namespace policy

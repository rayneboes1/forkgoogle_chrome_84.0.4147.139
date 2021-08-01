// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef CHROME_BROWSER_MEDIA_ROUTER_PROVIDERS_CAST_CAST_SESSION_TRACKER_H_
#define CHROME_BROWSER_MEDIA_ROUTER_PROVIDERS_CAST_CAST_SESSION_TRACKER_H_

#include "base/containers/flat_map.h"
#include "base/macros.h"
#include "base/observer_list.h"
#include "base/sequence_checker.h"
#include "base/values.h"
#include "chrome/browser/media/router/providers/cast/cast_internal_message_util.h"
#include "chrome/common/media_router/discovery/media_sink_internal.h"
#include "chrome/common/media_router/discovery/media_sink_service_base.h"
#include "components/cast_channel/cast_message_handler.h"
#include "components/cast_channel/cast_message_util.h"

namespace media_router {

// Tracks active sessions on Cast MediaSinks. Listens for RECEIVER_STATUS
// messages from Cast channels and notifies observers of changes to sessions.
// GetInstance() must be called on the UI thread while all other methods must be
// called on the IO thread.
class CastSessionTracker : public MediaSinkServiceBase::Observer,
                           public cast_channel::CastMessageHandler::Observer {
 public:
  typedef base::flat_map<MediaSink::Id, std::unique_ptr<CastSession>>
      SessionMap;

  class Observer : public base::CheckedObserver {
   public:
    ~Observer() override;
    virtual void OnSessionAddedOrUpdated(const MediaSinkInternal& sink,
                                         const CastSession& session) = 0;
    virtual void OnSessionRemoved(const MediaSinkInternal& sink) = 0;
    virtual void OnMediaStatusUpdated(const MediaSinkInternal& sink,
                                      const base::Value& media_status,
                                      base::Optional<int> request_id) = 0;
  };

  ~CastSessionTracker() override;

  // Must be called on UI thread.
  // TODO(https://crbug.com/904016): The UI/IO thread split makes this class
  // confusing to use.  If we can directly access CastMediaSinkServiceImpl
  // without going through DualMediaSinkService, then it will no longer be
  // necessary for this method to be run on UI thread.
  static CastSessionTracker* GetInstance();

  void AddObserver(Observer* observer);
  void RemoveObserver(Observer* observer);

  const SessionMap& GetSessions() const;

  // Returns nullptr if there is no session with the specified ID.
  CastSession* GetSessionById(const std::string& session_id) const;

 private:
  friend class CastSessionTrackerTest;
  friend class CastActivityRecordTest;
  friend class CastActivityManagerTest;
  friend class CastMediaRouteProviderTest;

  // Use |GetInstance()| instead.
  CastSessionTracker(
      MediaSinkServiceBase* media_sink_service,
      cast_channel::CastMessageHandler* message_handler,
      const scoped_refptr<base::SequencedTaskRunner>& task_runner);

  void InitOnIoThread();
  void HandleReceiverStatusMessage(const MediaSinkInternal& sink,
                                   const base::Value& message);
  void HandleMediaStatusMessage(const MediaSinkInternal& sink,
                                const base::Value& message);
  void CopySavedMediaFieldsToMediaList(CastSession* session,
                                       base::Value::ListView media_list);
  const MediaSinkInternal* GetSinkByChannelId(int channel_id) const;

  // MediaSinkServiceBase::Observer implementation
  void OnSinkAddedOrUpdated(const MediaSinkInternal& sink) override;
  void OnSinkRemoved(const MediaSinkInternal& sink) override;

  // cast_channel::CastMessageHandler::Observer implementation
  void OnInternalMessage(int channel_id,
                         const cast_channel::InternalMessage& message) override;

  static void SetInstanceForTest(CastSessionTracker* session_tracker);
  void SetSessionForTest(const MediaSink::Id& sink_id,
                         std::unique_ptr<CastSession> session);

  // Tests may override the value returned via |GetInstance()| by calling
  // |SetInstanceForTest()|.
  static CastSessionTracker* instance_for_test_;

  MediaSinkServiceBase* const media_sink_service_;
  cast_channel::CastMessageHandler* const message_handler_;

  SessionMap sessions_by_sink_id_;

  base::ObserverList<Observer> observers_;

  SEQUENCE_CHECKER(sequence_checker_);
  DISALLOW_COPY_AND_ASSIGN(CastSessionTracker);
  FRIEND_TEST_ALL_PREFIXES(CastActivityRecordTest, SendAppMessageToReceiver);
  FRIEND_TEST_ALL_PREFIXES(CastMediaRouteProviderTest, GetState);
  FRIEND_TEST_ALL_PREFIXES(CastSessionTrackerTest, RemoveSession);
  FRIEND_TEST_ALL_PREFIXES(CastSessionTrackerTest,
                           HandleMediaStatusMessageBasic);
  FRIEND_TEST_ALL_PREFIXES(CastSessionTrackerTest,
                           HandleMediaStatusMessageFancy);
  FRIEND_TEST_ALL_PREFIXES(CastSessionTrackerTest,
                           CopySavedMediaFieldsToMediaList);
  FRIEND_TEST_ALL_PREFIXES(CastSessionTrackerTest,
                           DoNotCopySavedMediaFieldsWhenFieldPresent);
};

}  // namespace media_router

#endif  // CHROME_BROWSER_MEDIA_ROUTER_PROVIDERS_CAST_CAST_SESSION_TRACKER_H_

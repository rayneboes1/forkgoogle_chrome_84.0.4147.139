// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#import "chrome/browser/renderer_host/chrome_render_widget_host_view_mac_history_swiper.h"

#include "chrome/browser/ui/browser.h"
#include "chrome/browser/ui/browser_commands.h"
#include "chrome/browser/ui/browser_finder.h"
#import "chrome/browser/ui/cocoa/history_overlay_controller.h"
#include "chrome/browser/ui/tabs/tab_strip_model.h"
#include "third_party/blink/public/common/input/web_gesture_event.h"
#include "third_party/blink/public/common/input/web_mouse_wheel_event.h"
#include "ui/events/blink/did_overscroll_params.h"

namespace {
// The horizontal distance required to cause the browser to perform a history
// navigation.
const CGFloat kHistorySwipeThreshold = 0.08;

// The horizontal distance required for this class to start consuming events,
// which stops the events from reaching the renderer.
const CGFloat kConsumeEventThreshold = 0.01;

// If there has been sufficient vertical motion, the gesture can't be intended
// for history swiping.
const CGFloat kCancelEventVerticalThreshold = 0.24;

// If there has been sufficient vertical motion, and more vertical than
// horizontal motion, the gesture can't be intended for history swiping.
const CGFloat kCancelEventVerticalLowerThreshold = 0.01;

// Once we call `[NSEvent trackSwipeEventWithOptions:]`, we cannot reliably
// expect NSTouch callbacks. We set this variable to YES and ignore NSTouch
// callbacks.
BOOL forceMagicMouse = NO;
}  // namespace

@interface HistorySwiper ()
// Given a touch event, returns the average touch position.
- (NSPoint)averagePositionInEvent:(NSEvent*)event;

// Updates internal state with the location information from the touch event.
- (void)updateGestureCurrentPointFromEvent:(NSEvent*)event;

// Updates the state machine with the given touch event.
// Returns NO if no further processing of the event should happen.
- (BOOL)processTouchEventForHistorySwiping:(NSEvent*)event;

// Returns whether the wheel event should be consumed, and not passed to the
// renderer.
- (BOOL)shouldConsumeWheelEvent:(NSEvent*)event;

// Shows the history swiper overlay.
- (void)showHistoryOverlay:(history_swiper::NavigationDirection)direction;

// Removes the history swiper overlay.
- (void)removeHistoryOverlay;

// Returns YES if the event was consumed or NO if it should be passed on to the
// renderer. If |event| was generated by a Magic Mouse, this method forwards to
// handleMagicMouseWheelEvent. Otherwise, this method attempts to transition
// the state machine from kPending -> kPotential. If it performs the
// transition, it also shows the history overlay. In order for a history swipe
// gesture to be recognized, the transition must occur.
//
// There are 4 types of scroll wheel events:
// 1. Magic mouse swipe events.
//      These are identical to magic trackpad events, except that there are no
//      -[NSView touches*WithEvent:] callbacks.  The only way to accurately
//      track these events is with the  `trackSwipeEventWithOptions:` API.
//      scrollingDelta{X,Y} is not accurate over long distances (it is computed
//      using the speed of the swipe, rather than just the distance moved by
//      the fingers).
// 2. Magic trackpad swipe events.
//      These are the most common history swipe events. The logic of this
//      method is predominantly designed to handle this use case.
// 3. Traditional mouse scrollwheel events.
//      These should not initiate scrolling. They can be distinguished by the
//      fact that `phase` and `momentumPhase` both return NSEventPhaseNone.
// 4. Momentum swipe events.
//      After a user finishes a swipe, the system continues to generate
//      artificial callbacks. `phase` returns NSEventPhaseNone, but
//      `momentumPhase` does not. Unfortunately, the callbacks don't work
//      properly (OSX 10.9). Sometimes, the system start sending momentum swipe
//      events instead of trackpad swipe events while the user is still
//      2-finger swiping.
- (BOOL)handleScrollWheelEvent:(NSEvent*)event;

// Returns YES if the event was consumed or NO if it should be passed on to the
// renderer. Attempts to initiate history swiping for Magic Mouse events.
- (BOOL)handleMagicMouseWheelEvent:(NSEvent*)theEvent;
@end

@implementation HistorySwiper
@synthesize delegate = _delegate;

- (id)initWithDelegate:(id<HistorySwiperDelegate>)delegate {
  self = [super init];
  if (self) {
    _delegate = delegate;
  }
  return self;
}

- (void)dealloc {
  [self removeHistoryOverlay];
  [super dealloc];
}

- (BOOL)handleEvent:(NSEvent*)event {
  if ([event type] != NSScrollWheel)
    return NO;

  return [self handleScrollWheelEvent:event];
}

- (void)rendererHandledWheelEvent:(const blink::WebMouseWheelEvent&)event
                         consumed:(BOOL)consumed {
  if (event.phase != NSEventPhaseBegan)
    return;
  _firstScrollUnconsumed = !consumed;
}

- (void)rendererHandledGestureScrollEvent:(const blink::WebGestureEvent&)event
                                 consumed:(BOOL)consumed {
  switch (event.GetType()) {
    case blink::WebInputEvent::Type::kGestureScrollBegin:
      if (event.data.scroll_begin.synthetic ||
          event.data.scroll_begin.inertial_phase ==
              blink::WebGestureEvent::InertialPhaseState::kMomentum) {
        return;
      }
      _waitingForFirstGestureScroll = YES;
      break;
    case blink::WebInputEvent::Type::kGestureScrollUpdate:
      if (_waitingForFirstGestureScroll)
        _firstScrollUnconsumed = !consumed;
      _waitingForFirstGestureScroll = NO;
      break;
    default:
      break;
  }
}

- (void)onOverscrolled:(const ui::DidOverscrollParams&)params {
  _overscrollTriggeredByRenderer =
      params.overscroll_behavior.x ==
      cc::OverscrollBehavior::OverscrollBehaviorType::
          kOverscrollBehaviorTypeAuto;
}

- (void)beginGestureWithEvent:(NSEvent*)event {
  _inGesture = YES;

  // Reset state pertaining to Magic Mouse swipe gestures.
  _mouseScrollDelta = NSZeroSize;
}

- (void)endGestureWithEvent:(NSEvent*)event {
  _inGesture = NO;
}

// This method assumes that there is at least 1 touch in the event.
// The event must correpond to a valid gesture, or else
// [NSEvent touchesMatchingPhase:inView:] will fail.
- (NSPoint)averagePositionInEvent:(NSEvent*)event {
  NSPoint position = NSMakePoint(0,0);
  int pointCount = 0;
  for (NSTouch* touch in
       [event touchesMatchingPhase:NSTouchPhaseAny inView:nil]) {
    position.x += touch.normalizedPosition.x;
    position.y += touch.normalizedPosition.y;
    ++pointCount;
  }

  if (pointCount > 1) {
    position.x /= pointCount;
    position.y /= pointCount;
  }

  return position;
}

- (void)updateGestureCurrentPointFromEvent:(NSEvent*)event {
  NSPoint averagePosition = [self averagePositionInEvent:event];

  // If the start point is valid, then so is the current point.
  if (_gestureStartPointValid)
    _gestureTotalY += fabs(averagePosition.y - _gestureCurrentPoint.y);

  // Update the current point of the gesture.
  _gestureCurrentPoint = averagePosition;

  // If the gesture doesn't have a start point, set one.
  if (!_gestureStartPointValid) {
    _gestureStartPointValid = YES;
    _gestureStartPoint = _gestureCurrentPoint;
  }
}

// Ideally, we'd set the gestureStartPoint_ here, but this method only gets
// called before the gesture begins, and the touches in an event are only
// available after the gesture begins.
- (void)touchesBeganWithEvent:(NSEvent*)event {
  _receivingTouches = YES;

  // Reset state pertaining to previous trackpad gestures.
  _gestureStartPointValid = NO;
  _gestureTotalY = 0;
  _firstScrollUnconsumed = NO;
  _overscrollTriggeredByRenderer = NO;
  _waitingForFirstGestureScroll = NO;
  _recognitionState = history_swiper::kPending;
}

- (void)touchesMovedWithEvent:(NSEvent*)event {
  [self processTouchEventForHistorySwiping:event];
}

- (void)touchesCancelledWithEvent:(NSEvent*)event {
  _receivingTouches = NO;

  if (![self processTouchEventForHistorySwiping:event])
    return;

  [self cancelHistorySwipe];
}

- (void)touchesEndedWithEvent:(NSEvent*)event {
  _receivingTouches = NO;
  if (![self processTouchEventForHistorySwiping:event])
    return;

  if (_historyOverlay) {
    BOOL finished = [self updateProgressBar];

    // If the gesture was completed, perform a navigation.
    if (finished)
      [self navigateBrowserInDirection:_historySwipeDirection];

    [self removeHistoryOverlay];

    // The gesture was completed.
    _recognitionState = history_swiper::kCompleted;
  }
}

- (BOOL)processTouchEventForHistorySwiping:(NSEvent*)event {
  NSEventType type = [event type];
  if (type != NSEventTypeBeginGesture && type != NSEventTypeEndGesture &&
      type != NSEventTypeGesture) {
    return NO;
  }

  switch (_recognitionState) {
    case history_swiper::kCancelled:
    case history_swiper::kCompleted:
      return NO;
    case history_swiper::kPending:
    case history_swiper::kPotential:
    case history_swiper::kTracking:
      break;
  }

  [self updateGestureCurrentPointFromEvent:event];

  // Consider cancelling the history swipe gesture.
  if ([self shouldCancelHorizontalSwipeWithCurrentPoint:_gestureCurrentPoint
                                             startPoint:_gestureStartPoint]) {
    [self cancelHistorySwipe];
    return NO;
  }

  // Don't do any more processing if the state machine is in the pending state.
  if (_recognitionState == history_swiper::kPending)
    return NO;

  if (_recognitionState == history_swiper::kPotential) {
    // The user is in the process of doing history swiping.  If the history
    // swipe has progressed sufficiently far, stop sending events to the
    // renderer.
    BOOL sufficientlyFar = fabs(_gestureCurrentPoint.x - _gestureStartPoint.x) >
                           kConsumeEventThreshold;
    if (sufficientlyFar)
      _recognitionState = history_swiper::kTracking;
  }

  if (_historyOverlay)
    [self updateProgressBar];
  return YES;
}

// Consider cancelling the horizontal swipe if the user was intending a
// vertical swipe.
- (BOOL)shouldCancelHorizontalSwipeWithCurrentPoint:(NSPoint)currentPoint
    startPoint:(NSPoint)startPoint {
  CGFloat yDelta = _gestureTotalY;
  CGFloat xDelta = fabs(currentPoint.x - startPoint.x);

  // The gesture is pretty clearly more vertical than horizontal.
  if (yDelta > 2 * xDelta)
    return YES;

  // There's been more vertical distance than horizontal distance.
  if (yDelta * 1.3 > xDelta && yDelta > kCancelEventVerticalLowerThreshold)
    return YES;

  // There's been a lot of vertical distance.
  if (yDelta > kCancelEventVerticalThreshold)
    return YES;

  return NO;
}

- (void)cancelHistorySwipe {
  [self removeHistoryOverlay];
  _recognitionState = history_swiper::kCancelled;
}

- (void)removeHistoryOverlay {
  [_historyOverlay dismiss];
  [_historyOverlay release];
  _historyOverlay = nil;
}

// Returns whether the progress bar has been 100% filled.
- (BOOL)updateProgressBar {
  NSPoint currentPoint = _gestureCurrentPoint;
  NSPoint startPoint = _gestureStartPoint;

  float progress = 0;
  BOOL finished = NO;

  progress = (currentPoint.x - startPoint.x) / kHistorySwipeThreshold;
  // If the swipe is a backwards gesture, we need to invert progress.
  if (_historySwipeDirection == history_swiper::kBackwards)
    progress *= -1;

  // If the user has directions reversed, we need to invert progress.
  if (_historySwipeDirectionInverted)
    progress *= -1;

  if (progress >= 1.0)
    finished = YES;

  // Progress can't be less than 0 or greater than 1.
  progress = MAX(0.0, progress);
  progress = MIN(1.0, progress);

  [_historyOverlay setProgress:progress finished:finished];

  return finished;
}

- (BOOL)isEventDirectionInverted:(NSEvent*)event {
  if ([event respondsToSelector:@selector(isDirectionInvertedFromDevice)])
    return [event isDirectionInvertedFromDevice];
  return NO;
}

- (void)showHistoryOverlay:(history_swiper::NavigationDirection)direction {
  // We cannot make any assumptions about the current state of the
  // historyOverlay_, since users may attempt to use multiple gesture input
  // devices simultaneously, which confuses Cocoa.
  [self removeHistoryOverlay];

  HistoryOverlayController* historyOverlay = [[HistoryOverlayController alloc]
      initForMode:(direction == history_swiper::kForwards)
                     ? kHistoryOverlayModeForward
                     : kHistoryOverlayModeBack];
  [historyOverlay showPanelForView:[_delegate viewThatWantsHistoryOverlay]];
  _historyOverlay = historyOverlay;
}

- (BOOL)systemSettingsAllowHistorySwiping:(NSEvent*)event {
  if ([NSEvent
          respondsToSelector:@selector(isSwipeTrackingFromScrollEventsEnabled)])
    return [NSEvent isSwipeTrackingFromScrollEventsEnabled];
  return NO;
}

- (void)navigateBrowserInDirection:
            (history_swiper::NavigationDirection)direction {
  Browser* browser = chrome::FindBrowserWithWindow(
      _historyOverlay.view.window);
  if (browser) {
    if (direction == history_swiper::kForwards)
      chrome::GoForward(browser, WindowOpenDisposition::CURRENT_TAB);
    else
      chrome::GoBack(browser, WindowOpenDisposition::CURRENT_TAB);
  }
}

- (BOOL)browserCanNavigateInDirection:
        (history_swiper::NavigationDirection)direction
                                event:(NSEvent*)event {
  Browser* browser = chrome::FindBrowserWithWindow([event window]);
  if (!browser)
    return NO;

  if (direction == history_swiper::kForwards) {
    return chrome::CanGoForward(browser);
  } else {
    return chrome::CanGoBack(browser);
  }
}

- (BOOL)handleMagicMouseWheelEvent:(NSEvent*)theEvent {
  // The 'trackSwipeEventWithOptions:' api doesn't handle momentum events.
  if ([theEvent phase] == NSEventPhaseNone)
    return NO;

  _mouseScrollDelta.width += [theEvent scrollingDeltaX];
  _mouseScrollDelta.height += [theEvent scrollingDeltaY];

  BOOL isHorizontalGesture =
    std::abs(_mouseScrollDelta.width) > std::abs(_mouseScrollDelta.height);
  if (!isHorizontalGesture)
    return NO;

  BOOL isRightScroll = [theEvent scrollingDeltaX] < 0;
  history_swiper::NavigationDirection direction =
      isRightScroll ? history_swiper::kForwards : history_swiper::kBackwards;
  BOOL browserCanMove =
      [self browserCanNavigateInDirection:direction event:theEvent];
  if (!browserCanMove)
    return NO;

  [self initiateMagicMouseHistorySwipe:isRightScroll event:theEvent];
  return YES;
}

- (void)initiateMagicMouseHistorySwipe:(BOOL)isRightScroll
                                 event:(NSEvent*)event {
  // Released by the tracking handler once the gesture is complete.
  __block HistoryOverlayController* historyOverlay =
      [[HistoryOverlayController alloc]
          initForMode:isRightScroll ? kHistoryOverlayModeForward
                                    : kHistoryOverlayModeBack];

  // The way this API works: gestureAmount is between -1 and 1 (float).  If
  // the user does the gesture for more than about 30% (i.e. < -0.3 or >
  // 0.3) and then lets go, it is accepted, we get a NSEventPhaseEnded,
  // and after that the block is called with amounts animating towards 1
  // (or -1, depending on the direction).  If the user lets go below that
  // threshold, we get NSEventPhaseCancelled, and the amount animates
  // toward 0.  When gestureAmount has reaches its final value, i.e. the
  // track animation is done, the handler is called with |isComplete| set
  // to |YES|.
  // When starting a backwards navigation gesture (swipe from left to right,
  // gestureAmount will go from 0 to 1), if the user swipes from left to
  // right and then quickly back to the left, this call can send
  // NSEventPhaseEnded and then animate to gestureAmount of -1. For a
  // picture viewer, that makes sense, but for back/forward navigation users
  // find it confusing. There are two ways to prevent this:
  // 1. Set Options to NSEventSwipeTrackingLockDirection. This way,
  //    gestureAmount will always stay > 0.
  // 2. Pass min:0 max:1 (instead of min:-1 max:1). This way, gestureAmount
  //    will become less than 0, but on the quick swipe back to the left,
  //    NSEventPhaseCancelled is sent instead.
  // The current UI looks nicer with (1) so that swiping the opposite
  // direction after the initial swipe doesn't cause the shield to move
  // in the wrong direction.
  forceMagicMouse = YES;
  [event trackSwipeEventWithOptions:NSEventSwipeTrackingLockDirection
      dampenAmountThresholdMin:-1
      max:1
      usingHandler:^(CGFloat gestureAmount,
                     NSEventPhase phase,
                     BOOL isComplete,
                     BOOL* stop) {
          if (phase == NSEventPhaseBegan) {
            [historyOverlay
                showPanelForView:[_delegate viewThatWantsHistoryOverlay]];
            return;
          }

          BOOL ended = phase == NSEventPhaseEnded;

          // Dismiss the panel before navigation for immediate visual feedback.
          CGFloat progress = std::abs(gestureAmount) / 0.3;
          BOOL finished = progress >= 1.0;
          progress = MAX(0.0, progress);
          progress = MIN(1.0, progress);
          [historyOverlay setProgress:progress finished:finished];

          // |gestureAmount| obeys -[NSEvent isDirectionInvertedFromDevice]
          // automatically.
          Browser* browser =
              chrome::FindBrowserWithWindow(historyOverlay.view.window);
          if (ended && browser) {
            if (isRightScroll)
              chrome::GoForward(browser, WindowOpenDisposition::CURRENT_TAB);
            else
              chrome::GoBack(browser, WindowOpenDisposition::CURRENT_TAB);
          }

          if (ended || isComplete) {
            [historyOverlay dismiss];
            [historyOverlay release];
            historyOverlay = nil;
          }
      }];
}

- (BOOL)handleScrollWheelEvent:(NSEvent*)theEvent {
  if (![theEvent respondsToSelector:@selector(phase)])
    return NO;

  // The only events that this class consumes have type NSEventPhaseChanged.
  // This simultaneously weeds our regular mouse wheel scroll events, and
  // gesture events with incorrect phase.
  if ([theEvent phase] != NSEventPhaseChanged &&
      [theEvent momentumPhase] != NSEventPhaseChanged) {
    return NO;
  }

  // We've already processed this gesture.
  if (_recognitionState != history_swiper::kPending) {
    return [self shouldConsumeWheelEvent:theEvent];
  }

  // Don't allow momentum events to start history swiping.
  if ([theEvent momentumPhase] != NSEventPhaseNone)
    return NO;

  BOOL systemSettingsValid = [self systemSettingsAllowHistorySwiping:theEvent];
  if (!systemSettingsValid)
    return NO;

  if (![_delegate shouldAllowHistorySwiping])
    return NO;

  // Don't enable history swiping until the renderer has decided to not consume
  // the event with phase NSEventPhaseBegan.
  if (!_firstScrollUnconsumed)
    return NO;

  // History swiping should be prevented if the renderer hasn't triggered it.
  if (!_overscrollTriggeredByRenderer)
    return NO;

  // Magic mouse and touchpad swipe events are identical except magic mouse
  // events do not generate NSTouch callbacks. Since we rely on NSTouch
  // callbacks to perform history swiping, magic mouse swipe events use an
  // entirely different set of logic.
  if ((_inGesture && !_receivingTouches) || forceMagicMouse)
    return [self handleMagicMouseWheelEvent:theEvent];

  // The scrollWheel: callback is only relevant if it happens while the user is
  // still actively using the touchpad.
  if (!_receivingTouches)
    return NO;

  // TODO(erikchen): Ideally, the direction of history swiping should not be
  // determined this early in a gesture, when it's unclear what the user is
  // intending to do. Since it is determined this early, make sure that there
  // is at least a minimal amount of horizontal motion.
  CGFloat xDelta = _gestureCurrentPoint.x - _gestureStartPoint.x;
  if (fabs(xDelta) < 0.001)
    return NO;

  BOOL isRightScroll = xDelta > 0;
  BOOL inverted = [self isEventDirectionInverted:theEvent];
  if (inverted)
    isRightScroll = !isRightScroll;

  history_swiper::NavigationDirection direction =
      isRightScroll ? history_swiper::kForwards : history_swiper::kBackwards;
  BOOL browserCanMove =
      [self browserCanNavigateInDirection:direction event:theEvent];
  if (!browserCanMove)
    return NO;

  _historySwipeDirection = direction;
  _historySwipeDirectionInverted = [self isEventDirectionInverted:theEvent];
  _recognitionState = history_swiper::kPotential;
  [self showHistoryOverlay:direction];
  return [self shouldConsumeWheelEvent:theEvent];
}

- (BOOL)shouldConsumeWheelEvent:(NSEvent*)event {
  switch (_recognitionState) {
    case history_swiper::kPending:
    case history_swiper::kCancelled:
      return NO;
    case history_swiper::kTracking:
    case history_swiper::kCompleted:
      return YES;
    case history_swiper::kPotential:
      // It is unclear whether the user is attempting to perform history
      // swiping.  If the event has a vertical component, send it on to the
      // renderer.
      return [event scrollingDeltaY] == 0;
  }
}

@end

@implementation HistorySwiper (PrivateExposedForTesting)
+ (void)resetMagicMouseState {
  forceMagicMouse = NO;
}
@end

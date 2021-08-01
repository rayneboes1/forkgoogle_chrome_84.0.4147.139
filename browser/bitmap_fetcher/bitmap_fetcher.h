// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef CHROME_BROWSER_BITMAP_FETCHER_BITMAP_FETCHER_H_
#define CHROME_BROWSER_BITMAP_FETCHER_BITMAP_FETCHER_H_

#include <memory>

#include "base/callback_forward.h"
#include "base/macros.h"
#include "base/memory/weak_ptr.h"
#include "chrome/browser/bitmap_fetcher/bitmap_fetcher_delegate.h"
#include "chrome/browser/image_decoder/image_decoder.h"
#include "net/traffic_annotation/network_traffic_annotation.h"
#include "net/url_request/url_request.h"
#include "services/network/public/cpp/simple_url_loader.h"
#include "services/network/public/mojom/fetch_api.mojom-forward.h"
#include "services/network/public/mojom/url_loader_factory.mojom-forward.h"
#include "url/gurl.h"

class SkBitmap;

// Asynchronously fetches an image from the given URL and returns the
// decoded Bitmap to the provided BitmapFetcherDelegate.
class BitmapFetcher : public ImageDecoder::ImageRequest {
 public:
  BitmapFetcher(const GURL& url,
                BitmapFetcherDelegate* delegate,
                const net::NetworkTrafficAnnotationTag& traffic_annotation);
  ~BitmapFetcher() override;

  const GURL& url() const { return url_; }

  // |credentials_mode| determines whether credentials such as cookies should be
  // sent.  Init may be called more than once in some cases.  If so, subsequent
  // calls will be ignored.
  // TODO(tommycli): Init and Start should likely be combined.
  virtual void Init(const std::string& referrer,
                    net::URLRequest::ReferrerPolicy referrer_policy,
                    network::mojom::CredentialsMode credentials_mode);

  // Start fetching the URL with the fetcher. The delegate is notified
  // asynchronously when done.  Start may be called more than once in some
  // cases.  If so, subsequent calls will be ignored since the operation is
  // already in progress.
  virtual void Start(network::mojom::URLLoaderFactory* loader_factory);

  // Methods inherited from ImageDecoder::ImageRequest

  // Called when image is decoded. |decoder| is used to identify the image in
  // case of decoding several images simultaneously.
  void OnImageDecoded(const SkBitmap& decoded_image) override;

  // Called when decoding image failed.
  void OnDecodeImageFailed() override;

 private:
  void OnSimpleLoaderComplete(std::unique_ptr<std::string> response_body);

  // Alerts the delegate that a failure occurred.
  void ReportFailure();

  std::unique_ptr<network::SimpleURLLoader> simple_loader_;

  const GURL url_;
  BitmapFetcherDelegate* const delegate_;
  const net::NetworkTrafficAnnotationTag traffic_annotation_;

  base::WeakPtrFactory<BitmapFetcher> weak_factory_{this};

  DISALLOW_COPY_AND_ASSIGN(BitmapFetcher);
};

#endif  // CHROME_BROWSER_BITMAP_FETCHER_BITMAP_FETCHER_H_

// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.image_fetcher;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.VisibleForTesting;

import org.chromium.base.Callback;
import org.chromium.base.Log;
import org.chromium.base.task.PostTask;
import org.chromium.base.task.TaskTraits;
import org.chromium.content_public.browser.UiThreadTaskTraits;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import jp.tomorrowkey.android.gifplayer.BaseGifImage;

/**
 * ImageFetcher implementation that uses a disk cache.
 */
public class CachedImageFetcher extends ImageFetcher {
    private static final String TAG = "CachedImageFetcher";

    static class ImageLoader {
        /**
         * Attempt to load an image from disk with the given filepath.
         *
         * @param filePath The path to the image on disk (including the filename).
         * @return The Bitmap that's on disk or null if the there's no file or the decoding failed.
         */
        Bitmap tryToLoadImageFromDisk(String filePath) {
            if (new File(filePath).exists()) {
                return BitmapFactory.decodeFile(filePath, null);
            } else {
                return null;
            }
        }
        /**
         * Attempt to load a BaseGifImage from disk with the given filepath.
         *
         * @param filePath The path to the BaseGifImage on disk (including the filename).
         * @return The BaseGifImage that's on disk or null if the there's no file or the decoding
         *         failed.
         */
        BaseGifImage tryToLoadGifFromDisk(String filePath) {
            try {
                File file = new File(filePath);
                byte[] fileBytes = new byte[(int) file.length()];
                FileInputStream fileInputStream = new FileInputStream(filePath);

                int bytesRead = fileInputStream.read(fileBytes);
                if (bytesRead != fileBytes.length) return null;

                return new BaseGifImage(fileBytes);
            } catch (IOException e) {
                Log.w(TAG, "Failed to read: %s", filePath, e);
                return null;
            }
        }
    }

    private ImageLoader mImageLoader;

    /**
     * Creates a CachedImageFetcher with the given bridge.
     *
     * @param imageFetcherBridge Bridge used to interact with native.
     * @param imageLoader Delegate used to load
     */
    CachedImageFetcher(ImageFetcherBridge imageFetcherBridge, ImageLoader imageLoader) {
        super(imageFetcherBridge);
        mImageLoader = imageLoader;
    }

    @Override
    public void destroy() {
        // Do nothing, this lives for the lifetime of the application.
    }

    /**
     * Tries to load the gif from disk, if not it falls back to the bridge.
     */
    @Override
    public void fetchGif(String url, String clientName, Callback<BaseGifImage> callback) {
        long startTimeMillis = System.currentTimeMillis();
        PostTask.postTask(TaskTraits.USER_VISIBLE, () -> {
            // Try to read the gif from disk, then post back to the ui thread.
            String filePath = getImageFetcherBridge().getFilePath(url);
            BaseGifImage cachedGif = mImageLoader.tryToLoadGifFromDisk(filePath);
            PostTask.postTask(UiThreadTaskTraits.USER_VISIBLE, () -> {
                continueFetchGifAfterDisk(url, clientName, callback, cachedGif, startTimeMillis);
            });
        });
    }

    @VisibleForTesting
    void continueFetchGifAfterDisk(String url, String clientName, Callback<BaseGifImage> callback,
            BaseGifImage cachedGif, long startTimeMillis) {
        if (cachedGif != null) {
            callback.onResult(cachedGif);
            reportEvent(clientName, ImageFetcherEvent.JAVA_DISK_CACHE_HIT);
            getImageFetcherBridge().reportCacheHitTime(clientName, startTimeMillis);
        } else {
            getImageFetcherBridge().fetchGif(
                    getConfig(), url, clientName, (BaseGifImage gifFromNative) -> {
                        callback.onResult(gifFromNative);
                        getImageFetcherBridge().reportTotalFetchTimeFromNative(
                                clientName, startTimeMillis);
                    });
        }
    }

    /**
     * Tries to load the gif from disk, if not it falls back to the bridge.
     */
    @Override
    public void fetchImage(
            String url, String clientName, int width, int height, Callback<Bitmap> callback) {
        fetchImage(ImageFetcher.Params.create(url, clientName, width, height), callback);
    }

    @Override
    public void fetchImage(final Params params, Callback<Bitmap> callback) {
        long startTimeMillis = System.currentTimeMillis();
        PostTask.postTask(TaskTraits.USER_VISIBLE, () -> {
            // Try to read the bitmap from disk, then post back to the ui thread.
            String filePath = getImageFetcherBridge().getFilePath(params.url);
            Bitmap bitmap = mImageLoader.tryToLoadImageFromDisk(filePath);
            PostTask.postTask(UiThreadTaskTraits.USER_VISIBLE, () -> {
                continueFetchImageAfterDisk(params, callback, bitmap, startTimeMillis);
            });
        });
    }

    @VisibleForTesting
    void continueFetchImageAfterDisk(final ImageFetcher.Params params, Callback<Bitmap> callback,
            Bitmap cachedBitmap, long startTimeMillis) {
        if (cachedBitmap != null) {
            // In case the image's dimensions on disk don't match the desired dimensions.
            cachedBitmap = ImageFetcher.resizeImage(cachedBitmap, params.width, params.height);
            callback.onResult(cachedBitmap);
            reportEvent(params.clientName, ImageFetcherEvent.JAVA_DISK_CACHE_HIT);
            getImageFetcherBridge().reportCacheHitTime(params.clientName, startTimeMillis);
        } else {
            getImageFetcherBridge().fetchImage(getConfig(), params, (Bitmap bitmapFromNative) -> {
                callback.onResult(bitmapFromNative);
                getImageFetcherBridge().reportTotalFetchTimeFromNative(
                        params.clientName, startTimeMillis);
            });
        }
    }

    @Override
    public void clear() {}

    @Override
    public @ImageFetcherConfig int getConfig() {
        return ImageFetcherConfig.DISK_CACHE_ONLY;
    }
}

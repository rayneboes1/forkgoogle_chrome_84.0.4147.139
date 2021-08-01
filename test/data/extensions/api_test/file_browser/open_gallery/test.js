// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

/**
 * Runs tests to verify that file browser tasks for natively supported file
 * types are handled by the appropriate default-installed system app.
 */

const kTestPng = 'test_dir/test_file.png';
const kTestRawImage = 'test_dir/test_file.arw';

/**
 * Finds the `volumeType` volume then resolves the provided `path` as an Entry.
 */
function getFileEntry(volumeType, path) {
  return new Promise(resolve => {
    chrome.fileManagerPrivate.getVolumeMetadataList(list => {
      const volume = list.find(v => v.volumeType === volumeType);
      chrome.test.assertTrue(!!volume, `${volumeType} volume not found.`);
      chrome.fileSystem.requestFileSystem({volumeId: volume.volumeId}, fs => {
        fs.root.getFile(
            path, {},
            entry => {
              resolve(entry);
            },
            fileError => {
              chrome.test.fail(
                  `Unable to getFile "${path}": ${fileError.message}`);
            });
      });
    });
  });
}

/**
 * A method the camera app uses to open its "camera roll". Instrumented for
 * testing. See chromeos/camera/src/js/browser_proxy/browser_proxy.js.
 */
function openGallery(entry) {
  // "nlkncpkkdoccmpiclbokaimcnedabhhm" is the Gallery component chrome app's
  // extension ID. This task id is hard-coded in the Camera component app.
  const id = 'nlkncpkkdoccmpiclbokaimcnedabhhm|app|open';
  function taskCallback(taskResult) {
    chrome.test.assertEq(
        chrome.fileManagerPrivate.TaskResult.MESSAGE_SENT, taskResult);
    chrome.test.succeed();
  }
  chrome.fileManagerPrivate.executeTask(id, [entry], taskCallback);
}

function testPngOpensGallery() {
  getFileEntry('testing', kTestPng).then(openGallery);
}

function testRawOpensGallery() {
  getFileEntry('testing', kTestRawImage).then(openGallery);
}

// Handle the case where JSTestStarter has already injected a test to run.
if (self.testNameToRun) {
  chrome.test.runTests([self[testNameToRun]]);
}

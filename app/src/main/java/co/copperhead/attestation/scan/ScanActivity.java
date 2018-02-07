/*
 * FreeOTP
 *
 * Authors: Nathaniel McCallum <npmccallum@redhat.com>
 * Authors: Siemens AG <max.wittig@siemens.com>
 *
 * Copyright (C) 2013  Nathaniel McCallum, Red Hat
 * Copyright (C) 2017  Max Wittig, Siemens AG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package co.copperhead.attestation.scan;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import co.copperhead.attestation.R;
import io.fotoapparat.Fotoapparat;
import io.fotoapparat.parameter.ScaleType;
import io.fotoapparat.view.CameraView;

import static io.fotoapparat.selector.FocusModeSelectorsKt.autoFocus;
import static io.fotoapparat.selector.FocusModeSelectorsKt.continuousFocusPicture;
import static io.fotoapparat.selector.FocusModeSelectorsKt.fixed;
import static io.fotoapparat.selector.LensPositionSelectorsKt.back;
import static io.fotoapparat.selector.ResolutionSelectorsKt.highestResolution;
import static io.fotoapparat.selector.SelectorsKt.firstAvailable;

public class ScanActivity extends Activity {
    private Fotoapparat fotoapparat;
    private static ScanBroadcastReceiver receiver;

    public class ScanBroadcastReceiver extends BroadcastReceiver {
        public static final String ACTION = "co.copperhead.attestation.ACTION_CODE_SCANNED";

        @Override
        public void onReceive(Context context, Intent intent) {
            String text = intent.getStringExtra("scanResult");
            sendResult(text);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            this.unregisterReceiver(receiver);
        } catch (IllegalArgumentException e) {
            // catch exception, when trying to unregister receiver again
            // there seems to be no way to check, if receiver if registered
        }
    }

    private void sendResult(String text) {
        Intent result = new Intent("co.copperhead.attestation.RESULT_ACTION");
        result.putExtra("SCAN_RESULT", text);
        setResult(Activity.RESULT_OK, result);
        fotoapparat.stop();
        finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        receiver = new ScanBroadcastReceiver();
        this.registerReceiver(receiver, new IntentFilter(ScanBroadcastReceiver.ACTION));
        setContentView(R.layout.scan);
        CameraView cameraView = findViewById(R.id.camera_view);

        fotoapparat = Fotoapparat
                .with(this)
                .into(cameraView)
                .previewScaleType(ScaleType.CenterCrop)
                .previewResolution(highestResolution())
                .lensPosition(back())
                .focusMode(firstAvailable(
                        continuousFocusPicture(),
                        autoFocus(),
                        fixed()
                ))
                .frameProcessor(new ScanFrameProcessor(this))
                .build();
    }

    @Override
    protected void onStart() {
        super.onStart();
        fotoapparat.start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        fotoapparat.stop();
    }
}

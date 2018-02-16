package co.copperhead.attestation;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.ViewGroup;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;

import java.util.ArrayList;
import java.util.Collections;

import me.dm7.barcodescanner.core.IViewFinder;
import me.dm7.barcodescanner.core.ViewFinderView;
import me.dm7.barcodescanner.zxing.ZXingScannerView;

public class QRScannerActivity extends Activity implements ZXingScannerView.ResultHandler {
    private static final String TAG = "QRScannerActivity";

    private ZXingScannerView mScannerView;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_qrscanner);
        ViewGroup contentFrame = (ViewGroup) findViewById(R.id.content_frame);
        mScannerView = new ZXingScannerView(this) {
            @Override
            protected IViewFinder createViewFinderView(Context context) {
                return new SquareViewFinderView(context);
            }
        };
        contentFrame.addView(mScannerView);
        mScannerView.setFormats(new ArrayList<BarcodeFormat>(Collections.singletonList(BarcodeFormat.QR_CODE)));
    }

    @Override
    public void onResume() {
        super.onResume();
        mScannerView.setResultHandler(this);
        mScannerView.startCamera();
    }

    @Override
    public void onPause() {
        super.onPause();
        mScannerView.stopCamera();
    }

    @Override
    public void handleResult(Result rawResult) {
        Intent result = new Intent("co.copperhead.attestation.RESULT_ACTION");
        result.putExtra("SCAN_RESULT", rawResult.getText());
        setResult(Activity.RESULT_OK, result);
        mScannerView.stopCamera();
        finish();
    }

    private static class SquareViewFinderView extends ViewFinderView {
        public SquareViewFinderView(Context context) {
            super(context);
            init();
        }

        public SquareViewFinderView(Context context, AttributeSet attrs) {
            super(context, attrs);
            init();
        }

        private void init() {
            setSquareViewFinder(true);
        }

        @Override
        public void onDraw(Canvas canvas) {
            super.onDraw(canvas);
        }

    }
}

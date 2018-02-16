package co.copperhead.attestation;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;

import java.util.ArrayList;
import java.util.Collections;

import me.dm7.barcodescanner.zxing.ZXingScannerView;

public class QRScannerActivity extends Activity implements ZXingScannerView.ResultHandler {
    private static final String TAG = "QRScannerActivity";

    private ZXingScannerView mScannerView;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        mScannerView = new ZXingScannerView(this);
        setContentView(mScannerView);
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
}

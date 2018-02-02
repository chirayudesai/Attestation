package co.copperhead.attestation;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.method.ScrollingMovementMethod;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.EnumMap;
import java.util.Map;

import static android.graphics.Color.BLACK;
import static android.graphics.Color.WHITE;

public class AttestationActivity extends AppCompatActivity {
    public static final String TAG = "CopperheadAttestation";
    static final String STATE_OUTPUT = "output";

    AsyncTask<Object, String, Void> task = null;

    TextView textView;
    ImageView mView;

    Boolean mIsAuditor = false;
    Boolean mIsAuditee = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attestation);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        Button auditee = (Button) findViewById(R.id.auditee);
        Button auditor = (Button) findViewById(R.id.auditor);

        auditee.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "Auditee");
                mIsAuditor = false;
                mIsAuditee = true;
                auditee.setVisibility(View.GONE);
                auditor.setVisibility(View.GONE);
                doItAuditee();
            }
        });

        auditor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "Auditor");
                mIsAuditor = true;
                mIsAuditee = false;
                auditee.setVisibility(View.GONE);
                auditor.setVisibility(View.GONE);
                doItAuditor();
            }
        });

        textView = (TextView) findViewById(R.id.textview);
        textView.setMovementMethod(new ScrollingMovementMethod());

        if (savedInstanceState != null) {
            textView.setText(savedInstanceState.getString(STATE_OUTPUT));
        }

        mView = (ImageView) findViewById(R.id.imageview);
    }

    @Override
    public void onSaveInstanceState(final Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putString(STATE_OUTPUT, textView.getText().toString());
    }

    private String repeatString(String s, int count){
        StringBuilder r = new StringBuilder();
        for (int i = 0; i < count; i++) {
            r.append(s);
        }
        return r.toString();
    }

    private void doItAuditee() {
        Log.d(TAG, "doItAuditee");
        // generate qr
        String fingerprint = repeatString(Build.FINGERPRINT, 26);
        String fingerprint64 = Base64.encodeToString(fingerprint.getBytes(), Base64.DEFAULT);
        Log.d(TAG, "doItAuditee " + fingerprint64.length());
        Bitmap bitmap = createQrCode(fingerprint64);

        mView.setImageBitmap(bitmap);

        // now tap to scan
        mView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "Auditee qr view");
                view.setVisibility(View.GONE);
                showQrScanner("auditor");
            }
        });
        // show results
    }

    private void showAuditeeResults(String result) {
        Log.d(TAG, "showAuditeeResukts " + result);
        TextView textView = (TextView) findViewById(R.id.textview);
        textView.setText(result);
    }

    private void doItAuditor() {
        Log.d(TAG, "do it auditor");
        // scan qr
        showQrScanner("auditor");
    }

    private void continueAuditor(String result) {
        // generate key based on challenge from qr
        Bitmap bitmap = createQrCode(result + Build.FINGERPRINT);

        // show qr containing key
        mView.setImageBitmap(bitmap);
    }

    private Bitmap createQrCode(String contents) {
        BitMatrix result;
        try {
            QRCodeWriter writer = new QRCodeWriter();
            Map<EncodeHintType,Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);
            hints.put(EncodeHintType.QR_VERSION, 40);
            result = writer.encode(contents, BarcodeFormat.QR_CODE, mView.getWidth(),
                    mView.getWidth(), null);
        } catch (WriterException e) {
            return null;
        }

        int width = result.getWidth();
        int height = result.getHeight();
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            int offset = y * width;
            for (int x = 0; x < width; x++) {
                pixels[offset + x] = result.get(x, y) ? BLACK : WHITE;
            }
        }

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }

    private void showQrScanner(String initiator) {
        IntentIntegrator integrator = new IntentIntegrator(this);

        integrator.initiateScan(IntentIntegrator.QR_CODE_TYPES);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        Log.d(TAG, "on scan");
        IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);
        if (scanResult != null) {
            // handle scan result
            String contents = scanResult.getContents();
            Log.d(TAG, "key");
            if (mIsAuditor) {
                continueAuditor(contents);
            } else if (mIsAuditee) {
                showAuditeeResults(contents);
            }
        }
    }

    private void doIt() {
        TextView textView = (TextView) findViewById(R.id.textview);
        if (task != null && task.getStatus() == AsyncTask.Status.RUNNING) {
            return;
        }
        textView.setText("");
        try {
            task = new AttestationService(textView).execute(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_attestation, menu);
        return true;
    }
}

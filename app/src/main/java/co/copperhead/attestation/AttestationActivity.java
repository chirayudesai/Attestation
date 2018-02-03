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

import java.io.UnsupportedEncodingException;
import java.util.EnumMap;
import java.util.Map;

import static android.graphics.Color.BLACK;
import static android.graphics.Color.WHITE;

public class AttestationActivity extends AppCompatActivity {
    private static final String TAG = "CopperheadAttestation";

    private static final String STATE_AUDITOR_CHALLENGE = "auditor_challenge";
    private static final String STATE_STAGE = "stage";
    private static final String STATE_OUTPUT = "output";

    private AsyncTask<Object, String, byte[]> task = null;

    private TextView textView;
    private ImageView mView;

    private enum Stage {
        None,
        Auditee,
        Auditor
    }

    private Stage mStage = Stage.None;
    private byte[] auditorChallenge;

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
                mStage = Stage.Auditee;
                auditee.setVisibility(View.GONE);
                auditor.setVisibility(View.GONE);
                runAuditee();
            }
        });

        auditor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "Auditor");
                mStage = Stage.Auditor;
                auditee.setVisibility(View.GONE);
                auditor.setVisibility(View.GONE);
                runAuditor();
            }
        });

        textView = (TextView) findViewById(R.id.textview);
        textView.setMovementMethod(new ScrollingMovementMethod());

        mView = (ImageView) findViewById(R.id.imageview);

        if (savedInstanceState != null) {
            auditorChallenge = savedInstanceState.getByteArray(STATE_AUDITOR_CHALLENGE);
            mStage = Stage.valueOf(savedInstanceState.getString(STATE_STAGE));
            textView.setText(savedInstanceState.getString(STATE_OUTPUT));
        }

        if (mStage != Stage.None) {
            auditee.setVisibility(View.GONE);
            auditor.setVisibility(View.GONE);
        }
    }

    @Override
    public void onSaveInstanceState(final Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putByteArray(STATE_AUDITOR_CHALLENGE, auditorChallenge);
        savedInstanceState.putString(STATE_STAGE, mStage.name());
        savedInstanceState.putString(STATE_OUTPUT, textView.getText().toString());
    }

    private String logFormatBytes(final byte[] bytes) {
        return String.format("%d binary bytes logged here as base64 (%s)", bytes.length,
                Base64.encodeToString(bytes, Base64.NO_WRAP));
    }

    private void runAuditor() {
        Log.d(TAG, "runAuditor");
        // generate qr
        auditorChallenge = AttestationService.getChallenge();
        Log.d(TAG, "sending random challenge: " + logFormatBytes(auditorChallenge));
        Bitmap bitmap = createQrCode(auditorChallenge);

        mView.setImageBitmap(bitmap);

        // now tap to scan
        mView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "Auditor qr view");
                view.setVisibility(View.GONE);
                showQrScanner("auditor");
            }
        });
        // show results
    }

    private void showAuditorResults(final byte[] serialized) {
        Log.d(TAG, "received attestation: " + logFormatBytes(serialized));

        if (task != null && task.getStatus() == AsyncTask.Status.RUNNING) {
            return;
        }
        textView.setText("");
        try {
            task = new AttestationService(this, textView).execute(true, serialized, auditorChallenge);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void runAuditee() {
        Log.d(TAG, "runAuditee");
        // scan qr
        showQrScanner("auditee");
    }

    private void continueAuditee(final byte[] challenge) {
        Log.d(TAG, "received random challenge: " + logFormatBytes(challenge));

        if (task != null && task.getStatus() == AsyncTask.Status.RUNNING) {
            return;
        }
        textView.setText("");
        try {
            task = new AttestationService(this, textView).execute(false, challenge);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void continueAuditeeShowAttestation(final byte[] serialized) {
        Log.d(TAG, "sending attestation: " + logFormatBytes(serialized));
        Bitmap bitmap = createQrCode(serialized);
        mView.setImageBitmap(bitmap);
    }

    private Bitmap createQrCode(final byte[] contents) {
        BitMatrix result;
        try {
            QRCodeWriter writer = new QRCodeWriter();
            Map<EncodeHintType,Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, "ISO-8859-1");
            try {
                result = writer.encode(new String(contents, "ISO-8859-1"), BarcodeFormat.QR_CODE, mView.getWidth(),
                        mView.getWidth(), hints);
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException("ISO-8859-1 not supported", e);
            }
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

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        Log.d(TAG, "on scan");
        IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);
        if (scanResult != null) {
            // handle scan result
            final byte[] contents;
            try {
                contents = scanResult.getContents().getBytes("ISO-8859-1");
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException("ISO-8859-1 not supported", e);
            }
            Log.d(TAG, "key");
            if (mStage == Stage.Auditee) {
                continueAuditee(contents);
            } else if (mStage == Stage.Auditor) {
                showAuditorResults(contents);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_attestation, menu);
        return true;
    }
}

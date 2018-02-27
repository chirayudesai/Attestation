package co.copperhead.attestation;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.method.ScrollingMovementMethod;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.UnsupportedEncodingException;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import static android.graphics.Color.BLACK;
import static android.graphics.Color.WHITE;

public class AttestationActivity extends AppCompatActivity {
    private static final String TAG = "AttestationActivity";

    private static final String STATE_AUDITEE_PAIRING = "auditee_pairing";
    private static final String STATE_AUDITEE_SERIALIZED_ATTESTATION = "auditee_serialized_attestation";
    private static final String STATE_AUDITOR_CHALLENGE = "auditor_challenge";
    private static final String STATE_STAGE = "stage";
    private static final String STATE_OUTPUT = "output";

    private static final int GENERATE_REQUEST_CODE = 0;
    private static final int VERIFY_REQUEST_CODE = 1;
    private static final int SCAN_REQUEST_CODE = 2;

    private static final int PERMISSIONS_REQUEST_CAMERA = 10;

    private TextView textView;
    private ImageView mView;
    private Button auditee;
    private Button auditor;
    private TextView auditeeHint;
    private TextView auditorHint;

    private enum Stage {
        None,
        Auditee,
        AuditeeGenerate,
        AuditeeResults,
        Auditor,
        AuditorResults
    }

    private Stage mStage = Stage.None;
    private boolean auditeePairing;
    private byte[] auditeeSerializedAttestation;
    private byte[] auditorChallenge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attestation);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        auditee = findViewById(R.id.auditee);
        auditor = findViewById(R.id.auditor);

        auditee.setOnClickListener((final View view) -> {
            if (!Build.DEVICE.equals("taimen") && !Build.DEVICE.equals("walleye")) {
                Toast.makeText(this, getString(R.string.unsupported_auditee),
                        Toast.LENGTH_LONG).show();
                return;
            }
            mStage = Stage.Auditee;
            runAuditee();
        });

        auditor.setOnClickListener(view -> {
            mStage = Stage.Auditor;
            auditee.setVisibility(View.GONE);
            auditor.setVisibility(View.GONE);
            auditeeHint.setVisibility(View.GONE);
            auditorHint.setVisibility(View.GONE);
            runAuditor();
        });

        textView = findViewById(R.id.textview);
        textView.setMovementMethod(new ScrollingMovementMethod());

        auditeeHint = findViewById(R.id.textview_auditee_hint);
        auditorHint = findViewById(R.id.textview_auditor_hint);

        mView = findViewById(R.id.imageview);

        if (savedInstanceState != null) {
            auditeePairing = savedInstanceState.getBoolean(STATE_AUDITEE_PAIRING);
            auditeeSerializedAttestation = savedInstanceState.getByteArray(STATE_AUDITEE_SERIALIZED_ATTESTATION);
            auditorChallenge = savedInstanceState.getByteArray(STATE_AUDITOR_CHALLENGE);
            mStage = Stage.valueOf(savedInstanceState.getString(STATE_STAGE));
            textView.setText(savedInstanceState.getString(STATE_OUTPUT));
        }

        final ViewTreeObserver vto = mView.getViewTreeObserver();
        vto.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                mView.getViewTreeObserver().removeOnPreDrawListener(this);
                if (mStage != Stage.None) {
                    auditee.setVisibility(View.GONE);
                    auditor.setVisibility(View.GONE);
                    auditeeHint.setVisibility(View.GONE);
                    auditorHint.setVisibility(View.GONE);
                    if (mStage == Stage.Auditee) {
                        runAuditee();
                    } else if (mStage == Stage.AuditeeResults) {
                        auditeeShowAttestation(auditeeSerializedAttestation);
                    } else if (mStage == Stage.Auditor) {
                        runAuditor();
                    }
                }
                return true;
            }
        });
    }

    @Override
    public void onSaveInstanceState(final Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putBoolean(STATE_AUDITEE_PAIRING, auditeePairing);
        savedInstanceState.putByteArray(STATE_AUDITEE_SERIALIZED_ATTESTATION, auditeeSerializedAttestation);
        savedInstanceState.putByteArray(STATE_AUDITOR_CHALLENGE, auditorChallenge);
        savedInstanceState.putString(STATE_STAGE, mStage.name());
        savedInstanceState.putString(STATE_OUTPUT, textView.getText().toString());
    }

    private static String logFormatBytes(final byte[] bytes) {
        return String.format(Locale.US, "%d binary bytes logged here as base64 (%s)", bytes.length,
                Base64.encodeToString(bytes, Base64.NO_WRAP));
    }

    private void runAuditor() {
        if (auditorChallenge == null) {
            auditorChallenge = AttestationProtocol.getChallengeMessage(this);
        }
        Log.d(TAG, "sending random challenge: " + logFormatBytes(auditorChallenge));

        mView.setImageBitmap(createQrCode(auditorChallenge));
        textView.setText(R.string.qr_code_scan_hint_auditor);

        mView.setOnClickListener(view -> showQrScanner("Auditor"));
    }

    private void showAuditorResults(final byte[] serialized) {
        Log.d(TAG, "received attestation: " + logFormatBytes(serialized));

        final PendingIntent pending = createPendingResult(VERIFY_REQUEST_CODE, new Intent(), 0);
        final Intent intent = new Intent(this, VerifyAttestationService.class);
        intent.putExtra(VerifyAttestationService.EXTRA_CHALLENGE_MESSAGE, auditorChallenge);
        intent.putExtra(VerifyAttestationService.EXTRA_SERIALIZED, serialized);
        intent.putExtra(VerifyAttestationService.EXTRA_PENDING_RESULT, pending);
        startService(intent);
    }

    private void runAuditee() {
        showQrScanner("Auditee");
    }

    private void continueAuditee(final byte[] challenge) {
        Log.d(TAG, "received random challenge: " + logFormatBytes(challenge));

        final PendingIntent pending = createPendingResult(GENERATE_REQUEST_CODE, new Intent(), 0);
        final Intent intent = new Intent(this, GenerateAttestationService.class);
        intent.putExtra(GenerateAttestationService.EXTRA_CHALLENGE_MESSAGE, challenge);
        intent.putExtra(GenerateAttestationService.EXTRA_PENDING_RESULT, pending);
        startService(intent);
    }

    private void auditeeShowAttestation(final byte[] serialized) {
        Log.d(TAG, "sending attestation: " + logFormatBytes(serialized));
        auditeeSerializedAttestation = serialized;
        mStage = Stage.AuditeeResults;
        mView.setImageBitmap(createQrCode(serialized));
        if (auditeePairing) {
            textView.setText(R.string.qr_code_scan_hint_auditee_pairing);
        } else {
            textView.setText(R.string.qr_code_scan_hint_auditee);
        }
    }

    private Bitmap createQrCode(final byte[] contents) {
        final BitMatrix result;
        try {
            final QRCodeWriter writer = new QRCodeWriter();
            final Map<EncodeHintType,Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, "ISO-8859-1");
            try {
                final int size = Math.min(mView.getWidth(), mView.getHeight());
                result = writer.encode(new String(contents, "ISO-8859-1"), BarcodeFormat.QR_CODE,
                        size, size, hints);
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException("ISO-8859-1 not supported", e);
            }
        } catch (WriterException e) {
            return null;
        }

        final int width = result.getWidth();
        final int height = result.getHeight();
        final int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            final int offset = y * width;
            for (int x = 0; x < width; x++) {
                pixels[offset + x] = result.get(x, y) ? BLACK : WHITE;
            }
        }

        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.RGB_565);
    }

    private void showQrScanner(final String initiator) {
        Log.d(TAG, "showQrScanner: " + initiator);

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA},
                    PERMISSIONS_REQUEST_CAMERA);
        } else {
            startActivityForResult(new Intent(this, QRScannerActivity.class), SCAN_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_CAMERA: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startActivityForResult(new Intent(this, QRScannerActivity.class), SCAN_REQUEST_CODE);
                } else {
                    Toast.makeText(this, getString(R.string.camera_permission_denied),
                            Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
        Log.d(TAG, "onActivityResult " + requestCode + " " + resultCode);

        if (requestCode == GENERATE_REQUEST_CODE) {
            if (resultCode != GenerateAttestationService.RESULT_CODE) {
                throw new RuntimeException("unexpected result code");
            }
            if (intent.hasExtra(GenerateAttestationService.EXTRA_ATTESTATION_ERROR)) {
                textView.setText(R.string.generate_error);
                textView.append(intent.getStringExtra(GenerateAttestationService.EXTRA_ATTESTATION_ERROR));
                return;
            }
            auditeePairing = intent.getBooleanExtra(GenerateAttestationService.EXTRA_PAIRING, false);
            auditeeShowAttestation(intent.getByteArrayExtra(GenerateAttestationService.EXTRA_ATTESTATION));
        } else if (requestCode == VERIFY_REQUEST_CODE) {
            if (resultCode != VerifyAttestationService.RESULT_CODE) {
                throw new RuntimeException("unexpected result code");
            }
            if (intent.hasExtra(VerifyAttestationService.EXTRA_ERROR)) {
                textView.setText(getString(R.string.verify_error));
                textView.append(intent.getStringExtra(VerifyAttestationService.EXTRA_ERROR));
                return;
            }
            textView.setText(intent.getStringExtra(VerifyAttestationService.EXTRA_OUTPUT));
        } else if (requestCode == SCAN_REQUEST_CODE) {
            if (intent != null) {
                // handle scan result
                final String contents = intent.getStringExtra("SCAN_RESULT");
                if (contents == null) {
                    if (mStage == Stage.Auditee) {
                        mStage = Stage.None;
                    }
                    return;
                }
                final byte[] contentsBytes;
                try {
                    contentsBytes = contents.getBytes("ISO-8859-1");
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException("ISO-8859-1 not supported", e);
                }
                if (mStage == Stage.Auditee) {
                    mStage = Stage.AuditeeGenerate;
                    auditee.setVisibility(View.GONE);
                    auditor.setVisibility(View.GONE);
                    auditeeHint.setVisibility(View.GONE);
                    auditorHint.setVisibility(View.GONE);
                    continueAuditee(contentsBytes);
                } else if (mStage == Stage.Auditor) {
                    mStage = Stage.AuditorResults;
                    mView.setVisibility(View.GONE);
                    showAuditorResults(contentsBytes);
                } else {
                    Log.w(TAG, "received unexpected scan result");
                }
            } else {
                if (mStage == Stage.Auditee) {
                    mStage = Stage.None;
                }
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.menu_attestation, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_clear_auditee: {
                final Intent intent = new Intent(this, GenerateAttestationService.class);
                intent.putExtra(GenerateAttestationService.EXTRA_CLEAR, true);
                startService(intent);
                return true;
            }
            case R.id.action_clear_auditor: {
                final Intent intent = new Intent(this, VerifyAttestationService.class);
                intent.putExtra(VerifyAttestationService.EXTRA_CLEAR, true);
                startService(intent);
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (mStage == Stage.AuditeeResults || mStage == Stage.Auditor ||
                mStage == Stage.AuditorResults) {
            auditeeSerializedAttestation = null;
            auditorChallenge = null;
            mStage = Stage.None;
            textView.setText("");
            recreate();
            return;
        }
        super.onBackPressed();
    }
}

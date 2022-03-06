package li.power.app.wearos.odextesla;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.os.PowerManager;
import androidx.annotation.RequiresApi;
import androidx.wear.widget.ConfirmationOverlay;
import androidx.wear.widget.drawer.WearableNavigationDrawerView;

import li.power.app.wearos.odextesla.databinding.ActivityMainBinding;
import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.*;
import java.io.IOException;
import java.io.InputStream;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.spec.ECGenParameterSpec;

public class MainActivity extends Activity {

    private TextView mTextView;
    private ActivityMainBinding binding;
    private ImageButton about_button;

    private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";
    private static final String KEY_ALIAS = "tesla_nak";
    private static final String RSA_MODE = "RSA/ECB/PKCS1Padding";


    private KeyStore keyStore;
    private SharedPreferences sharedPreferences;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        sharedPreferences = getSharedPreferences(KEY_ALIAS, Context.MODE_PRIVATE);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        mTextView = binding.text;
        about_button = binding.button;

        try {
            keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
            keyStore.load(null);
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                generateEccPrivateKey();
            }
        } catch (KeyStoreException | CertificateException | IOException | NoSuchAlgorithmException e) {
            mTextView.setText("Failed to initialize keystore.");
            e.printStackTrace();
        } catch (InvalidAlgorithmParameterException | NoSuchProviderException | InvalidKeyException | BadPaddingException | NoSuchPaddingException | IllegalBlockSizeException e) {
            mTextView.setText("Failed to generate keypair.");
            e.printStackTrace();
        }
        mTextView.setText("Tesla Wear Key is ready to use.");

        PowerManager powerManager = (PowerManager) this.getSystemService(Context.POWER_SERVICE);

        if (powerManager.isPowerSaveMode()) {
            mTextView.setText("Power save mode is on, unlocking the car won't work.");
            return;
        }

        String[] text = {"Tesla Wear Key is ready to use.", "Touch the wearable against the door pillar to unlock the vehicle."};
        setUpFadeAnimation(mTextView, text);

        // TODO: Change between the 2 different text modes when power save get's changed
        // The android intend that notifies this is: ACTION_POWER_SAVE_MODE_CHANGED

        about_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAbout();
            }
        });
    }

    private String license_string;

    private void loadLicenseText(View layout) {
        TextView text = layout.findViewById(R.id.license);

        if(license_string) {
            text.setText(license_string);
            return;
        }

        try {
            Resources res = getResources();
            InputStream in_s = res.openRawResource(R.raw.license);
            byte[] b = new byte[in_s.available()];
            in_s.read(b);
            license_string = new String(b)
            text.setText(license_string);
        } catch (Exception e) {
            license_string = "Error: can't show License."
            text.setText(license_string);
        }
    }

    private void showAbout() {
            Dialog dialog = new Dialog(this);
            View myLayout = getLayoutInflater().inflate(R.layout.about, null);

            loadLicenseText(myLayout);

            dialog.setContentView(myLayout);
            dialog.show();
    }


    private void generateEccPrivateKey() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException, NoSuchPaddingException, IllegalBlockSizeException, KeyStoreException, BadPaddingException, InvalidKeyException {
        SecureRandom random = new SecureRandom();
        byte[] pk = new byte[32];
        random.nextBytes(pk);
        generateRsaKey();
        sharedPreferences.edit().putString(KEY_ALIAS, Hex.encodeHexString(encryptRSA(pk))).apply();
    }

    private byte[] encryptRSA(byte[] plainText) throws KeyStoreException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        PublicKey publicKey = keyStore.getCertificate(KEY_ALIAS).getPublicKey();

        Cipher cipher = Cipher.getInstance(RSA_MODE);
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);

        return cipher.doFinal(plainText);
    }


    private void generateRsaKey() throws NoSuchAlgorithmException, NoSuchProviderException, InvalidAlgorithmParameterException {

        KeyGenParameterSpec keyGenParameterSpec = new KeyGenParameterSpec
                .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                .build();

        KeyPairGenerator keyPairGenerator = KeyPairGenerator
                .getInstance(KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE_PROVIDER);
        keyPairGenerator.initialize(keyGenParameterSpec);
        keyPairGenerator.generateKeyPair();
    }

    private void setUpFadeAnimation(final TextView textView, final String[] text) {
        final Animation fadeIn = new AlphaAnimation(0.0f, 1.0f);
        fadeIn.setDuration(1500);
        fadeIn.setStartOffset(0);

        final Animation fadeOut = new AlphaAnimation(1.0f, 0.0f);
        fadeOut.setDuration(1500);
        fadeOut.setStartOffset(5000);

        fadeIn.setAnimationListener(new Animation.AnimationListener(){
            int current_index = 0;

            @Override
            public void onAnimationEnd(Animation arg0) {
                // start fadeOut when fadeIn ends (continue)
                textView.startAnimation(fadeOut);
            }

            @Override
            public void onAnimationRepeat(Animation arg0) {
            }

            @Override
            public void onAnimationStart(Animation arg0) {
                current_index = (current_index + 1) % text.length;
                textView.setText(text[current_index]);
            }
        });

        fadeOut.setAnimationListener(new Animation.AnimationListener() {

            @Override
            public void onAnimationEnd(Animation arg0) {
                // start fadeIn when fadeOut ends (repeat)
                textView.startAnimation(fadeIn);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }

            @Override
            public void onAnimationStart(Animation arg0) {
            }
        });

	textView.startAnimation(fadeOut);
    }
}

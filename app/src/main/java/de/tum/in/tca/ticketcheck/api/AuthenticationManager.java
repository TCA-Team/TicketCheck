package de.tum.in.tca.ticketcheck.api;

import android.app.AlertDialog;
import android.content.Context;
import android.util.Base64;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.UUID;

import androidx.annotation.NonNull;
import de.tum.in.tca.ticketcheck.R;
import de.tum.in.tca.ticketcheck.api.exception.NoPrivateKey;
import de.tum.in.tca.ticketcheck.api.exception.NoPublicKey;
import de.tum.in.tca.ticketcheck.component.ticket.payload.TicketSuccessResponse;
import de.tum.in.tca.ticketcheck.utils.Const;
import de.tum.in.tca.ticketcheck.utils.RSASigner;
import de.tum.in.tca.ticketcheck.utils.Utils;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * This provides methods to authenticate this app installation with the tumcabe server and other instances requiring a pki.
 */
public class AuthenticationManager {

    private final static String ALGORITHM = "RSA";
    private final static int RSA_KEY_SIZE = 4096;
    private static String uniqueID;

    private final Context mContext;

    public AuthenticationManager(Context context) {
        mContext = context;
    }

    /**
     * Gets an unique id that identifies this device.
     * Should only reset after a reinstall or wiping of the settingsPrefix.
     *
     * @return Unique device id
     */
    public static synchronized String getDeviceID(Context context) {
        if (uniqueID == null) {
            uniqueID = Utils.getSetting(context, Const.PREF_UNIQUE_ID, "");
            if (uniqueID.isEmpty()) {
                uniqueID = UUID.randomUUID().toString();
                Utils.setSetting(context, Const.PREF_UNIQUE_ID, uniqueID);
            }
        }
        return uniqueID;
    }

    private static KeyPairGenerator getKeyPairGeneratorInstance() {
        try {
            return KeyPairGenerator.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            // We don't support platforms without RSA
            throw new AssertionError(e);
        }
    }

    private static KeyFactory getKeyFactoryInstance() {
        try {
            return KeyFactory.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            // We don't support platforms without RSA
            throw new AssertionError(e);
        }
    }

    /**
     * Get the private key as string.
     *
     * @return
     * @throws NoPrivateKey
     */
    private String getPrivateKeyString() throws NoPrivateKey {
        String key = Utils.getSetting(mContext, Const.PRIVATE_KEY, "");
        if (key.isEmpty()) {
            throw new NoPrivateKey();
        }
        return key;
    }

    /**
     * Gets the public key as string.
     *
     * @return
     * @throws NoPublicKey
     */
    private String getPublicKeyString() throws NoPublicKey {
        String key = Utils.getSetting(mContext, Const.PUBLIC_KEY, "");
        if (key.isEmpty()) {
            throw new NoPublicKey();
        }
        return key;
    }

    /**
     * Loads the private key as an object.
     *
     * @return The private key object
     */
    private PrivateKey getPrivateKey() throws NoPrivateKey {
        byte[] privateKeyBytes = Base64.decode(this.getPrivateKeyString(), Base64.DEFAULT);
        try {
            return getKeyFactoryInstance().generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
        } catch (InvalidKeySpecException e) {
            Utils.log(e);
        }
        return null;
    }

    /**
     * Sign a message with the currently stored private key.
     *
     * @param data String to be signed
     * @return signature used to verify this request
     * @throws NoPrivateKey
     */
    public String sign(String data) throws NoPrivateKey {
        RSASigner signer = new RSASigner(this.getPrivateKey());
        return signer.sign(data);
    }

    /**
     * Gets private key from preferences or generates one.
     *
     * @return true if a private key is present
     */
    public boolean generatePrivateKey() {
        // Try to retrieve private key
        try {
            //Try to get the private key
            getPrivateKeyString();

            //Re-upload it in the case it was not yet transmitted to the server
            String publicKey = getPublicKeyString();
            uploadKey(publicKey);

            // If we already have one don't create a new one
            return true;
        } catch (NoPrivateKey | NoPublicKey e) {
            //Otherwise catch a not existing private key exception and proceed generation
        }

        //Something went wrong, generate a new pair
        clearKeys();

        // If the key is not in shared preferences, a new generate key-pair
        KeyPair keyPair = generateKeyPair();

        //In order to store the preferences we need to encode them as base64 string
        String publicKeyString = keyToBase64(keyPair.getPublic().getEncoded());
        String privateKeyString = keyToBase64(keyPair.getPrivate().getEncoded());
        saveKeys(privateKeyString, publicKeyString);

        //New keys, need to re-upload
        uploadKey(publicKeyString);
        return true;
    }

    /**
     * Try to upload the public key to the server and remember that state.
     *
     * @param publicKey
     */
    private void uploadKey(String publicKey) {
        //If we already uploaded it we don't need to redo that
        if (Utils.getSettingBool(mContext, Const.PUBLIC_KEY_UPLOADED, false)) {
            return;
        }

        TUMCabeClient.getInstance(mContext).uploadAdminKey(publicKey, new Callback<TicketSuccessResponse>() {
            @Override
            public void onResponse(@NonNull Call<TicketSuccessResponse> call,
                                   @NonNull Response<TicketSuccessResponse> response) {
                TicketSuccessResponse ticketResponse = response.body();
                if (ticketResponse != null && ticketResponse.getSuccess()) {
                    Utils.setSetting(mContext, Const.PUBLIC_KEY_UPLOADED, true);
                    showSuccessDialog();
                } else {
                    Utils.log("Could not upload admin key: " + response.toString());
                    showErrorDialog();
                }
            }

            @Override
            public void onFailure(@NonNull Call<TicketSuccessResponse> call, @NonNull Throwable t) {
                Utils.log(t);
                showErrorDialog();
            }
        });
    }

    private void showSuccessDialog() {
        showDialog(R.string.admin_key_upload_success_title, R.string.admin_key_upload_success_message);
    }

    private void showErrorDialog() {
        showDialog(R.string.error, R.string.admin_key_upload_error_message);
    }

    private void showDialog(int titleResId, int messageResId) {
        new AlertDialog.Builder(mContext)
                .setTitle(mContext.getString(titleResId))
                .setMessage(messageResId)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    /**
     * Convert a byte array to a more manageable base64 string to store it in the preferences.
     */
    private static String keyToBase64(byte[] key) {
        return Base64.encodeToString(key, Base64.DEFAULT);
    }

    /**
     * Generates a keypair with the given ALGORITHM & size
     */
    private static KeyPair generateKeyPair() {
        KeyPairGenerator keyGen = getKeyPairGeneratorInstance();
        keyGen.initialize(AuthenticationManager.RSA_KEY_SIZE);
        return keyGen.generateKeyPair();
    }

    /**
     * Save private key in shared preferences.
     */
    private void saveKeys(String privateKeyString, String publicKeyString) {
        Utils.setSetting(mContext, Const.PRIVATE_KEY, privateKeyString);
        Utils.setSetting(mContext, Const.PUBLIC_KEY, publicKeyString);
    }

    /**
     * Reset all keys generated - this should actually never happen other than when a token is reset.
     */
    private void clearKeys() {
        this.saveKeys("", "");
        Utils.setSetting(mContext, Const.PUBLIC_KEY_UPLOADED, false);
    }

}

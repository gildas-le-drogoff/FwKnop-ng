package org.openintents.openpgp.util;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import org.openintents.openpgp.IOpenPgpService2;
import org.openintents.openpgp.OpenPgpError;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
public class OpenPgpApi {
    public static final String TAG = "OpenPgp API";
    public static final String SERVICE_INTENT_2 = "org.openintents.openpgp.IOpenPgpService2";
    
    public static final int API_VERSION = 11;
    
    
    public static final String ACTION_CHECK_PERMISSION = "org.openintents.openpgp.action.CHECK_PERMISSION";
    
    public static final String ACTION_SIGN = "org.openintents.openpgp.action.SIGN";
    
    public static final String ACTION_CLEARTEXT_SIGN = "org.openintents.openpgp.action.CLEARTEXT_SIGN";
    
    public static final String ACTION_DETACHED_SIGN = "org.openintents.openpgp.action.DETACHED_SIGN";
    
    public static final String ACTION_ENCRYPT = "org.openintents.openpgp.action.ENCRYPT";
    
    public static final String ACTION_SIGN_AND_ENCRYPT = "org.openintents.openpgp.action.SIGN_AND_ENCRYPT";
    
    public static final String ACTION_DECRYPT_VERIFY = "org.openintents.openpgp.action.DECRYPT_VERIFY";
    
    public static final String ACTION_DECRYPT_METADATA = "org.openintents.openpgp.action.DECRYPT_METADATA";
    
    public static final String ACTION_GET_SIGN_KEY_ID = "org.openintents.openpgp.action.GET_SIGN_KEY_ID";
    
    public static final String ACTION_GET_KEY_IDS = "org.openintents.openpgp.action.GET_KEY_IDS";
    
    public static final String ACTION_GET_KEY = "org.openintents.openpgp.action.GET_KEY";
    
    public static final String EXTRA_API_VERSION = "api_version";
    
    public static final String EXTRA_ACCOUNT_NAME = "account_name";
    
    
    
    public static final String EXTRA_REQUEST_ASCII_ARMOR = "ascii_armor";
    
    public static final String RESULT_DETACHED_SIGNATURE = "detached_signature";
    public static final String RESULT_SIGNATURE_MICALG = "signature_micalg";
    
    public static final String EXTRA_USER_IDS = "user_ids";
    public static final String EXTRA_KEY_IDS = "key_ids";
    public static final String EXTRA_SIGN_KEY_ID = "sign_key_id";
    
    public static final String EXTRA_PASSPHRASE = "passphrase";
    public static final String EXTRA_ORIGINAL_FILENAME = "original_filename";
    public static final String EXTRA_ENABLE_COMPRESSION = "enable_compression";
    
    public static final String EXTRA_USER_ID = "user_id";
    
    public static final String EXTRA_KEY_ID = "key_id";
    public static final String RESULT_KEY_IDS = "key_ids";
    
    public static final String RESULT_CODE = "result_code";
    
    public static final int RESULT_CODE_ERROR = 0;
    
    public static final int RESULT_CODE_SUCCESS = 1;
    
    
    public static final int RESULT_CODE_USER_INTERACTION_REQUIRED = 2;
    public static final String RESULT_ERROR = "error";
    public static final String RESULT_INTENT = "intent";
    
    public static final String EXTRA_DETACHED_SIGNATURE = "detached_signature";
    public static final String RESULT_SIGNATURE = "signature";
    public static final String RESULT_DECRYPTION = "decryption";
    public static final String RESULT_METADATA = "metadata";
    
    public static final String RESULT_CHARSET = "charset";
    
    public static final String EXTRA_CALL_UUID1 = "call_uuid1";
    public static final String EXTRA_CALL_UUID2 = "call_uuid2";
    IOpenPgpService2 mService;
    Context mContext;
    final AtomicInteger mPipeIdGen = new AtomicInteger();
    public OpenPgpApi(Context context, IOpenPgpService2 service) {
        this.mContext = context;
        this.mService = service;
    }
    public interface IOpenPgpCallback {
        void onReturn(final Intent result);
    }
    private static final ExecutorService sExecutor = Executors.newCachedThreadPool();
    private static final Handler sMainHandler = new Handler(Looper.getMainLooper());
    public void executeApiAsync(Intent data, InputStream is, OutputStream os, IOpenPgpCallback callback) {
        sExecutor.execute(() -> {
            final Intent result = executeApi(data, is, os);
            sMainHandler.post(() -> callback.onReturn(result));
        });
    }
    public Intent executeApi(Intent data, InputStream is, OutputStream os) {
        ParcelFileDescriptor input = null;
        try {
            if (is != null) {
                input = ParcelFileDescriptorUtil.pipeFrom(is);
            }
            return executeApi(data, input, os);
        } catch (Exception e) {
            Log.e(OpenPgpApi.TAG, "Exception in executeApi call", e);
            Intent result = new Intent();
            result.putExtra(RESULT_CODE, RESULT_CODE_ERROR);
            result.putExtra(RESULT_ERROR,
                    new OpenPgpError(OpenPgpError.CLIENT_SIDE_ERROR, e.getMessage()));
            return result;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    Log.e(OpenPgpApi.TAG, "IOException when closing ParcelFileDescriptor!", e);
                }
            }
        }
    }
    
    public Intent executeApi(Intent data, ParcelFileDescriptor input, OutputStream os) {
        ParcelFileDescriptor output = null;
        try {
            
            data.putExtra(EXTRA_API_VERSION, OpenPgpApi.API_VERSION);
            Intent result;
            Thread pumpThread =null;
            int outputPipeId = 0;
            if (os != null) {
                outputPipeId = mPipeIdGen.incrementAndGet();
                output = mService.createOutputPipe(outputPipeId);
                pumpThread = ParcelFileDescriptorUtil.pipeTo(os, output);
            }
            
            result = mService.execute(data, input, outputPipeId);
            
            
            
            result.setExtrasClassLoader(mContext.getClassLoader());
            
            if (pumpThread != null) {
                pumpThread.join();
            }
            return result;
        } catch (Exception e) {
            Log.e(OpenPgpApi.TAG, "Exception in executeApi call", e);
            Intent result = new Intent();
            result.putExtra(RESULT_CODE, RESULT_CODE_ERROR);
            result.putExtra(RESULT_ERROR,
                    new OpenPgpError(OpenPgpError.CLIENT_SIDE_ERROR, e.getMessage()));
            return result;
        } finally {
            
            if (output != null) {
                try {
                    output.close();
                } catch (IOException e) {
                    Log.e(OpenPgpApi.TAG, "IOException when closing ParcelFileDescriptor!", e);
                }
            }
        }
    }
}

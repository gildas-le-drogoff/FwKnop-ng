package org.openintents.openpgp.util;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import org.openintents.openpgp.IOpenPgpService2;
public class OpenPgpServiceConnection {
    
    public interface OnBound {
        public void onBound(IOpenPgpService2 service);
        public void onError(Exception e);
    }
    private Context mApplicationContext;
    private IOpenPgpService2 mService;
    private String mProviderPackageName;
    private OnBound mOnBoundListener;
    
    public OpenPgpServiceConnection(Context context, String providerPackageName) {
        this.mApplicationContext = context.getApplicationContext();
        this.mProviderPackageName = providerPackageName;
    }
    
    public OpenPgpServiceConnection(Context context, String providerPackageName,
                                    OnBound onBoundListener) {
        this(context, providerPackageName);
        this.mOnBoundListener = onBoundListener;
    }
    public IOpenPgpService2 getService() {
        return mService;
    }
    public boolean isBound() {
        return (mService != null);
    }
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = IOpenPgpService2.Stub.asInterface(service);
            if (mOnBoundListener != null) {
                mOnBoundListener.onBound(mService);
            }
        }
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }
    };
    
    public void bindToService() {
        
        if (mService == null) {
            try {
                Intent serviceIntent = new Intent(OpenPgpApi.SERVICE_INTENT_2);
                
                serviceIntent.setPackage(mProviderPackageName);
                boolean connect = mApplicationContext.bindService(serviceIntent, mServiceConnection,
                        Context.BIND_AUTO_CREATE);
                if (!connect) {
                    throw new Exception("bindService() returned false!");
                }
            } catch (Exception e) {
                if (mOnBoundListener != null) {
                    mOnBoundListener.onError(e);
                }
            }
        } else {
            
            if (mOnBoundListener != null) {
                mOnBoundListener.onBound(mService);
            }
        }
    }
    public void unbindFromService() {
        mApplicationContext.unbindService(mServiceConnection);
    }
}

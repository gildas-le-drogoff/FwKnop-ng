package org.openintents.openpgp;
import android.os.Parcel;
import android.os.Parcelable;
public class OpenPgpDecryptionResult implements Parcelable {
    
    public static final int PARCELABLE_VERSION = 1;
    
    public static final int RESULT_NOT_ENCRYPTED = -1;
    
    public static final int RESULT_INSECURE = 0;
    
    public static final int RESULT_ENCRYPTED = 1;
    int result;
    public int getResult() {
        return result;
    }
    public void setResult(int result) {
        this.result = result;
    }
    public OpenPgpDecryptionResult() {
    }
    public OpenPgpDecryptionResult(int result) {
        this.result = result;
    }
    public OpenPgpDecryptionResult(OpenPgpDecryptionResult b) {
        this.result = b.result;
    }
    public int describeContents() {
        return 0;
    }
    public void writeToParcel(Parcel dest, int flags) {
        
        dest.writeInt(PARCELABLE_VERSION);
        
        
        int sizePosition = dest.dataPosition();
        dest.writeInt(0);
        int startPosition = dest.dataPosition();
        
        dest.writeInt(result);
        
        int parcelableSize = dest.dataPosition() - startPosition;
        dest.setDataPosition(sizePosition);
        dest.writeInt(parcelableSize);
        dest.setDataPosition(startPosition + parcelableSize);
    }
    public static final Creator<OpenPgpDecryptionResult> CREATOR = new Creator<OpenPgpDecryptionResult>() {
        public OpenPgpDecryptionResult createFromParcel(final Parcel source) {
            source.readInt(); 
            int parcelableSize = source.readInt();
            int startPosition = source.dataPosition();
            OpenPgpDecryptionResult vr = new OpenPgpDecryptionResult();
            vr.result = source.readInt();
            
            source.setDataPosition(startPosition + parcelableSize);
            return vr;
        }
        public OpenPgpDecryptionResult[] newArray(final int size) {
            return new OpenPgpDecryptionResult[size];
        }
    };
    @Override
    public String toString() {
        return "\nresult: " + result;
    }
}

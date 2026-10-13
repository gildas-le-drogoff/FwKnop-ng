package org.openintents.openpgp;
import android.os.Parcel;
import android.os.Parcelable;
import org.openintents.openpgp.util.OpenPgpUtils;
import java.util.ArrayList;
public class OpenPgpSignatureResult implements Parcelable {
    
    public static final int PARCELABLE_VERSION = 2;
    
    public static final int RESULT_NO_SIGNATURE = -1;
    
    public static final int RESULT_INVALID_SIGNATURE = 0;
    
    public static final int RESULT_VALID_CONFIRMED = 1;
    
    public static final int RESULT_KEY_MISSING = 2;
    
    public static final int RESULT_VALID_UNCONFIRMED = 3;
    
    public static final int RESULT_INVALID_KEY_REVOKED = 4;
    
    public static final int RESULT_INVALID_KEY_EXPIRED = 5;
    
    public static final int RESULT_INVALID_INSECURE = 6;
    int result;
    boolean signatureOnly;
    String primaryUserId;
    ArrayList<String> userIds;
    long keyId;
    public int getResult() {
        return result;
    }
    public void setResult(int result) {
        this.result = result;
    }
    
    public boolean isSignatureOnly() {
        return signatureOnly;
    }
    
    public void setSignatureOnly(boolean signatureOnly) {
        this.signatureOnly = signatureOnly;
    }
    public String getPrimaryUserId() {
        return primaryUserId;
    }
    public void setPrimaryUserId(String primaryUserId) {
        this.primaryUserId = primaryUserId;
    }
    public ArrayList<String> getUserIds() {
        return userIds;
    }
    public void setUserIds(ArrayList<String> userIds) {
        this.userIds = userIds;
    }
    public long getKeyId() {
        return keyId;
    }
    public void setKeyId(long keyId) {
        this.keyId = keyId;
    }
    public OpenPgpSignatureResult() {
    }
    public OpenPgpSignatureResult(int signatureStatus, String signatureUserId,
                                  boolean signatureOnly, long keyId, ArrayList<String> userIds) {
        this.result = signatureStatus;
        this.signatureOnly = signatureOnly;
        this.primaryUserId = signatureUserId;
        this.keyId = keyId;
        this.userIds = userIds;
    }
    public OpenPgpSignatureResult(OpenPgpSignatureResult b) {
        this.result = b.result;
        this.primaryUserId = b.primaryUserId;
        this.signatureOnly = b.signatureOnly;
        this.keyId = b.keyId;
        this.userIds = b.userIds;
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
        dest.writeByte((byte) (signatureOnly ? 1 : 0));
        dest.writeString(primaryUserId);
        dest.writeLong(keyId);
        
        dest.writeStringList(userIds);
        
        int parcelableSize = dest.dataPosition() - startPosition;
        dest.setDataPosition(sizePosition);
        dest.writeInt(parcelableSize);
        dest.setDataPosition(startPosition + parcelableSize);
    }
    public static final Creator<OpenPgpSignatureResult> CREATOR = new Creator<OpenPgpSignatureResult>() {
        public OpenPgpSignatureResult createFromParcel(final Parcel source) {
            source.readInt(); 
            int parcelableSize = source.readInt();
            int startPosition = source.dataPosition();
            OpenPgpSignatureResult vr = new OpenPgpSignatureResult();
            vr.result = source.readInt();
            vr.signatureOnly = source.readByte() == 1;
            vr.primaryUserId = source.readString();
            vr.keyId = source.readLong();
            vr.userIds = new ArrayList<String>();
            source.readStringList(vr.userIds);
            
            source.setDataPosition(startPosition + parcelableSize);
            return vr;
        }
        public OpenPgpSignatureResult[] newArray(final int size) {
            return new OpenPgpSignatureResult[size];
        }
    };
    @Override
    public String toString() {
        String out = "\nresult: " + result;
        out += "\nprimaryUserId: " + primaryUserId;
        out += "\nuserIds: " + userIds;
        out += "\nsignatureOnly: " + signatureOnly;
        out += "\nkeyId: " + OpenPgpUtils.convertKeyIdToHex(keyId);
        return out;
    }
}

package org.openintents.openpgp;
import android.os.Parcel;
import android.os.Parcelable;
public class OpenPgpMetadata implements Parcelable {
    
    public static final int PARCELABLE_VERSION = 2;
    String filename;
    String mimeType;
    String charset;
    long modificationTime;
    long originalSize;
    public String getFilename() {
        return filename;
    }
    public String getMimeType() {
        return mimeType;
    }
    public long getModificationTime() {
        return modificationTime;
    }
    public long getOriginalSize() {
        return originalSize;
    }
    public String getCharset() {
        return charset;
    }
    public OpenPgpMetadata() {
    }
    public OpenPgpMetadata(String filename, String mimeType, long modificationTime,
            long originalSize, String charset) {
        this.filename = filename;
        this.mimeType = mimeType;
        this.modificationTime = modificationTime;
        this.originalSize = originalSize;
        this.charset = charset;
    }
    public OpenPgpMetadata(String filename, String mimeType, long modificationTime,
                           long originalSize) {
        this.filename = filename;
        this.mimeType = mimeType;
        this.modificationTime = modificationTime;
        this.originalSize = originalSize;
    }
    public OpenPgpMetadata(OpenPgpMetadata b) {
        this.filename = b.filename;
        this.mimeType = b.mimeType;
        this.modificationTime = b.modificationTime;
        this.originalSize = b.originalSize;
    }
    public int describeContents() {
        return 0;
    }
    public void writeToParcel(Parcel dest, int flags) {
        
        dest.writeInt(PARCELABLE_VERSION);
        
        
        int sizePosition = dest.dataPosition();
        dest.writeInt(0);
        int startPosition = dest.dataPosition();
        
        dest.writeString(filename);
        dest.writeString(mimeType);
        dest.writeLong(modificationTime);
        dest.writeLong(originalSize);
        
        dest.writeString(charset);
        
        int parcelableSize = dest.dataPosition() - startPosition;
        dest.setDataPosition(sizePosition);
        dest.writeInt(parcelableSize);
        dest.setDataPosition(startPosition + parcelableSize);
    }
    public static final Creator<OpenPgpMetadata> CREATOR = new Creator<OpenPgpMetadata>() {
        public OpenPgpMetadata createFromParcel(final Parcel source) {
            int version = source.readInt(); 
            int parcelableSize = source.readInt();
            int startPosition = source.dataPosition();
            OpenPgpMetadata vr = new OpenPgpMetadata();
            vr.filename = source.readString();
            vr.mimeType = source.readString();
            vr.modificationTime = source.readLong();
            vr.originalSize = source.readLong();
            if (version >= 2) {
                vr.charset = source.readString();
            }
            
            source.setDataPosition(startPosition + parcelableSize);
            return vr;
        }
        public OpenPgpMetadata[] newArray(final int size) {
            return new OpenPgpMetadata[size];
        }
    };
    @Override
    public String toString() {
        String out = "\nfilename: " + filename;
        out += "\nmimeType: " + mimeType;
        out += "\nmodificationTime: " + modificationTime;
        out += "\noriginalSize: " + originalSize;
        out += "\ncharset: " + charset;
        return out;
    }
}

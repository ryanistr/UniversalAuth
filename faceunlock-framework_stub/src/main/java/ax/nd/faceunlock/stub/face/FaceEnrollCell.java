package ax.nd.faceunlock.stub.face;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

/**
 * A cell is a rectangular region of the preview with the user's face centered.
 *
 * @hide
 */
public class FaceEnrollCell implements Parcelable {
    public static final Creator<FaceEnrollCell> CREATOR = new Creator<FaceEnrollCell>() {
        @Override
        public FaceEnrollCell createFromParcel(Parcel source) {
            return new FaceEnrollCell(source);
        }

        @Override
        public FaceEnrollCell[] newArray(int size) {
            return new FaceEnrollCell[size];
        }
    };
    private final int mX;
    private final int mY;
    private final int mZ;

    public FaceEnrollCell(int x, int y, int z) {
        mX = x;
        mY = y;
        mZ = z;
    }

    public int getX() {
        return mX;
    }

    public int getY() {
        return mY;
    }

    public int getZ() {
        return mZ;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mX);
        dest.writeInt(mY);
        dest.writeInt(mZ);
    }

    private FaceEnrollCell(@NonNull Parcel source) {
        mX = source.readInt();
        mY = source.readInt();
        mZ = source.readInt();
    }
}
// Manual Modifications Required: None.
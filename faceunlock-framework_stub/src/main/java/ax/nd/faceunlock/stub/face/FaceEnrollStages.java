package ax.nd.faceunlock.stub.face;

import androidx.annotation.IntDef;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A stage is a set of steps to be completed by the user.
 *
 * @hide
 */
public class FaceEnrollStages {
    public static final int UNKNOWN = 0;
    public static final int FIRST_FRAME_RECEIVED = 1;
    public static final int WAITING_FOR_CENTERING = 2;
    public static final int HOLD_STILL_IN_CENTER = 3;
    public static final int ENROLLING = 4;
    public static final int ENROLLMENT_FINISHED = 5;

    @IntDef({
            UNKNOWN,
            FIRST_FRAME_RECEIVED,
            WAITING_FOR_CENTERING,
            HOLD_STILL_IN_CENTER,
            ENROLLING,
            ENROLLMENT_FINISHED
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface FaceEnrollStage {}
}
// Manual Modifications Required: None.
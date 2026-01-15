package ax.nd.faceunlock.stub.biometrics;

import androidx.annotation.IntDef;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Interface containing all of the face specific constants.
 *
 * @hide
 */
public interface BiometricFaceConstants {
    //
    // Error messages from face authentication implementation
    //
    /**
     * The hardware is unavailable. Try again later.
     */
    public static final int FACE_ERROR_HW_UNAVAILABLE = 1;

    /**
     * The user was unable to process the sensor.
     */
    public static final int FACE_ERROR_UNABLE_TO_PROCESS = 2;

    /**
     * The current operation has been running too long and has timed out.
     * For example, this can happen when trying to authenticate a user.
     * The operation is disabled.
     */
    public static final int FACE_ERROR_TIMEOUT = 3;

    /**
     * The operation was canceled because the face sensor is unavailable. For example,
     * this may happen when the user is switched, the device is locked or another pending operation
     * prevents or disables it.
     */
    public static final int FACE_ERROR_NO_SPACE = 4;

    /**
     * The operation was canceled because the face sensor is busy. For example,
     * this may happen when the user is switched, the device is locked or another pending operation
     * prevents or disables it.
     */
    public static final int FACE_ERROR_CANCELED = 5;

    /**
     * The operation was unable to remove the template.
     */
    public static final int FACE_ERROR_UNABLE_TO_REMOVE = 6;

    /**
     * The operation was canceled because the API is locked out due to too many attempts.
     * This occurs after 5 failed attempts, and lasts for 30 seconds.
     */
    public static final int FACE_ERROR_LOCKOUT = 7;

    /**
     * Hardware vendors may extend this list if there are conditions that do not fall under one of
     * the above categories. Vendors are responsible for providing error strings for these errors.
     * These messages are typically reserved for internal operations such as enrollment, but may be
     * used to express vendor errors not otherwise covered. Applications are expected to show the
     * error message string if they happen, but are advised not to rely on the message id since they
     * will be device and vendor-specific
     */
    public static final int FACE_ERROR_VENDOR = 8;

    /**
     * The operation was canceled because FACE_ERROR_LOCKOUT occurred too many times.
     * Face authentication is disabled until the user unlocks with strong authentication
     * (PIN/Pattern/Password)
     */
    public static final int FACE_ERROR_LOCKOUT_PERMANENT = 9;

    /**
     * The user canceled the operation. Upon receiving this, applications should use alternate
     * authentication (e.g. a password). The application should also provide the means to return
     * to face authentication, such as a "use face" button.
     */
    public static final int FACE_ERROR_USER_CANCELED = 10;

    /**
     * The user does not have any face enrolled.
     */
    public static final int FACE_ERROR_NOT_ENROLLED = 11;

    /**
     * The device does not have a face sensor.
     */
    public static final int FACE_ERROR_HW_NOT_PRESENT = 12;

    /**
     * Unknown error.
     */
    public static final int FACE_ERROR_UNKNOWN = 17;

    /**
     * Vendor error base.
     * @hide
     */
    public static final int FACE_ERROR_VENDOR_BASE = 1000;

    /**
     * @hide
     */
    @IntDef({FACE_ERROR_HW_UNAVAILABLE,
            FACE_ERROR_UNABLE_TO_PROCESS,
            FACE_ERROR_TIMEOUT,
            FACE_ERROR_NO_SPACE,
            FACE_ERROR_CANCELED,
            FACE_ERROR_UNABLE_TO_REMOVE,
            FACE_ERROR_LOCKOUT,
            FACE_ERROR_VENDOR,
            FACE_ERROR_LOCKOUT_PERMANENT,
            FACE_ERROR_USER_CANCELED,
            FACE_ERROR_NOT_ENROLLED,
            FACE_ERROR_HW_NOT_PRESENT,
            FACE_ERROR_UNKNOWN})
    @Retention(RetentionPolicy.SOURCE)
    @interface FaceError {}

    //
    // Image acquisition messages.
    //
    // The message number should match the BiometricFaceConstants.h file
    //

    /**
     * The image acquired was good.
     */
    public static final int FACE_ACQUIRED_GOOD = 0;

    /**
     * The face image was not good enough to process due to a detected condition.
     * (e.g. too much light, too little light)
     */
    public static final int FACE_ACQUIRED_INSUFFICIENT = 1;

    /**
     * The face image was too bright.
     */
    public static final int FACE_ACQUIRED_TOO_BRIGHT = 2;

    /**
     * The face image was too dark.
     */
    public static final int FACE_ACQUIRED_TOO_DARK = 3;

    /**
     * The face was too close to the sensor.
     */
    public static final int FACE_ACQUIRED_TOO_CLOSE = 4;

    /**
     * The face was too far from the sensor.
     */
    public static final int FACE_ACQUIRED_TOO_FAR = 5;

    /**
     * The face was too high in the frame.
     */
    public static final int FACE_ACQUIRED_TOO_HIGH = 6;

    /**
     * The face was too low in the frame.
     */
    public static final int FACE_ACQUIRED_TOO_LOW = 7;

    /**
     * The face was too right in the frame.
     */
    public static final int FACE_ACQUIRED_TOO_RIGHT = 8;

    /**
     * The face was too left in the frame.
     */
    public static final int FACE_ACQUIRED_TOO_LEFT = 9;

    /**
     * The face was moving too much.
     */
    public static final int FACE_ACQUIRED_POOR_GAZE = 10;

    /**
     * The face was not facing the sensor.
     */
    public static final int FACE_ACQUIRED_NOT_DETECTED = 11;

    /**
     * The face was not detected.
     */
    public static final int FACE_ACQUIRED_TOO_MUCH_MOTION = 12;

    /**
     * The face was re-calibrated.
     */
    public static final int FACE_ACQUIRED_RECALIBRATE = 13;

    /**
     * The face was not facing the sensor.
     */
    public static final int FACE_ACQUIRED_TOO_DIFFERENT = 14;

    /**
     * The face was not facing the sensor.
     */
    public static final int FACE_ACQUIRED_TOO_SIMILAR = 15;

    /**
     * The face was not facing the sensor.
     */
    public static final int FACE_ACQUIRED_PAN_TOO_EXTREME = 16;

    /**
     * The face was not facing the sensor.
     */
    public static final int FACE_ACQUIRED_TILT_TOO_EXTREME = 17;

    /**
     * The face was not facing the sensor.
     */
    public static final int FACE_ACQUIRED_ROLL_TOO_EXTREME = 18;

    /**
     * The face was not facing the sensor.
     */
    public static final int FACE_ACQUIRED_FACE_OBSCURED = 19;

    /**
     * The face was not facing the sensor.
     */
    public static final int FACE_ACQUIRED_START = 20;

    /**
     * The face was not facing the sensor.
     */
    public static final int FACE_ACQUIRED_SENSOR_DIRTY = 21;

    /**
     * Hardware vendors may extend this list if there are conditions that do not fall under one of
     * the above categories. Vendors are responsible for providing error strings for these errors.
     * @hide
     */
    public static final int FACE_ACQUIRED_VENDOR = 22;

    /**
     * @hide
     */
    @IntDef({FACE_ACQUIRED_GOOD,
            FACE_ACQUIRED_INSUFFICIENT,
            FACE_ACQUIRED_TOO_BRIGHT,
            FACE_ACQUIRED_TOO_DARK,
            FACE_ACQUIRED_TOO_CLOSE,
            FACE_ACQUIRED_TOO_FAR,
            FACE_ACQUIRED_TOO_HIGH,
            FACE_ACQUIRED_TOO_LOW,
            FACE_ACQUIRED_TOO_RIGHT,
            FACE_ACQUIRED_TOO_LEFT,
            FACE_ACQUIRED_POOR_GAZE,
            FACE_ACQUIRED_NOT_DETECTED,
            FACE_ACQUIRED_TOO_MUCH_MOTION,
            FACE_ACQUIRED_RECALIBRATE,
            FACE_ACQUIRED_TOO_DIFFERENT,
            FACE_ACQUIRED_TOO_SIMILAR,
            FACE_ACQUIRED_PAN_TOO_EXTREME,
            FACE_ACQUIRED_TILT_TOO_EXTREME,
            FACE_ACQUIRED_ROLL_TOO_EXTREME,
            FACE_ACQUIRED_FACE_OBSCURED,
            FACE_ACQUIRED_START,
            FACE_ACQUIRED_SENSOR_DIRTY,
            FACE_ACQUIRED_VENDOR})
    @Retention(RetentionPolicy.SOURCE)
    @interface FaceAcquired {}
}
// Manual Modifications Required: None.
package ax.nd.faceunlock.stub.biometrics;

import androidx.annotation.IntDef;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Interface containing all of the biometric modality agnostic constants.
 *
 * @hide
 */
public interface BiometricConstants {
    //
    // Error messages from face authentication implementation
    //
    /**
     * The hardware is unavailable. Try again later.
     */
    public static final int BIOMETRIC_ERROR_HW_UNAVAILABLE = 1;

    /**
     * The user was unable to process the sensor.
     */
    public static final int BIOMETRIC_ERROR_UNABLE_TO_PROCESS = 2;

    /**
     * The current operation has been running too long and has timed out.
     * For example, this can happen when trying to authenticate a user.
     * The operation is disabled.
     */
    public static final int BIOMETRIC_ERROR_TIMEOUT = 3;

    /**
     * The operation was canceled because the biometric sensor is unavailable. For example,
     * this may happen when the user is switched, the device is locked or another pending operation
     * prevents or disables it.
     */
    public static final int BIOMETRIC_ERROR_NO_SPACE = 4;

    /**
     * The operation was canceled because the biometric sensor is busy. For example,
     * this may happen when the user is switched, the device is locked or another pending operation
     * prevents or disables it.
     */
    public static final int BIOMETRIC_ERROR_CANCELED = 5;

    /**
     * The operation was unable to remove the template.
     */
    public static final int BIOMETRIC_ERROR_UNABLE_TO_REMOVE = 6;

    /**
     * The operation was canceled because the API is locked out due to too many attempts.
     * This occurs after 5 failed attempts, and lasts for 30 seconds.
     */
    public static final int BIOMETRIC_ERROR_LOCKOUT = 7;

    /**
     * Hardware vendors may extend this list if there are conditions that do not fall under one of
     * the above categories. Vendors are responsible for providing error strings for these errors.
     * These messages are typically reserved for internal operations such as enrollment, but may be
     * used to express vendor errors not otherwise covered. Applications are expected to show the
     * error message string if they happen, but are advised not to rely on the message id since they
     * will be device and vendor-specific
     */
    public static final int BIOMETRIC_ERROR_VENDOR = 8;

    /**
     * The operation was canceled because BIOMETRIC_ERROR_LOCKOUT occurred too many times.
     * Biometric authentication is disabled until the user unlocks with strong authentication
     * (PIN/Pattern/Password)
     */
    public static final int BIOMETRIC_ERROR_LOCKOUT_PERMANENT = 9;

    /**
     * The user canceled the operation. Upon receiving this, applications should use alternate
     * authentication (e.g. a password). The application should also provide the means to return
     * to biometric authentication, such as a "use <biometric>" button.
     */
    public static final int BIOMETRIC_ERROR_USER_CANCELED = 10;

    /**
     * The user does not have any biometrics enrolled.
     */
    public static final int BIOMETRIC_ERROR_NO_BIOMETRICS = 11;

    /**
     * The device does not have a biometric sensor.
     */
    public static final int BIOMETRIC_ERROR_HW_NOT_PRESENT = 12;

    /**
     * The user pressed the negative button. This is a placeholder that is currently only used
     * by the support library.
     * @hide
     */
    public static final int BIOMETRIC_ERROR_NEGATIVE_BUTTON = 13;

    /**
     * The device does not have pin, pattern, or password set up.
     */
    public static final int BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL = 14;

    /**
     * A security vulnerability has been discovered with one or more hardware sensors.
     * The affected sensor(s) are unavailable until a security update has addressed the issue.
     */
    public static final int BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED = 15;

    /**
     * @hide
     */
    @IntDef({BIOMETRIC_SUCCESS,
            BIOMETRIC_ERROR_HW_UNAVAILABLE,
            BIOMETRIC_ERROR_UNABLE_TO_PROCESS,
            BIOMETRIC_ERROR_TIMEOUT,
            BIOMETRIC_ERROR_NO_SPACE,
            BIOMETRIC_ERROR_CANCELED,
            BIOMETRIC_ERROR_UNABLE_TO_REMOVE,
            BIOMETRIC_ERROR_LOCKOUT,
            BIOMETRIC_ERROR_VENDOR,
            BIOMETRIC_ERROR_LOCKOUT_PERMANENT,
            BIOMETRIC_ERROR_USER_CANCELED,
            BIOMETRIC_ERROR_NO_BIOMETRICS,
            BIOMETRIC_ERROR_HW_NOT_PRESENT,
            BIOMETRIC_ERROR_NEGATIVE_BUTTON,
            BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL,
            BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED})
    @Retention(RetentionPolicy.SOURCE)
    @interface BiometricError {}

    //
    // Image acquisition messages.
    //
    // The message number should match the BiometricConstants.h file
    //

    /**
     * The image acquired was good.
     */
    public static final int BIOMETRIC_ACQUIRED_GOOD = 0;

    /**
     * Only a partial biometric image was detected. During enrollment, the user should be informed
     * on what needs to happen to resolve this problem, e.g. "press firmly on sensor."
     */
    public static final int BIOMETRIC_ACQUIRED_PARTIAL = 1;

    /**
     * The biometric image was too noisy to process due to a detected condition or a possibly dirty
     * sensor (e.g. dry skin, oils, scuffs).
     */
    public static final int BIOMETRIC_ACQUIRED_INSUFFICIENT = 2;

    /**
     * The biometric image was too noisy due to suspected sensor dirt or dust.
     */
    public static final int BIOMETRIC_ACQUIRED_IMAGER_DIRTY = 3;

    /**
     * The biometric image was unreadable due to lack of motion.
     */
    public static final int BIOMETRIC_ACQUIRED_TOO_SLOW = 4;

    /**
     * The biometric image was incomplete due to quick motion.
     */
    public static final int BIOMETRIC_ACQUIRED_TOO_FAST = 5;

    /**
     * Hardware vendors may extend this list if there are conditions that do not fall under one of
     * the above categories. Vendors are responsible for providing error strings for these errors.
     * @hide
     */
    public static final int BIOMETRIC_ACQUIRED_VENDOR = 6;

    /**
     * @hide
     */
    @IntDef({BIOMETRIC_ACQUIRED_GOOD,
            BIOMETRIC_ACQUIRED_PARTIAL,
            BIOMETRIC_ACQUIRED_INSUFFICIENT,
            BIOMETRIC_ACQUIRED_IMAGER_DIRTY,
            BIOMETRIC_ACQUIRED_TOO_SLOW,
            BIOMETRIC_ACQUIRED_TOO_FAST,
            BIOMETRIC_ACQUIRED_VENDOR})
    @Retention(RetentionPolicy.SOURCE)
    @interface BiometricAcquired {}

    /** @hide */
    public static final int BIOMETRIC_SUCCESS = 0;
}
// Manual Modifications Required: None.
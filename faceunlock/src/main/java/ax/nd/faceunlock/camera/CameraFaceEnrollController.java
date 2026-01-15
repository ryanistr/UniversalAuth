package ax.nd.faceunlock.camera;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowManager;

import ax.nd.faceunlock.FaceApplication;
import ax.nd.faceunlock.camera.listeners.ByteBufferCallbackListener;
import ax.nd.faceunlock.camera.listeners.CameraListener;
import ax.nd.faceunlock.camera.listeners.ErrorCallbackListener;
import ax.nd.faceunlock.camera.listeners.ReadParametersListener;
import ax.nd.faceunlock.util.Util;

import java.nio.ByteBuffer;
import java.util.ArrayList;

@SuppressWarnings("SynchronizeOnNonFinalField")
public class CameraFaceEnrollController {
    private static final int CAM_MSG_ERROR = 101;
    private static final int CAM_MSG_STATE_UPDATE = 102;
    private static final int CAM_MSG_SURFACE_CREATED = 103;
    private static final int MSG_FACE_HANDLE_DATA = 1003;
    private static final int MSG_FACE_UNLOCK_DETECT_AREA = 1004;
    private static final String TAG = "CameraFaceEnrollCtl";
    private static HandlerThread mFaceUnlockThread;
    @SuppressLint("StaticFieldLeak")
    private static CameraFaceEnrollController sInstance;
    private final ArrayList<CameraCallback> mCameraCallbacks = new ArrayList<>();
    private final Context mContext;
    private final Handler mHandler = new Handler(Looper.getMainLooper(), new CameraFaceEnrollController.HandlerCallback());
    
    // Session management
    private int mSessionId = 0;
    private CameraListener mCurrentCameraListener;

    private final int mCamID;
    
    protected ErrorCallbackListener mErrorCallbackListener = (i, unused) -> {
        Log.e(TAG, "ErrorCallbackListener triggered with code: " + i);
        mHandler.sendEmptyMessage(CAM_MSG_ERROR);
    };

    @SuppressWarnings("deprecation")
    private Camera.Parameters mCameraParam;
    private final ReadParametersListener mReadParamListener = new ReadParametersListener() {
        @SuppressWarnings("deprecation")
        @Override
        public void onEventCallback(int i, Object parameters) {
            mCameraParam = (Camera.Parameters) parameters;
        }
    };
    private CameraState mCameraState = CameraState.CAMERA_IDLE;
    private Handler mFaceUnlockHandler;
    private ByteBuffer mFrame;
    private boolean mHandling = false;
    private final ByteBufferCallbackListener mByteBufferListener = (i, byteBuffer) -> {
        if (!this.mHandling) {
            this.mHandling = true;
            Message obtain = Message.obtain(this.mFaceUnlockHandler, MSG_FACE_HANDLE_DATA);
            obtain.obj = byteBuffer;
            this.mFaceUnlockHandler.sendMessage(obtain);
        }
    };
    private SurfaceHolder mHolder;
    @SuppressWarnings("deprecation")
    private Camera.Size mPreviewSize;
    private boolean mPreviewStarted = false;
    private boolean mStop = false;
    private boolean mSurfaceCreated = false;

    private CameraFaceEnrollController(Context context) {
        mContext = context;
        mCamID = CameraUtil.getFrontFacingCameraId(context);
        Log.d(TAG, "Constructor called, CamID: " + mCamID);
        initWorkHandler();
    }

    public static CameraFaceEnrollController getInstance() {
        if (sInstance == null) {
            sInstance = new CameraFaceEnrollController(FaceApplication.getApp());
        }
        return sInstance;
    }

    public void setSurfaceHolder(SurfaceHolder surfaceHolder) {
        Log.d(TAG, "setSurfaceHolder: " + (surfaceHolder != null ? "VALID" : "NULL"));
        
        if (surfaceHolder == null) {
            synchronized (mCameraState) {
                // BUG FIX: Do NOT close camera here. If Service is still using it, we just stop preview.
                // Case 3 (STATE_UPDATE) will automatically restart preview on Dummy Surface if needed.
                if (mPreviewStarted) {
                    Log.d(TAG, "setSurfaceHolder(null): Stopping preview only (Camera remains OPEN)");
                    CameraService.stopPreview(null);
                    mPreviewStarted = false;
                }
                mSurfaceCreated = false;
                
                // Trigger an update to potentially switch to dummy texture if still running
                mHandler.sendEmptyMessage(CAM_MSG_STATE_UPDATE);
            }
        }
        
        mHolder = surfaceHolder;
        if (surfaceHolder != null) {
            surfaceHolder.setKeepScreenOn(true);
            mHolder.addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i2, int i3) {
                }

                @Override
                public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
                    Log.d(TAG, "Internal surfaceDestroyed callback");
                    mSurfaceCreated = false;
                }

                @Override
                public void surfaceCreated(SurfaceHolder surfaceHolder) {
                    Log.d(TAG, "Internal surfaceCreated callback");
                    mSurfaceCreated = true;
                    mHandler.sendEmptyMessage(CAM_MSG_SURFACE_CREATED);
                }
            });
            if (mHolder.getSurface() != null) {
                Log.d(TAG, "Surface already valid");
                mSurfaceCreated = true;
            }
        }
    }

    public void start(final CameraCallback cameraCallback, int i) {
        Log.i(TAG, "start() called. Callback: " + cameraCallback);
        
        // Start a new session
        mSessionId++;
        Log.d(TAG, "NEW SESSION ID: " + mSessionId);
        mCurrentCameraListener = new SessionCameraListener(mSessionId);

        synchronized (mCameraCallbacks) {
            if (mCameraCallbacks.contains(cameraCallback)) {
                Log.w(TAG, "Callback already registered");
                return;
            }
            mCameraCallbacks.add(cameraCallback);
        }
        synchronized (mCameraState) {
            Log.d(TAG, "start: Current CameraState = " + mCameraState);
            // Always trigger update. If IDLE -> Opens. If STARTED -> Restarts preview on new surface.
            mHandler.removeMessages(CAM_MSG_STATE_UPDATE);
            Message msg = mHandler.obtainMessage(CAM_MSG_STATE_UPDATE);
            msg.arg1 = mSessionId;
            mHandler.sendMessage(msg);
            mHandling = false;
        }
        if (i > 0) {
            mHandler.postDelayed(() -> {
                synchronized (mCameraState) {
                    if (mCameraCallbacks.contains(cameraCallback)) {
                        Log.d(TAG, "Timeout triggered");
                        cameraCallback.onTimeout();
                    }
                }
            }, i);
        }
        mStop = false;
    }

    public void stop(CameraCallback cameraCallback) {
        Log.i(TAG, "stop() called");
        
        // Invalidate current session
        mSessionId++; 
        Log.d(TAG, "stop: Session invalidated (Next ID: " + mSessionId + ")");
        
        synchronized (mCameraCallbacks) {
            if (!mCameraCallbacks.contains(cameraCallback)) {
                Log.e(TAG, "stop: callback has been released!");
                return;
            }
            mCameraCallbacks.remove(cameraCallback);
            if (mCameraCallbacks.size() > 0) {
                Log.d(TAG, "stop: other callbacks still active, not stopping camera fully");
                return;
            }
        }
        mStop = true;
        
        Log.d(TAG, "stop: clearing handlers");
        mHandler.removeCallbacksAndMessages(null);

        Handler handler = mFaceUnlockHandler;
        if (handler != null) {
            handler.removeMessages(MSG_FACE_HANDLE_DATA);
            mFaceUnlockHandler.removeMessages(MSG_FACE_UNLOCK_DETECT_AREA);
        }
        synchronized (mCameraState) {
            Log.d(TAG, "stop: Closing camera (Current State: " + mCameraState + ")");
            CameraService.clearQueue();
            if (mCameraState == CameraState.CAMERA_PREVIEW_STARTED) {
                mCameraState = CameraState.CAMERA_PREVIEW_STOPPING;
                CameraService.setFaceDetectionCallback(null, null);
                CameraService.stopPreview(null);
                CameraService.setPreviewCallback(null, false, null);
                CameraService.closeCamera(null);
            } else if (mCameraState != CameraState.CAMERA_IDLE) {
                CameraService.setPreviewCallback(null, false, null);
                CameraService.closeCamera(null);
            }
            // Ensure state is reset even if closeCamera is async
            mCameraState = CameraState.CAMERA_IDLE; 
        }
        mHolder = null;
        mCameraParam = null;
        mPreviewSize = null;
        mHandling = false;
        mPreviewStarted = false;
        mSurfaceCreated = false;
        Log.d(TAG, "stop: Reset complete");
    }

    @SuppressWarnings("deprecation")
    private void handleCameraStateUpdate() {
        if (!mStop) {
            synchronized (mCameraState) {
                int ordinal = mCameraState.ordinal();
                int stateAction = CameraStateOrdinal.STATE[ordinal];
                Log.d(TAG, "handleCameraStateUpdate: State=" + mCameraState + " Ordinal=" + ordinal + " Action=" + stateAction);

                switch (stateAction) {
                    case 0: // CAMERA_IDLE -> Open Camera
                        if (mCamID != -1) {
                            Log.d(TAG, "Opening Camera ID: " + mCamID);
                            CameraService.openCamera(mCamID, mErrorCallbackListener, mCurrentCameraListener);
                            mCameraState = CameraState.CAMERA_OPENED;
                            break;
                        } else {
                            Log.e(TAG, "No front camera found");
                            mHandler.sendEmptyMessage(CAM_MSG_ERROR);
                            return;
                        }
                    case 1: // CAMERA_OPENED -> Read Params
                        Log.d(TAG, "Reading parameters");
                        mCameraState = CameraState.CAMERA_PARAM_READ;
                        CameraService.readParameters(mReadParamListener, mCurrentCameraListener);
                        break;
                    case 2: // CAMERA_PARAM_READ -> Set Params
                        Log.d(TAG, "Setting parameters");
                        mCameraState = CameraState.CAMERA_PARAM_SET;
                        mPreviewSize = CameraUtil.calBestPreviewSize(mCameraParam, 480, 640);
                        int width = mPreviewSize.width;
                        int height = mPreviewSize.height;
                        mCameraParam.setPreviewSize(width, height);
                        mCameraParam.setPreviewFormat(ImageFormat.NV21);
                        mFrame = ByteBuffer.allocateDirect(getPreviewBufferSize(width, height));
                        CameraService.writeParameters(mCurrentCameraListener);
                        Log.d(TAG, "preview size " + mPreviewSize.height + " " + mPreviewSize.width);
                        break;
                    case 3: // CAMERA_PARAM_SET or STARTED -> Start/Restart Preview
                        Log.d(TAG, "Starting preview logic");
                        mCameraState = CameraState.CAMERA_PREVIEW_STARTED;
                        CameraService.addCallbackBuffer(mFrame.array(), null);
                        CameraService.setDisplayOrientationCallback(getCameraAngle(), null);
                        CameraService.setPreviewCallback(mByteBufferListener, true, null);
                        
                        // Smart Preview Switch logic
                        if (mHolder != null) {
                            if (mSurfaceCreated) {
                                Log.d(TAG, "Starting preview (Surface Ready)");
                                CameraService.startPreview(mHolder, mCurrentCameraListener);
                                mPreviewStarted = true;
                            } else {
                                Log.d(TAG, "Wait for surface...");
                            }
                        } else {
                            Log.d(TAG, "Starting preview with Dummy SurfaceTexture (Background Mode)");
                            SurfaceTexture surfaceTexture = new SurfaceTexture(10);
                            CameraService.startPreview(surfaceTexture, mCurrentCameraListener);
                            mPreviewStarted = true;
                        }
                        break;
                    case 4: // CAMERA_PREVIEW_STARTED -> Face Detection
                        Log.d(TAG, "Setting face detection callback");
                        CameraService.setFaceDetectionCallback((faceArr, camera) -> {
                            if (faceArr.length > 0) {
                                for (CameraCallback mCameraCallback : mCameraCallbacks) {
                                    mCameraCallback.onFaceDetected();
                                }
                            }
                        }, null);
                        break;
                    case 5: // CAMERA_PREVIEW_STOPPING -> Close Camera
                        Log.d(TAG, "Closing camera (Case 5 - Recovery)");
                        CameraService.setPreviewCallback(null, false, null);
                        CameraService.closeCamera(null);
                        
                        // Critical fix: Reset to IDLE so we can restart!
                        mCameraState = CameraState.CAMERA_IDLE;
                        
                        // If we are not stopped, it means we were trying to start but hit a stale stopping state.
                        // Retry immediately.
                        if (!mStop) {
                            Log.d(TAG, "State reset to IDLE, retrying start flow...");
                            mHandler.sendEmptyMessage(CAM_MSG_STATE_UPDATE);
                        }
                        break;
                }
            }
        } else {
            Log.d(TAG, "handleCameraStateUpdate: Skipped because mStop is true");
        }
    }

    private void initWorkHandler() {
        if (mFaceUnlockThread == null) {
            mFaceUnlockThread = new HandlerThread("Camera Face unlock");
            mFaceUnlockThread.setPriority(Process.THREAD_PRIORITY_BACKGROUND);
            mFaceUnlockThread.start();
        }
        mFaceUnlockHandler = new FaceHandler(mFaceUnlockThread.getLooper());
    }

    private int getPreviewBufferSize(int i, int i2) {
        return (((i2 * i) * ImageFormat.getBitsPerPixel(ImageFormat.NV21)) / 8) + 32;
    }

    @SuppressWarnings("deprecation")
    private int getCameraAngle() {
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        Camera.getCameraInfo(mCamID, cameraInfo);
        int rotation = mContext.getSystemService(WindowManager.class).getDefaultDisplay().getRotation();
        int i = 0;
        if (rotation != 0) {
            if (rotation == 1) {
                i = 90;
            } else if (rotation == 2) {
                i = 180;
            } else if (rotation == 3) {
                i = 270;
            }
        }
        if (cameraInfo.facing == 1) {
            return (360 - ((cameraInfo.orientation + i) % 360)) % 360;
        }
        return ((cameraInfo.orientation - i) + 360) % 360;
    }

    private enum CameraState {
        CAMERA_IDLE,
        CAMERA_OPENED,
        CAMERA_PARAM_READ,
        CAMERA_PARAM_SET,
        CAMERA_PREVIEW_STARTED,
        CAMERA_PREVIEW_STOPPING
    }

    public interface CameraCallback {
        int handleSaveFeature(byte[] bArr, int i, int i2, int i3);

        void handleSaveFeatureResult(int i);

        void onCameraError();

        void onFaceDetected();

        void onTimeout();

        @SuppressWarnings("deprecation")
        void setDetectArea(Camera.Size size);
    }

    // New Listener that ignores events from old sessions
    private class SessionCameraListener implements CameraListener {
        private final int mTargetSessionId;

        SessionCameraListener(int sessionId) {
            mTargetSessionId = sessionId;
        }

        @Override
        public void onComplete(Object unused) {
            if (mTargetSessionId != mSessionId) {
                Log.w(TAG, "Listener.onComplete: Ignoring stale session " + mTargetSessionId + " (Current: " + mSessionId + ")");
                return;
            }
            Log.d(TAG, "Listener.onComplete: Session matched (" + mTargetSessionId + "). Requesting update.");
            Message msg = mHandler.obtainMessage(CAM_MSG_STATE_UPDATE);
            msg.arg1 = mTargetSessionId;
            mHandler.sendMessage(msg);
        }

        @Override
        public void onError(Exception exc) {
            if (mTargetSessionId == mSessionId) {
                Log.e(TAG, "Listener.onError", exc);
                mHandler.sendEmptyMessage(CAM_MSG_ERROR);
            } else {
                Log.w(TAG, "Listener.onError: Ignoring stale session error");
            }
        }
    }

    static class CameraStateOrdinal {
        static final int[] STATE = new int[CameraState.values().length];

        static {
            // Explicitly map States to Switch Case integers
            STATE[CameraState.CAMERA_IDLE.ordinal()] = 0;
            STATE[CameraState.CAMERA_OPENED.ordinal()] = 1;
            STATE[CameraState.CAMERA_PARAM_READ.ordinal()] = 2;
            STATE[CameraState.CAMERA_PARAM_SET.ordinal()] = 3;
            STATE[CameraState.CAMERA_PREVIEW_STARTED.ordinal()] = 4; // Map STARTED to Case 4 (Face Detect)
            STATE[CameraState.CAMERA_PREVIEW_STOPPING.ordinal()] = 5; // Map STOPPING to Case 5 (Close)
        }
    }

    class HandlerCallback implements Handler.Callback {
        @Override
        public boolean handleMessage(Message message) {
            switch (message.what) {
                case CAM_MSG_ERROR:
                    Log.e(TAG, "Handler: CAM_MSG_ERROR");
                    for (CameraCallback mCameraCallback : mCameraCallbacks) {
                        mCameraCallback.onCameraError();
                    }
                    break;
                case CAM_MSG_STATE_UPDATE:
                    // Only process state updates for the CURRENT session
                    if (message.arg1 == mSessionId) {
                        handleCameraStateUpdate();
                    } else {
                        Log.w(TAG, "Handler: Ignoring stale CAM_MSG_STATE_UPDATE from session " + message.arg1 + " (current: " + mSessionId + ")");
                    }
                    break;
                case CAM_MSG_SURFACE_CREATED:
                    Log.d(TAG, "Handler: CAM_MSG_SURFACE_CREATED");
                    // Trigger state update to handle restart if needed
                    if (CameraState.CAMERA_PREVIEW_STARTED == mCameraState && !mPreviewStarted) {
                         // Send update instead of calling service directly to ensure single path
                         Message msg = mHandler.obtainMessage(CAM_MSG_STATE_UPDATE);
                         msg.arg1 = mSessionId;
                         mHandler.sendMessage(msg);
                    }
                    break;
            }
            return true;
        }
    }

    private class FaceHandler extends Handler {
        public FaceHandler(Looper looper) {
            super(looper);
        }

        @SuppressWarnings("deprecation")
        @Override
        public void handleMessage(Message message) {
            if (!mStop) {
                int i = message.what;
                if (i == MSG_FACE_HANDLE_DATA) {
                    synchronized (mCameraCallbacks) {
                        ByteBuffer byteBuffer = (ByteBuffer) message.obj;
                        int i2 = -1;
                        for (CameraCallback mCameraCallback : mCameraCallbacks) {
                            int handleSaveFeature = mCameraCallback.handleSaveFeature(byteBuffer.array(), mPreviewSize.width, mPreviewSize.height, 0);
                            if (handleSaveFeature != -1) {
                                i2 = handleSaveFeature;
                            }
                        }
                        for (CameraCallback mCameraCallback : mCameraCallbacks) {
                            mCameraCallback.handleSaveFeatureResult(i2);
                        }
                        if (mFrame != null) {
                            CameraService.addCallbackBuffer(mFrame.array(), null);
                            mHandling = false;
                        }
                    }
                } else if (i == MSG_FACE_UNLOCK_DETECT_AREA) {
                    for (CameraCallback mCameraCallback : mCameraCallbacks) {
                        mCameraCallback.setDetectArea(mPreviewSize);
                    }
                }
            }
        }
    }
}
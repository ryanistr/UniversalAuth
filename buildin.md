1. On this class
com.android.keyguard.KeyguardUpdateMonitor

find
isUnlockingWithBiometricAllowed
method

replace the whole method with
```smali
.method public final isUnlockingWithBiometricAllowed()Z
    .registers 1

    const/4 p0, 0x1

    return p0
.end method
```

2. On this class com.android.systemui.statusbar.phone.CentralSurfacesImpl

find the method
.method public registerCallbacks()V

replace the whole method to
```
.method public registerCallbacks()V
    .registers 12
    .annotation build Lcom/android/internal/annotations/VisibleForTesting;
    .end annotation

    move-object v10, p0

    iget-object v0, v10, Lcom/android/systemui/statusbar/phone/CentralSurfacesImpl;->mDeviceStateManager:Landroid/hardware/devicestate/DeviceStateManager;

    new-instance v1, Lcom/android/systemui/statusbar/phone/FoldStateListener;

    iget-object v2, v10, Lcom/android/systemui/statusbar/phone/CentralSurfacesImpl;->mContext:Landroid/content/Context;

    new-instance v3, Lcom/android/systemui/statusbar/phone/CentralSurfacesImpl$$ExternalSyntheticLambda0;

    invoke-direct {v3, v10}, Lcom/android/systemui/statusbar/phone/CentralSurfacesImpl$$ExternalSyntheticLambda0;-><init>(Ljava/lang/Object;)V

    invoke-direct {v1, v2, v3}, Lcom/android/systemui/statusbar/phone/FoldStateListener;-><init>(Landroid/content/Context;Lcom/android/systemui/statusbar/phone/FoldStateListener$OnFoldStateChangeListener;)V

    iget-object v2, v10, Lcom/android/systemui/statusbar/phone/CentralSurfacesImpl;->mMainExecutor:Lcom/android/systemui/util/concurrency/DelayableExecutor;

    invoke-virtual {v0, v2, v1}, Landroid/hardware/devicestate/DeviceStateManager;->registerCallback(Ljava/util/concurrent/Executor;Landroid/hardware/devicestate/DeviceStateManager$DeviceStateCallback;)V

    iget-object v0, v10, Lcom/android/systemui/statusbar/phone/CentralSurfacesImpl;->mCommunalInteractor:Lcom/android/systemui/communal/domain/interactor/CommunalInteractor;

    invoke-virtual {v0}, Lcom/android/systemui/communal/domain/interactor/CommunalInteractor;->isIdleOnCommunal()Lkotlinx/coroutines/flow/StateFlow;

    move-result-object v0

    iget-object v1, v10, Lcom/android/systemui/statusbar/phone/CentralSurfacesImpl;->mIdleOnCommunalConsumer:Lcom/android/systemui/statusbar/phone/CentralSurfacesImpl$$ExternalSyntheticLambda1;

    iget-object v2, v10, Lcom/android/systemui/statusbar/phone/CentralSurfacesImpl;->mJavaAdapter:Lcom/android/systemui/util/kotlin/JavaAdapter;

    invoke-virtual {v2, v0, v1}, Lcom/android/systemui/util/kotlin/JavaAdapter;->alwaysCollectFlow(Lkotlinx/coroutines/flow/Flow;Ljava/util/function/Consumer;)Lkotlinx/coroutines/Job;

    sget-object v0, Lcom/oplusos/systemui/common/util/AppSwitchManager;->INSTANCE:Lcom/oplusos/systemui/common/util/AppSwitchManager;

    iget-object v1, v10, Lcom/android/systemui/statusbar/phone/CentralSurfacesImpl;->mContext:Landroid/content/Context;

    invoke-virtual {v0, v1}, Lcom/oplusos/systemui/common/util/AppSwitchManager;->registerLauncherSwitchObserver(Landroid/content/Context;)V

    sget-object v0, Lcom/android/systemui/DependencyEx;->sDependency:Lcom/android/systemui/DependencyEx;

    const-class v1, Lcom/android/keyguard/OplusKeyguardDependencyEx;

    invoke-virtual {v0, v1}, Lcom/android/systemui/DependencyEx;->getDependency(Ljava/lang/Class;)Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Lcom/android/keyguard/OplusKeyguardDependencyEx;

    invoke-virtual {v0}, Lcom/android/keyguard/OplusKeyguardDependencyEx;->getOplusKeyguardBottomAreaController()Lcom/android/systemui/keyguard/OplusKeyguardBottomAreaControllerEx;

    move-result-object v0

    invoke-virtual {v0}, Lcom/android/systemui/keyguard/OplusKeyguardBottomAreaControllerEx;->initOplusAffordanceStatusSync()V

    iget-object v0, v10, Lcom/android/systemui/statusbar/phone/CentralSurfacesImpl;->mContext:Landroid/content/Context;

    new-instance v1, Lcom/android/systemui/FaceUnlockReceiver;

    invoke-direct {v1, v10}, Lcom/android/systemui/FaceUnlockReceiver;-><init>(Lcom/android/systemui/statusbar/phone/CentralSurfacesImpl;)V

    new-instance v2, Landroid/content/IntentFilter;

    const-string v3, "ax.nd.universalauth.unlock-device"

    invoke-direct {v2, v3}, Landroid/content/IntentFilter;-><init>(Ljava/lang/String;)V

    const/4 v3, 0x0

    const/4 v4, 0x0

    const/4 v5, 0x2

    invoke-virtual/range {v0 .. v5}, Landroid/content/Context;->registerReceiver(Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;Ljava/lang/String;Landroid/os/Handler;I)Landroid/content/Intent;

    return-void
.end method
```

3. Make a new class com.android.systemui.FaceUnlockReceiver
```Contents
.class public Lcom/android/systemui/FaceUnlockReceiver;
.super Landroid/content/BroadcastReceiver;
.source "FaceUnlockReceiver.java"

# Instance field to hold reference to CentralSurfaces (SystemUI)
.field private final mCentralSurfaces:Lcom/android/systemui/statusbar/phone/CentralSurfacesImpl;

# Constructor
.method public constructor <init>(Lcom/android/systemui/statusbar/phone/CentralSurfacesImpl;)V
    .registers 2
    .param p1, "centralSurfaces"    # Lcom/android/systemui/statusbar/phone/CentralSurfacesImpl;

    invoke-direct {p0}, Landroid/content/BroadcastReceiver;-><init>()V
    iput-object p1, p0, Lcom/android/systemui/FaceUnlockReceiver;->mCentralSurfaces:Lcom/android/systemui/statusbar/phone/CentralSurfacesImpl;
    return-void
.end method

# onReceive Method
.method public onReceive(Landroid/content/Context;Landroid/content/Intent;)V
    .registers 14
    .param p1, "context"    # Landroid/content/Context;
    .param p2, "intent"     # Landroid/content/Intent;

    const-string v0, "FaceUnlockDebug"

    :try_start_2
    const-string v1, "onReceive called. Analyzing intent..."
    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    # 1. Check Action
    invoke-virtual {p2}, Landroid/content/Intent;->getAction()Ljava/lang/String;
    move-result-object v1

    # Log the action
    new-instance v2, Ljava/lang/StringBuilder;
    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V
    const-string v3, "Received Action: "
    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    invoke-virtual {v2, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
    move-result-object v2
    invoke-static {v0, v2}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    const-string v2, "ax.nd.universalauth.unlock-device"
    invoke-virtual {v1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v1

    if-nez v1, :cond_30

    const-string v1, "Action mismatch! Ignoring."
    invoke-static {v0, v1}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;)I
    return-void

    :cond_30
    const-string v1, "Action matched. Checking bypass extra..."
    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    # 2. Check bypass keyguard
    const-string v1, "ax.nd.universalauth.unlock-device.bypass-keyguard"
    const/4 v2, 0x1
    invoke-virtual {p2, v1, v2}, Landroid/content/Intent;->getBooleanExtra(Ljava/lang/String;Z)Z
    move-result v1

    # Log bypass value
    new-instance v3, Ljava/lang/StringBuilder;
    invoke-direct {v3}, Ljava/lang/StringBuilder;-><init>()V
    const-string v4, "Bypass Keyguard Value: "
    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    invoke-virtual {v3, v1}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;
    invoke-virtual {v3}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
    move-result-object v3
    invoke-static {v0, v3}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    if-eqz v1, :cond_no_bypass

    # --- BYPASS PATH ---
    const-string v1, "Entering BYPASS execution path."
    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    # Get Unlock Mode
    const-string v1, "ax.nd.universalauth.unlock-device.unlock-mode"
    const/4 v3, 0x7
    invoke-virtual {p2, v1, v3}, Landroid/content/Intent;->getIntExtra(Ljava/lang/String;I)I
    move-result v1

    # Log Unlock Mode
    new-instance v3, Ljava/lang/StringBuilder;
    invoke-direct {v3}, Ljava/lang/StringBuilder;-><init>()V
    const-string v4, "Unlock Mode requested: "
    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    invoke-virtual {v3, v1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;
    invoke-virtual {v3}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
    move-result-object v3
    invoke-static {v0, v3}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    # Check CentralSurfaces instance
    iget-object v3, p0, Lcom/android/systemui/FaceUnlockReceiver;->mCentralSurfaces:Lcom/android/systemui/statusbar/phone/CentralSurfacesImpl;
    if-nez v3, :cond_7b
    const-string v1, "FATAL: mCentralSurfaces is null!"
    invoke-static {v0, v1}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I
    return-void

    :cond_7b
    # Retrieve BiometricUnlockController
    iget-object v3, v3, Lcom/android/systemui/statusbar/phone/CentralSurfacesImpl;->mBiometricUnlockController:Lcom/android/systemui/statusbar/phone/BiometricUnlockController;

    if-nez v3, :cond_85
    const-string v1, "FATAL: mBiometricUnlockController is null!"
    invoke-static {v0, v1}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I
    return-void

    :cond_85
    const-string v4, "BiometricUnlockController found. Attempting startWakeAndUnlock..."
    invoke-static {v0, v4}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    # Call startWakeAndUnlock(int, BiometricUnlockSource)
    # Passing null (0x0) for source. If this crashes, logs will show exception.
    const/4 v4, 0x0
    invoke-virtual {v3, v1, v4}, Lcom/android/systemui/statusbar/phone/BiometricUnlockController;->startWakeAndUnlock(ILcom/android/systemui/keyguard/shared/model/BiometricUnlockSource;)V

    const-string v1, "startWakeAndUnlock executed successfully (no crash)."
    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    goto :end

    :cond_no_bypass
    # --- TRUST AGENT PATH ---
    const-string v1, "Entering TRUST AGENT execution path (No Bypass)."
    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    iget-object v1, p0, Lcom/android/systemui/FaceUnlockReceiver;->mCentralSurfaces:Lcom/android/systemui/statusbar/phone/CentralSurfacesImpl;
    if-nez v1, :cond_9f
    const-string v1, "FATAL: mCentralSurfaces is null!"
    invoke-static {v0, v1}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I
    return-void

    :cond_9f
    iget-object v1, v1, Lcom/android/systemui/statusbar/phone/CentralSurfacesImpl;->mKeyguardUpdateMonitor:Lcom/android/keyguard/KeyguardUpdateMonitor;

    if-nez v1, :cond_a9
    const-string v1, "FATAL: mKeyguardUpdateMonitor is null!"
    invoke-static {v0, v1}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I
    return-void

    :cond_a9
    const-string v3, "KeyguardUpdateMonitor found. Calling onFaceAuthenticated..."
    invoke-static {v0, v3}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    invoke-static {}, Landroid/app/ActivityManager;->getCurrentUser()I
    move-result v3
    
    # Log user ID
    new-instance v4, Ljava/lang/StringBuilder;
    invoke-direct {v4}, Ljava/lang/StringBuilder;-><init>()V
    const-string v5, "Current User ID: "
    invoke-virtual {v4, v5}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    invoke-virtual {v4, v3}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;
    invoke-virtual {v4}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
    move-result-object v4
    invoke-static {v0, v4}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    invoke-virtual {v1, v3, v2}, Lcom/android/keyguard/KeyguardUpdateMonitor;->onFaceAuthenticated(IZ)V

    const-string v1, "onFaceAuthenticated executed."
    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
    :try_end_ce
    .catch Ljava/lang/Exception; {:try_start_2 .. :try_end_ce} :catch_cf

    goto :end

    :catch_cf
    move-exception v1
    const-string v2, "CRITICAL ERROR in FaceUnlockReceiver!"
    invoke-static {v0, v2, v1}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    :end
    return-void
.end method
```

Then add the app to priv-app with the permission xml and open
Accessibility > Downloaded apps > Face Unlock > Click the gear icon and register face > done.
To replicate the functionality of the `UniversalAuth` Xposed module directly in `SystemUI` via Smali editing, you need to implement two main logic loops:

1. **Broadcasting State (Early Unlock):** `KeyguardUpdateMonitor` must tell the Face Unlock app when the device is interactive and the keyguard is showing.
2. **Receiving Commands (Unlock):** `SystemUI` must listen for the unlock command from the Face Unlock app and trigger the biometric unlock controller.

Here is the technical implementation plan. You will create one new Smali class and modify two existing ones.

### **Manual Modifications Required**

1. **Decompile** `SystemUI.apk`.
2. **Verify Field Names:** The Smali code below uses standard AOSP field names (e.g., `mKeyguardIsVisible`, `mDeviceInteractive`). In your specific ROM, these might be obfuscated or slightly different. **Check your decompiled `KeyguardUpdateMonitor.smali` fields** before pasting.
3. **Verify Method Signatures:** `startWakeAndUnlock` in `BiometricUnlockController` changes between Android 12, 13, and 14. Adjust the signature in the receiver code if necessary.

---

### **1. Create New File: `UniversalAuthReceiver.smali**`

Create this file at `smali/com/android/systemui/UniversalAuthReceiver.smali`. This receiver replaces the Xposed hooks that listen for the unlock command.

```smali
.class public Lcom/android/systemui/UniversalAuthReceiver;
.super Landroid/content/BroadcastReceiver;
.source "UniversalAuthReceiver.smali"

# Instance field to hold reference to CentralSurfaces (SystemUI)
.field private final mCentralSurfaces:Lcom/android/systemui/statusbar/phone/CentralSurfacesImpl;

# Constructor
.method public constructor <init>(Lcom/android/systemui/statusbar/phone/CentralSurfacesImpl;)V
    .locals 0
    .param p1, "centralSurfaces"    # Lcom/android/systemui/statusbar/phone/CentralSurfacesImpl;

    invoke-direct {p0}, Landroid/content/BroadcastReceiver;-><init>()V
    iput-object p1, p0, Lcom/android/systemui/UniversalAuthReceiver;->mCentralSurfaces:Lcom/android/systemui/statusbar/phone/CentralSurfacesImpl;
    return-void
.end method

# onReceive Method
.method public onReceive(Landroid/content/Context;Landroid/content/Intent;)V
    .locals 5
    .param p1, "context"    # Landroid/content/Context;
    .param p2, "intent"    # Landroid/content/Intent;

    .locals 5

    # Check Action
    invoke-virtual {p2}, Landroid/content/Intent;->getAction()Ljava/lang/String;
    move-result-object v0
    const-string v1, "ax.nd.universalauth.UNLOCK_DEVICE"
    invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v0
    if-nez v0, :cond_action_mismatch
    return-void

    :cond_action_mismatch
    
    # Check if we should bypass keyguard
    const-string v0, "ax.nd.universalauth.extra.BYPASS_KEYGUARD"
    const/4 v1, 0x1
    invoke-virtual {p2, v0, v1}, Landroid/content/Intent;->getBooleanExtra(Ljava/lang/String;Z)Z
    move-result v0

    if-eqz v0, :cond_no_bypass

    # --- BYPASS PATH (Calls BiometricUnlockController.startWakeAndUnlock) ---
    
    # Get Unlock Mode (Default to 1 - MODE_UNLOCK_FADING)
    const-string v0, "ax.nd.universalauth.extra.UNLOCK_MODE"
    invoke-virtual {p2, v0, v1}, Landroid/content/Intent;->getIntExtra(Ljava/lang/String;I)I
    move-result v0

    # Retrieve BiometricUnlockController from CentralSurfaces
    # CHECK FIELD NAME: mBiometricUnlockController
    iget-object v1, p0, Lcom/android/systemui/UniversalAuthReceiver;->mCentralSurfaces:Lcom/android/systemui/statusbar/phone/CentralSurfacesImpl;
    iget-object v1, v1, Lcom/android/systemui/statusbar/phone/CentralSurfacesImpl;->mBiometricUnlockController:Lcom/android/systemui/statusbar/phone/BiometricUnlockController;

    # Call startWakeAndUnlock(int)
    # NOTE: On Android 14+, this might be startWakeAndUnlock(int, BiometricUnlockSource)
    # If 14+: invoke-virtual {v1, v0, v2}, ... (where v2 is null or source)
    invoke-virtual {v1, v0}, Lcom/android/systemui/statusbar/phone/BiometricUnlockController;->startWakeAndUnlock(I)V
    
    goto :cond_end

    :cond_no_bypass
    # --- TRUST AGENT PATH (Calls KeyguardUpdateMonitor.onFaceAuthenticated) ---

    # Retrieve KeyguardUpdateMonitor from CentralSurfaces
    # CHECK FIELD NAME: mKeyguardUpdateMonitor
    iget-object v1, p0, Lcom/android/systemui/UniversalAuthReceiver;->mCentralSurfaces:Lcom/android/systemui/statusbar/phone/CentralSurfacesImpl;
    iget-object v1, v1, Lcom/android/systemui/statusbar/phone/CentralSurfacesImpl;->mKeyguardUpdateMonitor:Lcom/android/keyguard/KeyguardUpdateMonitor;

    # Call onFaceAuthenticated(int userId, boolean isStrong)
    # Get Current User (Assuming 0 for primary or fetch via ActivityManager if needed)
    invoke-static {}, Landroid/app/ActivityManager;->getCurrentUser()I
    move-result v2
    
    invoke-virtual {v1, v2, v1}, Lcom/android/keyguard/KeyguardUpdateMonitor;->onFaceAuthenticated(IZ)V

    :cond_end
    return-void
.end method

```

---

### **2. Modify: `CentralSurfacesImpl.smali**`

(Note: On Android 12/12L this class is `StatusBar.smali`. On 13+ it is `CentralSurfacesImpl.smali`.)

**Goal:** Register the receiver when SystemUI starts.

**Locate Method:** `start()`

**Add before return:**

```smali
    # ... existing start() code ...

    # UniversalAuth Receiver Registration
    new-instance v0, Lcom/android/systemui/UniversalAuthReceiver;
    invoke-direct {v0, p0}, Lcom/android/systemui/UniversalAuthReceiver;-><init>(Lcom/android/systemui/statusbar/phone/CentralSurfacesImpl;)V
    
    new-instance v1, Landroid/content/IntentFilter;
    const-string v2, "ax.nd.universalauth.UNLOCK_DEVICE"
    invoke-direct {v1, v2}, Landroid/content/IntentFilter;-><init>(Ljava/lang/String;)V

    # Use PERMISSION_UNLOCK_DEVICE constant from repo: "ax.nd.universalauth.permission.UNLOCK_DEVICE"
    const-string v2, "ax.nd.universalauth.permission.UNLOCK_DEVICE"
    
    # Handler (null)
    const/4 v3, 0x0

    # flags (RECEIVER_EXPORTED = 2 for Android 14, 0 for older might warn)
    # If standard registerReceiver(receiver, filter, permission, handler)
    # CHECK METHOD SIGNATURE in your framework. 
    # Usually: registerReceiver(BroadcastReceiver, IntentFilter, String, Handler)
    
    iget-object v4, p0, Lcom/android/systemui/statusbar/phone/CentralSurfacesImpl;->mContext:Landroid/content/Context;
    invoke-virtual {v4, v0, v1, v2, v3}, Landroid/content/Context;->registerReceiver(Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;Ljava/lang/String;Landroid/os/Handler;)Landroid/content/Intent;

    return-void

```

---

### **3. Modify: `KeyguardUpdateMonitor.smali**`

**Goal:** Broadcast the state changes.

**Step A: Add a new field**
At the top of the file with other instance fields:

```smali
.field private mLastUniversalAuthMode:Z

```

**Step B: Create helper method**
Add this new method to `KeyguardUpdateMonitor.smali` to keep the logic clean.

```smali
.method private broadcastUniversalAuthState()V
    .locals 6

    .locals 6

    # 1. Get mStatusBarStateController state
    # CHECK FIELD: mStatusBarStateController
    iget-object v0, p0, Lcom/android/keyguard/KeyguardUpdateMonitor;->mStatusBarStateController:Lcom/android/systemui/plugins/statusbar/StatusBarStateController;
    invoke-interface {v0}, Lcom/android/systemui/plugins/statusbar/StatusBarStateController;->getState()I
    move-result v0
    
    # SHADE_LOCKED = 2
    const/4 v1, 0x2
    const/4 v2, 0x0 
    const/4 v3, 0x1

    # Check if shade is locked
    if-ne v0, v1, :cond_shade_locked
    move v0, v3
    goto :goto_state_check
    :cond_shade_locked
    move v0, v2
    :goto_state_check

    # 2. Check mKeyguardIsVisible (CHECK FIELD NAME)
    iget-boolean v1, p0, Lcom/android/keyguard/KeyguardUpdateMonitor;->mKeyguardIsVisible:Z
    
    # 3. Check mDeviceInteractive (CHECK FIELD NAME)
    iget-boolean v4, p0, Lcom/android/keyguard/KeyguardUpdateMonitor;->mDeviceInteractive:Z
    
    # 4. Check mGoingToSleep (CHECK FIELD NAME)
    iget-boolean v5, p0, Lcom/android/keyguard/KeyguardUpdateMonitor;->mGoingToSleep:Z

    # Logic: awakeKeyguard = visible && interactive && !sleeping && !shadeLocked
    if-eqz v1, :cond_false
    if-eqz v4, :cond_false
    if-nez v5, :cond_false
    if-nez v0, :cond_false

    move v0, v3 # awakeKeyguard = true
    goto :goto_calc_done

    :cond_false
    move v0, v2 # awakeKeyguard = false

    :goto_calc_done
    
    # Check against last mode
    iget-boolean v1, p0, Lcom/android/keyguard/KeyguardUpdateMonitor;->mLastUniversalAuthMode:Z
    if-ne v1, v0, :cond_skip

    # Update last mode
    iput-boolean v0, p0, Lcom/android/keyguard/KeyguardUpdateMonitor;->mLastUniversalAuthMode:Z

    # Send Broadcast
    new-instance v1, Landroid/content/Intent;
    const-string v2, "ax.nd.universalauth.EARLY_UNLOCK"
    invoke-direct {v1, v2}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V

    const-string v2, "ax.nd.universalauth.extra.EARLY_UNLOCK_MODE"
    invoke-virtual {v1, v2, v0}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Z)Landroid/content/Intent;

    iget-object v2, p0, Lcom/android/keyguard/KeyguardUpdateMonitor;->mContext:Landroid/content/Context;
    invoke-virtual {v2, v1}, Landroid/content/Context;->sendBroadcast(Landroid/content/Intent;)V

    :cond_skip
    return-void
.end method

```

**Step C: Inject into `updateFaceListeningState**`
Locate `updateFaceListeningState` (or `updateBiometricListeningState` in newer Android versions).
At the very end of the method, before `return-void`, add:

```smali
    # Inject UniversalAuth hook
    invoke-direct {p0}, Lcom/android/keyguard/KeyguardUpdateMonitor;->broadcastUniversalAuthState()V

```

### **Finalizing & Fact Check**

* **Android Version Compatibility:** The code references `CentralSurfacesImpl`. If you are on Android 12 or lower, this class is likely `StatusBar`. You must verify the class name in your decompiled source.
* **Permissions:** Ensure `AndroidManifest.xml` of SystemUI has the permissions declared if Android enforces strict receiver permissions, though usually SystemUI runs as system/uid 1000 and can broadcast freely.
* **Field Obfuscation:** Xposed uses `getDeclaredField("mKeyguardIsVisible")`. In decompiled smali, this field might be `mKeyguardIsVisible` or something like `a` or `b` if minified. **You must grep for the field names** to ensure they match what is in your `KeyguardUpdateMonitor`.
* **Safety:** If `BiometricUnlockController` is null when the receiver fires (unlikely after boot), it triggers a crash. The Xposed module assumes it's initialized.

**Manual Modifications Required:**

1. Check `CentralSurfacesImpl.smali` vs `StatusBar.smali`.
2. Check `startWakeAndUnlock` signature in `BiometricUnlockController.smali`.
3. Match field names in `KeyguardUpdateMonitor.smali` (`mKeyguardIsVisible`, `mDeviceInteractive`, `mGoingToSleep`, `mStatusBarStateController`).
4. Add the `ax.nd.universalauth.permission.UNLOCK_DEVICE` to SystemUI `AndroidManifest.xml` `<permission>` and `<uses-permission>` tags if necessary, though strictly speaking, the sender (the App) needs to hold the permission if you enforce it in `registerReceiver`. In the code above, we enforce it on the sender.

Dependencies:

* None. The code is self-contained within SystemUI using Android framework APIs.
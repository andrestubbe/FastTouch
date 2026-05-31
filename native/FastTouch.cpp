/**
 * @file FastTouch.cpp
 * @brief Native Windows Touchscreen Input Implementation for Java
 * 
 * @details Implements the JNI native layer for FastTouch using the Windows
 * WM_POINTER API (Windows 8+). Provides low-latency touch event capture
 * with multi-touch support, pressure sensitivity, and contact size measurement.
 * 
 * @par Architecture
 * - Window subclassing to intercept WM_POINTER* messages
 * - Thread-safe touch state storage with CriticalSection
 * - Automatic stale touch detection (500ms timeout)
 * 
 * @par Platform Requirements
 * - Windows 8 or later (WM_POINTER API)
 * - user32.dll (for GetPointerTouchInfo)
 * 
 * @author FastJava Team
 * @version 1.1.0
 * @since 1.0.0
 */

#include <jni.h>
#include <windows.h>
#include <stdio.h>

#pragma comment(lib, "user32.lib")

// ============================================================================
// WM_POINTER API CONSTANTS (Windows 8+)
// ============================================================================

#ifndef WM_POINTERDOWN
/** @brief WM_POINTERDOWN message constant (0x0246) - Finger contacts screen */
#define WM_POINTERDOWN 0x0246
/** @brief WM_POINTERUP message constant (0x0247) - Finger lifts from screen */
#define WM_POINTERUP 0x0247
/** @brief WM_POINTERUPDATE message constant (0x0245) - Finger moves on screen */
#define WM_POINTERUPDATE 0x0245
/** @brief Extract pointer ID from WPARAM */
#define GET_POINTERID_WPARAM(wParam) (LOWORD(wParam))
#endif

// ============================================================================
// WM_GESTURE API CONSTANTS (Windows 7+)
// ============================================================================

#ifndef WM_GESTURE
/** @brief WM_GESTURE message constant (0x0119) - Gesture detected */
#define WM_GESTURE 0x0119
/** @brief Begin gesture message */
#define WM_GESTURENOTIFY 0x011A
#endif

#ifndef GID_BEGIN
/** @brief Gesture IDs for WM_GESTURE */
#define GID_BEGIN       1
#define GID_END         2
#define GID_ZOOM        3   /**< Pinch/Zoom gesture */
#define GID_PAN         4
#define GID_ROTATE      5   /**< Rotation gesture */
#define GID_TWOFINGERTAP 6
#define GID_ROLLOVER    7
#endif

/** @brief Enable/disable gesture flags */
#ifndef GF_BEGIN
#define GF_BEGIN 0x00000001
#define GF_INERTIA 0x00000002
#define GF_END 0x00000004
#endif

/** @brief Decode zoom distance from gesture argument */
#ifndef GID_ZOOM_DISTANCE_FROM_ARGUMENT
#define GID_ZOOM_DISTANCE_FROM_ARGUMENT(arg) (float)((arg) & 0xFFFF)
#endif
/** @brief Decode rotation angle from gesture argument (in radians) */
#ifndef GID_ROTATE_ANGLE_FROM_ARGUMENT
#define GID_ROTATE_ANGLE_FROM_ARGUMENT(arg) (float)((arg) / 65536.0)
#endif

/**
 * @brief Windows GESTUREINFO structure for gesture data (local definition)
 */
typedef struct {
    UINT cbSize;           /**< Size of structure */
    DWORD dwFlags;         /**< Gesture flags (GF_BEGIN, GF_INERTIA, GF_END) */
    DWORD dwID;            /**< Gesture ID (GID_ZOOM, GID_ROTATE, etc.) */
    HWND hwndTarget;       /**< Target window */
    POINTS ptsLocation;    /**< Gesture location (x, y in screen coords) */
    DWORD dwInstanceID;    /**< Instance ID */
    DWORD dwSequenceID;    /**< Sequence ID */
    ULONGLONG ullArguments;/**< Gesture-specific arguments */
    UINT cbExtraArgs;      /**< Size of extra arguments */
} FASTTOUCH_GESTUREINFO;

// ============================================================================
// POINTER API FUNCTION TYPEDEFS (Dynamically Loaded)
// ============================================================================

typedef BOOL (WINAPI *GetPointerTypeFunc)(UINT32 pointerId, POINTER_INPUT_TYPE *pointerType);
typedef BOOL (WINAPI *GetPointerTouchInfoFunc)(UINT32 pointerId, POINTER_TOUCH_INFO *touchInfo);
typedef BOOL (WINAPI *GetPointerFrameTouchInfoFunc)(UINT32 pointerId, UINT32 *pointerCount, POINTER_TOUCH_INFO *touchInfo);
typedef BOOL (WINAPI *GetGestureInfoFunc)(HGESTUREINFO hGestureInfo, FASTTOUCH_GESTUREINFO* pGestureInfo);
typedef BOOL (WINAPI *SetGestureConfigFunc)(HWND hwnd, DWORD dwReserved, UINT cIDs, PGESTURECONFIG pGestureConfig, UINT cbSize);

static GetPointerTouchInfoFunc pGetPointerTouchInfo = nullptr;  /**< Dynamically loaded GetPointerTouchInfo */

// ============================================================================
// GLOBAL STATE
// ============================================================================

static HWND g_hwnd = nullptr;           /**< Target window handle for touch input */
static bool g_initialized = false;      /**< True if subsystem initialized */
static bool g_touchAvailable = false;   /**< True if WM_POINTER API is available */
static bool g_gestureAvailable = false;
static bool g_gestureEnabled = false;   /**< True if gesture recognition enabled */

// Gesture function pointer
static GetGestureInfoFunc pGetGestureInfo = nullptr;
static SetGestureConfigFunc pSetGestureConfig = nullptr;

// JNI callback state (reserved for future event-driven mode)
static JavaVM* g_javaVM = nullptr;      /**< Cached JavaVM for callbacks */
static jclass g_fastTouchClass = nullptr; /**< Cached FastTouch class reference */
static jmethodID g_onPinchMethod = nullptr;
static jmethodID g_onRotateMethod = nullptr;
static jmethodID g_onGestureEndMethod = nullptr;
static jmethodID g_onNativeTouchMethod = nullptr; /**< Cached onNativeTouch method ID */

/** @brief Maximum number of touch points supported simultaneously */
#define MAX_TOUCH_POINTS 20

/**
 * @brief Internal touch point storage structure
 * 
 * Stores all information about a single touch point including position,
 * pressure, contact size, and lifecycle state.
 */
struct TouchPoint {
    int id;           /**< Unique touch identifier (0-9) */
    int x;            /**< X coordinate in client pixels */
    int y;            /**< Y coordinate in client pixels */
    int pressure;     /**< Pressure 0-255 (scaled from Windows 0-1024) */
    int width;        /**< Contact width in pixels */
    int height;       /**< Contact height in pixels */
    long timestamp;   /**< Event timestamp (GetTickCount) */
    int state;       // -1=NONE, 0=DOWN, 1=MOVE, 2=UP
    bool active;
};

static TouchPoint g_touchPoints[MAX_TOUCH_POINTS];
static int g_activeSlots[MAX_TOUCH_POINTS]; // Snapshot of active slot indices
static int g_snapshotCount = 0;             // Number of active slots in snapshot
static int g_touchCount = 0;                        /**< Current number of active+ending touches */
static CRITICAL_SECTION g_touchLock;                /**< Thread lock for touch state access */

static WNDPROC g_origWndProc = nullptr;  /**< Original window procedure (for subclass chaining) */

/**
 * @brief Window procedure hook for intercepting WM_POINTER messages
 * 
 * @details Subclasses the target window to capture touch events. Processes
 * WM_POINTERDOWN, WM_POINTERUPDATE, and WM_POINTERUP messages, extracting
 * touch coordinates, pressure, and contact size. Chains to original WNDPROC
 * for non-touch messages.
 * 
 * @param hwnd Window handle receiving the message
 * @param msg Windows message identifier
 * @param wParam Message-specific parameter (contains pointer ID)
 * @param lParam Message-specific parameter (contains coordinates)
 * @return LRESULT Message result (0 if processed, chained otherwise)
 * 
 * @note Thread-safe: Uses g_touchLock CriticalSection
 * @see Java_fasttouch_FastTouch_initNative
 */
static LRESULT CALLBACK TouchWndProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    switch (msg) {
        case WM_POINTERDOWN:
        case WM_POINTERUPDATE:
        case WM_POINTERUP:
        case 0x024C: { // WM_POINTERCAPTURECHANGED
            UINT32 pointerId = GET_POINTERID_WPARAM(wParam);
            
            EnterCriticalSection(&g_touchLock);
            
            // Find existing touch slot
            int slot = -1;
            for (int j = 0; j < MAX_TOUCH_POINTS; j++) {
                if (g_touchPoints[j].id == (int)pointerId && g_touchPoints[j].active) {
                    slot = j;
                    break;
                }
            }
            
            // If it's a new touch point, allocate a slot before querying info
            if (msg == WM_POINTERDOWN && slot == -1) {
                for (int j = 0; j < MAX_TOUCH_POINTS; j++) {
                    if (!g_touchPoints[j].active && g_touchPoints[j].state != 2) {
                        slot = j;
                        g_touchPoints[j].active = true;
                        g_touchPoints[j].id = pointerId;
                        g_touchPoints[j].timestamp = GetTickCount();
                        
                        break;
                    }
                }
            }
            
            // Handle UP separately - pointer info may not be available
            if (msg == WM_POINTERUP || msg == 0x024C) {
                if (slot != -1) {
                    g_touchPoints[slot].state = 2; // UP
                    g_touchPoints[slot].active = false;
                    
                }
                LeaveCriticalSection(&g_touchLock);
                break;
            }
            
            // For DOWN/UPDATE, get touch info
            if (pGetPointerTouchInfo && slot != -1) {
                POINTER_TOUCH_INFO touchInfo;
                if (pGetPointerTouchInfo(pointerId, &touchInfo)) {
                    // Coordinates - handle UI scaling (Windows gives physical pixels)
                    POINT pt;
                    pt.x = touchInfo.pointerInfo.ptPixelLocation.x;
                    pt.y = touchInfo.pointerInfo.ptPixelLocation.y;
                    ScreenToClient(hwnd, &pt);
                    
                    g_touchPoints[slot].x = pt.x;
                    g_touchPoints[slot].y = pt.y;
                    g_touchPoints[slot].timestamp = GetTickCount();
                    
                    // Contact size
                    g_touchPoints[slot].width = touchInfo.rcContact.right - touchInfo.rcContact.left;
                    g_touchPoints[slot].height = touchInfo.rcContact.bottom - touchInfo.rcContact.top;
                    
                    // Pressure (0-1024 from Windows API, we scale to 0-255)
                    g_touchPoints[slot].pressure = (touchInfo.pressure * 255) / 1024;
                    
                    // State
                    if (msg == WM_POINTERDOWN) {
                        g_touchPoints[slot].state = 0; // DOWN
                        
                    } else {
                        g_touchPoints[slot].state = 1; // MOVE
                        
                        // Only log if position changed to avoid spam from pressure/keep-alive updates
                        static int lastX[MAX_TOUCH_POINTS] = {0};
                        static int lastY[MAX_TOUCH_POINTS] = {0};
                        
                        if (pt.x != lastX[slot] || pt.y != lastY[slot]) {
                            static int moveCount = 0;
                            if (++moveCount % 10 == 0) {
                                
                            }
                            lastX[slot] = pt.x;
                            lastY[slot] = pt.y;
                        }
                    }
                }
            }
            
            // Count active touches
            g_touchCount = 0;
            for (int i = 0; i < MAX_TOUCH_POINTS; i++) {
                if (g_touchPoints[i].active || g_touchPoints[i].state == 2) {
                    g_touchCount++;
                }
            }
            
            LeaveCriticalSection(&g_touchLock);
            break;
        }
        
        case WM_GESTURE: {
            if (!g_gestureEnabled || !pGetGestureInfo) {
                break;
            }
            if (pGetGestureInfo) {
                FASTTOUCH_GESTUREINFO gi;
                ZeroMemory(&gi, sizeof(gi));
                gi.cbSize = sizeof(FASTTOUCH_GESTUREINFO);
                
                if (pGetGestureInfo((HGESTUREINFO)lParam, &gi)) {
                    // Check if gesture is ending
                    if (gi.dwFlags & 0x00000004) { // GF_END
                        if (g_javaVM && g_fastTouchClass && g_onGestureEndMethod) {
                            JNIEnv* env = nullptr;
                            bool attached = false;
                            if (g_javaVM->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
                                g_javaVM->AttachCurrentThread((void**)&env, nullptr);
                                attached = true;
                            }
                            if (env) {
                                env->CallStaticVoidMethod(g_fastTouchClass, g_onGestureEndMethod);
                                if (attached) {
                                    g_javaVM->DetachCurrentThread();
                                }
                            }
                        }
                    }
                    
                    // Extract coordinates
                    POINT pt;
                    pt.x = gi.ptsLocation.x;
                    pt.y = gi.ptsLocation.y;
                    ScreenToClient(hwnd, &pt);
                    
                    switch (gi.dwID) {
                    case GID_ZOOM: {
                        // Pinch/Zoom gesture - extract distance and normalize to scale
                        float distance = GID_ZOOM_DISTANCE_FROM_ARGUMENT(gi.ullArguments);
                        // Normalize scale: distance around 100 = neutral (1.0)
                        float scale = distance / 100.0f;
                        if (scale < 0.1f) scale = 0.1f; // Clamp minimum
                        
                        
                        
                        // Call Java callback via JNI
                        if (g_javaVM && g_fastTouchClass && g_onPinchMethod) {
                            JNIEnv* env = nullptr;
                            bool attached = false;
                            if (g_javaVM->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
                                g_javaVM->AttachCurrentThread((void**)&env, nullptr);
                                attached = true;
                            }
                            if (env) {
                                env->CallStaticVoidMethod(g_fastTouchClass, g_onPinchMethod, 
                                    scale, (float)pt.x, (float)pt.y);
                                if (attached) {
                                    g_javaVM->DetachCurrentThread();
                                }
                            }
                        }
                        CloseGestureInfoHandle((HGESTUREINFO)lParam);
                        return 0;
                    }
                    
                    case GID_ROTATE: {
                        // Rotation gesture - extract angle and convert to degrees
                        float angleRad = GID_ROTATE_ANGLE_FROM_ARGUMENT(gi.ullArguments);
                        float angleDeg = angleRad * (180.0f / 3.14159265f);
                        
                        
                        
                        // Call Java callback via JNI
                        if (g_javaVM && g_fastTouchClass && g_onRotateMethod) {
                            JNIEnv* env = nullptr;
                            bool attached = false;
                            if (g_javaVM->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
                                g_javaVM->AttachCurrentThread((void**)&env, nullptr);
                                attached = true;
                            }
                            if (env) {
                                env->CallStaticVoidMethod(g_fastTouchClass, g_onRotateMethod, 
                                    angleDeg, (float)pt.x, (float)pt.y);
                                if (attached) {
                                    g_javaVM->DetachCurrentThread();
                                }
                            }
                        }
                        CloseGestureInfoHandle((HGESTUREINFO)lParam);
                        return 0;
                    }
                    }
                    // For unhandled gestures, we must close the handle
                    CloseGestureInfoHandle((HGESTUREINFO)lParam);
                }
            }
            break;
        }
    }
    
    if (g_origWndProc) {
        return CallWindowProc(g_origWndProc, hwnd, msg, wParam, lParam);
    }
    return DefWindowProc(hwnd, msg, wParam, lParam);
}

// ============================================================================
// JNI EXPORTED FUNCTIONS
// ============================================================================

extern "C" {

/**
 * @brief Initializes native touch input for the specified window
 * 
 * @details Subclasses the target window to intercept WM_POINTER messages.
 * Dynamically loads GetPointerTouchInfo from user32.dll (Windows 8+).
 * Creates CriticalSection for thread-safe touch state access.
 * 
 * @param env JNI environment (unused, for future callback support)
 * @param clazz Java class reference (unused)
 * @param hwnd Native window handle (HWND cast to jlong)
 * 
 * @note Must be called before any touch events can be received
 * @warning g_hwnd must remain valid for the lifetime of touch input
 * @see TouchWndProc
 */
JNIEXPORT void JNICALL Java_fasttouch_FastTouch_initNative(JNIEnv* env, jclass clazz, jlong hwnd) {
    g_hwnd = (HWND)hwnd;
    
    // Cache JavaVM for gesture callbacks
    env->GetJavaVM(&g_javaVM);
    
    // Cache FastTouch class reference
    g_fastTouchClass = (jclass)env->NewGlobalRef(clazz);
    
    g_onPinchMethod = env->GetStaticMethodID(g_fastTouchClass, "onNativePinch", "(FFF)V");
    g_onRotateMethod = env->GetStaticMethodID(g_fastTouchClass, "onNativeRotate", "(FFF)V");
    g_onGestureEndMethod = env->GetStaticMethodID(g_fastTouchClass, "onNativeGestureEnd", "()V");
    
    InitializeCriticalSection(&g_touchLock);
    
    // Load Windows 8 Pointer APIs dynamically
    HMODULE user32 = GetModuleHandleA("user32.dll");
    if (user32) {
        pGetPointerTouchInfo = (GetPointerTouchInfoFunc)GetProcAddress(user32, "GetPointerTouchInfo");
        pGetGestureInfo = (GetGestureInfoFunc)GetProcAddress(user32, "GetGestureInfo");
        pSetGestureConfig = (SetGestureConfigFunc)GetProcAddress(user32, "SetGestureConfig");
    }
    
    // WM_POINTER requires Windows 8+ (GetPointerTouchInfo)
    g_touchAvailable = (pGetPointerTouchInfo != nullptr);
    
    // WM_GESTURE requires Windows 7+ (GetGestureInfo)
    g_gestureAvailable = (pGetGestureInfo != nullptr);
    
    if (g_touchAvailable && g_hwnd) {
        // Subclass window to intercept pointer messages
        g_origWndProc = (WNDPROC)SetWindowLongPtr(g_hwnd, GWLP_WNDPROC, (LONG_PTR)TouchWndProc);
        g_initialized = true;
        
        
        if (g_gestureAvailable) {
            
        }
    } else {
        
    }
}

/**
 * @brief Finds a native window by its title
 * 
 * @param env JNI environment
 * @param clazz Java class reference (unused)
 * @param title Window title to search for
 * @return jlong Native HWND handle, or 0 if not found
 * 
 * @note Top-level windows only (FindWindowA with null class)
 */
JNIEXPORT jlong JNICALL Java_fasttouch_FastTouch_findWindow(JNIEnv* env, jclass, jstring title) {
    const jchar* str = nullptr;
    if (title) str = env->GetStringChars(title, nullptr);
    HWND hwnd = FindWindowW(nullptr, (LPCWSTR)str);
    if (title && str) env->ReleaseStringChars(title, str);
    return (jlong)hwnd;
}

JNIEXPORT void JNICALL Java_fasttouch_FastTouch_stopNative(JNIEnv* env, jclass clazz) {
    if (g_hwnd && g_origWndProc) {
        SetWindowLongPtr(g_hwnd, GWLP_WNDPROC, (LONG_PTR)g_origWndProc);
        g_origWndProc = nullptr;
    }
    if (g_fastTouchClass) {
        env->DeleteGlobalRef(g_fastTouchClass);
        g_fastTouchClass = nullptr;
    }
    g_initialized = false;
}

JNIEXPORT void JNICALL Java_fasttouch_FastTouch_clearUpSlotById(JNIEnv*, jclass, jint id) {
    EnterCriticalSection(&g_touchLock);
    for (int i = 0; i < MAX_TOUCH_POINTS; i++) {
        // Reset slot only if it matches the ID and is already marked as UP
        if (g_touchPoints[i].id == id && !g_touchPoints[i].active && g_touchPoints[i].state == 2) {
            ZeroMemory(&g_touchPoints[i], sizeof(TouchPoint));
            g_touchPoints[i].state = -1; // Fully clear
            break;
        }
    }
    LeaveCriticalSection(&g_touchLock);
}

/**
 * @brief Processes pending window messages and checks for stale touches
 * 
 * @details Polls the Windows message queue and automatically releases
 * touch points that haven't been updated for 500ms (stale detection).
 * Called periodically by the Java polling thread.
 * 
 * @note Thread-safe: Uses g_touchLock for state access
 * @warning Should be called regularly to prevent stale touch accumulation
 */
JNIEXPORT void JNICALL Java_fasttouch_FastTouch_pollNative(JNIEnv*, jclass) {
    // Process window messages
    MSG msg;
    while (PeekMessage(&msg, nullptr, 0, 0, PM_REMOVE)) {
        TranslateMessage(&msg);
        DispatchMessage(&msg);
    }
    
    // Check for stale touches (no update for > 500ms = UP)
    EnterCriticalSection(&g_touchLock);
    DWORD now = GetTickCount();
    for (int i = 0; i < MAX_TOUCH_POINTS; i++) {
        if (g_touchPoints[i].active && (now - g_touchPoints[i].timestamp > 500)) {
            // No update for 500ms - force UP
            g_touchPoints[i].state = 2; // UP
            g_touchPoints[i].active = false;
            
        }
    }
    LeaveCriticalSection(&g_touchLock);
}

/**
 * @brief Returns the current number of touch points (active + ending)
 * @return Number of touch points in the buffer
 * @note Thread-safe: Uses g_touchLock
 */
JNIEXPORT jint JNICALL Java_fasttouch_FastTouch_getTouchCount(JNIEnv*, jclass) {
    EnterCriticalSection(&g_touchLock);
    g_snapshotCount = 0;
    for (int i = 0; i < MAX_TOUCH_POINTS; i++) {
        if (g_touchPoints[i].active || g_touchPoints[i].state == 2) {
            g_activeSlots[g_snapshotCount++] = i;
        }
    }
    int count = g_snapshotCount;
    LeaveCriticalSection(&g_touchLock);
    return count;
}

/**
 * @brief Gets the touch ID at the specified index
 * @param index Touch point index (0 to g_snapshotCount-1)
 * @return Touch ID (0-9), or -1 if index invalid
 */
JNIEXPORT jint JNICALL Java_fasttouch_FastTouch_getTouchId(JNIEnv*, jclass, jint index) {
    if (index < 0 || index >= MAX_TOUCH_POINTS) return -1;
    EnterCriticalSection(&g_touchLock);
    int realIndex = index < g_snapshotCount ? g_activeSlots[index] : -1;
    jint val = realIndex != -1 ? g_touchPoints[realIndex].id : -1;
    LeaveCriticalSection(&g_touchLock);
    return val;
}

/**
 * @brief Gets the X coordinate at the specified index
 * @param index Touch point index
 * @return X coordinate in pixels, or 0 if index invalid
 */
JNIEXPORT jint JNICALL Java_fasttouch_FastTouch_getTouchX(JNIEnv*, jclass, jint index) {
    if (index < 0 || index >= MAX_TOUCH_POINTS) return 0;
    EnterCriticalSection(&g_touchLock);
    int realIndex = index < g_snapshotCount ? g_activeSlots[index] : -1;
    jint val = realIndex != -1 ? g_touchPoints[realIndex].x : 0;
    LeaveCriticalSection(&g_touchLock);
    return val;
}

/**
 * @brief Gets the Y coordinate at the specified index
 * @param index Touch point index
 * @return Y coordinate in pixels, or 0 if index invalid
 */
JNIEXPORT jint JNICALL Java_fasttouch_FastTouch_getTouchY(JNIEnv*, jclass, jint index) {
    if (index < 0 || index >= MAX_TOUCH_POINTS) return 0;
    EnterCriticalSection(&g_touchLock);
    int realIndex = index < g_snapshotCount ? g_activeSlots[index] : -1;
    jint val = realIndex != -1 ? g_touchPoints[realIndex].y : 0;
    LeaveCriticalSection(&g_touchLock);
    return val;
}

/**
 * @brief Gets the pressure at the specified index
 * @param index Touch point index
 * @return Pressure 0-255, or 0 if index invalid
 */
JNIEXPORT jint JNICALL Java_fasttouch_FastTouch_getTouchPressure(JNIEnv*, jclass, jint index) {
    if (index < 0 || index >= MAX_TOUCH_POINTS) return 0;
    EnterCriticalSection(&g_touchLock);
    int realIndex = index < g_snapshotCount ? g_activeSlots[index] : -1;
    jint val = realIndex != -1 ? g_touchPoints[realIndex].pressure : 0;
    LeaveCriticalSection(&g_touchLock);
    return val;
}

/**
 * @brief Gets the contact width at the specified index
 * @param index Touch point index
 * @return Width in pixels, or 0 if index invalid
 */
JNIEXPORT jint JNICALL Java_fasttouch_FastTouch_getTouchWidth(JNIEnv*, jclass, jint index) {
    if (index < 0 || index >= MAX_TOUCH_POINTS) return 0;
    EnterCriticalSection(&g_touchLock);
    int realIndex = index < g_snapshotCount ? g_activeSlots[index] : -1;
    jint val = realIndex != -1 ? g_touchPoints[realIndex].width : 0;
    LeaveCriticalSection(&g_touchLock);
    return val;
}

/**
 * @brief Gets the contact height at the specified index
 * @param index Touch point index
 * @return Height in pixels, or 0 if index invalid
 */
JNIEXPORT jint JNICALL Java_fasttouch_FastTouch_getTouchHeight(JNIEnv*, jclass, jint index) {
    if (index < 0 || index >= MAX_TOUCH_POINTS) return 0;
    EnterCriticalSection(&g_touchLock);
    int realIndex = index < g_snapshotCount ? g_activeSlots[index] : -1;
    jint val = realIndex != -1 ? g_touchPoints[realIndex].height : 0;
    LeaveCriticalSection(&g_touchLock);
    return val;
}

/**
 * @brief Gets the touch state at the specified index
 * @param index Touch point index
 * @return State (0=DOWN, 1=MOVE, 2=UP), or 2 (UP) if index invalid
 */
JNIEXPORT jint JNICALL Java_fasttouch_FastTouch_getTouchState(JNIEnv*, jclass, jint index) {
    if (index < 0 || index >= MAX_TOUCH_POINTS) return 2; // UP
    EnterCriticalSection(&g_touchLock);
    int realIndex = index < g_snapshotCount ? g_activeSlots[index] : -1;
    jint val = realIndex != -1 ? g_touchPoints[realIndex].state : 2;
    LeaveCriticalSection(&g_touchLock);
    return val;
}

/**
 * @brief Gets the timestamp at the specified index
 * @param index Touch point index
 * @return Timestamp in milliseconds, or 0 if index invalid
 */
JNIEXPORT jlong JNICALL Java_fasttouch_FastTouch_getTouchTimestamp(JNIEnv*, jclass, jint index) {
    if (index < 0 || index >= MAX_TOUCH_POINTS) return 0;
    EnterCriticalSection(&g_touchLock);
    int realIndex = index < g_snapshotCount ? g_activeSlots[index] : -1;
    jlong val = realIndex != -1 ? g_touchPoints[realIndex].timestamp : 0;
    LeaveCriticalSection(&g_touchLock);
    return val;
}

/**
 * @brief Checks if WM_POINTER API is available on this system
 * @return JNI_TRUE if touch input is available, JNI_FALSE otherwise
 */
JNIEXPORT jboolean JNICALL Java_fasttouch_FastTouch_isTouchAvailable(JNIEnv*, jclass) {
    return g_touchAvailable ? JNI_TRUE : JNI_FALSE;
}

/**
 * @brief Returns the maximum supported simultaneous touch points
 * @return Maximum touch points (10 for typical hardware)
 */
JNIEXPORT jint JNICALL Java_fasttouch_FastTouch_getMaxTouchPoints(JNIEnv*, jclass) {
    return 10;
}

/**
 * @brief Enables or disables native gesture recognition (WM_GESTURE)
 * 
 * @details When enabled, the window will receive GID_ZOOM (pinch) and 
 * GID_ROTATE gestures from Windows. These are delivered via the 
 * GestureCallback mechanism to Java.
 * 
 * @param env JNI environment
 * @param obj FastTouch instance
 * @param enable true to enable gesture recognition, false to disable
 */
JNIEXPORT void JNICALL Java_fasttouch_FastTouch_setGestureEnabled(JNIEnv*, jobject, jboolean enable) {
    g_gestureEnabled = (enable == JNI_TRUE);
    
    if (g_gestureEnabled && g_gestureAvailable) {
        
    } else if (g_gestureEnabled && !g_gestureAvailable) {
        
    } else {
        
    }
}

} // extern "C"


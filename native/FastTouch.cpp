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
#define GID_ROTATE_ANGLE_FROM_ARGUMENT(arg) (float)((arg) / 1000.0)
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

/** @brief Function pointer type for GetPointerTouchInfo (Windows 8+) */
typedef BOOL (WINAPI *GetPointerTouchInfoFunc)(UINT32 pointerId, POINTER_TOUCH_INFO* touchInfo);
/** @brief Function pointer type for GetPointerInfo (Windows 8+) */
typedef BOOL (WINAPI *GetPointerInfoFunc)(UINT32 pointerId, POINTER_INFO* pointerInfo);
/** @brief Function pointer type for GetPointerPenInfo (Windows 8+, for stylus) */
typedef BOOL (WINAPI *GetPointerPenInfoFunc)(UINT32 pointerId, POINTER_PEN_INFO* penInfo);
/** @brief Function pointer type for GetPointerFrameTouchInfo (multi-touch frames) */
typedef BOOL (WINAPI *GetPointerFrameTouchInfoFunc)(UINT32 pointerId, UINT32* pointerCount, POINTER_TOUCH_INFO* touchInfo);

static GetPointerTouchInfoFunc pGetPointerTouchInfo = nullptr;  /**< Dynamically loaded GetPointerTouchInfo */
static GetPointerInfoFunc pGetPointerInfo = nullptr;          /**< Dynamically loaded GetPointerInfo */
static GetPointerPenInfoFunc pGetPointerPenInfo = nullptr;    /**< Dynamically loaded GetPointerPenInfo */

// ============================================================================
// GLOBAL STATE
// ============================================================================

static HWND g_hwnd = nullptr;           /**< Target window handle for touch input */
static bool g_initialized = false;      /**< True after successful initialization */
static bool g_touchAvailable = false;   /**< True if WM_POINTER API is available */
static bool g_gestureAvailable = false; /**< True if WM_GESTURE API is available */
static bool g_gestureEnabled = false;   /**< True if gesture recognition enabled */

// Gesture function pointer
typedef BOOL (WINAPI *GetGestureInfoFunc)(HWND hwnd, FASTTOUCH_GESTUREINFO* pGestureInfo);
static GetGestureInfoFunc pGetGestureInfo = nullptr;

// JNI callback state (reserved for future event-driven mode)
static JavaVM* g_javaVM = nullptr;              /**< Cached JavaVM pointer */
static jclass g_fastTouchClass = nullptr;       /**< Cached FastTouch class reference */
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
    int state;        /**< 0=DOWN, 1=MOVE, 2=UP */
    bool active;      /**< True if finger currently touching */
};

static TouchPoint g_touchPoints[MAX_TOUCH_POINTS];  /**< Ring buffer for touch point storage */
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
        case WM_POINTERUP: {
            UINT32 pointerId = GET_POINTERID_WPARAM(wParam);
            BOOL isTouch = (msg != WM_POINTERUP); // UP might not have touch info
            
            EnterCriticalSection(&g_touchLock);
            
            // Find existing touch slot
            int slot = -1;
            for (int j = 0; j < MAX_TOUCH_POINTS; j++) {
                if (g_touchPoints[j].id == (int)pointerId && g_touchPoints[j].active) {
                    slot = j;
                    break;
                }
            }
            
            // Handle UP separately - pointer info may not be available
            if (msg == WM_POINTERUP) {
                if (slot != -1) {
                    g_touchPoints[slot].state = 2; // UP
                    g_touchPoints[slot].active = false;
                    fprintf(stderr, "[FastTouch] Pointer UP id=%d\n", pointerId);
                }
                LeaveCriticalSection(&g_touchLock);
                return 0;
            }
            
            // For DOWN/UPDATE, get touch info
            if (pGetPointerTouchInfo && slot != -1) {
                POINTER_TOUCH_INFO touchInfo;
                if (pGetPointerTouchInfo(pointerId, &touchInfo)) {
                    // Coordinates - handle UI scaling (Windows gives physical pixels)
                    POINT pt;
                    pt.x = touchInfo.pointerInfo.ptPixelLocation.x;
                    pt.y = touchInfo.pointerInfo.ptPixelLocation.y;
                    ScreenToClient(g_hwnd, &pt);
                    
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
                        fprintf(stderr, "[FastTouch] Pointer DOWN id=%d at (%d,%d) pressure=%d\n", 
                                pointerId, pt.x, pt.y, g_touchPoints[slot].pressure);
                    } else {
                        g_touchPoints[slot].state = 1; // MOVE
                        // Nur alle 30 frames ein MOVE log um Spam zu vermeiden
                        static int moveCount = 0;
                        if (++moveCount % 30 == 0) {
                            fprintf(stderr, "[FastTouch] Pointer MOVE id=%d at (%d,%d)\n", pointerId, pt.x, pt.y);
                        }
                    }
                }
            } else if (msg == WM_POINTERDOWN && slot == -1) {
                // Create new slot for DOWN
                for (int j = 0; j < MAX_TOUCH_POINTS; j++) {
                    if (!g_touchPoints[j].active && g_touchPoints[j].state != 2) {
                        slot = j;
                        g_touchPoints[j].active = true;
                        g_touchPoints[j].id = pointerId;
                        g_touchPoints[j].state = 0; // DOWN
                        g_touchPoints[j].timestamp = GetTickCount();
                        fprintf(stderr, "[FastTouch] New slot %d for id=%d\n", slot, pointerId);
                        break;
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
            return 0;
        }
        
        /**
         * @brief WM_GESTURE handler for Pinch (Zoom) and Rotate gestures
         * 
         * @details Processes Windows 7+ gesture messages. Supports:
         * - GID_ZOOM: Two-finger pinch/zoom (scale factor calculated from distance)
         * - GID_ROTATE: Two-finger rotation (angle in degrees)
         * 
         * Gesture coordinates are converted from screen to client space.
         * Events are forwarded to Java via JNI callbacks.
         * 
         * @note Requires gesture recognition to be enabled via setGestureEnabled()
         * @see Java_fasttouch_FastTouch_setGestureEnabled
         */
        case WM_GESTURE: {
            if (!g_gestureEnabled || !pGetGestureInfo) {
                break; // Let DefWindowProc handle it
            }
            
            FASTTOUCH_GESTUREINFO gi;
            gi.cbSize = sizeof(gi);
            
            if (pGetGestureInfo(hwnd, &gi)) {
                // Convert screen coordinates to client
                POINT pt;
                pt.x = gi.ptsLocation.x;
                pt.y = gi.ptsLocation.y;
                ScreenToClient(g_hwnd, &pt);
                
                switch (gi.dwID) {
                    case GID_ZOOM: {
                        // Pinch/Zoom gesture - extract distance and normalize to scale
                        float distance = GID_ZOOM_DISTANCE_FROM_ARGUMENT(gi.ullArguments);
                        // Normalize scale: distance around 100 = neutral (1.0)
                        float scale = distance / 100.0f;
                        if (scale < 0.1f) scale = 0.1f; // Clamp minimum
                        
                        fprintf(stderr, "[FastTouch] GESTURE PINCH scale=%.2f at (%d,%d)\n", 
                                scale, pt.x, pt.y);
                        
                        // Call Java callback via JNI
                        if (g_javaVM && g_fastTouchClass) {
                            JNIEnv* env;
                            if (g_javaVM->AttachCurrentThread((void**)&env, nullptr) == JNI_OK) {
                                jmethodID method = env->GetStaticMethodID(g_fastTouchClass, 
                                    "onNativePinch", "(FFF)V");
                                if (method) {
                                    env->CallStaticVoidMethod(g_fastTouchClass, method, 
                                        scale, (float)pt.x, (float)pt.y);
                                }
                                g_javaVM->DetachCurrentThread();
                            }
                        }
                        return 0;
                    }
                    
                    case GID_ROTATE: {
                        // Rotation gesture - extract angle and convert to degrees
                        float angleRad = GID_ROTATE_ANGLE_FROM_ARGUMENT(gi.ullArguments);
                        float angleDeg = angleRad * (180.0f / 3.14159265f);
                        
                        fprintf(stderr, "[FastTouch] GESTURE ROTATE angle=%.1f° at (%d,%d)\n", 
                                angleDeg, pt.x, pt.y);
                        
                        // Call Java callback via JNI
                        if (g_javaVM && g_fastTouchClass) {
                            JNIEnv* env;
                            if (g_javaVM->AttachCurrentThread((void**)&env, nullptr) == JNI_OK) {
                                jmethodID method = env->GetStaticMethodID(g_fastTouchClass, 
                                    "onNativeRotate", "(FFF)V");
                                if (method) {
                                    env->CallStaticVoidMethod(g_fastTouchClass, method, 
                                        angleDeg, (float)pt.x, (float)pt.y);
                                }
                                g_javaVM->DetachCurrentThread();
                            }
                        }
                        return 0;
                    }
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
    
    InitializeCriticalSection(&g_touchLock);
    
    // Load WM_POINTER API (Windows 8+)
    HMODULE hUser32 = GetModuleHandleA("user32.dll");
    if (hUser32) {
        pGetPointerTouchInfo = (GetPointerTouchInfoFunc)GetProcAddress(hUser32, "GetPointerTouchInfo");
        pGetPointerInfo = (GetPointerInfoFunc)GetProcAddress(hUser32, "GetPointerInfo");
        pGetPointerPenInfo = (GetPointerPenInfoFunc)GetProcAddress(hUser32, "GetPointerPenInfo");
        
        // Load WM_GESTURE API (Windows 7+)
        pGetGestureInfo = (GetGestureInfoFunc)GetProcAddress(hUser32, "GetGestureInfo");
    }
    
    // WM_POINTER requires Windows 8+ (GetPointerTouchInfo)
    g_touchAvailable = (pGetPointerTouchInfo != nullptr);
    
    // WM_GESTURE requires Windows 7+ (GetGestureInfo)
    g_gestureAvailable = (pGetGestureInfo != nullptr);
    
    if (g_touchAvailable && g_hwnd) {
        // Subclass window to intercept pointer messages
        g_origWndProc = (WNDPROC)SetWindowLongPtr(g_hwnd, GWLP_WNDPROC, (LONG_PTR)TouchWndProc);
        g_initialized = true;
        fprintf(stderr, "[FastTouch] WM_POINTER registered for window %p\n", g_hwnd);
        
        if (g_gestureAvailable) {
            fprintf(stderr, "[FastTouch] WM_GESTURE available (Pinch/Rotate support)\n");
        }
    } else {
        fprintf(stderr, "[FastTouch] WM_POINTER not available (Windows 8+ required)\n");
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
    const char* str = nullptr;
    if (title) str = env->GetStringUTFChars(title, nullptr);
    HWND hwnd = FindWindowA(nullptr, str);
    if (title && str) env->ReleaseStringUTFChars(title, str);
    return (jlong)hwnd;
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
            fprintf(stderr, "[FastTouch] Stale touch %d auto-released\n", g_touchPoints[i].id);
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
    int count = g_touchCount;
    LeaveCriticalSection(&g_touchLock);
    return count;
}

/**
 * @brief Gets the touch ID at the specified index
 * @param index Touch point index (0 to MAX_TOUCH_POINTS-1)
 * @return Touch ID (0-9), or -1 if index invalid
 */
JNIEXPORT jint JNICALL Java_fasttouch_FastTouch_getTouchId(JNIEnv*, jclass, jint index) {
    if (index < 0 || index >= MAX_TOUCH_POINTS) return -1;
    return g_touchPoints[index].id;
}

/**
 * @brief Gets the X coordinate at the specified index
 * @param index Touch point index
 * @return X coordinate in pixels, or 0 if index invalid
 */
JNIEXPORT jint JNICALL Java_fasttouch_FastTouch_getTouchX(JNIEnv*, jclass, jint index) {
    if (index < 0 || index >= MAX_TOUCH_POINTS) return 0;
    return g_touchPoints[index].x;
}

/**
 * @brief Gets the Y coordinate at the specified index
 * @param index Touch point index
 * @return Y coordinate in pixels, or 0 if index invalid
 */
JNIEXPORT jint JNICALL Java_fasttouch_FastTouch_getTouchY(JNIEnv*, jclass, jint index) {
    if (index < 0 || index >= MAX_TOUCH_POINTS) return 0;
    return g_touchPoints[index].y;
}

/**
 * @brief Gets the pressure at the specified index
 * @param index Touch point index
 * @return Pressure 0-255, or 0 if index invalid
 */
JNIEXPORT jint JNICALL Java_fasttouch_FastTouch_getTouchPressure(JNIEnv*, jclass, jint index) {
    if (index < 0 || index >= MAX_TOUCH_POINTS) return 0;
    return g_touchPoints[index].pressure;
}

/**
 * @brief Gets the contact width at the specified index
 * @param index Touch point index
 * @return Width in pixels, or 0 if index invalid
 */
JNIEXPORT jint JNICALL Java_fasttouch_FastTouch_getTouchWidth(JNIEnv*, jclass, jint index) {
    if (index < 0 || index >= MAX_TOUCH_POINTS) return 0;
    return g_touchPoints[index].width;
}

/**
 * @brief Gets the contact height at the specified index
 * @param index Touch point index
 * @return Height in pixels, or 0 if index invalid
 */
JNIEXPORT jint JNICALL Java_fasttouch_FastTouch_getTouchHeight(JNIEnv*, jclass, jint index) {
    if (index < 0 || index >= MAX_TOUCH_POINTS) return 0;
    return g_touchPoints[index].height;
}

/**
 * @brief Gets the touch state at the specified index
 * @param index Touch point index
 * @return State (0=DOWN, 1=MOVE, 2=UP), or 2 (UP) if index invalid
 */
JNIEXPORT jint JNICALL Java_fasttouch_FastTouch_getTouchState(JNIEnv*, jclass, jint index) {
    if (index < 0 || index >= MAX_TOUCH_POINTS) return 2; // UP
    return g_touchPoints[index].state;
}

/**
 * @brief Gets the timestamp at the specified index
 * @param index Touch point index
 * @return Timestamp in milliseconds, or 0 if index invalid
 */
JNIEXPORT jlong JNICALL Java_fasttouch_FastTouch_getTouchTimestamp(JNIEnv*, jclass, jint index) {
    if (index < 0 || index >= MAX_TOUCH_POINTS) return 0;
    return g_touchPoints[index].timestamp;
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
        fprintf(stderr, "[FastTouch] Gesture recognition ENABLED\n");
    } else if (g_gestureEnabled && !g_gestureAvailable) {
        fprintf(stderr, "[FastTouch] WARNING: Gesture recognition requested but WM_GESTURE not available\n");
    } else {
        fprintf(stderr, "[FastTouch] Gesture recognition DISABLED\n");
    }
}

} // extern "C"

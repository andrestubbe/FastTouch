/**
 * FastTouch - Native Touchscreen Input for Java
 * Windows API Implementation (WM_POINTER - Windows 10/11)
 */

#include <jni.h>
#include <windows.h>
#include <stdio.h>

#pragma comment(lib, "user32.lib")

// WM_POINTER is available on Windows 8+
#ifndef WM_POINTERDOWN
#define WM_POINTERDOWN 0x0246
#define WM_POINTERUP 0x0247
#define WM_POINTERUPDATE 0x0245
#define GET_POINTERID_WPARAM(wParam) (LOWORD(wParam))
#endif

// Function pointers for Pointer API (Windows 8+)
typedef BOOL (WINAPI *GetPointerTouchInfoFunc)(UINT32 pointerId, POINTER_TOUCH_INFO* touchInfo);
typedef BOOL (WINAPI *GetPointerInfoFunc)(UINT32 pointerId, POINTER_INFO* pointerInfo);
typedef BOOL (WINAPI *GetPointerPenInfoFunc)(UINT32 pointerId, POINTER_PEN_INFO* penInfo);
typedef BOOL (WINAPI *GetPointerFrameTouchInfoFunc)(UINT32 pointerId, UINT32* pointerCount, POINTER_TOUCH_INFO* touchInfo);

static GetPointerTouchInfoFunc pGetPointerTouchInfo = nullptr;
static GetPointerInfoFunc pGetPointerInfo = nullptr;
static GetPointerPenInfoFunc pGetPointerPenInfo = nullptr;

// Global state
static HWND g_hwnd = nullptr;
static bool g_initialized = false;
static bool g_touchAvailable = false;

// Touch point storage (simple ring buffer)
#define MAX_TOUCH_POINTS 20

struct TouchPoint {
    int id;
    int x;
    int y;
    int pressure;
    int width;
    int height;
    long timestamp;
    int state; // 0=DOWN, 1=MOVE, 2=UP
    bool active;
};

static TouchPoint g_touchPoints[MAX_TOUCH_POINTS];
static int g_touchCount = 0;
static CRITICAL_SECTION g_touchLock;

// Window procedure hook
static WNDPROC g_origWndProc = nullptr;

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
                        fprintf(stderr, "[FastTouch] Pointer DOWN id=%d at (%d,%d)\n", 
                                pointerId, pt.x, pt.y);
                    } else {
                        g_touchPoints[slot].state = 1; // MOVE
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
    }
    
    if (g_origWndProc) {
        return CallWindowProc(g_origWndProc, hwnd, msg, wParam, lParam);
    }
    return DefWindowProc(hwnd, msg, wParam, lParam);
}

extern "C" {

JNIEXPORT void JNICALL Java_fasttouch_FastTouch_initNative(JNIEnv*, jclass, jlong hwnd) {
    g_hwnd = (HWND)hwnd;
    
    InitializeCriticalSection(&g_touchLock);
    
    // Load WM_POINTER API (Windows 8+)
    HMODULE hUser32 = GetModuleHandleA("user32.dll");
    if (hUser32) {
        pGetPointerTouchInfo = (GetPointerTouchInfoFunc)GetProcAddress(hUser32, "GetPointerTouchInfo");
        pGetPointerInfo = (GetPointerInfoFunc)GetProcAddress(hUser32, "GetPointerInfo");
        pGetPointerPenInfo = (GetPointerPenInfoFunc)GetProcAddress(hUser32, "GetPointerPenInfo");
    }
    
    // WM_POINTER requires Windows 8+ (GetPointerTouchInfo)
    g_touchAvailable = (pGetPointerTouchInfo != nullptr);
    
    if (g_touchAvailable && g_hwnd) {
        // Subclass window to intercept pointer messages
        g_origWndProc = (WNDPROC)SetWindowLongPtr(g_hwnd, GWLP_WNDPROC, (LONG_PTR)TouchWndProc);
        g_initialized = true;
        fprintf(stderr, "[FastTouch] WM_POINTER registered for window %p\n", g_hwnd);
    } else {
        fprintf(stderr, "[FastTouch] WM_POINTER not available (Windows 8+ required)\n");
    }
}

JNIEXPORT jlong JNICALL Java_fasttouch_FastTouch_findWindow(JNIEnv* env, jclass, jstring title) {
    const char* str = nullptr;
    if (title) str = env->GetStringUTFChars(title, nullptr);
    HWND hwnd = FindWindowA(nullptr, str);
    if (title && str) env->ReleaseStringUTFChars(title, str);
    return (jlong)hwnd;
}

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

JNIEXPORT jint JNICALL Java_fasttouch_FastTouch_getTouchCount(JNIEnv*, jclass) {
    EnterCriticalSection(&g_touchLock);
    int count = g_touchCount;
    LeaveCriticalSection(&g_touchLock);
    return count;
}

JNIEXPORT jint JNICALL Java_fasttouch_FastTouch_getTouchId(JNIEnv*, jclass, jint index) {
    if (index < 0 || index >= MAX_TOUCH_POINTS) return -1;
    return g_touchPoints[index].id;
}

JNIEXPORT jint JNICALL Java_fasttouch_FastTouch_getTouchX(JNIEnv*, jclass, jint index) {
    if (index < 0 || index >= MAX_TOUCH_POINTS) return 0;
    return g_touchPoints[index].x;
}

JNIEXPORT jint JNICALL Java_fasttouch_FastTouch_getTouchY(JNIEnv*, jclass, jint index) {
    if (index < 0 || index >= MAX_TOUCH_POINTS) return 0;
    return g_touchPoints[index].y;
}

JNIEXPORT jint JNICALL Java_fasttouch_FastTouch_getTouchPressure(JNIEnv*, jclass, jint index) {
    if (index < 0 || index >= MAX_TOUCH_POINTS) return 0;
    return g_touchPoints[index].pressure;
}

JNIEXPORT jint JNICALL Java_fasttouch_FastTouch_getTouchWidth(JNIEnv*, jclass, jint index) {
    if (index < 0 || index >= MAX_TOUCH_POINTS) return 0;
    return g_touchPoints[index].width;
}

JNIEXPORT jint JNICALL Java_fasttouch_FastTouch_getTouchHeight(JNIEnv*, jclass, jint index) {
    if (index < 0 || index >= MAX_TOUCH_POINTS) return 0;
    return g_touchPoints[index].height;
}

JNIEXPORT jint JNICALL Java_fasttouch_FastTouch_getTouchState(JNIEnv*, jclass, jint index) {
    if (index < 0 || index >= MAX_TOUCH_POINTS) return 2; // UP
    return g_touchPoints[index].state;
}

JNIEXPORT jlong JNICALL Java_fasttouch_FastTouch_getTouchTimestamp(JNIEnv*, jclass, jint index) {
    if (index < 0 || index >= MAX_TOUCH_POINTS) return 0;
    return g_touchPoints[index].timestamp;
}

JNIEXPORT jboolean JNICALL Java_fasttouch_FastTouch_isTouchAvailable(JNIEnv*, jclass) {
    return g_touchAvailable ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL Java_fasttouch_FastTouch_getMaxTouchPoints(JNIEnv*, jclass) {
    // Windows 7+ supports 256 touch points theoretically
    // Most hardware supports 5-10
    return 10;
}

} // extern "C"

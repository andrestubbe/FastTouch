/**
 * FastTouch - Native Touchscreen Input for Java
 * Windows API Implementation
 */

#include <jni.h>
#include <windows.h>
#include <stdio.h>

#pragma comment(lib, "user32.lib")

// Touch structures (in case older SDK)
#ifndef TOUCHEVENTF_MOVE
#define TOUCHEVENTF_MOVE 0x0001
#define TOUCHEVENTF_DOWN 0x0002
#define TOUCHEVENTF_UP 0x0004
#define TOUCHEVENTF_PRIMARY 0x0010

typedef struct _TOUCHINPUT {
    LONG x;
    LONG y;
    HANDLE hSource;
    DWORD dwID;
    DWORD dwFlags;
    DWORD dwMask;
    DWORD dwTime;
    ULONG_PTR dwExtraInfo;
    DWORD cxContact;
    DWORD cyContact;
} TOUCHINPUT, *PTOUCHINPUT;

#define TOUCHINPUTMASKF_CONTACTAREA 0x0004
#define TOUCHINPUTMASKF_PRESSURE 0x0008
#endif

// Function pointers for runtime loading
typedef BOOL (WINAPI *RegisterTouchWindowFunc)(HWND hwnd, ULONG ulFlags);
typedef BOOL (WINAPI *GetTouchInputInfoFunc)(HANDLE hTouchInput, UINT cInputs, PTOUCHINPUT pInputs, int cbSize);
typedef BOOL (WINAPI *CloseTouchInputHandleFunc)(HANDLE hTouchInput);

static RegisterTouchWindowFunc pRegisterTouchWindow = nullptr;
static GetTouchInputInfoFunc pGetTouchInputInfo = nullptr;
static CloseTouchInputHandleFunc pCloseTouchInputHandle = nullptr;

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
        case WM_TOUCH: {
            UINT cInputs = LOWORD(wParam);
            if (cInputs > 0 && pGetTouchInputInfo && pCloseTouchInputHandle) {
                PTOUCHINPUT pInputs = new TOUCHINPUT[cInputs];
                if (pGetTouchInputInfo((HANDLE)lParam, cInputs, pInputs, sizeof(TOUCHINPUT))) {
                    EnterCriticalSection(&g_touchLock);
                    
                    // Mark all existing touches as MOVE initially
                    for (int i = 0; i < MAX_TOUCH_POINTS; i++) {
                        if (g_touchPoints[i].active) {
                            g_touchPoints[i].state = 1; // MOVE
                        }
                    }
                    
                    // Process new touch events
                    for (UINT i = 0; i < cInputs && i < MAX_TOUCH_POINTS; i++) {
                        TOUCHINPUT& ti = pInputs[i];
                        
                        // Find or create touch slot
                        int slot = -1;
                        for (int j = 0; j < MAX_TOUCH_POINTS; j++) {
                            if (g_touchPoints[j].id == (int)ti.dwID && g_touchPoints[j].active) {
                                slot = j;
                                break;
                            }
                        }
                        if (slot == -1) {
                            for (int j = 0; j < MAX_TOUCH_POINTS; j++) {
                                if (!g_touchPoints[j].active) {
                                    slot = j;
                                    g_touchPoints[j].active = true;
                                    g_touchPoints[j].id = ti.dwID;
                                    break;
                                }
                            }
                        }
                        
                        if (slot != -1) {
                            // Convert to client coordinates
                            POINT pt;
                            pt.x = ti.x / 100; // TOUCHINPUT uses 100ths of pixels
                            pt.y = ti.y / 100;
                            ScreenToClient(g_hwnd, &pt);
                            
                            g_touchPoints[slot].x = pt.x;
                            g_touchPoints[slot].y = pt.y;
                            g_touchPoints[slot].timestamp = GetTickCount();
                            
                            // Contact size
                            if (ti.dwMask & TOUCHINPUTMASKF_CONTACTAREA) {
                                g_touchPoints[slot].width = ti.cxContact / 100;
                                g_touchPoints[slot].height = ti.cyContact / 100;
                            } else {
                                g_touchPoints[slot].width = 20;
                                g_touchPoints[slot].height = 20;
                            }
                            
                            // Pressure (simulated if not available)
                            if (ti.dwMask & TOUCHINPUTMASKF_PRESSURE) {
                                g_touchPoints[slot].pressure = 128; // Placeholder
                            } else {
                                g_touchPoints[slot].pressure = 128;
                            }
                            
                            // State
                            if (ti.dwFlags & TOUCHEVENTF_DOWN) {
                                g_touchPoints[slot].state = 0; // DOWN
                            } else if (ti.dwFlags & TOUCHEVENTF_UP) {
                                g_touchPoints[slot].state = 2; // UP
                                g_touchPoints[slot].active = false;
                            } else {
                                g_touchPoints[slot].state = 1; // MOVE
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
                    pCloseTouchInputHandle((HANDLE)lParam);
                }
                delete[] pInputs;
            }
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
    
    // Load touch functions dynamically
    HMODULE hUser32 = GetModuleHandleA("user32.dll");
    if (hUser32) {
        pRegisterTouchWindow = (RegisterTouchWindowFunc)GetProcAddress(hUser32, "RegisterTouchWindow");
        pGetTouchInputInfo = (GetTouchInputInfoFunc)GetProcAddress(hUser32, "GetTouchInputInfo");
        pCloseTouchInputHandle = (CloseTouchInputHandleFunc)GetProcAddress(hUser32, "CloseTouchInputHandle");
    }
    
    g_touchAvailable = (pRegisterTouchWindow != nullptr);
    
    if (g_touchAvailable && g_hwnd) {
        // Register window for touch
        if (pRegisterTouchWindow(g_hwnd, 0)) {
            // Subclass window to intercept touch messages
            g_origWndProc = (WNDPROC)SetWindowLongPtr(g_hwnd, GWLP_WNDPROC, (LONG_PTR)TouchWndProc);
            g_initialized = true;
            fprintf(stderr, "[FastTouch] Touch registered for window %p\n", g_hwnd);
        } else {
            fprintf(stderr, "[FastTouch] RegisterTouchWindow failed\n");
        }
    } else {
        fprintf(stderr, "[FastTouch] Touch not available (Windows 7+ required)\n");
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
    // Process Windows message queue
    MSG msg;
    while (PeekMessage(&msg, g_hwnd, WM_TOUCH, WM_TOUCH, PM_REMOVE)) {
        TranslateMessage(&msg);
        DispatchMessage(&msg);
    }
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

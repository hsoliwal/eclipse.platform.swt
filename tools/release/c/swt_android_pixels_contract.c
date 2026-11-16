/* SPDX-License-Identifier: EPL-2.0 */
/* Headless fake NDK bitmap interface; compiled with actual production swt_pixels.c. */
#include <jni.h>
#include <android/bitmap.h>
#include <inttypes.h>
#include <limits.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

typedef struct FakeBitmap {
    AndroidBitmapInfo info;
    uint8_t *bytes;
    size_t length;
    int getInfoFail;
    int lockFail;
    int nullPointerSuccess;
    int locks;
    int unlocks;
    int locked;
} FakeBitmap;

/* Separate declarations force linker to use the original SWT native owner. */
JNIEXPORT jboolean JNICALL Java_org_eclipse_swt_internal_android_AndroidPixels_moveNative(
        JNIEnv *env, jclass clazz, jobject bitmap,
        jint x, jint y, jint width, jint height, jint dx, jint dy);

int AndroidBitmap_getInfo(JNIEnv *env, jobject bitmap, AndroidBitmapInfo *out) {
    (void)env;
    FakeBitmap *b=(FakeBitmap *)bitmap;
    if (b == NULL || out == NULL || b->getInfoFail) return ANDROID_BITMAP_RESULT_BAD_PARAMETER;
    *out=b->info;
    return ANDROID_BITMAP_RESULT_SUCCESS;
}
int AndroidBitmap_lockPixels(JNIEnv *env, jobject bitmap, void **pixels) {
    (void)env;
    FakeBitmap *b=(FakeBitmap *)bitmap;
    if (b==NULL || pixels==NULL || b->lockFail) return ANDROID_BITMAP_RESULT_BAD_PARAMETER;
    b->locks++;
    b->locked=1;
    *pixels=b->nullPointerSuccess ? NULL : b->bytes;
    return ANDROID_BITMAP_RESULT_SUCCESS;
}
int AndroidBitmap_unlockPixels(JNIEnv *env, jobject bitmap) {
    (void)env;
    FakeBitmap *b=(FakeBitmap *)bitmap;
    if (b==NULL || !b->locked) return ANDROID_BITMAP_RESULT_BAD_PARAMETER;
    b->unlocks++;
    b->locked=0;
    return ANDROID_BITMAP_RESULT_SUCCESS;
}

static uint32_t state=0x20261116u;
static uint32_t nextRand(void) {
    state=state*1664525u+1013904223u;
    return state;
}
static void insist(int condition, const char *message) {
    if (!condition) {
        fprintf(stderr,"SWT_ANDROID_NDK_CONTRACT_FAILURE: %s\n",message);
        exit(EXIT_FAILURE);
    }
}
static FakeBitmap makeBitmap(int w,int h,int pad) {
    FakeBitmap b;
    memset(&b,0,sizeof(b));
    b.info.width=(uint32_t)w;
    b.info.height=(uint32_t)h;
    b.info.stride=(uint32_t)(4*w+pad);
    b.info.format=ANDROID_BITMAP_FORMAT_RGBA_8888;
    b.length=(size_t)b.info.stride*(size_t)h;
    b.bytes=(uint8_t*)malloc(b.length);
    insist(b.bytes!=NULL,"bitmap allocation");
    for (size_t i=0;i<b.length;i++) b.bytes[i]=(uint8_t)(nextRand()>>24);
    return b;
}
static void disposeBitmap(FakeBitmap *b){
    insist(!b->locked,"bitmap must be unlocked");
    free(b->bytes);
    b->bytes=NULL;
}
static unsigned long long casesPassed=0, pixelsCompared=0;
static void validMove(int w,int h,int pad,int x,int y,int sw,int sh,int dx,int dy) {
    FakeBitmap b=makeBitmap(w,h,pad);
    size_t total=b.length;
    uint8_t *before=(uint8_t*)malloc(total), *expected=(uint8_t*)malloc(total);
    insist(before!=NULL && expected!=NULL,"oracle allocation");
    memcpy(before,b.bytes,total);
    memcpy(expected,b.bytes,total);
    for (int row=0;row<sh;row++) {
        size_t src=(size_t)(y+row)*b.info.stride+(size_t)x*4u;
        size_t dest=(size_t)(y+dy+row)*b.info.stride+(size_t)(x+dx)*4u;
        memcpy(expected+dest,before+src,(size_t)sw*4u);
    }
    jboolean result=Java_org_eclipse_swt_internal_android_AndroidPixels_moveNative(NULL,NULL,(jobject)&b,x,y,sw,sh,dx,dy);
    insist(result==JNI_TRUE,"valid move must succeed");
    insist(b.locks==1 && b.unlocks==1 && !b.locked,"successful lock/unlock balanced");
    if (memcmp(expected,b.bytes,total)!=0) {
        for (size_t i=0;i<total;i++) if(expected[i]!=b.bytes[i]) {
            fprintf(stderr,"SWT_ANDROID_NDK_PIXEL_MISMATCH case=%llu byte=%zu x=%d y=%d sw=%d sh=%d dx=%d dy=%d\n",casesPassed,i,x,y,sw,sh,dx,dy);
            exit(EXIT_FAILURE);
        }
    }
    casesPassed++;
    pixelsCompared+=(unsigned long long)sw*(unsigned long long)sh;
    free(before);free(expected);disposeBitmap(&b);
}
static void invalidMove(int width,int height,int strideOverride,
                        int x,int y,int sw,int sh,int dx,int dy,
                        int failInfo,int failLock,int nullOnSuccess,int badFormat,
                        int expectedLocks,int expectedUnlocks) {
    FakeBitmap b=makeBitmap(width,height,7);
    if (strideOverride >= 0) b.info.stride=(uint32_t)strideOverride;
    b.getInfoFail=failInfo;
    b.lockFail=failLock;
    b.nullPointerSuccess=nullOnSuccess;
    if(badFormat)b.info.format=99;
    uint8_t *snapshot=(uint8_t*)malloc(b.length);
    insist(snapshot!=NULL,"invalid input snapshot");
    memcpy(snapshot,b.bytes,b.length);
    jboolean result=Java_org_eclipse_swt_internal_android_AndroidPixels_moveNative(NULL,NULL,(jobject)&b,x,y,sw,sh,dx,dy);
    insist(result==JNI_FALSE,"invalid bitmap move must refuse");
    insist(memcmp(snapshot,b.bytes,b.length)==0,"invalid path must not mutate pixels");
    insist(b.locks==expectedLocks && b.unlocks==expectedUnlocks && !b.locked,
           "invalid path must release successful bitmap lock");
    casesPassed++;
    free(snapshot);disposeBitmap(&b);
}
int main(void) {
    validMove(100008,7,16,3,1,90000,4,7,1);
    validMove(100008,7,0,10,2,90000,4,-8,-1);
    validMove(100008,7,32,2,1,90000,4,9,0);
    validMove(100008,7,0,12,1,90000,4,-11,0);
    validMove(150025,5,7,10,0,130000,4,2,1);
    validMove(150025,5,5,10,1,130000,3,-4,-1);
    validMove(65550,4,0,3,0,65537,3,5,1);
    validMove(128,128,0,0,0,128,128,0,0);
    for(int i=0;i<6000;i++) {
        int w=16+(int)(nextRand()%128),h=8+(int)(nextRand()%96);
        int sw=1+(int)(nextRand()%(uint32_t)w),sh=1+(int)(nextRand()%(uint32_t)h);
        int x=(int)(nextRand()%(uint32_t)(w-sw+1)),y=(int)(nextRand()%(uint32_t)(h-sh+1));
        int destX=(int)(nextRand()%(uint32_t)(w-sw+1)),destY=(int)(nextRand()%(uint32_t)(h-sh+1));
        validMove(w,h,(int)(nextRand()%29),x,y,sw,sh,destX-x,destY-y);
    }
    invalidMove(32,24,-1,-1,1,5,5,0,0,0,0,0,0,0,0);
    invalidMove(32,24,-1,0,-1,5,5,0,0,0,0,0,0,0,0);
    invalidMove(32,24,-1,0,0,0,5,0,0,0,0,0,0,0,0);
    invalidMove(32,24,-1,0,0,5,0,0,0,0,0,0,0,0,0);
    invalidMove(32,24,-1,28,0,5,5,0,0,0,0,0,0,0,0);
    invalidMove(32,24,-1,0,20,5,5,0,0,0,0,0,0,0,0);
    invalidMove(32,24,-1,0,0,5,5,-1,0,0,0,0,0,0,0);
    invalidMove(32,24,-1,0,0,5,5,0,-1,0,0,0,0,0,0);
    invalidMove(32,24,-1,0,0,5,5,30,0,0,0,0,0,0,0);
    invalidMove(32,24,-1,0,0,5,5,0,22,0,0,0,0,0,0);
    invalidMove(32,24,-1,0,0,5,5,0,0,1,0,0,0,0,0);
    invalidMove(32,24,-1,0,0,5,5,0,0,0,1,0,0,0,0);
    invalidMove(32,24,-1,0,0,5,5,0,0,0,0,0,1,0,0);
    /* A successful lock reporting no pixel pointer must be balanced before refusal. */
    invalidMove(32,24,-1,0,0,5,5,0,0,0,0,1,0,1,1);
    /* A stride narrower than the advertised RGBA bitmap width is rejected before locking. */
    invalidMove(32,24,4*32-1,0,0,5,5,0,0,0,0,0,0,0,0);
    printf("SWT_ANDROID_NDK_PIXEL_MOVES=%llu PIXELS_CHECKED=%llu PASS\n",casesPassed,pixelsCompared);
    return EXIT_SUCCESS;
}

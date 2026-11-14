/* SPDX-License-Identifier: EPL-2.0 */
#import <UIKit/UIKit.h>
#import <Foundation/Foundation.h>
#import <dispatch/dispatch.h>
#import <jni.h>
#import <stdint.h>
#import <stdlib.h>

/* Native event values must match MobileWidgetRuntime.EventType ordinal order. */
enum { SWT_SELECT = 0, SWT_MODIFY = 1, SWT_TOUCH_DOWN = 2,
       SWT_TOUCH_MOVE = 3, SWT_TOUCH_UP = 4, SWT_FOCUS_IN = 5,
       SWT_FOCUS_OUT = 6, SWT_SCROLL = 7 };

@class SWTMobileContext;
@interface SWTMobileAction : NSObject <UIScrollViewDelegate>
@property(nonatomic, weak) SWTMobileContext *host;
@property(nonatomic) jlong handle;
- (void)tapped:(id)sender;
- (void)changed:(UITextField *)sender;
- (void)focused:(UITextField *)sender;
- (void)blurred:(UITextField *)sender;
@end

@interface SWTMobileNode : NSObject
@property(nonatomic, strong) UIView *view;
@property(nonatomic, strong) UIView *container;
@property(nonatomic, strong) SWTMobileAction *actions;
@end

@interface SWTMobileContext : NSObject
@property(nonatomic) JavaVM *vm;
@property(nonatomic) jobject javaHost;
@property(nonatomic, weak) UIView *root;
@property(nonatomic, strong) NSMutableDictionary<NSNumber *, SWTMobileNode *> *nodes;
- (void)send:(jlong)handle kind:(jint)kind text:(NSString *)text x:(float)x y:(float)y;
@end

@implementation SWTMobileNode
@end
@implementation SWTMobileContext
- (void)send:(jlong)handle kind:(jint)kind text:(NSString *)text x:(float)x y:(float)y {
    if (!self.javaHost) return;
    JNIEnv *env = NULL;
    jint status = (*self.vm)->GetEnv(self.vm, (void **)&env, JNI_VERSION_1_6);
    BOOL detach = NO;
    if (status == JNI_EDETACHED) {
        if ((*self.vm)->AttachCurrentThread(self.vm, (void **)&env, NULL) != JNI_OK) return;
        detach = YES;
    } else if (status != JNI_OK) return;
    jclass klass = (*env)->GetObjectClass(env, self.javaHost);
    jmethodID method = (*env)->GetMethodID(env, klass, "onNativeEvent", "(JILjava/lang/String;FF)V");
    jstring nativeText = NULL;
    if (text) {
        NSUInteger length = text.length;
        unichar *chars = malloc(MAX(length, 1) * sizeof(unichar));
        if (chars) {
            [text getCharacters:chars range:NSMakeRange(0, length)];
            nativeText = (*env)->NewString(env, (const jchar *)chars, (jsize)length);
            free(chars);
        }
    }
    if (method) (*env)->CallVoidMethod(env, self.javaHost, method, handle, kind, nativeText, x, y);
    if (nativeText) (*env)->DeleteLocalRef(env, nativeText);
    (*env)->DeleteLocalRef(env, klass);
    if (detach) (*self.vm)->DetachCurrentThread(self.vm);
}
@end
@implementation SWTMobileAction
- (void)tapped:(id)sender { [self.host send:self.handle kind:SWT_SELECT text:nil x:0 y:0]; }
- (void)changed:(UITextField *)sender {
    [self.host send:self.handle kind:SWT_MODIFY text:sender.text x:0 y:0];
}
- (void)focused:(UITextField *)sender { [self.host send:self.handle kind:SWT_FOCUS_IN text:nil x:0 y:0]; }
- (void)blurred:(UITextField *)sender { [self.host send:self.handle kind:SWT_FOCUS_OUT text:nil x:0 y:0]; }
- (void)scrollViewDidScroll:(UIScrollView *)scrollView {
    CGPoint p = scrollView.contentOffset;
    [self.host send:self.handle kind:SWT_SCROLL text:nil x:p.x y:p.y];
}
@end

static void swtThrow(JNIEnv *env, const char *message) {
    jclass type = (*env)->FindClass(env, "java/lang/IllegalStateException");
    if (type) { (*env)->ThrowNew(env, type, message); (*env)->DeleteLocalRef(env, type); }
}
static SWTMobileContext *swtContext(JNIEnv *env, jlong pointer) {
    if (!pointer || ![NSThread isMainThread]) {
        swtThrow(env, "UIKit context missing or called off main thread"); return nil;
    }
    return (__bridge SWTMobileContext *)(void *)(uintptr_t)pointer;
}
static SWTMobileNode *swtNode(JNIEnv *env, SWTMobileContext *host, jlong id) {
    SWTMobileNode *node = host.nodes[@(id)];
    if (!node) swtThrow(env, "unknown UIKit widget handle");
    return node;
}
static NSString *swtString(JNIEnv *env, jstring string) {
    if (!string) return @"";
    jsize len = (*env)->GetStringLength(env, string);
    const jchar *chars = (*env)->GetStringChars(env, string, NULL);
    if (!chars) return nil;
    NSString *result = [[NSString alloc] initWithCharacters:(const unichar *)chars length:len];
    (*env)->ReleaseStringChars(env, string, chars);
    return result;
}

JNIEXPORT jboolean JNICALL Java_org_eclipse_swt_internal_mobile_ios_IosMobileHost_nIsMainThread
  (JNIEnv *env, jclass klass) { return [NSThread isMainThread] ? JNI_TRUE : JNI_FALSE; }

JNIEXPORT jlong JNICALL Java_org_eclipse_swt_internal_mobile_ios_IosMobileHost_nAttach
  (JNIEnv *env, jobject self, jlong rootPointer) {
    if (![NSThread isMainThread] || !rootPointer) { swtThrow(env, "UIKit root requires main thread"); return 0; }
    UIView *view = (__bridge UIView *)(void *)(uintptr_t)rootPointer;
    SWTMobileContext *host = [SWTMobileContext new];
    JavaVM *vm = NULL;
    if ((*env)->GetJavaVM(env, &vm) != JNI_OK) { swtThrow(env, "missing JavaVM"); return 0; }
    host.vm = vm;
    host.javaHost = (*env)->NewGlobalRef(env, self);
    host.root = view;
    host.nodes = [NSMutableDictionary dictionary];
    return (jlong)(uintptr_t)CFBridgingRetain(host);
}

JNIEXPORT void JNICALL Java_org_eclipse_swt_internal_mobile_ios_IosMobileHost_nPost
  (JNIEnv *env, jobject self, jobject task) {
    JavaVM *vm = NULL;
    if ((*env)->GetJavaVM(env, &vm) != JNI_OK) { swtThrow(env, "missing JavaVM"); return; }
    jobject retained = (*env)->NewGlobalRef(env, task);
    dispatch_async(dispatch_get_main_queue(), ^{
        JNIEnv *e = NULL;
        jint status = (*vm)->GetEnv(vm, (void **)&e, JNI_VERSION_1_6);
        BOOL detach = NO;
        if (status == JNI_EDETACHED) {
            if ((*vm)->AttachCurrentThread(vm, (void **)&e, NULL) != JNI_OK) return;
            detach = YES;
        } else if (status != JNI_OK) return;
        jclass type = (*e)->GetObjectClass(e, retained);
        jmethodID run = (*e)->GetMethodID(e, type, "run", "()V");
        if (run) (*e)->CallVoidMethod(e, retained, run);
        (*e)->DeleteLocalRef(e, type);
        (*e)->DeleteGlobalRef(e, retained);
        if (detach) (*vm)->DetachCurrentThread(vm);
    });
}

JNIEXPORT void JNICALL Java_org_eclipse_swt_internal_mobile_ios_IosMobileHost_nCreate
  (JNIEnv *env, jobject self, jlong pointer, jlong id, jlong parent, jint kind) {
    SWTMobileContext *host = swtContext(env, pointer); if (!host) return;
    if (host.nodes[@(id)]) { swtThrow(env, "duplicate UIKit widget handle"); return; }
    SWTMobileNode *parentNode = parent == 0 ? nil : swtNode(env, host, parent);
    UIView *container = parent == 0 ? host.root : parentNode.container;
    if (!container || (*env)->ExceptionCheck(env)) { swtThrow(env, "missing UIKit parent"); return; }
    SWTMobileNode *node = [SWTMobileNode new];
    SWTMobileAction *actions = [SWTMobileAction new];
    actions.host = host;
    actions.handle = id;
    UIView *view;
    switch (kind) {
        case 0: case 1: case 5: view = [UIView new]; break; /* shell/composite/canvas */
        case 2: view = [UILabel new]; break;
        case 3: {
            UIButton *button = [UIButton buttonWithType:UIButtonTypeSystem];
            [button addTarget:actions action:@selector(tapped:) forControlEvents:UIControlEventTouchUpInside];
            view = button; break;
        }
        case 4: {
            UITextField *field = [UITextField new];
            field.borderStyle = UITextBorderStyleRoundedRect;
            [field addTarget:actions action:@selector(changed:) forControlEvents:UIControlEventEditingChanged];
            [field addTarget:actions action:@selector(focused:) forControlEvents:UIControlEventEditingDidBegin];
            [field addTarget:actions action:@selector(blurred:) forControlEvents:UIControlEventEditingDidEnd];
            view = field; break;
        }
        case 6: case 7: case 8: { /* scroll, table, tree viewport containers */
            UIScrollView *scroll = [UIScrollView new];
            scroll.delegate = actions;
            UIView *content = [UIView new];
            [scroll addSubview:content];
            node.container = content;
            view = scroll; break;
        }
        default: swtThrow(env, "unsupported UIKit widget type"); return;
    }
    node.view = view;
    if (!node.container) node.container = view;
    node.actions = actions;
    [container addSubview:view];
    host.nodes[@(id)] = node;
}

JNIEXPORT void JNICALL Java_org_eclipse_swt_internal_mobile_ios_IosMobileHost_nSetBounds
  (JNIEnv *env, jobject self, jlong pointer, jlong id, jint x, jint y, jint w, jint h) {
    SWTMobileContext *host = swtContext(env, pointer); if (!host) return;
    SWTMobileNode *node = swtNode(env, host, id); if (!node) return;
    node.view.frame = CGRectMake(x, y, w, h);
    UIView *parent = node.view.superview;
    if (parent && [parent.superview isKindOfClass:[UIScrollView class]]) {
        UIScrollView *scroll = (UIScrollView *)parent.superview;
        scroll.contentSize = CGSizeMake(MAX(scroll.contentSize.width, x+w), MAX(scroll.contentSize.height, y+h));
        parent.frame = CGRectMake(0, 0, scroll.contentSize.width, scroll.contentSize.height);
    }
}
JNIEXPORT void JNICALL Java_org_eclipse_swt_internal_mobile_ios_IosMobileHost_nSetText
  (JNIEnv *env, jobject self, jlong pointer, jlong id, jstring value) {
    SWTMobileContext *host = swtContext(env, pointer); if (!host) return;
    SWTMobileNode *node = swtNode(env, host, id); if (!node) return;
    UIView *view = node.view;
    NSString *string = swtString(env, value);
    if ([view isKindOfClass:[UILabel class]]) ((UILabel *)view).text = string;
    else if ([view isKindOfClass:[UITextField class]]) ((UITextField *)view).text = string;
    else if ([view isKindOfClass:[UIButton class]]) [(UIButton *)view setTitle:string forState:UIControlStateNormal];
}
JNIEXPORT void JNICALL Java_org_eclipse_swt_internal_mobile_ios_IosMobileHost_nSetEnabled
  (JNIEnv *env, jobject self, jlong pointer, jlong id, jboolean value) {
    SWTMobileContext *host = swtContext(env, pointer); if (!host) return;
    SWTMobileNode *node = swtNode(env, host, id); if (!node) return;
    UIView *view = node.view;
    if ([view isKindOfClass:[UIControl class]]) ((UIControl *)view).enabled = value;
    else view.userInteractionEnabled = value;
}
JNIEXPORT void JNICALL Java_org_eclipse_swt_internal_mobile_ios_IosMobileHost_nSetVisible
  (JNIEnv *env, jobject self, jlong pointer, jlong id, jboolean value) {
    SWTMobileContext *host = swtContext(env, pointer); if (!host) return;
    SWTMobileNode *node = swtNode(env, host, id); if (!node) return;
    node.view.hidden = !value;
}
JNIEXPORT void JNICALL Java_org_eclipse_swt_internal_mobile_ios_IosMobileHost_nSetAccessibilityLabel
  (JNIEnv *env, jobject self, jlong pointer, jlong id, jstring label) {
    SWTMobileContext *host = swtContext(env, pointer); if (!host) return;
    SWTMobileNode *node = swtNode(env, host, id); if (!node) return;
    UIView *view = node.view;
    view.isAccessibilityElement = YES;
    view.accessibilityLabel = swtString(env, label);
}
JNIEXPORT void JNICALL Java_org_eclipse_swt_internal_mobile_ios_IosMobileHost_nRequestKeyboard
  (JNIEnv *env, jobject self, jlong pointer, jlong id, jboolean visible) {
    SWTMobileContext *host = swtContext(env, pointer); if (!host) return;
    SWTMobileNode *node = swtNode(env, host, id); if (!node) return;
    UIView *view = node.view;
    if (![view isKindOfClass:[UITextField class]]) { swtThrow(env, "IME requires text field"); return; }
    if (visible) [view becomeFirstResponder]; else [view resignFirstResponder];
}
JNIEXPORT void JNICALL Java_org_eclipse_swt_internal_mobile_ios_IosMobileHost_nRemove
  (JNIEnv *env, jobject self, jlong pointer, jlong id) {
    SWTMobileContext *host = swtContext(env, pointer); if (!host) return;
    SWTMobileNode *node = host.nodes[@(id)];
    if (node) { [node.view removeFromSuperview]; [host.nodes removeObjectForKey:@(id)]; }
}
JNIEXPORT void JNICALL Java_org_eclipse_swt_internal_mobile_ios_IosMobileHost_nClose
  (JNIEnv *env, jobject self, jlong pointer) {
    SWTMobileContext *host = swtContext(env, pointer); if (!host) return;
    for (SWTMobileNode *node in host.nodes.allValues) [node.view removeFromSuperview];
    [host.nodes removeAllObjects];
    if (host.javaHost) { (*env)->DeleteGlobalRef(env, host.javaHost); host.javaHost = NULL; }
    CFBridgingRelease((void *)(uintptr_t)pointer);
}

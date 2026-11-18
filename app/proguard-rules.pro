# ==========================================
# 基础属性与优化
# ==========================================
# 保留泛型、内部类、注解等信息（ViewBinding 和 ViewModel 需要）
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,SourceFile,LineNumberTable
# 允许 R8 修改访问修饰符，极大提升优化效果
-allowaccessmodification

# ==========================================
# Android 基础组件（必须保留，系统反射调用）
# ==========================================
-keep public class * extends android.app.Activity {
    public <init>();
}
-keep public class * extends android.app.Application {
    public <init>();
}
-keep public class * extends android.app.Service {
    public <init>();
}
-keep public class * extends android.content.BroadcastReceiver {
    public <init>();
}
-keep public class * extends android.content.ContentProvider {
    public <init>();
}

# View 的构造函数，但允许混淆类名和优化代码
-keepclasseswithmembers,allowoptimization,allowobfuscation class * extends android.view.View {
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keepclasseswithmembers,allowoptimization,allowobfuscation class * extends android.view.View {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# ==========================================
# 项目特定组件 (ViewBinding & ViewModel)
# ==========================================
# Binding 类通过反射调用 inflate，必须保留方法名，但允许混淆类名
-keep,allowoptimization,allowobfuscation class com.cleanphone.freespace.utility.databinding.**
-keepclassmembers,allowoptimization class com.cleanphone.freespace.utility.databinding.** {
    public static *** inflate(android.view.LayoutInflater);
    public static *** inflate(android.view.LayoutInflater, android.view.ViewGroup, boolean);
}
# ViewModel 由 ViewModelProvider 反射实例化，需保留类名和构造函数
-keep,allowoptimization,allowobfuscation class com.cleanphone.freespace.utility.viewmodel.** extends androidx.lifecycle.ViewModel {
    public <init>();
}

# ==========================================
# 标准运行时契约（JNI、序列化、枚举等）
# ==========================================
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ==========================================
# 第三方库反射规则（精简保留）
# ==========================================
# EventBus 注解反射
-keepclassmembers,allowoptimization class * {
    @org.greenrobot.eventbus.Subscribe <methods>;
}

# Gson 反射（保留 Bean 包，建议后续可优化为只保留 @SerializedName 字段）
-keep,allowoptimization,allowobfuscation class com.cleanphone.freespace.utility.bean.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# 显式 @Keep 注解
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep <fields>;
    @androidx.annotation.Keep <methods>;
    @androidx.annotation.Keep <init>(...);
}

# 防重打包与 Lottie
-keep class com.cleanphone.freespace.utility.utils.SafeChecker {*;}
-keep class com.airbnb.lottie.** { *; } # Lottie 如果仍然崩溃，可以保留；否则建议也删掉

# ThinkingData (数数科技) - 建议查阅其官方最新文档获取精准规则
# 如果删掉后统计不上报，请按官方文档添加特定的类，而不是全包保留
-keep class cn.thinkingdata.android.** { *; }
-keep class com.thinkingdata.analytics.** { *; }

# ==========================================
# 移除 Release 包的日志
# ==========================================
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# ==========================================
# 警告抑制 (Dontwarn) - 只抑制警告，不影响混淆率
# ==========================================
-dontwarn android.webkit.**
-dontwarn org.apache.http.**
-dontwarn android.net.http.**
-dontwarn com.google.android.gms.**
-dontwarn com.google.api.client.**
-dontwarn com.google.ads.**
-dontwarn org.w3c.dom.**
-dontwarn javax.annotation.**
-dontwarn java.lang.invoke.**

# Firebase (删除全量 keep，依靠 AAR 自带规则)
-dontwarn com.google.firebase.**
-dontwarn com.crashlytics.**
-dontwarn com.google.firebase.crashlytics.**

# ==========================================
# 广告 SDK (全部删除全量 keep，仅保留 dontwarn)
# 现代广告 SDK 的 AAR 均自带 consumer-rules.pro
# ==========================================
-dontwarn com.unity3d.ads.**
-dontwarn com.ironsource.**
-dontwarn com.vungle.**
-dontwarn com.chartboost.**
-dontwarn com.fyber.**
-dontwarn com.mbridge.**
-dontwarn com.bytedance.**
-dontwarn com.inmobi.**
-dontwarn com.mintegral.**
-dontwarn com.applovin.**
-dontwarn com.google.android.ump.**

# Singular SDK (精简保留，如果追踪失效请查官方文档)
-keep class com.singular.sdk.** { *; }
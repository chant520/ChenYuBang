# release 混淆规则：Paddle-Lite JNI 按 Java 类/方法签名解析 native 符号，禁止重命名。
-keepclasseswithmembernames,includedescriptorclasses class com.baidu.paddle.lite.** {
    native <methods>;
}
-keep class com.baidu.paddle.lite.Tensor { public *; }
-keep class com.baidu.paddle.lite.PaddlePredictor { public *; }

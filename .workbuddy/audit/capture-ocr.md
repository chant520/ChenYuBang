# 拍照链与 OCR 引擎只读审计

范围：`app-android/feature-capture`（32 kt）、`app-android/ocr`（11 kt + jniLibs/assets）；只读，未改源码。硬口径：全链路零联网、P19 可编辑兜底、18/20 代理注记不写达标。

## 高

1. 定位：`app-android/feature-capture/src/main/kotlin/com/chenyubang/feature/capture/CropImage.kt:76-82`
   问题：`createScaledBitmap(bitmap, 360, height, false)` 在源宽恰为 360 时按 Android API 契约返回源对象本身，第 82 行 `scaled.recycle()` 随即回收源 bitmap。
   影响：P18 预览 `drawCropBitmap` 与导出 `CropImage.export` 使用已回收位图，抛 `Canvas: trying to use a recycled bitmap`，拍照链路崩溃；`CropImageTest` 只用 1.PNG，未覆盖该宽度。
   建议：改为 `if (scaled !== bitmap) scaled.recycle()`；补 360px 宽源图的 `suggest` + `export` 回归测试。

2. 定位：`app-android/ocr/src/main/kotlin/com/chenyubang/ocr/PaddleOcrEngine.kt:31-43`；`app-android/feature-capture/src/main/kotlin/com/chenyubang/feature/capture/CaptureOcrScreen.kt:112-127`
   问题：`runCatching{...}.getOrNull().orEmpty()` 吞掉模型加载/JNI/IO 全部异常，仅 `Log.e`；UI 的 `recognize` 无 try/finally，异常时 `scanning` 停留在 true。
   影响：模型损坏、so 加载失败、图片不可读均呈现“没识别出文字”，或卡在“正在识别…”；失败态不可诊断。
   建议：`OcrResult` 增加失败原因（或 sealed 结果）；`recognize` 用 try/catch/finally 复位 scanning 并把原因映射到 P19 文案；release 移除含堆栈的 `Log.e`。

3. 定位：`app-android/ocr/src/main/kotlin/com/chenyubang/ocr/OcrBitmapOps.kt:11-25,38-41,45-56`；`app-android/ocr/src/main/kotlin/com/chenyubang/ocr/PaddleOcrEngine.kt:46-51,86-102,104-112`
   问题：decode→upscale→det scaled→逐行 crop 全程无 recycle；4096² 解码约 64MiB 与 4000² 上采样约 61MiB 同时存活，det FloatArray 约 11MB，逐行 crop 继续累积。
   影响：真机 P50 1616ms / P90 1897ms 下峰值常超 150MB，低端机 OOM；重拍/重新识别次数越多越差。
   建议：`runPipeline` 用 try/finally 回收上采样图与全部临时 scaled/crop 位图；`detect`/`runModel` 内临时位图用完即回收；P19 缩略图另走低采样路径。

4. 定位：`app-android/ocr/src/main/kotlin/com/chenyubang/ocr/OcrModelStore.kt:19-39`
   问题：解压直接写最终 `det.nb/rec.nb/keys.txt`，仅以 `exists && length>0` 判有效；无临时文件+原子 rename，无长度/魔数 `v2.10`/hash 校验；版本目录只靠 `MODEL_DIR` 常量人工同步。
   影响：写入中断留下半截模型被当作有效，native 加载 Fatal 或被第 2 条吞成“无文字”；替换 .nb 资产忘改常量会静默沿用旧模型。
   建议：先写 `.tmp` 再原子 rename；校验预期字节数、文件头 `v2.10`、sha256，不符即删重解压；版本目录由模型版本元数据生成。

5. 定位：`shared/fixtures/ocr/results.json:1-...`；`tools/ocr_passrate.py:42-65`
   问题：未跟踪的 results.json 跑脚本得 20/20=100%、P50 1616ms，但无引擎/设备/日期/来源字段；`docs/开发规范-v1.10.md:862` 明写真机仅单张 `1.PNG`，`AGENTS.md:20` 要求保留 18/20 代理注记。
   影响：100% 数字易被当成真机达标回填验收第 9 条，违反硬约定；结果不可追溯。
   建议：移入 `.workbuddy/evidence/` 并补 `engine/device/date/source`；验收继续写“代理 18/20，真机待复测”；脚本对无 provenance 输入打印“中间产物，非验收数”。

6. 定位：`app-android/ocr/src/main/java/com/baidu/paddle/lite/Tensor.java:56-63`；`app-android/ocr/src/main/kotlin/com/chenyubang/ocr/PaddleOcrEngine.kt:92-96,133-139`
   问题：每轮 det/rec 都重新 `getInput(0)/getOutput(0)` 创建 Tensor 包装且不持有引用，包装 `finalize()` 调 `deleteCppTensor`，native 指针生命周期与 predictor 脱钩；`PaddlePredictor.java:104-107` 同样只靠 finalize 释放。
   影响：GC 时机不可控，存在悬垂/双重释放导致 native 崩溃；连续识别后 native 内存不回落。
   建议：Session 内缓存并复用 input/output Tensor；为 Java 包装增加显式 release，引擎替换/销毁 Session 时显式释放 predictor。

## 中

1. 定位：`app-android/ocr/src/main/kotlin/com/chenyubang/ocr/OcrLineGrouper.kt:9-28`；`app-android/ocr/src/main/kotlin/com/chenyubang/ocr/OcrLineClassifier.kt:20-34`
   问题：双列 A/B 垂直错位（重叠 <50%）时不并为一行，B 行因无选项标记被并入 A；classifier 只看字符串，拿不到 x 列信息纠正。
   影响：双栏题丢 B/C 选项，P19 需手动补；现有测试只覆盖完全同高。
   建议：按 y 行 + x 列联合聚类，或把 box 坐标传给 classifier；补带 y 偏移的双列回归测试。

2. 定位：`app-android/ocr/build.gradle.kts:26`；`app-android/ocr/src/main/jniLibs/arm64-v8a/`；`app-android/ocr/src/main/java/com/baidu/paddle/lite/PaddleLiteInitializer.java:19-21`
   问题：仅打包 arm64-v8a 的 so；32 位 ARM 设备 `System.loadLibrary` 抛 `UnsatisfiedLinkError`，被高危第 2 条吞成“没识别出文字”。
   影响：minSdk 26 的 32 位真机无法识别且无任何明确提示。
   建议：补 armeabi-v7a 库，或显式限制 ABI 并在加载失败时给出“设备不支持端侧识别，请手动补字”的确定文案。

3. 定位：`app-android/feature-capture/src/main/kotlin/com/chenyubang/feature/capture/CropImage.kt:116-149`
   问题：EXIF 旋转分支 `Bitmap.createBitmap(..., matrix, true)` 生成新图后原 bitmap 未回收（identity 分支除外）。
   影响：旋转过的照片峰值内存翻倍，与高危第 3 条叠加触发 OOM。
   建议：非 identity 分支在返回前回收原 bitmap。

4. 定位：`app-android/feature-capture/src/main/kotlin/com/chenyubang/feature/capture/CaptureImageStore.kt:22-37`
   问题：`importFromUri` 无文件大小上限，只 catch `IOException`，`SecurityException`/`IllegalStateException` 逃逸；cacheDir 文件在取消时无清理。
   影响：超大图拖慢解码并占满缓存；URI 权限失效时崩溃；“取消即整条丢弃、不留草稿”落空。
   建议：限制源大小并抽样解码；补 SecurityException 兜底；链路 cancel/retake 时删除本次 cache/capture 文件。

5. 定位：`shared/fixtures/ocr/样张登记表.csv:1-21`
   问题：20 行来源/光线/角度/版式/干扰/分辨率全部为空，与 `docs/OCR样张实测方案.md:32` 要求及 `docs/OCR实测记录.md:70` 自述一致但一直未补。
   影响：无法证明样张覆盖偏暗、倾斜、双栏等维度，通过率不能按维度解释。
   建议：回填五维度与分辨率，并与 results.json 的 id 对齐后重跑 `tools/ocr_passrate.py`。

6. 定位：`app-android/feature-capture/src/main/kotlin/com/chenyubang/feature/capture/P20FormScreen.kt:107-119`；`app-android/feature-capture/src/main/kotlin/com/chenyubang/feature/capture/P20SaveBridge.kt:39-56`
   问题：`current = model.state.value` 是点击时快照，`saving` 期间表单未禁用，`buildInput` 用旧快照。
   影响：加载中改答案/归属，落库与界面显示不一致。
   建议：`performSave` 进入时重读 state，或 saving=true 时整体禁用表单输入。

7. 定位：`app-android/ocr/src/main/kotlin/com/chenyubang/ocr/OcrLineClassifier.kt:36-41`
   问题：无选项标记且行数 >5 时无条件把末四行当 A–D，长题干折行也被吞成选项。
   影响：P19 题干缺行、选项错位；虽有可编辑兜底，但用户不知道哪几行是推测值。
   建议：仅当末四行短且无句末标点时回填，并在 P19 标注“推测选项，请复核”。

8. 定位：`app-android/feature-capture/src/main/kotlin/com/chenyubang/feature/capture/CaptureCropScreen.kt:49-53`
   问题：`LaunchedEffect` 内 `CropImage.load` 未捕获 OOM/异常，`state.failed` 仅在返回 null 时置位。
   影响：坏图或大图直接崩溃，不走“图片读取失败，请返回重拍”退路。
   建议：`runCatching { withContext(IO){ CropImage.load(...) } }`，失败统一喂给 `applyLoaded(null)`。

## 低

1. 定位：`app-android/feature-capture/src/main/kotlin/com/chenyubang/feature/capture/CaptureOcrScreen.kt:137-151`
   问题：`OcrEditSaver` 未保存 `scanning`；进程恢复后 scanning=false 且 loaded=false，LaunchedEffect 再跑一次识别并覆盖已保存文本。
   建议：把 scanning 纳入 saver，或恢复时依据 loaded/failed 决定是否重跑。

2. 定位：`app-android/feature-capture/src/main/kotlin/com/chenyubang/feature/capture/P20MatchViewModel.kt:86,92`；`app-android/feature-capture/src/main/kotlin/com/chenyubang/feature/capture/P20FormModels.kt:77`
   问题：`EMPTY_STEM`、`NO_IMAGE` 常量已定义但未接入保存校验，`canSave` 不校验题干。
   建议：若手动兜底允许空题干则删死常量，否则接入 `saveAction` 校验。

3. 定位：`app-android/feature-capture/src/main/kotlin/com/chenyubang/feature/capture/P20CandidateController.kt:53-64`
   问题：`windowStart += 3` 后对池长取模，池长非 3 倍数时多轮窗口重叠、候选重复出现。
   建议：生成不重复窗口序列，或在池尾明确提示“没有更多候选”而非回卷。

4. 定位：`tools/ocr_passrate.py:49,55`
   问题：字段非字符串时 `.strip()` 抛 `AttributeError`；`durationMs` 为 float 时被静默排除，P50/P90 样本数不可见。
   建议：加字段类型校验并打印实际参与统计的耗时条数。

5. 定位：`docs/OCR实测记录.md:3`
   问题：“达到方案里 ≥90% 落地首选的档”与 `AGENTS.md:20`“不写达标”口径冲突。
   建议：改为“仅作参考，非验收达标”，保留 18/20 注记。

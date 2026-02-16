# Magiscal (Android)

这是一个按你描述实现的“计算器外观 + 翻转触发时间结果”Demo。

## 已实现逻辑

1. UI 为深色原生计算器风格，常规输入和四则运算正常。
2. 点击 `AC` 会清空并开启一条新的本地记录（会重新记录后续每次 `+` 之前输入的数字）。
3. 设备翻转为屏幕朝下时：
   - 读取当前时间并拼接为 `MDDHHmm`（月不补零，日/时/分补零），得到 7 或 8 位。
   - 计算：`最后加数 = 时间数字 - 之前所有“+前数字”的和 + 2`。
   - 立刻把这个最后加数显示到当前算式里。
   - 锁定输入（翻转期间点击无效）。
4. 翻回屏幕朝上时恢复输入，按 `=` 按正常运算得到结果。

## 关键文件

- `app/src/main/java/com/magiscal/magiscal/MainActivity.kt`
- `app/src/main/res/layout/activity_main.xml`

## 导入说明

1. 用 Android Studio 打开该目录。
2. 若提示缺少 Gradle Wrapper，可在 IDE 里选择使用本地 Gradle 或重新生成 wrapper 后同步。
3. 连接 Pixel Fold 2023 或模拟器运行。

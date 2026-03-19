# Android Music 3

一个功能完整的Android音乐播放器应用，支持音频文件导入、视频音频提取、后台播放等功能。

## 功能特性

### 1. 音频文件管理
- **导入音频文件**：从手机文件夹中导入音频文件
- **多选批量删除**：支持选择多个文件进行批量删除
- **文件列表管理**：清晰的列表展示所有导入的音频文件

### 2. 视频音频提取
- **视频选择**：从设备中选择视频文件
- **音频提取**：将视频中的音频轨道提取为AAC格式
- **保存到库**：将提取的音频保存到设备媒体库
- **分享功能**：支持将提取的音频分享到其他应用

### 3. 播放控制
- **基本控制**：播放/暂停、上一曲、下一曲
- **播放速度**：支持0.5x到2.0x的播放速度调节
- **播放区间**：设置播放的开始和结束时间
- **循环播放**：支持设置循环次数（1-10次）

### 4. 后台播放
- **后台服务**：应用关闭后继续播放
- **通知控制**：通过系统通知控制播放
- **媒体按钮**：支持耳机媒体按钮控制

### 5. 界面设计
- **首页播放器**：启动即进入播放界面
- **简洁布局**：Material Design 3风格
- **深色主题**：支持深色模式

## 项目结构

```
androidMusic3/
├── app/
│   └── src/
│       └── main/
│           ├── java/com/example/androidmusic3/
│           │   ├── MainActivity.java              # 启动Activity，直接跳转到播放器
│           │   ├── MediaManager.java             # 媒体管理核心类
│           │   ├── service/
│           │   │   └── PlayerService.java        # 后台播放服务
│           │   ├── ui/
│           │   │   ├── PlayerActivity.java       # 主播放界面
│           │   │   ├── ImportActivity.java      # 音频导入界面
│           │   │   ├── ExtractAudioActivity.java # 视频音频提取界面
│           │   │   └── AudioListActivity.java   # 音频文件列表界面
│           │   ├── model/
│           │   │   ├── AudioFile.java           # 音频文件数据模型
│           │   │   └── PlaybackState.java       # 播放状态模型
│           │   └── util/
│           │       ├── PermissionHelper.java   # 权限助手类
│           │       └── AudioExtractor.java     # 音频提取工具类
│           └── res/
│               ├── layout/                      # XML布局文件
│               ├── values/                      # 资源值
│               └── menu/                        # 菜单资源
├── gradlew                                    # Gradle Wrapper
├── generate_keystore.sh                       # 生成签名证书脚本
├── build_apk.sh                              # 构建APK脚本
└── build_and_install.sh                      # 一键构建并安装脚本
```

## 环境要求

- Android Studio 2022.1 或更高版本
- JDK 8 或更高版本
- Android API 24+ (Android 7.0+)

## 构建步骤

### 1. 生成签名证书

首次构建前需要生成签名证书：

```bash
./generate_keystore.sh
```

脚本会提示输入信息并生成 `keystore.jks` 文件。

### 2. 构建APK

方法一：手动构建

```bash
./gradlew assembleRelease
```

APK位置：`app/build/outputs/apk/release/app-release.apk`

方法二：一键构建并安装（推荐）

```bash
./build_and_install.sh
```

这个脚本会自动：
- 生成签名证书（如果不存在）
- 清理之前的构建
- 构建release APK
- 安装到连接的设备

### 3. 手动安装APK

如果设备已连接，可以直接安装：

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

## 使用说明

### 1. 导入音频文件
- 点击"Import"按钮
- 选择要导入的音频文件（支持多选）
- 点击"Import"完成导入

### 2. 从视频中提取音频
- 点击"Extract"按钮
- 选择视频文件
- 点击"Extract"开始提取
- 提取完成后可选择保存或分享

### 3. 播放控制
- 主界面显示当前播放的音频
- 使用播放/暂停、上一曲、下一曲控制
- 点击"Speed"调节播放速度
- 点击"Loop"设置循环次数
- 点击"Set Range"设置播放区间

### 4. 音频列表
- 点击"List"查看所有音频文件
- 长按文件选择操作
- 支持多选和批量删除

### 5. 后台播放
- 应用关闭后仍可在后台播放
- 通过通知栏控制播放
- 支持蓝牙/耳机媒体按钮

## 权限说明

应用需要以下权限：

- `READ_EXTERNAL_STORAGE` - 读取音频文件
- `WRITE_EXTERNAL_STORAGE` - 保存提取的音频
- `READ_MEDIA_AUDIO` - 访问媒体库音频
- `READ_MEDIA_VIDEO` - 访问媒体库视频
- `FOREGROUND_SERVICE` - 启动前台服务
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK` - 前台媒体播放服务
- `POST_NOTIFICATIONS` - 显示通知
- `WAKE_LOCK` - 防止休眠

## 技术栈

- **语言**：Kotlin
- **UI框架**：AndroidX Material Components
- **播放引擎**：ExoPlayer 2.19.1
- **架构**：MVVM
- **协程**：Kotlin Coroutines
- **生命周期**：Lifecycle-aware components

## 注意事项

1. 首次使用需要授予存储权限
2. 视频音频提取需要一定时间，请耐心等待
3. 后台播放时不要手动停止进程，否则会影响播放
4. 提取的音频文件保存在应用的私有目录中

## 故障排除

### 1. 无法导入音频文件
- 检查是否授予了存储权限
- 确保文件格式受支持（MP3、AAC、WAV等）

### 2. 无法提取音频
- 确保视频文件包含音频轨道
- 检查存储空间是否足够

### 3. 后台播放停止
- 检查电池优化设置
- 确保通知权限已授予

### 4. 构建失败
- 确保已安装最新版本的Gradle
- 检查JDK版本是否符合要求

## 开发

如需进行二次开发：

1. 使用Android Studio打开项目
2. 修改 `app/build.gradle.kts` 配置构建信息
3. 运行 `./gradlew buildDebug` 构建调试版本
4. 使用 `adb install -r app/build/outputs/apk/debug/app-debug.apk` 安装调试版

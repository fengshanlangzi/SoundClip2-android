# Android Music 3 使用说明

## 功能特性

### 1. 音频文件导入
- 支持从手机存储导入音频文件
- 支持格式：MP3, WAV, M4A, AAC, FLAC, OGG
- 支持多选批量导入
- 列表展示所有导入的文件
- 导入后保存到文件，杀进程再次打开时候播放里边加载历史导入的文件 ✅

### 2. 视频音频提取
- 从视频文件中提取音频
- 提取为AAC格式
- 提取后支持保存到媒体库或分享到其他App

### 3. 播放控制（首页播放界面）
- **上一曲/下一曲**：切换播放列表中的音频
- **播放速度**：0.5x - 2.0x 可调
- **播放区间**：设置起始时间和持续时间 ✅
- **循环次数**：支持无限循环（∞）或自定义循环次数，默认为无限循环 ✅
- **播放/暂停**：控制播放状态
- 自动恢复上次播放的音频文件、循环次数和播放区间设置 ✅

### 4. 文件管理
- 音频文件列表显示
- 支持删除单个文件
- 支持多选批量删除

### 5. 后台播放
- 支持后台播放音频
- 通知栏显示播放控制
- 支持上一曲/下一曲控制

## 快速开始

### 一键构建并安装

在项目根目录执行：

```bash
./build_and_install.sh
```

这会自动：
1. 生成签名证书（如果不存在）
2. 清理之前的构建
3. 构建release APK
4. 安装到连接的设备

### 手动构建

生成签名证书：
```bash
./generate_keystore.sh
```

构建APK：
```bash
./build_apk.sh
```

APK 位置：`app/build/outputs/apk/release/app-release.apk`

## 安装说明

1. 确保Android手机已启用USB调试
2. 使用USB连接手机
3. 执行 `./build_and_install.sh` 自动安装

或手动安装：
```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

## 使用指南

### 主界面（播放器）
应用启动后直接进入播放器界面：

- **顶部按钮**：
  - Import：导入音频文件
  - Extract：从视频提取音频
  - List：查看/管理音频文件列表

- **播放控制**：
  - 播放/暂停按钮
  - 上一曲/下一曲按钮
  - 进度条（可拖动）

- **高级设置**：
  - Speed：调节播放速度（0.5x - 2.0x）
  - Loop：设置循环次数（1-10次）
  - Set Range：设置播放区间

### 导入音频
1. 点击"Import"按钮
2. 浏览文件系统选择音频文件
3. 可多选多个文件
4. 点击"Import"完成导入
5. 导入后自动添加到播放列表

### 提取视频音频
1. 点击"Extract"按钮
2. 选择视频文件
3. 点击"Extract Audio"开始提取
4. 等待提取完成（显示进度）
5. 完成后选择：
   - Save to Library：保存到设备媒体库
   - Share：分享到其他应用

### 音频文件列表
1. 点击"List"按钮
2. 查看所有导入的音频文件
3. 操作方式：
   - 单击：播放该音频
   - 长按：显示操作菜单（播放/删除/选择）
   - 多选：进入选择模式后勾选文件
4. 支持批量删除操作

### 后台播放
应用关闭后音乐继续播放：
- 通知栏显示当前播放信息和控制按钮
- 点击通知可以返回应用
- 支持耳机媒体按钮

## 权限说明

首次使用时需要授予以下权限：

- `READ_EXTERNAL_STORAGE` / `READ_MEDIA_AUDIO`：读取音频文件
- `WRITE_EXTERNAL_STORAGE`：保存提取的文件
- `READ_MEDIA_VIDEO`：访问视频文件进行提取
- `FOREGROUND_SERVICE`：启动后台服务
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK`：媒体播放前台服务
- `POST_NOTIFICATIONS`：显示播放通知
- `WAKE_LOCK`：防止设备休眠

## 签名信息

- Keystore 文件：`keystore.jks`（项目根目录）
- 密码：`androidmusic3`
- 别名：`androidmusic3`
- Key 密码：`androidmusic3`

注意：首次构建前需要运行 `./generate_keystore.sh` 生成签名证书。

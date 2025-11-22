# Synthetic Quota Tracker 📊

[![Version](https://img.shields.io/badge/version-1.0-blue.svg)]()
[![Platform](https://img.shields.io/badge/platform-IntelliJ-pink.svg)]()
[![Java](https://img.shields.io/badge/java-17%2B-orange.svg)]()

**Synthetic Quota Tracker** is a JetBrains IDE plugin designed to help you monitor your [Synthetic API](https://synthetic.new) usage directly from your development environment. Stay informed about your quota limits and renewal times without leaving your code.

---

##  Disclaimer: I am not affiliated with [Synthetic.new](https://synthetic.new) in any way.


## ✨ Features

*   **Real-time Status Bar Widget**: View your current request count and total limit at a glance (e.g., `Synthetic: 150/1000`).
*   **Detailed Tooltip**: Hover over the widget to see exactly when your quota renews.
*   **Auto-Refresh**: The plugin automatically polls the API to keep your stats up-to-date.
*   **Configurable**: Set your own refresh interval and manage your API token securely.
*   **Click-to-Configure**: Click the status bar widget to quickly access settings.

## 🚀 Getting Started

### Prerequisites

*   **IntelliJ IDEA** (or any JetBrains IDE) running on **Java 17** or later.
*   A valid API Token from [Synthetic](https://synthetic.new).

### Installation

1.  **Build from Source** (see below) or download the latest release.
2.  In your IDE, go to `Settings/Preferences` > `Plugins`.
3.  Click the **Gear Icon** ⚙️ and select **Install Plugin from Disk...**.
4.  Choose the plugin `.zip` file.
5.  Restart the IDE.

### Configuration

Once installed, you need to connect the plugin to your account:

1.  Open `Settings/Preferences`.
2.  Navigate to **Tools** > **Synthetic Quota Tracker**.
3.  Paste your **API Token**.
4.  (Optional) Adjust the **Refresh Interval** (default is 60 seconds).
5.  Click **Apply/OK**.

The widget will appear in your status bar (bottom right) and start fetching data.

---

## 🛠️ Development & Building

This project is built using Gradle and the IntelliJ Platform Plugin SDK.

### Build the Plugin

To create the distributable ZIP file:

**Windows**
```powershell
.\gradlew.bat buildPlugin
```

**macOS / Linux**
```bash
./gradlew buildPlugin
```

 The artifact will be generated at: `build/distributions/synthetic-quota-tracker-1.0-SNAPSHOT.zip`.

### Run in Sandbox

Test the plugin in an isolated IDE instance:

**Windows**
```powershell
.\gradlew.bat runIde
```

**macOS / Linux**
```bash
./gradlew runIde
```

## ❓ Troubleshooting

**Build fails with "Dependency requires at least JVM runtime version 17"**

This project strictly requires **Java 17**.
*   Check your version: `java -version`
*   If older, install JDK 17 and set your `JAVA_HOME` environment variable before running the build commands.

## 🙏 Acknowledgments

* I was inspired by [VSCode Synthetic Quota](https://marketplace.visualstudio.com/items?itemName=nrw.vscode-synthetic-quota) extension to make this plugin for JetBrains IDEs. Thanks for the initial idea!
* Also thanks to the [Synthetic](https://synthetic.new) team for providing the API.
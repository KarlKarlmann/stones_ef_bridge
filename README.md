# Stones: Epic Fight Integration

![Minecraft Version](https://img.shields.io/badge/Minecraft-1.20.1-brightgreen)
![Forge Version](https://img.shields.io/badge/Forge-47.3.0+-orange)
![License](https://img.shields.io/badge/License-CC%20BY--NC%204.0-blue)
[![Support on Ko-fi](https://img.shields.io/badge/Ko--fi-Support_Project-F16061?logo=ko-fi&logoColor=white)](https://ko-fi.com/karlkarlmann)

A Minecraft Forge addon for **Stones** and **Epic Fight** for Minecraft **1.20.1**.

This addon replaces Epic Fight's default skill progression with the progression system from **Stones**.

Instead of permanently learning skills directly from Skill Books, duplicate books can be collected and used to enchant Epic Fight Skills onto **Stones Runes**. Once socketed into your Runestone, these runes allow you to unlock and upgrade Epic Fight abilities using the Stones progression system and Player XP Levels.

The goal is not to reinvent Epic Fight, but to make its progression feel like a natural part of the Stones experience.

---

## 🌟 Features

* **⚔️ Integrated Skill Progression:** Replaces Epic Fight's default skill learning with rune-based progression.

* **📚 Skill Book Collection:** Duplicate Skill Books are never wasted. Every additional copy contributes towards enchanting a Rune, allowing gradual progression instead of relying on a single lucky drop.

* **🔮 Rune Integration:** Epic Fight Skills become enchantments on Stones Runes and can be socketed into your Runestone like any other ability.

* **🧩 Automatic Addon Support:** Skills added by Epic Fight addons are detected automatically and integrated without requiring dedicated compatibility patches.

* **⚙️ Configurable Progression:** Skill rarity, required book count, XP costs, and progression values can all be configured to fit your own modpack.

* **🌐 Multiplayer Ready:** All progression settings are synchronized from the server to connected clients.

* **🖥️ Integrated User Interface:** Skill Books, Rune Tooltips, and the enchanting workflow are fully integrated into the existing Stones menus.

---

## 🎯 Design Philosophy

This addon intentionally focuses on **one thing only**: integrating Epic Fight into the Stones progression system.

It does **not** add new combat mechanics, weapons, armor, or unrelated gameplay systems. Instead, it preserves Epic Fight's combat while replacing its progression with a more collection-driven approach that naturally fits into Stones.

Because every modpack balances Epic Fight differently, this addon intentionally avoids enforcing a universal progression. Server owners and modpack creators are encouraged to tweak the configuration to match their own economy and gameplay pace.

---

## 🔗 Links & Support

* 🎥 **Showcase Video:** Coming soon.

* ☕ **Support the Project:** https://ko-fi.com/karlkarlmann

---

## 🎮 Installation (For Players)

> 💡 The latest compiled version can always be downloaded from the CurseForge page.

### Requirements

- Minecraft **1.20.1**
- Minecraft Forge **47.3.0+**
- Stones
- Epic Fight

Simply place all required mods into your `mods` folder and launch the game.

---

## 💻 Development (Build from Source)

### Prerequisites

- Java 17
- Git

### Setup

Clone the repository:

```bash
git clone https://github.com/KarlKarlmann/stones-epicfight-integration.git
```

Enter the project directory:

```bash
cd stones-epicfight-integration
```

Build using Gradle:

```bash
# Windows
gradlew build

# Linux / macOS
./gradlew build
```

The compiled mod can be found inside:

```
build/libs/
```

---

## 📜 Credits & Permissions

* **Author:** KarlKarlmann
* **Modpacks:** You are free to include this addon in any CurseForge modpack.
* **Videos & Streams:** Feel free to showcase this addon in videos or livestreams.
* **Re-Uploads:** Please do not re-upload the mod files to other websites. Link back to the original CurseForge or GitHub page instead.

---

## ⚖️ License

This project is licensed under the **CC BY-NC 4.0** License.

https://creativecommons.org/licenses/by-nc/4.0/
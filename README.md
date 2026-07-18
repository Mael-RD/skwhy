<div align="center">

<br/>

```
███████╗██╗  ██╗██╗    ██╗██╗  ██╗██╗   ██╗
██╔════╝██║ ██╔╝██║    ██║██║  ██║╚██╗ ██╔╝
███████╗█████╔╝ ██║ █╗ ██║███████║ ╚████╔╝ 
╚════██║██╔═██╗ ██║███╗██║██╔══██║  ╚██╔╝  
███████║██║  ██╗╚███╔███╔╝██║  ██║   ██║   
╚══════╝╚═╝  ╚═╝ ╚══╝╚══╝ ╚═╝  ╚═╝   ╚═╝  
```

**A personal Skript addon — built for a specific server, open for everyone.**

![Skript](https://img.shields.io/badge/Skript-addon-4B8BBE?style=flat-square&logo=java&logoColor=white)
![Minecraft](https://img.shields.io/badge/Minecraft-Java-62B542?style=flat-square)
![Status](https://img.shields.io/badge/status-active%20development-orange?style=flat-square)

</div>

---

## What is SkWhy?

SkWhy is a personal Skript addon built for a private Minecraft server. Every feature in it exists because no other addon provided it — either it was too niche, too different from what was available, or simply missing. The result is a growing collection of very specific, handcrafted modules.

If you run into the same gaps, you're welcome to use it.

---

## Modules

All modules can be individually **enabled or disabled** in the plugin config.

---

### 📦 Fake Display Entities — *Packet-based display group management*

Lets you create, manipulate and destroy groups of fake display entities (block, item, text) entirely through packets — no real entities are spawned on the server.

Groups can be scaled, rotated, mirrored, mounted, and updated at any time. Everything is serializable and can be stored in Skript variables for persistence.

**Requires:** [PacketEvents](https://github.com/retrooper/packetevents)

```skript
set {_group} to a new fake display group at player's location
set {_block} to [a new fake block display]:
    block: stone
    scale: vector(2, 2, 2)
    shadow: 0.5
mount group {_group} to player
mirror {_group} on axis x
destroy display group {_group}
```

**What's included:**
- Fake block, item and text display types
- Full transform control: scale, translation, rotation, billboard, glow
- Cosmetic system with hat, back and tail parts
- Tail physics and per-part visibility

---

### 🎙️ Voice Recognition — *Keyword detection over SimpleVoiceChat*

Listens to players speaking through [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) and fires a Skript event when a recognized word or phrase is detected. The phrase list can be updated at any time without reloading.

Detection is fast and resource-light — powered by [Vosk](https://alphacephei.com/vosk/), an offline speech recognition engine. No audio is ever sent to an external server.

**Requires:** [SimpleVoiceChat](https://modrinth.com/plugin/simple-voice-chat)

**Setup — Vosk model installation:**

> 1. Download a model from **[alphacephei.com/vosk/models](https://alphacephei.com/vosk/models)**
>    → The **small** variant is recommended for performance
>    → Pick the language that matches your players
> 2. Unzip the downloaded archive
> 3. Place the extracted folder at `plugins/SkWhy/model/`

```skript
on voice phrase detected:
    broadcast "%player% said: %event-string%"
```

```skript
if player has voice:
    send "You are connected to voice chat."
```

---

### 🔧 Miscellaneous

Small additions that don't belong to a larger module — utilities and expressions that come up during development and get added along the way.

Includes things like body yaw control, entity metadata helpers, and other low-level tools that Skript doesn't expose by default.

---

## Roadmap

These features are in progress or planned for the future. No ETA.

| Feature | Status |
|---|---|
| **Pathfinding** — Real or packet-only NPCs that navigate the world using actual pathfinding | 🔨 In development |
| **Long-range GPS routing** — Guide pathfinding entities or players across very large distances | 🗓️ Planned (long-term) |

---

## Installation

1. Download the latest release
2. Drop `SkWhy.jar` into your `plugins/` folder
3. Add any required dependencies for the modules you want to use
4. Start the server — modules with missing dependencies will disable themselves automatically and log a clear warning

---

## Dependencies

| Module | Required dependency |
|---|---|
| All | Skript 2.15+ |
| Fake Display Entities | [PacketEvents](https://github.com/retrooper/packetevents) |

---

## Documentation

Full syntax reference is available in the [releases](../../releases) — each version ships with a patchnote listing every effect, expression, condition, type and section with patterns and examples.
[![SkriptHubViewTheDocs](http://skripthub.net/static/addon/ViewTheDocsButton.png)](http://skripthub.net/docs/?addon=SkWhy)

---

<div align="center">

Built for one server. Shared for whoever needs it.

</div>

ps, documentation and coding is partially IA made, I've checked it, it must be fine, I think, at least it's working fine.

# Better Whips

Minecraft 1.21.1 / NeoForge 的程序化物理鞭子模组源码。

## 仓库内容

包含 Java 源码、着色器源码、语言文件、配方、标签及模组配置。
模型、贴图、图标和音效不包含在本仓库中，也不属于本仓库 MIT 许可证的授权范围。

## 构建与运行

这是源码发布仓库，不是完整的可直接构建工程。当前源码包不包含 Gradle 构建脚本或 Wrapper。
需要 Java 21、适用于 Minecraft 1.21.1 的 NeoForge 开发工程及其构建配置。
`neoforge.mods.toml` 中的变量须由构建配置填充。
运行还需要另行提供具有使用权限的模型、贴图、图标和音效，并保持源码引用的资源路径。
仅下载本仓库不能得到完整可运行的模组。

## License

本仓库发布的代码使用 [MIT License](LICENSE)。未发布的美术和音频资源不随代码授权。

---

Source release of a procedural physics whip mod for Minecraft 1.21.1 and NeoForge.
Java sources, shader sources, translations, recipes, tags and mod configuration are included.
Models, textures, icons and audio are excluded and are not licensed under this repository's MIT license.

This is not a standalone buildable project: Gradle build scripts and the wrapper were not included
in the source package. Building requires Java 21 and an appropriate NeoForge 1.21.1 development
project, including substitution of the mod metadata placeholders. Running additionally requires
separately authorized assets at the resource paths referenced by the code.

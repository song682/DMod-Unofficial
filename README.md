# D-Mod Unofficial

![](docs/dmod_banner.png)

D-Mod is a mod that backports some features from later versions of Minecraft to 1.7.10. You could consider it an add-on to [Et Futurum Requiem](https://www.curseforge.com/minecraft/mc-mods/et-futurum-requiem) (though it's not a hard dependency).

---

**Currently implemented**:

* Foxes(Before 2.2.0)
* Bundles
* Colorful bundles
* Grindstone
* Loom

> [!IMPORTANT]
> After 2.2.0, hence the future Et Futurum Requiem add the foxes. This mods foxes will get its retirement. And hence the retirement, the prerequisites [Jar Utils](https://github.com/song682/jar-utils) is no longer needed.

# Known Limitation (Before 2.5.0, developing to address such an issue)

* The bundle has three layers, which let it show the contents in the item like it opens, which causing error when you put a glass or something that opaque in the bundle this will render transparent the bundle layer to the background.
* And some problem was caused when you open the ui and put the cursor on the item it will render two tooltip background stacked over the legacy tooltip and I'm still debating on how to fix it.

# Dependencies

* [CatFrame](https://github.com/song682/CatFrame) allow us showing the contents in the bundle for which provides the modern Item model rendering. 
* [Jar Utils(2.2.0 only)](https://github.com/song682/jar-utils) for determine the GTNH version's [Et Futurum Requiem](https://github.com/GTNewHorizons/Et-Futurum-Requiem).
* [UniMixins](https://github.com/LegacyModdingMC/UniMixins) provides a lot of Mixin support and the official's recommended mods [MixingASM](https://github.com/makamys/Mixingasm) is also self-contained. 
* [Et Futurum Requiem](https://www.curseforge.com/minecraft/mc-mods/et-futurum-requiem) is highly recommended as it backports sweet berries (useful for foxes) and rabbits (useful for bundles), and the banner/dye of the loom that this mod added.


# License

This mod is licensed under [MIT License](LICENSE) (Former is the [Unlicense](LICENSE-legacy)).   
It largely consists of ported Mojang code though, so keep that in mind.

---

# Credits

The original mod, which is usually a big help in this unofficial's development.    
The original link: [![Original](https://img.shields.io/badge/GitHub-gray?logo=github)](https://github.com/makamys/DMod)[![CurseForge](https://shields.io/badge/CurseForge-555555?logo=curseforge)](https://www.curseforge.com/minecraft/mc-mods/dmod)

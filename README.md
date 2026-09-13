# D-Mod Unofficial

![](docs/dmod_banner.png)

D-Mod is a mod that backports some features from later versions of Minecraft to 1.7.10. You could consider it an add-on to [Et Futurum Requiem](https://www.curseforge.com/minecraft/mc-mods/et-futurum-requiem) (though it's not a hard dependency).

---

**Currently implemented**:

* Foxes(Before 2.2.0)
* Bundles
* Colorful bundles

## Foxes

Foxes have some (optional) extra features implemented. Untamed, they behave the same as they do in new vanilla versions. But as they defeat enemies, they gain EXP and unlock various new abilities and AI improvements.

The major ones are getting healed when fed food, following the owner after being fed a sweet berry, and possessing Looting I intrinsically (does not stack with swords that have Looting). Foxes pass down their EXP to their children.

More info [in the wiki](https://github.com/makamys/DMod/wiki/Fox).

> [!IMPORTANT]
> After 2.2.0, hence the future Et Futurum Requiem add the foxes. This mods foxes will get its retirement. And hence the retirement, the prerequisites [Jar Utils](https://github.com/song682/jar-utils) is no longer needed.     

## Bundle

The bundle has three layers, which let it show the contents in the item like it opens.   
Also the tooltip is also modernized.    

---

# Dependencies

* Item icons in bundle tooltips will only be drawn if [NEI](https://www.curseforge.com/minecraft/mc-mods/notenoughitems) is installed (I recommend the [GTNH fork](https://www.curseforge.com/minecraft/mc-mods/notenoughitems-gtnh)).
* [CatFrame](https://github.com/song682/CatFrame) allow us showing the contents in the bundle for which provides the modern Item model rendering. 
* [Jar Utils(2.2.0 only)](https://github.com/song682/jar-utils) for determine the GTNH version's [Et Futurum Requiem](https://github.com/GTNewHorizons/Et-Futurum-Requiem).
* [UniMixins](https://github.com/LegacyModdingMC/UniMixins) provides a lot of Mixin support and the official's recommended mods [MixingASM](https://github.com/makamys/Mixingasm) is also self-contained. 
* [Et Futurum Requiem](https://www.curseforge.com/minecraft/mc-mods/et-futurum-requiem) is highly recommended as it backports sweet berries (useful for foxes) and rabbits (useful for bundles).


# License

This mod is licensed under [MIT License](LICENSE) (Former is the [Unlicense](LICENSE-legacy)).   
It largely consists of ported Mojang code though, so keep that in mind.

---

# Credits

The original mod, which is usually a big help in this unofficial's development.    
The original link: [![1.12.2](https://img.shields.io/badge/GitHub-gray?logo=github)](https://github.com/makamys/DMod)[![CurseForge](https://shields.io/badge/CurseForge-555555?logo=curseforge)](https://www.curseforge.com/minecraft/mc-mods/dmod)

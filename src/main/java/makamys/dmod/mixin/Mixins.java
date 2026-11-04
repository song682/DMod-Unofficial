package makamys.dmod.mixin;

import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizon.gtnhmixins.builders.MixinBuilder;

import javax.annotation.Nonnull;

public enum Mixins implements IMixins {
    ACCESSOR(Side.CLIENT, "middle.accessor.AccessorGuiContainer"),

    MIXIN_GUI(Side.CLIENT, "middle.MixinContainer",
              "middle.MixinGuiContainer",
              "middle.MixinGuiScreen",
              "middle.MixinRenderItem");


    private final MixinBuilder builder;

    Mixins(MixinBuilder builder) {
        this.builder = builder;
    }

    Mixins(Side side, String... mixins) {
        this.builder = new MixinBuilder().addSidedMixins(side, mixins);
    }

    @Nonnull
    @Override
    public MixinBuilder getBuilder() {
        return this.builder;
    }
}

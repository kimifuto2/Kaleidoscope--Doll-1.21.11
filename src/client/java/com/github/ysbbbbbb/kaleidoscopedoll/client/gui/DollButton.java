package com.github.ysbbbbbb.kaleidoscopedoll.client.gui;

import com.github.ysbbbbbb.kaleidoscopedoll.KaleidoscopeDoll;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class DollButton extends Button {
    private static final Identifier BG = Identifier.fromNamespaceAndPath(KaleidoscopeDoll.MOD_ID, "textures/gui/computer.png");
    private final ItemStack doll;
    @Nullable
    private final Component tooltip;

    public DollButton(int x, int y, ItemStack doll, OnPress onPress) {
        this(x, y, doll, null, onPress);
    }

    public DollButton(int x, int y, ItemStack doll, @Nullable Component tooltip, OnPress onPress) {
        super(x, y, 18, 18, Component.empty(), onPress, DEFAULT_NARRATION);
        this.doll = doll;
        this.tooltip = tooltip;
    }

    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 先画槽位底图，再画物品：底图是不透明贴图，反过来的话会把物品盖住
        int v = 16;
        if (this.isFocused()) {
            v += 18;
        } else if (this.isHovered()) {
            v += 36;
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, BG, this.getX(), this.getY(), 177, v, 18, 18, 256, 256);
        graphics.renderFakeItem(this.doll, this.getX() + 1, this.getY() + 1);
        if (this.isHovered()) {
            if (this.tooltip != null) {
                graphics.setTooltipForNextFrame(Minecraft.getInstance().font, this.tooltip, mouseX, mouseY);
            } else {
                graphics.setTooltipForNextFrame(Minecraft.getInstance().font, this.doll, mouseX, mouseY);
            }
        }
    }
}

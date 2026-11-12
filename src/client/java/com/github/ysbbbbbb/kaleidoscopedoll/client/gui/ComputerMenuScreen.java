package com.github.ysbbbbbb.kaleidoscopedoll.client.gui;

import com.github.ysbbbbbb.kaleidoscopedoll.KaleidoscopeDoll;
import com.github.ysbbbbbb.kaleidoscopedoll.event.ModRegisterEvent;
import com.github.ysbbbbbb.kaleidoscopedoll.inventory.ComputerMenu;
import com.github.ysbbbbbb.kaleidoscopedoll.network.ComputerDollClickPayload;
import com.google.common.collect.Lists;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.github.ysbbbbbb.kaleidoscopedoll.network.PlayerDollCraftPayload;
import com.github.ysbbbbbb.kaleidoscopedoll.network.ComputerStatusPayload;
import com.github.ysbbbbbb.kaleidoscopedoll.network.PlayerDollPreviewPayload;
import com.github.ysbbbbbb.kaleidoscopedoll.network.PlayerDollPreviewResponsePayload;
import com.github.ysbbbbbb.kaleidoscopedoll.init.ModDataComponent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Locale;

import static com.github.ysbbbbbb.kaleidoscopedoll.event.ModRegisterEvent.SPECIAL_TOOLTIPS;

/**
 * 玩偶电脑界面：搜索框 + 8x4 玩偶网格 + 滚动条 + 输入/输出槽。
 * 点击玩偶按钮会发 {@link ComputerDollClickPayload} 给服务端，由服务端校验羊毛并生成输出。
 */
public class ComputerMenuScreen extends AbstractContainerScreen<ComputerMenu> {
    private static final Identifier BG = Identifier.fromNamespaceAndPath(KaleidoscopeDoll.MOD_ID, "textures/gui/computer.png");
    private static final Identifier OUTPUT_SLOT_ICON = Identifier.fromNamespaceAndPath(KaleidoscopeDoll.MOD_ID, "textures/slot/computer_output_slot.png");
    private static final int MAX_DOLLS_PER_PAGE = 32;
    public static final String PLAYER_PREFIX = "@+";

    private final List<ItemStack> dolls = Lists.newArrayList();
    private final List<DollButton> dollButtons = Lists.newArrayList();
    private int currentPage;
    private float scrollOffs;
    private boolean scrolling;
    private EditBox searchField;
    private Button confirmButton;
    private ItemStack selectedDoll = ItemStack.EMPTY;
    private ItemStack previewDoll = ItemStack.EMPTY;
    private boolean playerMode;
    private String lastRequestedName = "";
    private long lastRequestTime;
    private String statusKey = "";
    private String statusArg = "";
    private long statusUntil;

    public ComputerMenuScreen(ComputerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageHeight = 208;
    }

    @Override
    protected void init() {
        super.init();
        this.refresh();
    }

    /**
     * 重建搜索框与玩偶按钮（保留搜索框内容和焦点）。
     */
    private void refresh() {
        String previousText = this.searchField == null ? "" : this.searchField.getValue();
        boolean previousFocus = this.searchField != null && this.searchField.isFocused();

        this.clearWidgets();
        this.dollButtons.clear();

        this.initSearch(previousText, previousFocus);
        this.rebuildDolls();
    }

    private void initSearch(String previousText, boolean previousFocus) {
        this.searchField = new EditBox(this.font, this.leftPos + 9, this.topPos + 102, 66, 16,
                Component.literal("Doll Search Box"));
        // 必须先 setValue 再 setResponder，避免重建时触发回调递归
        this.searchField.setValue(previousText);
        this.searchField.setBordered(false);
        this.searchField.setMaxLength(16);
        this.searchField.setTextColor(0xFF39FF56);
        this.searchField.setFocused(previousFocus);
        this.searchField.moveCursorToEnd(true);
        // 实时搜索：普通文本即时过滤，@ 开头进入玩家预览模式
        this.searchField.setResponder(text -> {
            if (text.trim().startsWith("@")) {
                this.playerMode = true;
                this.selectedDoll = ItemStack.EMPTY;
                this.previewDoll = ItemStack.EMPTY;
                this.dolls.clear();
                this.rebuildDolls();
            } else {
                this.playerMode = false;
                this.lastRequestedName = "";
                this.currentPage = 0;
                this.scrollOffs = 0.0F;
                this.rebuildDolls();
            }
        });
        this.confirmButton = Button.builder(Component.literal("\u2714"), button -> this.confirmSearch())
                .bounds(this.leftPos + 84, this.topPos + 97, 18, 18)
                .build();
        this.addRenderableWidget(this.confirmButton);
        this.addWidget(this.searchField);
    }

    /**
     * 点击 ✔：普通文本过滤玩偶列表，@+玩家ID 直接请求服务端生成玩家玩偶。
     */
    private void confirmSearch() {
        String raw = this.searchField == null ? "" : this.searchField.getValue().trim();
        if (raw.startsWith("@")) {
            if (!this.selectedDoll.isEmpty()) {
                ClientPlayNetworking.send(new ComputerDollClickPayload(this.selectedDoll));
            } else {
                this.setStatus("message.kaleidoscope_doll.computer.select_doll", "");
            }
            return;
        }
        if (!this.selectedDoll.isEmpty()) {
            ClientPlayNetworking.send(new ComputerDollClickPayload(this.selectedDoll));
            return;
        }
        this.currentPage = 0;
        this.scrollOffs = 0.0F;
        this.rebuildDolls();
    }

    /**
     * 刷新当前页的玩偶按钮。
     */
    private void rebuildDolls() {
        for (DollButton button : this.dollButtons) {
            this.removeWidget(button);
        }
        this.dollButtons.clear();

        String raw = this.searchField == null ? "" : this.searchField.getValue().trim();
        if (raw.startsWith("@")) {
            this.dolls.clear();
            if (!this.previewDoll.isEmpty()) {
                this.dolls.add(this.previewDoll);
            }
            this.rebuildDollButtons();
            return;
        }

        String searchText = raw.toLowerCase(Locale.ROOT);
        this.dolls.clear();
        for (Item item : ModRegisterEvent.DOLL_ITEMS) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null) {
                continue;
            }
            String tooltipKey = SPECIAL_TOOLTIPS.get(id);
            // 只显示作者/赞助玩偶（与上游 PLAYER_DOLLS 标签一致）
            if (tooltipKey == null) {
                continue;
            }
            if (!searchText.isEmpty() && !matches(id, tooltipKey, searchText)) {
                continue;
            }
            this.dolls.add(new ItemStack(item));
        }

        this.rebuildDollButtons();
    }

    private void rebuildDollButtons() {
        for (DollButton button : this.dollButtons) {
            this.removeWidget(button);
        }
        this.dollButtons.clear();
        int maxPage = Math.max(0, (this.dolls.size() + MAX_DOLLS_PER_PAGE - 1) / MAX_DOLLS_PER_PAGE - 1);
        this.currentPage = Math.max(0, Math.min(this.currentPage, maxPage));
        int start = this.currentPage * MAX_DOLLS_PER_PAGE;
        int end = Math.min(start + MAX_DOLLS_PER_PAGE, this.dolls.size());
        for (int i = start; i < end; i++) {
            ItemStack doll = this.dolls.get(i);
            int index = i - start;
            int x = this.leftPos + 7 + (index % 8) * 18;
            int y = this.topPos + 17 + (index / 8) * 18;
            DollButton button = new DollButton(x, y, doll, b -> {
                for (DollButton other : this.dollButtons) {
                    other.setFocused(other == b);
                }
                this.selectedDoll = doll;
            });
            this.addRenderableWidget(button);
            this.dollButtons.add(button);
        }
    }

    private String currentPlayerName() {
        if (this.searchField == null) {
            return "";
        }
        return this.searchField.getValue().trim().replaceFirst("^@\\+?", "").trim();
    }

    /** 服务端返回玩家预览档案。 */
    public void setPreview(ResolvableProfile profile, String name, boolean found) {
        String current = this.currentPlayerName();
        if (!name.equals(current)) {
            return;
        }
        if (found) {
            ItemStack stack = new ItemStack(com.github.ysbbbbbb.kaleidoscopedoll.init.ModItems.PLAYER_DOLL);
            stack.set(ModDataComponent.DOLL_OWNER, profile);
            stack.set(DataComponents.PROFILE, profile);
            this.previewDoll = stack;
        } else {
            this.previewDoll = ItemStack.EMPTY;
            this.selectedDoll = ItemStack.EMPTY;
            this.setStatus("message.kaleidoscope_doll.computer.player_not_found", "");
        }
        this.rebuildDolls();
    }

    public void setStatus(String key, String arg) {
        this.statusKey = key;
        this.statusArg = arg;
        this.statusUntil = System.currentTimeMillis() + 6000L;
    }

    private boolean matches(Identifier id, String tooltipKey, String searchText) {
        if (id.toString().contains(searchText)) {
            return true;
        }
        // 语言文件的 key 也可作为搜索判断
        if (tooltipKey.contains(searchText)) {
            return true;
        }
        return I18n.get("tooltip.kaleidoscope_doll.doll." + tooltipKey).toLowerCase(Locale.ROOT).contains(searchText);
    }

    private int getOffscreenRows() {
        return Math.max(1, (this.dolls.size() + MAX_DOLLS_PER_PAGE - 1) / MAX_DOLLS_PER_PAGE - 1);
    }

    private boolean isScrollBarActive() {
        return this.dolls.size() > MAX_DOLLS_PER_PAGE;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, Component.translatable("block.kaleidoscope_doll.computer"),
                this.titleLabelX, this.titleLabelY, 0xFF404040, false);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, BG, this.leftPos, this.topPos, 0, 0,
                this.imageWidth, this.imageHeight, 256, 256);
        // 输出槽为空时画一个空槽图标（不使用图集 sprite，避免覆盖原版 blocks 图集）
        if (!this.menu.getSlot(ComputerMenu.OUTPUT_SLOT).hasItem()) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, OUTPUT_SLOT_ICON,
                    this.leftPos + 151, this.topPos + 98, 0, 0, 16, 16, 16, 16);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        // 滚动条
        int uOffset = this.isScrollBarActive() ? 0 : 14;
        int offset = (int) (57 * this.scrollOffs);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BG, this.leftPos + 155, this.topPos + 17 + offset,
                177 + uOffset, 0, 12, 15, 256, 256);

        // 制作进度：按原版箭头的形状逐行填充
        int craftTotal = this.menu.getCraftTotal();
        int craftProgress = this.menu.getCraftProgress();
        if (craftTotal > 0 && craftProgress > 0) {
            float ratio = Math.min(1.0F, (float) craftProgress / craftTotal);
            int fillWidth = Math.round(16 * ratio);
            int green = 0xFF55FF55;
            // 箭头在 GUI 内的像素形状：x 128..143，y 101..109
            int[][] arrowRows = {
                    {140, 140}, {140, 141}, {140, 142},
                    {128, 142}, {128, 143}, {128, 143},
                    {140, 142}, {140, 141}, {140, 140}
            };
            for (int i = 0; i < arrowRows.length; i++) {
                int rowX0 = arrowRows[i][0];
                int rowX1 = arrowRows[i][1];
                int rowEnd = Math.min(rowX1 + 1, 128 + fillWidth);
                if (rowEnd > rowX0) {
                    graphics.fill(this.leftPos + rowX0, this.topPos + 101 + i,
                            this.leftPos + rowEnd, this.topPos + 102 + i, green);
                }
            }
        }

        // 玩家预览请求（300ms 防抖）
        if (this.playerMode && this.searchField != null) {
            String name = this.searchField.getValue().trim().replaceFirst("^@\\+?", "").trim();
            if (!name.isEmpty() && !name.equals(this.lastRequestedName)
                    && System.currentTimeMillis() - this.lastRequestTime > 300) {
                this.lastRequestedName = name;
                this.lastRequestTime = System.currentTimeMillis();
                ClientPlayNetworking.send(new PlayerDollPreviewPayload(name));
            }
        }

        // 界面内提示（替代聊天栏）
        if (System.currentTimeMillis() < this.statusUntil && !this.statusKey.isEmpty()) {
            Component message = this.statusArg.isEmpty()
                    ? Component.translatable(this.statusKey)
                    : Component.translatable(this.statusKey, this.statusArg);
            graphics.enableScissor(this.leftPos + 6, this.topPos + 114, this.leftPos + 170, this.topPos + 124);
            graphics.drawString(this.font, message, this.leftPos + 8, this.topPos + 116, 0xFFB8860B, false);
            graphics.disableScissor();
        }

        if (this.searchField != null) {
            this.searchField.render(graphics, mouseX, mouseY, partialTick);
            if (!this.searchField.isFocused() && this.searchField.getValue().isEmpty()) {
                graphics.enableScissor(this.leftPos + 8, this.topPos + 97, this.leftPos + 82, this.topPos + 115);
                graphics.drawString(this.font, Component.translatable("gui.kaleidoscope_doll.computer.search")
                                .withStyle(ChatFormatting.GRAY),
                        this.leftPos + 10, this.topPos + 102, 0xFFAAAAAA, false);
                graphics.disableScissor();
            }
        }

        if (this.isHovering(108, 98, 16, 16, mouseX, mouseY)
            && this.hoveredSlot != null && !this.hoveredSlot.hasItem()) {
            graphics.setTooltipForNextFrame(this.font,
                    Component.translatable("tooltip.kaleidoscope_doll.computer.input"), mouseX, mouseY);
        }

        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        this.scrolling = false;
        double scrollX = this.leftPos + 155;
        double scrollY = this.topPos + 17;
        if (scrollX <= event.x() && event.x() < (scrollX + 12)
            && scrollY <= event.y() && event.y() < (scrollY + 72)) {
            this.scrolling = true;
        }

        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.scrolling && this.isScrollBarActive()) {
            float yOffset = (float) event.y() - this.topPos - 17;
            this.scrollOffs = Math.max(0.0F, Math.min(1.0F, yOffset / 57.0F));
            this.currentPage = (int) ((this.scrollOffs * this.getOffscreenRows()) + 0.5F);
            this.rebuildDolls();
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.isScrollBarActive()) {
            int offscreenRows = this.getOffscreenRows();
            float scrollAmount = (float) scrollY / offscreenRows;
            this.scrollOffs = Math.max(0.0F, Math.min(1.0F, this.scrollOffs - scrollAmount));
            this.currentPage = (int) ((this.scrollOffs * offscreenRows) + 0.5F);
            this.rebuildDolls();
        }
        return true;
    }
}

package com.solegendary.reignofnether.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.registrars.Keybinds;
import com.solegendary.reignofnether.units.Unit;
import com.solegendary.reignofnether.units.UnitClientEvents;
import com.solegendary.reignofnether.util.MiscUtil;
import com.solegendary.reignofnether.util.MyRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.model.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class HudClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();
    private static int mouseX = 0;
    private static int mouseY = 0;

    private static ArrayList<Button> unitButtons = new ArrayList<>();
    private static final ArrayList<Button> genericActionButtons = new ArrayList<>(Arrays.asList(
            ActionButtons.attack,
            ActionButtons.stop,
            ActionButtons.hold,
            ActionButtons.move
    ));
    // unit type that is selected in the list of unit icons
    public static Entity hudSelectedUnit = null;
    // private class used to render only the head of a unit on screen for the portrait
    public static PortraitRenderer portraitRenderer = new PortraitRenderer(null);

    // where to start drawing the centre hud (from left to right: portrait, stats, unit icon buttons)
    private static int hudStartingXPos = 0;

    // if we are rendering > this amount, then just render an empty icon with +N for the remaining units
    private static final int unitButtonsPerRow = 8;

    // eg. entity.reignofnether.zombie_unit -> zombie
    private static String getSimpleUnitName(Entity unit) {
        return unit.getName().getString()
            .replace(" ","")
            .replace("entity.reignofnether.","")
            .replace("_unit","");
    }

    @SubscribeEvent
    public static void onDrawScreen(ScreenEvent.DrawScreenEvent evt) {
        String screenName = evt.getScreen().getTitle().getString();
        if (!OrthoviewClientEvents.isEnabled() || !screenName.equals("topdowngui_container"))
            return;
        if (MC.level == null)
            return;

        hudStartingXPos = MC.getWindow().getGuiScaledWidth() / 5;

        mouseX = evt.getMouseX();
        mouseY = evt.getMouseY();

        ArrayList<LivingEntity> units = new ArrayList<>();
        unitButtons = new ArrayList<>();

        for (int id: UnitClientEvents.getSelectedUnitIds()) {
            Entity entity = MC.level.getEntity(id);
            if (entity instanceof LivingEntity)
                units.add((LivingEntity) entity);
        }

        // sort and hudSelect the first unit type in the list
        units.sort(Comparator.comparing(HudClientEvents::getSimpleUnitName));

        if (units.size() <= 0)
            hudSelectedUnit = null;
        else if (hudSelectedUnit == null || units.size() == 1)
            hudSelectedUnit = units.get(0);
        // create all of the unit buttons for this frame
        int screenHeight = MC.getWindow().getGuiScaledHeight();

        int iconSize = 14;
        int iconFrameSize = Button.iconFrameSize;

        for (LivingEntity unit : units) {
            if (unitButtons.size() < (unitButtonsPerRow * 2)) {
                // mob head icon
                String unitName = getSimpleUnitName(unit);

                unitButtons.add(new Button(
                        unitName,
                        iconSize,
                        "textures/mobheads/" + unitName + ".png",
                        unit,
                        () -> getSimpleUnitName(hudSelectedUnit).equals(unitName),
                        () -> {
                            // click to select this unit type as a group
                            if (getSimpleUnitName(hudSelectedUnit).equals(unitName)) {
                                UnitClientEvents.setSelectedUnitIds(new ArrayList<>());
                                UnitClientEvents.addSelectedUnitId(unit.getId());
                            } else { // select this one specific unit
                                hudSelectedUnit = unit;
                            }
                        }
                ));
            }
        }

        // ------------------------------------------------
        // Unit head portrait (based on selected unit type)
        // ------------------------------------------------
        int blitX = hudStartingXPos;
        int blitY = MC.getWindow().getGuiScaledHeight() - portraitRenderer.frameSize;

        if (hudSelectedUnit != null && portraitRenderer.model != null && portraitRenderer.renderer != null) {
            portraitRenderer.renderHeadOnScreen(
                    evt.getPoseStack(), blitX, blitY,
                    (LivingEntity) hudSelectedUnit);

            // draw unit stats
            blitX += portraitRenderer.frameSize - 2;
            MyRenderer.renderFrameWithBg(evt.getPoseStack(), blitX, blitY,
                    portraitRenderer.frameSize,
                    portraitRenderer.frameSize,
                    0x80000000);
        }

        // ----------------------------------------------
        // Unit icons using mob heads on 2 rows if needed
        // ----------------------------------------------
        int buttonsRendered = 0;
        blitX += portraitRenderer.frameSize + 20;
        int blitXStart = blitX;
        blitY = screenHeight - iconFrameSize;
        if (unitButtons.size() > unitButtonsPerRow)
            blitY -= iconFrameSize + 5;

        for (Button unitButton : unitButtons) {
            // replace last icon with a +X number of units icon
            if (buttonsRendered == (unitButtonsPerRow * 2) - 1 &&
                    units.size() > (unitButtonsPerRow * 2)) {
                int numExtraUnits = units.size() - (unitButtonsPerRow * 2) + 1;
                MyRenderer.renderIconFrameWithBg(evt.getPoseStack(), blitX, blitY, iconFrameSize, 0x64000000);
                GuiComponent.drawCenteredString(evt.getPoseStack(), MC.font, "+" + numExtraUnits,
                        blitX + 8, blitY + 8, 0xFFFFFF);
            }
            else {
                unitButton.render(evt.getPoseStack(), blitX, blitY, mouseX, mouseY);
                unitButton.renderHealthBar(evt.getPoseStack());
                blitX += iconFrameSize;
                buttonsRendered += 1;
                if (buttonsRendered == unitButtonsPerRow) {
                    blitX = blitXStart;
                    blitY += iconFrameSize + 6;
                }
            }
        }

        // -------------------------------------------------------
        // Unit action icons (attack, stop, move, abilities etc.)
        // -------------------------------------------------------

        if (UnitClientEvents.getSelectedUnitIds().size() > 0) {
            blitX = 0;
            blitY = screenHeight - iconFrameSize;
            for (Button actionButton : genericActionButtons) {
                actionButton.render(evt.getPoseStack(), blitX, blitY, mouseX, mouseY);
                actionButton.checkPressed();
                blitX += iconFrameSize;
            }
            blitX = 0;
            blitY = screenHeight - (iconFrameSize * 2);
            for (LivingEntity unit : units) {
                if (getSimpleUnitName(unit).equals(getSimpleUnitName(hudSelectedUnit))) {
                    for (AbilityButton ability : ((Unit) unit).getAbilities()) {
                        ability.render(evt.getPoseStack(), blitX, blitY, mouseX, mouseY);
                        ability.checkPressed();
                        blitX += iconFrameSize;
                    }
                    break;
                }
            }
        }
    }

    @SubscribeEvent
    public static void onMouseRelease(ScreenEvent.MouseReleasedEvent.Post evt) {
        int mouseX = (int) evt.getMouseX();
        int mouseY = (int) evt.getMouseY();

        ArrayList<Button> buttons = new ArrayList<>();
        buttons.addAll(genericActionButtons);
        buttons.addAll(unitButtons);

        for (Button button : buttons)
            button.checkClicked(mouseX, mouseY);
    }

    @SubscribeEvent
    public static void onRenderLivingEntity(RenderLivingEvent.Pre<? extends LivingEntity, ? extends Model> evt) {
        LivingEntity entity = evt.getEntity();
        if (hudSelectedUnit == null) {
            portraitRenderer.model = null;
            portraitRenderer.renderer = null;
        }
        else if (entity == hudSelectedUnit) {
            portraitRenderer.model = evt.getRenderer().getModel();
            portraitRenderer.renderer = evt.getRenderer();
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent evt) {
        //if (OrthoviewClientEvents.isEnabled())
        //    portraitRenderer.tickAnimation();
    }

    // uncomment to adjust render position/size
    @SubscribeEvent
    public static void onInput(InputEvent.KeyInputEvent evt) {
        if (evt.getAction() == GLFW.GLFW_PRESS) { // prevent repeated key actions
            if (evt.getKey() == Keybinds.panMinusX.getKey().getValue())
                portraitRenderer.headOffsetX += 1;
            if (evt.getKey() == Keybinds.panPlusX.getKey().getValue())
                portraitRenderer.headOffsetX -= 1;
            if (evt.getKey() == Keybinds.panMinusZ.getKey().getValue())
                portraitRenderer.headOffsetY += 1;
            if (evt.getKey() == Keybinds.panPlusZ.getKey().getValue())
                portraitRenderer.headOffsetY -= 1;

            if (evt.getKey() == Keybinds.nums[9].getKey().getValue())
                portraitRenderer.headSize -= 1;
            if (evt.getKey() == Keybinds.nums[0].getKey().getValue())
                portraitRenderer.headSize += 1;
        }
    }
    @SubscribeEvent
    public static void onRenderOverLay(RenderGameOverlayEvent.Pre evt) {
        if (hudSelectedUnit != null)
            MiscUtil.drawDebugStrings(evt.getMatrixStack(), MC.font, new String[] {
                    "headOffsetX: " + portraitRenderer.headOffsetX,
                    "headOffsetY: " + portraitRenderer.headOffsetY,
                    "headSize: " + portraitRenderer.headSize
            });
    }
}

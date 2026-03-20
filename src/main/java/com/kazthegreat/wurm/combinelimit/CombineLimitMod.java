package com.kazthegreat.wurm.combinelimit;

import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.CannotCompileException;

import org.gotti.wurmunlimited.modloader.classhooks.HookException;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;

/**
 * CombineLimitMod — Raises the maximum combine weight for all items.
 *
 * Vanilla WU caps combining at 64x base weight (or 64kg for metal lumps).
 * This mod replaces those limits with configurable values.
 *
 * Patches: com.wurmonline.server.items.Item.combine(Item, Creature)
 *
 * By KaZtheGreat — for the Wurm Unlimited community.
 * https://wurmcoders.com
 */
public class CombineLimitMod implements WurmServerMod, PreInitable, Configurable {

    private static final Logger logger = Logger.getLogger(CombineLimitMod.class.getName());

    private int combineMultiplier = 100;
    private int metalLumpFloorKg = 100;

    @Override
    public void configure(Properties properties) {
        combineMultiplier = Integer.parseInt(properties.getProperty("combineMultiplier", "100"));
        metalLumpFloorKg = Integer.parseInt(properties.getProperty("metalLumpFloorKg", "100"));
        logger.log(Level.INFO, "CombineLimitMod: combineMultiplier=" + combineMultiplier
                + ", metalLumpFloorKg=" + metalLumpFloorKg);
    }

    @Override
    public void preInit() {
        try {
            ClassPool classPool = HookManager.getInstance().getClassPool();

            // Import all server classes that setBody() source code references.
            // At preInit() time, Javassist's ClassPool can resolve these from the
            // server's classpath, but only if we explicitly import them first.
            classPool.importPackage("com.wurmonline.server");
            classPool.importPackage("com.wurmonline.server.items");
            classPool.importPackage("com.wurmonline.server.creatures");
            classPool.importPackage("com.wurmonline.server.zones");

            CtClass ctItem = classPool.get("com.wurmonline.server.items.Item");

            // Inject static config fields into the Item class
            ctItem.addField(CtField.make(
                "public static int combineLimitMultiplier = " + combineMultiplier + ";", ctItem));
            ctItem.addField(CtField.make(
                "public static int combineLimitFloorGrams = " + (metalLumpFloorKg * 1000) + ";", ctItem));

            // Inject a static helper method that computes max combine weight
            ctItem.addMethod(CtMethod.make(
                "public static float calcCombineMaxWeight(int templateWeightGrams, boolean metalLump) {" +
                "    float maxW = (float)(templateWeightGrams * combineLimitMultiplier);" +
                "    if (metalLump) {" +
                "        maxW = (float)java.lang.Math.max((int)maxW, combineLimitFloorGrams);" +
                "    }" +
                "    return maxW;" +
                "}",
                ctItem
            ));

            logger.log(Level.INFO, "CombineLimitMod: Added calcCombineMaxWeight helper to Item class");

            // Replace the combine() method body with our patched version.
            // Identical to vanilla except the maxW calculation uses our helper.
            // With importPackage() above, we can use short class names.
            CtMethod combineMethod = ctItem.getDeclaredMethod("combine");

            combineMethod.setBody(
                "{" +
                "    Item target = $1;" +
                "    com.wurmonline.server.creatures.Creature performer = $2;" +
                "    if (this.equals(target)) { return false; }" +
                "    Item parent = null;" +
                "    if (this.parentId != -10L && target.getParentId() != this.parentId) {" +
                "        try {" +
                "            parent = Items.getItem(this.parentId);" +
                "            if (!parent.hasSpaceFor(target.getVolume())) {" +
                "                throw new FailedException(\"The container could not contain the combined items.\");" +
                "            }" +
                "        } catch (NoSuchItemException e) {" +
                "            logInfo(\"Strange, combining item without parent: \" + this.id);" +
                "            throw new FailedException(\"The container could not contain the combined items.\");" +
                "        }" +
                "    }" +
                "    if (this.ownerId != -10L && target.getOwnerId() != -10L) {" +
                "        if (this.isCombineCold() || !this.isMetal() || target.getTemplateId() == 204 || performer.getPower() != 0 || (this.temperature >= 3500 && target.getTemperature() >= 3500)) {" +
                "            if (this.getTemplateId() == target.getTemplateId() && this.isCombine()) {" +
                "                if (this.getMaterial() == target.getMaterial() || (this.isWood() && target.isWood())) {" +
                "                    int allWeight = this.getWeightGrams() + target.getWeightGrams();" +
                "                    if (this.isLiquid() && parent != null && !parent.hasSpaceFor(allWeight)) {" +
                "                        throw new FailedException(\"The \" + parent.getName() + \" cannot contain that much \" + this.getName() + \".\");" +
                "                    }" +
                "                    float maxW = calcCombineMaxWeight(this.template.getWeightGrams(), this.template.isMetalLump());" +
                "                    if ((float)allWeight <= maxW) {" +
                "                        if (parent != null) {" +
                "                            try {" +
                "                                parent.dropItem(this.id, false);" +
                "                            } catch (NoSuchItemException e2) {" +
                "                                logWarn(\"This item doesn't exist: \" + this.id, e2);" +
                "                                return false;" +
                "                            }" +
                "                        }" +
                "                        float newQl = (this.getCurrentQualityLevel() * (float)this.getWeightGrams() + target.getCurrentQualityLevel() * (float)target.getWeightGrams()) / (float)allWeight;" +
                "                        if (allWeight > 0) {" +
                "                            if (target.isColor() && this.isColor()) {" +
                "                                this.setColor(WurmColor.mixColors(this.color, this.getWeightGrams(), target.color, target.getWeightGrams(), newQl));" +
                "                            }" +
                "                            if (this.getRarity() > target.getRarity()) {" +
                "                                if (Server.rand.nextInt(allWeight) > this.getWeightGrams() / 4) {" +
                "                                    this.setRarity(target.getRarity());" +
                "                                }" +
                "                            } else if (target.getRarity() > this.getRarity() && Server.rand.nextInt(allWeight) > target.getWeightGrams() / 4) {" +
                "                                this.setRarity(target.getRarity());" +
                "                            }" +
                "                            this.setWeight(allWeight, false);" +
                "                            this.setQualityLevel(newQl);" +
                "                            this.setDamage(0.0F);" +
                "                            Items.destroyItem(target.getWurmId());" +
                "                            if (parent != null && !parent.insertItem(this)) {" +
                "                                try {" +
                "                                    long powner = parent.getOwner();" +
                "                                    com.wurmonline.server.creatures.Creature pown = Server.getInstance().getCreature(powner);" +
                "                                    pown.getInventory().insertItem(this);" +
                "                                } catch (NoSuchCreatureException e3) {" +
                "                                    logWarn(this.getName() + \", \" + this.getWurmId() + e3.getMessage(), e3);" +
                "                                } catch (NoSuchPlayerException e4) {" +
                "                                    logWarn(this.getName() + \", \" + this.getWurmId() + e4.getMessage(), e4);" +
                "                                } catch (NotOwnedException e5) {" +
                "                                    VolaTile tile = Zones.getOrCreateTile((int)this.getPosX() >> 2, (int)this.getPosY() >> 2, this.isOnSurface());" +
                "                                    tile.addItem(this, false, false);" +
                "                                    logWarn(\"The combined \" + this.getName() + \" was created on ground. This should not happen.\");" +
                "                                }" +
                "                            }" +
                "                        } else {" +
                "                            Items.destroyItem(this.id);" +
                "                        }" +
                "                        return true;" +
                "                    } else {" +
                "                        throw new FailedException(\"The combined item would be too large to handle.\");" +
                "                    }" +
                "                } else {" +
                "                    throw new FailedException(\"The items are of different materials.\");" +
                "                }" +
                "            } else {" +
                "                return false;" +
                "            }" +
                "        } else {" +
                "            throw new FailedException(\"Metal needs to be glowing hot to be combined.\");" +
                "        }" +
                "    } else {" +
                "        throw new FailedException(\"You need to carry both items to combine them.\");" +
                "    }" +
                "}"
            );

            logger.log(Level.INFO, "CombineLimitMod: Successfully patched Item.combine()");
            logger.log(Level.INFO, "CombineLimitMod: Combine limit = " + combineMultiplier
                + "x base weight, metal lump floor = " + metalLumpFloorKg + "kg");

        } catch (NotFoundException e) {
            throw new HookException(e);
        } catch (CannotCompileException e) {
            logger.log(Level.SEVERE, "CombineLimitMod: Failed to compile patch: " + e.getMessage(), e);
            throw new HookException(e);
        }
    }
}

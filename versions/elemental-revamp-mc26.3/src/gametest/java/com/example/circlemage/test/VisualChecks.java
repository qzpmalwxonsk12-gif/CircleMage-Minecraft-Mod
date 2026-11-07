package com.example.circlemage.test;
import com.example.circlemage.client.*;
import com.example.circlemage.mana.*;
import com.example.circlemage.network.ModNetworking;
import com.example.circlemage.spell.SpellCatalog;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
/** Actual 26.3 client, isolated disposable world, screenshots and network assertions. */
public class VisualChecks implements FabricClientGameTest {
    public void runTest(ClientGameTestContext c) {
        try(var world=c.worldBuilder().create()) {
            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runOnServer(server -> {
                var player=server.getPlayerList().getPlayers().getFirst();
                var data=ManaPlayer.get(player); data.setIntroStage(2); data.setCircle(3); data.setCurrentMana(12000);
                for(var entry:SpellCatalog.ALL)data.unlockSpell(entry.spell().id);
                data.setBoundSpells((1<<11)|(1<<14)|(1<<15)|(1<<16));
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,com.example.circlemage.item.WandItem.build(com.example.circlemage.item.WandItem.WandMaterial.COPPER,com.example.circlemage.item.WandItem.WandCore.NETHER_STAR,com.example.circlemage.item.WandItem.WandCrystal.AMETHYST));
                player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST,new net.minecraft.world.item.ItemStack(com.example.circlemage.item.ModItems.MAGE_ROBE_AMETHYST));
                ModNetworking.syncToClient(player);
            });
            c.waitFor(mc -> mc.player!=null && ClientManaState.circle==3);
            c.runOnClient(mc -> ModKeybinds.cast(mc,18));
            world.getServer().waitFor(server -> server.getPlayerList().getPlayers().getFirst().hasEffect(com.example.circlemage.spell.RevampStatusEffects.SOOTHING));
            c.setScreen(MagicTreeScreen::new); c.waitTicks(10); c.takeScreenshot("tree-circle3");
            c.setScreen(SpellbookScreen::new); c.waitTicks(10); c.takeScreenshot("journal");
            c.runOnClient(mc -> mc.gui.setScreen(new ArcaneStationScreen(new com.example.circlemage.block.ArcaneStationScreenHandler(0,mc.player.getInventory()),mc.player.getInventory(),net.minecraft.network.chat.Component.literal("Arcane Station"))));
            c.waitTicks(10);c.takeScreenshot("arcane-station");
            c.runOnClient(mc -> mc.gui.setScreen(null));
            world.getServer().runCommand("tp @a 0 100 0 0 0");
            world.getServer().runCommand("fill -20 99 -20 20 99 25 minecraft:stone");
            world.getServer().runOnServer(server -> SpellPreviewScreen.spawn(server.getPlayerList().getPlayers().getFirst()));
            world.getServer().runCommand("tp @a 0 100 -8 0 0");
            c.waitTicks(25); c.takeScreenshot("spell-and-npc-gallery");
            c.runOnClient(mc -> ClientImpactState.trigger(30)); c.waitTicks(18);
            c.runOnClient(mc -> {if(!mc.gameRenderer.getAppliedPostEffects().contains(net.minecraft.resources.Identifier.fromNamespaceAndPath("circlemage","desaturate")))throw new AssertionError("Impact desaturation was not applied by the renderer");});
            c.takeScreenshot("impact-desaturation");
            c.waitTicks(35);
            System.out.println("CIRCLEMAGE_CLIENT_TEST_PASSED: network cast, mana sync, screens, wands, robes, entities and impact effect");
        }
    }
}

package com.example.circlemage.test;
import com.example.circlemage.entity.*;
import com.example.circlemage.item.*;
import com.example.circlemage.mana.*;
import com.example.circlemage.block.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.util.ProblemReporter;
public class MigrationGameTests {
    private static final String ARENA="circlemage_tests:arena";
    @GameTest(structure=ARENA)
    public void legacyStoragePreservesWandAndSlots(GameTestHelper c) {
        ManaData data=new ManaData(); ListTag list=new ListTag();
        CompoundTag diamond=new CompoundTag(); diamond.putByte("Slot",(byte)26); diamond.putString("id","minecraft:diamond"); diamond.putByte("Count",(byte)37); list.add(diamond);
        CompoundTag wand=new CompoundTag(); wand.putByte("Slot",(byte)3); wand.putString("id","circlemage:wand"); wand.putByte("Count",(byte)1);
        CompoundTag parts=new CompoundTag(); parts.putString("material","COPPER");parts.putString("core","NETHER_STAR");parts.putString("crystal","AMETHYST");parts.putInt("CustomModelData",1);wand.put("tag",parts);list.add(wand);data.setStorageItems(list);
        var inventory=new StorageInventory(data,c.getLevel().registryAccess());
        c.assertTrue(inventory.getItem(26).is(Items.DIAMOND)&&inventory.getItem(26).getCount()==37,"Last slot and stack count survive old item format");
        var item=inventory.getItem(3);c.assertTrue(WandItem.materialOf(item)==WandItem.WandMaterial.COPPER&&WandItem.coreOf(item)==WandItem.WandCore.NETHER_STAR&&WandItem.crystalOf(item)==WandItem.WandCrystal.AMETHYST,"Legacy wand components survive data fixing");
        WandItem.refreshAppearance(item);inventory.setChanged();var restored=new StorageInventory(data,c.getLevel().registryAccess());
        c.assertTrue(ItemStack.matches(item,restored.getItem(3))&&restored.getItem(26).getCount()==37,"Modern storage codec roundtrip preserves stacks");c.succeed();
    }
    @GameTest(structure=ARENA)
    public void guardAndBossSaveLoad(GameTestHelper c) {
        var mage=new WanderingMageEntity(ModEntities.WANDERING_MAGE,c.getLevel());mage.configureForSpawn(3,false);mage.configureAsGuard(c.absoluteVec(new net.minecraft.world.phys.Vec3(8,2,8)),12);mage.setHealth(17);
        var output=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,c.getLevel().registryAccess());mage.save(output);
        var restored=new WanderingMageEntity(ModEntities.WANDERING_MAGE,c.getLevel());restored.load(TagValueInput.create(ProblemReporter.DISCARDING,c.getLevel().registryAccess(),output.buildResult()));
        c.assertTrue(restored.getCircle()==3&&restored.isGuarding()&&Math.abs(restored.getHealth()-17)<.01,"Guardian rank, post and vanilla health survive entity serialization");
        var master=new MasterEntity(ModEntities.MASTER,c.getLevel());CompoundTag boss=new CompoundTag();boss.putBoolean("phase2",true);boss.putInt("countdown",73);boss.putInt("meteorPhasesDone",2);master.readAdditionalSaveDataLegacy(boss);
        var bossOutput=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,c.getLevel().registryAccess());master.save(bossOutput);
        var copy=new MasterEntity(ModEntities.MASTER,c.getLevel());copy.load(TagValueInput.create(ProblemReporter.DISCARDING,c.getLevel().registryAccess(),bossOutput.buildResult()));CompoundTag saved=new CompoundTag();copy.addAdditionalSaveDataLegacy(saved);
        c.assertTrue(saved.getBooleanOr("phase2",false)&&saved.getIntOr("countdown",0)==73&&saved.getIntOr("meteorPhasesDone",0)==2,"Boss phase and attack timers survive save/load");c.succeed();
    }
    @GameTest(structure=ARENA)
    public void assemblyConsumesExactlyOnePerInput(GameTestHelper c) {
        var player=c.makeMockServerPlayerInLevel();var menu=new ArcaneStationScreenHandler(1,player.getInventory());
        menu.getSlot(0).set(new ItemStack(ModItems.ARCANE_GLUE,3));menu.getSlot(1).set(new ItemStack(Items.COPPER_INGOT,3));menu.getSlot(2).set(new ItemStack(Items.NETHER_STAR,3));menu.getSlot(3).set(new ItemStack(Items.AMETHYST_SHARD,3));
        var output=menu.getSlot(4).getItem().copy();c.assertTrue(output.is(ModItems.WAND),"Assembly input listeners create a wand result");
        menu.quickMoveStack(player,4);
        for(int i=0;i<4;i++)c.assertTrue(menu.getSlot(i).getItem().getCount()==2,"Shift-click consumes exactly one input in slot "+i);
        c.assertTrue(player.getInventory().contains(output),"Shift-click transfers the completed wand");c.succeed();
    }

    @GameTest(structure=ARENA,maxTicks=30)
    public void windVolleyAndMageInteraction(GameTestHelper c) {
        var level=c.getLevel(); var cow=net.minecraft.world.entity.EntityTypes.COW.create(level,net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        cow.setPos(c.absoluteVec(new net.minecraft.world.phys.Vec3(12,2,8)));cow.setNoAi(true);cow.setNoGravity(true);level.addFreshEntity(cow);
        var owner=c.makeMockServerPlayerInLevel();owner.setPos(c.absoluteVec(new net.minecraft.world.phys.Vec3(8,2,8)));owner.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);ManaPlayer.get(owner).setIntroStage(2);
        for(int i=0;i<5;i++){var blade=new WindBladeEntity(level,owner,1);blade.setPos(cow.position().add(-1,.7,0));blade.setDeltaMovement(.6,0,0);level.addFreshEntity(blade);}
        var mage=new WanderingMageEntity(ModEntities.WANDERING_MAGE,level);mage.configureForSpawn(1,false);mage.setPos(owner.position().add(0,0,2));mage.setNoAi(true);level.addFreshEntity(mage);
        owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.EMERALD,2));mage.interact(owner,net.minecraft.world.InteractionHand.MAIN_HAND,mage.position());
        c.assertTrue(owner.getMainHandItem().getCount()==1&&owner.getInventory().contains(new ItemStack(ModItems.MANA_VIAL)),"Peaceful mage trades one emerald for one vial");
        mage.setHealth(4);mage.hurtServer(level,level.damageSources().playerAttack(owner),1);
        c.assertTrue(mage.isRetreating()&&mage.getTarget()==null,"Wounded peaceful mage yields and clears aggression");
        c.runAfterDelay(5,()->{c.assertTrue(Math.abs(cow.getHealth()-5)<.01,"All five wind blades register their damage");c.succeed();});
    }
}

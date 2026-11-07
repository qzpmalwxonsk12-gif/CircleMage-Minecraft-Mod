package com.example.circlemage.mixin;

import com.example.circlemage.mana.ManaData;
import com.example.circlemage.mana.ManaPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerEntityMixin implements ManaPlayer {

    @Unique
    private final ManaData circlemage$manaData = new ManaData();

    @Override
    public ManaData circlemage$getManaData() {
        return circlemage$manaData;
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void circlemage$writeData(net.minecraft.world.level.storage.ValueOutput nbt, CallbackInfo ci) {
        CompoundTag tag = new CompoundTag();
        circlemage$manaData.writeNbt(tag);
        nbt.store("CircleMage", CompoundTag.CODEC, tag);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void circlemage$readData(net.minecraft.world.level.storage.ValueInput nbt, CallbackInfo ci) {
        nbt.read("CircleMage", CompoundTag.CODEC).ifPresent(circlemage$manaData::readNbt);
    }
}

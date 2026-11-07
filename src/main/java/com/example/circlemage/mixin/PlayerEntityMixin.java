package com.example.circlemage.mixin;

import com.example.circlemage.mana.ManaData;
import com.example.circlemage.mana.ManaPlayer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin implements ManaPlayer {

    @Unique
    private final ManaData circlemage$manaData = new ManaData();

    @Override
    public ManaData circlemage$getManaData() {
        return circlemage$manaData;
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void circlemage$writeData(NbtCompound nbt, CallbackInfo ci) {
        NbtCompound tag = new NbtCompound();
        circlemage$manaData.writeNbt(tag);
        nbt.put("CircleMage", tag);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void circlemage$readData(NbtCompound nbt, CallbackInfo ci) {
        if (nbt.contains("CircleMage", NbtCompound.COMPOUND_TYPE)) {
            circlemage$manaData.readNbt(nbt.getCompound("CircleMage"));
        }
    }
}

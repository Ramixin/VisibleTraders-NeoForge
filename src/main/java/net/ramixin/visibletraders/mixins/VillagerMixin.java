package net.ramixin.visibletraders.mixins;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ReputationEventHandler;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerDataHolder;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.ramixin.visibletraders.LockedTradeData;
import net.ramixin.visibletraders.ducks.VillagerDuck;
import org.apache.commons.lang3.mutable.Mutable;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Mixin(Villager.class)
public abstract class VillagerMixin extends AbstractVillager implements ReputationEventHandler, VillagerDataHolder, VillagerDuck {

    @Shadow public abstract @NotNull VillagerData getVillagerData();

    @Unique
    private final Mutable<LockedTradeData> visibleTraders$lockedTradeData = new MutableObject<>();

    public VillagerMixin(EntityType<? extends AbstractVillager> entityType, Level level) {
        super(entityType, level);
    }

    @Unique
    private void visibleTraders$ifPresent(Consumer<LockedTradeData> consumer) {
        LockedTradeData val = visibleTraders$lockedTradeData.getValue();
        if(val == null) return;
        consumer.accept(val);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("HEAD"))
    private void saveLockedTradeData(ValueOutput valueOutput, CallbackInfo ci) {
        visibleTraders$ifPresent(data -> data.write(valueOutput));
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void readLockedTradeData(ValueInput valueInput, CallbackInfo ci) {
        visibleTraders$lockedTradeData.setValue(new LockedTradeData(valueInput));
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void removeLockedTradeDataIfNoOffers(CallbackInfo ci) {
        if(this.offers == null)
            this.visibleTraders$lockedTradeData.setValue(null);
        visibleTraders$ifPresent(data -> data.tick((Villager) (Object) this, this::visibleTraders$appendLockedOffer));
    }

    @Inject(method = "updateTrades", at = @At("HEAD"), cancellable = true)
    private void preventAdditionalTradesOnRankIncrease(CallbackInfo ci) {
        if(this.offers == null || this.offers.isEmpty()) {
            this.visibleTraders$lockedTradeData.setValue(null);
            return;
        }
        if(visibleTraders$appendLockedOffer()) ci.cancel();
    }

    @Unique
    private boolean visibleTraders$appendLockedOffer() {
        if(this.offers == null) return false;
        AtomicBoolean result = new AtomicBoolean(false);
        visibleTraders$ifPresent(data -> {
            MerchantOffers dismissedTrades = data.popTradeSet();
            if(dismissedTrades != null) {
                this.offers.addAll(dismissedTrades);
                result.set(true);
            }
        });
        return result.get();
    }

    @Override
    public void visibleTraders$setLockedTradeData(LockedTradeData data) {
        this.visibleTraders$lockedTradeData.setValue(data);
    }

    @Override
    public Optional<LockedTradeData> visibleTraders$getLockedTradeData() {
        return Optional.ofNullable(visibleTraders$lockedTradeData.getValue());
    }

    @Override
    public void visibleTrades$regenerateTrades() {
        this.visibleTraders$lockedTradeData.setValue(new LockedTradeData((Villager) (Object) this));
    }

    @Override
    public int visibleTraders$getShiftedLevel() {
        int level = getVillagerData().level();
        if(this.offers == null) return level;
        return level | (this.offers.size() << 8);
    }

    @Override
    public MerchantOffers visibleTraders$getCombinedOffers() {
        MerchantOffers offers = new MerchantOffers();
        offers.addAll(this.offers);
        if(visibleTraders$lockedTradeData.getValue() == null)
            visibleTrades$regenerateTrades();
        visibleTraders$ifPresent(data -> offers.addAll(data.buildLockedOffers()));
        return offers;
    }
}

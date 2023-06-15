package funwayguy.bdsandm.utils;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;

import javax.annotation.Nonnull;

public class ItemStackUtils {
    public static<T extends Capability<?>> boolean is(@Nonnull ItemStack self, @Nonnull T capability) {
        return self.hasCapability(capability, null);
    }
    public static boolean isContainer(@Nonnull ItemStack self) {
        return is(self, CapabilityItemHandler.ITEM_HANDLER_CAPABILITY)
                || is(self, CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY)
                || is(self, CapabilityEnergy.ENERGY)
                ;
    }

    public static boolean isItemHandler(@Nonnull ItemStack self) {
        return is(self, CapabilityItemHandler.ITEM_HANDLER_CAPABILITY);
    }

    public static boolean isFluidHandler(@Nonnull ItemStack self) {
       return is(self, CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY);
    }
}

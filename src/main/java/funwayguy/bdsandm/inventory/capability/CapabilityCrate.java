package funwayguy.bdsandm.inventory.capability;

import funwayguy.bdsandm.core.BDSM;
import funwayguy.bdsandm.core.BdsmConfig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.OreIngredient;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CapabilityCrate implements ICrate
{

    /**
     * The reference item to be stored in this crate.
     * Should have the count property set to a of 1, since the crate item count is stored in count
     * @see CapabilityCrate#getRefItem
     * @see CapabilityCrate#insertItem(int, ItemStack, boolean)
     */
    private ItemStack refStack = ItemStack.EMPTY;

    /**
     * The ore dictionary mappings.
     * Should be optimized and only used when needed.
     * Likely deferred as well since constantly checking this can lead to hanging up.
     * Work could be done in order to permanently cache this into an NBT array.
     * Simplification of the data being retained also could help in reducing the impacts of accessing this array.
     */
    private final List<OreIngredient> cachedOres = new ArrayList<>();

    /**
     * The maximum amount of stacks that can be fit inside this crate after upgrades are applied.
     */
    private int maxStackCapacity;
    private boolean oreDict = false;
    private boolean lock = false;
    private boolean overflow = false;
    private int[] colors = new int[]{0xFFFFFFFF, 0xFFFFFFFF};


    /**
     *  How many item stacks can fit on this crate
     */
    private int stackCapacity;

    /**
     * The current item count
     */

    // TODO: Sort out the madness that's happening here.
    //       The assignments to this makes no sense and it really is a wonder that this is working.
    private int count = 0;

    /**
     * A callback used to trigger an event once a crate is modified.
     * Called on the start of the crate synchronization.
     * @see ICrateCallback#onCrateChanged()
     * @see CapabilityCrate#syncContainer()
     */
    private ICrateCallback callback;
    
    public CapabilityCrate(int initCap, int maxStackCap)
    {
        this.stackCapacity = initCap;
        this.maxStackCapacity = maxStackCap;
    }
    
    @Override
    public int getColorCount()
    {
        return colors.length;
    }
    
    @Override
    public int[] getColors()
    {
        return colors;
    }
    
    @Override
    public void setColors(int[] c)
    {
        for(int i = 0; i < c.length && i < colors.length; i++)
        {
            colors[i] = c[i];
        }
    }
    
    /**
     * The ItemStack being stored on this crate.
     * It's count should always be equals to 1.
     * The original javadocs said this was "Read-Only" although there is no guarantees that
     * this contract was ever followed by the mod.
     **/
    @Nonnull
    @Override
    public ItemStack getRefItem()
    {
        return this.refStack;
    }
    
    @Override
    public boolean isLocked()
    {
        return this.lock;
    }
    
    @Override
    public void setLocked(boolean state)
    {
        this.lock = state;
    }
    
    @Override
    public boolean voidOverflow()
    {
        return overflow;
    }
    
    @Override
    public void setVoidOverflow(boolean state)
    {
        this.overflow = state;
    }
    
    @Override
    public boolean canMergeWith(ItemStack stack)
    {
        // You can always merge with an empty stack
        // With this change `canMergeWith` is call order-independent.
        // It was previously needed that the refStack was set to something prior
        // to calling this function, since Empty != Any and it would return false.

        if (refStack == ItemStack.EMPTY) return true;

        boolean nonOreCheck =
                ItemStack.areItemsEqual(refStack, stack) && ItemStack.areItemStackTagsEqual(refStack, stack);

        // Checking the ore dictionary is more expensive than doing ItemStack comparisons.
        // Only run ore dictionary checks if the simplest checks failed.
        if (!nonOreCheck && oreDict) {
            for (OreIngredient ore : cachedOres) {
                if(ore.apply(stack)) return true;
            }
        }

        return nonOreCheck;
    }

    @Override
    public CapabilityCrate setCallback(ICrateCallback callback)
    {
        this.callback = callback;
        return this;
    }


    /**
     * All this function seems to do is synchronize slotRef with refStack
     * or updates the count of the stack to be the same as the crate's count
     * and sends a event to the callback.
     * A sync is always called unless the item transfer is simulated.
     * @see CapabilityCrate#slotRef
     * @see CapabilityCrate#refStack
     */
    @Override
    public void syncContainer()
    {
        if(callback != null)
        {
            callback.onCrateChanged();
        }
        
        if(refStack.isEmpty())
        {
            slotRef = ItemStack.EMPTY;
        } else
        {
            if(!slotRef.isEmpty() && canMergeWith(slotRef))
            {
                slotRef.setCount(getCount());
            } else
            {
                slotRef = refStack.copy();
                slotRef.setCount(getCount());
            }
        }
    }

    /**
     * @return The current stack capacity.
     */
    @Override
    public int getStackCap()
    {
        return this.stackCapacity;
    }

    /**
     * @return The maximum stack capacity after applying all upgrades.
     */
    @Override
    public int getUpgradeCap()
    {
        return this.maxStackCapacity;
    }
    
    @Override
    public boolean isOreDict()
    {
        return this.oreDict;
    }
    
    @Override
    public void enableOreDict(boolean state)
    {
        this.oreDict = state;
    }
    
    @Override
    public void setStackCap(int value)
    {
        this.stackCapacity = value;
    }

    /**
     * @return The current item count, if the barrel doesn't have a Creative Upgrade.
     * Or the creative item count, if the barrel has a Creative Upgrade.
     */
    @Override
    public int getCount()
    {
        return stackCapacity < 0 ? ((1 << 15) * refStack.getMaxStackSize()) : this.count;
    }

    /**
     * Is set to 2 for unknown reasons.
     * A comment says there is something to deal with Ore Dictionary handling.
     * @return Always returns 2.
     */

    @Override
    public int getSlots()
    {
        return 2;//(oreDict || overflow) ? 2 : 1;
    }

    /**
     * As far as I was able to search, this represents some "internal state" of the crate
     * that's assigned at NBT deserialization and updated at container synchronization
     * to keep its internal count similar to the crate's count.
     * Has 13 usages, and they're all internal to this class.
     * Exposed only via getStackInSlot, which makes me believe that the main use of this
     * is to fake the Inventory API.
     * We can treat this as a "ghost" ItemStack that duplicates data.
     * Data duplication is bad but that's the least of our concerns.
     * @see CapabilityCrate#refStack
     * @see CapabilityCrate#syncContainer()
     * @see CapabilityCrate#deserializeNBT(NBTTagCompound)
     * @see CapabilityCrate#getStackInSlot(int)
     */
    private ItemStack slotRef = ItemStack.EMPTY;

    /**
     * This function is defined by Forge not the mod.
     * This really is the only case where slotRef is exposed. Everything else uses refStack.
     * The most strange part of this is there's a lot of code around on this codebase that
     * basically takes refStack, clones it and sets the clone's count to the crate's count
     * (which was supposed to be slotRef's whole purpose????).
     * @param slot Slot to query
     * @return slotRef, if slot is equals to zero.
     * ItemStack.EMPTY otherwise.
     * @see CapabilityCrate#slotRef
     */
    @Nonnull
    @Override
    public ItemStack getStackInSlot(int slot)
    {
        if(slot != 0 || refStack.isEmpty())
        {
            return ItemStack.EMPTY;
        }
        
        return slotRef;
    }
    
    @Nonnull
    @Override
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate)
    {
        if(slot < 0 || slot >= 2 || stack.isEmpty() || BdsmConfig.isBlacklisted(stack))
        {
            return stack;
        } else if(refStack.isEmpty() || (stackCapacity < 0 && !lock))
        {
            if(lock || stack.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null) || stack.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null) || stack.hasCapability(CapabilityEnergy.ENERGY, null))
            {
                return stack; // BANNED! Nested containers are not permitted!
            } else if(!simulate)
            {
                refStack = stack.copy();
                count = Math.min(stack.getCount(), (stackCapacity < 0 ? (1 << 15) : stackCapacity) * stack.getMaxStackSize());
                refStack.setCount(1);
                
                cachedOres.clear();
                int[] aryIDs = OreDictionary.getOreIDs(refStack);
                topLoop:
                for(int id : aryIDs)
                {
                    String name = OreDictionary.getOreName(id);
                    for(String bl : BdsmConfig.oreDictBlacklist)
                    {
                        if(name.matches(bl)) continue topLoop;
                    }
                    cachedOres.add(new OreIngredient(name));
                }
                
                syncContainer();
            }
            
            int used = Math.min(stack.getCount(), (stackCapacity < 0 ? (1 << 15) : stackCapacity) * stack.getMaxStackSize());
            if(used > stack.getCount()) return ItemStack.EMPTY;
            ItemStack rStack = stack.copy();
            rStack.shrink(used);
            return rStack;
        } else if(!canMergeWith(stack))
        {
            return stack;
        }
        
        long rem = stackCapacity < 0 ? 0 : (long)stackCapacity * (long)refStack.getMaxStackSize() - getCount();
        int add = (int)Math.min(rem, stack.getCount());
        if(add < 0) add = 0;
        
        ItemStack copy = stack.copy();
        copy.setCount(overflow ? 0 : stack.getCount() - add);
        
        if(!simulate && add != 0)
        {
            count += add;
            syncContainer();
        }
        
        return copy;
    }
    
    @Nonnull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate)
    {
        if(slot != 0 || refStack.isEmpty())
        {
            return ItemStack.EMPTY;
        } else if(stackCapacity < 0)
        {
            ItemStack copy = refStack.copy();
            copy.setCount(amount);
            return copy;
        }
        
        ItemStack copy = refStack.copy();
        copy.setCount(Math.min(amount, getCount()));
        
        if(!simulate)
        {
            count -= copy.getCount();
            
            if(count <= 0 && !lock)
            {
                refStack = ItemStack.EMPTY;
                cachedOres.clear();
            }
            
            syncContainer();
        }
        
        return copy;
    }


    /**
     * @param slot Slot to query.
     * @return Integer.MAX_VALUE if the "Overflow Upgrade" is installed.
     * 64 if there is no item currently inserted.
     * stackCapacity * item's stack size if some item is inserted.
     */
    @Override
    public int getSlotLimit(int slot)
    {
        return overflow ? Integer.MAX_VALUE : (refStack.isEmpty() ? 64 : refStack.getMaxStackSize()) * stackCapacity;
    }
    
    @Override
    public void copyContainer(IStackContainer crate)
    {
        this.deserializeNBT(crate.serializeNBT());
    }
    
    @Override
    public boolean installUpgrade(@Nonnull EntityPlayer player, @Nonnull ItemStack stack)
    {
        if(stack.isEmpty()) return false;
        
        if(stack.getItem() == BDSM.itemUpgrade)
        {
            if(stack.getItemDamage() >= 0 && stack.getItemDamage() < 4) // Capacity upgrade
            {
                int value = 64 << (stack.getItemDamage() * 2);
                int remCap = getUpgradeCap() - getStackCap();
                
                if(remCap > 0) // Upgrades are now lossy if not exact
                {
                    setStackCap(getStackCap() + Math.min(value, remCap));
                    syncContainer();
                    
                    if(!player.capabilities.isCreativeMode)
                    {
                        stack.shrink(1);
                        player.inventory.markDirty();
                    }
                    
                    return true;
                }
                
                return false;
                
            } else if(stack.getItemDamage() == 4) // Creative upgrade
            {
                if(getStackCap() >= 0)
                {
                    setStackCap(-1);
                    count = 1 << 15;
                    syncContainer();
                    
                    if(!player.capabilities.isCreativeMode)
                    {
                        stack.shrink(1);
                        player.inventory.markDirty();
                    }
                    
                    return true;
                }
                
                return false;
            } else if(stack.getItemDamage() == 5) // Ore Dict upgrade
            {
                if(!oreDict)
                {
                    oreDict = true;
                    syncContainer();
                    
                    if(!player.capabilities.isCreativeMode)
                    {
                        stack.shrink(1);
                        player.inventory.markDirty();
                    }
                    
                    return true;
                }
                
                return false;
            } else if(stack.getItemDamage() == 6) // Void upgrade
            {
                if(!overflow)
                {
                    overflow = true;
                    syncContainer();
                    
                    if(!player.capabilities.isCreativeMode)
                    {
                        stack.shrink(1);
                        player.inventory.markDirty();
                    }
                    
                    return true;
                }
                
                return false;
            } else if(stack.getItemDamage() == 7) // Upgrade Reset
            {
                if(count <= 0 || stackCapacity < 0)
                {
                    // We're not going to refund creative players. They can just spawn more whenever
                    if(!player.capabilities.isCreativeMode && getStackCap() > 64)
                    {
                        int rem = getStackCap() - 64;
        
                        while(rem >= 64)
                        {
                            ItemStack drop = new ItemStack(BDSM.itemUpgrade, 1, 0);
            
                            if(rem >= 4096)
                            {
                                drop.setItemDamage(3);
                            } else if(rem >= 1024)
                            {
                                drop.setItemDamage(2);
                            } else if(rem >= 256)
                            {
                                drop.setItemDamage(1);
                            }
            
                            rem -= 64 << (drop.getItemDamage() * 2);
            
                            if(!player.addItemStackToInventory(drop)) player.dropItem(drop, true, false);
                        }
                    }
                    
                    if(stackCapacity < 0) count = 0; // Must be reset in the event of a creative upgrade (which modifies the underlying value at times)
                    setStackCap(64); // Also erases creative upgrade (which we're not refunding)
                    if(!lock) refStack = ItemStack.EMPTY;
                }
                
                if(oreDict)
                {
                    if(!player.capabilities.isCreativeMode)
                    {
                        ItemStack drop = new ItemStack(BDSM.itemUpgrade, 1, 5);
                        if(!player.addItemStackToInventory(drop)) player.dropItem(drop, true, false);
                    }
                    oreDict = false;
                }
                
                if(overflow)
                {
                    if(!player.capabilities.isCreativeMode)
                    {
                        ItemStack drop = new ItemStack(BDSM.itemUpgrade, 1, 6);
                        if(!player.addItemStackToInventory(drop)) player.dropItem(drop, true, false);
                    }
                    overflow = false;
                }
                
                syncContainer();
                return true;
            }
        } else if(stack.getItem() == BDSM.itemKey)
        {
            lock = !lock;
            
            if(!lock && getCount() <= 0)
            {
                refStack = ItemStack.EMPTY;
                cachedOres.clear();
            }
            
            syncContainer();
            
            return true;
        }
        
        return false;
    }
    
    @Override
    public NBTTagCompound serializeNBT()
    {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setTag("refStack", refStack.writeToNBT(new NBTTagCompound()));
        nbt.setInteger("count", count);
        nbt.setInteger("stackCap", stackCapacity);
        nbt.setInteger("maxCap", maxStackCapacity);
        nbt.setBoolean("oreDict", oreDict);
        nbt.setBoolean("overflow", overflow);
        nbt.setBoolean("locked", lock);
        nbt.setIntArray("objColors", colors);
        return nbt;
    }
    
    @Override
    public void deserializeNBT(NBTTagCompound nbt)
    {
        refStack = new ItemStack(nbt.getCompoundTag("refStack"));
        count = nbt.getInteger("count");
        stackCapacity = nbt.getInteger("stackCap");
        maxStackCapacity = nbt.getInteger("maxCap");
        oreDict = nbt.getBoolean("oreDict");
        overflow = nbt.getBoolean("overflow");
        lock = nbt.getBoolean("locked");
        colors = Arrays.copyOf(nbt.getIntArray("objColors"), colors.length);
        
        if(!refStack.isEmpty())
        {
            cachedOres.clear();
            int[] aryIDs = OreDictionary.getOreIDs(refStack);
            topLoop:
            for(int id : aryIDs)
            {
                String name = OreDictionary.getOreName(id);
                for(String bl : BdsmConfig.oreDictBlacklist)
                {
                    if(name.matches(bl)) continue topLoop;
                }
                cachedOres.add(new OreIngredient(name));
            }
            
            if(!slotRef.isEmpty() && canMergeWith(slotRef))
            {
                slotRef.setCount(getCount());
            } else
            {
                slotRef = refStack.copy();
                slotRef.setCount(getCount());
            }
        } else
        {
            slotRef = ItemStack.EMPTY;
        }
    }
}

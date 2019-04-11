package com.gmail.woodyc40.instantskull;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.GameProfileSerializer;
import net.minecraft.server.v1_8_R3.IInventory;
import net.minecraft.server.v1_8_R3.NBTTagCompound;
import net.minecraft.server.v1_8_R3.TileEntitySkull;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftInventory;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class InstantSkull extends JavaPlugin implements Listener {
    // Cache used to store retrieved skulls
    private final Cache<String, ItemStack> skullCache =
            CacheBuilder.newBuilder()
                    .expireAfterWrite(1, TimeUnit.HOURS)
                    .concurrencyLevel(1)
                    .maximumSize(500)
                    .build();
    // Inventories opened from /instantskull
    private final Set<Inventory> skullInventories = new HashSet<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        this.getCommand("instantskull").setExecutor(this);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        // Prevent players from putting or taking items
        // from inventories opened through /instantskull
        Inventory inv = event.getInventory();
        if (this.skullInventories.contains(inv)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        this.skullInventories.remove(event.getInventory());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("instantskull.command")) {
            sender.sendMessage("No permission!");
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("You are not a player!");
            return true;
        }

        if (args.length != 1) {
            return false;
        }

        Inventory inventory = this.createSkullInv(args[0]);
        if (inventory == null) {
            sender.sendMessage(String.format("Something went wrong retrieving %s's skull, check console", args[0]));
            return true;
        }

        Player player = (Player) sender;
        player.openInventory(inventory);

        this.skullInventories.add(inventory);
        return true;
    }

    /**
     * Creates a new inventory containing a skull in the 4th
     * slot with the skin for the given skull owner.
     *
     * @param skullOwner the player whose texture will be
     *                   used for the skull
     * @return the new inventory
     */
    private Inventory createSkullInv(String skullOwner) {
        Inventory inventory = Bukkit.createInventory(null, 9, String.format("%s's skull", skullOwner));

        try {
            ItemStack item = this.skullCache.get(skullOwner, () -> getPlayerSkull(skullOwner));
            inventory.setItem(4, item);
        } catch (ExecutionException e) {
            this.getLogger().log(Level.WARNING, String.format("Encountered error retrieving %s's skull", skullOwner), e);
            return null;
        }

        // Ensure that the skull has actually been loaded
        ensureSkullTextures(inventory, 4);

        return inventory;
    }

    /**
     * Obtains a player skull for the given player name,
     * eagerly querying the server to load the texture data.
     *
     * @param playerName the name of the player which to
     *                   obtain the skull
     * @return the {@link ItemStack} representing the skull
     */
    private static ItemStack getPlayerSkull(String playerName) {
        ItemStack craftItem = CraftItemStack.asCraftCopy(new ItemStack(Material.SKULL_ITEM, 1, (byte) 3));
        SkullMeta meta = (SkullMeta) craftItem.getItemMeta();
        meta.setOwner(playerName);
        craftItem.setItemMeta(meta);

        return craftItem;
    }

    /**
     * Ensures that the skull placed into the inventory has
     * the skull textures filled.
     *
     * <p>If this method determines that the skull has no
     * textures, it will perform a query to retrieve and
     * update the skull once the data has been filled.</p>
     *
     * <p>This is only needed if the slot will not be
     * updated by an existing query. This is a complement to
     * {@link #getPlayerSkull(String)} because placing the
     * item into an inventory causes a clone to be created,
     * which will prevent the skull from being updated in
     * the inventory if it doesn't already have the
     * textures.</p>
     *
     * @param inv the inventory that the skull is placed in
     * @param slot the slot the skull occupies
     * @throws IllegalArgumentException if the skull doesn't
     * have an NBT tag indicating that it should have a
     * texture
     */
    private static void ensureSkullTextures(Inventory inv, int slot) {
        IInventory iinv = ((CraftInventory) inv).getInventory();
        net.minecraft.server.v1_8_R3.ItemStack item = iinv.getItem(slot);
        NBTTagCompound tag = item.getTag();

        NBTTagCompound skullOwner = (NBTTagCompound) tag.get("SkullOwner");
        if (skullOwner == null) {
            throw new IllegalArgumentException("No SkullOwner NBT tag");
        }

        GameProfile profile = GameProfileSerializer.deserialize(skullOwner);
        if (!profile.getProperties().containsKey("textures")) {
            TileEntitySkull.b(profile, input -> {
                NBTTagCompound owner = new NBTTagCompound();
                GameProfileSerializer.serialize(owner, input);
                tag.set("SkullOwner", owner);

                updateSlot(inv, slot, item);
                return false;
            });
        }
    }

    /**
     * Causes an item at a given slot in the given inventory
     * to be updated to the given item.
     *
     * @param inventory the inventory to update
     * @param slot the slot to update
     * @param item the item which the inventory should
     *             place into the slot
     */
    private static void updateSlot(Inventory inventory, int slot, net.minecraft.server.v1_8_R3.ItemStack item) {
        for (HumanEntity viewer : inventory.getViewers()) {
            Player player = (Player) viewer;
            EntityPlayer ep = ((CraftPlayer) player).getHandle();
            ep.a(ep.activeContainer, slot, item);
        }
    }
}

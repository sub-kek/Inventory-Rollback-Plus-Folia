package me.danjono.inventoryrollback.listeners;

import com.nuclyon.technicallycoded.inventoryrollback.InventoryRollbackPlus;
import com.nuclyon.technicallycoded.inventoryrollback.nms.EnumNmsVersion;
import me.danjono.inventoryrollback.config.ConfigData;
import me.danjono.inventoryrollback.data.LogType;
import me.danjono.inventoryrollback.inventory.SaveInventory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.BlockProjectileSource;
import org.bukkit.projectiles.ProjectileSource;

import java.util.UUID;

public class EventLogs implements Listener {

	private InventoryRollbackPlus main;

	public EventLogs() {
		this.main = InventoryRollbackPlus.getInstance();
	}

	@EventHandler
	private void playerJoin(PlayerJoinEvent e) {
		if (!ConfigData.isEnabled()) return;

		Player player = e.getPlayer();
		if (player.hasPermission("inventoryrollbackplus.joinsave")) {
			new SaveInventory(e.getPlayer(), LogType.JOIN, null, null, player.getInventory(), player.getEnderChest()).createSave(true);
		}
		if (player.hasPermission("inventoryrollbackplus.adminalerts")) {
			// can send info to admins here
		}
	}

	@EventHandler
	private void playerQuit(PlayerQuitEvent e) {
		if (!ConfigData.isEnabled()) return;

		Player player = e.getPlayer();

		if (player.hasPermission("inventoryrollbackplus.leavesave")) {
			new SaveInventory(e.getPlayer(), LogType.QUIT, null, null, player.getInventory(), player.getEnderChest()).createSave(true);
		}

		UUID uuid = player.getUniqueId();

		// Run the cleanup 1 tick later in case the rate limiter should need to provide debug data.
		// If the cleanup would run and the event is being spammed, this cleanup would delete the rate limiter's data
		// before it has a chance to act.
		main.getServer().getGlobalRegionScheduler().runDelayed(main, t -> {
			// Double check that the player is offline
			if (main.getServer().getPlayer(uuid) != null) return;
			// Cleanup the player's data
			SaveInventory.cleanup(uuid);
		}, 1);
	}

	/**
	 * Handle saving the player's inventory on death.
	 * @param event Bukkit damage event
	 */
    @EventHandler(priority = EventPriority.MONITOR)
    private void playerDeath(PlayerDeathEvent event) {
        // Sanity checks to prevent unwanted saves
        if (!ConfigData.isEnabled()) return;

        Player player = event.getEntity();

        // Check that the player has the permission for inventory saves
        if (player.hasPermission("inventoryrollbackplus.deathsave")) {

            EntityDamageEvent damageEvent = event.getEntity().getLastDamageCause();
            EntityDamageEvent.DamageCause damageCause;

            if (damageEvent == null) damageCause = EntityDamageEvent.DamageCause.CUSTOM;
            else damageCause = damageEvent.getCause();

            // Detailed reason for the death that can be applied given certain conditions
            String reason = null;

            // Handler the case where the death is caused by an entity
            if (isEntityCause(damageCause) && damageEvent instanceof EntityDamageByEntityEvent) {
                EntityDamageByEntityEvent damageByEntityEvent = (EntityDamageByEntityEvent) damageEvent;
                Entity damager = damageByEntityEvent.getDamager();

                // Get the shooter's name if the killing entity is a projectile
                String shooterName = "";
                if (damager instanceof Projectile) {

                    Projectile proj = (Projectile) damager;
                    ProjectileSource shooter = proj.getShooter();

                    // Show shooter name if it's a living entity
                    if (shooter instanceof LivingEntity) {
                        LivingEntity shooterEntity = (LivingEntity) shooter;
                        shooterName = ", " + shooterEntity.getName();
                    }
                    // Show shooter block type if it's a block projectile source
                    else if (shooter instanceof BlockProjectileSource) {
                        BlockProjectileSource shooterBlock = (BlockProjectileSource) shooter;
                        shooterName = ", " + shooterBlock.getBlock().getType().name();

                    }
                    // In all other cases, don't show projectile detailed shooter info
                }

                // Create a more specific reason given the data above
                reason = damageCause.name() + " (" + damageByEntityEvent.getDamager().getName() + shooterName + ")";
            }

            // After all checks, create the save with data provided above
            new SaveInventory(player, LogType.DEATH, damageCause, reason, player.getInventory(), player.getEnderChest()).createSave(true);
        }
    }

	@EventHandler
	private void playerChangeWorld(PlayerChangedWorldEvent e) {
		if (!ConfigData.isEnabled()) return;

		Player player = e.getPlayer();

		if (player.hasPermission("inventoryrollbackplus.worldchangesave")) {
			new SaveInventory(e.getPlayer(), LogType.WORLD_CHANGE, null, null, player.getInventory(), player.getEnderChest()).createSave(true);
		}
	}

	public boolean isEntityCause(EntityDamageEvent.DamageCause cause) {
		if (cause.equals(EntityDamageEvent.DamageCause.ENTITY_ATTACK) ||
				cause.equals(EntityDamageEvent.DamageCause.PROJECTILE)) return true;
		if (this.main.getVersion().isAtLeast(EnumNmsVersion.v1_11_R1)) {
			if (cause.equals(EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)) return true;
		}
		return false;
	}

}

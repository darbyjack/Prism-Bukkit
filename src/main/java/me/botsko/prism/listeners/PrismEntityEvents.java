package me.botsko.prism.listeners;

import me.botsko.prism.utils.DeathUtils;
import me.botsko.prism.utils.InventoryUtils;
import me.botsko.prism.utils.MaterialTag;
import me.botsko.prism.Prism;
import me.botsko.prism.actionlibs.ActionFactory;
import me.botsko.prism.actionlibs.RecordingQueue;
import me.botsko.prism.utils.BlockUtils;
import me.botsko.prism.utils.MiscUtils;
import me.botsko.prism.utils.WandUtils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Wither;
import org.bukkit.entity.minecart.PoweredMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingBreakEvent.RemoveCause;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerUnleashEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.Door;
import org.bukkit.potion.PotionEffect;
import org.bukkit.projectiles.BlockProjectileSource;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.Locale;
import java.util.UUID;

public class PrismEntityEvents implements Listener {

	/**
	 *
	 */
	private final Prism plugin;

	/**
	 * 
	 * @param plugin
	 */
	public PrismEntityEvents(Prism plugin) {
		this.plugin = plugin;
	}

	/**
	 * 
	 * @param event
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onEntityDamageEvent(final EntityDamageByEntityEvent event) {

		if (!(event.getDamager() instanceof Player))
			return;

		final Entity entity = event.getEntity();
		final Player player = (Player) event.getDamager();

		// Cancel the event if a wand is in use
		if (WandUtils.playerUsesWandOnClick(player, entity.getLocation())) {
			event.setCancelled(true);
			return;
		}

		if (entity instanceof ItemFrame) {
			final ItemFrame frame = (ItemFrame) event.getEntity();
			// Frame is empty but an item is held
			if (!frame.getItem().getType().equals(Material.AIR)) {
				if (Prism.getIgnore().event("item-remove", player)) {
					RecordingQueue.addToQueue(ActionFactory.createItemStack("item-remove", frame.getItem(), 1, frame.getAttachedFace(), null,
							entity.getLocation(), player));
				}
			}
		}
	}

	/**
	 * 
	 * @param event
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onEntityDeath(final EntityDeathEvent event) {
		final Entity entity = event.getEntity();

		// Mob Death
		if (!(entity instanceof Player)) {
			// Log item drops
			if (Prism.getIgnore().event("item-drop", entity.getWorld())) {
				String name = entity.getType().name().toLowerCase();

				// Inventory
				if (entity instanceof InventoryHolder) {
					final InventoryHolder holder = (InventoryHolder) entity;

					for (final ItemStack i : holder.getInventory().getContents()) {
						if (i != null) {
							RecordingQueue.addToQueue(ActionFactory.createItemStack("item-drop", i, i.getAmount(), -1,
									null, entity.getLocation(), name));
						}
					}
				}

				// Equipment
				if (entity instanceof LivingEntity) {
					final LivingEntity living = (LivingEntity) entity;

					for (final ItemStack i : living.getEquipment().getArmorContents()) {
						if (i != null) {
							RecordingQueue.addToQueue(ActionFactory.createItemStack("item-drop", i, i.getAmount(), -1,
									null, entity.getLocation(), name));
						}
					}

					// Hand items not stored in "getArmorContents"
					ItemStack main = living.getEquipment().getItemInMainHand();
					ItemStack off = living.getEquipment().getItemInOffHand();

					if (main != null) {
						RecordingQueue.addToQueue(ActionFactory.createItemStack("item-drop", main, main.getAmount(), -1,
								null, entity.getLocation(), name));
					}

					if (off != null) {
						RecordingQueue.addToQueue(ActionFactory.createItemStack("item-drop", off, off.getAmount(), -1,
								null, entity.getLocation(), name));
					}
				}
			}

			EntityDamageEvent damageEvent = entity.getLastDamageCause();

			Entity entitySource = null;
			Block blockSource = null;

			// Resolve source
			if (!damageEvent.isCancelled()) {
				if (damageEvent instanceof EntityDamageByEntityEvent) {
					entitySource = ((EntityDamageByEntityEvent) damageEvent).getDamager();

					if (entitySource instanceof Projectile) {
						ProjectileSource ps = ((Projectile) entitySource).getShooter();

						if (ps instanceof BlockProjectileSource) {
							entitySource = null;
							blockSource = ((BlockProjectileSource) ps).getBlock();
						}
						else {
							entitySource = (Entity) ps;
						}
					}
				}
				else if (damageEvent instanceof EntityDamageByBlockEvent) {
					blockSource = ((EntityDamageByBlockEvent) damageEvent).getDamager();
				}
			}

			// Create handlers
			if (entitySource instanceof Player) {
				Player player = (Player) entitySource;

				if (!Prism.getIgnore().event("player-kill", player))
					return;

				RecordingQueue.addToQueue(ActionFactory.createEntity("player-kill", entity, player));
			}
			else if (entitySource != null) {
				if (!Prism.getIgnore().event("entity-kill", entity.getWorld()))
					return;

				String name = entitySource.getType().name().toLowerCase(Locale.ENGLISH).replace('_', ' ');
				RecordingQueue.addToQueue(ActionFactory.createEntity("entity-kill", entity, name));
			}
			else if (blockSource != null) {
				if (!Prism.getIgnore().event("entity-kill", entity.getWorld()))
					return;

				String name = "block:" + blockSource.getType().name().toLowerCase(Locale.ENGLISH).replace('_', ' ');
				RecordingQueue.addToQueue(ActionFactory.createEntity("entity-kill", entity, name));
			}
			else {
				if (!Prism.getIgnore().event("entity-kill", entity.getWorld()))
					return;

				String name = "unknown";

				if (!damageEvent.isCancelled())
					name = damageEvent.getCause().name().toLowerCase(Locale.ENGLISH).replace('_', ' ');

				RecordingQueue.addToQueue(ActionFactory.createEntity("entity-kill", entity, name));
			}

			/*
			 * if (entity.getLastDamageCause() instanceof EntityDamageByEntityEvent) { final
			 * EntityDamageByEntityEvent entityDamageByEntityEvent =
			 * (EntityDamageByEntityEvent) entity .getLastDamageCause();
			 * 
			 * // Mob killed by player if (entityDamageByEntityEvent.getDamager() instanceof
			 * Player) { final Player player = (Player)
			 * entityDamageByEntityEvent.getDamager(); if
			 * (!Prism.getIgnore().event("player-kill", player)) return;
			 * RecordingQueue.addToQueue(ActionFactory.createEntity("player-kill", entity,
			 * player));
			 * 
			 * } // Mob shot by an arrow from a player else if
			 * (entityDamageByEntityEvent.getDamager() instanceof Arrow) { final Arrow arrow
			 * = (Arrow) entityDamageByEntityEvent.getDamager();
			 * 
			 * if (arrow.getShooter() instanceof Player) { final Player player = (Player)
			 * arrow.getShooter(); if (!Prism.getIgnore().event("player-kill", player))
			 * return; RecordingQueue.addToQueue(ActionFactory.createEntity("player-kill",
			 * entity, player));
			 * 
			 * } else if (arrow.getShooter() instanceof LivingEntity) { final Entity damager
			 * = (Entity) arrow.getShooter(); String name = "unknown"; if (damager != null)
			 * { name = damager.getType().name().toLowerCase(); }
			 * 
			 * if (!Prism.getIgnore().event("entity-kill", entity.getWorld())) return;
			 * RecordingQueue.addToQueue(ActionFactory.createEntity("entity-kill", entity,
			 * name)); } else if (arrow.getShooter() instanceof BlockProjectileSource) {
			 * 
			 * final Block damager = (Block)
			 * ((BlockProjectileSource)arrow.getShooter()).getBlock();
			 * 
			 * if (!Prism.getIgnore().event("entity-kill", entity.getWorld())) return;
			 * 
			 * String name = "block:" + damager.getType().name().toLowerCase();
			 * 
			 * RecordingQueue.addToQueue(ActionFactory.createEntity("entity-kill", entity,
			 * name)); } } else { // Mob died by another mob final Entity damager =
			 * entityDamageByEntityEvent.getDamager(); String name = "unknown"; if (damager
			 * != null) { name = damager.getType().name().toLowerCase(); }
			 * 
			 * if (!Prism.getIgnore().event("entity-kill", entity.getWorld())) return;
			 * RecordingQueue.addToQueue(ActionFactory.createEntity("entity-kill", entity,
			 * name)); } } else {
			 * 
			 * if (!Prism.getIgnore().event("entity-kill", entity.getWorld())) return;
			 * 
			 * String killer = "unknown"; final EntityDamageEvent damage =
			 * entity.getLastDamageCause(); if (damage != null) { final DamageCause cause =
			 * damage.getCause(); if (cause != null) { killer = cause.name().toLowerCase();
			 * } }
			 * 
			 * // Record the death as natural
			 * RecordingQueue.addToQueue(ActionFactory.createEntity("entity-kill", entity,
			 * killer));
			 * 
			 * }
			 */
		}
		else {

			// Determine who died and what the exact cause was
			final Player p = (Player) event.getEntity();
			if (Prism.getIgnore().event("player-death", p)) {
				final String cause = DeathUtils.getCauseNiceName(p);
				String attacker = DeathUtils.getAttackerName(p);
				if (attacker.equals("pvpwolf")) {
					final String owner = DeathUtils.getTameWolfOwner(event);
					attacker = owner + "'s wolf";
				}
				RecordingQueue.addToQueue(ActionFactory.createPlayerDeath("player-death", p, cause, attacker));
			}

			// Log item drops
			if (Prism.getIgnore().event("item-drop", p)) {
				if (!event.getDrops().isEmpty()) {
					for (final ItemStack i : event.getDrops()) {
						RecordingQueue.addToQueue(ActionFactory.createItemStack("item-drop", i, i.getAmount(), -1, null,
								p.getLocation(), p));
					}
				}
			}
		}
	}

	/**
	 * 
	 * @param event
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onCreatureSpawn(final CreatureSpawnEvent event) {
		if (!Prism.getIgnore().event("entity-spawn", event.getEntity().getWorld()))
			return;
		final String reason = event.getSpawnReason().name().toLowerCase().replace("_", " ");
		if (reason.equals("natural"))
			return;
		RecordingQueue.addToQueue(ActionFactory.createEntity("entity-spawn", event.getEntity(), reason));
	}

	/**
	 * 
	 * @param event
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onEntityTargetEvent(final EntityTargetEvent event) {
		if (!Prism.getIgnore().event("entity-follow", event.getEntity().getWorld()))
			return;
		if (event.getTarget() instanceof Player) {
			if (event.getEntity().getType().equals(EntityType.CREEPER)) {
				final Player player = (Player) event.getTarget();
				RecordingQueue.addToQueue(ActionFactory.createEntity("entity-follow", event.getEntity(), player));
			}
		}
	}

	/**
	 * 
	 * @param event
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlayerShearEntity(final PlayerShearEntityEvent event) {
		if (!Prism.getIgnore().event("entity-shear", event.getPlayer()))
			return;
		RecordingQueue.addToQueue(ActionFactory.createEntity("entity-shear", event.getEntity(), event.getPlayer()));
	}

	/**
	 * 
	 * @param event
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void interactAtVariant(final PlayerInteractAtEntityEvent event) {

		final Player p = event.getPlayer();
		final Entity e = event.getRightClicked();
		final ItemStack hand = p.getInventory().getItemInMainHand();
		// @todo right clicks should technically follow blockface
		// Cancel the event if a wand is in use
		if (WandUtils.playerUsesWandOnClick(p, e.getLocation())) {
			event.setCancelled(true);
			return;
		}
		
		if(e instanceof ArmorStand && event instanceof PlayerInteractAtEntityEvent) {
			Vector at = ((PlayerInteractAtEntityEvent)event).getClickedPosition();
			ArmorStand stand = (ArmorStand) e;

			if(hand.getType() != Material.AIR) {
				EquipmentSlot target = InventoryUtils.getTargetArmorSlot(hand.getType());
				
				if(stand.hasArms() || target != EquipmentSlot.HAND) {
					ItemStack atSlot = InventoryUtils.getEquipment(stand.getEquipment(), target);
					
					if(atSlot.getType() != Material.AIR) {
						RecordingQueue.addToQueue(
								ActionFactory.createItemStack("item-remove", atSlot, 1, target, null, e.getLocation(), p));
						RecordingQueue.addToQueue(
								ActionFactory.createItemStack("item-insert", hand, 1, target, null, e.getLocation(), p));
					}
					else {
						RecordingQueue.addToQueue(
								ActionFactory.createItemStack("item-insert", hand, 1, target, null, e.getLocation(), p));
					}
				}
			}
			else {
				double elevation = at.getY();
				
				EquipmentSlot slot;
				boolean hasChestplate;
				{
					ItemStack chestplate = stand.getEquipment().getChestplate();
					hasChestplate = chestplate != null && chestplate.getType() != Material.AIR;
				}
				
				if(elevation >= 1.6) {
					slot = EquipmentSlot.HEAD;
				}
				else if(hasChestplate && elevation >= 0.9) {
					slot = EquipmentSlot.CHEST;
				}
				else if(!hasChestplate && elevation >= 1.2) {
					slot = EquipmentSlot.HAND;
				}
				else if(elevation >= 0.55) {
					slot = EquipmentSlot.LEGS;
				}
				else {
					slot = EquipmentSlot.FEET;
				}
				
				ItemStack atSlot = InventoryUtils.getEquipment(stand.getEquipment(), slot);
				
				if(atSlot.getType() != Material.AIR) {
					RecordingQueue.addToQueue(
							ActionFactory.createItemStack("item-remove", atSlot, 1, slot, null, e.getLocation(), p));
				}
			}
		}
	}
	
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlayerInteractEntityEvent(final PlayerInteractEntityEvent event) {

		final Player p = event.getPlayer();
		final Entity e = event.getRightClicked();
		final ItemStack hand = p.getInventory().getItemInMainHand();
		// @todo right clicks should technically follow blockface
		// Cancel the event if a wand is in use
		if (WandUtils.playerUsesWandOnClick(p, e.getLocation())) {
			event.setCancelled(true);
			return;
		}

		if (e instanceof ItemFrame) {
			final ItemFrame frame = (ItemFrame) e;

			// If held item doesn't equal existing item frame object type
			if (!frame.getItem().getType().equals(Material.AIR)) {
				RecordingQueue.addToQueue(ActionFactory.createPlayer("item-rotate", event.getPlayer(),
						frame.getRotation().name().toLowerCase()));
			}

			// Frame is empty but an item is held
			if (frame.getItem().getType().equals(Material.AIR) && hand != null) {
				if (Prism.getIgnore().event("item-insert", p)) {
					RecordingQueue.addToQueue(
							ActionFactory.createItemStack("item-insert", hand, 1, frame.getAttachedFace(), null, e.getLocation(), p));
				}
			}
		}

		if (hand != null) {
			// if they're holding coal (or charcoal, a subitem) and they click a
			// powered minecart
			if (hand.getType() == Material.COAL && e instanceof PoweredMinecart) {
				if (!Prism.getIgnore().event("item-insert", p))
					return;
				RecordingQueue
						.addToQueue(ActionFactory.createItemStack("item-insert", hand, 1, 0, null, e.getLocation(), p));
			}

			if (!Prism.getIgnore().event("entity-dye", p))
				return;
			// Only track the event on sheep, when player holds dye
			if (MaterialTag.DYES.isTagged(hand.getType()) && e.getType() == EntityType.SHEEP) {
				final String newColor = Prism.getItems().getAlias(hand.getType(), null);
				RecordingQueue.addToQueue(
						ActionFactory.createEntity("entity-dye", event.getRightClicked(), event.getPlayer(), newColor));
			}
		}
	}

	/**
	 * 
	 * @param event
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onEntityBreakDoor(final EntityBreakDoorEvent event) {
		if (!Prism.getIgnore().event("entity-break", event.getEntity().getWorld()))
			return;
		RecordingQueue.addToQueue(ActionFactory.createBlock("entity-break", event.getBlock(),
				event.getEntityType().name().toLowerCase()));
	}

	/**
	 * 
	 * @param event
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlayerEntityLeash(final PlayerLeashEntityEvent event) {
		if (!Prism.getIgnore().event("entity-leash", event.getPlayer()))
			return;
		RecordingQueue.addToQueue(ActionFactory.createEntity("entity-leash", event.getEntity(), event.getPlayer()));
	}

	/**
	 * 
	 * @param event
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlayerEntityUnleash(final PlayerUnleashEntityEvent event) {
		if (!Prism.getIgnore().event("entity-unleash", event.getPlayer()))
			return;
		RecordingQueue.addToQueue(ActionFactory.createEntity("entity-unleash", event.getEntity(), event.getPlayer()));
	}

	/**
	 * 
	 * @param event
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onEntityUnleash(final EntityUnleashEvent event) {
		if (!Prism.getIgnore().event("entity-unleash"))
			return;
		RecordingQueue.addToQueue(ActionFactory.createEntity("entity-unleash", event.getEntity(),
				event.getReason().toString().toLowerCase()));
	}

	/**
	 * 
	 * @param event
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPotionSplashEvent(final PotionSplashEvent event) {

		final ProjectileSource source = event.getPotion().getShooter();

		// Ignore from non-players for the time being
		if (!(source instanceof Player))
			return;

		final Player player = (Player) source;

		if (!Prism.getIgnore().event("potion-splash", player))
			return;

		// What type?
		// Right now this won't support anything with multiple effects
		final Collection<PotionEffect> potion = event.getPotion().getEffects();
		String name = "";
		for (final PotionEffect eff : potion) {
			name = eff.getType().getName().toLowerCase();
		}

		RecordingQueue.addToQueue(ActionFactory.createPlayer("potion-splash", player, name));

	}

	/**
	 * 
	 * @param event
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onHangingPlaceEvent(final HangingPlaceEvent event) {
		// Cancel the event if a wand is in use
		if (WandUtils.playerUsesWandOnClick(event.getPlayer(), event.getEntity().getLocation())) {
			event.setCancelled(true);
			return;
		}
		if (!Prism.getIgnore().event("hangingitem-place", event.getPlayer()))
			return;
		RecordingQueue
				.addToQueue(ActionFactory.createHangingItem("hangingitem-place", event.getEntity(), event.getPlayer()));
	}

	/**
	 * Hanging items broken by a player fall under the HangingBreakByEntityEvent
	 * events. This is merely here to capture cause = physics for when they detach
	 * from a block.
	 * 
	 * @param event
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onHangingBreakEvent(final HangingBreakEvent event) {

		// Ignore other causes. Entity cause already handled.
		if (!event.getCause().equals(RemoveCause.PHYSICS)) {
			return;
		}

		if (!Prism.getIgnore().event("hangingitem-break", event.getEntity().getWorld()))
			return;

		final Hanging e = event.getEntity();

		// Check for planned hanging item breaks
		final String coord_key = e.getLocation().getBlockX() + ":" + e.getLocation().getBlockY() + ":"
				+ e.getLocation().getBlockZ();

		String value = plugin.preplannedBlockFalls.remove(coord_key);
		
		if(value == null) {
			value = "unknown";
		}
		
		Player player = null;
		try {
			player = Bukkit.getPlayer(UUID.fromString(value));
		}
		catch (Exception e2) {
		}

		// Track the hanging item break
		if (player != null)
			RecordingQueue.addToQueue(ActionFactory.createHangingItem("hangingitem-break", e, player));
		else {
			RecordingQueue.addToQueue(ActionFactory.createHangingItem("hangingitem-break", e, value));
		}

		plugin.preplannedBlockFalls.remove(coord_key);

		if (!Prism.getIgnore().event("item-remove", event.getEntity().getWorld()))
			return;

		// If an item frame, track it's contents
		if (e instanceof ItemFrame) {
			final ItemFrame frame = (ItemFrame) e;
			if (frame.getItem() != null) {
				if (player != null)
					RecordingQueue.addToQueue(ActionFactory.createItemStack("item-remove", frame.getItem(),
							frame.getItem().getAmount(), -1, null, e.getLocation(), player));
				else
					RecordingQueue.addToQueue(ActionFactory.createItemStack("item-remove", frame.getItem(),
							frame.getItem().getAmount(), -1, null, e.getLocation(), value));
			}
		}
	}

	/**
	 * 
	 * @param event
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onHangingBreakByEntityEvent(final HangingBreakByEntityEvent event) {

		final Entity entity = event.getEntity();
		final Entity remover = event.getRemover();
		Player player = null;
		if (remover instanceof Player)
			player = (Player) remover;

		// Cancel the event if a wand is in use
		if (player != null && WandUtils.playerUsesWandOnClick(player, event.getEntity().getLocation())) {
			event.setCancelled(true);
			return;
		}

		if (!Prism.getIgnore().event("hangingitem-break", event.getEntity().getWorld()))
			return;

		String breaking_name = remover.getType().name().toLowerCase();
		if (player != null)
			RecordingQueue.addToQueue(ActionFactory.createHangingItem("hangingitem-break", event.getEntity(), player));
		else
			RecordingQueue
					.addToQueue(ActionFactory.createHangingItem("hangingitem-break", event.getEntity(), breaking_name));

		if (!Prism.getIgnore().event("item-remove", event.getEntity().getWorld()))
			return;

		// If an item frame, track it's contents
		if (event.getEntity() instanceof ItemFrame) {
			final ItemFrame frame = (ItemFrame) event.getEntity();
			if (frame.getItem() != null) {
				RecordingQueue.addToQueue(ActionFactory.createItemStack("item-remove", frame.getItem(),
						frame.getItem().getAmount(), -1, null, entity.getLocation(), breaking_name));
			}
		}
	}

	/**
	 * 
	 * @param event
	 */
	
	// TODO: This is a mess. Please, for the love of god, revisit and fix.
	@Deprecated
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onEntityChangeBlock(final EntityChangeBlockEvent event) {
		final String entity = MiscUtils.getEntityName(event.getEntity());

		// Technically I think that I really should name it "entity-eat" for
		// better consistency and
		// in case other mobs ever are made to eat. But that's not as fun
		Material to = event.getTo();
		Material from = event.getBlock().getType();
		if (from == Material.GRASS && to == Material.DIRT) {
			if (event.getEntityType() != EntityType.SHEEP)
				return;
			if (!Prism.getIgnore().event("sheep-eat", event.getBlock()))
				return;
			RecordingQueue.addToQueue(ActionFactory.createBlock("sheep-eat", event.getBlock(), entity));
		}
		else if (to == Material.AIR ^ from == Material.AIR && event.getEntity() instanceof Enderman) {
			if (from == Material.AIR) {
				if (!Prism.getIgnore().event("enderman-place", event.getBlock()))
					return;
				BlockState state = event.getBlock().getState();
				state.setType(to);
				RecordingQueue.addToQueue(ActionFactory.createBlock("enderman-place", state, entity));
			}
			else {
				if (!Prism.getIgnore().event("enderman-pickup", event.getBlock()))
					return;
				
				BlockState state = event.getBlock().getState();
				state.setBlockData(event.getBlockData());
				RecordingQueue.addToQueue(ActionFactory.createBlock("enderman-pickup", state, entity));
			}
		}
		else if (to == Material.AIR && event.getEntity() instanceof Wither) {
			if (!Prism.getIgnore().event("entity-break", event.getBlock()))
				return;
			RecordingQueue.addToQueue(ActionFactory.createBlock("block-break", event.getBlock(),
					event.getEntityType().name().toLowerCase()));
		}
	}

	/**
	 * 
	 * @param event
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onEntityBlockForm(final EntityBlockFormEvent event) {
		if (!Prism.getIgnore().event("entity-form", event.getBlock()))
			return;
		final Block block = event.getBlock();
		final Location loc = block.getLocation();
		final BlockState newState = event.getNewState();
		final String entity = event.getEntity().getType().name().toLowerCase();

		RecordingQueue.addToQueue(ActionFactory.createBlockChange("entity-form", loc, block.getType(),
				block.getBlockData(), newState.getType(), newState.getBlockData(), entity));
	}

	/**
	 * 
	 * @param event
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onEntityExplodeChangeBlock(final EntityExplodeEvent event) {

		if (event.blockList() == null || event.blockList().isEmpty())
			return;

		String name;
		String action = "entity-explode";
		if (event.getEntity() != null) {
			if (event.getEntity() instanceof Creeper) {
				if (!Prism.getIgnore().event("creeper-explode", event.getEntity().getWorld()))
					return;
				action = "creeper-explode";
				name = "creeper";
			}
			else if (event.getEntity() instanceof TNTPrimed) {
				if (!Prism.getIgnore().event("tnt-explode", event.getEntity().getWorld()))
					return;
				action = "tnt-explode";
				Entity source = ((TNTPrimed) event.getEntity()).getSource();
				name = followTNTTrail(source);
			}
			else if (event.getEntity() instanceof EnderDragon) {
				if (!Prism.getIgnore().event("dragon-eat", event.getEntity().getWorld()))
					return;
				action = "dragon-eat";
				name = "enderdragon";
			}
			else {
				if (!Prism.getIgnore().event("entity-explode", event.getLocation().getWorld()))
					return;
				try {
					name = event.getEntity().getType().name().toLowerCase().replace("_", " ");
					name = name.length() > 15 ? name.substring(0, 15) : name; // I
																				// don't
																				// think
																				// this
																				// can
																				// happen,
																				// but
																				// just
																				// in
																				// case.
																				// Might
																				// look
																				// weird,
																				// but
																				// that's
																				// better
																				// than
																				// breaking
																				// stuff.
				}
				catch (final NullPointerException e) {
					name = "unknown";
				}
			}
		}
		else {
			if (!Prism.getIgnore().event("entity-explode", event.getLocation().getWorld()))
				return;
			name = "magic";
		}
		// Also log item-removes from chests that are blown up
		final PrismBlockEvents be = new PrismBlockEvents(plugin);
		for (Block block : event.blockList()) {

			// don't bother record upper doors.
			if (MaterialTag.DOORS.isTagged(block.getType())) {
				if (((Door) block.getState().getData()).isTopHalf()) {
					continue;
				}
			}

			// Change handling a bit if it's a long block
			final Block sibling = BlockUtils.getSiblingForDoubleLengthBlock(block);
			if (sibling != null && !block.getType().equals(Material.CHEST)
					&& !block.getType().equals(Material.TRAPPED_CHEST)) {
				block = sibling;
			}

			// log items removed from container
			// note: done before the container so a "rewind" for rollback will
			// work properly
			final Block b2 = block;
			final String source = name;
			be.forEachItem(block, (i, s) -> {
				RecordingQueue.addToQueue(ActionFactory.createItemStack("item-remove", i, i.getAmount(), 0, null,
						b2.getLocation(), source));
			});
			// be.logItemRemoveFromDestroyedContainer( name, block );
			RecordingQueue.addToQueue(ActionFactory.createBlock(action, block, source));
			// look for relationships
			be.logBlockRelationshipsForBlock(source, block);

		}
	}

	private String followTNTTrail(Entity initial) {
		int counter = 10000000;

		while (initial != null) {
			if (initial instanceof Player) {
				return ((Player) initial).getName();
			}
			else if (initial instanceof TNTPrimed) {
				initial = (((TNTPrimed) initial).getSource());
				if (counter < 0) {
					Location last = initial.getLocation();
					plugin.getLogger().warning("TnT chain has exceeded one million, will not continue!");
					plugin.getLogger().warning("Last Tnt was at " + last.getX() + ", " + last.getY() + ". "
							+ last.getZ() + " in world " + last.getWorld());
					return "tnt";
				}
				counter--;
			}
			else {
				return initial.getType().name();
			}
		}

		return "tnt";
	}
}
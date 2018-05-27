package com.github.ustc_zzzz.virtualchest.record;

import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.annotations.IdName;
import org.javalite.activejdbc.annotations.Table;
import org.spongepowered.api.entity.living.player.Player;

import java.util.Date;
import java.util.UUID;

/**
 * @author ustc_zzzz
 */
@IdName("submit_uuid")
@Table(VirtualChestRecordManager.SLOT_CLICK_RECORD)
public class VirtualChestClickSlotRecord extends Model
{
    VirtualChestClickSlotRecord(UUID uuid, String menuName, int menuSlot,
                                boolean isShift, boolean isPrimary, boolean isSecondary, Player p)
    {
        this.set("submit_time", new Date());
        this.set("submit_uuid", uuid.toString());
        this.set("menu_name", menuName).set("menu_slot", menuSlot);
        this.set("player_name", p.getName()).set("player_uuid", p.getUniqueId().toString());
        this.set("is_shift", isShift).set("is_primary", isPrimary).set("is_secondary", isSecondary);
    }
}

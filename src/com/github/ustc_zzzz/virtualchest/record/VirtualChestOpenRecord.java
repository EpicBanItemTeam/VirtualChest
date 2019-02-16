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
@Table(VirtualChestRecordManager.OPEN_RECORD)
public class VirtualChestOpenRecord extends Model
{
    private static final long serialVersionUID = 1L;

    VirtualChestOpenRecord(UUID uuid, String menuName, Player p)
    {
        this.set("submit_time", new Date());
        this.set("submit_uuid", uuid.toString());
        this.set("menu_name", menuName).set("player_name", p.getName()).set("player_uuid", p.getUniqueId().toString());
    }
}

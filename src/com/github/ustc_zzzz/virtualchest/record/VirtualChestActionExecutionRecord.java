package com.github.ustc_zzzz.virtualchest.record;

import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.annotations.CompositePK;
import org.javalite.activejdbc.annotations.Table;

import java.util.Date;
import java.util.UUID;

/**
 * @author ustc_zzzz
 */
@CompositePK({"submit_uuid", "execution_order"})
@Table(VirtualChestRecordManager.ACTION_EXECUTION_RECORD)
public class VirtualChestActionExecutionRecord extends Model
{
    private static final long serialVersionUID = 1L;

    VirtualChestActionExecutionRecord(UUID uuid, int order, String executionPrefix, String executionSuffix)
    {
        this.set("execution_time", new Date());
        this.set("execution_order", order).set("submit_uuid", uuid.toString());
        this.set("execution_prefix", executionPrefix).set("execution_suffix", executionSuffix);
    }
}

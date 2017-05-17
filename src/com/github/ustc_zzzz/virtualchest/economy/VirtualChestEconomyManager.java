package com.github.ustc_zzzz.virtualchest.economy;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.service.economy.Currency;
import org.spongepowered.api.service.economy.EconomyService;
import org.spongepowered.api.service.economy.account.UniqueAccount;
import org.spongepowered.api.service.economy.transaction.ResultType;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author ustc_zzzz
 */
public class VirtualChestEconomyManager
{
    private final Cause cause;
    private final Logger logger;
    private final Map<String, Currency> currencies = new HashMap<>();

    private Currency defaultCurrency;
    private EconomyService economyService;

    public VirtualChestEconomyManager(VirtualChestPlugin plugin)
    {
        this.logger = plugin.getLogger();
        this.cause = Cause.source(plugin).build();
    }

    public void init()
    {
        Optional<EconomyService> serviceOptional = Sponge.getServiceManager().provide(EconomyService.class);
        if (serviceOptional.isPresent())
        {
            economyService = serviceOptional.get();
            defaultCurrency = economyService.getDefaultCurrency();
            currencies.put(defaultCurrency.getId().toLowerCase(), defaultCurrency);
            economyService.getCurrencies().forEach(c -> currencies.put(c.getId().toLowerCase(), c));
        }
        else
        {
            this.logger.warn("VirtualChest could not find the economy service. ");
            this.logger.warn("Features related to the economy may not work normally.");
        }
    }

    public boolean withdrawBalance(String currencyName, Player player, BigDecimal cost, boolean simulate)
    {
        try
        {
            Currency currency = Optional
                    .ofNullable(currencyName.isEmpty() ? defaultCurrency : currencies.get(currencyName))
                    .orElseThrow(() -> new IllegalArgumentException("Unsupported currency name: " + currencyName));
            UniqueAccount account = economyService.getOrCreateAccount(player.getUniqueId())
                    .orElseThrow(() -> new IllegalArgumentException("Unsupported account for given player"));
            return withdrawBalance(currency, account, cost, simulate);
        }
        catch (IllegalArgumentException e)
        {
            this.logger.warn("Find error when checking the balance of player '" + player.getName() + "'.", e);
            return false;
        }
    }

    private boolean withdrawBalance(Currency currency, UniqueAccount account, BigDecimal cost, boolean simulate)
    {
        BigDecimal balance = account.getBalance(currency);
        ResultType result = account.withdraw(currency, cost, cause).getResult();
        if (simulate)
        {
            account.setBalance(currency, balance, cause);
        }
        return ResultType.SUCCESS.equals(result);
    }
}

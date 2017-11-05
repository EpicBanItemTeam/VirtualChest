package com.github.ustc_zzzz.virtualchest.economy;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.github.ustc_zzzz.virtualchest.unsafe.SpongeUnimplemented;
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
import java.util.UUID;

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
        this.cause = SpongeUnimplemented.createCause(plugin);
    }

    public void init()
    {
        Optional<EconomyService> serviceOptional = Sponge.getServiceManager().provide(EconomyService.class);
        if (serviceOptional.isPresent())
        {
            economyService = serviceOptional.get();
            defaultCurrency = economyService.getDefaultCurrency();
            economyService.getCurrencies().forEach(this::addCurrency);
            Optional.ofNullable(defaultCurrency).ifPresent(this::addCurrency);
        }
        else
        {
            this.logger.warn("VirtualChest could not find the economy service. ");
            this.logger.warn("Features related to the economy may not work normally.");
        }
    }

    public boolean withdrawBalance(String currencyName, Player player, BigDecimal cost)
    {
        try
        {
            return withdrawBalance(getCurrencyByName(currencyName), getAccountByPlayer(player), cost);
        }
        catch (IllegalArgumentException e)
        {
            this.logger.warn("Find error when checking the balance of player '" + player.getName() + "'.", e);
            return false;
        }
    }

    public boolean depositBalance(String currencyName, Player player, BigDecimal cost)
    {
        try
        {
            return depositBalance(getCurrencyByName(currencyName), getAccountByPlayer(player), cost);
        }
        catch (IllegalArgumentException e)
        {
            this.logger.warn("Find error when checking the balance of player '" + player.getName() + "'.", e);
            return false;
        }
    }

    private void addCurrency(Currency currency)
    {
        currencies.put(currency.getId().toLowerCase(), currency);
    }

    private Currency getCurrencyByName(String name)
    {
        if (name.isEmpty())
        {
            String message = "Default currency is not supported by the economy plugin";
            return Optional.ofNullable(defaultCurrency).orElseThrow(() -> new IllegalArgumentException(message));
        }
        else
        {
            String message = "The specified currency name (" + name + ") is not supported by the economy plugin";
            return Optional.ofNullable(currencies.get(name)).orElseThrow(() -> new IllegalArgumentException(message));
        }
    }

    private UniqueAccount getAccountByPlayer(Player player)
    {
        UUID uniqueId = player.getUniqueId();
        String message = "Unsupported account for uuid: " + uniqueId.toString();
        return economyService.getOrCreateAccount(uniqueId).orElseThrow(() -> new IllegalArgumentException(message));
    }

    private boolean withdrawBalance(Currency currency, UniqueAccount account, BigDecimal cost)
    {
        BigDecimal balance = account.getBalance(currency);
        ResultType result = account.withdraw(currency, cost, cause).getResult();
        return ResultType.SUCCESS.equals(result);
    }

    private boolean depositBalance(Currency currency, UniqueAccount account, BigDecimal cost)
    {
        BigDecimal balance = account.getBalance(currency);
        ResultType result = account.deposit(currency, cost, cause).getResult();
        return ResultType.SUCCESS.equals(result);
    }
}

package com.github.ustc_zzzz.virtualchest.script;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.util.Tuple;

import javax.script.*;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.Function;

/**
 * @author ustc_zzzz
 */
public class VirtualChestJavaScriptManager
{
    private final Logger logger;
    private final VirtualChestPlugin plugin;
    private final ScriptEngine scriptEngine;

    private final CompiledScript nonsenseTrue;
    private final CompiledScript nonsenseFalse;

    private final Map<Player, Long> tickWhileOpeningInventory = new WeakHashMap<>();

    public VirtualChestJavaScriptManager(VirtualChestPlugin plugin)
    {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.scriptEngine = Objects.requireNonNull(new ScriptEngineManager(null).getEngineByName("nashorn"));

        this.nonsenseTrue = new CompiledScriptNonsense(this.scriptEngine, Boolean.TRUE);
        this.nonsenseFalse = new CompiledScriptNonsense(this.scriptEngine, Boolean.FALSE);
    }

    public void onOpeningInventory(Player player)
    {
        this.tickWhileOpeningInventory.put(player, player.getWorld().getProperties().getTotalTime());
    }

    public Tuple<String, CompiledScript> prepare(String scriptLiteral)
    {
        String script = this.plugin.getPlaceholderManager().parseJavaScriptLiteral(scriptLiteral.trim(), "papi");
        if (script.isEmpty())
        {
            return Tuple.of(scriptLiteral, this.nonsenseTrue);
        }
        try
        {
            this.logger.debug("Compile script \"{}\" for preparation", script);
            return Tuple.of(scriptLiteral, ((Compilable) this.scriptEngine).compile(script));
        }
        catch (ScriptException e)
        {
            this.logger.error("Error found when compiling script \"{}\"", script);
            return Tuple.of(scriptLiteral, this.nonsenseFalse);
        }
    }

    public boolean execute(Player player, Tuple<String, CompiledScript> tuple)
    {
        String scriptLiteral = tuple.getFirst();
        try
        {
            Bindings binding = this.getBindings(player, scriptLiteral);
            this.logger.debug("Execute script for player {}", player);
            String executionResult = String.valueOf(tuple.getSecond().eval(binding));
            this.logger.debug("Literal: {}", scriptLiteral);
            this.logger.debug("Result: {}", executionResult);
            return Boolean.valueOf(executionResult);
        }
        catch (ScriptException e)
        {
            this.logger.error("Error found when executing script for player " + player.getName(), e);
            this.logger.debug("Literal: {}", scriptLiteral);
            this.logger.debug("Result: {}", Boolean.FALSE);
            return false;
        }
    }

    private Bindings getBindings(Player player, String scriptLiteral)
    {
        SimpleBindings bindings = new SimpleBindings();
        Map<String, Object> map = this.plugin.getPlaceholderManager().getPlaceholderAPIMap(player, scriptLiteral);

        bindings.put("player", player);
        bindings.put("server", Sponge.getServer());
        bindings.put("tick", this.getTickFromOpeningInventory(player));
        bindings.put("papi", (Function<String, ?>) s -> map.containsKey(s) ? Text.of(map.get(s)).toPlain() : null);

        return bindings;
    }

    private Long getTickFromOpeningInventory(Player player)
    {
        if (this.tickWhileOpeningInventory.containsKey(player))
        {
            return player.getWorld().getProperties().getTotalTime() - this.tickWhileOpeningInventory.get(player);
        }
        return 0L;
    }

    private static class CompiledScriptNonsense extends CompiledScript
    {
        private final Object result;
        private final ScriptEngine engine;

        private CompiledScriptNonsense(ScriptEngine engine, Object result)
        {
            this.result = result;
            this.engine = engine;
        }

        @Override
        public Object eval(ScriptContext context) throws ScriptException
        {
            return this.result;
        }

        @Override
        public ScriptEngine getEngine()
        {
            return this.engine;
        }
    }
}

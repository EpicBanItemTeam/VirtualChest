package com.github.ustc_zzzz.virtualchest.script;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.util.Tuple;

import javax.script.*;
import java.util.Map;
import java.util.function.Function;

/**
 * @author ustc_zzzz
 */
public class VirtualChestJavaScriptManager
{
    private final Logger logger;
    private final VirtualChestPlugin plugin;
    private final ScriptEngine engine = new ScriptEngineManager(null).getEngineByName("nashorn");

    private final CompiledScript nonsenseTrue = new CompiledScriptNonsense(this.engine, Boolean.TRUE);
    private final CompiledScript nonsenseFalse = new CompiledScriptNonsense(this.engine, Boolean.FALSE);

    public VirtualChestJavaScriptManager(VirtualChestPlugin plugin)
    {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
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
            return Tuple.of(scriptLiteral, ((Compilable) this.engine).compile(script));
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
        bindings.put("tick", player.getWorld().getProperties().getTotalTime());
        bindings.put("papi", (Function<String, ?>) s -> map.containsKey(s) ? Text.of(map.get(s)).toPlain() : null);

        return bindings;
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

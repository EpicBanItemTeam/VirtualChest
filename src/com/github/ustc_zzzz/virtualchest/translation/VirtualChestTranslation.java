package com.github.ustc_zzzz.virtualchest.translation;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.asset.Asset;
import org.spongepowered.api.asset.AssetManager;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.serializer.TextSerializer;
import org.spongepowered.api.text.serializer.TextSerializers;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;

/**
 * @author ustc_zzzz
 */
public class VirtualChestTranslation
{
    private static final String I18N_ERROR = "Cannot find the default i18n file: i18n/en_US.properties";

    private final Logger logger;
    private final ResourceBundle resourceBundle;
    private final TextSerializer textSerializer = TextSerializers.FORMATTING_CODE;

    public VirtualChestTranslation(VirtualChestPlugin plugin)
    {
        Locale locale = Locale.getDefault();
        AssetManager assets = Sponge.getAssetManager();
        logger = plugin.getLogger();
        try
        {
            Asset asset = assets.getAsset(plugin, "i18n/" + locale.toString() + ".properties").orElse(assets.
                    getAsset(plugin, "i18n/en_US.properties").orElseThrow(() -> new IOException(I18N_ERROR)));
            resourceBundle = new PropertyResourceBundle(asset.getUrl().openStream());
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public void info(String key)
    {
        logger.info(getString(key, new Object[0]));
    }

    public void info(String key, Object... values)
    {
        logger.info(getString(key, values));
    }

    public Text take(String key)
    {
        return textSerializer.deserialize(getString(key, new Object[0]));
    }

    public Text take(String key, Object... values)
    {
        return textSerializer.deserialize(getString(key, values));
    }

    private String getString(String key, Object[] values)
    {
        try
        {
            return new MessageFormat(resourceBundle.getString(key)).format(values);
        }
        catch (MissingResourceException | ClassCastException | IllegalArgumentException e)
        {
            return key;
        }
    }
}

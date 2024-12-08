package de.themoep.autoserver.velocity;

/*
 * AutoServer - velocity
 * Copyright (c) 2024 Max Lee aka Phoenix616 (max@themoep.de)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Pattern;

public class PluginConfig {

	private static final Pattern PATH_PATTERN = Pattern.compile("\\.");

	private final AutoServer plugin;
	private final Path configFile;
	private final String defaultFile;
	private final YamlConfigurationLoader configLoader;
	private ConfigurationNode config;
	private ConfigurationNode defaultConfig;

	public PluginConfig(AutoServer plugin, Path configFile) {
		this(plugin, configFile, configFile.getFileName().toString());
	}

	public PluginConfig(AutoServer plugin, Path configFile, String defaultFile) {
		this.plugin = plugin;
		this.configFile = configFile;
		this.defaultFile = defaultFile;
		configLoader = YamlConfigurationLoader.builder()
				.indent(2)
				.path(configFile)
				.nodeStyle(NodeStyle.BLOCK)
				.build();
	}

	public boolean load() {
		try {
			config = configLoader.load();
			if (defaultFile != null) {
				defaultConfig = YamlConfigurationLoader.builder()
						.indent(2)
						.source(() -> new BufferedReader(new InputStreamReader(plugin.getResourceAsStream(defaultFile))))
						.build().load();
				if (config.empty()) {
					config = defaultConfig.copy();
				}
			}
			plugin.log(Level.INFO, "Loaded " + configFile.getFileName());
			return true;
		} catch (IOException e) {
			plugin.log(Level.SEVERE, "Unable to load configuration file " + configFile.getFileName(), e);
			return false;
		}
	}

	public boolean createDefaultConfig() throws IOException {
		try (InputStream in = plugin.getResourceAsStream(defaultFile)) {
			if (in == null) {
				plugin.log(Level.WARNING, "No default config '" + defaultFile + "' found in " + plugin.getName() + "!");
				return false;
			}
			if (!Files.exists(configFile)) {
				Path parent = configFile.getParent();
				if (!Files.exists(parent)) {
					Files.createDirectories(parent);
				}
				try {
					Files.copy(in, configFile);
					return true;
				} catch (IOException ex) {
					plugin.log(Level.SEVERE, "Could not save " + configFile.getFileName() + " to " + configFile, ex);
				}
			}
		} catch (IOException ex) {
			plugin.log(Level.SEVERE, "Could not load default config from " + defaultFile, ex);
		}
		return false;
	}

	public void save() {
		try {
			configLoader.save(config);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public Object set(String path, Object value) {
		ConfigurationNode node = config.node(splitPath(path));
		Object prev = node.raw();
		try {
			node.set(value);
		} catch (SerializationException e) {
			throw new RuntimeException(e);
		}
		return prev;
	}

	public ConfigurationNode remove(String path) {
		ConfigurationNode node = config.node(splitPath(path));
		try {
			return node.virtual() ? node : node.set(null);
		} catch (SerializationException e) {
			throw new RuntimeException(e);
		}
	}

	public ConfigurationNode getRawConfig() {
		return config;
	}

	public ConfigurationNode getRawConfig(String path) {
		return getRawConfig().node(splitPath(path));
	}

	public boolean has(String path) {
		return !getRawConfig(path).virtual();
	}

	public boolean isSection(String path) {
		return !getRawConfig(path).childrenMap().isEmpty();
	}

	public Map<String, Object> getSection(String key) {
		return getConfigMap(getRawConfig(key));
	}

	public int getInt(String path) {
		return getInt(path, defaultConfig != null ? defaultConfig.node(splitPath(path)).getInt() : 0);
	}

	public int getInt(String path, int def) {
		return getRawConfig(path).getInt(def);
	}

	public double getDouble(String path) {
		return getDouble(path, defaultConfig != null ? defaultConfig.node(splitPath(path)).getDouble() : 0);
	}

	public double getDouble(String path, double def) {
		return getRawConfig(path).getDouble(def);
	}

	public String getString(String path) {
		return getString(path, defaultConfig != null ? defaultConfig.node(splitPath(path)).getString() : null);
	}

	public String getString(String path, String def) {
		ConfigurationNode node = getRawConfig(path);
		if (def != null) {
			return node.getString(def);
		}
		return node.getString();
	}

	public boolean getBoolean(String path) {
		return getBoolean(path, defaultConfig != null && defaultConfig.node(splitPath(path)).getBoolean());
	}

	public boolean getBoolean(String path, boolean def) {
		return getRawConfig(path).getBoolean(def);
	}

	private static Object[] splitPath(String key) {
		return PATH_PATTERN.split(key);
	}

	public static Map<String, Object> getConfigMap(Object configuration) {
		if (configuration instanceof Map) {
			return getValues((Map<?, ?>) configuration);
		} else if (configuration instanceof ConfigurationNode) {
			return getValues((ConfigurationNode) configuration);
		}
		return null;
	}

	private static Map<String, Object> getValues(ConfigurationNode config) {
		Map<String, Object> map = new LinkedHashMap<>();
		for (Map.Entry<Object, ? extends ConfigurationNode> entry : config.childrenMap().entrySet()) {
			String key = String.valueOf(entry.getKey());
			ConfigurationNode value = entry.getValue();
			if (value.isMap()) {
				map.put(key, getValues(value));
			} else {
				map.put(key, value.raw());
			}
		}
		return map;
	}

	private static Map<String, Object> getValues(Map<?, ?> map) {
		Map<String, Object> returnMap = new LinkedHashMap<>();
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			String key = String.valueOf(entry.getKey());
			if (entry.getValue() instanceof Map) {
				returnMap.put(key, getValues((Map<?, ?>) entry.getValue()));
			} else if (entry.getValue() instanceof ConfigurationNode) {
				returnMap.put(key, getValues((ConfigurationNode) entry.getValue()));
			} else {
				returnMap.put(key, entry.getValue());
			}
		}
		return returnMap;
	}
}
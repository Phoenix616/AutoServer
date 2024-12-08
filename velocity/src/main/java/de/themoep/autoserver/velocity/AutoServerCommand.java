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

import com.velocitypowered.api.command.SimpleCommand;

import java.util.Locale;

public class AutoServerCommand implements SimpleCommand {
	private final AutoServer plugin;

	public AutoServerCommand(AutoServer plugin) {
		this.plugin = plugin;
	}

	@Override
	public void execute(Invocation invocation) {
		if (hasPermission(invocation)) {
			if (invocation.arguments().length > 0) {
				switch (invocation.arguments()[0].toLowerCase(Locale.ROOT)) {
					case "reload" -> {
						if (plugin.loadConfig()) {
							invocation.source().sendMessage(plugin.getTranslation(invocation.source(), "command.reloaded"));
						} else {
							invocation.source().sendMessage(plugin.getTranslation(invocation.source(), "command.reload-failed"));
						}
					}
					default -> invocation.source().sendMessage(plugin.getTranslation(invocation.source(), "command.unknown-subcommand"));
				}
			} else {
				invocation.source().sendMessage(plugin.getTranslation(invocation.source(), "command.usage"));
			}
		}
	}

	@Override
	public boolean hasPermission(Invocation invocation) {
		return invocation.source().hasPermission("autoserver.command");
	}
}

# AutoServer

AutoServer is a Velocity plugin which allows you to automatically start a shutdown Minecraft server that the player tries to connect to. This is useful for servers that are not always online, but you want to allow players to connect to the server at any time.

This plugin is designed with the concept that your server is running inside a loop in your start script and the application is blocking the execution of the script until it receives a request to close itself which in turn starts the server again.

## Installation

1. Download the [Velocity plugin](https://ci.minebench.de/job/AutoServer/lastSuccessfulBuild/artifact/velocity/target/AutoServer-Velocity.jar) and the [standalone application](https://ci.minebench.de/job/AutoServer/lastSuccessfulBuild/artifact/application/target/AutoServer-Application.jar)
2. Place the plugin in the `plugins` folder of your Velocity server
3. Place the standalone application in the same directory as your server
4. Add some way to automatically stop the Minecraft server e.g. a plugin which stops it after every player left like [AutoStop](https://github.com/pmdevita/AutoStop).
5. Modify your server start script so that it runs in a loop and that the standalone application is started after the server is shutdown e.g. this on linux:
```bash
#!/bin/bash

[ -e stop ] && rm stop
echo -n 0 > stop

while [ `cat stop` -ne 1 ];
do
    echo "Server started at $(date --rfc-3339=seconds)";
    java -jar paper.jar nogui; # your server start command
    echo "Server stopped at $(date --rfc-3339=seconds)";
    java -jar AutoServer-Application.jar;
done
```

The standalone application will then wait until it receives a request on the port the Minecraft server normally uses until it shuts down which allows the looping script to start your server again.

## License

This project is licensed under the AGPL-3.0 License - see the [LICENSE](LICENSE) file for details.
```
AutoServer
Copyright (C) 2024 Max Lee aka Phoenix616 (max@themoep.de)

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published
by the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
```

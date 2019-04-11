# `instant-skull`

This is a demo of the solution I came up with to solve the
question [here](https://www.spigotmc.org/threads/skull-texture.364457/).

The issue was that an inventory containing a player's skull
does not seem to update the textures on the skull.

In the event that the textures of a skull item isn't
updating, it is possible to forcibly send the client the
skull textures. There are two conditions to this, 1) the
server is retrieving the correct texture, and 2), the server
is not being rate limited if the textures need to be looked
up.

The crux of the problem is that a normal Bukkit ItemStack
only contains the String owner name field, and is therefore
incapable of carrying the texture data. Whenever it is
inserted into an inventory, the server has to do a new
lookup of the texture data when the Bukkit ItemStack is
converted to an NMS ItemStack internally. However, we can
use CraftItemStack as a vehicle to store the texture data,
because it is backed by an NMS ItemStack. By using a
CraftItemStack where we would normally use a Bukkit
ItemStack, no we will only need to lookup the texture when
the new ItemStack is created, rather than each time it is
placed into an inventory.

One problem that I ran into is the skull texture never
updating the first time the skull is created. This is due to
the fact that an Inventory internally clones the
CraftItemStack. If you are still doing a lookup on the
original, cached ItemStack, the textures on the original
will be updated. Since the Inventory has already created a
clone, the clone's texture will never be updated. In order
to fix this, I wrote `ensureSkullTextures`, which will
perform a separate lookup for the cloned item if it
determines that its textures have not been populated yet.
This lookup will then forcibly update the skull texture,
which means that the player will see the texture update
regardless of whether the server automatically performs
inventory updates.

# Compiling

``` shell
git clone https://github.com/AgentTroll/instant-skull.git
cd instant-skull
mvn clean install
```

Requires Spigot 1.8.8 API and server to be installed in your
local maven repository.

# Usage

`/instantskull [name]` pulls up an inventory containing the
skull of the given player name.

# Credits

Built with [IntelliJ IDEA](https://www.jetbrains.com/idea/).

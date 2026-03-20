# CombineLimitMod v1.0.0

**Server Mod** — Raise the maximum combine weight limit for all items.

## Installation (Server Admin)
1. Extract `combinelimit-1.0.0.zip` into your WU server folder
2. Files go to: `mods/combinelimit.properties` and `mods/combinelimit/combinelimit.jar`
3. Restart the server

## What It Does
Vanilla WU caps item combining at 64x the base template weight (e.g. iron lumps cap at 64kg). This mod raises that limit to a configurable multiplier.

## Configuration (combinelimit.properties)
| Setting | Default | Description |
|---------|---------|-------------|
| `combineMultiplier` | `100` | Maximum combine weight = base weight × this value |
| `metalLumpFloorKg` | `100` | Minimum max weight for metal lumps in kg |

## Examples
With default settings (100x multiplier):
- **Iron lump** (1kg base): can combine up to 100kg (vanilla: 64kg)
- **Plank** (3kg base): can combine up to 300kg (vanilla: 192kg)
- **Log** (24kg base): can combine up to 2400kg (vanilla: 1536kg)

## Technical Details
Patches `com.wurmonline.server.items.Item.combine()` using Javassist `setBody()`. Injects a static `calcCombineMaxWeight()` helper method and two configuration fields into the Item class. The full combine method body is replaced with an identical copy that uses the configurable multiplier instead of the hardcoded 64x.


## Tested
Confirmed working on Draeda server — iron lumps combined to 99.17kg successfully.

## Author
KaZtheGreat — https://wurmcoders.com

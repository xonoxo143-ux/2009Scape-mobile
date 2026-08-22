#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
if [[ -d "$ROOT/vendor/2009scape/Server" ]]; then
    SERVER="$ROOT/vendor/2009scape/Server/src/main"
elif [[ -d "$ROOT/server/2009scape-master/Server" ]]; then
    SERVER="$ROOT/server/2009scape-master/Server/src/main"
else
    echo "Unable to locate the canonical 2009Scape Server tree" >&2
    exit 1
fi

require() {
    local pattern="$1"
    local file="$2"
    grep -q "$pattern" "$file" || {
        echo "Missing Grand League seam '$pattern' in $file" >&2
        exit 1
    }
}

require 'GrandLeagueManager.xpMultiplier(player, slot)' "$SERVER/core/game/node/entity/skill/Skills.java"
require 'GrandLeagueManager.productionDelay(player, delay, isLeagueConstructionPulse())' "$SERVER/core/game/node/entity/skill/SkillPulse.java"
require 'GrandLeagueManager.runEnergyMultiplier(player, drain)' "$SERVER/core/game/node/entity/player/link/Settings.java"
require 'GrandLeagueManager.shopPriceMultiplier(player)' "$SERVER/core/game/shops/Shop.kt"
require 'GrandLeagueManager.shopStockConsumptionMultiplier(player)' "$SERVER/core/game/shops/Shop.kt"
require 'GrandLeagueManager.farmingGrowthMultiplier(player)' "$SERVER/content/global/skill/farming/Patch.kt"
require 'GrandLeagueManager.farmingYieldMultiplier(player)' "$SERVER/content/global/skill/farming/Patch.kt"
require 'GrandLeagueManager.farmingDiseaseImmune(player)' "$SERVER/content/global/skill/farming/Patch.kt"

require 'ResourceActivity.GATHERING, ResourceSkill.WOODCUTTING' "$SERVER/content/global/skill/gather/woodcutting/WoodcuttingListener.kt"
require 'ResourceActivity.GATHERING, ResourceSkill.FISHING' "$SERVER/content/global/skill/gather/fishing/FishingPulse.kt"
require 'ResourceActivity.GATHERING, ResourceSkill.MINING' "$SERVER/content/global/skill/gather/mining/MiningSkillPulse.kt"

require 'ResourceActivity.PRODUCTION, ResourceSkill.COOKING' "$SERVER/content/global/skill/cooking/StandardCookingPulse.java"
require 'ResourceActivity.PRODUCTION, ResourceSkill.SMITHING' "$SERVER/content/global/skill/smithing/SmithingPulse.java"
require 'ResourceActivity.PRODUCTION, ResourceSkill.CRAFTING' "$SERVER/content/global/skill/crafting/glass/GlassCraftingPulse.kt"
require 'GrandLeagueManager.productionDelay' "$SERVER/content/global/skill/cooking/StandardCookingPulse.java"
require 'GrandLeagueManager.productionDelay' "$SERVER/content/global/skill/crafting/silver/SilverCraftingPulse.kt"
require 'GrandLeagueManager.productionDelay' "$SERVER/content/global/skill/crafting/glass/GlassCraftingPulse.kt"
require 'GrandLeagueManager.productionDelay' "$SERVER/content/global/skill/crafting/glass/GlassMakePulse.kt"

require 'LeagueGatheringProcessor.resolve' "$SERVER/content/global/leagues/GrandLeagueManager.kt"
require 'Bar.STEEL' "$SERVER/content/global/leagues/LeagueGatheringProcessor.kt"
require 'CookableItems.forId' "$SERVER/content/global/leagues/LeagueGatheringProcessor.kt"
require 'Log.forId' "$SERVER/content/global/leagues/LeagueGatheringProcessor.kt"

require 'GrandLeagueManager.thievingSuccessMultiplier' "$SERVER/content/global/skill/thieving/ThievingListeners.kt"
require 'GrandLeagueManager.agilityFailChanceMultiplier' "$SERVER/content/global/skill/agility/AgilityHandler.java"
require 'GrandLeagueManager.hunterSuccessMultiplier' "$SERVER/content/global/skill/hunter/TrapSetting.java"
require 'GrandLeagueManager.hunterSuccessMultiplier' "$SERVER/content/global/skill/hunter/falconry/FalconryCatchPulse.java"
require 'GrandLeagueManager.hunterSuccessMultiplier' "$SERVER/content/global/skill/hunter/bnet/BNetPulse.java"

# Combat modifier seams are intentionally central: all normal style swings pass through these hooks.
require 'GrandLeagueManager.combatAccuracyMultiplier' "$SERVER/core/game/node/entity/combat/CombatSwingHandler.kt"
require 'GrandLeagueManager.combatDefencePenetration' "$SERVER/core/game/node/entity/combat/CombatSwingHandler.kt"
require 'GrandLeagueManager.combatDamageMultiplier' "$SERVER/core/game/node/entity/combat/CombatSwingHandler.kt"
require 'GrandLeagueManager.combatAttackInterval' "$SERVER/core/game/node/entity/combat/CombatPulse.kt"
require 'GrandLeagueManager.rangedAmmoSaveChance' "$SERVER/core/game/node/entity/combat/RangeSwingHandler.kt"
require 'GrandLeagueManager.magicRuneSaveChance' "$SERVER/core/game/node/entity/combat/spell/MagicSpell.java"
require 'GrandLeagueManager.combatExtraHitChance' "$SERVER/core/game/node/entity/combat/CombatSwingHandler.kt"
require 'GrandLeagueManager.combatExecutionDamage' "$SERVER/core/game/node/entity/combat/ImpactHandler.java"
require 'GrandLeagueManager.interceptLethalDamage' "$SERVER/core/game/node/entity/combat/ImpactHandler.java"
require 'GrandLeagueManager.incomingCombatDamage' "$SERVER/core/game/node/entity/combat/ImpactHandler.java"
require 'GrandLeagueManager.applyCombatSustain' "$SERVER/core/game/node/entity/combat/ImpactHandler.java"
require 'GrandLeagueManager.reflectedCombatDamage' "$SERVER/core/game/node/entity/combat/ImpactHandler.java"
require 'GrandLeagueManager.specialAttackCost' "$SERVER/core/game/node/entity/player/link/Settings.java"
require 'GrandLeagueManager.specialEnergyRestore' "$SERVER/core/game/system/timer/impl/SkillRestore.kt"

# Guard event-quantity corrections that prevent production relics multiplying batch counters.
require 'dairy.getProduct().getAmount()' "$SERVER/content/global/skill/cooking/dairy/DairyChurnPulse.java"
require 'ResourceProducedEvent(product, 1' "$SERVER/content/global/skill/crafting/glass/GlassMakePulse.kt"

echo 'GRAND LEAGUE SERVER SEAMS PASS'

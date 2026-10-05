package com.leftovers.app.ui.icons

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Icons that categories and goals can use, stored in the database by key.
 * The key lives in the `emoji` column for historical reasons.
 */
object CategoryIcons {
    private val icons: Map<String, () -> ImageVector> = linkedMapOf(
        "utensils" to { Lucide.Utensils },
        "coffee" to { Lucide.Coffee },
        "shopping-cart" to { Lucide.ShoppingCart },
        "store" to { Lucide.Store },
        "taxi" to { Lucide.CarTaxiFront },
        "car" to { Lucide.Car },
        "bus" to { Lucide.Bus },
        "train" to { Lucide.TrainFront },
        "bike" to { Lucide.Bike },
        "fuel" to { Lucide.Fuel },
        "plane" to { Lucide.Plane },
        "shopping-bag" to { Lucide.ShoppingBag },
        "shirt" to { Lucide.Shirt },
        "receipt" to { Lucide.Receipt },
        "zap" to { Lucide.Zap },
        "droplet" to { Lucide.Droplet },
        "wifi" to { Lucide.Wifi },
        "smartphone" to { Lucide.Smartphone },
        "house" to { Lucide.House },
        "sofa" to { Lucide.Sofa },
        "wrench" to { Lucide.Wrench },
        "clapperboard" to { Lucide.Clapperboard },
        "popcorn" to { Lucide.Popcorn },
        "ticket" to { Lucide.Ticket },
        "music" to { Lucide.Music },
        "gamepad" to { Lucide.Gamepad2 },
        "tv" to { Lucide.Tv },
        "heart-pulse" to { Lucide.HeartPulse },
        "pill" to { Lucide.Pill },
        "stethoscope" to { Lucide.Stethoscope },
        "dumbbell" to { Lucide.Dumbbell },
        "graduation-cap" to { Lucide.GraduationCap },
        "book" to { Lucide.BookOpen },
        "gift" to { Lucide.Gift },
        "party" to { Lucide.PartyPopper },
        "baby" to { Lucide.Baby },
        "paw" to { Lucide.PawPrint },
        "scissors" to { Lucide.Scissors },
        "beer" to { Lucide.Beer },
        "globe" to { Lucide.Globe },
        "mountain" to { Lucide.Mountain },
        "umbrella" to { Lucide.Umbrella },
        "shield" to { Lucide.Shield },
        "credit-card" to { Lucide.CreditCard },
        "package" to { Lucide.Package },
        "briefcase" to { Lucide.Briefcase },
        "laptop" to { Lucide.Laptop },
        "monitor" to { Lucide.Monitor },
        "camera" to { Lucide.Camera },
        "guitar" to { Lucide.Guitar },
        "gem" to { Lucide.Gem },
        "building" to { Lucide.Building2 },
        "landmark" to { Lucide.Landmark },
        "trending-up" to { Lucide.TrendingUp },
        "hand-coins" to { Lucide.HandCoins },
        "banknote" to { Lucide.Banknote },
        "coins" to { Lucide.Coins },
        "piggy-bank" to { Lucide.PiggyBank },
        "wallet" to { Lucide.Wallet },
        "sparkles" to { Lucide.Sparkles },
    )

    val keys: List<String> = icons.keys.toList()

    /** Unknown keys (e.g. emoji from very old data) fall back to a neutral icon. */
    operator fun get(key: String): ImageVector = (icons[key] ?: icons.getValue("sparkles"))()

    val goalKeys = listOf(
        "monitor", "laptop", "smartphone", "gamepad", "car", "bike", "plane", "house",
        "gem", "graduation-cap", "camera", "guitar", "shield", "gift", "piggy-bank",
    )
}

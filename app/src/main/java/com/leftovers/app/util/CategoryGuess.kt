package com.leftovers.app.util

import com.leftovers.app.data.TxType

/** An icon (a key from CategoryIcons) for a category made on import, guessed from its name in any common wording. */
object CategoryGuess {
    private val rules: List<Pair<Regex, String>> = listOf(
        "loan|emi|credit card|cards?(?![a-z])" to "credit-card",
        "coffee|cafe|tea(?![a-z])" to "coffee",
        "grocer|supermarket|vegetable|fruit|kirana" to "shopping-cart",
        "food|dining|restaurant|eat|lunch|dinner|breakfast|snack|meal|takeaway|swiggy|zomato" to "utensils",
        "fuel|petrol|diesel|gas station" to "fuel",
        "taxi|cab|uber|ola|auto|rickshaw" to "taxi",
        "car|vehicle|parking|toll" to "car",
        "bus|transport|commute|metro|transit" to "bus",
        "train|rail" to "train",
        "flight|travel|trip|holiday|vacation|plane" to "plane",
        "hotel|stay" to "building",
        "cloth|apparel|fashion|shoe" to "shirt",
        "shop|purchase|amazon|flipkart" to "shopping-bag",
        "electric|power|utilit" to "zap",
        "water" to "droplet",
        "internet|wifi|broadband" to "wifi",
        "phone|mobile|recharge" to "smartphone",
        "rent|house|home|housing" to "house",
        "furniture|decor" to "sofa",
        "repair|maintenance|service" to "wrench",
        "movie|cinema|film" to "clapperboard",
        "entertain|fun|leisure|hobb" to "ticket",
        "music|spotify|concert" to "music",
        "game|gaming" to "gamepad",
        "subscription|stream|netflix|tv" to "tv",
        "health|medical|hospital|doctor" to "stethoscope",
        "medicine|pharma|drug|pill" to "pill",
        "gym|fitness|sport|workout|yoga" to "dumbbell",
        "educat|school|college|tuition|course|fees" to "graduation-cap",
        "book|stationer" to "book",
        "gift|present|donat|charity" to "gift",
        "party|celebrat|wedding|festival" to "party",
        "baby|kid|child" to "baby",
        "(pets?|dogs?|cats?|vet)(?![a-z])" to "paw",
        "salon|beauty|hair|grooming|personal care" to "scissors",
        "drink|bar|alcohol|beer|wine" to "beer",
        "insurance" to "shield",
        "tax|government|fine" to "landmark",
        "invest|stock|mutual|sip|dividend|interest" to "trending-up",
        "salary|wage|paycheck|payroll|income|job" to "briefcase",
        "freelanc|business|work" to "laptop",
        "refund|cashback|reimburs" to "hand-coins",
        "saving" to "piggy-bank",
        "cash|atm" to "banknote",
    ).map { (words, icon) -> Regex("""\b($words)""", RegexOption.IGNORE_CASE) to icon }

    fun icon(name: String, type: TxType): String =
        rules.firstOrNull { (re, _) -> re.containsMatchIn(name) }?.second ?: if (type == TxType.INCOME) "coins" else "package"
}

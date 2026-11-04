else if (shown.isEmpty()) Card { EmptyState(Icons.AutoMirrored.Filled.Send, "No $filter transfers", "Nothing here right now.") }
        shown.forEachIndexed { i, t -> key(t.id) { Appear(minOf(i, 6) * 40) { TransferCard(t) } } }

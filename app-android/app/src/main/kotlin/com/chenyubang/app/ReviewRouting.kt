package com.chenyubang.app

fun RouterState.startReview(pendingCount: Int): RouterState {
    if (pendingCount <= 0 || review != null || modal != null) return this
    require(current.route == Route.Mistakes || current.route == Route.Idiom)
    val session = ReviewSession(current, stack.size, current.id.takeIf { current.route == Route.Idiom })
    return append(RouteEntry(Route.Queue, session.idiomId, sequence)).copy(review = session)
}

fun RouterState.finishReview(answeredCount: Int): RouterState {
    val session = requireNotNull(review)
    require(current.route == Route.Queue && answeredCount > 0)
    return replaceTop(RouteEntry(Route.Summary, session.idiomId, sequence))
}

fun RouterState.retryReview(pendingCount: Int): RouterState {
    val session = review
    if (pendingCount <= 0 || session == null || current.route != Route.Summary) return this
    return append(RouteEntry(Route.Queue, session.idiomId, sequence))
}

fun RouterState.exitReview(): RouterState {
    val session = review ?: return this
    return withStack(stack.take(session.baseDepth)).copy(review = null)
}

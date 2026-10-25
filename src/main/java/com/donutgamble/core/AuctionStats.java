package com.donutgamble.core;

/** Completed auctions: how many, total and average collected, and the biggest one (null if none). */
public record AuctionStats(int completed, long totalCollected, long average, Auction biggest) {
}

package com.realestate.interfaces;

/**
 * Interface demonstrating Java OOP abstraction for any entity
 * that supports keyword, location, and price matching.
 */
public interface Searchable {
    boolean matchesKeyword(String keyword);
    boolean matchesLocation(String city);
    boolean matchesPriceRange(double minPrice, double maxPrice);
}
